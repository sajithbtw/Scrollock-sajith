package com.sajith.scrolllock.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import com.example.R;
import com.sajith.scrolllock.ScrollLockApp;
import com.sajith.scrolllock.data.local.AppRepository;
import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.model.FocusState;
import com.sajith.scrolllock.ui.FocusInterruptionActivity;
import com.sajith.scrolllock.ui.MainActivity;
import com.sajith.scrolllock.util.PermissionHelper;

import java.util.Set;

/**
 * Play-Protect-safe UsageStats distraction monitor replacing AccessibilityService window snooping.
 *
 * Uses user-granted PACKAGE_USAGE_STATS during active Focus Sessions to detect transitions into
 * guarded apps, Reels/Shorts host apps, or non-essential internet-restricted apps, posting a
 * transparent high-priority heads-up guard alert with direct "Workout to Unlock" and "App Data"
 * actions instead of hijacking the screen or injecting Accessibility gestures.
 */
public final class UsageStatsGuardMonitor {

    public enum GuardAlertType {
        NONE,
        APP_LOCK,
        SHORT_FORM,
        DEEP_FOCUS_NETWORK
    }

    private static final int NOTIFICATION_ID_GUARD = 1004;
    private static final long QUERY_WINDOW_MS = 4500L;
    private static final long ALERT_COOLDOWN_MS = 12_000L;

    private static String lastAlertPkg = "";
    private static long lastAlertElapsedMs = 0L;

    private UsageStatsGuardMonitor() {
    }

    /**
     * Pure, unit-testable evaluator deciding whether switching to {@code foregroundPkg} during an
     * active session should trigger a Play-safe Focus Guard alert.
     */
    public static GuardAlertType evaluateForegroundPackage(
            String foregroundPkg,
            String ownPkg,
            boolean focusRunning,
            boolean alwaysGuardEnabled,
            boolean shortFormEnabled,
            boolean deepFocusNetworkShieldActive,
            Set<String> guardedPackages,
            Set<String> shortFormPackages,
            Set<String> userAllowedDeepFocusPackages,
            boolean isAppTemporarilyUnlocked,
            boolean isShortFormTemporarilyUnlocked
    ) {
        if (foregroundPkg == null || foregroundPkg.trim().isEmpty()) {
            return GuardAlertType.NONE;
        }
        if (foregroundPkg.equals(ownPkg)
                || NonEssentialAppClassifier.isCoreEssentialPackage(foregroundPkg, ownPkg)) {
            return GuardAlertType.NONE;
        }

        if ((focusRunning || alwaysGuardEnabled)
                && guardedPackages != null
                && guardedPackages.contains(foregroundPkg)
                && (focusRunning || !isAppTemporarilyUnlocked)) {
            return GuardAlertType.APP_LOCK;
        }

        if (shortFormEnabled
                && shortFormPackages != null
                && shortFormPackages.contains(foregroundPkg)
                && (focusRunning || !isShortFormTemporarilyUnlocked)) {
            return GuardAlertType.SHORT_FORM;
        }

        if (focusRunning
                && deepFocusNetworkShieldActive
                && (userAllowedDeepFocusPackages == null || !userAllowedDeepFocusPackages.contains(foregroundPkg))) {
            return GuardAlertType.DEEP_FOCUS_NETWORK;
        }

        return GuardAlertType.NONE;
    }

