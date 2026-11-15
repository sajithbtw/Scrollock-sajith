package com.sajith.scrolllock.workout

import kotlin.math.abs

/**
 * Result produced by an exercise-specific detection engine for a single pose frame.
 */
data class ExerciseFrameSignal(
    val primaryAngle: Float,
    val secondaryAngle: Float,
    val isAtTop: Boolean,
    val isAtBottom: Boolean,
    val isDescending: Boolean,
    val formQualityScore: Int,
    val errorJoints: Set<Int>,
    val postureCue: String?,
    val jointAnglesMap: Map<String, Float>,
    val leftMeasureAngle: Float = primaryAngle,
    val rightMeasureAngle: Float = primaryAngle,
    val isPlankHolding: Boolean = false
)

/**
 * Modular exercise-specific detection and posture correction engines.
 */
object ExerciseDetectors {

    fun evaluate(
        exercise: ExerciseType,
        landmarks: Map<Int, LandmarkPoint>,
        calibrator: DynamicCalibrator
    ): ExerciseFrameSignal {
        return when (exercise) {
            ExerciseType.PUSHUPS -> detectPushup(landmarks, calibrator)
            ExerciseType.SQUATS -> detectSquat(landmarks, calibrator)
            ExerciseType.LUNGES -> detectLunge(landmarks)
            ExerciseType.JUMPING_JACKS -> detectJumpingJack(landmarks, calibrator)
            ExerciseType.HIGH_KNEES -> detectHighKnees(landmarks)
            ExerciseType.SHOULDER_PRESS -> detectShoulderPress(landmarks)
            ExerciseType.BICEP_CURLS -> detectBicepCurls(landmarks)
            ExerciseType.SIT_UPS -> detectSitUps(landmarks)
            ExerciseType.PLANKS -> detectPlank(landmarks)
            ExerciseType.AUTO -> {
                val classified = classifyExercise(landmarks, calibrator)
                evaluate(classified, landmarks, calibrator)
            }
        }
    }

