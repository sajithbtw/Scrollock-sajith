package com.sajith.scrolllock.ui.main

import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper
import com.sajith.scrolllock.data.model.AppItem
import com.sajith.scrolllock.data.model.FocusState
import com.sajith.scrolllock.data.model.FocusTask
import com.sajith.scrolllock.data.model.StudySessionRecord
import com.sajith.scrolllock.data.model.TopAppStat
import com.sajith.scrolllock.workout.ExerciseType

enum class MainTab(val routeKey: String, val label: String) {
    HOME("home", "Home"),
    TASKS("tasks", "Tasks"),
    FOCUS("focus", "Focus"),
    APPS("apps", "Apps"),
    INSIGHTS("insights", "Insights"),
    SETTINGS("settings", "Settings")
}

data class MainUiState(
    val currentTab: MainTab = MainTab.HOME,
    val searchQuery: String = "",
    val greeting: String = "Welcome back",
    val focusState: FocusState = FocusState.IDLE,
    val remainingMillis: Long = 25 * 60_000L,
    val totalMillis: Long = 25 * 60_000L,
    val sessionInterruptions: Int = 0,
    val selectedAppsCount: Int = 0,
    val shortFormBlockedCount: Int = 0,
    val todayDeviceUsageMillis: Long = 0L,
    val todayGuardedUsageMillis: Long = 0L,
    val hasUsageAccess: Boolean = false,
    val hasDndAccess: Boolean = false,
    val hasCameraPerm: Boolean = false,
    val hasNotificationPerm: Boolean = true,
    val hasBatteryExemption: Boolean = false,
    val deepFocusEnabled: Boolean = false,
    val deepFocusHidePreviews: Boolean = true,
    val deepFocusBlockInternet: Boolean = true,
    val deepFocusAllowedCount: Int = 0,
    val deepFocusShieldedPreviewCount: Int = 0,
    val customDurationMinutes: Int = 90,
    val defaultDurationMinutes: Int = 25,
    val tasks: List<FocusTask> = emptyList(),
    val recentStudySessions: List<StudySessionRecord> = emptyList(),
    val todayStudyPages: Int = 0,
    val totalStudyPages: Int = 0,
    val todayWorkoutReps: Int = 0,
    val totalWorkoutReps: Int = 0,
    val workoutSessionsCount: Int = 0,
    val averageFormScore: Int = 0,
    val totalFocusSeconds: Int = 0,
    val completedSessionsCount: Int = 0,
    val totalInterruptionsCount: Int = 0,
    val weeklyFocusMinutes: Map<String, Int> = emptyMap(),
    val topApps: List<TopAppStat> = emptyList(),
    val personalBests: List<ScrollLockDatabaseHelper.PersonalBestItem> = emptyList(),
    val achievements: List<ScrollLockDatabaseHelper.AchievementBadge> = emptyList(),
    val userXp: Int = 0,
    val userLevel: Int = 1,
    val workoutStreakDays: Int = 0,
    val preferredExercise: ExerciseType = ExerciseType.SQUATS,
    val requiredReps: Int = 10,
    val unlockDurationMinutes: Int = 15,
    val defaultStudyPages: Int = 10,
    val defaultStudySubject: String = "Focused Reading",
    val voiceCoachEnabled: Boolean = true,
    val ghostTrainerEnabled: Boolean = true,
    val mirrorModeEnabled: Boolean = false,
    val shortFormBlockerEnabled: Boolean = true,
    val alwaysGuardEnabled: Boolean = true,
    val workoutUnlockEnabled: Boolean = true,
    val installedApps: List<AppItem> = emptyList(),
    val appsLoading: Boolean = false
)
