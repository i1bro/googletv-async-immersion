package com.neilturner.aerialviews.ui.toposcan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutputTileTest {
    @Test
    fun fourFullHdBuffersCoverEvery4kPixelWithoutScaling() {
        val tiles = OutputTile.split(RenderSize(3840, 2160), true)
        assertEquals(4, tiles.size)
        assertTrue(tiles.all { it.width == 1920 && it.height == 1080 })
        assertEquals(listOf(0 to 0, 1920 to 0, 0 to 1080, 1920 to 1080), tiles.map { it.left to it.top })
    }

    @Test
    fun oddSizesHaveNoGapsOrOverlap() {
        val size = RenderSize(3839, 2159)
        val tiles = OutputTile.split(size, true)
        assertEquals(size.width * size.height, tiles.sumOf { it.width * it.height })
        assertEquals(tiles[0].left + tiles[0].width, tiles[1].left)
        assertEquals(tiles[0].top + tiles[0].height, tiles[2].top)
        assertEquals(size.width, tiles[3].left + tiles[3].width)
        assertEquals(size.height, tiles[3].top + tiles[3].height)
    }

    @Test
    fun ordinaryOutputRemainsOneFullSizeWindow() {
        assertEquals(listOf(OutputTile(0, 0, 3840, 2160)), OutputTile.split(RenderSize(3840, 2160), false))
    }
}