    /**
     * Automatically identifies the exercise from body orientation and limb configuration.
     */
    fun classifyExercise(
        landmarks: Map<Int, LandmarkPoint>,
        calibrator: DynamicCalibrator
    ): ExerciseType {
        val useLeft = PoseGeometry.preferLeftSide(landmarks)
        val s = landmarks[if (useLeft) LandmarkId.LEFT_SHOULDER else LandmarkId.RIGHT_SHOULDER]
        val e = landmarks[if (useLeft) LandmarkId.LEFT_ELBOW else LandmarkId.RIGHT_ELBOW]
        val w = landmarks[if (useLeft) LandmarkId.LEFT_WRIST else LandmarkId.RIGHT_WRIST]
        val h = landmarks[if (useLeft) LandmarkId.LEFT_HIP else LandmarkId.RIGHT_HIP]
        val k = landmarks[if (useLeft) LandmarkId.LEFT_KNEE else LandmarkId.RIGHT_KNEE]
        val a = landmarks[if (useLeft) LandmarkId.LEFT_ANKLE else LandmarkId.RIGHT_ANKLE]

        if (s == null || h == null) return ExerciseType.SQUATS

        val torsoTilt = PoseGeometry.calculateTorsoTiltFromVertical(s, h)

        // Horizontal or supine posture -> Push-up vs Sit-up vs Plank Hold
        if (torsoTilt >= 48f) {
            val kAngle = if (h != null && k != null && a != null) PoseGeometry.calculateAngle(h, k, a) else 170f
            if (kAngle < 130f && s.y >= h.y - 0.15f) {
                return ExerciseType.SIT_UPS
            }
            val elbowAngle = if (e != null && w != null) PoseGeometry.calculateAngle(s, e, w) else 170f
            return if (elbowAngle < 142f) ExerciseType.PUSHUPS else ExerciseType.PLANKS
        }

        // Upright posture -> Jumping Jacks vs Overhead Press vs Arm Curls vs High Knees vs Lunges vs Squats
        val lw = landmarks[LandmarkId.LEFT_WRIST]
        val rw = landmarks[LandmarkId.RIGHT_WRIST]
        val ls = landmarks[LandmarkId.LEFT_SHOULDER]
        val rs = landmarks[LandmarkId.RIGHT_SHOULDER]
        val le = landmarks[LandmarkId.LEFT_ELBOW]
        val re = landmarks[LandmarkId.RIGHT_ELBOW]
        val lk = landmarks[LandmarkId.LEFT_KNEE]
        val rk = landmarks[LandmarkId.RIGHT_KNEE]
        val lh = landmarks[LandmarkId.LEFT_HIP]
        val rh = landmarks[LandmarkId.RIGHT_HIP]
        val la = landmarks[LandmarkId.LEFT_ANKLE]
        val ra = landmarks[LandmarkId.RIGHT_ANKLE]

        if (lw != null && rw != null && ls != null && rs != null) {
            val wristsOverhead = lw.y < ls.y - 0.04f && rw.y < rs.y - 0.04f
            if (wristsOverhead) {
                val ankleSpread = if (la != null && ra != null) abs(la.x - ra.x) else 0.1f
                val shoulderW = calibrator.profile.shoulderWidth.coerceAtLeast(0.12f)
                return if (ankleSpread > shoulderW * 1.25f) ExerciseType.JUMPING_JACKS else ExerciseType.SHOULDER_PRESS
            }
            if (le != null && re != null && lw.y < le.y - 0.04f && rw.y < re.y - 0.04f) {
                return ExerciseType.BICEP_CURLS
            }
        }

        if (lk != null && rk != null && lh != null && rh != null && ls != null && rs != null) {
            val leftHipLift = PoseGeometry.calculateAngle(ls, lh, lk)
            val rightHipLift = PoseGeometry.calculateAngle(rs, rh, rk)
            if (abs(leftHipLift - rightHipLift) > 35f && minOf(leftHipLift, rightHipLift) < 118f) {
                return ExerciseType.HIGH_KNEES
            }
        }

        if (lk != null && rk != null && lh != null && rh != null && la != null && ra != null) {
            val leftKnee = PoseGeometry.calculateAngle(lh, lk, la)
            val rightKnee = PoseGeometry.calculateAngle(rh, rk, ra)
            val ankleSpreadX = abs(la.x - ra.x)
            val shoulderW = calibrator.profile.shoulderWidth.coerceAtLeast(0.12f)
            if (abs(leftKnee - rightKnee) > 25f || ankleSpreadX > shoulderW * 1.9f) {
                return ExerciseType.LUNGES
            }
        }

        if (k != null && a != null) {
            return ExerciseType.SQUATS
        }
        return ExerciseType.SQUATS
    }

    /**
     * Push-up Detector:
     * Key Landmarks: Shoulder -> Elbow -> Wrist (primary elbow flexion), Shoulder -> Hip -> Ankle (plank alignment).
     */
    private fun detectPushup(
        landmarks: Map<Int, LandmarkPoint>,
        calibrator: DynamicCalibrator
    ): ExerciseFrameSignal {
        val useLeft = PoseGeometry.preferLeftSide(landmarks)
        val ls = landmarks[LandmarkId.LEFT_SHOULDER]
        val le = landmarks[LandmarkId.LEFT_ELBOW]
        val lw = landmarks[LandmarkId.LEFT_WRIST]
        val rs = landmarks[LandmarkId.RIGHT_SHOULDER]
        val re = landmarks[LandmarkId.RIGHT_ELBOW]
        val rw = landmarks[LandmarkId.RIGHT_WRIST]

        val leftElbow = if (ls != null && le != null && lw != null) PoseGeometry.calculateAngle(ls, le, lw) else 170f
        val rightElbow = if (rs != null && re != null && rw != null) PoseGeometry.calculateAngle(rs, re, rw) else 170f
        val elbowAngle = if (useLeft) leftElbow else rightElbow

        val s = if (useLeft) ls else rs
        val h = landmarks[if (useLeft) LandmarkId.LEFT_HIP else LandmarkId.RIGHT_HIP]
        val a = landmarks[if (useLeft) LandmarkId.LEFT_ANKLE else LandmarkId.RIGHT_ANKLE]

        val bodyLineAngle = if (s != null && h != null && a != null) {
            PoseGeometry.calculateAngle(s, h, a)
        } else 175f

        val downThreshold = calibrator.getPushupDownThreshold()
        val upThreshold = calibrator.getPushupUpThreshold()

        val errors = mutableSetOf<Int>()
        var cue: String? = null
        var quality = 100

        if (bodyLineAngle < 152f) {
            errors.add(LandmarkId.LEFT_HIP)
            errors.add(LandmarkId.RIGHT_HIP)
            cue = "Keep your body straight — maintain proper body alignment"
            quality -= 25
        } else if (elbowAngle in (downThreshold + 2f)..(upThreshold - 18f)) {
            errors.add(if (useLeft) LandmarkId.LEFT_ELBOW else LandmarkId.RIGHT_ELBOW)
            cue = "Lower your chest closer to the floor"
            quality -= 10
        }

        val angles = mapOf("Elbow" to elbowAngle, "BodyLine" to bodyLineAngle)
        return ExerciseFrameSignal(
            primaryAngle = elbowAngle,
            secondaryAngle = bodyLineAngle,
            isAtTop = elbowAngle >= upThreshold,
            isAtBottom = elbowAngle <= downThreshold,
            isDescending = elbowAngle < upThreshold - 12f,
            formQualityScore = quality.coerceIn(40, 100),
            errorJoints = errors,
            postureCue = cue,
            jointAnglesMap = angles,
            leftMeasureAngle = leftElbow,
            rightMeasureAngle = rightElbow
        )
    }

