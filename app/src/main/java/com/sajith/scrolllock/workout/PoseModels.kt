package com.sajith.scrolllock.workout

/**
 * Pure Kotlin landmark IDs matching Google ML Kit PoseLandmark indices
 * so the workout engine remains 100% unit-testable on the JVM.
 */
object LandmarkId {
    const val NOSE = 0
    const val LEFT_EYE = 2
    const val RIGHT_EYE = 5
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
}

enum class ExerciseType(
    val displayName: String,
    val cameraGuide: String,
    val defaultTargetReps: Int,
    val categoryTag: String = "Full Body"
) {
    AUTO("Auto Detect", "Place phone 1.5-2m away showing your full body.", 10, "Smart AI"),
    PUSHUPS("Push-ups", "Side angle, full body from head to ankles visible.", 10, "Upper Body"),
    SQUATS("Squats", "45° or side angle, full body from head to ankles visible.", 10, "Lower Body"),
    LUNGES("Lunges", "Side angle with room to step forward and lower knee.", 10, "Lower Body"),
    JUMPING_JACKS("Jumping Jacks", "Front facing, arms overhead clearance visible.", 15, "Cardio"),
    HIGH_KNEES("High Knees", "Front or 45° angle, hips and knees clearly visible.", 16, "Cardio"),
    SHOULDER_PRESS("Overhead Press", "Front angle, hips to extended hands overhead visible.", 12, "Upper Body"),
    BICEP_CURLS("Arm Curls", "Front or 45° angle, shoulders, elbows and wrists visible.", 12, "Upper Body"),
    SIT_UPS("Sit-ups", "Side angle on floor, knees bent and chest to knees visible.", 12, "Core"),
    PLANKS("Plank Hold", "Side angle on floor, shoulders to ankles in frame.", 30, "Core")
}

enum class RepPhase {
    START,
    DESCENDING,
    BOTTOM,
    RETURNING
}

enum class FatigueLevel(val label: String) {
    FRESH("Fresh"),
    MODERATE("Moderate"),
    FATIGUED("Fatigued")
}

data class LandmarkPoint(
    val type: Int,
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val inFrameLikelihood: Float = 1.0f
)

data class PoseFrame(
    val timestampMs: Long,
    val landmarks: Map<Int, LandmarkPoint>,
    val jointAngles: Map<String, Float>,
    val errorJoints: Set<Int>,
    val feedback: String?
)

data class RepAnalysisRecord(
    val repNumber: Int,
    val exercise: ExerciseType,
    val isValid: Boolean,
    val rejectionReason: String?,
    val durationMs: Long,
    val minPrimaryAngle: Float,
    val maxPrimaryAngle: Float,
    val leftRightAsymmetryDeg: Float,
    val formQualityScore: Int,
    val postureCues: List<String>,
    val frames: List<PoseFrame>
)

data class CameraPositionStatus(
    val isReady: Boolean,
    val visibilityScore: Float,
    val lightingConfidence: Float,
    val lightingWarning: String?,
    val guidanceMessage: String,
    val recommendedAngle: String,
    val personCount: Int = 1
)

data class CalibrationProfile(
    val isCalibrated: Boolean = false,
    val sampleCount: Int = 0,
    val standingKneeAngle: Float = 172f,
    val torsoLength: Float = 0.25f,
    val standingHipAngle: Float = 170f,
    val standingElbowAngle: Float = 168f,
    val shoulderWidth: Float = 0.18f
)

data class FatigueStatus(
    val level: FatigueLevel = FatigueLevel.FRESH,
    val score: Int = 0,
    val recommendedRestSeconds: Int = 30,
    val message: String = "Pace and form are steady."
)

data class BalanceReport(
    val symmetryPercent: Int = 100,
    val leftAvgAngle: Float = 0f,
    val rightAvgAngle: Float = 0f,
    val suggestion: String = "Left and right sides are balanced."
)

data class WorkoutEvaluation(
    val detectedExercise: ExerciseType,
    val repCount: Int,
    val invalidRepCount: Int,
    val phase: RepPhase,
    val stateLabel: String,
    val primaryAngle: Float,
    val secondaryAngle: Float,
    val formQualityScore: Int,
    val errorJoints: Set<Int>,
    val postureCorrection: String?,
    val isCheatingDetected: Boolean,
    val cheatReason: String?,
    val plankHoldSeconds: Int,
    val isCalibrated: Boolean,
    val calibrationProgress: Float,
    val fatigueStatus: FatigueStatus,
    val balanceReport: BalanceReport,
    val justCompletedRep: RepAnalysisRecord? = null
)
