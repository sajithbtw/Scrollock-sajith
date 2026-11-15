package com.sajith.scrolllock.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.sajith.scrolllock.data.model.FocusTask;
import com.sajith.scrolllock.data.model.StudySessionRecord;
import com.sajith.scrolllock.data.model.TaskCategory;
import com.sajith.scrolllock.data.model.TopAppStat;
import com.sajith.scrolllock.util.TimeFormatter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local SQLite database storing completed focus sessions, app guard stats,
 * workout history, personal bests, and gamification achievement badges.
 * All data stays offline on-device, with JSON export available for optional sync.
 */
public class ScrollLockDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "scrolllock_local.db";
    private static final int DB_VERSION = 3;

    // Focus sessions & app stats (v1)
    private static final String TABLE_SESSIONS = "focus_sessions";
    private static final String COL_ID = "id";
    private static final String COL_START_TIME = "start_time";
    private static final String COL_END_TIME = "end_time";
    private static final String COL_PLANNED_MIN = "planned_minutes";
    private static final String COL_ACTUAL_SEC = "actual_seconds";
    private static final String COL_COMPLETED = "completed";
    private static final String COL_INTERRUPTED_COUNT = "interrupted_count";
    private static final String COL_DAY_KEY = "day_key";

    private static final String TABLE_APP_STATS = "app_stats";
    private static final String COL_PKG = "package_name";
    private static final String COL_APP_NAME = "app_name";
    private static final String COL_TIMES_SELECTED = "times_selected";
    private static final String COL_TIMES_INTERRUPTED = "times_interrupted";

    // Workout history, personal bests & achievements (v2)
    private static final String TABLE_WORKOUTS = "workout_sessions";
    private static final String COL_W_TIMESTAMP = "timestamp_ms";
    private static final String COL_W_EXERCISE = "exercise_type";
    private static final String COL_W_REPS = "reps_completed";
    private static final String COL_W_INVALID = "invalid_reps";
    private static final String COL_W_FORM = "form_score";
    private static final String COL_W_DURATION = "duration_seconds";
    private static final String COL_W_BALANCE = "balance_percent";
    private static final String COL_W_FATIGUE = "fatigue_level";
    private static final String COL_W_XP = "xp_earned";

    private static final String TABLE_PERSONAL_BESTS = "personal_bests";
    private static final String COL_PB_EXERCISE = "exercise_type";
    private static final String COL_PB_REPS = "best_reps";
    private static final String COL_PB_FORM = "best_form_score";
    private static final String COL_PB_UPDATED = "updated_at";

    private static final String TABLE_ACHIEVEMENTS = "achievements";
    private static final String COL_ACH_ID = "badge_id";
    private static final String COL_ACH_TITLE = "title";
    private static final String COL_ACH_DESC = "description";
    private static final String COL_ACH_UNLOCKED = "unlocked_at";

    // Reminders, Alarm-Based Tasks & Study Page Tasks (v3)
    private static final String TABLE_TASKS = "focus_tasks";
    private static final String COL_T_TITLE = "title";
    private static final String COL_T_NOTES = "notes";
    private static final String COL_T_CATEGORY = "category";
    private static final String COL_T_TRIGGER_MS = "trigger_time_ms";
    private static final String COL_T_HOUR = "hour_of_day";
    private static final String COL_T_MINUTE = "minute_of_hour";
    private static final String COL_T_REPEAT_MASK = "repeat_days_mask";
    private static final String COL_T_ENABLED = "is_enabled";
    private static final String COL_T_COMPLETED = "is_completed";
    private static final String COL_T_COMPLETED_AT = "completed_at_ms";
    private static final String COL_T_EXERCISE = "linked_exercise";
    private static final String COL_T_TARGET_COUNT = "target_count";
    private static final String COL_T_COMPLETED_COUNT = "completed_count";
    private static final String COL_T_REQUIRE_DISMISS = "require_completion_dismiss";
    private static final String COL_T_CREATED_AT = "created_at_ms";

    private static final String TABLE_STUDY_SESSIONS = "study_sessions";
    private static final String COL_S_TASK_ID = "task_id";
    private static final String COL_S_SUBJECT = "subject_title";
    private static final String COL_S_TARGET_PAGES = "target_pages";
    private static final String COL_S_PAGES_TURNED = "pages_turned";
    private static final String COL_S_DURATION_SEC = "duration_seconds";
    private static final String COL_S_AVG_SEC_PAGE = "avg_sec_per_page";
    private static final String COL_S_XP = "xp_earned";
    private static final String COL_S_TIMESTAMP = "timestamp_ms";

    public static class WorkoutHistoryItem {
        public final String exerciseType;
        public final int repsCompleted;
        public final int invalidReps;
        public final int formScore;
        public final int durationSeconds;
        public final int balancePercent;
        public final String fatigueLevel;
        public final int xpEarned;
        public final String dayKey;
        public final long timestampMs;

        public WorkoutHistoryItem(String exerciseType, int repsCompleted, int invalidReps,
                                  int formScore, int durationSeconds, int balancePercent,
                                  String fatigueLevel, int xpEarned, String dayKey, long timestampMs) {
            this.exerciseType = exerciseType;
            this.repsCompleted = repsCompleted;
            this.invalidReps = invalidReps;
            this.formScore = formScore;
            this.durationSeconds = durationSeconds;
            this.balancePercent = balancePercent;
            this.fatigueLevel = fatigueLevel;
            this.xpEarned = xpEarned;
            this.dayKey = dayKey;
            this.timestampMs = timestampMs;
        }
    }

    public static class PersonalBestItem {
        public final String exerciseType;
        public final int bestReps;
        public final int bestFormScore;

        public PersonalBestItem(String exerciseType, int bestReps, int bestFormScore) {
            this.exerciseType = exerciseType;
            this.bestReps = bestReps;
            this.bestFormScore = bestFormScore;
        }
    }

    public static class AchievementBadge {
        public final String id;
        public final String title;
        public final String description;
        public final long unlockedAt;

        public AchievementBadge(String id, String title, String description, long unlockedAt) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.unlockedAt = unlockedAt;
        }
    }

    private static ScrollLockDatabaseHelper instance;

    public static synchronized ScrollLockDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ScrollLockDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private ScrollLockDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SESSIONS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_START_TIME + " INTEGER NOT NULL, "
                + COL_END_TIME + " INTEGER NOT NULL, "
                + COL_PLANNED_MIN + " INTEGER NOT NULL, "
                + COL_ACTUAL_SEC + " INTEGER NOT NULL, "
                + COL_COMPLETED + " INTEGER NOT NULL, "
                + COL_INTERRUPTED_COUNT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_DAY_KEY + " TEXT NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_APP_STATS + " ("
                + COL_PKG + " TEXT PRIMARY KEY, "
                + COL_APP_NAME + " TEXT NOT NULL, "
                + COL_TIMES_SELECTED + " INTEGER NOT NULL DEFAULT 0, "
                + COL_TIMES_INTERRUPTED + " INTEGER NOT NULL DEFAULT 0)");

        createWorkoutTables(db);
        createTaskAndStudyTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createWorkoutTables(db);
        }
        if (oldVersion < 3) {
            createTaskAndStudyTables(db);
        }
    }

    private void createWorkoutTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_WORKOUTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_W_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_W_EXERCISE + " TEXT NOT NULL, "
                + COL_W_REPS + " INTEGER NOT NULL, "
                + COL_W_INVALID + " INTEGER NOT NULL DEFAULT 0, "
                + COL_W_FORM + " INTEGER NOT NULL DEFAULT 100, "
                + COL_W_DURATION + " INTEGER NOT NULL DEFAULT 0, "
                + COL_W_BALANCE + " INTEGER NOT NULL DEFAULT 100, "
                + COL_W_FATIGUE + " TEXT NOT NULL DEFAULT 'Fresh', "
                + COL_W_XP + " INTEGER NOT NULL DEFAULT 0, "
                + COL_DAY_KEY + " TEXT NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PERSONAL_BESTS + " ("
                + COL_PB_EXERCISE + " TEXT PRIMARY KEY, "
                + COL_PB_REPS + " INTEGER NOT NULL DEFAULT 0, "
                + COL_PB_FORM + " INTEGER NOT NULL DEFAULT 0, "
                + COL_PB_UPDATED + " INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ACHIEVEMENTS + " ("
                + COL_ACH_ID + " TEXT PRIMARY KEY, "
                + COL_ACH_TITLE + " TEXT NOT NULL, "
                + COL_ACH_DESC + " TEXT NOT NULL, "
                + COL_ACH_UNLOCKED + " INTEGER NOT NULL)");
    }

    private void createTaskAndStudyTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TASKS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_T_TITLE + " TEXT NOT NULL, "
                + COL_T_NOTES + " TEXT NOT NULL DEFAULT '', "
                + COL_T_CATEGORY + " TEXT NOT NULL, "
                + COL_T_TRIGGER_MS + " INTEGER NOT NULL DEFAULT 0, "
                + COL_T_HOUR + " INTEGER NOT NULL DEFAULT 9, "
                + COL_T_MINUTE + " INTEGER NOT NULL DEFAULT 0, "
                + COL_T_REPEAT_MASK + " INTEGER NOT NULL DEFAULT 0, "
                + COL_T_ENABLED + " INTEGER NOT NULL DEFAULT 1, "
                + COL_T_COMPLETED + " INTEGER NOT NULL DEFAULT 0, "
                + COL_T_COMPLETED_AT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_T_EXERCISE + " TEXT NOT NULL DEFAULT 'SQUATS', "
                + COL_T_TARGET_COUNT + " INTEGER NOT NULL DEFAULT 10, "
                + COL_T_COMPLETED_COUNT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_T_REQUIRE_DISMISS + " INTEGER NOT NULL DEFAULT 0, "
                + COL_T_CREATED_AT + " INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_STUDY_SESSIONS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_S_TASK_ID + " INTEGER NOT NULL DEFAULT -1, "
                + COL_S_SUBJECT + " TEXT NOT NULL, "
                + COL_S_TARGET_PAGES + " INTEGER NOT NULL DEFAULT 10, "
                + COL_S_PAGES_TURNED + " INTEGER NOT NULL DEFAULT 0, "
                + COL_S_DURATION_SEC + " INTEGER NOT NULL DEFAULT 0, "
                + COL_S_AVG_SEC_PAGE + " REAL NOT NULL DEFAULT 0, "
                + COL_S_XP + " INTEGER NOT NULL DEFAULT 0, "
                + COL_DAY_KEY + " TEXT NOT NULL, "
                + COL_S_TIMESTAMP + " INTEGER NOT NULL)");
    }

    // --- Focus Sessions & Guard Stats ---

    public synchronized void insertCompletedSession(
            long startTimeMillis,
            long endTimeMillis,
            int plannedMinutes,
            int actualSeconds,
            int interruptedCount
    ) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_START_TIME, startTimeMillis);
        values.put(COL_END_TIME, endTimeMillis);
        values.put(COL_PLANNED_MIN, plannedMinutes);
        values.put(COL_ACTUAL_SEC, actualSeconds);
        values.put(COL_COMPLETED, 1);
        values.put(COL_INTERRUPTED_COUNT, interruptedCount);
        values.put(COL_DAY_KEY, TimeFormatter.toDayKey(endTimeMillis));
        db.insert(TABLE_SESSIONS, null, values);
    }

    public synchronized void recordAppSelected(String packageName, String appName) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.query(
                TABLE_APP_STATS,
                new String[]{COL_TIMES_SELECTED, COL_TIMES_INTERRUPTED},
                COL_PKG + "=?",
                new String[]{packageName},
                null, null, null
        );

        ContentValues values = new ContentValues();
        values.put(COL_PKG, packageName);
        values.put(COL_APP_NAME, appName);

        if (cursor.moveToFirst()) {
            values.put(COL_TIMES_SELECTED, cursor.getInt(0) + 1);
            values.put(COL_TIMES_INTERRUPTED, cursor.getInt(1));
            db.update(TABLE_APP_STATS, values, COL_PKG + "=?", new String[]{packageName});
        } else {
            values.put(COL_TIMES_SELECTED, 1);
            values.put(COL_TIMES_INTERRUPTED, 0);
            db.insert(TABLE_APP_STATS, null, values);
        }
        cursor.close();
    }

    public synchronized void recordAppInterrupted(String packageName, String appName) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.query(
                TABLE_APP_STATS,
                new String[]{COL_TIMES_SELECTED, COL_TIMES_INTERRUPTED},
                COL_PKG + "=?",
                new String[]{packageName},
                null, null, null
        );

        ContentValues values = new ContentValues();
        values.put(COL_PKG, packageName);
        values.put(COL_APP_NAME, appName);

        if (cursor.moveToFirst()) {
            values.put(COL_TIMES_SELECTED, cursor.getInt(0));
            values.put(COL_TIMES_INTERRUPTED, cursor.getInt(1) + 1);
            db.update(TABLE_APP_STATS, values, COL_PKG + "=?", new String[]{packageName});
        } else {
            values.put(COL_TIMES_SELECTED, 1);
            values.put(COL_TIMES_INTERRUPTED, 1);
            db.insert(TABLE_APP_STATS, null, values);
        }
        cursor.close();
    }

    public synchronized int getTotalFocusSeconds() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(" + COL_ACTUAL_SEC + "), 0) FROM " + TABLE_SESSIONS + " WHERE " + COL_COMPLETED + "=1",
                null
        );
        int total = cursor.moveToFirst() ? cursor.getInt(0) : 0;
        cursor.close();
        return total;
    }

    public synchronized int getCompletedSessionsCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_SESSIONS + " WHERE " + COL_COMPLETED + "=1",
                null
        );
        int count = cursor.moveToFirst() ? cursor.getInt(0) : 0;
        cursor.close();
        return count;
    }

    public synchronized int getTotalInterruptionsCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(" + COL_TIMES_INTERRUPTED + "), 0) FROM " + TABLE_APP_STATS,
                null
        );
        int count = cursor.moveToFirst() ? cursor.getInt(0) : 0;
        cursor.close();
        return count;
    }

    public synchronized Map<String, Integer> getLast7DaysFocusMinutes() {
        Map<String, Integer> result = new LinkedHashMap<>();
        SQLiteDatabase db = getReadableDatabase();

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);

        for (int i = 0; i < 7; i++) {
            long dayMillis = cal.getTimeInMillis();
            String dayKey = TimeFormatter.toDayKey(dayMillis);
            String label = TimeFormatter.toShortDayLabel(dayMillis);

            Cursor cursor = db.rawQuery(
                    "SELECT COALESCE(SUM(" + COL_ACTUAL_SEC + "), 0) FROM " + TABLE_SESSIONS
                            + " WHERE " + COL_COMPLETED + "=1 AND " + COL_DAY_KEY + "=?",
                    new String[]{dayKey}
            );
            int seconds = cursor.moveToFirst() ? cursor.getInt(0) : 0;
            cursor.close();

            int minutes = seconds > 0 ? Math.max(1, (seconds + 30) / 60) : 0;
            result.put(label, minutes);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        return result;
    }

    public synchronized List<TopAppStat> getTopDistractingApps(int limit) {
        List<TopAppStat> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COL_PKG + ", " + COL_APP_NAME + ", " + COL_TIMES_SELECTED + ", " + COL_TIMES_INTERRUPTED
                        + " FROM " + TABLE_APP_STATS
                        + " ORDER BY (" + COL_TIMES_INTERRUPTED + " * 2 + " + COL_TIMES_SELECTED + ") DESC"
                        + " LIMIT ?",
                new String[]{String.valueOf(limit)}
        );

        while (cursor.moveToNext()) {
            list.add(new TopAppStat(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getInt(2),
                    cursor.getInt(3)
            ));
        }
        cursor.close();
        return list;
    }

    // --- Workout Sessions, Personal Bests & Gamification ---

    public synchronized int insertWorkoutSession(
            String exerciseType,
            int repsCompleted,
            int invalidReps,
            int formScore,
            int durationSeconds,
            int balancePercent,
            String fatigueLevel,
            boolean unlockedApp
    ) {
        long now = System.currentTimeMillis();
        String dayKey = TimeFormatter.toDayKey(now);
        int xpEarned = Math.max(10, repsCompleted * 5 + (formScore / 5) + (unlockedApp ? 25 : 0));

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_W_TIMESTAMP, now);
        values.put(COL_W_EXERCISE, exerciseType);
        values.put(COL_W_REPS, repsCompleted);
        values.put(COL_W_INVALID, invalidReps);
        values.put(COL_W_FORM, formScore);
        values.put(COL_W_DURATION, durationSeconds);
        values.put(COL_W_BALANCE, balancePercent);
        values.put(COL_W_FATIGUE, fatigueLevel);
        values.put(COL_W_XP, xpEarned);
        values.put(COL_DAY_KEY, dayKey);
        db.insert(TABLE_WORKOUTS, null, values);

        updatePersonalBest(db, exerciseType, repsCompleted, formScore, now);
        evaluateAndUnlockAchievements(db, exerciseType, repsCompleted, formScore, balancePercent, unlockedApp, now);
        return xpEarned;
    }

    private void updatePersonalBest(SQLiteDatabase db, String exerciseType, int reps, int formScore, long now) {
        Cursor c = db.query(TABLE_PERSONAL_BESTS, new String[]{COL_PB_REPS, COL_PB_FORM},
                COL_PB_EXERCISE + "=?", new String[]{exerciseType}, null, null, null);
        ContentValues cv = new ContentValues();
        cv.put(COL_PB_EXERCISE, exerciseType);
        cv.put(COL_PB_UPDATED, now);
        if (c.moveToFirst()) {
            cv.put(COL_PB_REPS, Math.max(c.getInt(0), reps));
            cv.put(COL_PB_FORM, Math.max(c.getInt(1), formScore));
            db.update(TABLE_PERSONAL_BESTS, cv, COL_PB_EXERCISE + "=?", new String[]{exerciseType});
        } else {
            cv.put(COL_PB_REPS, reps);
            cv.put(COL_PB_FORM, formScore);
            db.insert(TABLE_PERSONAL_BESTS, null, cv);
        }
        c.close();
    }

    private void evaluateAndUnlockAchievements(
            SQLiteDatabase db,
            String exerciseType,
            int reps,
            int formScore,
            int balancePercent,
            boolean unlockedApp,
            long now
    ) {
        unlockBadge(db, "first_workout", "First Sweat", "Completed your first AI-tracked workout.", now);
        if (unlockedApp) {
            unlockBadge(db, "lock_breaker", "Scroll Breaker", "Unlocked a guarded app through exercise.", now);
        }
        if (formScore >= 90 && reps >= 5) {
            unlockBadge(db, "form_master", "Form Master", "Scored 90%+ posture quality in a session.", now);
        }
        if (balancePercent >= 92 && reps >= 5) {
            unlockBadge(db, "balanced_mover", "Balanced Mover", "Maintained 92%+ left/right symmetry.", now);
        }
        if ("SQUATS".equalsIgnoreCase(exerciseType) && reps >= 10) {
            unlockBadge(db, "squat_specialist", "Squat Specialist", "Completed 10+ validated squats.", now);
        }
        if ("PUSHUPS".equalsIgnoreCase(exerciseType) && reps >= 10) {
            unlockBadge(db, "pushup_specialist", "Push-up Pro", "Completed 10+ validated push-ups.", now);
        }
        if (getTotalWorkoutRepsInternal(db) >= 50) {
            unlockBadge(db, "fifty_club", "50 Rep Club", "Reached 50 total validated reps.", now);
        }
    }

    private void unlockBadge(SQLiteDatabase db, String id, String title, String desc, long now) {
        ContentValues cv = new ContentValues();
        cv.put(COL_ACH_ID, id);
        cv.put(COL_ACH_TITLE, title);
        cv.put(COL_ACH_DESC, desc);
        cv.put(COL_ACH_UNLOCKED, now);
        db.insertWithOnConflict(TABLE_ACHIEVEMENTS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private int getTotalWorkoutRepsInternal(SQLiteDatabase db) {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + COL_W_REPS + "), 0) FROM " + TABLE_WORKOUTS, null);
        int total = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return total;
    }

    public synchronized int getTotalWorkoutReps() {
        return getTotalWorkoutRepsInternal(getReadableDatabase());
    }

    public synchronized int getTodayWorkoutReps() {
        String today = TimeFormatter.toDayKey(System.currentTimeMillis());
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(" + COL_W_REPS + "), 0) FROM " + TABLE_WORKOUTS + " WHERE " + COL_DAY_KEY + "=?",
                new String[]{today}
        );
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    public synchronized int getWorkoutSessionsCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE_WORKOUTS, null);
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    public synchronized int getAverageWorkoutFormScore() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(AVG(" + COL_W_FORM + "), 0) FROM " + TABLE_WORKOUTS, null);
        int avg = c.moveToFirst() ? Math.round(c.getFloat(0)) : 0;
        c.close();
        return avg;
    }

    public synchronized int getTotalInvalidReps() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(" + COL_W_INVALID + "), 0) FROM " + TABLE_WORKOUTS, null);
        int total = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return total;
    }

    public synchronized List<WorkoutHistoryItem> getRecentWorkouts(int limit) {
        List<WorkoutHistoryItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COL_W_EXERCISE + ", " + COL_W_REPS + ", " + COL_W_INVALID + ", " + COL_W_FORM + ", "
                        + COL_W_DURATION + ", " + COL_W_BALANCE + ", " + COL_W_FATIGUE + ", " + COL_W_XP + ", "
                        + COL_DAY_KEY + ", " + COL_W_TIMESTAMP
                        + " FROM " + TABLE_WORKOUTS
                        + " ORDER BY " + COL_W_TIMESTAMP + " DESC LIMIT ?",
                new String[]{String.valueOf(limit)}
        );
        while (c.moveToNext()) {
            list.add(new WorkoutHistoryItem(
                    c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3),
                    c.getInt(4), c.getInt(5), c.getString(6), c.getInt(7),
                    c.getString(8), c.getLong(9)
            ));
        }
        c.close();
        return list;
    }

    public synchronized List<PersonalBestItem> getPersonalBests() {
        List<PersonalBestItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COL_PB_EXERCISE + ", " + COL_PB_REPS + ", " + COL_PB_FORM
                        + " FROM " + TABLE_PERSONAL_BESTS + " ORDER BY " + COL_PB_REPS + " DESC",
                null
        );
        while (c.moveToNext()) {
            list.add(new PersonalBestItem(c.getString(0), c.getInt(1), c.getInt(2)));
        }
        c.close();
        return list;
    }

    public synchronized List<AchievementBadge> getUnlockedAchievements() {
        List<AchievementBadge> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COL_ACH_ID + ", " + COL_ACH_TITLE + ", " + COL_ACH_DESC + ", " + COL_ACH_UNLOCKED
                        + " FROM " + TABLE_ACHIEVEMENTS + " ORDER BY " + COL_ACH_UNLOCKED + " DESC",
                null
        );
        while (c.moveToNext()) {
            list.add(new AchievementBadge(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        c.close();
        return list;
    }

    /**
     * Produces a portable JSON snapshot of local workout history for offline backup or optional cloud sync.
     */
    public synchronized String exportOfflineSummaryJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("totalReps", getTotalWorkoutReps());
            root.put("avgFormScore", getAverageWorkoutFormScore());
            root.put("sessionsCount", getWorkoutSessionsCount());
            JSONArray array = new JSONArray();
            for (WorkoutHistoryItem item : getRecentWorkouts(20)) {
                JSONObject obj = new JSONObject();
                obj.put("exercise", item.exerciseType);
                obj.put("reps", item.repsCompleted);
                obj.put("formScore", item.formScore);
                obj.put("balance", item.balancePercent);
                obj.put("dayKey", item.dayKey);
                array.put(obj);
            }
            root.put("totalStudyPages", getTotalStudyPages());
            root.put("recentWorkouts", array);
            return root.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    // --- Reminders, Alarm Tasks & Study Sessions ---

    public synchronized long insertTask(FocusTask task) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = taskToContentValues(task);
        return db.insert(TABLE_TASKS, null, cv);
    }

    public synchronized void updateTask(FocusTask task) {
        if (task.getId() <= 0L) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = taskToContentValues(task);
        db.update(TABLE_TASKS, cv, COL_ID + "=?", new String[]{String.valueOf(task.getId())});
    }

    public synchronized void setTaskEnabled(long taskId, boolean enabled, long nextTriggerMillis) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_T_ENABLED, enabled ? 1 : 0);
        if (nextTriggerMillis > 0L) {
            cv.put(COL_T_TRIGGER_MS, nextTriggerMillis);
        }
        db.update(TABLE_TASKS, cv, COL_ID + "=?", new String[]{String.valueOf(taskId)});
    }

    public synchronized void markTaskCompleted(long taskId, int completedCount) {
        if (taskId <= 0L) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_T_COMPLETED, 1);
        cv.put(COL_T_COMPLETED_AT, System.currentTimeMillis());
        cv.put(COL_T_COMPLETED_COUNT, Math.max(0, completedCount));
        db.update(TABLE_TASKS, cv, COL_ID + "=?", new String[]{String.valueOf(taskId)});
    }

    public synchronized void setTaskCompletedState(long taskId, boolean completed) {
        if (taskId <= 0L) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_T_COMPLETED, completed ? 1 : 0);
        cv.put(COL_T_COMPLETED_AT, completed ? System.currentTimeMillis() : 0L);
        db.update(TABLE_TASKS, cv, COL_ID + "=?", new String[]{String.valueOf(taskId)});
    }

    public synchronized void updateTaskProgress(long taskId, int completedCount, boolean completed) {
        if (taskId <= 0L) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_T_COMPLETED_COUNT, Math.max(0, completedCount));
        cv.put(COL_T_COMPLETED, completed ? 1 : 0);
        if (completed) {
            cv.put(COL_T_COMPLETED_AT, System.currentTimeMillis());
        }
        db.update(TABLE_TASKS, cv, COL_ID + "=?", new String[]{String.valueOf(taskId)});
    }

    public synchronized void deleteTask(long taskId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_TASKS, COL_ID + "=?", new String[]{String.valueOf(taskId)});
    }

    public synchronized FocusTask getTaskById(long taskId) {
        Cursor c = getReadableDatabase().query(
                TABLE_TASKS, null, COL_ID + "=?", new String[]{String.valueOf(taskId)}, null, null, null
        );
        FocusTask result = c.moveToFirst() ? cursorToTask(c) : null;
        c.close();
        return result;
    }

    public synchronized List<FocusTask> getAllTasks() {
        List<FocusTask> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE_TASKS
                        + " ORDER BY " + COL_T_COMPLETED + " ASC, "
                        + COL_T_ENABLED + " DESC, "
                        + COL_T_TRIGGER_MS + " ASC, "
                        + COL_ID + " DESC",
                null
        );
        while (c.moveToNext()) {
            list.add(cursorToTask(c));
        }
        c.close();
        return list;
    }

    public synchronized int insertStudySession(
            long taskId,
            String subjectTitle,
            int targetPages,
            int pagesTurned,
            int durationSeconds
    ) {
        long now = System.currentTimeMillis();
        String dayKey = TimeFormatter.toDayKey(now);
        float avgSec = pagesTurned > 0 ? ((float) durationSeconds / (float) pagesTurned) : 0f;
        boolean goalMet = pagesTurned >= Math.max(1, targetPages);
        int xpEarned = Math.max(10, pagesTurned * 12 + (goalMet ? 30 : 0));

        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_S_TASK_ID, taskId);
        cv.put(COL_S_SUBJECT, subjectTitle == null ? "Study Session" : subjectTitle);
        cv.put(COL_S_TARGET_PAGES, Math.max(1, targetPages));
        cv.put(COL_S_PAGES_TURNED, Math.max(0, pagesTurned));
        cv.put(COL_S_DURATION_SEC, Math.max(1, durationSeconds));
        cv.put(COL_S_AVG_SEC_PAGE, avgSec);
        cv.put(COL_S_XP, xpEarned);
        cv.put(COL_DAY_KEY, dayKey);
        cv.put(COL_S_TIMESTAMP, now);
        db.insert(TABLE_STUDY_SESSIONS, null, cv);

        if (taskId > 0L) {
            FocusTask existing = getTaskById(taskId);
            if (existing != null) {
                int newTotal = existing.getCompletedCount() + Math.max(0, pagesTurned);
                updateTaskProgress(taskId, newTotal, newTotal >= existing.getTargetCount() || goalMet);
            }
        }

        if (pagesTurned >= 1) {
            unlockBadge(db, "first_study", "Focused Reader", "Completed your first camera-tracked study session.", now);
        }
        if (getTotalStudyPagesInternal(db) >= 25) {
            unlockBadge(db, "page_turner_25", "Page Turner", "Studied 25+ physical pages cleanly.", now);
        }
        return xpEarned;
    }

    private int getTotalStudyPagesInternal(SQLiteDatabase db) {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + COL_S_PAGES_TURNED + "), 0) FROM " + TABLE_STUDY_SESSIONS, null);
        int total = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return total;
    }

    public synchronized int getTotalStudyPages() {
        return getTotalStudyPagesInternal(getReadableDatabase());
    }

    public synchronized int getTodayStudyPages() {
        String today = TimeFormatter.toDayKey(System.currentTimeMillis());
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(" + COL_S_PAGES_TURNED + "), 0) FROM " + TABLE_STUDY_SESSIONS
                        + " WHERE " + COL_DAY_KEY + "=?",
                new String[]{today}
        );
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    public synchronized List<StudySessionRecord> getRecentStudySessions(int limit) {
        List<StudySessionRecord> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COL_ID + ", " + COL_S_TASK_ID + ", " + COL_S_SUBJECT + ", "
                        + COL_S_TARGET_PAGES + ", " + COL_S_PAGES_TURNED + ", " + COL_S_DURATION_SEC + ", "
                        + COL_S_AVG_SEC_PAGE + ", " + COL_S_XP + ", " + COL_DAY_KEY + ", " + COL_S_TIMESTAMP
                        + " FROM " + TABLE_STUDY_SESSIONS
                        + " ORDER BY " + COL_S_TIMESTAMP + " DESC LIMIT ?",
                new String[]{String.valueOf(limit)}
        );
        while (c.moveToNext()) {
            list.add(new StudySessionRecord(
                    c.getLong(0), c.getLong(1), c.getString(2),
                    c.getInt(3), c.getInt(4), c.getInt(5),
                    c.getFloat(6), c.getInt(7), c.getString(8), c.getLong(9)
            ));
        }
        c.close();
        return list;
    }

    private ContentValues taskToContentValues(FocusTask task) {
        ContentValues cv = new ContentValues();
        cv.put(COL_T_TITLE, task.getTitle());
        cv.put(COL_T_NOTES, task.getNotes());
        cv.put(COL_T_CATEGORY, task.getCategory().name());
        cv.put(COL_T_TRIGGER_MS, task.getTriggerTimeMillis());
        cv.put(COL_T_HOUR, task.getHourOfDay());
        cv.put(COL_T_MINUTE, task.getMinuteOfHour());
        cv.put(COL_T_REPEAT_MASK, task.getRepeatDaysMask());
        cv.put(COL_T_ENABLED, task.isEnabled() ? 1 : 0);
        cv.put(COL_T_COMPLETED, task.isCompleted() ? 1 : 0);
        cv.put(COL_T_COMPLETED_AT, task.getCompletedAtMillis());
        cv.put(COL_T_EXERCISE, task.getLinkedExercise());
        cv.put(COL_T_TARGET_COUNT, task.getTargetCount());
        cv.put(COL_T_COMPLETED_COUNT, task.getCompletedCount());
        cv.put(COL_T_REQUIRE_DISMISS, task.getRequireCompletionToDismiss() ? 1 : 0);
        cv.put(COL_T_CREATED_AT, task.getCreatedAtMillis());
        return cv;
    }

    private FocusTask cursorToTask(Cursor c) {
        TaskCategory category;
        try {
            category = TaskCategory.valueOf(c.getString(c.getColumnIndexOrThrow(COL_T_CATEGORY)));
        } catch (Exception e) {
            category = TaskCategory.REMINDER;
        }
        return new FocusTask(
                c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_T_TITLE)),
                c.getString(c.getColumnIndexOrThrow(COL_T_NOTES)),
                category,
                c.getLong(c.getColumnIndexOrThrow(COL_T_TRIGGER_MS)),
                c.getInt(c.getColumnIndexOrThrow(COL_T_HOUR)),
                c.getInt(c.getColumnIndexOrThrow(COL_T_MINUTE)),
                c.getInt(c.getColumnIndexOrThrow(COL_T_REPEAT_MASK)),
                c.getInt(c.getColumnIndexOrThrow(COL_T_ENABLED)) == 1,
                c.getInt(c.getColumnIndexOrThrow(COL_T_COMPLETED)) == 1,
                c.getLong(c.getColumnIndexOrThrow(COL_T_COMPLETED_AT)),
                c.getString(c.getColumnIndexOrThrow(COL_T_EXERCISE)),
                c.getInt(c.getColumnIndexOrThrow(COL_T_TARGET_COUNT)),
                c.getInt(c.getColumnIndexOrThrow(COL_T_COMPLETED_COUNT)),
                c.getInt(c.getColumnIndexOrThrow(COL_T_REQUIRE_DISMISS)) == 1,
                c.getLong(c.getColumnIndexOrThrow(COL_T_CREATED_AT))
        );
    }
}