    /**
     * Squat Detector:
     * Key Landmarks: Hip -> Knee -> Ankle (knee flexion), Shoulder -> Hip -> Knee (hip hinge),
     * and Shoulder -> Hip (torso tilt vs vertical).
     */
    private fun detectSquat(
        landmarks: Map<Int, LandmarkPoint>,
        calibrator: DynamicCalibrator
    ): ExerciseFrameSignal {
        val useLeft = PoseGeometry.preferLeftSide(landmarks)
        val ls = landmarks[LandmarkId.LEFT_SHOULDER]
        val lh = landmarks[LandmarkId.LEFT_HIP]
        val lk = landmarks[LandmarkId.LEFT_KNEE]
        val la = landmarks[LandmarkId.LEFT_ANKLE]
        val rs = landmarks[LandmarkId.RIGHT_SHOULDER]
        val rh = landmarks[LandmarkId.RIGHT_HIP]
        val rk = landmarks[LandmarkId.RIGHT_KNEE]
        val ra = landmarks[LandmarkId.RIGHT_ANKLE]

        val leftKnee = if (lh != null && lk != null && la != null) PoseGeometry.calculateAngle(lh, lk, la) else 172f
        val rightKnee = if (rh != null && rk != null && ra != null) PoseGeometry.calculateAngle(rh, rk, ra) else 172f
        val primaryKnee = if (useLeft) leftKnee else rightKnee

        val s = if (useLeft) ls else rs
        val h = if (useLeft) lh else rh
        val k = if (useLeft) lk else rk

        val hipAngle = if (s != null && h != null && k != null) PoseGeometry.calculateAngle(s, h, k) else 170f
        val torsoTilt = if (s != null && h != null) PoseGeometry.calculateTorsoTiltFromVertical(s, h) else 10f

        val downThreshold = calibrator.getSquatDownThreshold()
        val upThreshold = calibrator.getSquatUpThreshold()

        val errors = mutableSetOf<Int>()
        var cue: String? = null
        var quality = 100

        if (torsoTilt > 45f) {
            errors.add(LandmarkId.LEFT_SHOULDER)
            errors.add(LandmarkId.RIGHT_SHOULDER)
            errors.add(LandmarkId.LEFT_HIP)
            cue = "Keep your back straight"
            quality -= 25
        } else if (abs(leftKnee - rightKnee) > 22f && primaryKnee < 145f) {
            errors.add(LandmarkId.LEFT_KNEE)
            errors.add(LandmarkId.RIGHT_KNEE)
            cue = "Maintain proper body alignment across both knees"
            quality -= 15
        } else if (primaryKnee in (downThreshold + 5f)..(upThreshold - 22f)) {
            errors.add(if (useLeft) LandmarkId.LEFT_KNEE else LandmarkId.RIGHT_KNEE)
            cue = "Bend your knees more"
            quality -= 10
        }

        val angles = mapOf("Knee" to primaryKnee, "Hip" to hipAngle, "Torso" to torsoTilt)
        return ExerciseFrameSignal(
            primaryAngle = primaryKnee,
            secondaryAngle = hipAngle,
            isAtTop = primaryKnee >= upThreshold && hipAngle >= 145f,
            isAtBottom = primaryKnee <= downThreshold && hipAngle <= 125f,
            isDescending = primaryKnee < upThreshold - 10f,
            formQualityScore = quality.coerceIn(40, 100),
            errorJoints = errors,
            postureCue = cue,
            jointAnglesMap = angles,
            leftMeasureAngle = leftKnee,
            rightMeasureAngle = rightKnee
        )
    }

