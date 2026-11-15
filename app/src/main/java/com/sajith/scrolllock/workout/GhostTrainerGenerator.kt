package com.sajith.scrolllock.workout

/**
 * Generates normalized (0..1) ideal Ghost Trainer reference skeletons aligned to the user's
 * exercise type and current repetition phase, helping users visualize ideal shoulder, elbow,
 * hip, and knee alignment.
 */
object GhostTrainerGenerator {

    fun generateIdealPose(exercise: ExerciseType, phase: RepPhase): Map<Int, LandmarkPoint> {
        val downFactor = when (phase) {
            RepPhase.START -> 0f
            RepPhase.DESCENDING -> 0.55f
            RepPhase.BOTTOM -> 1.0f
            RepPhase.RETURNING -> 0.45f
        }

        return when (exercise) {
            ExerciseType.PUSHUPS, ExerciseType.PLANKS -> generateHorizontalPose(
                if (exercise == ExerciseType.PLANKS) 0.15f else downFactor
            )
            ExerciseType.JUMPING_JACKS -> generateJumpingJackPose(downFactor)
            ExerciseType.HIGH_KNEES -> generateHighKneesPose(downFactor)
            ExerciseType.SHOULDER_PRESS -> generateShoulderPressPose(downFactor)
            ExerciseType.BICEP_CURLS -> generateBicepCurlPose(downFactor)
            ExerciseType.SIT_UPS -> generateSitUpPose(downFactor)
            ExerciseType.LUNGES -> generateLungePose(downFactor)
            ExerciseType.SQUATS, ExerciseType.AUTO -> generateSquatPose(downFactor)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun generateSquatPose(t: Float): Map<Int, LandmarkPoint> {
        val hipY = lerp(0.50f, 0.68f, t)
        val shoulderY = lerp(0.26f, 0.44f, t)
        val noseY = shoulderY - 0.10f
        val kneeXShift = lerp(0.01f, 0.09f, t)

        return mapOf(
            LandmarkId.NOSE to LandmarkPoint(LandmarkId.NOSE, 0.50f, noseY),
            LandmarkId.LEFT_SHOULDER to LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.44f, shoulderY),
            LandmarkId.RIGHT_SHOULDER to LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.56f, shoulderY),
            LandmarkId.LEFT_ELBOW to LandmarkPoint(LandmarkId.LEFT_ELBOW, 0.39f, shoulderY + 0.09f),
            LandmarkId.RIGHT_ELBOW to LandmarkPoint(LandmarkId.RIGHT_ELBOW, 0.61f, shoulderY + 0.09f),
            LandmarkId.LEFT_WRIST to LandmarkPoint(LandmarkId.LEFT_WRIST, 0.42f, shoulderY + 0.16f),
            LandmarkId.RIGHT_WRIST to LandmarkPoint(LandmarkId.RIGHT_WRIST, 0.58f, shoulderY + 0.16f),
            LandmarkId.LEFT_HIP to LandmarkPoint(LandmarkId.LEFT_HIP, 0.45f, hipY),
            LandmarkId.RIGHT_HIP to LandmarkPoint(LandmarkId.RIGHT_HIP, 0.55f, hipY),
            LandmarkId.LEFT_KNEE to LandmarkPoint(LandmarkId.LEFT_KNEE, 0.43f - kneeXShift, 0.70f),
            LandmarkId.RIGHT_KNEE to LandmarkPoint(LandmarkId.RIGHT_KNEE, 0.57f + kneeXShift, 0.70f),
            LandmarkId.LEFT_ANKLE to LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.43f, 0.89f),
            LandmarkId.RIGHT_ANKLE to LandmarkPoint(LandmarkId.RIGHT_ANKLE, 0.57f, 0.89f)
        )
    }

    private fun generatePushupPose(t: Float): Map<Int, LandmarkPoint> = generateHorizontalPose(t)

    private fun generateHorizontalPose(t: Float): Map<Int, LandmarkPoint> {
        val chestDrop = lerp(0.0f, 0.12f, t)
        val shoulderY = 0.56f + chestDrop
        val hipY = 0.62f + chestDrop * 0.65f
        val elbowX = 0.25f - t * 0.04f
        val elbowY = lerp(0.67f, 0.66f, t)

        return mapOf(
            LandmarkId.NOSE to LandmarkPoint(LandmarkId.NOSE, 0.20f, shoulderY - 0.05f),
            LandmarkId.LEFT_SHOULDER to LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.28f, shoulderY),
            LandmarkId.RIGHT_SHOULDER to LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.30f, shoulderY + 0.02f),
            LandmarkId.LEFT_ELBOW to LandmarkPoint(LandmarkId.LEFT_ELBOW, elbowX, elbowY),
            LandmarkId.RIGHT_ELBOW to LandmarkPoint(LandmarkId.RIGHT_ELBOW, elbowX + 0.02f, elbowY + 0.02f),
            LandmarkId.LEFT_WRIST to LandmarkPoint(LandmarkId.LEFT_WRIST, 0.28f, 0.78f),
            LandmarkId.RIGHT_WRIST to LandmarkPoint(LandmarkId.RIGHT_WRIST, 0.30f, 0.79f),
            LandmarkId.LEFT_HIP to LandmarkPoint(LandmarkId.LEFT_HIP, 0.54f, hipY),
            LandmarkId.RIGHT_HIP to LandmarkPoint(LandmarkId.RIGHT_HIP, 0.56f, hipY + 0.01f),
            LandmarkId.LEFT_KNEE to LandmarkPoint(LandmarkId.LEFT_KNEE, 0.71f, 0.69f),
            LandmarkId.RIGHT_KNEE to LandmarkPoint(LandmarkId.RIGHT_KNEE, 0.72f, 0.70f),
            LandmarkId.LEFT_ANKLE to LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.86f, 0.76f),
            LandmarkId.RIGHT_ANKLE to LandmarkPoint(LandmarkId.RIGHT_ANKLE, 0.87f, 0.77f)
        )
    }

    private fun generateLungePose(t: Float): Map<Int, LandmarkPoint> {
        val drop = lerp(0f, 0.14f, t)
        return mapOf(
            LandmarkId.NOSE to LandmarkPoint(LandmarkId.NOSE, 0.48f, 0.20f + drop),
            LandmarkId.LEFT_SHOULDER to LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.46f, 0.30f + drop),
            LandmarkId.RIGHT_SHOULDER to LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.52f, 0.30f + drop),
            LandmarkId.LEFT_ELBOW to LandmarkPoint(LandmarkId.LEFT_ELBOW, 0.44f, 0.42f + drop),
            LandmarkId.RIGHT_ELBOW to LandmarkPoint(LandmarkId.RIGHT_ELBOW, 0.54f, 0.42f + drop),
            LandmarkId.LEFT_WRIST to LandmarkPoint(LandmarkId.LEFT_WRIST, 0.45f, 0.52f + drop),
            LandmarkId.RIGHT_WRIST to LandmarkPoint(LandmarkId.RIGHT_WRIST, 0.53f, 0.52f + drop),
            LandmarkId.LEFT_HIP to LandmarkPoint(LandmarkId.LEFT_HIP, 0.47f, 0.52f + drop),
            LandmarkId.RIGHT_HIP to LandmarkPoint(LandmarkId.RIGHT_HIP, 0.53f, 0.52f + drop),
            LandmarkId.LEFT_KNEE to LandmarkPoint(LandmarkId.LEFT_KNEE, 0.36f, 0.68f + drop * 0.4f),
            LandmarkId.RIGHT_KNEE to LandmarkPoint(LandmarkId.RIGHT_KNEE, 0.62f, 0.72f + drop * 0.6f),
            LandmarkId.LEFT_ANKLE to LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.34f, 0.88f),
            LandmarkId.RIGHT_ANKLE to LandmarkPoint(LandmarkId.RIGHT_ANKLE, 0.68f, 0.88f)
        )
    }

    private fun generateJumpingJackPose(t: Float): Map<Int, LandmarkPoint> {
        val wristY = lerp(0.54f, 0.12f, t)
        val wristSpread = lerp(0.14f, 0.11f, t)
        val ankleSpread = lerp(0.05f, 0.14f, t)

        return mapOf(
            LandmarkId.NOSE to LandmarkPoint(LandmarkId.NOSE, 0.50f, 0.18f),
            LandmarkId.LEFT_SHOULDER to LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.43f, 0.28f),
            LandmarkId.RIGHT_SHOULDER to LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.57f, 0.28f),
            LandmarkId.LEFT_ELBOW to LandmarkPoint(LandmarkId.LEFT_ELBOW, 0.43f - wristSpread * 0.7f, lerp(0.41f, 0.20f, t)),
            LandmarkId.RIGHT_ELBOW to LandmarkPoint(LandmarkId.RIGHT_ELBOW, 0.57f + wristSpread * 0.7f, lerp(0.41f, 0.20f, t)),
            LandmarkId.LEFT_WRIST to LandmarkPoint(LandmarkId.LEFT_WRIST, 0.43f - wristSpread, wristY),
            LandmarkId.RIGHT_WRIST to LandmarkPoint(LandmarkId.RIGHT_WRIST, 0.57f + wristSpread, wristY),
            LandmarkId.LEFT_HIP to LandmarkPoint(LandmarkId.LEFT_HIP, 0.45f, 0.52f),
            LandmarkId.RIGHT_HIP to LandmarkPoint(LandmarkId.RIGHT_HIP, 0.55f, 0.52f),
            LandmarkId.LEFT_KNEE to LandmarkPoint(LandmarkId.LEFT_KNEE, 0.45f - ankleSpread * 0.5f, 0.71f),
            LandmarkId.RIGHT_KNEE to LandmarkPoint(LandmarkId.RIGHT_KNEE, 0.55f + ankleSpread * 0.5f, 0.71f),
            LandmarkId.LEFT_ANKLE to LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.45f - ankleSpread, 0.89f),
            LandmarkId.RIGHT_ANKLE to LandmarkPoint(LandmarkId.RIGHT_ANKLE, 0.55f + ankleSpread, 0.89f)
        )
    }

    private fun generateHighKneesPose(t: Float): Map<Int, LandmarkPoint> {
        val leftKneeY = lerp(0.71f, 0.49f, t)
        val leftAnkleY = lerp(0.89f, 0.67f, t)
        return mapOf(
            LandmarkId.NOSE to LandmarkPoint(LandmarkId.NOSE, 0.50f, 0.18f),
            LandmarkId.LEFT_SHOULDER to LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.44f, 0.28f),
            LandmarkId.RIGHT_SHOULDER to LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.56f, 0.28f),
            LandmarkId.LEFT_ELBOW to LandmarkPoint(LandmarkId.LEFT_ELBOW, 0.40f, 0.40f),
            LandmarkId.RIGHT_ELBOW to LandmarkPoint(LandmarkId.RIGHT_ELBOW, 0.60f, 0.40f),
            LandmarkId.LEFT_WRIST to LandmarkPoint(LandmarkId.LEFT_WRIST, 0.42f, lerp(0.50f, 0.34f, t)),
            LandmarkId.RIGHT_WRIST to LandmarkPoint(LandmarkId.RIGHT_WRIST, 0.58f, lerp(0.50f, 0.44f, t)),
            LandmarkId.LEFT_HIP to LandmarkPoint(LandmarkId.LEFT_HIP, 0.45f, 0.52f),
            LandmarkId.RIGHT_HIP to LandmarkPoint(LandmarkId.RIGHT_HIP, 0.55f, 0.52f),
            LandmarkId.LEFT_KNEE to LandmarkPoint(LandmarkId.LEFT_KNEE, 0.45f, leftKneeY),
            LandmarkId.RIGHT_KNEE to LandmarkPoint(LandmarkId.RIGHT_KNEE, 0.55f, 0.71f),
            LandmarkId.LEFT_ANKLE to LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.45f, leftAnkleY),
            LandmarkId.RIGHT_ANKLE to LandmarkPoint(LandmarkId.RIGHT_ANKLE, 0.55f, 0.89f)
        )
    }

    private fun generateShoulderPressPose(t: Float): Map<Int, LandmarkPoint> {
        val wristY = lerp(0.28f, 0.11f, t)
        val elbowY = lerp(0.35f, 0.20f, t)
        return mapOf(
            LandmarkId.NOSE to LandmarkPoint(LandmarkId.NOSE, 0.50f, 0.20f),
            LandmarkId.LEFT_SHOULDER to LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.43f, 0.31f),
            LandmarkId.RIGHT_SHOULDER to LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.57f, 0.31f),
            LandmarkId.LEFT_ELBOW to LandmarkPoint(LandmarkId.LEFT_ELBOW, lerp(0.32f, 0.40f, t), elbowY),
            LandmarkId.RIGHT_ELBOW to LandmarkPoint(LandmarkId.RIGHT_ELBOW, lerp(0.68f, 0.60f, t), elbowY),
            LandmarkId.LEFT_WRIST to LandmarkPoint(LandmarkId.LEFT_WRIST, lerp(0.33f, 0.42f, t), wristY),
            LandmarkId.RIGHT_WRIST to LandmarkPoint(LandmarkId.RIGHT_WRIST, lerp(0.67f, 0.58f, t), wristY),
            LandmarkId.LEFT_HIP to LandmarkPoint(LandmarkId.LEFT_HIP, 0.45f, 0.56f),
            LandmarkId.RIGHT_HIP to LandmarkPoint(LandmarkId.RIGHT_HIP, 0.55f, 0.56f),
            LandmarkId.LEFT_KNEE to LandmarkPoint(LandmarkId.LEFT_KNEE, 0.45f, 0.74f),
            LandmarkId.RIGHT_KNEE to LandmarkPoint(LandmarkId.RIGHT_KNEE, 0.55f, 0.74f),
            LandmarkId.LEFT_ANKLE to LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.45f, 0.90f),
            LandmarkId.RIGHT_ANKLE to LandmarkPoint(LandmarkId.RIGHT_ANKLE, 0.55f, 0.90f)
        )
    }

    private fun generateBicepCurlPose(t: Float): Map<Int, LandmarkPoint> {
        val wristY = lerp(0.56f, 0.33f, t)
        return mapOf(
            LandmarkId.NOSE to LandmarkPoint(LandmarkId.NOSE, 0.50f, 0.19f),
            LandmarkId.LEFT_SHOULDER to LandmarkPoint(LandmarkId.LEFT_SHOULDER, 0.43f, 0.30f),
            LandmarkId.RIGHT_SHOULDER to LandmarkPoint(LandmarkId.RIGHT_SHOULDER, 0.57f, 0.30f),
            LandmarkId.LEFT_ELBOW to LandmarkPoint(LandmarkId.LEFT_ELBOW, 0.40f, 0.44f),
            LandmarkId.RIGHT_ELBOW to LandmarkPoint(LandmarkId.RIGHT_ELBOW, 0.60f, 0.44f),
            LandmarkId.LEFT_WRIST to LandmarkPoint(LandmarkId.LEFT_WRIST, 0.41f, wristY),
            LandmarkId.RIGHT_WRIST to LandmarkPoint(LandmarkId.RIGHT_WRIST, 0.59f, wristY),
            LandmarkId.LEFT_HIP to LandmarkPoint(LandmarkId.LEFT_HIP, 0.45f, 0.56f),
            LandmarkId.RIGHT_HIP to LandmarkPoint(LandmarkId.RIGHT_HIP, 0.55f, 0.56f),
            LandmarkId.LEFT_KNEE to LandmarkPoint(LandmarkId.LEFT_KNEE, 0.45f, 0.74f),
            LandmarkId.RIGHT_KNEE to LandmarkPoint(LandmarkId.RIGHT_KNEE, 0.55f, 0.74f),
            LandmarkId.LEFT_ANKLE to LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.45f, 0.90f),
            LandmarkId.RIGHT_ANKLE to LandmarkPoint(LandmarkId.RIGHT_ANKLE, 0.55f, 0.90f)
        )
    }

    private fun generateSitUpPose(t: Float): Map<Int, LandmarkPoint> {
        val shoulderX = lerp(0.24f, 0.46f, t)
        val shoulderY = lerp(0.76f, 0.56f, t)
        return mapOf(
            LandmarkId.NOSE to LandmarkPoint(LandmarkId.NOSE, shoulderX - 0.05f, shoulderY - 0.06f),
            LandmarkId.LEFT_SHOULDER to LandmarkPoint(LandmarkId.LEFT_SHOULDER, shoulderX, shoulderY),
            LandmarkId.RIGHT_SHOULDER to LandmarkPoint(LandmarkId.RIGHT_SHOULDER, shoulderX + 0.02f, shoulderY + 0.01f),
            LandmarkId.LEFT_ELBOW to LandmarkPoint(LandmarkId.LEFT_ELBOW, shoulderX + 0.05f, shoulderY + 0.05f),
            LandmarkId.RIGHT_ELBOW to LandmarkPoint(LandmarkId.RIGHT_ELBOW, shoulderX + 0.07f, shoulderY + 0.06f),
            LandmarkId.LEFT_WRIST to LandmarkPoint(LandmarkId.LEFT_WRIST, shoulderX + 0.08f, shoulderY - 0.02f),
            LandmarkId.RIGHT_WRIST to LandmarkPoint(LandmarkId.RIGHT_WRIST, shoulderX + 0.10f, shoulderY - 0.01f),
            LandmarkId.LEFT_HIP to LandmarkPoint(LandmarkId.LEFT_HIP, 0.52f, 0.78f),
            LandmarkId.RIGHT_HIP to LandmarkPoint(LandmarkId.RIGHT_HIP, 0.53f, 0.79f),
            LandmarkId.LEFT_KNEE to LandmarkPoint(LandmarkId.LEFT_KNEE, 0.68f, 0.62f),
            LandmarkId.RIGHT_KNEE to LandmarkPoint(LandmarkId.RIGHT_KNEE, 0.69f, 0.63f),
            LandmarkId.LEFT_ANKLE to LandmarkPoint(LandmarkId.LEFT_ANKLE, 0.80f, 0.80f),
            LandmarkId.RIGHT_ANKLE to LandmarkPoint(LandmarkId.RIGHT_ANKLE, 0.81f, 0.81f)
        )
    }
}
