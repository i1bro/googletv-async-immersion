package com.neilturner.aerialviews.ui.toposcan

import kotlin.math.floor

data class RenderSize(
    val width: Int,
    val height: Int,
) {
    companion object {
        /** The surface can exceed the TV's 1080p UI, but never the active display or GPU limits. */
        fun choose(
            width: Int,
            height: Int,
            limit: Int,
            maxTextureSize: Int,
        ): RenderSize {
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            val cap = minOf(limit.coerceIn(1280, 3840), maxTextureSize.coerceAtLeast(1))
            val scale = minOf(1.0, cap.toDouble() / maxOf(w, h))
            return RenderSize(floor(w * scale).toInt().coerceAtLeast(1), floor(h * scale).toInt().coerceAtLeast(1))
        }
    }
}
