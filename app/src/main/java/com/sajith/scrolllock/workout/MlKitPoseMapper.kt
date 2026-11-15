package com.sajith.scrolllock.workout

import com.google.mlkit.vision.pose.Pose

/**
 * Maps Google ML Kit Pose output into normalized (0..1) LandmarkPoint coordinates
 * accounting for image rotation and front-camera mirroring, and evaluates camera framing & lighting confidence.
 */
object MlKitPoseMapper {

    fun toNormalizedMap(
        pose: Pose,
        rotatedImageWidth: Int,
        rotatedImageHeight: Int,
        isFrontCamera: Boolean
    ): Map<Int, LandmarkPoint> {
        val allLandmarks = pose.allPoseLandmarks
        if (allLandmarks.isEmpty() || rotatedImageWidth <= 0 || rotatedImageHeight <= 0) {
            return emptyMap()
        }

        val w = rotatedImageWidth.toFloat()
        val h = rotatedImageHeight.toFloat()
        val mapped = HashMap<Int, LandmarkPoint>(allLandmarks.size)

        for (lm in allLandmarks) {
            var normX = (lm.position.x / w).coerceIn(0f, 1f)
            val normY = (lm.position.y / h).coerceIn(0f, 1f)
            if (isFrontCamera) {
                normX = 1f - normX
            }
            mapped[lm.landmarkType] = LandmarkPoint(
                type = lm.landmarkType,
                x = normX,
                y = normY,
                z = lm.position3D.z / w,
                inFrameLikelihood = lm.inFrameLikelihood
            )
        }
        return mapped
    }

    /**
     * Checks full-body framing, landmark confidence (lighting proxy), and gives camera placement advice.
     */
    fun checkCameraPosition(
        exercise: ExerciseType,
        landmarks: Map<Int, LandmarkPoint>,
        averageLuma: Float
    ): CameraPositionStatus {
        val lowLight = averageLuma in 1f..48f
        val lightingWarning = if (lowLight) "Low lighting detected — move to a brighter area." else null

        if (landmarks.isEmpty()) {
            return CameraPositionStatus(
                isReady = false,
                visibilityScore = 0f,
                lightingConfidence = if (lowLight) 0.3f else 0.9f,
                lightingWarning = lightingWarning,
                guidanceMessage = "No person detected. Step back so your full body fits inside the frame.",
                recommendedAngle = exercise.cameraGuide
            )
        }

        val keyIds = listOf(
            LandmarkId.NOSE,
            LandmarkId.LEFT_SHOULDER, LandmarkId.RIGHT_SHOULDER,
            LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
            LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
            LandmarkId.LEFT_ANKLE, LandmarkId.RIGHT_ANKLE
        )

        val avgConfidence = keyIds.map { landmarks[it]?.inFrameLikelihood ?: 0f }.average().toFloat()
        val isUpperBody = exercise == ExerciseType.SHOULDER_PRESS || exercise == ExerciseType.BICEP_CURLS
        val hasUpper = PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_SHOULDER], 0.55f) ||
            PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_SHOULDER], 0.55f)
        val hasArms = PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_WRIST], 0.45f) ||
            PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_WRIST], 0.45f)
        val hasHip = PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_HIP], 0.55f) ||
            PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_HIP], 0.55f)
        val hasLower = (PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_ANKLE], 0.45f) ||
            PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_ANKLE], 0.45f)) &&
            (PoseGeometry.isVisible(landmarks[LandmarkId.LEFT_KNEE], 0.50f) ||
                PoseGeometry.isVisible(landmarks[LandmarkId.RIGHT_KNEE], 0.50f))

        val isReady = if (isUpperBody) {
            hasUpper && hasArms && avgConfidence >= 0.35f
        } else {
            hasUpper && hasHip && hasLower && avgConfidence >= 0.48f
        }
        val guidance = when {
            !hasUpper -> "Adjust camera up so your shoulders and head are visible."
            isUpperBody && !hasArms -> "Keep your elbows and wrists inside the camera frame."
            !isUpperBody && !hasHip -> "Step back slightly so your hips stay centered in frame."
            !isUpperBody && !hasLower -> "Tilt camera down or step back so your knees and ankles are visible."
            lowLight -> "Lighting is dim — tracking accuracy may drop."
            else -> "Great framing! Ready to track ${exercise.displayName}."
        }

        return CameraPositionStatus(
            isReady = isReady,
            visibilityScore = avgConfidence.coerceIn(0f, 1f),
            lightingConfidence = if (lowLight) 0.4f else avgConfidence.coerceIn(0.5f, 1f),
            lightingWarning = lightingWarning,
            guidanceMessage = guidance,
            recommendedAngle = exercise.cameraGuide
        )
    }
}
