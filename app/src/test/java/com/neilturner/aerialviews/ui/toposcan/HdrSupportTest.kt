package com.neilturner.aerialviews.ui.toposcan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdrSupportTest {
    @Test
    fun `PQ output still needs Android 13 display HDR10 GLES3 and the PQ extension`() {
        assertTrue(HdrSupport.evaluate(33, true, true, true).available)
        assertFalse(HdrSupport.evaluate(32, true, true, true).available)
        assertFalse(HdrSupport.evaluate(35, false, true, true).available)
        assertFalse(HdrSupport.evaluate(35, true, false, true).available)
        assertFalse(HdrSupport.evaluate(35, true, true, false).available)
    }

    @Test
    fun `Android 12 can select HLG but never enables the unsupported PQ path`() {
        assertEquals(HdrOutput.HLG, HdrSupport.evaluate(31, true, true, true, true, true).output)
        assertFalse(HdrSupport.evaluate(31, true, true, true, true, false).available)
        assertFalse(HdrSupport.evaluate(31, true, true, true, false, true).available)
        assertFalse(HdrSupport.evaluate(30, true, true, true, true, true).available)
        assertFalse(HdrSupport.evaluate(31, true, false, true, true, true).available)
        assertTrue(HdrSupport.evaluate(31, true, true, true, true, false).reason.contains("EGL BT.2020/HLG"))
    }

    @Test
    fun `auto prefers PQ but falls back to HLG when PQ output is unavailable`() {
        assertEquals(HdrOutput.HDR10, HdrSupport.evaluate(33, true, true, true, true, true).output)
        assertEquals(HdrOutput.HLG, HdrSupport.evaluate(33, true, true, false, true, true).output)
        assertEquals(HdrOutput.HLG, HdrSupport.evaluate(33, false, true, true, true, true).output)
    }

    @Test
    fun `explicit output choices are capability gated and never silently substituted`() {
        assertEquals(HdrOutput.HLG, HdrSupport.evaluate(33, true, true, true, true, true, "hlg").output)
        assertFalse(HdrSupport.evaluate(33, true, true, true, true, false, "hlg").available)
        assertFalse(HdrSupport.evaluate(31, true, true, true, true, true, "hdr10").available)
        assertFalse(HdrSupport.evaluate(33, true, true, false, true, true, "hdr10").available)
    }

    @Test
    fun `PQ and HLG have different EGL colour space tags`() {
        assertEquals(0x3340, HdrOutput.HDR10.eglColourSpace)
        assertEquals(0x3540, HdrOutput.HLG.eglColourSpace)
    }
}
