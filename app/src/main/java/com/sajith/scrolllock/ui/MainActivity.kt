package com.sajith.scrolllock.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.sajith.scrolllock.data.local.AppRepository
import com.sajith.scrolllock.data.local.PreferencesManager
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper
import com.sajith.scrolllock.data.model.AppItem
import com.sajith.scrolllock.data.model.FocusState
import com.sajith.scrolllock.data.model.FocusTask
import com.sajith.scrolllock.data.model.RepeatDays
import com.sajith.scrolllock.data.model.TaskCategory
import com.sajith.scrolllock.service.DeepFocusController
import com.sajith.scrolllock.service.FocusSessionManager
import com.sajith.scrolllock.service.ReminderScheduler
import com.sajith.scrolllock.ui.main.AppFilterMode
import com.sajith.scrolllock.ui.main.AppsGuardGlassScreen
import com.sajith.scrolllock.ui.main.FocusGlassScreen
import com.sajith.scrolllock.ui.main.HomeHubScreen
import com.sajith.scrolllock.ui.main.InsightsGlassScreen
import com.sajith.scrolllock.ui.main.MainTab
import com.sajith.scrolllock.ui.main.MainUiState
import com.sajith.scrolllock.ui.main.RightTelemetryColumn
import com.sajith.scrolllock.ui.main.SettingsGlassScreen
import com.sajith.scrolllock.ui.main.TaskEditorDialog
import com.sajith.scrolllock.ui.main.TasksAlarmsScreen
import com.sajith.scrolllock.ui.study.StudySessionActivity
import com.sajith.scrolllock.ui.theme.AmbientNebulaBackground
import com.sajith.scrolllock.ui.theme.AppJetBrainsMonoFamily
import com.sajith.scrolllock.ui.theme.GlassCard
import com.sajith.scrolllock.ui.theme.GlassColors
import com.sajith.scrolllock.ui.theme.GlowingNavPill
import com.sajith.scrolllock.ui.theme.ScrollLockGlassTheme
import com.sajith.scrolllock.ui.workout.RepReplayActivity
import com.sajith.scrolllock.ui.workout.WorkoutCameraActivity
import com.sajith.scrolllock.util.PermissionHelper
import com.sajith.scrolllock.util.TimeFormatter
import com.sajith.scrolllock.workout.ExerciseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/**
 * Unified Midnight-Indigo & Luminous Violet Glassmorphic Host Activity for ScrollLock.
 * Preserves adaptive layouts across Compact phones, Medium foldables, and Expanded tablets.
 */
