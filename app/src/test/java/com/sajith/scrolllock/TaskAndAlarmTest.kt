package com.sajith.scrolllock

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.sajith.scrolllock.data.local.PreferencesManager
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper
import com.sajith.scrolllock.data.model.FocusTask
import com.sajith.scrolllock.data.model.RepeatDays
import com.sajith.scrolllock.data.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskAndAlarmTest {

    @Test
    fun repeatDays_computeNextTriggerMillis_handlesOneShotAndWeekdays() {
        // Wednesday 2026-11-11 10:00:00
        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 11, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = baseCal.timeInMillis

        // One-shot later same day (14:30)
        val sameDayMillis = RepeatDays.computeNextTriggerMillis(now, 14, 30, 0)
        val sameDayCal = Calendar.getInstance().apply { timeInMillis = sameDayMillis }
        assertEquals(Calendar.WEDNESDAY, sameDayCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(14, sameDayCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, sameDayCal.get(Calendar.MINUTE))

        // One-shot earlier time today (08:15) rolls to tomorrow (Thursday)
        val nextDayMillis = RepeatDays.computeNextTriggerMillis(now, 8, 15, 0)
        val nextDayCal = Calendar.getInstance().apply { timeInMillis = nextDayMillis }
        assertEquals(Calendar.THURSDAY, nextDayCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, nextDayCal.get(Calendar.HOUR_OF_DAY))

        // Friday 20:00 with WEEKDAYS mask -> skips Saturday & Sunday to Monday
        val fridayCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 13, 20, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val weekdayNextMillis = RepeatDays.computeNextTriggerMillis(
            fridayCal.timeInMillis, 7, 30, RepeatDays.WEEKDAYS
        )
        val weekdayNextCal = Calendar.getInstance().apply { timeInMillis = weekdayNextMillis }
        assertEquals(Calendar.MONDAY, weekdayNextCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(7, weekdayNextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, weekdayNextCal.get(Calendar.MINUTE))
    }

    @Test
    fun preferencesManager_clampsStudyTargetPagesAndNormalizesSubject() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        prefs.setDefaultStudyTargetPages(0)
        assertEquals(1, prefs.defaultStudyTargetPages)

        prefs.setDefaultStudyTargetPages(999)
        assertEquals(200, prefs.defaultStudyTargetPages)

        prefs.setDefaultStudyTargetPages(25)
        assertEquals(25, prefs.defaultStudyTargetPages)

        prefs.setDefaultStudySubject("   ")
        assertEquals("Focused Reading", prefs.defaultStudySubject)

        prefs.setDefaultStudySubject("  Organic Chemistry  ")
        assertEquals("Organic Chemistry", prefs.defaultStudySubject)
    }

    @Test
    fun databaseHelper_taskCrudAndStudySessions_updateProgressAndBadges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = ScrollLockDatabaseHelper.getInstance(context)

        val initialPages = db.totalStudyPages
        val taskId = db.insertTask(
            FocusTask(
                title = "Read Physics Chapter 4",
                notes = "Finish worked problems",
                category = TaskCategory.STUDY,
                triggerTimeMillis = System.currentTimeMillis() + 3_600_000L,
                hourOfDay = 18,
                minuteOfHour = 30,
                repeatDaysMask = RepeatDays.WEEKDAYS,
                isEnabled = true,
                targetCount = 25,
                requireCompletionToDismiss = true
            )
        )
        assertTrue(taskId > 0L)

        val tasks = db.allTasks
        assertTrue(tasks.any { it.id == taskId && it.title == "Read Physics Chapter 4" })

        db.setTaskEnabled(taskId, false, 0L)
        val disabled = db.getTaskById(taskId)
        assertNotNull(disabled)
        assertFalse(disabled!!.isEnabled)

        val xp = db.insertStudySession(taskId, "Physics Ch. 4", 25, 26, 600)
        assertTrue(xp > 0)
        assertEquals(initialPages + 26, db.totalStudyPages)
        assertTrue(db.todayStudyPages >= 26)

        val recentStudy = db.getRecentStudySessions(5)
        assertTrue(recentStudy.isNotEmpty())
        assertEquals("Physics Ch. 4", recentStudy.first().subjectTitle)

        val updatedTask = db.getTaskById(taskId)
        assertNotNull(updatedTask)
        assertEquals(26, updatedTask!!.completedCount)
        assertTrue(updatedTask.isCompleted)

        val badgeIds = db.unlockedAchievements.map { it.id }.toSet()
        assertTrue(badgeIds.contains("first_study"))
        assertTrue(badgeIds.contains("page_turner_25"))

        db.deleteTask(taskId)
    }

    @Test
    fun manifest_hasZeroProhibitedExactAlarmOrPlayProtectPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val pkgInfo: PackageInfo = pm.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        )

        val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse(perms.contains("android.permission.USE_EXACT_ALARM"))
        assertFalse(perms.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertFalse(perms.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertFalse(perms.contains("android.permission.BIND_VPN_SERVICE"))
        assertFalse(perms.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(perms.contains("android.permission.SYSTEM_ALERT_WINDOW"))
    }
}
