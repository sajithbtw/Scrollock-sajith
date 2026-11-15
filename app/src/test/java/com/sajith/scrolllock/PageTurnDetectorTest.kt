package com.sajith.scrolllock

import com.sajith.scrolllock.study.PageTurnDetector
import com.sajith.scrolllock.study.PageTurnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class PageTurnDetectorTest {

    private fun solidGrid(luma: Float): FloatArray = FloatArray(64) { luma }

    private fun sweepGrid(leftLuma: Float, rightLuma: Float): FloatArray {
        val grid = FloatArray(64)
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                grid[r * 8 + c] = if (c < 4) leftLuma else rightLuma
            }
        }
        return grid
    }

    @Test
    fun steadyDeskFrames_produceZeroPagesAndStayReady() {
        val detector = PageTurnDetector()
        var t = 1_000L
        var eval = detector.processLumaGrid(solidGrid(170f), t)
        repeat(30) { i ->
            t += 50L
            val jitter = if (i % 2 == 0) 171.2f else 169.3f
            eval = detector.processLumaGrid(solidGrid(jitter), t)
            assertFalse(eval.justTurnedPage)
        }
        assertEquals(0, eval.pagesTurned)
        assertEquals(PageTurnState.STEADY, eval.state)
        assertFalse(eval.isLowLight)
    }

    @Test
    fun fullPageTurnCycle_incrementsCountOnceAndEntersCooldown() {
        val detector = PageTurnDetector()
        var t = 5_000L

        // 3 calibration frames + steady frame
        repeat(4) {
            detector.processLumaGrid(solidGrid(185f), t)
            t += 50L
        }

        // Physical page turn sweep (>140ms duration, delta >= 13.5)
        val turnStart = detector.processLumaGrid(sweepGrid(120f, 185f), t)
        assertEquals(PageTurnState.TURNING, turnStart.state)
        assertFalse(turnStart.justTurnedPage)

        t += 110L
        val midTurn = detector.processLumaGrid(sweepGrid(110f, 130f), t)
        assertEquals(PageTurnState.TURNING, midTurn.state)
        assertFalse(midTurn.justTurnedPage)

        t += 110L
        val settling1 = detector.processLumaGrid(solidGrid(176f), t) // big frameDelta from sweep -> new page
        assertFalse(settling1.justTurnedPage)

        // 3 consecutive settled frames on new page
        t += 60L
        assertFalse(detector.processLumaGrid(solidGrid(176f), t).justTurnedPage)
        t += 60L
        assertFalse(detector.processLumaGrid(solidGrid(176f), t).justTurnedPage)
        t += 60L
        val counted = detector.processLumaGrid(solidGrid(176f), t)

        assertTrue(counted.justTurnedPage)
        assertEquals(1, counted.pagesTurned)
        assertEquals(PageTurnState.COOLDOWN, counted.state)

        // Next frame inside cooldown must not double count
        t += 80L
        val next = detector.processLumaGrid(solidGrid(176f), t)
        assertFalse(next.justTurnedPage)
        assertEquals(1, next.pagesTurned)
    }

    @Test
    fun fastExposureFlickerUnderMinDuration_isIgnored() {
        val detector = PageTurnDetector()
        var t = 10_000L

        repeat(4) {
            detector.processLumaGrid(solidGrid(180f), t)
            t += 50L
        }

        // Brief spike lasting < 140ms before settling back
        detector.processLumaGrid(solidGrid(125f), t)
        t += 30L
        detector.processLumaGrid(solidGrid(180f), t)
        t += 30L
        detector.processLumaGrid(solidGrid(180f), t)
        t += 30L
        detector.processLumaGrid(solidGrid(180f), t)
        t += 30L
        val settled = detector.processLumaGrid(solidGrid(180f), t) // total duration = 120ms < 140ms

        assertFalse(settled.justTurnedPage)
        assertEquals(0, settled.pagesTurned)
        assertEquals(PageTurnState.STEADY, settled.state)
    }

    @Test
    fun cooldownSuppressionSecondTurnAndUndoAdjustCount_workSafely() {
        val detector = PageTurnDetector()
        var t = 20_000L

        // Calibrate
        repeat(4) {
            detector.processLumaGrid(solidGrid(180f), t)
            t += 50L
        }

        // Turn #1
        detector.processLumaGrid(sweepGrid(115f, 180f), t)
        t += 180L
        detector.processLumaGrid(solidGrid(178f), t)
        t += 60L
        detector.processLumaGrid(solidGrid(178f), t)
        t += 60L
        detector.processLumaGrid(solidGrid(178f), t)
        t += 60L
        val afterFirst = detector.processLumaGrid(solidGrid(178f), t)
        assertEquals(1, afterFirst.pagesTurned)
        assertEquals(PageTurnState.COOLDOWN, afterFirst.state)

        // Motion during cooldown (<1100ms) is suppressed
        t += 400L
        val duringCooldown = detector.processLumaGrid(sweepGrid(95f, 178f), t)
        assertEquals(PageTurnState.COOLDOWN, duringCooldown.state)
        assertEquals(1, duringCooldown.pagesTurned)

        // Return to still page while cooldown finishes, then verify transition back to STEADY
        t += 200L
        detector.processLumaGrid(solidGrid(178f), t)
        t += 650L
        val recovered = detector.processLumaGrid(solidGrid(178f), t)
        assertEquals(PageTurnState.STEADY, recovered.state)

        // Turn #2
        t += 100L
        detector.processLumaGrid(sweepGrid(112f, 178f), t)
        t += 200L
        detector.processLumaGrid(solidGrid(182f), t)
        t += 60L
        detector.processLumaGrid(solidGrid(182f), t)
        t += 60L
        detector.processLumaGrid(solidGrid(182f), t)
        t += 60L
        val afterSecond = detector.processLumaGrid(solidGrid(182f), t)
        assertTrue(afterSecond.justTurnedPage)
        assertEquals(2, afterSecond.pagesTurned)

        // Undo (-1) and floor clamp at 0
        assertEquals(1, detector.adjustCount(-1))
        assertEquals(1, detector.getPagesTurned())
        assertEquals(0, detector.adjustCount(-5))
        assertEquals(0, detector.getPagesTurned())
    }

    @Test
    fun downsampleLumaPlane_computesAccurateGridFromByteBuffer() {
        val width = 16
        val height = 16
        val bytes = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bytes[y * width + x] = if (x < width / 2) 40.toByte() else 200.toByte()
            }
        }
        val buffer = ByteBuffer.wrap(bytes)
        val grid = PageTurnDetector.downsampleLumaPlane(
            buffer = buffer,
            width = width,
            height = height,
            rowStride = width,
            pixelStride = 1,
            cols = 8,
            rows = 8
        )

        assertEquals(64, grid.size)
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val expected = if (c < 4) 40f else 200f
                assertEquals(expected, grid[r * 8 + c], 0.5f)
            }
        }
    }
}
