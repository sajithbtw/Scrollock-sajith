package com.sajith.scrolllock.ui.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sajith.scrolllock.workout.ExerciseType
import com.sajith.scrolllock.workout.FatigueLevel
import com.sajith.scrolllock.workout.WorkoutEvaluation
import java.util.Locale

data class WorkoutDashboardState(
    val currentReps: Int = 0,
    val targetReps: Int = 10,
    val selectedExercise: ExerciseType = ExerciseType.SQUATS,
    val detectedExercise: ExerciseType = ExerciseType.SQUATS,
    val elapsedSeconds: Long = 0L,
    val plankRemainingSeconds: Int = 0,
    val restRemainingSeconds: Int = 0,
    val isResting: Boolean = false,
    val isPaused: Boolean = false,
    val recommendedRestSeconds: Int = 30,
    val phaseLabel: String = "READY",
    val primaryAngle: Float = 175f,
    val formQualityScore: Int = 100,
    val symmetryPercent: Int = 100,
    val fatigueLabel: String = "Fresh",
    val postureFeedback: String = "Maintain steady form",
    val isAlertFeedback: Boolean = false,
    val recordedReplayCount: Int = 0,
    val completedRepEventId: Int = 0,
    val lastCompletedRepNumber: Int = 0,
    val lastCompletedRepFormScore: Int = 100,
    val lastCompletedRepValid: Boolean = true,
    val lastRepRejectionReason: String? = null
)

object WorkoutDashboardReducer {

