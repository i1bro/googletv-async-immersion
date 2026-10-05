package com.neilturner.aerialviews.ui.toposcan

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoStartGateTest {
    @Test
    fun `metadata before texture frame waits then starts once`() {
        val gate = VideoStartGate().apply { request() }
        assertFalse(gate.claim(hasFrame = false, hasFormat = true, hasSurface = true))
        assertTrue(gate.claim(hasFrame = true, hasFormat = true, hasSurface = true))
        assertFalse(gate.claim(hasFrame = true, hasFormat = true, hasSurface = true))
    }

    @Test
    fun `texture frame before metadata waits then starts without decoder notification`() {
        val gate = VideoStartGate().apply { request() }
        assertFalse(gate.claim(hasFrame = true, hasFormat = false, hasSurface = true))
        assertTrue(gate.claim(hasFrame = true, hasFormat = true, hasSurface = true))
    }

    @Test
    fun `missing surface does not consume start and reset permits next clip`() {
        val gate = VideoStartGate().apply { request() }
        assertFalse(gate.claim(hasFrame = true, hasFormat = true, hasSurface = false))
        assertTrue(gate.claim(hasFrame = true, hasFormat = true, hasSurface = true))
        gate.request()
        assertFalse(gate.claim(hasFrame = false, hasFormat = true, hasSurface = true))
        assertTrue(gate.claim(hasFrame = true, hasFormat = true, hasSurface = true))
    }

    @Test
    fun `late decoder events cannot start stopped media after surface recreation`() {
        val gate = VideoStartGate().apply { request() }
        assertTrue(gate.claim(hasFrame = true, hasFormat = true, hasSurface = true))
        gate.reset()
        assertTrue(gate.claim(hasFrame = true, hasFormat = true, hasSurface = true))
        gate.cancel()
        gate.reset()
        assertFalse(gate.claim(hasFrame = true, hasFormat = true, hasSurface = true))
        gate.request()
        assertTrue(gate.claim(hasFrame = true, hasFormat = true, hasSurface = true))
    }
}