    /**
     * Polls UsageEvents during an active Focus Session and dispatches a compliant heads-up alert
     * if the user enters a guarded or distracting app.
     */
    public static void checkForegroundTransition(Context context) {
        if (!PermissionHelper.hasUsageStatsPermission(context)) {
            return;
        }
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return;
        }

        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - QUERY_WINDOW_MS, now);
        if (events == null) {
            return;
        }

        String latestPkg = null;
        long latestTs = 0L;
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            if ((type == UsageEvents.Event.ACTIVITY_RESUMED
                    || type == UsageEvents.Event.MOVE_TO_FOREGROUND)
                    && event.getTimeStamp() >= latestTs) {
                latestTs = event.getTimeStamp();
                latestPkg = event.getPackageName();
            }
        }

        if (latestPkg == null || latestPkg.isEmpty()) {
            return;
        }

        PreferencesManager prefs = new PreferencesManager(context);
        FocusSessionManager sessionManager = FocusSessionManager.getInstance(context);
        boolean focusRunning = sessionManager.getCurrentStateValue() == FocusState.RUNNING;
        boolean deepFocusNetShield = focusRunning
                && prefs.isDeepFocusEnabled()
                && prefs.isDeepFocusBlockInternetEnabled();

        GuardAlertType alertType = evaluateForegroundPackage(
                latestPkg,
                context.getPackageName(),
                focusRunning,
                prefs.isAlwaysGuardAppsEnabled() && prefs.isWorkoutUnlockEnabled(),
                prefs.isShortFormBlockerEnabled(),
                deepFocusNetShield,
                prefs.getSelectedPackages(),
                prefs.getShortFormBlockedPackages(),
                prefs.getDeepFocusAllowedPackages(),
                prefs.isAppTemporarilyUnlocked(latestPkg),
                prefs.isShortFormTemporarilyUnlocked(latestPkg)
        );

        if (alertType == GuardAlertType.NONE) {
            return;
        }

        long elapsed = SystemClock.elapsedRealtime();
        if (latestPkg.equals(lastAlertPkg) && (elapsed - lastAlertElapsedMs) < ALERT_COOLDOWN_MS) {
            return;
        }
        lastAlertPkg = latestPkg;
        lastAlertElapsedMs = elapsed;

        String appLabel = new AppRepository(context).getAppLabel(latestPkg);
        sessionManager.incrementSessionInterruption(latestPkg, appLabel);
        postGuardAlertNotification(context, latestPkg, appLabel, alertType);
    }

    private static void postGuardAlertNotification(
            Context context,
            String targetPkg,
            String appLabel,
            GuardAlertType alertType
    ) {
        if (!PermissionHelper.hasNotificationPermission(context)) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }

        Intent gateIntent = new Intent(context, FocusInterruptionActivity.class);
        gateIntent.putExtra(FocusInterruptionActivity.EXTRA_BLOCKED_PKG, targetPkg);
        gateIntent.putExtra(FocusInterruptionActivity.EXTRA_BLOCKED_APP_NAME, appLabel);
        gateIntent.putExtra(
                FocusInterruptionActivity.EXTRA_BLOCK_MODE,
                alertType == GuardAlertType.SHORT_FORM
                        ? FocusInterruptionActivity.MODE_SHORT_FORM
                        : FocusInterruptionActivity.MODE_APP_LOCK
        );
        gateIntent.putExtra(FocusInterruptionActivity.EXTRA_BLOCK_SURFACE, appLabel + " Reels/Shorts");
        gateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent gatePending = PendingIntent.getActivity(
                context, 31, gateIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent returnFocusIntent = new Intent(context, MainActivity.class);
        returnFocusIntent.putExtra(MainActivity.EXTRA_TARGET_TAB, R.id.nav_focus);
        returnFocusIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent returnPending = PendingIntent.getActivity(
                context, 32, returnFocusIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title;
        String body;
        if (alertType == GuardAlertType.SHORT_FORM) {
            title = context.getString(R.string.guard_notif_reels_title, appLabel);
            body = context.getString(R.string.guard_notif_reels_body);
        } else if (alertType == GuardAlertType.DEEP_FOCUS_NETWORK) {
            title = context.getString(R.string.guard_notif_deep_title, appLabel);
            body = context.getString(R.string.guard_notif_deep_body);
        } else {
            title = context.getString(R.string.guard_notif_app_title, appLabel);
            body = context.getString(R.string.guard_notif_app_body);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, ScrollLockApp.CHANNEL_GUARD_ALERTS_ID
        )
                .setSmallIcon(R.drawable.ic_shield_check)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(gatePending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.btn_start_workout_unlock), gatePending)
                .addAction(0, context.getString(R.string.btn_return_to_focus), returnPending);

        if (alertType == GuardAlertType.DEEP_FOCUS_NETWORK) {
            Intent appDetailsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            appDetailsIntent.setData(Uri.fromParts("package", targetPkg, null));
            appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent dataPending = PendingIntent.getActivity(
                    context, 33, appDetailsIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(0, context.getString(R.string.guard_notif_action_data_settings), dataPending);
        }

        nm.notify(NOTIFICATION_ID_GUARD, builder.build());
    }
}
