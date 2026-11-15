package com.sajith.scrolllock.ui.workout

import android.view.ViewGroup
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.State
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.sajith.scrolllock.workout.ExerciseType
import com.sajith.scrolllock.workout.LandmarkPoint

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll

data class WorkoutCompletionSummary(
    val title: String,
    val message: String,
    val unlockedApp: Boolean,
    val challengeMet: Boolean = false
)

data class WorkoutUiState(
    val hasCameraPermission: Boolean = true,
    val useFrontCamera: Boolean = true,
    val showGhostTrainer: Boolean = true,
    val voiceCoachEnabled: Boolean = true,
    val selectedExercise: ExerciseType = ExerciseType.SQUATS,
    val unlockAppName: String? = null,
    val cameraGuidance: String = "Position full body in camera frame…",
    val cameraSubStatus: String = "Calibrating posture baseline…",
    val isCalibrated: Boolean = false,
    val calibrationProgress: Float = 0f,
    val isBodyAligned: Boolean = false,
    val userLandmarks: Map<Int, LandmarkPoint> = emptyMap(),
    val ghostLandmarks: Map<Int, LandmarkPoint> = emptyMap(),
    val errorJoints: Set<Int> = emptySet(),
    val analysisWidth: Int = 0,
    val analysisHeight: Int = 0,
    val completionSummary: WorkoutCompletionSummary? = null
)

@Composable
fun WorkoutScreen(
    uiState: WorkoutUiState,
    dashboardState: State<WorkoutDashboardState>,
    onProvideSurfaceProvider: (Preview.SurfaceProvider) -> Unit,
    onCloseClick: () -> Unit,
    onSelectExercise: (ExerciseType) -> Unit,
    onToggleCamera: () -> Unit,
    onToggleGhost: () -> Unit,
    onToggleVoiceCoach: () -> Unit,
    onVoiceCommandClick: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleRest: () -> Unit,
    onOpenReplay: () -> Unit,
    onFinishWorkout: () -> Unit,
    onGrantCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCompletionContinue: () -> Unit,
    onCompletionReplay: () -> Unit
) {
    var showExercisePicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WorkoutColors.ObsidianBg)
    ) {
        if (uiState.hasCameraPermission) {
            // 1. Live CameraX Feed (FILL_CENTER matching Skeleton Canvas)
            CameraPreviewSurface(
                modifier = Modifier.fillMaxSize(),
                onSurfaceProviderReady = onProvideSurfaceProvider
            )

            // 2. Native Jetpack Compose Canvas Skeleton & Ghost Trainer Overlay
            PoseSkeletonCanvas(
                landmarks = uiState.userLandmarks,
                errorJoints = uiState.errorJoints,
                ghostLandmarks = uiState.ghostLandmarks,
                showGhostTrainer = uiState.showGhostTrainer,
                analysisWidth = uiState.analysisWidth,
                analysisHeight = uiState.analysisHeight,
                isBodyAligned = uiState.isBodyAligned,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle top & bottom gradient scrims for crisp glass HUD legibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                WorkoutColors.ObsidianBg.copy(alpha = 0.72f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                WorkoutColors.ObsidianBg.copy(alpha = 0.86f)
                            )
                        )
                    )
            )

            // 3. Floating Top Controls & Smart Camera Guide
            TopWorkoutHud(
                uiState = uiState,
                onCloseClick = onCloseClick,
                onOpenExercisePicker = { showExercisePicker = true },
                onToggleCamera = onToggleCamera,
                onToggleGhost = onToggleGhost,
                onToggleVoiceCoach = onToggleVoiceCoach,
                onVoiceCommandClick = onVoiceCommandClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // 4. Floating WorkoutDashboard Telemetry & Rep Pulse Ring
            WorkoutDashboard(
                dashboardState = dashboardState,
                onTogglePause = onTogglePause,
                onToggleRest = onToggleRest,
                onOpenReplay = onOpenReplay,
                onFinishWorkout = onFinishWorkout,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        } else {
            CameraPermissionOverlay(
                onGrantCameraPermission = onGrantCameraPermission,
                onOpenAppSettings = onOpenAppSettings,
                onCloseClick = onCloseClick
            )
        }
    }

    if (showExercisePicker) {
        ExercisePickerDialog(
            selected = uiState.selectedExercise,
            onSelect = { exercise ->
                showExercisePicker = false
                onSelectExercise(exercise)
            },
            onDismiss = { showExercisePicker = false }
        )
    }

    uiState.completionSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = WorkoutColors.CardSurface,
            titleContentColor = WorkoutColors.SoftWhite,
            textContentColor = WorkoutColors.MutedGrey,
            title = {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = WorkoutColors.SoftWhite
                )
            },
            text = {
                Text(
                    text = summary.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WorkoutColors.SoftWhite.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                Button(
                    onClick = onCompletionContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WorkoutColors.TurquoiseAccent,
                        contentColor = WorkoutColors.OnAccentDark
                    ),
                    modifier = Modifier.testTag("workout_complete_confirm_button")
                ) {
                    Text(if (summary.unlockedApp) "Continue to App" else "Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCompletionReplay,
                    modifier = Modifier.testTag("workout_complete_replay_button")
                ) {
                    Text("Rep Replay", color = WorkoutColors.TurquoiseAccent)
                }
            }
        )
    }
}

