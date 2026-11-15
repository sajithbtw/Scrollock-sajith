package com.sajith.scrolllock.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.sajith.scrolllock.data.model.FocusState;
import com.sajith.scrolllock.util.TimeFormatter;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages persistent local preferences using SharedPreferences:
 * - Selected distracting apps
 * - Theme choice & focus defaults
 * - Focus timer state
 * - Workout-to-unlock settings, per-app unlock expiration timers, XP, streak & replay cache
 */
public class PreferencesManager {

    private static final String PREFS_NAME = "scrolllock_prefs";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private static final String KEY_SELECTED_APPS = "selected_app_packages";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_DEFAULT_DURATION_MIN = "default_duration_min";

    // Timer state persistence keys
    private static final String KEY_FOCUS_STATE = "focus_state";
    private static final String KEY_SESSION_TOTAL_MILLIS = "session_total_millis";
    private static final String KEY_SESSION_REMAINING_MILLIS = "session_remaining_millis";
    private static final String KEY_SESSION_END_EPOCH = "session_end_epoch";
    private static final String KEY_SESSION_START_EPOCH = "session_start_epoch";
    private static final String KEY_SESSION_INTERRUPTIONS = "session_interruptions";

    // SweatLock / Workout-to-Unlock keys
    private static final String KEY_ALWAYS_GUARD_APPS = "always_guard_apps";
    private static final String KEY_WORKOUT_UNLOCK_ENABLED = "workout_unlock_enabled";
    private static final String KEY_REQUIRED_REPS = "required_workout_reps";
    private static final String KEY_UNLOCK_DURATION_MIN = "unlock_duration_min";
    private static final String KEY_PREFERRED_EXERCISE = "preferred_exercise";
    private static final String KEY_VOICE_COACH_ENABLED = "voice_coach_enabled";
    private static final String KEY_GHOST_TRAINER_ENABLED = "ghost_trainer_enabled";
    private static final String KEY_MIRROR_MODE_ENABLED = "mirror_mode_enabled";
    private static final String KEY_ONBOARDING_DONE = "permissions_onboarding_done";
    private static final String KEY_PREFIX_UNLOCK_UNTIL = "unlock_until_";

    // Real Short-Form / Reels Blocker keys
    private static final String KEY_SHORT_FORM_LOCK_ENABLED = "short_form_lock_enabled";
    private static final String KEY_SHORT_FORM_BLOCKED_APPS = "short_form_blocked_packages";
    private static final String KEY_PREFIX_SHORT_FORM_UNLOCK_UNTIL = "short_form_unlock_until_";

    // Deep Focus Mode keys
    private static final String KEY_DEEP_FOCUS_ENABLED = "deep_focus_enabled";
    private static final String KEY_DEEP_FOCUS_HIDE_PREVIEWS = "deep_focus_hide_previews";
    private static final String KEY_DEEP_FOCUS_BLOCK_INTERNET = "deep_focus_block_internet";
    private static final String KEY_DEEP_FOCUS_ALLOWED_PKGS = "deep_focus_allowed_packages";
    private static final String KEY_DEEP_FOCUS_RUNTIME_ACTIVE = "deep_focus_runtime_active";
    private static final String KEY_DEEP_FOCUS_PRIOR_DND_SAVED = "deep_focus_prior_dnd_saved";
    private static final String KEY_DEEP_FOCUS_PRIOR_FILTER = "deep_focus_prior_filter";
    private static final String KEY_DEEP_FOCUS_PRIOR_PRIORITY_CATS = "deep_focus_prior_priority_cats";
    private static final String KEY_DEEP_FOCUS_PRIOR_CALL_SENDERS = "deep_focus_prior_call_senders";
    private static final String KEY_DEEP_FOCUS_PRIOR_MSG_SENDERS = "deep_focus_prior_msg_senders";
    private static final String KEY_DEEP_FOCUS_PRIOR_SUPPRESSED = "deep_focus_prior_suppressed";
    private static final String KEY_CUSTOM_FOCUS_MINUTES = "custom_focus_duration_min";

    // Gamification & Replay Cache
    private static final String KEY_WORKOUT_STREAK = "workout_streak_days";
    private static final String KEY_LAST_WORKOUT_DAY = "last_workout_day_key";
    private static final String KEY_USER_XP = "user_total_xp";
    private static final String KEY_LATEST_REPLAY_JSON = "latest_replay_json";

