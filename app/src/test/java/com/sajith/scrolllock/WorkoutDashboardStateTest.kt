package com.sajith.scrolllock

import com.sajith.scrolllock.ui.workout.WorkoutDashboardReducer
import com.sajith.scrolllock.ui.workout.WorkoutDashboardState
import com.sajith.scrolllock.workout.BalanceReport
import com.sajith.scrolllock.workout.ExerciseType
import com.sajith.scrolllock.workout.FatigueLevel
import com.sajith.scrolllock.workout.FatigueStatus
import com.sajith.scrolllock.workout.RepAnalysisRecord
import com.sajith.scrolllock.workout.RepPhase
import com.sajith.scrolllock.workout.WorkoutEvaluation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDashboardStateTest {

    @Test
    fun formatTimerProducesPadZeroMmSs() {
        assertEquals("00:00", WorkoutDashboardReducer.formatTimer(0L))
        assertEquals("00:09", WorkoutDashboardReducer.formatTimer(9L))
        assertEquals("01:25", WorkoutDashboardReducer.formatTimer(85L))
        assertEquals("12:00", WorkoutDashboardReducer.formatTimer(720L))
    }

    @Test
    fun applyEvaluationIncrementsCompletedRepEventAndCapturesFormBadge() {
        val initial = WorkoutDashboardState(
            currentReps = 0,
            targetReps = 10,
            selectedExercise = ExerciseType.SQUATS
        )
        val completedRecord = RepAnalysisRecord(
            repNumber = 1,
            exercise = ExerciseType.SQUATS,
            isValid = true,
            rejectionReason = null,
            durationMs = 1550L,
            minPrimaryAngle = 86f,
            maxPrimaryAngle = 172f,
            leftRightAsymmetryDeg = 3.2f,
            formQualityScore = 96,
            postureCues = emptyList(),
            frames = emptyList()
        )
        val eval = WorkoutEvaluation(
            detectedExercise = ExerciseType.SQUATS,
            repCount = 1,
            invalidRepCount = 0,
            phase = RepPhase.START,
            stateLabel = "UP",
            primaryAngle = 170f,
            secondaryAngle = 168f,
            formQualityScore = 96,
            errorJoints = emptySet(),
            postureCorrection = null,
            isCheatingDetected = false,
            cheatReason = null,
            plankHoldSeconds = 0,
            isCalibrated = true,
            calibrationProgress = 1f,
            fatigueStatus = FatigueStatus(FatigueLevel.FRESH, 10, 30, "Steady"),
            balanceReport = BalanceReport(98, 170f, 169f, "Balanced"),
            justCompletedRep = completedRecord
        )

        val updated = WorkoutDashboardReducer.applyEvaluation(initial, eval, recordedReplayCount = 1)
        assertEquals(1, updated.currentReps)
        assertEquals(1, updated.completedRepEventId)
        assertTrue(updated.lastCompletedRepValid)
        assertEquals(96, updated.lastCompletedRepFormScore)
        assertTrue(updated.postureFeedback.contains("Rep #1 Complete"))
        assertFalse(updated.isAlertFeedback)
    }

    @Test
    fun tickOneSecondTracksElapsedAndRestCountdownCleanly() {
        val active = WorkoutDashboardState(elapsedSeconds = 12L, isPaused = false, isResting = false)
        val afterTick = WorkoutDashboardReducer.tickOneSecond(active, sessionEnded = false)
        assertEquals(13L, afterTick.elapsedSeconds)

        val resting = active.copy(isResting = true, restRemainingSeconds = 1)
        val restDone = WorkoutDashboardReducer.tickOneSecond(resting, sessionEnded = false)
        assertFalse(restDone.isResting)
        assertEquals(0, restDone.restRemainingSeconds)
        assertEquals(12L, restDone.elapsedSeconds)
    }

    @Test
    fun plankModeUpdatesPlankRemainingCountdown() {
        val initial = WorkoutDashboardState(
            targetReps = 30,
            selectedExercise = ExerciseType.PLANKS,
            detectedExercise = ExerciseType.PLANKS,
            plankRemainingSeconds = 30
        )
        val eval = WorkoutEvaluation(
            detectedExercise = ExerciseType.PLANKS,
            repCount = 14,
            invalidRepCount = 0,
            phase = RepPhase.BOTTOM,
            stateLabel = "HOLD",
            primaryAngle = 174f,
            secondaryAngle = 172f,
            formQualityScore = 94,
            errorJoints = emptySet(),
            postureCorrection = null,
            isCheatingDetected = false,
            cheatReason = null,
            plankHoldSeconds = 14,
            isCalibrated = true,
            calibrationProgress = 1f,
            fatigueStatus = FatigueStatus(),
            balanceReport = BalanceReport()
        )
        val updated = WorkoutDashboardReducer.applyEvaluation(initial, eval, recordedReplayCount = 0)
        assertEquals(16, updated.plankRemainingSeconds)
    }
}
