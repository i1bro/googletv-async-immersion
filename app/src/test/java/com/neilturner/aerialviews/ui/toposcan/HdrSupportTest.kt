package com.neilturner.aerialviews.ui.toposcan

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdrSupportTest {
    @Test
    fun `HDR output needs Android 13 display HDR10 GLES3 and the PQ extension`() {
        assertTrue(HdrSupport.evaluate(33, true, true, true).available)
        assertFalse(HdrSupport.evaluate(32, true, true, true).available)
        assertFalse(HdrSupport.evaluate(35, false, true, true).available)
        assertFalse(HdrSupport.evaluate(35, true, false, true).available)
        assertFalse(HdrSupport.evaluate(35, true, true, false).available)
    }
}
