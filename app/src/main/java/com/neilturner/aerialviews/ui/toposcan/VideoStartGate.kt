package com.neilturner.aerialviews.ui.toposcan

/** Decoder notifications and SurfaceTexture frames can arrive in either order. */
class VideoStartGate {
    private var started = false
    var requested = false
        private set

    fun request() {
        requested = true
        reset()
    }

    fun cancel() {
        requested = false
        reset()
    }

    fun reset() {
        started = false
    }

    fun claim(
        hasFrame: Boolean,
        hasFormat: Boolean,
        hasSurface: Boolean,
    ): Boolean {
        if (!requested || started || !hasFrame || !hasFormat || !hasSurface) return false
        started = true
        return true
    }
}