@Composable
private fun CameraPreviewSurface(
    modifier: Modifier = Modifier,
    onSurfaceProviderReady: (Preview.SurfaceProvider) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                onSurfaceProviderReady(surfaceProvider)
            }
        }
    )
}

@Composable
private fun TopWorkoutHud(
    uiState: WorkoutUiState,
    onCloseClick: () -> Unit,
    onOpenExercisePicker: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleGhost: () -> Unit,
    onToggleVoiceCoach: () -> Unit,
    onVoiceCommandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1: Back, Exercise Selector Pill, Camera Flip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = WorkoutColors.GlassSurface,
                border = BorderStroke(1.dp, WorkoutColors.SubtleBorder),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCloseClick)
                    .testTag("workout_close_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.nav_home),
                        tint = WorkoutColors.SoftWhite
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = WorkoutColors.GlassSurface,
                border = BorderStroke(1.dp, WorkoutColors.SubtleBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenExercisePicker)
                    .testTag("workout_exercise_picker_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FitnessCenter,
                        contentDescription = null,
                        tint = WorkoutColors.TurquoiseAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    val titleLabel = if (uiState.unlockAppName != null) {
                        "${uiState.selectedExercise.displayName} • ${uiState.unlockAppName}"
                    } else {
                        uiState.selectedExercise.displayName
                    }
                    Text(
                        text = titleLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = WorkoutColors.TurquoiseAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Switch exercise",
                        tint = WorkoutColors.TurquoiseAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = WorkoutColors.GlassSurface,
                border = BorderStroke(1.dp, WorkoutColors.SubtleBorder),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggleCamera)
                    .testTag("workout_camera_flip_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = WorkoutColors.SoftWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (uiState.useFrontCamera) "Front" else "Back",
                        style = MaterialTheme.typography.labelMedium,
                        color = WorkoutColors.SoftWhite
                    )
                }
            }
        }

        // Row 2: Ghost Trainer, Coach Audio, Voice Command pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HudFilterPill(
                label = if (uiState.showGhostTrainer) "Ghost: ON" else "Ghost: OFF",
                icon = if (uiState.showGhostTrainer) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                active = uiState.showGhostTrainer,
                onClick = onToggleGhost,
                testTag = "workout_toggle_ghost_button"
            )
            HudFilterPill(
                label = if (uiState.voiceCoachEnabled) "Coach: ON" else "Coach: OFF",
                icon = if (uiState.voiceCoachEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                active = uiState.voiceCoachEnabled,
                onClick = onToggleVoiceCoach,
                testTag = "workout_toggle_voice_button"
            )
            HudFilterPill(
                label = "Voice Cmd",
                icon = Icons.Rounded.Mic,
                active = false,
                onClick = onVoiceCommandClick,
                testTag = "workout_voice_command_button"
            )
        }

        // Row 3: Smart Camera Positioning & Calibration Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = WorkoutColors.GlassTile,
            border = BorderStroke(
                width = 1.dp,
                color = if (uiState.isBodyAligned) {
                    WorkoutColors.TurquoiseAccent.copy(alpha = 0.45f)
                } else {
                    WorkoutColors.SubtleBorder
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = uiState.cameraGuidance,
                    style = MaterialTheme.typography.labelMedium,
                    color = WorkoutColors.SoftWhite,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = uiState.cameraSubStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = WorkoutColors.MutedGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!uiState.isCalibrated) {
                    val calProgress by animateFloatAsState(
                        targetValue = uiState.calibrationProgress.coerceIn(0f, 1f),
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "calProgress"
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { calProgress },
                        color = WorkoutColors.TurquoiseAccent,
                        trackColor = WorkoutColors.SubtleBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun HudFilterPill(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) WorkoutColors.TurquoiseSoft else WorkoutColors.GlassSurface,
        label = "pillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) WorkoutColors.TurquoiseAccent else WorkoutColors.SoftWhite,
        label = "pillContent"
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (active) WorkoutColors.TurquoiseAccent.copy(alpha = 0.65f) else WorkoutColors.SubtleBorder
        ),
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ExercisePickerDialog(
    selected: ExerciseType,
    onSelect: (ExerciseType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WorkoutColors.CardSurface,
        titleContentColor = WorkoutColors.SoftWhite,
        title = {
            Text("Select Exercise Engine (${ExerciseType.entries.size})", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 390.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExerciseType.entries.forEach { exercise ->
                    val isCurrent = exercise == selected
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) WorkoutColors.TurquoiseSoft else WorkoutColors.ObsidianBg,
                        border = BorderStroke(
                            1.dp,
                            if (isCurrent) WorkoutColors.TurquoiseAccent else WorkoutColors.SubtleBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(exercise) }
                            .testTag("exercise_option_${exercise.name.lowercase()}")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = exercise.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCurrent) WorkoutColors.TurquoiseAccent else WorkoutColors.SoftWhite,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = WorkoutColors.TurquoiseAccent.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = exercise.categoryTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WorkoutColors.TurquoiseAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = exercise.cameraGuide,
                                style = MaterialTheme.typography.labelSmall,
                                color = WorkoutColors.MutedGrey
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = WorkoutColors.MutedGrey)
            }
        }
    )
}

@Composable
private fun CameraPermissionOverlay(
    onGrantCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.FitnessCenter,
            contentDescription = null,
            tint = WorkoutColors.TurquoiseAccent,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.camera_perm_title),
            style = MaterialTheme.typography.titleLarge,
            color = WorkoutColors.SoftWhite,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.camera_perm_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = WorkoutColors.MutedGrey,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGrantCameraPermission,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WorkoutColors.TurquoiseAccent,
                contentColor = WorkoutColors.OnAccentDark
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("workout_grant_camera_button")
        ) {
            Text(stringResource(R.string.btn_grant_camera), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onOpenAppSettings,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, WorkoutColors.SubtleBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("workout_open_camera_settings_button")
        ) {
            Text(stringResource(R.string.btn_open_app_settings), color = WorkoutColors.SoftWhite)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(
            onClick = onCloseClick,
            modifier = Modifier
                .height(48.dp)
                .testTag("workout_permission_back_button")
        ) {
            Text("Back", color = WorkoutColors.MutedGrey)
        }
    }
}
