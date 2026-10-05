package com.neilturner.aerialviews.ui.toposcan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RenderSizeTest {
    @Test
    fun `4K is retained on a capable display`() {
        assertEquals(RenderSize(3840, 2160), RenderSize.choose(3840, 2160, 3840, 8192))
    }

    @Test
    fun `display and user limits are respected`() {
        assertEquals(RenderSize(1920, 1080), RenderSize.choose(1920, 1080, 3840, 8192))
        assertEquals(RenderSize(1920, 1080), RenderSize.choose(3840, 2160, 1920, 8192))
    }

    @Test
    fun `GPU and portrait bounds preserve aspect ratio`() {
        assertEquals(RenderSize(2048, 1152), RenderSize.choose(3840, 2160, 3840, 2048))
        assertEquals(RenderSize(1080, 1920), RenderSize.choose(2160, 3840, 1920, 8192))
        assertEquals(RenderSize(3840, 2160), RenderSize.choose(7680, 4320, 9999, 16384))
    }

    @Test
    fun `empty layout never produces zero size buffers`() {
        assertEquals(RenderSize(1, 1), RenderSize.choose(0, 0, 3840, 0))
    }
}