    /**
     * Lunge Detector:
     * Tracks minimum knee flexion across left/right legs and upright torso stability.
     */
    private fun detectLunge(landmarks: Map<Int, LandmarkPoint>): ExerciseFrameSignal {
        val lh = landmarks[LandmarkId.LEFT_HIP]
        val lk = landmarks[LandmarkId.LEFT_KNEE]
        val la = landmarks[LandmarkId.LEFT_ANKLE]
        val rh = landmarks[LandmarkId.RIGHT_HIP]
        val rk = landmarks[LandmarkId.RIGHT_KNEE]
        val ra = landmarks[LandmarkId.RIGHT_ANKLE]
        val ls = landmarks[LandmarkId.LEFT_SHOULDER]

        val leftKnee = if (lh != null && lk != null && la != null) PoseGeometry.calculateAngle(lh, lk, la) else 172f
        val rightKnee = if (rh != null && rk != null && ra != null) PoseGeometry.calculateAngle(rh, rk, ra) else 172f
        val activeKnee = minOf(leftKnee, rightKnee)
        val torsoTilt = if (ls != null && lh != null) PoseGeometry.calculateTorsoTiltFromVertical(ls, lh) else 10f

        val errors = mutableSetOf<Int>()
        var cue: String? = null
        var quality = 100

        if (torsoTilt > 32f) {
            errors.add(LandmarkId.LEFT_SHOULDER)
            errors.add(LandmarkId.LEFT_HIP)
            cue = "Keep your back straight and torso upright"
            quality -= 25
        } else if (activeKnee in 110f..145f) {
            errors.add(LandmarkId.LEFT_KNEE)
            errors.add(LandmarkId.RIGHT_KNEE)
            cue = "Bend your knees more to complete the lunge"
            quality -= 10
        }

        val angles = mapOf("Knee" to activeKnee, "Torso" to torsoTilt)
        return ExerciseFrameSignal(
            primaryAngle = activeKnee,
            secondaryAngle = torsoTilt,
            isAtTop = leftKnee >= 154f && rightKnee >= 154f,
            isAtBottom = activeKnee <= 106f,
            isDescending = activeKnee < 148f,
            formQualityScore = quality.coerceIn(40, 100),
            errorJoints = errors,
            postureCue = cue,
            jointAnglesMap = angles,
            leftMeasureAngle = leftKnee,
            rightMeasureAngle = rightKnee
        )
    }

    /**
     * Jumping Jack Detector:
     * Uses Hip -> Shoulder -> Wrist abduction angle (mapped to inverted range so RepCounterEngine
     * handles Top=arms down ~25°/180° consistently by converting to 180° - armAngle).
     */
    private fun detectJumpingJack(
        landmarks: Map<Int, LandmarkPoint>,
        calibrator: DynamicCalibrator
    ): ExerciseFrameSignal {
        val ls = landmarks[LandmarkId.LEFT_SHOULDER]
        val lw = landmarks[LandmarkId.LEFT_WRIST]
        val lh = landmarks[LandmarkId.LEFT_HIP]
        val rs = landmarks[LandmarkId.RIGHT_SHOULDER]
        val rw = landmarks[LandmarkId.RIGHT_WRIST]
        val rh = landmarks[LandmarkId.RIGHT_HIP]
        val la = landmarks[LandmarkId.LEFT_ANKLE]
        val ra = landmarks[LandmarkId.RIGHT_ANKLE]

        val leftArm = if (lh != null && ls != null && lw != null) PoseGeometry.calculateAngle(lh, ls, lw) else 20f
        val rightArm = if (rh != null && rs != null && rw != null) PoseGeometry.calculateAngle(rh, rs, rw) else 20f
        val avgArm = (leftArm + rightArm) * 0.5f

        val ankleSpread = if (la != null && ra != null) abs(la.x - ra.x) else 0.1f
        val shoulderW = calibrator.profile.shoulderWidth.coerceAtLeast(0.12f)
        val feetWide = ankleSpread > shoulderW * 1.25f

        // Normalize into descending-then-ascending angle curve: closed = 165°, overhead open = 40°
        val normalizedSignalAngle = (180f - avgArm).coerceIn(20f, 175f)

        val errors = mutableSetOf<Int>()
        var cue: String? = null
        var quality = 100

        if (avgArm > 95f && !feetWide) {
            errors.add(LandmarkId.LEFT_ANKLE)
            errors.add(LandmarkId.RIGHT_ANKLE)
            cue = "Jump your feet wider apart as arms go up"
            quality -= 20
        }

        val angles = mapOf("ArmRaise" to avgArm, "AnkleSpread" to ankleSpread * 100f)
        return ExerciseFrameSignal(
            primaryAngle = normalizedSignalAngle,
            secondaryAngle = avgArm,
            isAtTop = avgArm <= 48f,
            isAtBottom = avgArm >= 128f && feetWide,
            isDescending = avgArm > 62f,
            formQualityScore = quality.coerceIn(40, 100),
            errorJoints = errors,
            postureCue = cue,
            jointAnglesMap = angles,
            leftMeasureAngle = leftArm,
            rightMeasureAngle = rightArm
        )
    }