    fun formatTimer(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val mins = safe / 60L
        val secs = safe % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun applyEvaluation(
        current: WorkoutDashboardState,
        eval: WorkoutEvaluation,
        recordedReplayCount: Int
    ): WorkoutDashboardState {
        val completed = eval.justCompletedRep
        val feedbackText = eval.postureCorrection ?: when {
            completed != null && completed.isValid ->
                "Rep #${completed.repNumber} Complete • ${completed.formQualityScore}% Form"
            eval.fatigueStatus.level == FatigueLevel.FATIGUED -> eval.fatigueStatus.message
            else -> "Great form — keep moving smoothly"
        }
        val plankRemaining = if (eval.detectedExercise == ExerciseType.PLANKS) {
            (current.targetReps - eval.plankHoldSeconds).coerceAtLeast(0)
        } else {
            0
        }
        return current.copy(
            detectedExercise = eval.detectedExercise,
            currentReps = eval.repCount,
            recordedReplayCount = recordedReplayCount,
            plankRemainingSeconds = plankRemaining,
            recommendedRestSeconds = eval.fatigueStatus.recommendedRestSeconds,
            phaseLabel = eval.stateLabel,
            primaryAngle = eval.primaryAngle,
            formQualityScore = eval.formQualityScore,
            symmetryPercent = eval.balanceReport.symmetryPercent,
            fatigueLabel = eval.fatigueStatus.level.label,
            postureFeedback = feedbackText,
            isAlertFeedback = eval.isCheatingDetected || eval.errorJoints.isNotEmpty() || (completed != null && !completed.isValid),
            completedRepEventId = if (completed != null) current.completedRepEventId + 1 else current.completedRepEventId,
            lastCompletedRepNumber = completed?.repNumber ?: current.lastCompletedRepNumber,
            lastCompletedRepFormScore = completed?.formQualityScore ?: current.lastCompletedRepFormScore,
            lastCompletedRepValid = completed?.isValid ?: current.lastCompletedRepValid,
            lastRepRejectionReason = if (completed != null) completed.rejectionReason else current.lastRepRejectionReason
        )
    }

    fun tickOneSecond(current: WorkoutDashboardState, sessionEnded: Boolean): WorkoutDashboardState {
        if (current.isPaused || sessionEnded) return current
        if (current.isResting) {
            val nextRest = (current.restRemainingSeconds - 1).coerceAtLeast(0)
            return if (nextRest == 0) {
                current.copy(
                    isResting = false,
                    restRemainingSeconds = 0,
                    phaseLabel = "UP",
                    postureFeedback = "Rest complete — resume your set",
                    isAlertFeedback = false
                )
            } else {
                current.copy(restRemainingSeconds = nextRest)
            }
        }
        return current.copy(elapsedSeconds = current.elapsedSeconds + 1L)
    }
}

/**
 * Real-time Jetpack Compose WorkoutDashboard driven directly by Compose [State] objects.
 * Displays a spring-animated rep counter inside a circular progress ring, active exercise badge,
 * dual elapsed/plank-or-rest timers, and a dynamic form-quality completion badge.
 */
@Composable
fun WorkoutDashboard(
    dashboardState: State<WorkoutDashboardState>,
    onTogglePause: () -> Unit,
    onToggleRest: () -> Unit,
    onOpenReplay: () -> Unit,
    onFinishWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = dashboardState.value
    val isPlank = state.detectedExercise == ExerciseType.PLANKS
    val unit = if (isPlank) "s" else ""

    // Spring pulse animation triggered whenever the pose processor emits a completed valid rep
    val repPulseScale = remember { Animatable(1f) }
    LaunchedEffect(state.completedRepEventId) {
        if (state.completedRepEventId > 0 && state.lastCompletedRepValid) {
            repPulseScale.snapTo(1f)
            repPulseScale.animateTo(
                targetValue = 1.20f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            repPulseScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val goalFraction = (state.currentReps.toFloat() / state.targetReps.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)
    val animatedRingProgress by animateFloatAsState(
        targetValue = goalFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dashboardGoalRing"
    )

    val feedbackBg by animateColorAsState(
        targetValue = when {
            state.isResting -> WorkoutColors.WarningSoft
            state.isAlertFeedback -> WorkoutColors.WarningSoft
            else -> WorkoutColors.TurquoiseSoft
        },
        label = "dashboardFeedbackBg"
    )
    val feedbackTextColor by animateColorAsState(
        targetValue = when {
            state.isResting -> WorkoutColors.WarningAmber
            state.isAlertFeedback -> WorkoutColors.WarningAmber
            else -> WorkoutColors.TurquoiseAccent
        },
        label = "dashboardFeedbackColor"
    )

    val ringActiveColor = if (state.isResting) WorkoutColors.WarningAmber else WorkoutColors.TurquoiseAccent

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = WorkoutColors.GlassSurface,
        border = BorderStroke(1.dp, WorkoutColors.SubtleBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dynamic Rep Completion / Posture / Rest Feedback Badge
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = feedbackBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workout_form_badge")
            ) {
                val bannerText = when {
                    state.isResting -> "Recovery Rest • ${WorkoutDashboardReducer.formatTimer(state.restRemainingSeconds.toLong())} remaining"
                    else -> state.postureFeedback
                }
                Text(
                    text = bannerText,
                    style = MaterialTheme.typography.labelLarge,
                    color = feedbackTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // Core Bento Row: Circular Progress Ring + Spring Rep Counter | Exercise + Dual Timers
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Circular Goal Ring & Spring Rep Counter
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(84.dp)
                        .graphicsLayer {
                            scaleX = repPulseScale.value
                            scaleY = repPulseScale.value
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 7.dp.toPx()
                        drawArc(
                            color = WorkoutColors.SubtleBorder,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = ringActiveColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedRingProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${state.currentReps}$unit",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 22.sp,
                                lineHeight = 24.sp
                            ),
                            color = WorkoutColors.TurquoiseAccent,
                            modifier = Modifier.testTag("workout_rep_counter_text")
                        )
                        Text(
                            text = "/ ${state.targetReps}$unit",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = WorkoutColors.MutedGrey
                        )
                    }
                }

                // Exercise Type, Phase Badge, Dual Timers & Biomechanics
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: Exercise Type Pill + Phase Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FitnessCenter,
                                contentDescription = null,
                                tint = WorkoutColors.TurquoiseAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            val exLabel = if (state.selectedExercise == ExerciseType.AUTO) {
                                "${state.detectedExercise.displayName} (Auto)"
                            } else {
                                state.detectedExercise.displayName
                            }
                            Text(
                                text = exLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = WorkoutColors.SoftWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("workout_exercise_type_text")
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.isResting) WorkoutColors.WarningSoft else WorkoutColors.TurquoiseSoft
                        ) {
                            Text(
                                text = if (state.isResting) "REST" else state.phaseLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (state.isResting) WorkoutColors.WarningAmber else WorkoutColors.TurquoiseAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Row 2: Live Elapsed Timer + Plank/Rest Countdown Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = WorkoutColors.CardSurface,
                            border = BorderStroke(1.dp, WorkoutColors.SubtleBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = "Elapsed Timer",
                                    tint = WorkoutColors.TurquoiseAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = WorkoutDashboardReducer.formatTimer(state.elapsedSeconds),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = WorkoutColors.SoftWhite,
                                    modifier = Modifier.testTag("workout_elapsed_timer_text")
                                )
                            }
                        }

                        val secondaryTimerLabel = when {
                            state.isResting -> "Rest ${WorkoutDashboardReducer.formatTimer(state.restRemainingSeconds.toLong())}"
                            isPlank -> "Left ${WorkoutDashboardReducer.formatTimer(state.plankRemainingSeconds.toLong())}"
                            else -> "Rest ${state.recommendedRestSeconds}s"
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.isResting) WorkoutColors.WarningSoft else WorkoutColors.CardSurface,
                            border = BorderStroke(
                                1.dp,
                                if (state.isResting) WorkoutColors.WarningAmber else WorkoutColors.SubtleBorder
                            ),
                            onClick = onToggleRest,
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("workout_countdown_timer_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SelfImprovement,
                                    contentDescription = "Rest or Plank Countdown",
                                    tint = if (state.isResting) WorkoutColors.WarningAmber else WorkoutColors.MutedGrey,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = secondaryTimerLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (state.isResting) WorkoutColors.WarningAmber else WorkoutColors.SoftWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Row 3: Form Quality + Angle + L/R Symmetry Telemetry
                    val repTag = if (state.completedRepEventId > 0) {
                        if (state.lastCompletedRepValid) {
                            "Rep #${state.lastCompletedRepNumber}: ${state.lastCompletedRepFormScore}%"
                        } else {
                            "Rejected"
                        }
                    } else {
                        "Form ${state.formQualityScore}%"
                    }
                    Text(
                        text = "$repTag • Angle ${state.primaryAngle.toInt()}° • Bal ${state.symmetryPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = WorkoutColors.MutedGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Controls Row (48.dp+ touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onTogglePause,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, WorkoutColors.SubtleBorder),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("workout_pause_button")
                ) {
                    Icon(
                        imageVector = if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = WorkoutColors.SoftWhite,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (state.isPaused) "Resume" else "Pause",
                        style = MaterialTheme.typography.labelMedium,
                        color = WorkoutColors.SoftWhite,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onToggleRest,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (state.isResting) WorkoutColors.WarningAmber else WorkoutColors.SubtleBorder
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp)
                        .testTag("workout_rest_button")
                ) {
                    Text(
                        text = if (state.isResting) "Skip Rest" else "Rest",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isResting) WorkoutColors.WarningAmber else WorkoutColors.SoftWhite,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onOpenReplay,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, WorkoutColors.SubtleBorder),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("workout_replay_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay,
                        contentDescription = null,
                        tint = WorkoutColors.SoftWhite,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "Replay (${state.recordedReplayCount})",
                        style = MaterialTheme.typography.labelMedium,
                        color = WorkoutColors.SoftWhite,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onFinishWorkout,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WorkoutColors.TurquoiseAccent,
                        contentColor = WorkoutColors.OnAccentDark
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .weight(0.95f)
                        .height(48.dp)
                        .testTag("workout_finish_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Finish",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
