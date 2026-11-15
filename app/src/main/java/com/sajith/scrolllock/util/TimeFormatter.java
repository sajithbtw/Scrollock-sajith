package com.sajith.scrolllock.util;

import android.content.Context;

import com.example.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Utility helpers for formatting countdown timers, durations, greetings, and day keys.
 */
public final class TimeFormatter {

    private TimeFormatter() {
    }

    /**
     * Formats milliseconds as MM:SS (e.g., 25:00).
     */
    public static String formatCountdown(long millis) {
        long safeMillis = Math.max(0L, millis);
        long totalSeconds = (safeMillis + 999L) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    /**
     * Formats milliseconds into human-readable duration (e.g., "1h 15m" or "24m" or "< 1m").
     */
    public static String formatDurationShort(long millis) {
        if (millis <= 0L) {
            return "0m";
        }
        long totalMinutes = millis / 60_000L;
        if (totalMinutes < 1L) {
            return "< 1m";
        }
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L && minutes > 0L) {
            return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        } else if (hours > 0L) {
            return String.format(Locale.getDefault(), "%dh", hours);
        } else {
            return String.format(Locale.getDefault(), "%dm", minutes);
        }
    }

    /**
     * Formats total focus seconds for Insights cards.
     */
    public static String formatFocusMinutesSummary(int totalSeconds) {
        int totalMinutes = totalSeconds / 60;
        if (totalMinutes >= 60) {
            int hours = totalMinutes / 60;
            int mins = totalMinutes % 60;
            return mins > 0
                    ? String.format(Locale.getDefault(), "%dh %dm", hours, mins)
                    : String.format(Locale.getDefault(), "%dh", hours);
        }
        return String.format(Locale.getDefault(), "%dm", totalMinutes);
    }

    /**
     * Returns a time-appropriate greeting string.
     */
    public static String getGreeting(Context context) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return context.getString(R.string.greeting_morning);
        } else if (hour < 17) {
            return context.getString(R.string.greeting_afternoon);
        } else {
            return context.getString(R.string.greeting_evening);
        }
    }

    /**
     * Returns ISO day key (yyyy-MM-dd) for a given epoch timestamp.
     */
    public static String toDayKey(long epochMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date(epochMillis));
    }

    /**
     * Returns short weekday label (e.g., "Mon") for a given epoch timestamp.
     */
    public static String toShortDayLabel(long epochMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE", Locale.getDefault());
        return sdf.format(new Date(epochMillis));
    }
}
