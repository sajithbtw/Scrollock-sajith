package com.sajith.scrolllock.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.R
import com.sajith.scrolllock.ScrollLockApp
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper
import com.sajith.scrolllock.data.model.FocusTask
import com.sajith.scrolllock.data.model.TaskCategory
import com.sajith.scrolllock.ui.MainActivity
import com.sajith.scrolllock.ui.study.StudySessionActivity
import com.sajith.scrolllock.ui.tasks.TaskAlarmActivity
import com.sajith.scrolllock.ui.workout.WorkoutCameraActivity
import com.sajith.scrolllock.util.PermissionHelper

/**
 * Handles scheduled Reminder & Alarm Task triggers, Snooze actions, and One-Tap Completion
 * without background Activity hijacking (Play Protect compliant).
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_TASK = "com.sajith.scrolllock.ACTION_TRIGGER_TASK"
        const val ACTION_MARK_DONE = "com.sajith.scrolllock.ACTION_MARK_TASK_DONE"
        const val ACTION_SNOOZE_10M = "com.sajith.scrolllock.ACTION_SNOOZE_TASK_10M"
        private const val NOTIF_ID_BASE = 4000
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return

        val db = ScrollLockDatabaseHelper.getInstance(context)
        val notifId = NOTIF_ID_BASE + (taskId.toInt() and 0x7FFF)
        val nm = context.getSystemService(NotificationManager::class.java)

        when (action) {
            ACTION_MARK_DONE -> {
                val task = db.getTaskById(taskId)
                if (task != null && task.repeatDaysMask == 0) {
                    db.markTaskCompleted(taskId, task.targetCount)
                }
                nm?.cancel(notifId)
            }
            ACTION_SNOOZE_10M -> {
                nm?.cancel(notifId)
                ReminderScheduler.scheduleSnooze(context, taskId, 10)
            }
            ACTION_TRIGGER_TASK -> {
                val task = db.getTaskById(taskId) ?: return
                if (!task.isEnabled) return

                // Schedule next occurrence cleanly if repeating, otherwise disable after trigger
                if (task.repeatDaysMask != 0) {
                    ReminderScheduler.scheduleTask(context, task)
                } else {
                    db.setTaskEnabled(task.id, false, 0L)
                }

                postTaskNotification(context, task, notifId)
            }
        }
    }

    private fun postTaskNotification(context: Context, task: FocusTask, notifId: Int) {
        if (!PermissionHelper.hasNotificationPermission(context)) {
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val isAlarm = task.category == TaskCategory.ALARM
        val channelId = if (isAlarm) {
            ScrollLockApp.CHANNEL_ALARM_TASKS_ID
        } else {
            ScrollLockApp.CHANNEL_REMINDERS_ID
        }

        val primaryIntent = when (task.category) {
            TaskCategory.ALARM -> Intent(context, TaskAlarmActivity::class.java).apply {
                putExtra(TaskAlarmActivity.EXTRA_TASK_ID, task.id)
                putExtra(TaskAlarmActivity.EXTRA_NOTIFICATION_ID, notifId)
            }
            TaskCategory.STUDY -> Intent(context, StudySessionActivity::class.java).apply {
                putExtra(StudySessionActivity.EXTRA_TASK_ID, task.id)
                putExtra(StudySessionActivity.EXTRA_SUBJECT_TITLE, task.title)
                putExtra(StudySessionActivity.EXTRA_TARGET_PAGES, task.targetCount.coerceAtLeast(1))
            }
            TaskCategory.WORKOUT -> Intent(context, WorkoutCameraActivity::class.java).apply {
                putExtra(WorkoutCameraActivity.EXTRA_TASK_ID, task.id)
                putExtra(WorkoutCameraActivity.EXTRA_TARGET_REPS, task.targetCount.coerceAtLeast(5))
                putExtra(WorkoutCameraActivity.EXTRA_EXERCISE_TYPE, task.linkedExercise)
                putExtra(WorkoutCameraActivity.EXTRA_UNLOCK_APP_NAME, task.title)
            }
            TaskCategory.REMINDER -> Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TARGET_ROUTE, "tasks")
            }
        }
        primaryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPending = PendingIntent.getActivity(
            context,
            notifId,
            primaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
            this.action = ACTION_SNOOZE_10M
            putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            notifId + 10_000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
            this.action = ACTION_MARK_DONE
            putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
        }
        val donePending = PendingIntent.getBroadcast(
            context,
            notifId + 20_000,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = when {
            task.notes.isNotBlank() -> task.notes
            task.category == TaskCategory.STUDY -> "Goal: Turn ${task.targetCount} physical pages on camera"
            task.category == TaskCategory.WORKOUT -> "Goal: Complete ${task.targetCount} reps"
            isAlarm -> "Tap to open alarm & complete linked goal"
            else -> "Scheduled focus reminder"
        }

        val actionLabel = when (task.category) {
            TaskCategory.ALARM -> "Open Alarm"
            TaskCategory.STUDY -> "Start Study"
            TaskCategory.WORKOUT -> "Start Workout"
            TaskCategory.REMINDER -> "Open Tasks"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle("${task.category.displayLabel}: ${task.title}")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setContentIntent(contentPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(!isAlarm)
            .addAction(0, actionLabel, contentPending)
            .addAction(0, "Snooze 10m", snoozePending)
            .addAction(0, "Done", donePending)

        nm.notify(notifId, builder.build())
    }
}
