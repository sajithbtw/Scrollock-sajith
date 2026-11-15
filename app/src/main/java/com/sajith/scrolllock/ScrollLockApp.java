package com.sajith.scrolllock;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.R;
import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.service.DeepFocusController;
import com.sajith.scrolllock.service.ReminderScheduler;

/**
 * Application entry point for ScrollLock.
 * Initializes saved theme preferences, creates notification channels, reconciles
 * Deep Focus DND state, and reschedules enabled task reminders cleanly on startup.
 */
public class ScrollLockApp extends Application {

    public static final String CHANNEL_TIMER_ID = "scrolllock_timer_channel";
    public static final String CHANNEL_ALERTS_ID = "scrolllock_alerts_channel";
    public static final String CHANNEL_GUARD_ALERTS_ID = "scrolllock_guard_alerts_channel";
    public static final String CHANNEL_REMINDERS_ID = "scrolllock_reminders_channel";
    public static final String CHANNEL_ALARM_TASKS_ID = "scrolllock_alarm_tasks_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        applySavedTheme();
        createNotificationChannels();
        DeepFocusController.reconcileIfIdle(this);
        ReminderScheduler.INSTANCE.rescheduleAllEnabledTasks(this);
    }

    /**
     * Reads the persisted theme choice (System, Light, or Dark) and applies it immediately.
     */
    public void applySavedTheme() {
        PreferencesManager prefs = new PreferencesManager(this);
        applyThemeMode(prefs.getThemeMode());
    }

    public static void applyThemeMode(int themeMode) {
        switch (themeMode) {
            case PreferencesManager.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case PreferencesManager.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case PreferencesManager.THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private void createNotificationChannels() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        // Low-importance silent channel for the persistent focus countdown notification
        NotificationChannel timerChannel = new NotificationChannel(
                CHANNEL_TIMER_ID,
                getString(R.string.notif_channel_timer_name),
                NotificationManager.IMPORTANCE_LOW
        );
        timerChannel.setDescription(getString(R.string.notif_channel_timer_desc));
        timerChannel.setShowBadge(false);

        // Default-importance channel for focus session completion alerts
        NotificationChannel alertChannel = new NotificationChannel(
                CHANNEL_ALERTS_ID,
                getString(R.string.notif_channel_alert_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        alertChannel.setDescription(getString(R.string.notif_channel_alert_desc));

        // High-importance channel for UsageStats Focus Guard & Reels Shield notifications
        NotificationChannel guardChannel = new NotificationChannel(
                CHANNEL_GUARD_ALERTS_ID,
                getString(R.string.notif_channel_guard_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        guardChannel.setDescription(getString(R.string.notif_channel_guard_desc));

        // High-importance channel for Reminders & Study Task notifications
        NotificationChannel reminderChannel = new NotificationChannel(
                CHANNEL_REMINDERS_ID,
                getString(R.string.notif_channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        reminderChannel.setDescription(getString(R.string.notif_channel_reminders_desc));
        reminderChannel.enableVibration(true);

        // High-importance alarm channel with USAGE_ALARM audio & vibration
        NotificationChannel alarmTaskChannel = new NotificationChannel(
                CHANNEL_ALARM_TASKS_ID,
                getString(R.string.notif_channel_alarm_tasks_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        alarmTaskChannel.setDescription(getString(R.string.notif_channel_alarm_tasks_desc));
        alarmTaskChannel.enableVibration(true);
        alarmTaskChannel.setVibrationPattern(new long[]{0L, 400L, 250L, 400L});
        alarmTaskChannel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
        );

        manager.createNotificationChannel(timerChannel);
        manager.createNotificationChannel(alertChannel);
        manager.createNotificationChannel(guardChannel);
        manager.createNotificationChannel(reminderChannel);
        manager.createNotificationChannel(alarmTaskChannel);
    }
}
