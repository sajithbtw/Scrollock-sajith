package com.sajith.scrolllock.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sajith.scrolllock.data.model.FocusState
import com.sajith.scrolllock.data.model.FocusTask
import com.sajith.scrolllock.data.model.RepeatDays
import com.sajith.scrolllock.data.model.TaskCategory
import com.sajith.scrolllock.ui.theme.ActivitySparklineChart
import com.sajith.scrolllock.ui.theme.AppJetBrainsMonoFamily
import com.sajith.scrolllock.ui.theme.GlassCard
import com.sajith.scrolllock.ui.theme.GlassColors
import com.sajith.scrolllock.util.TimeFormatter

@Composable
fun HomeHubScreen(
    state: MainUiState,
    showEmbeddedTelemetry: Boolean,
    onQuickComposeSubmit: (String) -> Unit,
    onOpenStudyCamera: () -> Unit,
    onOpenWorkoutCamera: () -> Unit,
    onOpenNewTaskModal: (TaskCategory) -> Unit,
    onNavigateTab: (MainTab) -> Unit,
    onOpenSandbox: () -> Unit,
    onToggleTaskDone: (FocusTask) -> Unit,
    onLaunchTaskAction: (FocusTask) -> Unit,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var quickInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Centered Obsidian-Indigo Aurora Hero matching layout visual hierarchy
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.focusState == FocusState.RUNNING || state.focusState == FocusState.PAUSED) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = GlassColors.VioletSoft,
                    border = BorderStroke(1.dp, GlassColors.ActiveGlowBorder),
                    modifier = Modifier
                        .clickable { onNavigateTab(MainTab.FOCUS) }
                        .testTag("home_live_focus_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.focusState == FocusState.RUNNING) GlassColors.MintVerified
                                    else GlassColors.AmberAlarm
                                )
                        )
                        Text(
                            text = "${if (state.deepFocusEnabled) "Deep Focus" else "Focus"} • ${TimeFormatter.formatCountdown(state.remainingMillis)}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = AppJetBrainsMonoFamily),
                            color = GlassColors.TextPrimary
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Text(
                text = if (state.focusState == FocusState.RUNNING) {
                    "Stay locked in.\nYour shields are active."
                } else {
                    "What will you\nfocus on today?"
                },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 29.sp,
                    lineHeight = 35.sp
                ),
                color = GlassColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${state.greeting} • ${state.selectedAppsCount} guarded • ${state.shortFormBlockedCount} Reels blocked",
                style = MaterialTheme.typography.labelMedium,
                color = GlassColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        // Frosted Quick Task / Study Composer Box (with inline Study Camera & Submit action)
        GlassCard(
            highlighted = quickInput.isNotBlank(),
            cornerRadius = 24.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = quickInput,
                    onValueChange = { quickInput = it },
                    placeholder = {
                        Text(
                            text = "Add a reminder, alarm, or study goal (e.g. Read 15p Biology)…",
                            color = GlassColors.TextMuted,
                            maxLines = 2
                        )
                    },
                    singleLine = false,
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (quickInput.isNotBlank()) {
                                onQuickComposeSubmit(quickInput)
                                quickInput = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = GlassColors.TextPrimary,
                        unfocusedTextColor = GlassColors.TextPrimary,
                        cursorColor = GlassColors.VioletBright,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_quick_composer_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GlassColors.GlassSurface,
                            border = BorderStroke(1.dp, GlassColors.GlassBorder),
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenStudyCamera)
                                .testTag("home_composer_study_cam_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.CameraAlt,
                                    "Open Study Page Counter",
                                    tint = GlassColors.CyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Page Cam",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GlassColors.TextPrimary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GlassColors.GlassSurface,
                            border = BorderStroke(1.dp, GlassColors.GlassBorder),
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenSandbox)
                                .testTag("home_composer_sandbox_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Shield,
                                    "Scroll Barrier Test Bench",
                                    tint = GlassColors.VioletBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Barrier",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GlassColors.TextPrimary
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = GlassColors.VioletPrimary,
                        border = BorderStroke(1.dp, GlassColors.ActiveGlowBorder),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (quickInput.isNotBlank()) {
                                    onQuickComposeSubmit(quickInput)
                                    quickInput = ""
                                } else {
                                    onOpenNewTaskModal(TaskCategory.REMINDER)
                                }
                            }
                            .testTag("home_quick_compose_submit")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Create Focus Task",
                                tint = GlassColors.TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4 Frosted Quick-Action Pills (matching reference pills row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickGlassPill(
                label = "Study Pages",
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                tint = GlassColors.CyanAccent,
                onClick = onOpenStudyCamera,
                testTag = "pill_study_pages"
            )
            QuickGlassPill(
                label = "AI Workout",
                icon = Icons.Rounded.FitnessCenter,
                tint = GlassColors.VioletBright,
                onClick = onOpenWorkoutCamera,
                testTag = "pill_ai_workout"
            )
            QuickGlassPill(
                label = "Set Alarm",
                icon = Icons.Rounded.Alarm,
                tint = GlassColors.AmberAlarm,
                onClick = { onOpenNewTaskModal(TaskCategory.ALARM) },
                testTag = "pill_set_alarm"
            )
            QuickGlassPill(
                label = "Deep Focus",
                icon = Icons.Rounded.DoNotDisturbOn,
                tint = GlassColors.MintVerified,
                onClick = { onNavigateTab(MainTab.FOCUS) },
                testTag = "pill_deep_focus"
            )
        }

        // 4 Bento Action Cards (4-across on wide / 2x2 grid on narrow phones matching reference)
        BentoActionGrid(
            state = state,
            onOpenStudyCamera = onOpenStudyCamera,
            onNavigateTasks = { onNavigateTab(MainTab.TASKS) },
            onOpenWorkoutCamera = onOpenWorkoutCamera,
            onNavigateFocus = { onNavigateTab(MainTab.FOCUS) }
        )

        // Active Reminders & Alarms quick preview if present
        val activeTasks = state.tasks.filter { !it.isCompleted }.take(3)
        if (activeTasks.isNotEmpty()) {
            GlassCard(cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Next Reminders & Study Goals",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassColors.TextPrimary
                        )
                        Text(
                            text = "Manage all",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassColors.VioletBright,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateTab(MainTab.TASKS) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    activeTasks.forEach { task ->
                        HomeTaskPreviewRow(
                            task = task,
                            onToggleDone = { onToggleTaskDone(task) },
                            onLaunch = { onLaunchTaskAction(task) }
                        )
                    }
                }
            }
        }

        // On Compact/Medium widths (<900dp), embed the Right Telemetry & Capabilities cards cleanly below
        if (showEmbeddedTelemetry) {
            RightTelemetryColumn(
                state = state,
                onOpenStudyCamera = onOpenStudyCamera,
                onNavigateTab = onNavigateTab,
                onOpenOnboarding = onOpenOnboarding
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QuickGlassPill(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    GlassCard(
        cornerRadius = 16.dp,
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = GlassColors.TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BentoActionGrid(
    state: MainUiState,
    onOpenStudyCamera: () -> Unit,
    onNavigateTasks: () -> Unit,
    onOpenWorkoutCamera: () -> Unit,
    onNavigateFocus: () -> Unit
) {
    val activeTaskCount = state.tasks.count { it.isEnabled && !it.isCompleted }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoCardItem(
                title = "Study\nPage Cam",
                subtitle = "${state.todayStudyPages}p today • Goal ${state.defaultStudyPages}p",
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                accent = GlassColors.CyanAccent,
                onClick = onOpenStudyCamera,
                testTag = "bento_study_pages",
                modifier = Modifier.weight(1f)
            )
            BentoCardItem(
                title = "Alarms &\nReminders",
                subtitle = "$activeTaskCount active schedules",
                icon = Icons.Rounded.Alarm,
                accent = GlassColors.AmberAlarm,
                onClick = onNavigateTasks,
                testTag = "bento_alarms_tasks",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoCardItem(
                title = "9 AI\nExercises",
                subtitle = "${state.preferredExercise.displayName} • ${state.requiredReps} reps",
                icon = Icons.Rounded.FitnessCenter,
                accent = GlassColors.VioletBright,
                onClick = onOpenWorkoutCamera,
                testTag = "bento_ai_exercises",
                modifier = Modifier.weight(1f)
            )
            BentoCardItem(
                title = "Deep\nFocus",
                subtitle = if (state.deepFocusEnabled) "DND previews hidden" else "Tap to start ${state.defaultDurationMinutes}m lock",
                icon = Icons.Rounded.Lock,
                accent = GlassColors.MintVerified,
                onClick = onNavigateFocus,
                testTag = "bento_deep_focus",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BentoCardItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    GlassCard(
        cornerRadius = 20.dp,
        onClick = onClick,
        modifier = modifier
            .height(150.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
                    color = GlassColors.TextPrimary,
                    maxLines = 2
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = title.replace("\n", " "),
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HomeTaskPreviewRow(
    task: FocusTask,
    onToggleDone: () -> Unit,
    onLaunch: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlassColors.GlassSurface,
        border = BorderStroke(1.dp, GlassColors.GlassBorder.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = when (task.category) {
                    TaskCategory.ALARM -> Icons.Rounded.Alarm
                    TaskCategory.STUDY -> Icons.AutoMirrored.Rounded.MenuBook
                    TaskCategory.WORKOUT -> Icons.Rounded.FitnessCenter
                    TaskCategory.REMINDER -> Icons.Rounded.NotificationsActive
                },
                contentDescription = null,
                tint = when (task.category) {
                    TaskCategory.ALARM -> GlassColors.AmberAlarm
                    TaskCategory.STUDY -> GlassColors.CyanAccent
                    TaskCategory.WORKOUT -> GlassColors.VioletBright
                    TaskCategory.REMINDER -> GlassColors.MintVerified
                },
                modifier = Modifier.size(18.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = GlassColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${RepeatDays.formatTime12h(task.hourOfDay, task.minuteOfHour)} • ${RepeatDays.formatMask(task.repeatDaysMask)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextSecondary
                )
            }

            if (task.category == TaskCategory.STUDY || task.category == TaskCategory.WORKOUT) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GlassColors.VioletSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onLaunch)
                ) {
                    Text(
                        text = "Start",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GlassColors.VioletBright,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = GlassColors.GlassCardTop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleDone)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, "Done", tint = GlassColors.MintVerified, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RightTelemetryColumn(
    state: MainUiState,
    onOpenStudyCamera: () -> Unit,
    onNavigateTab: (MainTab) -> Unit,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Highlighted Spotlight Glass Card (matching top-right GPT-4o card in screenshot)
        GlassCard(highlighted = true, cornerRadius = 22.dp, onClick = onOpenStudyCamera) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Study & Pose AI",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassColors.TextPrimary
                    )
                    Icon(Icons.Rounded.AutoAwesome, null, tint = GlassColors.VioletBright, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "On-device page-turn counter & 9 ML Kit exercises. Goal: ${state.defaultStudyPages} pages.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x55080914)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val c = Offset(size.width * 0.5f, size.height * 0.52f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0x99A78BFF), Color(0x337C5CFF), Color.Transparent),
                                center = c,
                                radius = size.minDimension * 0.65f
                            ),
                            center = c,
                            radius = size.minDimension * 0.65f
                        )
                        drawCircle(
                            brush = Brush.linearGradient(
                                listOf(GlassColors.TextPrimary, GlassColors.VioletBright, GlassColors.CyanAccent)
                            ),
                            radius = size.minDimension * 0.24f,
                            center = c
                        )
                    }
                    Button(
                        onClick = onOpenStudyCamera,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GlassColors.VioletPrimary.copy(alpha = 0.85f)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    ) {
                        Text("Start Study", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 2. Active Shields Card ("Capabilities" checklist from screenshot)
        GlassCard(cornerRadius = 22.dp, onClick = onOpenOnboarding) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Shields",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassColors.TextPrimary
                    )
                    Text(
                        text = "Setup",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.VioletBright
                    )
                }
                ShieldChecklistRow("Usage App Guard", Icons.Rounded.Shield, state.hasUsageAccess)
                ShieldChecklistRow("DND Hide Previews", Icons.Rounded.DoNotDisturbOn, state.hasDndAccess)
                ShieldChecklistRow("Reels & Shorts Lock", Icons.Rounded.Lock, state.shortFormBlockedCount > 0)
                ShieldChecklistRow("Page & Pose Camera", Icons.Rounded.CameraAlt, state.hasCameraPerm)
            }
        }

        // 3. Your Activity Card + Violet Sparkline Chart
        GlassCard(cornerRadius = 22.dp, onClick = { onNavigateTab(MainTab.INSIGHTS) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Activity",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassColors.TextPrimary
                    )
                    Icon(Icons.Rounded.Insights, null, tint = GlassColors.VioletBright, modifier = Modifier.size(16.dp))
                }
                ActivityStatRow("Pages turned", "${state.totalStudyPages}")
                ActivityStatRow("Workout reps", "${state.totalWorkoutReps}")
                ActivityStatRow("Focus saved", TimeFormatter.formatFocusMinutesSummary(state.totalFocusSeconds))

                val sparkPoints = state.weeklyFocusMinutes.values.map { it.toFloat() }
                val chartValues = if (sparkPoints.any { it > 0f }) sparkPoints else listOf(2f, 5f, 4f, 8f, 6f, 11f, 9f)
                ActivitySparklineChart(
                    values = chartValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 4.dp)
                )
            }
        }

        // 4. Bottom Level & Streak Card (matching bottom-right Plus card)
        GlassCard(
            cornerRadius = 20.dp,
            onClick = { onNavigateTab(MainTab.INSIGHTS) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = GlassColors.VioletSoft,
                    border = BorderStroke(1.dp, GlassColors.ActiveGlowBorder),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Timer, null, tint = GlassColors.VioletBright, modifier = Modifier.size(18.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Level ${state.userLevel} • ${state.userXp} XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = GlassColors.TextPrimary
                    )
                    Text(
                        text = "${state.workoutStreakDays} day streak • Play-Protect Safe",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ShieldChecklistRow(label: String, icon: ImageVector, ready: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GlassColors.TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = GlassColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
            contentDescription = if (ready) "Ready" else "Setup needed",
            tint = if (ready) GlassColors.MintVerified else GlassColors.AmberAlarm,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ActivityStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = GlassColors.TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = AppJetBrainsMonoFamily),
            color = GlassColors.TextPrimary
        )
    }
}
