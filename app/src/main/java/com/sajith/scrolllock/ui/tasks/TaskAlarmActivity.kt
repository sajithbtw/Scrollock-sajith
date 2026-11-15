package com.sajith.scrolllock.ui.tasks

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper
import com.sajith.scrolllock.data.model.FocusTask
import com.sajith.scrolllock.data.model.RepeatDays
import com.sajith.scrolllock.data.model.TaskCategory
import com.sajith.scrolllock.service.ReminderScheduler
import com.sajith.scrolllock.ui.study.StudySessionActivity
import com.sajith.scrolllock.ui.theme.AmbientNebulaBackground
import com.sajith.scrolllock.ui.theme.AppJetBrainsMonoFamily
import com.sajith.scrolllock.ui.theme.GlassCard
import com.sajith.scrolllock.ui.theme.GlassColors
import com.sajith.scrolllock.ui.theme.ScrollLockGlassTheme
import com.sajith.scrolllock.ui.workout.WorkoutCameraActivity
import com.sajith.scrolllock.workout.ExerciseType

/**
 * Alarm & Task Challenge screen allowing users to snooze, dismiss, or complete a linked
 * Study Page-Turn session or AI Pose Workout directly from an active task alarm.
 */
class TaskAlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    private var task: FocusTask? = null
    private var notificationId: Int = -1
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isMuted by mutableStateOf(false)

    private val challengeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            markCompletedAndFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val db = ScrollLockDatabaseHelper.getInstance(this)
        task = if (taskId > 0L) db.getTaskById(taskId) else null

        startAlarmFeedback()

        setContent {
            val activeTask = task ?: FocusTask(
                title = "Scheduled Focus Alarm",
                category = TaskCategory.ALARM
            )
            ScrollLockGlassTheme {
                TaskAlarmScreen(
                    task = activeTask,
                    isMuted = isMuted,
                    onToggleMute = {
                        isMuted = !isMuted
                        if (isMuted) stopAlarmFeedback() else startAlarmFeedback()
                    },
                    onLaunchLinkedChallenge = { launchLinkedChallenge(activeTask) },
                    onSnooze = {
                        stopAlarmFeedback()
                        clearAlarmNotification()
                        if (activeTask.id > 0L) {
                            ReminderScheduler.scheduleSnooze(this, activeTask.id, 10)
                        }
                        finish()
                    },
                    onDismiss = { markCompletedAndFinish() }
                )
            }
        }
    }

    private fun launchLinkedChallenge(activeTask: FocusTask) {
        stopAlarmFeedback()
        val isStudy = activeTask.category == TaskCategory.STUDY
        if (isStudy) {
            val intent = Intent(this, StudySessionActivity::class.java).apply {
                putExtra(StudySessionActivity.EXTRA_TASK_ID, activeTask.id)
                putExtra(StudySessionActivity.EXTRA_SUBJECT_TITLE, activeTask.title)
                putExtra(StudySessionActivity.EXTRA_TARGET_PAGES, activeTask.targetCount.coerceAtLeast(1))
                putExtra(StudySessionActivity.EXTRA_FROM_ALARM, true)
            }
            challengeLauncher.launch(intent)
        } else {
            val intent = Intent(this, WorkoutCameraActivity::class.java).apply {
                putExtra(WorkoutCameraActivity.EXTRA_TASK_ID, activeTask.id)
                putExtra(WorkoutCameraActivity.EXTRA_TARGET_REPS, activeTask.targetCount.coerceAtLeast(5))
                putExtra(WorkoutCameraActivity.EXTRA_EXERCISE_TYPE, activeTask.linkedExercise)
                putExtra(WorkoutCameraActivity.EXTRA_UNLOCK_APP_NAME, activeTask.title)
            }
            challengeLauncher.launch(intent)
        }
    }

    private fun markCompletedAndFinish() {
        stopAlarmFeedback()
        clearAlarmNotification()
        task?.let { t ->
            if (t.id > 0L && t.repeatDaysMask == 0) {
                ScrollLockDatabaseHelper.getInstance(this).markTaskCompleted(t.id, t.targetCount)
            }
        }
        finish()
    }

    private fun clearAlarmNotification() {
        if (notificationId > 0) {
            getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        }
    }

    private fun startAlarmFeedback() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (_: Exception) {
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 450, 350, 450), 0)
            vibrator?.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    private fun stopAlarmFeedback() {
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        stopAlarmFeedback()
        super.onDestroy()
    }
}

@Composable
private fun TaskAlarmScreen(
    task: FocusTask,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onLaunchLinkedChallenge: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "alarmPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alarmIconScale"
    )

    val exDisplay = try {
        ExerciseType.valueOf(task.linkedExercise).displayName
    } catch (_: Exception) {
        "Squats"
    }

    AmbientNebulaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onToggleMute,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassColors.GlassBorder),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        null,
                        tint = GlassColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isMuted) "Unmute" else "Mute", color = GlassColors.TextPrimary)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = GlassColors.VioletSoft,
                    border = BorderStroke(1.5.dp, GlassColors.ActiveGlowBorder),
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (task.category) {
                                TaskCategory.STUDY -> Icons.AutoMirrored.Rounded.MenuBook
                                TaskCategory.WORKOUT -> Icons.Rounded.FitnessCenter
                                else -> Icons.Rounded.Alarm
                            },
                            contentDescription = "Alarm Active",
                            tint = GlassColors.VioletBright,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = RepeatDays.formatTime12h(task.hourOfDay, task.minuteOfHour),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = AppJetBrainsMonoFamily,
                        fontSize = 36.sp
                    ),
                    color = GlassColors.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = RepeatDays.formatMask(task.repeatDaysMask),
                    style = MaterialTheme.typography.labelLarge,
                    color = GlassColors.VioletBright
                )
                Spacer(Modifier.height(16.dp))

                GlassCard(highlighted = true, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = GlassColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        if (task.notes.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = task.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (task.requireCompletionToDismiss || task.category == TaskCategory.STUDY || task.category == TaskCategory.WORKOUT) {
                            Spacer(Modifier.height(12.dp))
                            val challengeText = if (task.category == TaskCategory.STUDY) {
                                "Goal: Turn ${task.targetCount} study pages on camera"
                            } else {
                                "Challenge: ${task.targetCount} $exDisplay"
                            }
                            Surface(shape = RoundedCornerShape(12.dp), color = GlassColors.VioletSoft) {
                                Text(
                                    text = challengeText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = GlassColors.VioletBright,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (task.category == TaskCategory.STUDY || task.category == TaskCategory.WORKOUT || task.requireCompletionToDismiss) {
                    Button(
                        onClick = onLaunchLinkedChallenge,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("alarm_start_challenge_button")
                    ) {
                        Icon(
                            if (task.category == TaskCategory.STUDY) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.FitnessCenter,
                            null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (task.category == TaskCategory.STUDY) {
                                "Start ${task.targetCount}-Page Study"
                            } else {
                                "Start ${task.targetCount} $exDisplay"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSnooze,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, GlassColors.GlassBorder),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Rounded.Snooze, null, tint = GlassColors.TextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Snooze 10m", color = GlassColors.TextPrimary)
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GlassColors.GlassCardTop,
                            contentColor = GlassColors.TextPrimary
                        ),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = GlassColors.MintVerified, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Dismiss")
                    }
                }

                if (task.requireCompletionToDismiss) {
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Skip Challenge & Dismiss", color = GlassColors.TextMuted)
                    }
                }
            }
        }
    }
}