    /**
     * Plank Hold Detector:
     * Validates straight Shoulder -> Hip -> Ankle alignment while horizontal.
     */
    private fun detectPlank(landmarks: Map<Int, LandmarkPoint>): ExerciseFrameSignal {
        val useLeft = PoseGeometry.preferLeftSide(landmarks)
        val s = landmarks[if (useLeft) LandmarkId.LEFT_SHOULDER else LandmarkId.RIGHT_SHOULDER]
        val h = landmarks[if (useLeft) LandmarkId.LEFT_HIP else LandmarkId.RIGHT_HIP]
        val a = landmarks[if (useLeft) LandmarkId.LEFT_ANKLE else LandmarkId.RIGHT_ANKLE]

        val bodyLine = if (s != null && h != null && a != null) PoseGeometry.calculateAngle(s, h, a) else 170f
        val torsoTilt = if (s != null && h != null) PoseGeometry.calculateTorsoTiltFromVertical(s, h) else 75f

        val errors = mutableSetOf<Int>()
        var cue: String? = null
        var quality = 100
        val holdingGood = torsoTilt >= 52f && bodyLine >= 156f

        if (torsoTilt < 52f) {
            errors.add(LandmarkId.LEFT_SHOULDER)
            errors.add(LandmarkId.LEFT_HIP)
            cue = "Get into horizontal plank position"
            quality -= 35
        } else if (bodyLine < 156f) {
            errors.add(LandmarkId.LEFT_HIP)
            errors.add(LandmarkId.RIGHT_HIP)
            cue = "Maintain proper body alignment — keep hips level"
            quality -= 25
        }

        val angles = mapOf("BodyLine" to bodyLine, "TorsoTilt" to torsoTilt)
        return ExerciseFrameSignal(
            primaryAngle = bodyLine,
            secondaryAngle = torsoTilt,
            isAtTop = true,
            isAtBottom = false,
            isDescending = false,
            formQualityScore = quality.coerceIn(40, 100),
            errorJoints = errors,
            postureCue = cue,
            jointAnglesMap = angles,
            isPlankHolding = holdingGood
        )
    }