class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val EXTRA_TARGET_ROUTE = "extra_target_route"
        private const val KEY_SELECTED_TAB = "selected_tab_route"
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var dbHelper: ScrollLockDatabaseHelper
    private lateinit var appRepo: AppRepository
    private lateinit var sessionManager: FocusSessionManager

    private val uiState = MutableStateFlow(MainUiState())
    private val appFilterState = MutableStateFlow(AppFilterMode.ALL)
    private val deepFocusAllowedState = MutableStateFlow<Set<String>>(emptySet())

    private var taskEditorRequest by mutableStateOf<Pair<TaskCategory, FocusTask?>?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshAllState()
        if (!granted) {
            Toast.makeText(this, "Enable Notifications in Settings for Focus & Alarm alerts.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(this)
        dbHelper = ScrollLockDatabaseHelper.getInstance(this)
        appRepo = AppRepository(this)
        sessionManager = FocusSessionManager.getInstance(this)

        val initialTab = resolveTabFromIntent(intent)
            ?: savedInstanceState?.getString(KEY_SELECTED_TAB)?.let { route ->
                MainTab.entries.firstOrNull { it.routeKey == route }
            }
            ?: MainTab.HOME

        uiState.update { it.copy(currentTab = initialTab) }
        bindSessionLiveData()
        refreshAllState()
        loadInstalledAppsAsync()

        setContent {
            val state by uiState.collectAsStateWithLifecycle()
            val appFilter by appFilterState.collectAsStateWithLifecycle()
            val deepFocusAllowed by deepFocusAllowedState.collectAsStateWithLifecycle()

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                refreshAllState()
                loadInstalledAppsAsync()
            }

            BackHandler(enabled = state.currentTab != MainTab.HOME) {
                switchToTab(MainTab.HOME)
            }

            ScrollLockGlassTheme {
                ScrollLockShell(
                    state = state,
                    appFilter = appFilter,
                    deepFocusAllowed = deepFocusAllowed,
                    onSelectTab = { switchToTab(it) },
                    onSearchChange = { q -> uiState.update { it.copy(searchQuery = q) } },
                    onOpenNewTaskModal = { cat -> taskEditorRequest = cat to null },
                    onEditTask = { task -> taskEditorRequest = task.category to task },
                    onQuickComposeSubmit = { raw -> handleQuickCompose(raw) },
                    onOpenStudyCamera = { openStudyCamera() },
                    onOpenWorkoutCamera = { openWorkoutCamera() },
                    onOpenSandbox = { startActivity(Intent(this, ScrollSandboxActivity::class.java)) },
                    onOpenOnboarding = { startActivity(Intent(this, OnboardingActivity::class.java)) },
                    onToggleTaskEnabled = { task, enabled -> toggleTaskEnabled(task, enabled) },
                    onToggleTaskCompleted = { task -> toggleTaskCompleted(task) },
                    onDeleteTask = { task -> deleteTask(task) },
                    onLaunchTaskSession = { task -> launchTaskSession(task) },
                    onSelectDurationMinutes = { mins -> selectFocusDuration(mins) },
                    onFocusPrimaryAction = { handleFocusPrimaryAction() },
                    onFocusReset = { sessionManager.resetSession() },
                    onToggleDeepFocus = { enabled ->
                        prefs.setDeepFocusEnabled(enabled)
                        sessionManager.syncDeepFocusWithState()
                        refreshAllState()
                    },
                    onToggleHidePreviews = { enabled ->
                        prefs.setDeepFocusHidePreviewsEnabled(enabled)
                        sessionManager.syncDeepFocusWithState()
                        refreshAllState()
                    },
                    onToggleBlockInternet = { enabled ->
                        prefs.setDeepFocusBlockInternetEnabled(enabled)
                        sessionManager.syncDeepFocusWithState()
                        refreshAllState()
                    },
                    onGrantDndAccess = { PermissionHelper.openNotificationPolicySettings(this) },
                    onOpenConnectivityPanel = { PermissionHelper.openInternetConnectivityPanel(this) },
                    onOpenDataSaver = { PermissionHelper.openDataSaverSettings(this) },
                    onToggleDeepFocusException = { pkg ->
                        prefs.toggleDeepFocusAllowedPackage(pkg)
                        deepFocusAllowedState.value = prefs.getDeepFocusAllowedPackages()
                        refreshAllState()
                    },
                    isSocialOrGame = { pkg -> appRepo.isSocialOrGamePackage(pkg) },
                    onAppFilterChange = { appFilterState.value = it },
                    onToggleAppGuard = { app, checked -> toggleAppGuard(app, checked) },
                    onToggleAppReels = { app -> toggleAppReels(app) },
                    onPresetBlockReels = { applyReelsPreset() },
                    onPresetGuardSocial = { applySocialGamesPreset() },
                    onExportJsonClick = { shareInsightsJson() },
                    onSelectDefaultExercise = { ex ->
                        prefs.setPreferredExerciseName(ex.name)
                        refreshAllState()
                    },
                    onSetRequiredReps = { reps ->
                        prefs.setRequiredWorkoutReps(reps)
                        refreshAllState()
                    },
                    onSetDefaultStudyPages = { pages ->
                        prefs.setDefaultStudyTargetPages(pages)
                        refreshAllState()
                    },
                    onSetDefaultFocusMinutes = { mins ->
                        prefs.setDefaultDurationMinutes(mins)
                        if (sessionManager.getCurrentStateValue() == FocusState.IDLE) {
                            sessionManager.setPresetDurationMinutes(mins)
                        }
                        refreshAllState()
                    },
                    onToggleVoiceCoach = { prefs.setVoiceCoachEnabled(it); refreshAllState() },
                    onToggleGhostTrainer = { prefs.setGhostTrainerEnabled(it); refreshAllState() },
                    onToggleMirrorMode = { prefs.setMirrorModeEnabled(it); refreshAllState() },
                    onToggleShortFormBlocker = { prefs.setShortFormBlockerEnabled(it); refreshAllState() },
                    onToggleAlwaysGuard = { prefs.setAlwaysGuardAppsEnabled(it); refreshAllState() },
                    onToggleWorkoutUnlock = { prefs.setWorkoutUnlockEnabled(it); refreshAllState() },
                    onOpenUsageSettings = { PermissionHelper.openUsageAccessSettings(this) },
                    onOpenDndSettings = { PermissionHelper.openNotificationPolicySettings(this) },
                    onOpenNotificationSettings = { requestOrOpenNotificationSettings() },
                    onOpenAppSettings = { PermissionHelper.openAppDetailsSettings(this) },
                    onOpenBatterySettings = { PermissionHelper.openBatteryOptimizationSettings(this) },
                    onOpenRepReplay = { openLatestReplay() }
                )

                val req = taskEditorRequest
                if (req != null) {
                    TaskEditorDialog(
                        initialCategory = req.first,
                        existingTask = req.second,
                        defaultStudyPages = state.defaultStudyPages,
                        defaultExercise = state.preferredExercise,
                        onDismiss = { taskEditorRequest = null },
                        onSave = { savedTask ->
                            taskEditorRequest = null
                            saveOrUpdateTask(savedTask)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveTabFromIntent(intent)?.let { switchToTab(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED_TAB, uiState.value.currentTab.routeKey)
    }

    fun navigateToTab(menuItemId: Int) {
        mapMenuIdToTab(menuItemId)?.let { switchToTab(it) }
    }

    private fun resolveTabFromIntent(intent: Intent?): MainTab? {
        if (intent == null) return null
        val route = intent.getStringExtra(EXTRA_TARGET_ROUTE)
        if (!route.isNullOrBlank()) {
            MainTab.entries.firstOrNull { it.routeKey.equals(route, ignoreCase = true) }?.let { return it }
        }
        val tabId = intent.getIntExtra(EXTRA_TARGET_TAB, -1)
        return mapMenuIdToTab(tabId)
    }

    private fun mapMenuIdToTab(menuId: Int): MainTab? = when (menuId) {
        R.id.nav_home -> MainTab.HOME
        R.id.nav_apps -> MainTab.APPS
        R.id.nav_focus -> MainTab.FOCUS
        R.id.nav_insights -> MainTab.INSIGHTS
        R.id.nav_settings -> MainTab.SETTINGS
        else -> null
    }

    private fun switchToTab(tab: MainTab) {
        uiState.update { it.copy(currentTab = tab) }
    }

    private fun bindSessionLiveData() {
        sessionManager.focusState.observe(this) { state ->
            uiState.update { it.copy(focusState = state ?: FocusState.IDLE) }
        }
        sessionManager.remainingMillis.observe(this) { rem ->
            uiState.update { it.copy(remainingMillis = rem ?: (25 * 60_000L)) }
        }
        sessionManager.totalMillis.observe(this) { total ->
            uiState.update { it.copy(totalMillis = total ?: (25 * 60_000L)) }
        }
        sessionManager.sessionInterruptions.observe(this) { count ->
            uiState.update { it.copy(sessionInterruptions = count ?: 0) }
        }
    }

    private fun refreshAllState() {
        val selectedPkgs = prefs.getSelectedPackages()
        val shortFormPkgs = prefs.getShortFormBlockedPackages()
        val allowedPkgs = prefs.getDeepFocusAllowedPackages()
        deepFocusAllowedState.value = allowedPkgs

        val prefEx = runCatching { ExerciseType.valueOf(prefs.getPreferredExerciseName()) }
            .getOrDefault(ExerciseType.SQUATS)

        uiState.update { current ->
            current.copy(
                greeting = TimeFormatter.getGreeting(this),
                focusState = sessionManager.currentStateValue,
                remainingMillis = sessionManager.currentRemainingMillisValue,
                totalMillis = sessionManager.currentTotalMillisValue,
                sessionInterruptions = sessionManager.currentInterruptionsValue,
                selectedAppsCount = selectedPkgs.size,
                shortFormBlockedCount = shortFormPkgs.size,
                hasUsageAccess = PermissionHelper.hasUsageStatsPermission(this),
                hasDndAccess = PermissionHelper.hasNotificationPolicyAccess(this),
                hasCameraPerm = PermissionHelper.hasCameraPermission(this),
                hasNotificationPerm = PermissionHelper.hasNotificationPermission(this),
                hasBatteryExemption = PermissionHelper.isIgnoringBatteryOptimizations(this),
                deepFocusEnabled = prefs.isDeepFocusEnabled(),
                deepFocusHidePreviews = prefs.isDeepFocusHidePreviewsEnabled(),
                deepFocusBlockInternet = prefs.isDeepFocusBlockInternetEnabled(),
                deepFocusAllowedCount = allowedPkgs.size,
                deepFocusShieldedPreviewCount = DeepFocusController.resolveBlockedInternetPackages(this).size,
                customDurationMinutes = prefs.getCustomFocusDurationMinutes(),
                defaultDurationMinutes = prefs.getDefaultDurationMinutes(),
                tasks = dbHelper.allTasks,
                recentStudySessions = dbHelper.getRecentStudySessions(10),
                todayStudyPages = dbHelper.todayStudyPages,
                totalStudyPages = dbHelper.totalStudyPages,
                todayWorkoutReps = dbHelper.todayWorkoutReps,
                totalWorkoutReps = dbHelper.totalWorkoutReps,
                workoutSessionsCount = dbHelper.workoutSessionsCount,
                averageFormScore = dbHelper.averageWorkoutFormScore,
                totalFocusSeconds = dbHelper.totalFocusSeconds,
                completedSessionsCount = dbHelper.completedSessionsCount,
                totalInterruptionsCount = dbHelper.totalInterruptionsCount,
                weeklyFocusMinutes = dbHelper.last7DaysFocusMinutes,
                topApps = dbHelper.getTopDistractingApps(6),
                personalBests = dbHelper.personalBests,
                achievements = dbHelper.unlockedAchievements,
                userXp = prefs.userXp,
                userLevel = prefs.userLevel,
                workoutStreakDays = prefs.workoutStreakDays,
                preferredExercise = prefEx,
                requiredReps = prefs.requiredWorkoutReps,
                unlockDurationMinutes = prefs.unlockDurationMinutes,
                defaultStudyPages = prefs.defaultStudyTargetPages,
                defaultStudySubject = prefs.defaultStudySubject,
                voiceCoachEnabled = prefs.isVoiceCoachEnabled(),
                ghostTrainerEnabled = prefs.isGhostTrainerEnabled(),
                mirrorModeEnabled = prefs.isMirrorModeEnabled(),
                shortFormBlockerEnabled = prefs.isShortFormBlockerEnabled(),
                alwaysGuardEnabled = prefs.isAlwaysGuardAppsEnabled(),
                workoutUnlockEnabled = prefs.isWorkoutUnlockEnabled()
            )
        }
    }

    private fun loadInstalledAppsAsync() {
        uiState.update { it.copy(appsLoading = true) }
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { appRepo.launchableApps }
            val selectedPkgs = prefs.getSelectedPackages()
            var deviceMs = 0L
            var guardedMs = 0L
            for (item in loaded) {
                deviceMs += item.todayUsageMillis
                if (selectedPkgs.contains(item.packageName)) {
                    guardedMs += item.todayUsageMillis
                }
            }
            uiState.update {
                it.copy(
                    installedApps = loaded,
                    appsLoading = false,
                    todayDeviceUsageMillis = deviceMs,
                    todayGuardedUsageMillis = guardedMs
                )
            }
        }
    }

    private fun handleQuickCompose(rawInput: String) {
        val text = rawInput.trim()
        if (text.isEmpty()) return

        val lower = text.lowercase(Locale.US)
        val pageMatch = Regex("(\\d+)\\s*(?:p|pg|page|pages)\\b").find(lower)
        val parsedPages = pageMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 200)

        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 30) }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)

        val matchedExercise = when {
            lower.contains("push") -> ExerciseType.PUSHUPS
            lower.contains("squat") -> ExerciseType.SQUATS
            lower.contains("lunge") -> ExerciseType.LUNGES
            lower.contains("jack") -> ExerciseType.JUMPING_JACKS
            lower.contains("knee") -> ExerciseType.HIGH_KNEES
            lower.contains("press") -> ExerciseType.SHOULDER_PRESS
            lower.contains("curl") -> ExerciseType.BICEP_CURLS
            lower.contains("sit") || lower.contains("crunch") -> ExerciseType.SIT_UPS
            lower.contains("plank") -> ExerciseType.PLANKS
            else -> null
        }

        val category = when {
            parsedPages != null || lower.contains("read") || lower.contains("study") || lower.contains("book") -> TaskCategory.STUDY
            matchedExercise != null || lower.contains("workout") -> TaskCategory.WORKOUT
            lower.contains("alarm") || lower.contains("wake") -> TaskCategory.ALARM
            else -> TaskCategory.REMINDER
        }

        val target = when (category) {
            TaskCategory.STUDY -> parsedPages ?: prefs.defaultStudyTargetPages
            TaskCategory.WORKOUT -> matchedExercise?.defaultTargetReps ?: prefs.requiredWorkoutReps
            else -> 1
        }

        val triggerMs = RepeatDays.computeNextTriggerMillis(System.currentTimeMillis(), hour, min, 0)
        val task = FocusTask(
            title = text,
            category = category,
            triggerTimeMillis = triggerMs,
            hourOfDay = hour,
            minuteOfHour = min,
            repeatDaysMask = 0,
            isEnabled = true,
            linkedExercise = (matchedExercise ?: uiState.value.preferredExercise).name,
            targetCount = target
        )
        saveOrUpdateTask(task)
        Toast.makeText(this, "Scheduled ${category.displayLabel}: $text", Toast.LENGTH_SHORT).show()
    }

    private fun saveOrUpdateTask(task: FocusTask) {
        ensureNotificationPermissionIfNeeded()
        val persisted = if (task.id > 0L) {
            dbHelper.updateTask(task)
            task
        } else {
            val newId = dbHelper.insertTask(task)
            task.copy(id = newId)
        }
        if (persisted.isEnabled && !persisted.isCompleted) {
            ReminderScheduler.scheduleTask(this, persisted)
        } else {
            ReminderScheduler.cancelTask(this, persisted.id)
        }
        refreshAllState()
    }

    private fun toggleTaskEnabled(task: FocusTask, enabled: Boolean) {
        if (enabled) {
            ensureNotificationPermissionIfNeeded()
            val nextMs = RepeatDays.computeNextTriggerMillis(
                System.currentTimeMillis(),
                task.hourOfDay,
                task.minuteOfHour,
                task.repeatDaysMask
            )
            dbHelper.setTaskEnabled(task.id, true, nextMs)
            ReminderScheduler.scheduleTask(this, task.copy(isEnabled = true, triggerTimeMillis = nextMs))
        } else {
            dbHelper.setTaskEnabled(task.id, false, 0L)
            ReminderScheduler.cancelTask(this, task.id)
        }
        refreshAllState()
    }

    private fun toggleTaskCompleted(task: FocusTask) {
        val nextDone = !task.isCompleted
        dbHelper.setTaskCompletedState(task.id, nextDone)
        if (nextDone && task.repeatDaysMask == 0) {
            dbHelper.setTaskEnabled(task.id, false, 0L)
            ReminderScheduler.cancelTask(this, task.id)
        }
        refreshAllState()
    }

    private fun deleteTask(task: FocusTask) {
        ReminderScheduler.cancelTask(this, task.id)
        dbHelper.deleteTask(task.id)
        refreshAllState()
    }

    private fun launchTaskSession(task: FocusTask) {
        if (task.category == TaskCategory.STUDY) {
            val intent = Intent(this, StudySessionActivity::class.java).apply {
                putExtra(StudySessionActivity.EXTRA_TASK_ID, task.id)
                putExtra(StudySessionActivity.EXTRA_SUBJECT_TITLE, task.title)
                putExtra(StudySessionActivity.EXTRA_TARGET_PAGES, task.targetCount.coerceAtLeast(1))
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, WorkoutCameraActivity::class.java).apply {
                putExtra(WorkoutCameraActivity.EXTRA_TASK_ID, task.id)
                putExtra(WorkoutCameraActivity.EXTRA_TARGET_REPS, task.targetCount.coerceAtLeast(5))
                putExtra(WorkoutCameraActivity.EXTRA_EXERCISE_TYPE, task.linkedExercise)
                putExtra(WorkoutCameraActivity.EXTRA_UNLOCK_APP_NAME, task.title)
            }
            startActivity(intent)
        }
    }

    private fun openStudyCamera() {
        val intent = Intent(this, StudySessionActivity::class.java).apply {
            putExtra(StudySessionActivity.EXTRA_SUBJECT_TITLE, uiState.value.defaultStudySubject)
            putExtra(StudySessionActivity.EXTRA_TARGET_PAGES, uiState.value.defaultStudyPages)
        }
        startActivity(intent)
    }

    private fun openWorkoutCamera() {
        val intent = Intent(this, WorkoutCameraActivity::class.java).apply {
            putExtra(WorkoutCameraActivity.EXTRA_TARGET_REPS, uiState.value.requiredReps)
            putExtra(WorkoutCameraActivity.EXTRA_EXERCISE_TYPE, uiState.value.preferredExercise.name)
        }
        startActivity(intent)
    }

    private fun openLatestReplay() {
        if (prefs.latestReplayJson.isNullOrEmpty()) {
            Toast.makeText(this, R.string.workout_no_replays, Toast.LENGTH_SHORT).show()
        } else {
            startActivity(Intent(this, RepReplayActivity::class.java))
        }
    }

    private fun selectFocusDuration(minutes: Int) {
        prefs.setDefaultDurationMinutes(minutes)
        if (minutes !in listOf(15, 25, 45, 60, 90)) {
            prefs.setCustomFocusDurationMinutes(minutes)
        }
        sessionManager.setPresetDurationMinutes(minutes)
        refreshAllState()
    }

    private fun handleFocusPrimaryAction() {
        when (sessionManager.currentStateValue) {
            FocusState.RUNNING -> sessionManager.pauseSession()
            FocusState.PAUSED -> sessionManager.resumeSession()
            FocusState.IDLE, FocusState.COMPLETED -> {
                ensureNotificationPermissionIfNeeded()
                val durationMin = ((sessionManager.currentTotalMillisValue + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
                sessionManager.startNewSession(durationMin)
            }
        }
        refreshAllState()
    }

    private fun toggleAppGuard(app: AppItem, checked: Boolean) {
        val selected = prefs.getSelectedPackages()
        if (checked) {
            if (selected.add(app.packageName)) {
                dbHelper.recordAppSelected(app.packageName, app.appName)
            }
        } else {
            selected.remove(app.packageName)
        }
        prefs.setSelectedPackages(selected)
        val updatedList = uiState.value.installedApps.map {
            if (it.packageName == app.packageName) {
                AppItem(it.packageName, it.appName, it.icon, it.todayUsageMillis, checked, it.isShortFormBlocked)
            } else it
        }
        uiState.update { it.copy(installedApps = updatedList, selectedAppsCount = selected.size) }
    }

    private fun toggleAppReels(app: AppItem) {
        val blockedNow = prefs.toggleShortFormPackage(app.packageName)
        if (blockedNow && (!PermissionHelper.hasUsageStatsPermission(this) || !PermissionHelper.hasNotificationPermission(this))) {
            Toast.makeText(this, R.string.toast_enable_usage_for_reels, Toast.LENGTH_SHORT).show()
        }
        val updatedList = uiState.value.installedApps.map {
            if (it.packageName == app.packageName) {
                AppItem(it.packageName, it.appName, it.icon, it.todayUsageMillis, it.isSelected, blockedNow)
            } else it
        }
        uiState.update {
            it.copy(
                installedApps = updatedList,
                shortFormBlockedCount = prefs.getShortFormBlockedPackages().size
            )
        }
    }

    private fun applyReelsPreset() {
        val shortForm = prefs.getShortFormBlockedPackages()
        var added = 0
        for (app in uiState.value.installedApps) {
            if (appRepo.isShortFormHostPackage(app.packageName) && shortForm.add(app.packageName)) {
                added++
            }
        }
        prefs.setShortFormBlockedPackages(shortForm)
        prefs.setShortFormBlockerEnabled(true)
        Toast.makeText(this, "Guarding Reels & Shorts in ${shortForm.size} apps (+$added).", Toast.LENGTH_SHORT).show()
        refreshAllState()
        loadInstalledAppsAsync()
    }

    private fun applySocialGamesPreset() {
        val selected = prefs.getSelectedPackages()
        var added = 0
        for (app in uiState.value.installedApps) {
            if (appRepo.isSocialOrGamePackage(app.packageName) && selected.add(app.packageName)) {
                dbHelper.recordAppSelected(app.packageName, app.appName)
                added++
            }
        }
        prefs.setSelectedPackages(selected)
        prefs.setAlwaysGuardAppsEnabled(true)
        Toast.makeText(this, "Guarded ${selected.size} Social & Game apps (+$added).", Toast.LENGTH_SHORT).show()
        refreshAllState()
        loadInstalledAppsAsync()
    }

    private fun shareInsightsJson() {
        val json = dbHelper.exportOfflineSummaryJson()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "ScrollLock Focus & Study Summary")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(intent, "Export ScrollLock Summary"))
    }

    private fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestOrOpenNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PermissionHelper.openAppNotificationSettings(this)
        }
    }
}

