package com.sajith.scrolllock.study

import java.nio.ByteBuffer
import kotlin.math.abs

enum class PageTurnState(val label: String) {
    CALIBRATING("CALIBRATING"),
    STEADY("READY"),
    TURNING("TURNING"),
    COOLDOWN("COUNTED")
}

data class PageTurnEvaluation(
    val pagesTurned: Int,
    val justTurnedPage: Boolean,
    val state: PageTurnState,
    val motionLevel: Float,
    val leftRightShift: Float,
    val avgLuma: Float,
    val isLowLight: Boolean,
    val statusHint: String
)

/**
 * Pure, unit-testable camera luminance-grid analyzer for counting physical page turns.
 * Measures frame-to-frame motion pulses, directional left/right sweep balance, and post-turn
 * page stabilization while rejecting exposure flickers, micro-jitter, and rapid double counts.
 */
class PageTurnDetector(
    private val gridCols: Int = 8,
    private val gridRows: Int = 8,
    private val motionStartThreshold: Float = 10.0f,
    private val baselineTriggerThreshold: Float = 13.5f,
    private val settleThreshold: Float = 5.5f,
    private val minPeakDelta: Float = 12.0f,
    private val minTurnDurationMs: Long = 140L,
    private val maxTurnDurationMs: Long = 4500L,
    private val cooldownMs: Long = 1100L
) {
    private val cellCount = gridCols * gridRows
    private var baselineGrid: FloatArray? = null
    private var prevGrid: FloatArray? = null
    private var calibrationFrames = 0
    private var state = PageTurnState.CALIBRATING
    private var motionStartMs = 0L
    private var peakDelta = 0f
    private var settledStreak = 0
    private var lastCountedAtMs = 0L
    private var pagesTurned = 0

    fun reset() {
        baselineGrid = null
        prevGrid = null
        calibrationFrames = 0
        state = PageTurnState.CALIBRATING
        motionStartMs = 0L
        peakDelta = 0f
        settledStreak = 0
        lastCountedAtMs = 0L
        pagesTurned = 0
    }

    fun recalibrateBaseline() {
        baselineGrid = null
        prevGrid = null
        calibrationFrames = 0
        state = PageTurnState.CALIBRATING
        settledStreak = 0
        peakDelta = 0f
    }

    fun adjustCount(delta: Int): Int {
        pagesTurned = (pagesTurned + delta).coerceAtLeast(0)
        return pagesTurned
    }

    fun getPagesTurned(): Int = pagesTurned

    fun processLumaGrid(grid: FloatArray, timestampMs: Long): PageTurnEvaluation {
        if (grid.size != cellCount) {
            return PageTurnEvaluation(
                pagesTurned = pagesTurned,
                justTurnedPage = false,
                state = state,
                motionLevel = 0f,
                leftRightShift = 0f,
                avgLuma = 128f,
                isLowLight = false,
                statusHint = "Point camera at your book or study notes."
            )
        }

        val avgLuma = grid.average().toFloat()
        val isLowLight = avgLuma < 42f

        val previous = prevGrid
        if (previous == null) {
            prevGrid = grid.copyOf()
            baselineGrid = grid.copyOf()
            calibrationFrames = 1
            return PageTurnEvaluation(
                pagesTurned = pagesTurned,
                justTurnedPage = false,
                state = PageTurnState.CALIBRATING,
                motionLevel = 0f,
                leftRightShift = 0f,
                avgLuma = avgLuma,
                isLowLight = isLowLight,
                statusHint = "Calibrating desk view…"
            )
        }

        val base = baselineGrid ?: grid.copyOf().also { baselineGrid = it }
        var frameSum = 0f
        var baseSum = 0f
        var leftDeltaSum = 0f
        var rightDeltaSum = 0f
        val halfCols = gridCols / 2

        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                val idx = r * gridCols + c
                val dFrame = abs(grid[idx] - previous[idx])
                val dBase = abs(grid[idx] - base[idx])
                frameSum += dFrame
                baseSum += dBase
                if (c < halfCols) {
                    leftDeltaSum += dFrame
                } else {
                    rightDeltaSum += dFrame
                }
            }
        }

        val frameDelta = frameSum / cellCount.toFloat()
        val baseDelta = baseSum / cellCount.toFloat()
        val halfCells = (cellCount / 2).coerceAtLeast(1).toFloat()
        val lrShift = (leftDeltaSum - rightDeltaSum) / halfCells
        val motionLevel = (maxOf(frameDelta, baseDelta * 0.65f) / 28f).coerceIn(0f, 1f)

        prevGrid = grid.copyOf()

        if (state == PageTurnState.CALIBRATING) {
            for (i in 0 until cellCount) {
                base[i] = base[i] * 0.7f + grid[i] * 0.3f
            }
            calibrationFrames++
            if (calibrationFrames >= 3) {
                state = PageTurnState.STEADY
            }
            return PageTurnEvaluation(
                pagesTurned = pagesTurned,
                justTurnedPage = false,
                state = state,
                motionLevel = motionLevel,
                leftRightShift = lrShift,
                avgLuma = avgLuma,
                isLowLight = isLowLight,
                statusHint = if (isLowLight) "Low light — turn on torch for better page accuracy."
                else "Ready — flip a page to count automatically."
            )
        }

        if (state == PageTurnState.COOLDOWN) {
            if (timestampMs - lastCountedAtMs >= cooldownMs) {
                state = PageTurnState.STEADY
            } else {
                return PageTurnEvaluation(
                    pagesTurned = pagesTurned,
                    justTurnedPage = false,
                    state = PageTurnState.COOLDOWN,
                    motionLevel = motionLevel,
                    leftRightShift = lrShift,
                    avgLuma = avgLuma,
                    isLowLight = isLowLight,
                    statusHint = "Page #$pagesTurned counted! Hold steady on new page."
                )
            }
        }

        var justTurned = false
        var hint: String

        when (state) {
            PageTurnState.STEADY -> {
                if (frameDelta >= motionStartThreshold || baseDelta >= baselineTriggerThreshold) {
                    state = PageTurnState.TURNING
                    motionStartMs = timestampMs
                    peakDelta = maxOf(frameDelta, baseDelta)
                    settledStreak = 0
                    hint = "Page turn in progress…"
                } else {
                    // Gently adapt baseline to slow ambient light shifts while page sits still
                    for (i in 0 until cellCount) {
                        base[i] = base[i] * 0.94f + grid[i] * 0.06f
                    }
                    hint = if (isLowLight) {
                        "Dim desk lighting — turn on camera torch."
                    } else {
                        "Watching page — turn a page when ready."
                    }
                }
            }
            PageTurnState.TURNING -> {
                peakDelta = maxOf(peakDelta, maxOf(frameDelta, baseDelta))
                val duration = timestampMs - motionStartMs
                if (frameDelta <= settleThreshold) {
                    settledStreak++
                    if (settledStreak >= 3) {
                        val cooldownOk = lastCountedAtMs == 0L || (timestampMs - lastCountedAtMs) >= cooldownMs
                        if (duration in minTurnDurationMs..maxTurnDurationMs &&
                            peakDelta >= minPeakDelta &&
                            cooldownOk
                        ) {
                            pagesTurned++
                            lastCountedAtMs = timestampMs
                            baselineGrid = grid.copyOf()
                            state = PageTurnState.COOLDOWN
                            justTurned = true
                            hint = "Page #$pagesTurned counted!"
                        } else {
                            baselineGrid = grid.copyOf()
                            state = PageTurnState.STEADY
                            hint = "Flicker ignored — turn a full page."
                        }
                    } else {
                        hint = "Settling onto next page…"
                    }
                } else {
                    settledStreak = 0
                    if (duration > maxTurnDurationMs + 800L) {
                        // Book or camera repositioned for too long -> cleanly re-baseline
                        baselineGrid = grid.copyOf()
                        state = PageTurnState.STEADY
                        hint = "Desk repositioned — baseline updated."
                    } else {
                        hint = "Page turn in progress…"
                    }
                }
            }
            else -> {
                hint = "Watching page…"
            }
        }

        return PageTurnEvaluation(
            pagesTurned = pagesTurned,
            justTurnedPage = justTurned,
            state = state,
            motionLevel = motionLevel,
            leftRightShift = lrShift,
            avgLuma = avgLuma,
            isLowLight = isLowLight,
            statusHint = hint
        )
    }

    companion object {
        /**
         * Downsamples an ImageProxy Y (luminance) plane into a compact [cols] x [rows] float grid (0..255).
         */
        fun downsampleLumaPlane(
            buffer: ByteBuffer,
            width: Int,
            height: Int,
            rowStride: Int,
            pixelStride: Int,
            cols: Int = 8,
            rows: Int = 8
        ): FloatArray {
            val out = FloatArray(cols * rows)
            if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) {
                return out
            }
            val limit = buffer.limit()
            for (r in 0 until rows) {
                val yStart = (r * height) / rows
                val yEnd = (((r + 1) * height) / rows).coerceAtLeast(yStart + 1)
                val yStep = ((yEnd - yStart) / 4).coerceAtLeast(1)
                for (c in 0 until cols) {
                    val xStart = (c * width) / cols
                    val xEnd = (((c + 1) * width) / cols).coerceAtLeast(xStart + 1)
                    val xStep = ((xEnd - xStart) / 4).coerceAtLeast(1)
                    var sum = 0L
                    var samples = 0
                    var y = yStart
                    while (y < yEnd) {
                        var x = xStart
                        val rowOffset = y * rowStride
                        while (x < xEnd) {
                            val idx = rowOffset + x * pixelStride
                            if (idx in 0 until limit) {
                                sum += (buffer.get(idx).toInt() and 0xFF)
                                samples++
                            }
                            x += xStep
                        }
                        y += yStep
                    }
                    out[r * cols + c] = if (samples > 0) sum.toFloat() / samples.toFloat() else 0f
                }
            }
            return out
        }
    }
}
