package com.sajith.scrolllock.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.R;
import com.sajith.scrolllock.ScrollLockApp;
import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.model.FocusState;
import com.sajith.scrolllock.ui.MainActivity;
import com.sajith.scrolllock.util.PermissionHelper;
import com.sajith.scrolllock.util.TimeFormatter;

/**
 * Foreground Service keeping the Focus Session countdown accurate and visible
 * while the user puts ScrollLock in the background.
 */
public class FocusTimerService extends Service {

    public static final String ACTION_START_OR_RESUME = "com.sajith.scrolllock.action.START_OR_RESUME";
    public static final String ACTION_PAUSE = "com.sajith.scrolllock.action.PAUSE";
    public static final String ACTION_STOP = "com.sajith.scrolllock.action.STOP";
    public static final String ACTION_COMPLETE = "com.sajith.scrolllock.action.COMPLETE";

    private static final int NOTIFICATION_ID_TIMER = 1001;
    private static final int NOTIFICATION_ID_COMPLETE = 1002;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FocusSessionManager sessionManager;
    private PreferencesManager preferencesManager;
    private boolean isTicking = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTicking) {
                return;
            }
            sessionManager.onTimerTick();
            if (sessionManager.getCurrentStateValue() == FocusState.RUNNING) {
                UsageStatsGuardMonitor.checkForegroundTransition(FocusTimerService.this);
                updateOngoingNotification(true);
                handler.postDelayed(this, 1000L);
            } else {
                isTicking = false;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sessionManager = FocusSessionManager.getInstance(this);
        preferencesManager = new PreferencesManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START_OR_RESUME;
        if (ACTION_PAUSE.equals(action)) {
            isTicking = false;
            handler.removeCallbacks(tickRunnable);
            updateOngoingNotification(false);
        } else if (ACTION_STOP.equals(action)) {
            isTicking = false;
            handler.removeCallbacks(tickRunnable);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        } else if (ACTION_COMPLETE.equals(action)) {
            isTicking = false;
            handler.removeCallbacks(tickRunnable);
            stopForeground(STOP_FOREGROUND_REMOVE);
            showCompletionNotification();
            stopSelf();
        } else {
            startForeground(NOTIFICATION_ID_TIMER, buildTimerNotification(true));
            if (!isTicking) {
                isTicking = true;
                handler.removeCallbacks(tickRunnable);
                handler.postDelayed(tickRunnable, 1000L);
            }
        }
        return START_STICKY;
    }

    private Notification buildTimerNotification(boolean running) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.putExtra(MainActivity.EXTRA_TARGET_TAB, R.id.nav_focus);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String formattedTime = TimeFormatter.formatCountdown(sessionManager.getCurrentRemainingMillisValue());
        String title = running
                ? getString(R.string.notif_timer_running_format, formattedTime)
                : getString(R.string.notif_timer_paused_format, formattedTime);

        boolean deepSecret = running
                && preferencesManager != null
                && preferencesManager.isDeepFocusEnabled()
                && preferencesManager.isDeepFocusHidePreviewsEnabled();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ScrollLockApp.CHANNEL_TIMER_ID)
                .setSmallIcon(R.drawable.ic_lock)
                .setContentTitle(title)
                .setContentText(getString(deepSecret ? R.string.deep_focus_timer_subtext : R.string.notif_timer_subtext))
                .setContentIntent(contentIntent)
                .setOngoing(running)
                .setOnlyAlertOnce(true)
                .setSilent(true);

        if (deepSecret) {
            builder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
        }
        return builder.build();
    }

    private void updateOngoingNotification(boolean running) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_TIMER, buildTimerNotification(running));
        }
    }

    private void showCompletionNotification() {
        if (!PermissionHelper.hasNotificationPermission(this)) {
            return;
        }
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.putExtra(MainActivity.EXTRA_TARGET_TAB, R.id.nav_focus);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, ScrollLockApp.CHANNEL_ALERTS_ID)
                .setSmallIcon(R.drawable.ic_check_circle)
                .setContentTitle(getString(R.string.notif_complete_title))
                .setContentText(getString(R.string.notif_complete_body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_COMPLETE, notification);
        }
    }

    @Override
    public void onDestroy() {
        isTicking = false;
        handler.removeCallbacks(tickRunnable);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
