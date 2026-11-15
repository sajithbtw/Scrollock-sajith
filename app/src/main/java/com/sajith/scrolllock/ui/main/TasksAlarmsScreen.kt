package com.sajith.scrolllock.ui.main

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sajith.scrolllock.data.model.FocusTask
import com.sajith.scrolllock.data.model.RepeatDays
import com.sajith.scrolllock.data.model.TaskCategory
import com.sajith.scrolllock.ui.theme.AppJetBrainsMonoFamily
import com.sajith.scrolllock.ui.theme.GlassCard
import com.sajith.scrolllock.ui.theme.GlassColors
import com.sajith.scrolllock.workout.ExerciseType
import kotlin.math.roundToInt

@Composable
fun TasksAlarmsScreen(
    state: MainUiState,
    onCreateNewTask: (TaskCategory) -> Unit,
    onEditTask: (FocusTask) -> Unit,
    onToggleTaskEnabled: (FocusTask, Boolean) -> Unit,
    onToggleTaskCompleted: (FocusTask) -> Unit,
    onDeleteTask: (FocusTask) -> Unit,
    onLaunchTaskSession: (FocusTask) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<TaskCategory?>(null) }

    val filteredTasks = remember(state.tasks, selectedFilter, state.searchQuery) {
        val q = state.searchQuery.trim()
        state.tasks.filter { task ->
            val catMatch = selectedFilter == null || task.category == selectedFilter
            val queryMatch = q.isEmpty() ||
                task.title.contains(q, ignoreCase = true) ||
                task.notes.contains(q, ignoreCase = true)
            catMatch && queryMatch
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reminders, Alarms & Study",
                        style = MaterialTheme.typography.titleLarge,
                        color = GlassColors.TextPrimary
                    )
                    Text(
                        text = "${state.todayStudyPages} pages & ${state.todayWorkoutReps} reps logged today",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassColors.TextSecondary
                    )
                }
                Button(
                    onClick = { onCreateNewTask(selectedFilter ?: TaskCategory.ALARM) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("tasks_add_button")
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Task", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskFilterChip(
                    label = "All (${state.tasks.size})",
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    testTag = "filter_tasks_all"
                )
                TaskCategory.entries.forEach { cat ->
                    val count = state.tasks.count { it.category == cat }
                    TaskFilterChip(
                        label = "${cat.displayLabel} ($count)",
                        selected = selectedFilter == cat,
                        onClick = { selectedFilter = cat },
                        testTag = "filter_tasks_${cat.name.lowercase()}"
                    )
                }
            }
        }

        if (filteredTasks.isEmpty()) {
            item {
                GlassCard(cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Alarm,
                            null,
                            tint = GlassColors.VioletBright,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No scheduled tasks here yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassColors.TextPrimary
                        )
                        Text(
                            text = "Schedule repeating morning alarms, focus reminders, camera page-turn study sessions, or 9-exercise AI workouts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassColors.TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onCreateNewTask(TaskCategory.STUDY) },
                                colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletPrimary),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Add Study Goal")
                            }
                            OutlinedButton(
                                onClick = { onCreateNewTask(TaskCategory.ALARM) },
                                border = BorderStroke(1.dp, GlassColors.GlassBorder),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Set Alarm", color = GlassColors.TextPrimary)
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredTasks, key = { it.id }) { task ->
                TaskCardItem(
                    task = task,
                    onClick = { onEditTask(task) },
                    onToggleEnabled = { enabled -> onToggleTaskEnabled(task, enabled) },
                    onToggleCompleted = { onToggleTaskCompleted(task) },
                    onDelete = { onDeleteTask(task) },
                    onLaunchSession = { onLaunchTaskSession(task) }
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun TaskFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) GlassColors.VioletSoft else GlassColors.GlassCard,
        border = BorderStroke(1.dp, if (selected) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) GlassColors.VioletBright else GlassColors.TextSecondary
            )
        }
    }
}

