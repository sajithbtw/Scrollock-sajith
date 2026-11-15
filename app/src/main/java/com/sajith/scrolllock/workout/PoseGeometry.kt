package com.sajith.scrolllock.workout

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Pure geometry and smoothing utilities for joint-angle computation and landmark validation.
 */
object PoseGeometry {

    fun calculateAngle(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Float {
        val radians = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble()) -
            atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())
        var angle = abs(radians * 180.0 / PI)
        if (angle > 180.0) {
            angle = 360.0 - angle
        }
        return angle.toFloat()
    }

    /**
     * Returns angle in degrees relative to vertical (0° = upright standing, 90° = horizontal plank/pushup).
     */
    fun calculateTorsoTiltFromVertical(shoulder: LandmarkPoint, hip: LandmarkPoint): Float {
        val dx = abs(shoulder.x - hip.x).toDouble()
        val dy = abs(hip.y - shoulder.y).toDouble()
        return (atan2(dx, dy.coerceAtLeast(1e-4)) * 180.0 / PI).toFloat()
    }

    fun distance(a: LandmarkPoint, b: LandmarkPoint): Float {
        return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
    }

    fun isVisible(point: LandmarkPoint?, minLikelihood: Float = 0.50f): Boolean {
        return point != null && point.inFrameLikelihood >= minLikelihood
    }

    fun midpoint(a: LandmarkPoint, b: LandmarkPoint, id: Int = -1): LandmarkPoint {
        return LandmarkPoint(
            type = id,
            x = (a.x + b.x) * 0.5f,
            y = (a.y + b.y) * 0.5f,
            z = (a.z + b.z) * 0.5f,
            inFrameLikelihood = minOf(a.inFrameLikelihood, b.inFrameLikelihood)
        )
    }

    /**
     * Picks whichever side (left or right) has higher average landmark visibility.
     */
    fun preferLeftSide(landmarks: Map<Int, LandmarkPoint>): Boolean {
        val leftScore = (landmarks[LandmarkId.LEFT_SHOULDER]?.inFrameLikelihood ?: 0f) +
            (landmarks[LandmarkId.LEFT_HIP]?.inFrameLikelihood ?: 0f) +
            (landmarks[LandmarkId.LEFT_KNEE]?.inFrameLikelihood ?: 0f)
        val rightScore = (landmarks[LandmarkId.RIGHT_SHOULDER]?.inFrameLikelihood ?: 0f) +
            (landmarks[LandmarkId.RIGHT_HIP]?.inFrameLikelihood ?: 0f) +
            (landmarks[LandmarkId.RIGHT_KNEE]?.inFrameLikelihood ?: 0f)
        return leftScore >= rightScore
    }
}

/**
 * Exponential moving average filter that smooths normalized pose landmarks across frames.
 */
class LandmarkSmoother(private val alpha: Float = 0.45f) {
    private val smoothed = mutableMapOf<Int, LandmarkPoint>()

    fun smooth(raw: Map<Int, LandmarkPoint>): Map<Int, LandmarkPoint> {
        if (raw.isEmpty()) {
            smoothed.clear()
            return emptyMap()
        }
        val result = HashMap<Int, LandmarkPoint>(raw.size)
        for ((id, pt) in raw) {
            val prev = smoothed[id]
            val next = if (prev == null) {
                pt
            } else {
                LandmarkPoint(
                    type = id,
                    x = prev.x + alpha * (pt.x - prev.x),
                    y = prev.y + alpha * (pt.y - prev.y),
                    z = prev.z + alpha * (pt.z - prev.z),
                    inFrameLikelihood = pt.inFrameLikelihood
                )
            }
            smoothed[id] = next
            result[id] = next
        }
        return result
    }

    fun reset() {
        smoothed.clear()
    }
}