    private final SharedPreferences prefs;

    public PreferencesManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Selected Distracting Apps ---

    public Set<String> getSelectedPackages() {
        Set<String> stored = prefs.getStringSet(KEY_SELECTED_APPS, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public void setSelectedPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_SELECTED_APPS, new HashSet<>(packages)).apply();
    }

    public boolean togglePackageSelection(String packageName) {
        Set<String> current = getSelectedPackages();
        boolean nowSelected;
        if (current.contains(packageName)) {
            current.remove(packageName);
            nowSelected = false;
        } else {
            current.add(packageName);
            nowSelected = true;
        }
        setSelectedPackages(current);
        return nowSelected;
    }

    // --- Appearance & Focus Preferences ---

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public int getDefaultDurationMinutes() {
        return prefs.getInt(KEY_DEFAULT_DURATION_MIN, 25);
    }

    public void setDefaultDurationMinutes(int minutes) {
        prefs.edit().putInt(KEY_DEFAULT_DURATION_MIN, minutes).apply();
    }

    // --- Timer State Persistence ---

    public FocusState getFocusState() {
        String raw = prefs.getString(KEY_FOCUS_STATE, FocusState.IDLE.name());
        try {
            return FocusState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return FocusState.IDLE;
        }
    }

    public long getSessionTotalMillis() {
        long defaultMillis = getDefaultDurationMinutes() * 60_000L;
        return prefs.getLong(KEY_SESSION_TOTAL_MILLIS, defaultMillis);
    }

    public long getSessionRemainingMillis() {
        long defaultMillis = getDefaultDurationMinutes() * 60_000L;
        return prefs.getLong(KEY_SESSION_REMAINING_MILLIS, defaultMillis);
    }

    public long getSessionEndEpochMillis() {
        return prefs.getLong(KEY_SESSION_END_EPOCH, 0L);
    }

    public long getSessionStartEpochMillis() {
        return prefs.getLong(KEY_SESSION_START_EPOCH, 0L);
    }

    public int getSessionInterruptions() {
        return prefs.getInt(KEY_SESSION_INTERRUPTIONS, 0);
    }

    public void saveTimerState(
            FocusState state,
            long totalMillis,
            long remainingMillis,
            long startEpochMillis,
            long endEpochMillis,
            int interruptions
    ) {
        prefs.edit()
                .putString(KEY_FOCUS_STATE, state.name())
                .putLong(KEY_SESSION_TOTAL_MILLIS, totalMillis)
                .putLong(KEY_SESSION_REMAINING_MILLIS, remainingMillis)
                .putLong(KEY_SESSION_START_EPOCH, startEpochMillis)
                .putLong(KEY_SESSION_END_EPOCH, endEpochMillis)
                .putInt(KEY_SESSION_INTERRUPTIONS, interruptions)
                .apply();
    }

    public void clearTimerStateToIdle() {
        long defaultMillis = getDefaultDurationMinutes() * 60_000L;
        saveTimerState(FocusState.IDLE, defaultMillis, defaultMillis, 0L, 0L, 0);
    }

    // --- SweatLock Workout-to-Unlock & App Guard ---

    public boolean isAlwaysGuardAppsEnabled() {
        return prefs.getBoolean(KEY_ALWAYS_GUARD_APPS, true);
    }