@Composable
private fun ScrollLockShell(
    state: MainUiState,
    appFilter: AppFilterMode,
    deepFocusAllowed: Set<String>,
    onSelectTab: (MainTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenNewTaskModal: (TaskCategory) -> Unit,
    onEditTask: (FocusTask) -> Unit,
    onQuickComposeSubmit: (String) -> Unit,
    onOpenStudyCamera: () -> Unit,
    onOpenWorkoutCamera: () -> Unit,
    onOpenSandbox: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onToggleTaskEnabled: (FocusTask, Boolean) -> Unit,
    onToggleTaskCompleted: (FocusTask) -> Unit,
    onDeleteTask: (FocusTask) -> Unit,
    onLaunchTaskSession: (FocusTask) -> Unit,
    onSelectDurationMinutes: (Int) -> Unit,
    onFocusPrimaryAction: () -> Unit,
    onFocusReset: () -> Unit,
    onToggleDeepFocus: (Boolean) -> Unit,
    onToggleHidePreviews: (Boolean) -> Unit,
    onToggleBlockInternet: (Boolean) -> Unit,
    onGrantDndAccess: () -> Unit,
    onOpenConnectivityPanel: () -> Unit,
    onOpenDataSaver: () -> Unit,
    onToggleDeepFocusException: (String) -> Unit,
    isSocialOrGame: (String) -> Boolean,
    onAppFilterChange: (AppFilterMode) -> Unit,
    onToggleAppGuard: (AppItem, Boolean) -> Unit,
    onToggleAppReels: (AppItem) -> Unit,
    onPresetBlockReels: () -> Unit,
    onPresetGuardSocial: () -> Unit,
    onExportJsonClick: () -> Unit,
    onSelectDefaultExercise: (ExerciseType) -> Unit,
    onSetRequiredReps: (Int) -> Unit,
    onSetDefaultStudyPages: (Int) -> Unit,
    onSetDefaultFocusMinutes: (Int) -> Unit,
    onToggleVoiceCoach: (Boolean) -> Unit,
    onToggleGhostTrainer: (Boolean) -> Unit,
    onToggleMirrorMode: (Boolean) -> Unit,
    onToggleShortFormBlocker: (Boolean) -> Unit,
    onToggleAlwaysGuard: (Boolean) -> Unit,
    onToggleWorkoutUnlock: (Boolean) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenDndSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenRepReplay: () -> Unit
) {
    AmbientNebulaBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isMediumOrExpanded = maxWidth >= 600.dp
            val isExpandedThreePane = maxWidth >= 900.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!isMediumOrExpanded) {
                        CompactFloatingBottomDock(
                            currentTab = state.currentTab,
                            onSelectTab = onSelectTab
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isMediumOrExpanded) {
                        LeftGlassSidebar(
                            state = state,
                            onSelectTab = onSelectTab,
                            onOpenNewTaskModal = onOpenNewTaskModal,
                            onEditTask = onEditTask,
                            modifier = Modifier
                                .width(226.dp)
                                .fillMaxHeight()
                        )
                    }

                    // Center Glass Workspace + Top Search/Action Bar
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .statusBarsPadding()
                    ) {
                        TopGlassCommandBar(
                            state = state,
                            isCompact = !isMediumOrExpanded,
                            onSearchChange = onSearchChange,
                            onNewTaskClick = { onOpenNewTaskModal(TaskCategory.ALARM) },
                            onInsightsClick = { onSelectTab(MainTab.INSIGHTS) },
                            onOnboardingClick = onOpenOnboarding
                        )

                        Crossfade(
                            targetState = state.currentTab,
                            label = "mainTabCrossfade",
                            modifier = Modifier.weight(1f)
                        ) { tab ->
                            when (tab) {
                                MainTab.HOME -> HomeHubScreen(
                                    state = state,
                                    showEmbeddedTelemetry = !isExpandedThreePane,
                                    onQuickComposeSubmit = onQuickComposeSubmit,
                                    onOpenStudyCamera = onOpenStudyCamera,
                                    onOpenWorkoutCamera = onOpenWorkoutCamera,
                                    onOpenNewTaskModal = onOpenNewTaskModal,
                                    onNavigateTab = onSelectTab,
                                    onOpenSandbox = onOpenSandbox,
                                    onToggleTaskDone = onToggleTaskCompleted,
                                    onLaunchTaskAction = onLaunchTaskSession,
                                    onOpenOnboarding = onOpenOnboarding
                                )
                                MainTab.TASKS -> TasksAlarmsScreen(
                                    state = state,
                                    onCreateNewTask = onOpenNewTaskModal,
                                    onEditTask = onEditTask,
                                    onToggleTaskEnabled = onToggleTaskEnabled,
                                    onToggleTaskCompleted = onToggleTaskCompleted,
                                    onDeleteTask = onDeleteTask,
                                    onLaunchTaskSession = onLaunchTaskSession
                                )
                                MainTab.FOCUS -> FocusGlassScreen(
                                    state = state,
                                    deepFocusAllowedPackages = deepFocusAllowed,
                                    onSelectDurationMinutes = onSelectDurationMinutes,
                                    onPrimaryActionClick = onFocusPrimaryAction,
                                    onResetClick = onFocusReset,
                                    onToggleDeepFocus = onToggleDeepFocus,
                                    onToggleHidePreviews = onToggleHidePreviews,
                                    onToggleBlockInternet = onToggleBlockInternet,
                                    onGrantDndAccess = onGrantDndAccess,
                                    onOpenConnectivityPanel = onOpenConnectivityPanel,
                                    onOpenDataSaver = onOpenDataSaver,
                                    onToggleDeepFocusException = onToggleDeepFocusException,
                                    onOpenStudyCamera = onOpenStudyCamera,
                                    onOpenSandbox = onOpenSandbox
                                )
                                MainTab.APPS -> AppsGuardGlassScreen(
                                    state = state,
                                    filterMode = appFilter,
                                    isSocialOrGame = isSocialOrGame,
                                    onFilterChange = onAppFilterChange,
                                    onToggleAppGuard = onToggleAppGuard,
                                    onToggleAppReels = onToggleAppReels,
                                    onPresetBlockReels = onPresetBlockReels,
                                    onPresetGuardSocial = onPresetGuardSocial
                                )
                                MainTab.INSIGHTS -> InsightsGlassScreen(
                                    state = state,
                                    onExportJsonClick = onExportJsonClick
                                )
                                MainTab.SETTINGS -> SettingsGlassScreen(
                                    state = state,
                                    onSelectDefaultExercise = onSelectDefaultExercise,
                                    onSetRequiredReps = onSetRequiredReps,
                                    onSetDefaultStudyPages = onSetDefaultStudyPages,
                                    onSetDefaultFocusMinutes = onSetDefaultFocusMinutes,
                                    onToggleVoiceCoach = onToggleVoiceCoach,
                                    onToggleGhostTrainer = onToggleGhostTrainer,
                                    onToggleMirrorMode = onToggleMirrorMode,
                                    onToggleShortFormBlocker = onToggleShortFormBlocker,
                                    onToggleAlwaysGuard = onToggleAlwaysGuard,
                                    onToggleWorkoutUnlock = onToggleWorkoutUnlock,
                                    onOpenUsageSettings = onOpenUsageSettings,
                                    onOpenDndSettings = onOpenDndSettings,
                                    onOpenNotificationSettings = onOpenNotificationSettings,
                                    onOpenAppSettings = onOpenAppSettings,
                                    onOpenBatterySettings = onOpenBatterySettings,
                                    onOpenOnboarding = onOpenOnboarding,
                                    onOpenRepReplay = onOpenRepReplay
                                )
                            }
                        }
                    }

                    // 3rd Right Telemetry Column on Expanded Screens (matches uploaded screenshot)
                    if (isExpandedThreePane && state.currentTab == MainTab.HOME) {
                        RightTelemetryColumn(
                            state = state,
                            onOpenStudyCamera = onOpenStudyCamera,
                            onNavigateTab = onSelectTab,
                            onOpenOnboarding = onOpenOnboarding,
                            modifier = Modifier
                                .width(282.dp)
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 14.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeftGlassSidebar(
    state: MainUiState,
    onSelectTab: (MainTab) -> Unit,
    onOpenNewTaskModal: (TaskCategory) -> Unit,
    onEditTask: (FocusTask) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0x880B0C1C),
        border = BorderStroke(1.dp, GlassColors.GlassBorder.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo_art),
                        contentDescription = "ScrollLock Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Text(
                        text = "ScrollLock",
                        style = MaterialTheme.typography.titleLarge,
                        color = GlassColors.TextPrimary
                    )
                }

                Spacer(Modifier.height(4.dp))

                MainTab.entries.forEach { tab ->
                    GlowingNavPill(
                        label = tab.label,
                        icon = iconForTab(tab),
                        selected = state.currentTab == tab,
                        onClick = { onSelectTab(tab) },
                        testTag = "sidebar_nav_${tab.routeKey}",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upcoming Tasks",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassColors.TextSecondary
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onOpenNewTaskModal(TaskCategory.ALARM) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, "Add Task", tint = GlassColors.TextPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                state.tasks.filter { !it.isCompleted }.take(4).forEach { task ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEditTask(task) }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = RepeatDays.formatTime12h(task.hourOfDay, task.minuteOfHour),
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassColors.TextMuted
                        )
                    }
                }
            }

            GlassCard(
                cornerRadius = 16.dp,
                onClick = { onSelectTab(MainTab.TASKS) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("View all tasks", style = MaterialTheme.typography.labelLarge, color = GlassColors.TextPrimary)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = GlassColors.VioletBright, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TopGlassCommandBar(
    state: MainUiState,
    isCompact: Boolean,
    onSearchChange: (String) -> Unit,
    onNewTaskClick: () -> Unit,
    onInsightsClick: () -> Unit,
    onOnboardingClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isCompact) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_art),
                contentDescription = "ScrollLock",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
            )
        }

        // Frosted Glass Search Pill
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = GlassColors.GlassCard,
            border = BorderStroke(1.dp, GlassColors.GlassBorder.copy(alpha = 0.7f)),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Search, "Search", tint = GlassColors.TextMuted, modifier = Modifier.size(18.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            text = if (isCompact) "Search tasks & apps…" else "Search ScrollLock tasks, study & apps…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = GlassColors.TextPrimary),
                        cursorBrush = SolidColor(GlassColors.VioletBright),
                        modifier = Modifier.fillMaxWidth().testTag("top_search_input")
                    )
                }
                if (state.searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear Search",
                        tint = GlassColors.TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onSearchChange("") }
                    )
                }
            }
        }

        // + New Task Glass Pill Button
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GlassColors.GlassCardTop,
            border = BorderStroke(1.dp, GlassColors.ActiveGlowBorder.copy(alpha = 0.65f)),
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onNewTaskClick)
                .testTag("top_new_task_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.Add, "New Task", tint = GlassColors.VioletBright, modifier = Modifier.size(18.dp))
                Text(
                    text = if (isCompact) "New" else "New Task",
                    style = MaterialTheme.typography.labelLarge,
                    color = GlassColors.TextPrimary
                )
            }
        }

        if (isCompact) {
            Surface(
                shape = CircleShape,
                color = GlassColors.GlassCard,
                border = BorderStroke(1.dp, GlassColors.GlassBorder),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onInsightsClick)
                    .testTag("top_insights_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Insights, "Insights", tint = GlassColors.VioletBright, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            Surface(
                shape = CircleShape,
                color = GlassColors.GlassCard,
                border = BorderStroke(1.dp, GlassColors.GlassBorder),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOnboardingClick)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.NotificationsNone, "Permissions Setup", tint = GlassColors.TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Surface(
                shape = CircleShape,
                color = GlassColors.VioletSoft,
                border = BorderStroke(1.dp, GlassColors.ActiveGlowBorder),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onInsightsClick)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "L${state.userLevel}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = AppJetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = GlassColors.VioletBright
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactFloatingBottomDock(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    val compactTabs = listOf(
        MainTab.HOME,
        MainTab.TASKS,
        MainTab.FOCUS,
        MainTab.APPS,
        MainTab.SETTINGS
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xEB101228),
            border = BorderStroke(1.dp, GlassColors.GlassBorder.copy(alpha = 0.85f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                compactTabs.forEach { tab ->
                    val selected = currentTab == tab
                    val pillBrush = if (selected) {
                        Brush.horizontalGradient(listOf(Color(0xD96D54FF), Color(0x994A39B8)))
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(18.dp))
                            .background(pillBrush)
                            .clickable { onSelectTab(tab) }
                            .testTag("bottom_nav_${tab.routeKey}")
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = iconForTab(tab),
                                contentDescription = tab.label,
                                tint = if (selected) GlassColors.TextPrimary else GlassColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) GlassColors.TextPrimary else GlassColors.TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun iconForTab(tab: MainTab): ImageVector = when (tab) {
    MainTab.HOME -> Icons.Rounded.Home
    MainTab.TASKS -> Icons.Rounded.TaskAlt
    MainTab.FOCUS -> Icons.Rounded.Timer
    MainTab.APPS -> Icons.Rounded.Apps
    MainTab.INSIGHTS -> Icons.Rounded.Insights
    MainTab.SETTINGS -> Icons.Rounded.Settings
}
