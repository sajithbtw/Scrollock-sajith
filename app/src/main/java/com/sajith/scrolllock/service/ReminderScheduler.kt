package com.sajith.scrolllock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper
import com.sajith.scrolllock.data.model.FocusTask
import com.sajith.scrolllock.data.model.RepeatDays

/**
 * Play-Protect-safe task & alarm scheduler built on AlarmManager.setAndAllowWhileIdle(...)
 * without requesting restricted USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM permissions.
 */
object ReminderScheduler {

    const val EXTRA_TASK_ID = "extra_task_id"
    private const val REQUEST_CODE_OFFSET = 20_000

    fun scheduleTask(context: Context, task: FocusTask): Long {
        if (!task.isEnabled || task.id <= 0L) {
            cancelTask(context, task.id)
            return 0L
        }
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val triggerAtMillis = if (task.triggerTimeMillis > now + 2_000L && task.repeatDaysMask == 0) {
            task.triggerTimeMillis
        } else {
            RepeatDays.computeNextTriggerMillis(
                nowMillis = now,
                hourOfDay = task.hourOfDay,
                minuteOfHour = task.minuteOfHour,
                repeatMask = task.repeatDaysMask
            )
        }

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return triggerAtMillis
        val pending = buildTriggerPendingIntent(appContext, task.id)
        try {
            AlarmManagerCompat.setAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pending
            )
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
        ScrollLockDatabaseHelper.getInstance(appContext).setTaskEnabled(task.id, true, triggerAtMillis)
        return triggerAtMillis
    }

    fun scheduleSnooze(context: Context, taskId: Long, snoozeMinutes: Int = 10): Long {
        if (taskId <= 0L) return 0L
        val appContext = context.applicationContext
        val triggerAt = System.currentTimeMillis() + (snoozeMinutes.coerceAtLeast(1) * 60_000L)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return triggerAt
        val pending = buildTriggerPendingIntent(appContext, taskId)
        try {
            AlarmManagerCompat.setAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pending
            )
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        return triggerAt
    }

    fun cancelTask(context: Context, taskId: Long) {
        if (taskId <= 0L) return
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildTriggerPendingIntent(appContext, taskId))
    }

    fun rescheduleAllEnabledTasks(context: Context) {
        val appContext = context.applicationContext
        val db = ScrollLockDatabaseHelper.getInstance(appContext)
        val tasks = db.getAllTasks()
        for (task in tasks) {
            if (task.isEnabled && (!task.isCompleted || task.repeatDaysMask != 0)) {
                scheduleTask(appContext, task)
            }
        }
    }

    private fun buildTriggerPendingIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_TRIGGER_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val requestCode = REQUEST_CODE_OFFSET + (taskId.toInt() and 0x7FFF)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
