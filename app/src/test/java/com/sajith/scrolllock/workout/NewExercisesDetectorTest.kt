package com.sajith.scrolllock.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class NewExercisesDetectorTest {

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

    private fun highKneesPose(leftHipDeg: Float): Map<Int, LandmarkPoint> {
        val ls = LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.44f, 0.24f)
        val rs = LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.56f, 0.24f)
        val lh = LandmarkPoint(LandmarkId.LEFT_HIP, 0.45f, 0.50f)
        val rh = LandmarkPoint(LandmarkId.RIGHT_HIP, 0.55f, 0.50f)

        val lk = pointAtAngle(ls, lh, 0.20f, leftHipDeg, LandmarkId.LEFT_KNEE, clockwise = false)
        val la = LandmarkPoint(LandmarkId.LEFT_ANKLE, lk.x, lk.y + 0.18f)

        val rk = LandmarkPoint(LandmarkId.RIGHT_KNEE, 0.55f, 0.70f)
        val ra = LandmarkPoint(LandmarkId.RIGHT_ANKLE, 0.55f, 0.88f)

        val le = LandmarkPoint(LandmarkId.LEFT_ELBOW, 0.38f, 0.36f)
        val lw = LandmarkPoint(LandmarkId.LEFT_WRIST, 0.38f, 0.46f)
        val re = LandmarkPoint(LandmarkId.RIGHT_ELBOW, 0.62f, 0.36f)
        val rw = LandmarkPoint(LandmarkId.RIGHT_WRIST, 0.62f, 0.46f)

        return mapOf(
            LandmarkId.LEFT_SHOULDER to ls,
            LandmarkId.RIGHT_SHOULDER to rs,
            LandmarkId.LEFT_ELBOW to le,
            LandmarkId.RIGHT_ELBOW to re,
            LandmarkId.LEFT_WRIST to lw,
            LandmarkId.RIGHT_WRIST to rw,
            LandmarkId.LEFT_HIP to lh,
            LandmarkId.RIGHT_HIP to rh,
            LandmarkId.LEFT_KNEE to lk,
            LandmarkId.RIGHT_KNEE to rk,
            LandmarkId.LEFT_ANKLE to la,
            LandmarkId.RIGHT_ANKLE to ra
        )
    }

    private fun shoulderPressPose(armRaiseDeg: Float, elbowDeg: Float): Map<Int, LandmarkPoint> {
        val lh = LandmarkPoint(LandmarkId.LEFT_HIP, 0.44f, 0.58f)
        val rh = LandmarkPoint(LandmarkId.RIGHT_HIP, 0.56f, 0.58f)
        val ls = LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.43f, 0.34f)
        val rs = LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.57f, 0.34f)

        val segLen = 0.14f
        val halfAngleRad = Math.toRadians((elbowDeg.coerceIn(20f, 178f) * 0.5).toDouble())
        val distSW = (2.0 * segLen * sin(halfAngleRad)).toFloat().coerceAtLeast(0.05f)
        val altitude = (segLen * cos(halfAngleRad)).toFloat()

        val lw = pointAtAngle(lh, ls, distSW, armRaiseDeg, LandmarkId.LEFT_WRIST, clockwise = true)
        val rw = pointAtAngle(rh, rs, distSW, armRaiseDeg, LandmarkId.RIGHT_WRIST, clockwise = false)

        val lux = (lw.x - ls.x) / distSW
        val luy = (lw.y - ls.y) / distSW
        // Outward-downward normal for Left arm (ls -> lw normal rotated toward +y)
        val lnx = if (luy >= 0f) -luy else luy
        val lny = if (luy >= 0f) lux else -lux
        val le = LandmarkPoint(
            LandmarkId.LEFT_ELBOW,
            (ls.x + lw.x) * 0.5f + altitude * lnx,
            (ls.y + lw.y) * 0.5f + altitude * lny
        )

        val rux = (rw.x - rs.x) / distSW
        val ruy = (rw.y - rs.y) / distSW
        val rnx = if (ruy >= 0f) ruy else -ruy
        val rny = if (ruy >= 0f) -rux else rux
        val re = LandmarkPoint(
            LandmarkId.RIGHT_ELBOW,
            (rs.x + rw.x) * 0.5f + altitude * rnx,
            (rs.y + rw.y) * 0.5f + altitude * rny
        )

        return mapOf(
            LandmarkId.LEFT_SHOULDER to ls,
            LandmarkId.RIGHT_SHOULDER to rs,
            LandmarkId.LEFT_ELBOW to le,
            LandmarkId.RIGHT_ELBOW to re,
            LandmarkId.LEFT_WRIST to lw,
            LandmarkId.RIGHT_WRIST to rw,
            LandmarkId.LEFT_HIP to lh,
            LandmarkId.RIGHT_HIP to rh
        )
    }

    private fun bicepCurlPose(elbowDeg: Float): Map<Int, LandmarkPoint> {
        val lh = LandmarkPoint(LandmarkId.LEFT_HIP, 0.44f, 0.58f)
        val rh = LandmarkPoint(LandmarkId.RIGHT_HIP, 0.56f, 0.58f)
        val ls = LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.43f, 0.32f)
        val rs = LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.57f, 0.32f)
        val le = LandmarkPoint(LandmarkId.LEFT_ELBOW, 0.42f, 0.48f)
        val re = LandmarkPoint(LandmarkId.RIGHT_ELBOW, 0.58f, 0.48f)

        val lw = pointAtAngle(ls, le, 0.16f, elbowDeg, LandmarkId.LEFT_WRIST, clockwise = true)
        val rw = pointAtAngle(rs, re, 0.16f, elbowDeg, LandmarkId.RIGHT_WRIST, clockwise = false)

        return mapOf(
            LandmarkId.LEFT_SHOULDER to ls,
            LandmarkId.RIGHT_SHOULDER to rs,
            LandmarkId.LEFT_ELBOW to le,
            LandmarkId.RIGHT_ELBOW to re,
            LandmarkId.LEFT_WRIST to lw,
            LandmarkId.RIGHT_WRIST to rw,
            LandmarkId.LEFT_HIP to lh,
            LandmarkId.RIGHT_HIP to rh
        )
    }

    private fun sitUpPose(hipFlexDeg: Float): Map<Int, LandmarkPoint> {
        val ankle = LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.78f, 0.79f)
        val knee = LandmarkPoint(LandmarkId.LEFT_KNEE, 0.64f, 0.65f)
        val hip = LandmarkPoint(LandmarkId.LEFT_HIP, 0.46f, 0.79f)
        val shoulder = pointAtAngle(knee, hip, 0.26f, hipFlexDeg, LandmarkId.LEFT_SHOULDER, clockwise = false)
        val elbow = LandmarkPoint(LandmarkId.LEFT_ELBOW, shoulder.x + 0.04f, shoulder.y - 0.04f)
        val wrist = LandmarkPoint(LandmarkId.LEFT_WRIST, shoulder.x + 0.06f, shoulder.y - 0.08f)

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
    fun highKnees_validCycleCountsOneRep() {
        val engine = RepCounterEngine(initialExercise = ExerciseType.HIGH_KNEES, minRepDurationMs = 1000L)
        var t = 1_000L

        repeat(8) {
            engine.processFrame(highKneesPose(172f), t)
            t += 120L
        }

        val angles = listOf(132f, 102f, 84f, 92f, 134f, 172f, 172f)
        var eval = engine.processFrame(highKneesPose(172f), t)
        for (deg in angles) {
            t += 200L
            eval = engine.processFrame(highKneesPose(deg), t)
        }

        assertEquals(1, eval.repCount)
        assertEquals(0, eval.invalidRepCount)
        assertFalse(eval.isCheatingDetected)
    }

    @Test
    fun shoulderPress_countsRepWithUpperBodyLandmarksOnly() {
        val engine = RepCounterEngine(initialExercise = ExerciseType.SHOULDER_PRESS, minRepDurationMs = 1000L)
        var t = 5_000L

        // Start racked at shoulders (armRaiseDeg = 72°, elbow = 82°)
        repeat(8) {
            val e = engine.processFrame(shoulderPressPose(72f, 82f), t)
            assertFalse(e.isCheatingDetected)
            t += 120L
        }

        val steps = listOf(
            115f to 132f,
            152f to 164f,
            168f to 172f,
            148f to 160f,
            105f to 112f,
            72f to 82f,
            72f to 82f,
            72f to 82f
        )
        var eval = engine.processFrame(shoulderPressPose(72f, 82f), t)
        for ((arm, elbow) in steps) {
            t += 200L
            eval = engine.processFrame(shoulderPressPose(arm, elbow), t)
        }

        assertEquals(1, eval.repCount)
        assertEquals(0, eval.invalidRepCount)
        assertFalse(eval.isCheatingDetected)
    }

    @Test
    fun bicepCurls_countsRepWithUpperBodyLandmarksOnly() {
        val engine = RepCounterEngine(initialExercise = ExerciseType.BICEP_CURLS, minRepDurationMs = 1000L)
        var t = 12_000L

        repeat(8) {
            val e = engine.processFrame(bicepCurlPose(168f), t)
            assertFalse(e.isCheatingDetected)
            t += 120L
        }

        val angles = listOf(124f, 82f, 48f, 45f, 88f, 138f, 168f, 168f)
        var eval = engine.processFrame(bicepCurlPose(168f), t)
        for (deg in angles) {
            t += 200L
            eval = engine.processFrame(bicepCurlPose(deg), t)
        }

        assertEquals(1, eval.repCount)
        assertEquals(0, eval.invalidRepCount)
        assertFalse(eval.isCheatingDetected)
    }

    @Test
    fun sitUps_countsFullCoreRepCleanly() {
        val engine = RepCounterEngine(initialExercise = ExerciseType.SIT_UPS, minRepDurationMs = 1000L)
        var t = 22_000L

        repeat(8) {
            engine.processFrame(sitUpPose(144f), t)
            t += 120L
        }

        val angles = listOf(110f, 88f, 64f, 60f, 86f, 118f, 144f, 144f)
        var eval = engine.processFrame(sitUpPose(144f), t)
        for (deg in angles) {
            t += 200L
            eval = engine.processFrame(sitUpPose(deg), t)
        }

        assertEquals(1, eval.repCount)
        assertEquals(0, eval.invalidRepCount)
        assertFalse(eval.isCheatingDetected)
    }

    @Test
    fun autoClassify_distinguishesNewExercises() {
        val calibrator = DynamicCalibrator()

        assertEquals(
            ExerciseType.SIT_UPS,
            ExerciseDetectors.classifyExercise(sitUpPose(135f), calibrator)
        )
        assertEquals(
            ExerciseType.HIGH_KNEES,
            ExerciseDetectors.classifyExercise(highKneesPose(90f), calibrator)
        )
        assertEquals(
            ExerciseType.SHOULDER_PRESS,
            ExerciseDetectors.classifyExercise(shoulderPressPose(165f, 170f), calibrator)
        )
        assertEquals(
            ExerciseType.BICEP_CURLS,
            ExerciseDetectors.classifyExercise(bicepCurlPose(55f), calibrator)
        )
    }
}