    /**
     * High Knees Detector:
     * Tracks hip flexion (Shoulder -> Hip -> Knee) as alternating knees lift to hip height.
     */
    private fun detectHighKnees(landmarks: Map<Int, LandmarkPoint>): ExerciseFrameSignal {
        val ls = landmarks[LandmarkId.LEFT_SHOULDER]
        val lh = landmarks[LandmarkId.LEFT_HIP]
        val lk = landmarks[LandmarkId.LEFT_KNEE]
        val rs = landmarks[LandmarkId.RIGHT_SHOULDER]
        val rh = landmarks[LandmarkId.RIGHT_HIP]
        val rk = landmarks[LandmarkId.RIGHT_KNEE]

        val leftHip = if (ls != null && lh != null && lk != null) PoseGeometry.calculateAngle(ls, lh, lk) else 172f
        val rightHip = if (rs != null && rh != null && rk != null) PoseGeometry.calculateAngle(rs, rh, rk) else 172f
        val activeHip = minOf(leftHip, rightHip)
        val torsoTilt = if (ls != null && lh != null) PoseGeometry.calculateTorsoTiltFromVertical(ls, lh) else 8f

        val errors = mutableSetOf<Int>()
        var cue: String? = null
        var quality = 100

        if (torsoTilt > 32f) {
            errors.add(LandmarkId.LEFT_SHOULDER)
            errors.add(LandmarkId.LEFT_HIP)
            cue = "Keep your chest tall — avoid leaning back"
            quality -= 20
        } else if (activeHip in 112f..140f) {
            errors.add(if (leftHip <= rightHip) LandmarkId.LEFT_KNEE else LandmarkId.RIGHT_KNEE)
            cue = "Drive your knee higher up toward hip level"
            quality -= 10
        }

        val angles = mapOf("HipLift" to activeHip, "Torso" to torsoTilt)
        return ExerciseFrameSignal(
            primaryAngle = activeHip,
            secondaryAngle = torsoTilt,
            isAtTop = leftHip >= 146f && rightHip >= 146f,
            isAtBottom = activeHip <= 106f,
            isDescending = activeHip < 138f,
            formQualityScore = quality.coerceIn(40, 100),
            errorJoints = errors,
            postureCue = cue,
            jointAnglesMap = angles,
            leftMeasureAngle = leftHip,
            rightMeasureAngle = rightHip
        )
    }

    /**
     * Overhead Shoulder Press Detector:
     * Inverted abduction angle so Start (hands racked at shoulders ~85°) maps to ~95°–125°
     * and Peak Overhead Press (~165°) maps to ~15°–35° consistently with RepCounterEngine.
     */
    private fun detectShoulderPress(landmarks: Map<Int, LandmarkPoint>): ExerciseFrameSignal {
        val lh = landmarks[LandmarkId.LEFT_HIP]
        val ls = landmarks[LandmarkId.LEFT_SHOULDER]
        val le = landmarks[LandmarkId.LEFT_ELBOW]
        val lw = landmarks[LandmarkId.LEFT_WRIST]
        val rh = landmarks[LandmarkId.RIGHT_HIP]
        val rs = landmarks[LandmarkId.RIGHT_SHOULDER]
        val re = landmarks[LandmarkId.RIGHT_ELBOW]
        val rw = landmarks[LandmarkId.RIGHT_WRIST]

        val leftArm = if (lh != null && ls != null && lw != null) PoseGeometry.calculateAngle(lh, ls, lw) else 80f
        val rightArm = if (rh != null && rs != null && rw != null) PoseGeometry.calculateAngle(rh, rs, rw) else 80f
        val avgArm = (leftArm + rightArm) * 0.5f

        val leftElbow = if (ls != null && le != null && lw != null) PoseGeometry.calculateAngle(ls, le, lw) else 95f
        val rightElbow = if (rs != null && re != null && rw != null) PoseGeometry.calculateAngle(rs, re, rw) else 95f
        val avgElbow = (leftElbow + rightElbow) * 0.5f

        val normalizedAngle = (180f - avgArm).coerceIn(10f, 170f)
        val errors = mutableSetOf<Int>()
        var cue: String? = null
        var quality = 100

        if (abs(leftArm - rightArm) > 22f && avgArm > 105f) {
            errors.add(LandmarkId.LEFT_WRIST)
            errors.add(LandmarkId.RIGHT_WRIST)
            cue = "Press both arms overhead evenly"
            quality -= 15
        } else if (avgArm > 110f && avgElbow < 138f) {
            errors.add(LandmarkId.LEFT_ELBOW)
            errors.add(LandmarkId.RIGHT_ELBOW)
            cue = "Extend elbows cleanly at the top of each press"
            quality -= 10
        }

        val angles = mapOf("PressAngle" to avgArm, "Elbow" to avgElbow)
        return ExerciseFrameSignal(
            primaryAngle = normalizedAngle,
            secondaryAngle = avgElbow,
            isAtTop = avgArm <= 96f && avgElbow <= 125f,
            isAtBottom = avgArm >= 144f && avgElbow >= 142f,
            isDescending = avgArm > 106f,
            formQualityScore = quality.coerceIn(40, 100),
            errorJoints = errors,
            postureCue = cue,
            jointAnglesMap = angles,
            leftMeasureAngle = leftArm,
            rightMeasureAngle = rightArm
        )
    }

