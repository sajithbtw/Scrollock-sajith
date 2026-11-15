package com.sajith.scrolllock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper;
import com.sajith.scrolllock.data.model.FocusState;
import com.sajith.scrolllock.data.model.TopAppStat;
import com.sajith.scrolllock.service.FocusSessionManager;
import com.sajith.scrolllock.util.TimeFormatter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Local Robolectric & unit tests verifying ScrollLock persistence, SQLite analytics,
 * timer state transitions, and time formatting.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScrollLockCoreTest {

    @Test
    public void testPreferencesPersistence() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferencesManager prefs = new PreferencesManager(context);

        assertTrue(prefs.togglePackageSelection("com.instagram.android"));
        assertTrue(prefs.togglePackageSelection("com.google.android.youtube"));
        Set<String> selected = prefs.getSelectedPackages();
        assertEquals(2, selected.size());
        assertTrue(selected.contains("com.instagram.android"));

        assertFalse(prefs.togglePackageSelection("com.instagram.android"));
        assertEquals(1, prefs.getSelectedPackages().size());

        prefs.setThemeMode(PreferencesManager.THEME_DARK);
        assertEquals(PreferencesManager.THEME_DARK, prefs.getThemeMode());

        prefs.setDefaultDurationMinutes(45);
        assertEquals(45, prefs.getDefaultDurationMinutes());

        assertFalse(prefs.isOnboardingCompleted());
        prefs.setOnboardingCompleted(true);
        assertTrue(prefs.isOnboardingCompleted());
        assertTrue(prefs.isAlwaysGuardAppsEnabled());
    }

    @Test
    public void testDatabaseRecordingAndQueries() {
        Context context = ApplicationProvider.getApplicationContext();
        ScrollLockDatabaseHelper db = ScrollLockDatabaseHelper.getInstance(context);

        long now = System.currentTimeMillis();
        db.insertCompletedSession(now - 1500_000L, now, 25, 1500, 2);
        db.recordAppSelected("com.instagram.android", "Instagram");
        db.recordAppInterrupted("com.instagram.android", "Instagram");

        assertTrue(db.getCompletedSessionsCount() >= 1);
        assertTrue(db.getTotalFocusSeconds() >= 1500);
        assertTrue(db.getTotalInterruptionsCount() >= 1);

        Map<String, Integer> weekly = db.getLast7DaysFocusMinutes();
        assertEquals(7, weekly.size());

        List<TopAppStat> topApps = db.getTopDistractingApps(5);
        assertFalse(topApps.isEmpty());
        assertEquals("Instagram", topApps.get(0).getAppName());
    }

    @Test
    public void testFocusSessionLifecycle() {
        Context context = ApplicationProvider.getApplicationContext();
        FocusSessionManager manager = FocusSessionManager.getInstance(context);

        manager.resetSession();
        assertEquals(FocusState.IDLE, manager.getCurrentStateValue());

        manager.startNewSession(25);
        assertEquals(FocusState.RUNNING, manager.getCurrentStateValue());
        assertEquals(25 * 60_000L, manager.getCurrentTotalMillisValue());

        manager.pauseSession();
        assertEquals(FocusState.PAUSED, manager.getCurrentStateValue());

        manager.resumeSession();
        assertEquals(FocusState.RUNNING, manager.getCurrentStateValue());

        manager.resetSession();
        assertEquals(FocusState.IDLE, manager.getCurrentStateValue());
    }

    @Test
    public void testTimeFormatter() {
        assertEquals("25:00", TimeFormatter.formatCountdown(25 * 60_000L));
        assertEquals("00:05", TimeFormatter.formatCountdown(4_500L));
        assertEquals("1h 15m", TimeFormatter.formatDurationShort(75 * 60_000L));
        assertEquals("< 1m", TimeFormatter.formatDurationShort(15_000L));
        assertEquals("2h 5m", TimeFormatter.formatFocusMinutesSummary(125 * 60));
    }

    @Test
    public void testWorkoutUnlockTimersAndDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferencesManager prefs = new PreferencesManager(context);
        ScrollLockDatabaseHelper db = ScrollLockDatabaseHelper.getInstance(context);

        String pkg = "com.instagram.android";
        prefs.relockApp(pkg);
        assertFalse(prefs.isAppTemporarilyUnlocked(pkg));

        prefs.grantAppUnlock(pkg, 15);
        assertTrue(prefs.isAppTemporarilyUnlocked(pkg));
        assertTrue(prefs.getAppUnlockRemainingMinutes(pkg) >= 14);

        prefs.relockApp(pkg);
        assertFalse(prefs.isAppTemporarilyUnlocked(pkg));

        // Short-form / Reels blocker preferences & temporary unlock timers
        assertTrue(prefs.isShortFormBlockerEnabled());
        assertTrue(prefs.toggleShortFormPackage(pkg));
        assertTrue(prefs.isShortFormBlockedForPackage(pkg));
        prefs.grantShortFormUnlock(pkg, 10);
        assertTrue(prefs.isShortFormTemporarilyUnlocked(pkg));
        assertTrue(prefs.getShortFormUnlockRemainingMinutes(pkg) >= 9);
        prefs.relockShortForm(pkg);
        assertFalse(prefs.isShortFormTemporarilyUnlocked(pkg));
        assertFalse(prefs.toggleShortFormPackage(pkg));
        assertFalse(prefs.isShortFormBlockedForPackage(pkg));

        int xp = db.insertWorkoutSession("SQUATS", 12, 1, 92, 45, 95, "Fresh", true);
        assertTrue(xp > 0);
        assertTrue(db.getTotalWorkoutReps() >= 12);
        assertTrue(db.getWorkoutSessionsCount() >= 1);
        assertFalse(db.getPersonalBests().isEmpty());
        assertFalse(db.getUnlockedAchievements().isEmpty());
        assertTrue(db.exportOfflineSummaryJson().contains("SQUATS"));
    }
}