@Composable
private fun TaskCardItem(
    task: FocusTask,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit,
    onLaunchSession: () -> Unit
) {
    val accent = when (task.category) {
        TaskCategory.ALARM -> GlassColors.AmberAlarm
        TaskCategory.STUDY -> GlassColors.CyanAccent
        TaskCategory.WORKOUT -> GlassColors.VioletBright
        TaskCategory.REMINDER -> GlassColors.MintVerified
    }
    val icon = when (task.category) {
        TaskCategory.ALARM -> Icons.Rounded.Alarm
        TaskCategory.STUDY -> Icons.AutoMirrored.Rounded.MenuBook
        TaskCategory.WORKOUT -> Icons.Rounded.FitnessCenter
        TaskCategory.REMINDER -> Icons.Rounded.NotificationsActive
    }

    GlassCard(
        highlighted = task.isEnabled && !task.isCompleted,
        cornerRadius = 20.dp,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onToggleCompleted,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (task.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (task.isCompleted) "Completed" else "Mark completed",
                        tint = if (task.isCompleted) GlassColors.MintVerified else accent
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(icon, null, tint = accent, modifier = Modifier.size(12.dp))
                                Text(
                                    text = task.category.displayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = RepeatDays.formatTime12h(task.hourOfDay, task.minuteOfHour),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = AppJetBrainsMonoFamily),
                            color = GlassColors.TextPrimary
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (task.isCompleted) GlassColors.TextMuted else GlassColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(RepeatDays.formatMask(task.repeatDaysMask))
                            if (task.category == TaskCategory.STUDY) {
                                append(" • ${task.completedCount}/${task.targetCount} pages")
                            } else if (task.category == TaskCategory.WORKOUT || task.requireCompletionToDismiss) {
                                val exName = runCatching { ExerciseType.valueOf(task.linkedExercise).displayName }
                                    .getOrDefault("Squats")
                                append(" • $exName (${task.completedCount}/${task.targetCount})")
                            }
                            if (task.notes.isNotBlank()) {
                                append(" • ${task.notes}")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = task.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GlassColors.TextPrimary,
                        checkedTrackColor = GlassColors.VioletPrimary
                    )
                )
            }

            if (task.category == TaskCategory.STUDY || task.category == TaskCategory.WORKOUT || task.requireCompletionToDismiss) {
                val target = task.targetCount.coerceAtLeast(1)
                val progress = (task.completedCount.toFloat() / target).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    color = accent,
                    trackColor = GlassColors.GlassBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.category == TaskCategory.STUDY || task.category == TaskCategory.WORKOUT || task.requireCompletionToDismiss) {
                    Button(
                        onClick = onLaunchSession,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletSoft),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = GlassColors.VioletBright, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (task.category == TaskCategory.STUDY) {
                                "Start Study (${task.targetCount}p)"
                            } else {
                                "Start Workout (${task.targetCount})"
                            },
                            color = GlassColors.VioletBright,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete task",
                        tint = GlassColors.CoralError
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
    initialCategory: TaskCategory,
    existingTask: FocusTask?,
    defaultStudyPages: Int,
    defaultExercise: ExerciseType,
    onDismiss: () -> Unit,
    onSave: (FocusTask) -> Unit
) {
    var category by remember { mutableStateOf(existingTask?.category ?: initialCategory) }
    var title by remember { mutableStateOf(existingTask?.title ?: "") }
    var notes by remember { mutableStateOf(existingTask?.notes ?: "") }
    var repeatMask by remember { mutableIntStateOf(existingTask?.repeatDaysMask ?: 0) }
    var targetCountSlider by remember {
        mutableFloatStateOf(
            (existingTask?.targetCount
                ?: if (initialCategory == TaskCategory.STUDY) defaultStudyPages else defaultExercise.defaultTargetReps
            ).toFloat()
        )
    }
    var selectedExercise by remember {
        mutableStateOf(
            existingTask?.let { runCatching { ExerciseType.valueOf(it.linkedExercise) }.getOrNull() }
                ?: defaultExercise
        )
    }
    var requireChallenge by remember { mutableStateOf(existingTask?.requireCompletionToDismiss ?: false) }

    val timeState = rememberTimePickerState(
        initialHour = existingTask?.hourOfDay ?: 8,
        initialMinute = existingTask?.minuteOfHour ?: 0,
        is24Hour = false
    )
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassColors.DeepIndigoBg,
        titleContentColor = GlassColors.TextPrimary,
        title = {
            Text(if (existingTask == null) "Schedule New Task" else "Edit Task")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category selector row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskCategory.entries.forEach { cat ->
                        val active = cat == category
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) GlassColors.VioletSoft else GlassColors.GlassCard,
                            border = BorderStroke(1.dp, if (active) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    category = cat
                                    if (cat == TaskCategory.STUDY && targetCountSlider < 1f) {
                                        targetCountSlider = defaultStudyPages.toFloat()
                                    } else if (cat == TaskCategory.WORKOUT) {
                                        targetCountSlider = selectedExercise.defaultTargetReps.toFloat()
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    text = cat.displayLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) GlassColors.VioletBright else GlassColors.TextSecondary
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = {
                        Text(
                            when (category) {
                                TaskCategory.STUDY -> "Book / Study Subject"
                                TaskCategory.ALARM -> "Alarm Label"
                                TaskCategory.WORKOUT -> "Workout Goal Name"
                                TaskCategory.REMINDER -> "Reminder Title"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            when (category) {
                                TaskCategory.STUDY -> "e.g. Organic Chemistry Ch.4"
                                TaskCategory.ALARM -> "e.g. Morning Focus Wakeup"
                                TaskCategory.WORKOUT -> "e.g. Evening Posture Reset"
                                TaskCategory.REMINDER -> "e.g. Lock social apps & hydrate"
                            }
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassColors.VioletBright,
                        unfocusedBorderColor = GlassColors.GlassBorder,
                        focusedTextColor = GlassColors.TextPrimary,
                        unfocusedTextColor = GlassColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("task_editor_title_input")
                )

                // Time Picker Trigger
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GlassColors.GlassCard,
                    border = BorderStroke(1.dp, GlassColors.GlassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showTimePicker = !showTimePicker }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Trigger Time", style = MaterialTheme.typography.labelLarge, color = GlassColors.TextSecondary)
                        Text(
                            text = RepeatDays.formatTime12h(timeState.hour, timeState.minute),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = AppJetBrainsMonoFamily),
                            color = GlassColors.VioletBright
                        )
                    }
                }

                if (showTimePicker) {
                    TimePicker(
                        state = timeState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = GlassColors.GlassCard,
                            selectorColor = GlassColors.VioletPrimary,
                            periodSelectorSelectedContainerColor = GlassColors.VioletSoft,
                            timeSelectorSelectedContainerColor = GlassColors.VioletSoft,
                            timeSelectorUnselectedContainerColor = GlassColors.GlassCard,
                            timeSelectorSelectedContentColor = GlassColors.TextPrimary,
                            timeSelectorUnselectedContentColor = GlassColors.TextSecondary
                        )
                    )
                }

                // Repeat Days Mask (Mon-Sun)
                Text("Repeat Days", style = MaterialTheme.typography.labelMedium, color = GlassColors.TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RepeatDays.ORDERED_FLAGS.forEach { (flag, shortLabel) ->
                        val active = (repeatMask and flag) != 0
                        Surface(
                            shape = CircleShape,
                            color = if (active) GlassColors.VioletPrimary else GlassColors.GlassCard,
                            border = BorderStroke(1.dp, if (active) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable {
                                    repeatMask = if (active) (repeatMask and flag.inv()) else (repeatMask or flag)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) GlassColors.TextPrimary else GlassColors.TextSecondary
                                )
                            }
                        }
                    }
                }

                // Study Page Goal Chooser OR Workout Exercise Goal Chooser
                if (category == TaskCategory.STUDY) {
                    val pages = targetCountSlider.roundToInt().coerceIn(1, 200)
                    Text(
                        text = "Pages to Study: $pages pages",
                        style = MaterialTheme.typography.labelLarge,
                        color = GlassColors.CyanAccent
                    )
                    Slider(
                        value = targetCountSlider,
                        onValueChange = { targetCountSlider = it },
                        valueRange = 1f..200f,
                        colors = SliderDefaults.colors(
                            thumbColor = GlassColors.CyanAccent,
                            activeTrackColor = GlassColors.VioletPrimary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15, 25, 50).forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (pages == preset) GlassColors.VioletSoft else GlassColors.GlassCard,
                                border = BorderStroke(1.dp, if (pages == preset) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { targetCountSlider = preset.toFloat() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${preset}p", style = MaterialTheme.typography.labelSmall, color = GlassColors.TextPrimary)
                                }
                            }
                        }
                    }
                } else if (category == TaskCategory.WORKOUT || (category == TaskCategory.ALARM && requireChallenge)) {
                    val reps = targetCountSlider.roundToInt().coerceIn(5, 60)
                    Text(
                        text = "Exercise: ${selectedExercise.displayName} • $reps reps",
                        style = MaterialTheme.typography.labelLarge,
                        color = GlassColors.VioletBright
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExerciseType.entries.filter { it != ExerciseType.AUTO }.forEach { ex ->
                            val isSel = ex == selectedExercise
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) GlassColors.VioletSoft else GlassColors.GlassCard,
                                border = BorderStroke(1.dp, if (isSel) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedExercise = ex }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(
                                        text = ex.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSel) GlassColors.VioletBright else GlassColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    Slider(
                        value = targetCountSlider.coerceIn(5f, 60f),
                        onValueChange = { targetCountSlider = it },
                        valueRange = 5f..60f,
                        colors = SliderDefaults.colors(
                            thumbColor = GlassColors.VioletBright,
                            activeTrackColor = GlassColors.VioletPrimary
                        )
                    )
                }

                if (category == TaskCategory.ALARM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Require Workout to Dismiss", style = MaterialTheme.typography.labelLarge, color = GlassColors.TextPrimary)
                            Text("Link an AI exercise set to alarm dismissal", style = MaterialTheme.typography.labelSmall, color = GlassColors.TextSecondary)
                        }
                        Switch(
                            checked = requireChallenge,
                            onCheckedChange = { requireChallenge = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = GlassColors.VioletPrimary)
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassColors.VioletBright,
                        unfocusedBorderColor = GlassColors.GlassBorder,
                        focusedTextColor = GlassColors.TextPrimary,
                        unfocusedTextColor = GlassColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTitle = title.trim().ifEmpty {
                        when (category) {
                            TaskCategory.STUDY -> "Read ${targetCountSlider.roundToInt().coerceIn(1, 200)} Pages"
                            TaskCategory.ALARM -> "Morning Focus Alarm"
                            TaskCategory.WORKOUT -> "${selectedExercise.displayName} Set"
                            TaskCategory.REMINDER -> "Stay Locked In"
                        }
                    }
                    val target = when (category) {
                        TaskCategory.STUDY -> targetCountSlider.roundToInt().coerceIn(1, 200)
                        TaskCategory.WORKOUT -> targetCountSlider.roundToInt().coerceIn(5, 60)
                        TaskCategory.ALARM -> if (requireChallenge) targetCountSlider.roundToInt().coerceIn(5, 60) else 10
                        TaskCategory.REMINDER -> 1
                    }
                    val nextTrigger = RepeatDays.computeNextTriggerMillis(
                        System.currentTimeMillis(),
                        timeState.hour,
                        timeState.minute,
                        repeatMask
                    )
                    onSave(
                        FocusTask(
                            id = existingTask?.id ?: 0L,
                            title = cleanTitle,
                            notes = notes.trim(),
                            category = category,
                            triggerTimeMillis = nextTrigger,
                            hourOfDay = timeState.hour,
                            minuteOfHour = timeState.minute,
                            repeatDaysMask = repeatMask,
                            isEnabled = true,
                            isCompleted = existingTask?.isCompleted ?: false,
                            completedAtMillis = existingTask?.completedAtMillis ?: 0L,
                            linkedExercise = selectedExercise.name,
                            targetCount = target,
                            completedCount = existingTask?.completedCount ?: 0,
                            requireCompletionToDismiss = requireChallenge,
                            createdAtMillis = existingTask?.createdAtMillis ?: System.currentTimeMillis()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletPrimary),
                modifier = Modifier.testTag("task_editor_save_button")
            ) {
                Text("Save Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GlassColors.TextSecondary)
            }
        }
    )
}