    /**
     * Bicep Arm Curl Detector:
     * Tracks Shoulder -> Elbow -> Wrist flexion across both arms.
     */
    private fun detectBicepCurls(landmarks: Map<Int, LandmarkPoint>): ExerciseFrameSignal {
        val useLeft = PoseGeometry.preferLeftSide(landmarks)
        val ls = landmarks[LandmarkId.LEFT_SHOULDER]
        val le = landmarks[LandmarkId.LEFT_ELBOW]
        val lw = landmarks[LandmarkId.LEFT_WRIST]
        val rs = landmarks[LandmarkId.RIGHT_SHOULDER]
        val re = landmarks[LandmarkId.RIGHT_ELBOW]
        val rw = landmarks[LandmarkId.RIGHT_WRIST]

        val leftElbow = if (ls != null && le != null && lw != null) PoseGeometry.calculateAngle(ls, le, lw) else 166f
        val rightElbow = if (rs != null && re != null && rw != null) PoseGeometry.calculateAngle(rs, re, rw) else 166f
        val primaryElbow = if (ls != null && le != null && lw != null && rs != null && re != null && rw != null) {
            minOf(leftElbow, rightElbow)
        } else if (useLeft) {
            leftElbow
        } else {
            rightElbow
        }

        val errors = mutableSetOf<Int>()
        var cue: String? = null
        var quality = 100

        if (primaryElbow in 74f..118f) {
            errors.add(if (leftElbow <= rightElbow) LandmarkId.LEFT_WRIST else LandmarkId.RIGHT_WRIST)
            cue = "Curl all the way up toward your shoulders"
            quality -= 10
        }

        val angles = mapOf("LeftElbow" to leftElbow, "RightElbow" to rightElbow)
        return ExerciseFrameSignal(
            primaryAngle = primaryElbow,
            secondaryAngle = (leftElbow + rightElbow) * 0.5f,
            isAtTop = primaryElbow >= 146f,
            isAtBottom = primaryElbow <= 68f,
            isDescending = primaryElbow < 132f,
            formQualityScore = quality.coerceIn(40, 100),
            errorJoints = errors,
            postureCue = cue,
            jointAnglesMap = angles,
            leftMeasureAngle = leftElbow,
            rightMeasureAngle = rightElbow
        )
    }

    /**
     * Core Sit-up Detector:
     * Tracks Shoulder -> Hip -> Knee angle from supine/reclined (>=126°) up to seated crunch (<=78°).
     */
    private fun detectSitUps(landmarks: Map<Int, LandmarkPoint>): ExerciseFrameSignal {
        val useLeft = PoseGeometry.preferLeftSide(landmarks)
        val s = landmarks[if (useLeft) LandmarkId.LEFT_SHOULDER else LandmarkId.RIGHT_SHOULDER]
        val h = landmarks[if (useLeft) LandmarkId.LEFT_HIP else LandmarkId.RIGHT_HIP]
        val k = landmarks[if (useLeft) LandmarkId.LEFT_KNEE else LandmarkId.RIGHT_KNEE]

        val hipFlex = if (s != null && h != null && k != null) PoseGeometry.calculateAngle(s, h, k) else 140f
        val torsoTilt = if (s != null && h != null) PoseGeometry.calculateTorsoTiltFromVertical(s, h) else 65f

        val errors = mutableSetOf<Int>()
        var cue: String? = null
        var quality = 100

        if (hipFlex in 84f..112f) {
            errors.add(if (useLeft) LandmarkId.LEFT_SHOULDER else LandmarkId.RIGHT_SHOULDER)
            cue = "Curl your chest higher toward your knees"
            quality -= 12
        }

        val angles = mapOf("HipFlex" to hipFlex, "TorsoTilt" to torsoTilt)
        return ExerciseFrameSignal(
            primaryAngle = hipFlex,
            secondaryAngle = torsoTilt,
            isAtTop = hipFlex >= 126f,
            isAtBottom = hipFlex <= 78f,
            isDescending = hipFlex < 114f,
            formQualityScore = quality.coerceIn(40, 100),
            errorJoints = errors,
            postureCue = cue,
            jointAnglesMap = angles,
            leftMeasureAngle = hipFlex,
            rightMeasureAngle = hipFlex
        )
    }
}
