package com.sajith.scrolllock.service;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.model.FocusState;
import com.sajith.scrolllock.util.PermissionHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Play-Protect-compliant Deep Focus coordinator:
 * 1. Complete Notification Preview Hiding via Android Do Not Disturb Policy
 *    (ACCESS_NOTIFICATION_POLICY) with deterministic snapshot/restore of user DND settings.
 * 2. Offline / Non-Essential Internet Shielding using standard LAUNCHER query visibility
 *    and UsageStats-backed distraction alerts + official Android Connectivity Panel / App Data links
 *    (zero VpnService packet interception and zero system-wide package inventory harvesting).
 */
public final class DeepFocusController {

    private DeepFocusController() {
    }

    /**
     * Activates configured Deep Focus restrictions (notification preview hiding & non-essential
     * app internet/distraction tracking) while a Focus Session is active.
     */
    public static synchronized void engage(Context context) {
        Context appContext = context.getApplicationContext();
        PreferencesManager prefs = new PreferencesManager(appContext);
        if (!prefs.isDeepFocusEnabled()) {
            return;
        }
        prefs.setDeepFocusRuntimeActive(true);

        if (prefs.isDeepFocusHidePreviewsEnabled()) {
            applyDndPreviewSuppression(appContext, prefs);
        } else {
            restoreDndIfSaved(appContext, prefs);
        }
    }

    /**
     * Immediately restores notification previews (prior DND policy) and clears Deep Focus active flag.
     */
    public static synchronized void disengage(Context context) {
        Context appContext = context.getApplicationContext();
        PreferencesManager prefs = new PreferencesManager(appContext);

        restoreDndIfSaved(appContext, prefs);
        prefs.setDeepFocusRuntimeActive(false);
    }

    /**
     * Ensures no Deep Focus locks remain active if the app restarts after a session finished offline.
     */
    public static synchronized void reconcileIfIdle(Context context) {
        Context appContext = context.getApplicationContext();
        PreferencesManager prefs = new PreferencesManager(appContext);
        if (prefs.isDeepFocusRuntimeActive() && prefs.getFocusState() != FocusState.RUNNING) {
            disengage(appContext);
        }
    }

    /**
     * Re-applies Deep Focus rules immediately when the user edits exceptions or toggles
     * options while a Focus Session is actively running.
     */
    public static synchronized void syncWithSessionState(Context context, FocusState currentState) {
        Context appContext = context.getApplicationContext();
        PreferencesManager prefs = new PreferencesManager(appContext);
        if (currentState == FocusState.RUNNING && prefs.isDeepFocusEnabled()) {
            engage(appContext);
        } else {
            disengage(appContext);
        }
    }

    /**
     * Resolves launchable installed apps whose internet access/usage should be restricted during Deep Focus.
     * Uses strictly the declared LAUNCHER queryIntentActivities filter—never scans hidden OS daemons.
     */
    public static ArrayList<String> resolveBlockedInternetPackages(Context context) {
        PackageManager pm = context.getPackageManager();
        PreferencesManager prefs = new PreferencesManager(context);
        Set<String> userAllowed = prefs.getDeepFocusAllowedPackages();
        String ownPkg = context.getPackageName();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(mainIntent, 0);

        ArrayList<String> blocked = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : resolved) {
            if (info == null || info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (seen.contains(pkg)) {
                continue;
            }
            seen.add(pkg);

            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            boolean isSystem = appInfo != null &&
                    (appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;

            if (NonEssentialAppClassifier.shouldBlockInternet(
                    pkg,
                    ownPkg,
                    true,
                    isSystem,
                    userAllowed
            )) {
                blocked.add(pkg);
            }
        }
        return blocked;
    }

    private static void applyDndPreviewSuppression(Context context, PreferencesManager prefs) {
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        try {
            if (!prefs.hasSavedPriorDndSnapshot()) {
                int currentFilter = nm.getCurrentInterruptionFilter();
                NotificationManager.Policy currentPolicy = nm.getNotificationPolicy();
                int cats = currentPolicy != null ? currentPolicy.priorityCategories : NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS;
                int calls = currentPolicy != null ? currentPolicy.priorityCallSenders : NotificationManager.Policy.PRIORITY_SENDERS_STARRED;
                int msgs = currentPolicy != null ? currentPolicy.priorityMessageSenders : NotificationManager.Policy.PRIORITY_SENDERS_STARRED;
                int suppressed = currentPolicy != null ? currentPolicy.suppressedVisualEffects : 0;
                prefs.savePriorDndSnapshot(currentFilter, cats, calls, msgs, suppressed);
            }

            int suppressedEffects = buildFullPreviewSuppressionMask();
            // Keep alarms and repeat callers available while suppressing visual notification previews
            int priorityCategories = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
                    | NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS;
            NotificationManager.Policy deepPolicy = new NotificationManager.Policy(
                    priorityCategories,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                    suppressedEffects
            );
            nm.setNotificationPolicy(deepPolicy);
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        } catch (SecurityException ignored) {
            // Permission may have been revoked externally
        }
    }

    private static int buildFullPreviewSuppressionMask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT
                    | NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS
                    | NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK
                    | NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR
                    | NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE
                    | NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT
                    | NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST;
        }
        return NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF
                | NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON;
    }

    private static void restoreDndIfSaved(Context context, PreferencesManager prefs) {
        if (!prefs.hasSavedPriorDndSnapshot()) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null && PermissionHelper.hasNotificationPolicyAccess(context)) {
            try {
                NotificationManager.Policy priorPolicy = new NotificationManager.Policy(
                        prefs.getPriorDndPriorityCategories(),
                        prefs.getPriorDndCallSenders(),
                        prefs.getPriorDndMessageSenders(),
                        prefs.getPriorDndSuppressedEffects()
                );
                nm.setNotificationPolicy(priorPolicy);
                nm.setInterruptionFilter(prefs.getPriorDndFilter());
            } catch (SecurityException ignored) {
            }
        }
        prefs.clearPriorDndSnapshot();
    }
}
