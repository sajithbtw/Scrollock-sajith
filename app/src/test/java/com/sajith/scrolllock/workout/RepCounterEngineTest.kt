package com.sajith.scrolllock.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure JVM unit tests verifying RepCounterEngine across Squats, Push-ups,
 * Planks, Auto-detection, and Anti-cheat rejections.
 */
class RepCounterEngineTest {

    private fun pointAtAngle(
        a: LandmarkPoint,
        b: LandmarkPoint,
        dist: Float,
        angleDeg: Float,
        cId: Int,
        clockwise: Boolean = true
    ): LandmarkPoint {
        val thetaBA = atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())
        val delta = Math.toRadians(angleDeg.toDouble())
        val thetaBC = if (clockwise) thetaBA + delta else thetaBA - delta
        return LandmarkPoint(
            type = cId,
            x = b.x + (dist * cos(thetaBC)).toFloat(),
            y = b.y + (dist * sin(thetaBC)).toFloat(),
            inFrameLikelihood = 1.0f
        )
    }

    private fun squatPose(kneeDeg: Float): Map<Int, LandmarkPoint> {
        val ankle = LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.50f, 0.85f)
        val knee = LandmarkPoint(LandmarkId.LEFT_KNEE, 0.49f, 0.68f)
        val hip = pointAtAngle(ankle, knee, 0.17f, kneeDeg, LandmarkId.LEFT_HIP, clockwise = true)

        // Realistic biomechanical squat hip angle: ~174° when standing, ~95° at bottom, keeping torso upright
        val hipFlexionAngle = (kneeDeg * 0.92f + 12f).coerceIn(85f, 176f)
        val shoulder = pointAtAngle(knee, hip, 0.22f, hipFlexionAngle, LandmarkId.LEFT_SHOULDER, clockwise = false)

        val elbow = LandmarkPoint(LandmarkId.LEFT_ELBOW, shoulder.x - 0.03f, shoulder.y + 0.09f)
        val wrist = LandmarkPoint(LandmarkId.LEFT_WRIST, shoulder.x - 0.04f, shoulder.y + 0.16f)

        return mapOf(
            LandmarkId.LEFT_SHOULDER to shoulder,
            LandmarkId.RIGHT_SHOULDER to shoulder.copy(type = LandmarkId.RIGHT_SHOULDER, x = shoulder.x + 0.04f),
            LandmarkId.LEFT_ELBOW to elbow,
            LandmarkId.RIGHT_ELBOW to elbow.copy(type = LandmarkId.RIGHT_ELBOW, x = elbow.x + 0.04f),
            LandmarkId.LEFT_WRIST to wrist,
            LandmarkId.RIGHT_WRIST to wrist.copy(type = LandmarkId.RIGHT_WRIST, x = wrist.x + 0.04f),
            LandmarkId.LEFT_HIP to hip,
            LandmarkId.RIGHT_HIP to hip.copy(type = LandmarkId.RIGHT_HIP, x = hip.x + 0.04f),
            LandmarkId.LEFT_KNEE to knee,
            LandmarkId.RIGHT_KNEE to knee.copy(type = LandmarkId.RIGHT_KNEE, x = knee.x + 0.04f),
            LandmarkId.LEFT_ANKLE to ankle,
            LandmarkId.RIGHT_ANKLE to ankle.copy(type = LandmarkId.RIGHT_ANKLE, x = ankle.x + 0.04f)
        )
    }

    private fun pushupPose(elbowDeg: Float): Map<Int, LandmarkPoint> {
        val ankle = LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.76f, 0.62f)
        val knee = LandmarkPoint(LandmarkId.LEFT_KNEE, 0.64f, 0.61f)
        val hip = LandmarkPoint(LandmarkId.LEFT_HIP, 0.53f, 0.60f)
        val shoulder = LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.30f, 0.58f)
        val bendFraction = ((180f - elbowDeg) / 90f).coerceIn(0f, 1f)
        val elbow = LandmarkPoint(
            LandmarkId.LEFT_ELBOW,
            shoulder.x + 0.05f * bendFraction,
            shoulder.y + 0.11f - 0.03f * bendFraction
        )
        val wrist = pointAtAngle(shoulder, elbow, 0.11f, elbowDeg, LandmarkId.LEFT_WRIST, clockwise = false)

        return mapOf(
            LandmarkId.LEFT_SHOULDER to shoulder,
            LandmarkId.RIGHT_SHOULDER to shoulder.copy(type = LandmarkId.RIGHT_SHOULDER, y = shoulder.y + 0.02f),
            LandmarkId.LEFT_ELBOW to elbow,
            LandmarkId.RIGHT_ELBOW to elbow.copy(type = LandmarkId.RIGHT_ELBOW, y = elbow.y + 0.02f),
            LandmarkId.LEFT_WRIST to wrist,
            LandmarkId.RIGHT_WRIST to wrist.copy(type = LandmarkId.RIGHT_WRIST, y = wrist.y + 0.02f),
            LandmarkId.LEFT_HIP to hip,
            LandmarkId.RIGHT_HIP to hip.copy(type = LandmarkId.RIGHT_HIP, y = hip.y + 0.02f),
            LandmarkId.LEFT_KNEE to knee,
            LandmarkId.RIGHT_KNEE to knee.copy(type = LandmarkId.RIGHT_KNEE, y = knee.y + 0.02f),
            LandmarkId.LEFT_ANKLE to ankle,
            LandmarkId.RIGHT_ANKLE to ankle.copy(type = LandmarkId.RIGHT_ANKLE, y = ankle.y + 0.02f)
        )
    }

    @Test
    fun `valid squat rep counts across full cycle and at least 1 second`() {
        val engine = RepCounterEngine(initialExercise = ExerciseType.SQUATS, minRepDurationMs = 1000L)
        var t = 10_000L

        repeat(8) {
            engine.processFrame(squatPose(174f), t)
            t += 120L
        }

        val angles = listOf(160f, 138f, 114f, 92f, 88f, 95f, 124f, 152f, 174f, 174f)
        var lastEval = engine.processFrame(squatPose(174f), t)
        for (deg in angles) {
            t += 180L
            lastEval = engine.processFrame(squatPose(deg), t)
        }

        assertEquals(1, lastEval.repCount)
        assertEquals(0, lastEval.invalidRepCount)
        assertFalse(lastEval.isCheatingDetected)
        assertEquals(1, engine.getRecordedReps().size)
        assertTrue(engine.getRecordedReps().first().isValid)
    }

    @Test
    fun `half squat rep is rejected by anti-cheat`() {
        val engine = RepCounterEngine(initialExercise = ExerciseType.SQUATS, minRepDurationMs = 1000L)
        var t = 20_000L

        repeat(8) {
            engine.processFrame(squatPose(174f), t)
            t += 120L
        }

        val partialAngles = listOf(155f, 140f, 132f, 142f, 162f, 174f, 174f)
        var lastEval = engine.processFrame(squatPose(174f), t)
        for (deg in partialAngles) {
            t += 220L
            lastEval = engine.processFrame(squatPose(deg), t)
        }

        assertEquals(0, lastEval.repCount)
        assertEquals(1, lastEval.invalidRepCount)
    }

    @Test
    fun `too-fast squat rep under 1 second is rejected by anti-cheat`() {
        val engine = RepCounterEngine(initialExercise = ExerciseType.SQUATS, minRepDurationMs = 1000L)
        var t = 30_000L

        repeat(8) {
            engine.processFrame(squatPose(174f), t)
            t += 100L
        }

        val fastAngles = listOf(148f, 118f, 88f, 128f, 165f, 174f, 174f)
        var lastEval = engine.processFrame(squatPose(174f), t)
        for (deg in fastAngles) {
            t += 100L
            lastEval = engine.processFrame(squatPose(deg), t)
        }

        assertEquals(0, lastEval.repCount)
        assertEquals(1, lastEval.invalidRepCount)
    }

    @Test
    fun `missing lower-body landmarks fail anti-cheat full-body requirement`() {
        val engine = RepCounterEngine(initialExercise = ExerciseType.SQUATS)
        val partialPose = mapOf(
            LandmarkId.LEFT_SHOULDER to LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.45f, 0.30f),
            LandmarkId.RIGHT_SHOULDER to LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.55f, 0.30f),
            LandmarkId.LEFT_HIP to LandmarkPoint(LandmarkId.LEFT_HIP, 0.46f, 0.55f)
        )
        val eval = engine.processFrame(partialPose, 50_000L)
        assertTrue(eval.isCheatingDetected)
        assertEquals(0, eval.repCount)
    }

    @Test
    fun `valid pushup rep counts accurately`() {
        val engine = RepCounterEngine(initialExercise = ExerciseType.PUSHUPS, minRepDurationMs = 1000L)
        var t = 40_000L

        repeat(8) {
            engine.processFrame(pushupPose(172f), t)
            t += 120L
        }

        val angles = listOf(148f, 120f, 88f, 82f, 118f, 154f, 172f, 172f)
        var lastEval = engine.processFrame(pushupPose(172f), t)
        for (deg in angles) {
            t += 200L
            lastEval = engine.processFrame(pushupPose(deg), t)
        }

        assertEquals(1, lastEval.repCount)
        assertEquals(0, lastEval.invalidRepCount)
    }

    @Test
    fun `plank hold accumulates seconds when body alignment is straight`() {
        val engine = RepCounterEngine(initialExercise = ExerciseType.PLANKS)
        var t = 60_000L
        val plank = pushupPose(168f)
        var eval = engine.processFrame(plank, t)
        repeat(8) {
            t += 250L
            eval = engine.processFrame(plank, t)
        }
        assertTrue(eval.plankHoldSeconds >= 2)
        assertEquals("HOLD", eval.stateLabel)
    }

    @Test
    fun `auto detect distinguishes horizontal pushup from upright squat`() {
        val calibrator = DynamicCalibrator()
        val squatDetected = ExerciseDetectors.classifyExercise(squatPose(110f), calibrator)
        assertEquals(ExerciseType.SQUATS, squatDetected)

        val pushupDetected = ExerciseDetectors.classifyExercise(pushupPose(95f), calibrator)
        assertEquals(ExerciseType.PUSHUPS, pushupDetected)
    }
}
