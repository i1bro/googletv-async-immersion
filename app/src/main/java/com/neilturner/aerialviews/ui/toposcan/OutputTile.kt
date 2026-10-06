package com.neilturner.aerialviews.ui.toposcan

/** Pixel coordinates in the full image, with the origin at the top left. */
data class OutputTile(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    companion object {
        fun split(
            size: RenderSize,
            tiled: Boolean,
        ): List<OutputTile> {
            if (!tiled || size.width < 2 || size.height < 2) return listOf(OutputTile(0, 0, size.width, size.height))
            val x = size.width / 2
            val y = size.height / 2
            return listOf(
                OutputTile(0, 0, x, y),
                OutputTile(x, 0, size.width - x, y),
                OutputTile(0, y, x, size.height - y),
                OutputTile(x, y, size.width - x, size.height - y),
            )
        }
    }
}