    public void setAlwaysGuardAppsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALWAYS_GUARD_APPS, enabled).apply();
    }

    public boolean isWorkoutUnlockEnabled() {
        return prefs.getBoolean(KEY_WORKOUT_UNLOCK_ENABLED, true);
    }

    public void setWorkoutUnlockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WORKOUT_UNLOCK_ENABLED, enabled).apply();
    }

    public int getRequiredWorkoutReps() {
        return prefs.getInt(KEY_REQUIRED_REPS, 10);
    }

    public void setRequiredWorkoutReps(int reps) {
        prefs.edit().putInt(KEY_REQUIRED_REPS, Math.max(1, reps)).apply();
    }

    public int getUnlockDurationMinutes() {
        return prefs.getInt(KEY_UNLOCK_DURATION_MIN, 15);
    }

    public void setUnlockDurationMinutes(int minutes) {
        prefs.edit().putInt(KEY_UNLOCK_DURATION_MIN, Math.max(1, minutes)).apply();
    }

    public String getPreferredExerciseName() {
        return prefs.getString(KEY_PREFERRED_EXERCISE, "SQUATS");
    }

    public void setPreferredExerciseName(String exerciseName) {
        prefs.edit().putString(KEY_PREFERRED_EXERCISE, exerciseName).apply();
    }

    public boolean isVoiceCoachEnabled() {
        return prefs.getBoolean(KEY_VOICE_COACH_ENABLED, true);
    }

    public void setVoiceCoachEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOICE_COACH_ENABLED, enabled).apply();
    }

    public boolean isGhostTrainerEnabled() {
        return prefs.getBoolean(KEY_GHOST_TRAINER_ENABLED, true);
    }

    public void setGhostTrainerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GHOST_TRAINER_ENABLED, enabled).apply();
    }

    public boolean isMirrorModeEnabled() {
        return prefs.getBoolean(KEY_MIRROR_MODE_ENABLED, false);
    }

    public void setMirrorModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MIRROR_MODE_ENABLED, enabled).apply();
    }

    public boolean isOnboardingCompleted() {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public void setOnboardingCompleted(boolean completed) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply();
    }

    /**
     * Unlocks a blocked app for the specified number of minutes, then automatically expires.
     */
    public void grantAppUnlock(String packageName, int durationMinutes) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        long unlockUntil = System.currentTimeMillis() + (durationMinutes * 60_000L);
        prefs.edit().putLong(KEY_PREFIX_UNLOCK_UNTIL + packageName, unlockUntil).apply();
    }

    public boolean isAppTemporarilyUnlocked(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        long unlockUntil = prefs.getLong(KEY_PREFIX_UNLOCK_UNTIL + packageName, 0L);
        if (unlockUntil <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() < unlockUntil) {
            return true;
        }
        // Expired -> clean up and re-lock automatically
        prefs.edit().remove(KEY_PREFIX_UNLOCK_UNTIL + packageName).apply();
        return false;
    }

    public int getAppUnlockRemainingMinutes(String packageName) {
        long unlockUntil = prefs.getLong(KEY_PREFIX_UNLOCK_UNTIL + packageName, 0L);
        long diff = unlockUntil - System.currentTimeMillis();
        if (diff <= 0L) {
            return 0;
        }
        return (int) ((diff + 59_999L) / 60_000L);
    }

    public void relockApp(String packageName) {
        prefs.edit().remove(KEY_PREFIX_UNLOCK_UNTIL + packageName).apply();
    }

    // --- Real Short-Form / Reels & Shorts Blocker ---

    public boolean isShortFormBlockerEnabled() {
        return prefs.getBoolean(KEY_SHORT_FORM_LOCK_ENABLED, true);
    }

    public void setShortFormBlockerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHORT_FORM_LOCK_ENABLED, enabled).apply();
    }

    public Set<String> getShortFormBlockedPackages() {
        Set<String> stored = prefs.getStringSet(KEY_SHORT_FORM_BLOCKED_APPS, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public void setShortFormBlockedPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_SHORT_FORM_BLOCKED_APPS, new HashSet<>(packages)).apply();
    }

    public boolean toggleShortFormPackage(String packageName) {
        Set<String> current = getShortFormBlockedPackages();
        boolean nowBlocked;
        if (current.contains(packageName)) {
            current.remove(packageName);
            nowBlocked = false;
        } else {
            current.add(packageName);
            nowBlocked = true;
        }
        setShortFormBlockedPackages(current);
        return nowBlocked;
    }

    public boolean isShortFormBlockedForPackage(String packageName) {
        return packageName != null && getShortFormBlockedPackages().contains(packageName);
    }

    public void grantShortFormUnlock(String packageName, int durationMinutes) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        long unlockUntil = System.currentTimeMillis() + (durationMinutes * 60_000L);
        prefs.edit().putLong(KEY_PREFIX_SHORT_FORM_UNLOCK_UNTIL + packageName, unlockUntil).apply();
    }

    public boolean isShortFormTemporarilyUnlocked(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        long unlockUntil = prefs.getLong(KEY_PREFIX_SHORT_FORM_UNLOCK_UNTIL + packageName, 0L);
        if (unlockUntil <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() < unlockUntil) {
            return true;
        }
        prefs.edit().remove(KEY_PREFIX_SHORT_FORM_UNLOCK_UNTIL + packageName).apply();
        return false;
    }

    public int getShortFormUnlockRemainingMinutes(String packageName) {
        long unlockUntil = prefs.getLong(KEY_PREFIX_SHORT_FORM_UNLOCK_UNTIL + packageName, 0L);
        long diff = unlockUntil - System.currentTimeMillis();
        if (diff <= 0L) {
            return 0;
        }
        return (int) ((diff + 59_999L) / 60_000L);
    }

    public void relockShortForm(String packageName) {
        prefs.edit().remove(KEY_PREFIX_SHORT_FORM_UNLOCK_UNTIL + packageName).apply();
    }

    // --- Deep Focus Mode (No Previews + Local Internet Firewall) ---

    public boolean isDeepFocusEnabled() {
        return prefs.getBoolean(KEY_DEEP_FOCUS_ENABLED, false);
    }

    public void setDeepFocusEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEEP_FOCUS_ENABLED, enabled).apply();
    }

    public boolean isDeepFocusHidePreviewsEnabled() {
        return prefs.getBoolean(KEY_DEEP_FOCUS_HIDE_PREVIEWS, true);
    }

    public void setDeepFocusHidePreviewsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEEP_FOCUS_HIDE_PREVIEWS, enabled).apply();
    }

    public boolean isDeepFocusBlockInternetEnabled() {
        return prefs.getBoolean(KEY_DEEP_FOCUS_BLOCK_INTERNET, true);
    }

    public void setDeepFocusBlockInternetEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEEP_FOCUS_BLOCK_INTERNET, enabled).apply();
    }

    public Set<String> getDeepFocusAllowedPackages() {
        Set<String> stored = prefs.getStringSet(KEY_DEEP_FOCUS_ALLOWED_PKGS, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public void setDeepFocusAllowedPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_DEEP_FOCUS_ALLOWED_PKGS, new HashSet<>(packages)).apply();
    }

    public boolean toggleDeepFocusAllowedPackage(String packageName) {
        Set<String> current = getDeepFocusAllowedPackages();
        boolean nowAllowed;
        if (current.contains(packageName)) {
            current.remove(packageName);
            nowAllowed = false;
        } else {
            current.add(packageName);
            nowAllowed = true;
        }
        setDeepFocusAllowedPackages(current);
        return nowAllowed;
    }

    public int getCustomFocusDurationMinutes() {
        return prefs.getInt(KEY_CUSTOM_FOCUS_MINUTES, 90);
    }

    public void setCustomFocusDurationMinutes(int minutes) {
        int clamped = Math.max(5, Math.min(180, minutes));
        prefs.edit().putInt(KEY_CUSTOM_FOCUS_MINUTES, clamped).apply();
    }

    public boolean isDeepFocusRuntimeActive() {
        return prefs.getBoolean(KEY_DEEP_FOCUS_RUNTIME_ACTIVE, false);
    }

    public void setDeepFocusRuntimeActive(boolean active) {
        prefs.edit().putBoolean(KEY_DEEP_FOCUS_RUNTIME_ACTIVE, active).apply();
    }

    public boolean hasSavedPriorDndSnapshot() {
        return prefs.getBoolean(KEY_DEEP_FOCUS_PRIOR_DND_SAVED, false);
    }

    public void savePriorDndSnapshot(
            int interruptionFilter,
            int priorityCategories,
            int callSenders,
            int messageSenders,
            int suppressedVisualEffects
    ) {
        prefs.edit()
                .putBoolean(KEY_DEEP_FOCUS_PRIOR_DND_SAVED, true)
                .putInt(KEY_DEEP_FOCUS_PRIOR_FILTER, interruptionFilter)
                .putInt(KEY_DEEP_FOCUS_PRIOR_PRIORITY_CATS, priorityCategories)
                .putInt(KEY_DEEP_FOCUS_PRIOR_CALL_SENDERS, callSenders)
                .putInt(KEY_DEEP_FOCUS_PRIOR_MSG_SENDERS, messageSenders)
                .putInt(KEY_DEEP_FOCUS_PRIOR_SUPPRESSED, suppressedVisualEffects)
                .apply();
    }

    public int getPriorDndFilter() {
        return prefs.getInt(KEY_DEEP_FOCUS_PRIOR_FILTER, android.app.NotificationManager.INTERRUPTION_FILTER_ALL);
    }

    public int getPriorDndPriorityCategories() {
        return prefs.getInt(KEY_DEEP_FOCUS_PRIOR_PRIORITY_CATS, 0);
    }

    public int getPriorDndCallSenders() {
        return prefs.getInt(KEY_DEEP_FOCUS_PRIOR_CALL_SENDERS, 0);
    }

    public int getPriorDndMessageSenders() {
        return prefs.getInt(KEY_DEEP_FOCUS_PRIOR_MSG_SENDERS, 0);
    }

    public int getPriorDndSuppressedEffects() {
        return prefs.getInt(KEY_DEEP_FOCUS_PRIOR_SUPPRESSED, 0);
    }

    public void clearPriorDndSnapshot() {
        prefs.edit()
                .remove(KEY_DEEP_FOCUS_PRIOR_DND_SAVED)
                .remove(KEY_DEEP_FOCUS_PRIOR_FILTER)
                .remove(KEY_DEEP_FOCUS_PRIOR_PRIORITY_CATS)
                .remove(KEY_DEEP_FOCUS_PRIOR_CALL_SENDERS)
                .remove(KEY_DEEP_FOCUS_PRIOR_MSG_SENDERS)
                .remove(KEY_DEEP_FOCUS_PRIOR_SUPPRESSED)
                .apply();
    }

    // --- Gamification: Streak, XP, Replay Cache ---

    public int getWorkoutStreakDays() {
        return prefs.getInt(KEY_WORKOUT_STREAK, 0);
    }

    public void recordWorkoutDayCompleted() {
        long now = System.currentTimeMillis();
        String todayKey = TimeFormatter.toDayKey(now);
        String yesterdayKey = TimeFormatter.toDayKey(now - 86_400_000L);
        String lastDay = prefs.getString(KEY_LAST_WORKOUT_DAY, "");
        int streak = prefs.getInt(KEY_WORKOUT_STREAK, 0);

        if (todayKey.equals(lastDay)) {
            if (streak == 0) {
                streak = 1;
            }
        } else if (yesterdayKey.equals(lastDay)) {
            streak = streak + 1;
        } else {
            streak = 1;
        }

        prefs.edit()
                .putString(KEY_LAST_WORKOUT_DAY, todayKey)
                .putInt(KEY_WORKOUT_STREAK, streak)
                .apply();
    }

    public int getUserXp() {
        return prefs.getInt(KEY_USER_XP, 0);
    }

    public int getUserLevel() {
        return 1 + (getUserXp() / 250);
    }

    public void addUserXp(int xpEarned) {
        int current = getUserXp();
        prefs.edit().putInt(KEY_USER_XP, current + Math.max(0, xpEarned)).apply();
    }

    public String getLatestReplayJson() {
        return prefs.getString(KEY_LATEST_REPLAY_JSON, "");
    }

    public void saveLatestReplayJson(String json) {
        prefs.edit().putString(KEY_LATEST_REPLAY_JSON, json == null ? "" : json).apply();
    }

    // --- Study Page-Turn Task Defaults ---

    private static final String KEY_STUDY_TARGET_PAGES = "study_default_target_pages";
    private static final String KEY_STUDY_SUBJECT = "study_default_subject";

    public int getDefaultStudyTargetPages() {
        return prefs.getInt(KEY_STUDY_TARGET_PAGES, 10);
    }

    public void setDefaultStudyTargetPages(int pages) {
        int clamped = Math.max(1, Math.min(200, pages));
        prefs.edit().putInt(KEY_STUDY_TARGET_PAGES, clamped).apply();
    }

    public String getDefaultStudySubject() {
        return prefs.getString(KEY_STUDY_SUBJECT, "Focused Reading");
    }

    public void setDefaultStudySubject(String subject) {
        String clean = (subject == null || subject.trim().isEmpty()) ? "Focused Reading" : subject.trim();
        prefs.edit().putString(KEY_STUDY_SUBJECT, clean).apply();
    }
}
