package com.sajith.scrolllock.workout

import kotlin.math.abs

/**
 * Analyzes rep speed and range-of-motion consistency across a workout to estimate fatigue
 * and recommend adaptive rest intervals (strictly fitness/wellness guidance, non-medical).
 */
class FatigueAnalyzer {
    private val repDurationsMs = mutableListOf<Long>()
    private val repRoms = mutableListOf<Float>()

    fun reset() {
        repDurationsMs.clear()
        repRoms.clear()
    }

    fun recordRep(durationMs: Long, minAngle: Float, maxAngle: Float): FatigueStatus {
        repDurationsMs.add(durationMs)
        repRoms.add((maxAngle - minAngle).coerceAtLeast(0f))
        return evaluate()
    }

    fun evaluate(): FatigueStatus {
        if (repDurationsMs.size < 4) {
            return FatigueStatus(
                level = FatigueLevel.FRESH,
                score = 15,
                recommendedRestSeconds = 30,
                message = "Pace and range of motion are steady."
            )
        }

        val baselineDuration = repDurationsMs.take(3).average()
        val recentDuration = repDurationsMs.takeLast(2).average()
        val slowdownRatio = if (baselineDuration > 0) (recentDuration - baselineDuration) / baselineDuration else 0.0

        val baselineRom = repRoms.take(3).average()
        val recentRom = repRoms.takeLast(2).average()
        val romDrop = (baselineRom - recentRom).coerceAtLeast(0.0)

        val fatigueScore = ((slowdownRatio * 100.0) + (romDrop * 1.8) + (repDurationsMs.size * 1.5))
            .toInt()
            .coerceIn(0, 100)

        return when {
            fatigueScore >= 65 -> FatigueStatus(
                level = FatigueLevel.FATIGUED,
                score = fatigueScore,
                recommendedRestSeconds = 60,
                message = "Rep pace slowed and depth reduced — take a 60s recovery break."
            )
            fatigueScore >= 38 -> FatigueStatus(
                level = FatigueLevel.MODERATE,
                score = fatigueScore,
                recommendedRestSeconds = 45,
                message = "Moderate fatigue noticed — focus on clean form and breathe."
            )
            else -> FatigueStatus(
                level = FatigueLevel.FRESH,
                score = fatigueScore,
                recommendedRestSeconds = 30,
                message = "Strong, consistent tempo maintained."
            )
        }
    }
}

/**
 * Compares Left vs Right joint angles across frames and repetitions to evaluate body symmetry.
 */
class BodyBalanceAnalyzer {
    private val leftAngles = mutableListOf<Float>()
    private val rightAngles = mutableListOf<Float>()

    fun reset() {
        leftAngles.clear()
        rightAngles.clear()
    }

    fun addSample(leftAngle: Float, rightAngle: Float) {
        if (leftAngle > 0f && rightAngle > 0f) {
            leftAngles.add(leftAngle)
            rightAngles.add(rightAngle)
        }
    }

    fun getReport(): BalanceReport {
        if (leftAngles.isEmpty() || rightAngles.isEmpty()) {
            return BalanceReport()
        }
        val leftAvg = leftAngles.average().toFloat()
        val rightAvg = rightAngles.average().toFloat()
        val diff = abs(leftAvg - rightAvg)
        val symmetry = (100f - (diff * 2.2f)).toInt().coerceIn(50, 100)

        val suggestion = when {
            diff <= 6f -> "Left and right movement symmetry looks well balanced ($symmetry%)."
            leftAvg < rightAvg -> "Left side bends deeper (${diff.toInt()}° delta) — keep weight centered."
            else -> "Right side bends deeper (${diff.toInt()}° delta) — engage both sides evenly."
        }

        return BalanceReport(
            symmetryPercent = symmetry,
            leftAvgAngle = leftAvg,
            rightAvgAngle = rightAvg,
            suggestion = suggestion
        )
    }
}
