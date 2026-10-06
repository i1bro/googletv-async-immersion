package com.neilturner.aerialviews.ui.toposcan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SdrSurfaceModeTest {
    @Test
    fun `new and unknown settings use RGBA on the standard layer`() {
        for (value in listOf("rgba", "", "unknown")) {
            val mode = SdrSurfaceMode.fromPreference(value)
            assertEquals(SdrSurfaceMode.RGBA, mode)
            assertEquals(8, mode.alphaBits)
            assertFalse(mode.mediaOverlay)
        }
    }

    @Test
    fun `legacy mode preserves the previous EGL alpha and layer`() {
        val mode = SdrSurfaceMode.fromPreference("legacy")
        assertEquals(SdrSurfaceMode.LEGACY, mode)
        assertEquals(0, mode.alphaBits)
        assertTrue(mode.mediaOverlay)
    }
}
