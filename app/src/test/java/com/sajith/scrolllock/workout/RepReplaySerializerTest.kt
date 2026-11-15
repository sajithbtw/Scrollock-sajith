package com.sajith.scrolllock.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric unit test verifying JSON serialization/deserialization of recorded Rep Replay frames.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepReplaySerializerTest {

    @Test
    fun `replay serializer round-trips recorded reps cleanly`() {
        val sampleFrames = listOf(
            PoseFrame(
                timestampMs = 1000L,
                landmarks = GhostTrainerGenerator.generateIdealPose(ExerciseType.SQUATS, RepPhase.START),
                jointAngles = mapOf("Knee" to 172f, "Hip" to 170f),
                errorJoints = emptySet(),
                feedback = null
            ),
            PoseFrame(
                timestampMs = 1600L,
                landmarks = GhostTrainerGenerator.generateIdealPose(ExerciseType.SQUATS, RepPhase.BOTTOM),
                jointAngles = mapOf("Knee" to 92f, "Hip" to 96f),
                errorJoints = setOf(LandmarkId.LEFT_KNEE),
                feedback = "Keep back straight"
            )
        )
        val record = RepAnalysisRecord(
            repNumber = 1,
            exercise = ExerciseType.SQUATS,
            isValid = true,
            rejectionReason = null,
            durationMs = 1500L,
            minPrimaryAngle = 92f,
            maxPrimaryAngle = 172f,
            leftRightAsymmetryDeg = 3.5f,
            formQualityScore = 94,
            postureCues = listOf("Keep back straight"),
            frames = sampleFrames
        )

        val json = RepReplaySerializer.toJson(listOf(record))
        val parsed = RepReplaySerializer.fromJson(json)
        assertEquals(1, parsed.size)
        assertEquals(ExerciseType.SQUATS, parsed.first().exercise)
        assertEquals(2, parsed.first().frames.size)
        assertTrue(parsed.first().frames[1].errorJoints.contains(LandmarkId.LEFT_KNEE))
    }
}
