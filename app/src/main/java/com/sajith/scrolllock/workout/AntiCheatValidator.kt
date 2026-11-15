package com.sajith.scrolllock.workout

/**
 * Enforces anti-cheat validation across exercises:
 * - Full-body / essential landmark visibility verification
 * - Camera movement & sudden frame-jump detection
 * - Minimum repetition duration (> 1000ms) to block rapid fake reps
 * - Full depth & sequence verification to reject half-reps
 */
class AntiCheatValidator(
    private val minRepDurationMs: Long = 1000L,
    private val minConfidence: Float = 0.50f
) {
    private var prevCenterX: Float? = null
    private var prevCenterY: Float? = null
    private var lastRepCompletedAtMs: Long = 0L

    data class CheatCheckResult(
        val isClean: Boolean,
        val reason: String? = null
    )

    fun reset() {
        prevCenterX = null
        prevCenterY = null
        lastRepCompletedAtMs = 0L
    }

    /**
     * Ensures essential landmarks for the active exercise are visible and camera is stable.
     */
    fun validateFrame(
        exercise: ExerciseType,
        landmarks: Map<Int, LandmarkPoint>,
        requireFullBody: Boolean = true
    ): CheatCheckResult {
        if (landmarks.isEmpty()) {
            return CheatCheckResult(false, "Step into camera view so your body is visible.")
        }

        val requiredIds = when (exercise) {
            ExerciseType.PUSHUPS, ExerciseType.PLANKS,
            ExerciseType.SHOULDER_PRESS, ExerciseType.BICEP_CURLS -> listOf(
                LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
                LandmarkId.LEFT_ELBOW, LandmarkId.RIGHT_ELBOW,
                LandmarkId.LEFT_WRIST, LandmarkId.RIGHT_WRIST,
                LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP
            )
            ExerciseType.SIT_UPS, ExerciseType.HIGH_KNEES,
            ExerciseType.SQUATS, ExerciseType.LUNGES,
            ExerciseType.JUMPING_JACKS, ExerciseType.AUTO -> listOf(
                LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
                LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
                LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
                LandmarkId.LEFT_ANKLE, LandmarkId.RIGHT_ANKLE
            )
        }

        val isUpperBodyOnly = exercise == ExerciseType.SHOULDER_PRESS || exercise == ExerciseType.BICEP_CURLS
        val hasLeftChain = if (isUpperBodyOnly) {
            PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_SHOULDER], minConfidence) &&
                PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_ELBOW], minConfidence) &&
                PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_WRIST], minConfidence)
        } else {
            PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_SHOULDER], minConfidence) &&
                PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_HIP], minConfidence) &&
                PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_KNEE], minConfidence) &&
                PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_ANKLE], minConfidence)
        }

        val hasRightChain = if (isUpperBodyOnly) {
            PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_SHOULDER], minConfidence) &&
                PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_ELBOW], minConfidence) &&
                PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_WRIST], minConfidence)
        } else {
            PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_SHOULDER], minConfidence) &&
                PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_HIP], minConfidence) &&
                PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_KNEE], minConfidence) &&
                PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_ANKLE], minConfidence)
        }

        if (requireFullBody && !hasLeftChain && !hasRightChain) {
            return CheatCheckResult(
                false,
                if (isUpperBodyOnly) {
                    "Upper body required in frame (shoulders, elbows, and wrists)."
                } else {
                    "Full body required in frame (shoulders to ankles)."
                }
            )
        }

        val visibleCount = requiredIds.count { PoseGeometry.isVisible(landmarks[it], minConfidence) }
        if (visibleCount < 3) {
            return CheatCheckResult(false, "Too many body landmarks obscured.")
        }

        // Camera jump / sudden shake detection using hip midpoint
        val lh = landmarks[LandmarkId.LEFT_HIP]
        val rh = landmarks[LandmarkId.RIGHT_HIP]
        if (lh != null && rh != null) {
            val cx = (lh.x + rh.x) * 0.5f
            val cy = (lh.y + rh.y) * 0.5f
            val px = prevCenterX
            val py = prevCenterY
            prevCenterX = cx
            prevCenterY = cy
            if (px != null && py != null) {
                val shift = kotlin.math.hypot((cx - px).toDouble(), (cy - py).toDouble())
                if (shift > 0.18) {
                    return CheatCheckResult(false, "Hold camera steady — sudden frame jump detected.")
                }
            }
        }

        return CheatCheckResult(true)
    }

    /**
     * Validates a completed movement cycle against minimum rep duration, cooldown, and full depth.
     */
    fun validateCompletedRep(
        timestampMs: Long,
        repStartMs: Long,
        reachedBottom: Boolean,
        exercise: ExerciseType,
        minAngleReached: Float,
        maxAngleReached: Float
    ): CheatCheckResult {
        val duration = timestampMs - repStartMs
        if (!reachedBottom) {
            return CheatCheckResult(false, "Half rep rejected — reach full depth.")
        }
        if (duration < minRepDurationMs) {
            return CheatCheckResult(false, "Too fast (${duration}ms) — control each rep for ≥1s.")
        }
        if (lastRepCompletedAtMs > 0L && (timestampMs - lastRepCompletedAtMs) < 350L) {
            return CheatCheckResult(false, "Duplicate rep ignored.")
        }

        // Range of motion verification per exercise
        val rom = maxAngleReached - minAngleReached
        val minRequiredRom = when (exercise) {
            ExerciseType.SQUATS -> 45f
            ExerciseType.PUSHUPS -> 45f
            ExerciseType.LUNGES -> 40f
            ExerciseType.JUMPING_JACKS -> 55f
            ExerciseType.HIGH_KNEES -> 36f
            ExerciseType.SHOULDER_PRESS -> 40f
            ExerciseType.BICEP_CURLS -> 50f
            ExerciseType.SIT_UPS -> 38f
            ExerciseType.PLANKS, ExerciseType.AUTO -> 30f
        }
        if (rom < minRequiredRom) {
            return CheatCheckResult(false, "Incomplete range of motion (${rom.toInt()}°).")
        }

        lastRepCompletedAtMs = timestampMs
        return CheatCheckResult(true)
    }
}
