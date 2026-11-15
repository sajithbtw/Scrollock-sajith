package com.sajith.scrolllock.workout

/**
 * Dynamically calibrates baseline joint angles and body proportions from the user's initial
 * posture instead of relying exclusively on rigid fixed angle thresholds.
 */
class DynamicCalibrator(private val requiredSamples: Int = 10) {

    private val kneeSamples = mutableListOf<Float>()
    private val hipSamples = mutableListOf<Float>()
    private val elbowSamples = mutableListOf<Float>()
    private val torsoSamples = mutableListOf<Float>()
    private val shoulderWidthSamples = mutableListOf<Float>()

    var profile: CalibrationProfile = CalibrationProfile()
        private set

    fun reset() {
        kneeSamples.clear()
        hipSamples.clear()
        elbowSamples.clear()
        torsoSamples.clear()
        shoulderWidthSamples.clear()
        profile = CalibrationProfile()
    }

    fun update(landmarks: Map<Int, LandmarkPoint>): CalibrationProfile {
        if (profile.isCalibrated) {
            return profile
        }
        val ls = landmarks[LandmarkId.LEFT_SHOULDER]
        val rs = landmarks[LandmarkId.RIGHT_SHOULDER]
        val le = landmarks[LandmarkId.LEFT_ELBOW]
        val lw = landmarks[LandmarkId.LEFT_WRIST]
        val lh = landmarks[LandmarkId.LEFT_HIP]
        val rh = landmarks[LandmarkId.RIGHT_HIP]
        val lk = landmarks[LandmarkId.LEFT_KNEE]
        val la = landmarks[LandmarkId.LEFT_ANKLE]

        val useLeft = PoseGeometry.preferLeftSide(landmarks)
        val s = if (useLeft) ls else rs
        val e = if (useLeft) le else landmarks[LandmarkId.RIGHT_ELBOW]
        val w = if (useLeft) lw else landmarks[LandmarkId.RIGHT_WRIST]
        val h = if (useLeft) lh else rh
        val k = if (useLeft) lk else landmarks[LandmarkId.RIGHT_KNEE]
        val a = if (useLeft) la else landmarks[LandmarkId.RIGHT_ANKLE]

        if (!PoseGeometry.isVisible(s) || !PoseGeometry.isVisible(h)) {
            return profile
        }

        if (PoseGeometry.isVisible(k) && PoseGeometry.isVisible(a)) {
            val knee = PoseGeometry.calculateAngle(h!!, k!!, a!!)
            if (knee > 140f) kneeSamples.add(knee)
            val hip = PoseGeometry.calculateAngle(s!!, h, k)
            if (hip > 135f) hipSamples.add(hip)
        }
        if (PoseGeometry.isVisible(e) && PoseGeometry.isVisible(w)) {
            val elbow = PoseGeometry.calculateAngle(s!!, e!!, w!!)
            if (elbow > 135f) elbowSamples.add(elbow)
        }
        torsoSamples.add(PoseGeometry.distance(s!!, h!!))
        if (PoseGeometry.isVisible(ls) && PoseGeometry.isVisible(rs)) {
            shoulderWidthSamples.add(PoseGeometry.distance(ls!!, rs!!))
        }

        val count = maxOf(kneeSamples.size, elbowSamples.size, torsoSamples.size)
        val done = count >= requiredSamples
        profile = CalibrationProfile(
            isCalibrated = done,
            sampleCount = count,
            standingKneeAngle = averageOr(kneeSamples, 172f),
            torsoLength = averageOr(torsoSamples, 0.25f),
            standingHipAngle = averageOr(hipSamples, 170f),
            standingElbowAngle = averageOr(elbowSamples, 168f),
            shoulderWidth = averageOr(shoulderWidthSamples, 0.18f)
        )
        return profile
    }

    /**
     * Adapts target bottom/top thresholds relative to the calibrated standing/extended baseline.
     */
    fun getSquatDownThreshold(): Float {
        val baseline = profile.standingKneeAngle.coerceIn(155f, 180f)
        return (baseline - 65f).coerceIn(92f, 112f)
    }

    fun getSquatUpThreshold(): Float {
        val baseline = profile.standingKneeAngle.coerceIn(155f, 180f)
        return (baseline - 14f).coerceIn(150f, 166f)
    }

    fun getPushupDownThreshold(): Float {
        val baseline = profile.standingElbowAngle.coerceIn(150f, 180f)
        return (baseline - 72f).coerceIn(85f, 102f)
    }

    fun getPushupUpThreshold(): Float {
        val baseline = profile.standingElbowAngle.coerceIn(150f, 180f)
        return (baseline - 16f).coerceIn(148f, 164f)
    }

    private fun averageOr(values: List<Float>, fallback: Float): Float {
        return if (values.isEmpty()) fallback else values.average().toFloat()
    }
}
