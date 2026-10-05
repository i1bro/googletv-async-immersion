package com.neilturner.aerialviews.ui.toposcan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScanTimelineTest {
    private fun reveal(
        fps: Double = 25.0,
        delay: Double = 4.0,
    ): ScanTimeline =
        ScanTimeline(ScanSettings(field = 0.1, freezeDelay = delay)).apply {
            begin(true, fps)
            advance(0.1, 1)
            finishFrame()
            assertEquals(ScanPhase.REVEAL, phase)
        }

    @Test
    fun `stalls and duplicate callbacks never cause a catch up step`() {
        val timeline = reveal()
        timeline.advance(0.04, 2)
        assertEquals(0.04, timeline.time, 1e-9)
        timeline.advance(8.0)
        timeline.advance(8.0, 2)
        assertEquals(0.04, timeline.time, 1e-9)
        timeline.advance(8.0, 5_000_000_000)
        assertEquals(0.08, timeline.time, 1e-9)
    }

    @Test
    fun `pause discards frames and loop timestamps still count once`() {
        val timeline = reveal(30000.0 / 1001.0)
        timeline.paused = true
        timeline.advance(5.0, 100)
        timeline.paused = false
        timeline.advance(5.0, 100)
        assertEquals(0.0, timeline.time)
        timeline.advance(0.01, 1)
        assertEquals(1001.0 / 30000.0, timeline.time, 1e-9)
    }

    @Test
    fun `trailing freeze completes before hold and full cycle`() {
        val timeline = reveal()
        repeat(810) { timeline.advance(0.04, it.toLong() + 2) }
        assertEquals(1f, timeline.reveal)
        assertTrue(timeline.freeze < 1)
        repeat(100) { timeline.advance(0.04, it.toLong() + 812) }
        assertEquals(1f, timeline.freeze)
        timeline.finishFrame()
        assertEquals(ScanPhase.HOLD, timeline.phase)
        repeat(100) {
            timeline.advance(0.1)
            timeline.finishFrame()
        }
        assertEquals(ScanPhase.OUT, timeline.phase)
        repeat(400) {
            timeline.advance(0.1)
            timeline.finishFrame()
        }
        assertEquals(ScanPhase.WAIT, timeline.phase)
        timeline.begin(false, Double.NaN)
        assertEquals(-1f, timeline.direction)
    }

    @Test
    fun `zero delay freezes at the reveal boundary and photos use bounded wall time`() {
        val timeline = reveal(delay = 0.0)
        timeline.advance(0.04, 2)
        assertEquals(timeline.freeze, timeline.reveal)
        timeline.begin(false, 0.0)
        timeline.advance(10.0)
        assertEquals(0.1, timeline.time)
    }
}
