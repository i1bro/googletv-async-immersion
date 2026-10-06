package com.neilturner.aerialviews.ui.toposcan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

/** One GL thread and one set of full-resolution textures; only presentation is split. */
internal class ScanOutputView(
    context: Context,
    hdrEgl: HdrEgl?,
    mode: SdrSurfaceMode,
) : FrameLayout(context) {
    val primary = GLSurfaceView(context)
    private val tiled = hdrEgl == null && mode == SdrSurfaceMode.TILED
    val surfaces: List<SurfaceView> = listOf(primary) + if (tiled) List(3) { SurfaceView(context) } else emptyList()

    @Volatile
    var bufferSize = RenderSize(1920, 1080)
        private set

    private data class NativeWindow(
        val surface: Surface,
        val width: Int,
        val height: Int,
    )

    private class WindowSlot {
        @Volatile var native: NativeWindow? = null
        var bound: NativeWindow? = null
        var eglSurface: EGLSurface? = null
    }

    private val slots = List(surfaces.size - 1) { WindowSlot() }
    private var eglDisplay: EGLDisplay? = null
    private var eglConfig: EGLConfig? = null
    private var released = false

    init {
        if (hdrEgl != null) {
            primary.holder.setFormat(PixelFormat.RGBA_1010102)
            primary.setEGLConfigChooser(hdrEgl)
            primary.setEGLContextFactory(hdrEgl)
            primary.setEGLWindowSurfaceFactory(hdrEgl)
        } else {
            primary.setEGLContextClientVersion(2)
            primary.setEGLConfigChooser(8, 8, 8, mode.alphaBits, 0, 0)
        }
        primary.preserveEGLContextOnPause = true
        surfaces.forEachIndexed { index, view ->
            if (hdrEgl == null && mode != SdrSurfaceMode.LEGACY) view.holder.setFormat(PixelFormat.RGBA_8888)
            view.setZOrderMediaOverlay(hdrEgl != null || mode.mediaOverlay)
            addView(view)
            if (index > 0) {
                val slot = slots[index - 1]
                view.holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = Unit

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            slot.native = NativeWindow(holder.surface, width, height)
                            primary.requestRender()
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            slot.native = null
                            if (released) return
                            // Stop using this native window before SurfaceView destroys it.
                            val done = CountDownLatch(1)
                            primary.queueEvent {
                                try {
                                    destroy(slot)
                                } finally {
                                    done.countDown()
                                }
                            }
                            done.await(2, TimeUnit.SECONDS)
                        }
                    },
                )
            }
        }
    }

    fun setBufferSize(size: RenderSize) {
        bufferSize = size
        OutputTile.split(size, tiled).forEachIndexed { index, tile ->
            surfaces[index].holder.setFixedSize(tile.width, tile.height)
        }
        requestLayout()
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val size = bufferSize
        OutputTile.split(size, tiled).forEachIndexed { index, tile ->
            surfaces[index].layout(
                tile.left * width / size.width,
                tile.top * height / size.height,
                (tile.left + tile.width) * width / size.width,
                (tile.top + tile.height) * height / size.height,
            )
        }
    }

    fun contextCreated(config: EGLConfig?) {
        releaseGl()
        eglDisplay = egl().eglGetCurrentDisplay()
        eglConfig = config
    }

    /** Called on the GL thread. Auxiliary windows use the exact same EGL context as the primary. */
    fun ready(): Boolean {
        val tiles = OutputTile.split(bufferSize, tiled)
        val egl = egl()
        val display = eglDisplay ?: return false
        val config = eglConfig ?: return false
        val actual = IntArray(1)
        val primaryWindow = egl.eglGetCurrentSurface(EGL10.EGL_DRAW)
        egl.eglQuerySurface(display, primaryWindow, EGL10.EGL_WIDTH, actual)
        if (actual[0] != tiles.first().width) return false
        egl.eglQuerySurface(display, primaryWindow, EGL10.EGL_HEIGHT, actual)
        if (actual[0] != tiles.first().height) return false
        for ((index, slot) in slots.withIndex()) {
            val native = slot.native ?: return false
            val tile = tiles[index + 1]
            if (native.width != tile.width || native.height != tile.height || !native.surface.isValid) return false
            if (slot.bound !== native) {
                destroy(slot)
                val window = egl.eglCreateWindowSurface(display, config, native.surface, intArrayOf(EGL10.EGL_NONE))
                check(window != null && window != EGL10.EGL_NO_SURFACE) { "Tile EGL window failed: 0x${egl.eglGetError().toString(16)}" }
                slot.eglSurface = window
                slot.bound = native
            }
        }
        return true
    }

    fun present(draw: (OutputTile) -> Unit) {
        val tiles = OutputTile.split(bufferSize, tiled)
        if (slots.isEmpty()) {
            val tile = tiles.single()
            GLES20.glViewport(0, 0, tile.width, tile.height)
            draw(tile)
            return
        }
        val egl = egl()
        val display = egl.eglGetCurrentDisplay()
        val context = egl.eglGetCurrentContext()
        val originalDraw = egl.eglGetCurrentSurface(EGL10.EGL_DRAW)
        val originalRead = egl.eglGetCurrentSurface(EGL10.EGL_READ)
        try {
            slots.forEachIndexed { index, slot ->
                val window = checkNotNull(slot.eglSurface)
                check(egl.eglMakeCurrent(display, window, window, context)) { "Cannot bind output tile ${index + 2}" }
                // Request unpaced auxiliary swaps; the driver may clamp this to its minimum interval.
                EGL14.eglSwapInterval(EGL14.eglGetCurrentDisplay(), 0)
                val tile = tiles[index + 1]
                GLES20.glViewport(0, 0, tile.width, tile.height)
                draw(tile)
                check(egl.eglSwapBuffers(display, window)) { "Tile swap failed: 0x${egl.eglGetError().toString(16)}" }
            }
        } finally {
            // GLSurfaceView must regain its own window before its managed swap and lifecycle work.
            check(egl.eglMakeCurrent(display, originalDraw, originalRead, context)) { "Cannot restore primary EGL window" }
        }
        val tile = tiles.first()
        GLES20.glViewport(0, 0, tile.width, tile.height)
        draw(tile)
    }

    fun release() {
        released = true
    }

    fun releaseGl() {
        slots.forEach(::destroy)
        eglDisplay = null
        eglConfig = null
    }

    private fun destroy(slot: WindowSlot) {
        slot.eglSurface?.let { window -> eglDisplay?.let { egl().eglDestroySurface(it, window) } }
        slot.eglSurface = null
        slot.bound = null
    }

    /** Reassemble submitted buffers for diagnostics; this is never part of playback. */
    fun copyPresented(
        bitmap: Bitmap,
        handler: Handler,
        callback: (Int) -> Unit,
    ) {
        val size = bufferSize
        val tiles = OutputTile.split(size, tiled)
        val canvas = Canvas(bitmap)

        fun copy(index: Int) {
            if (released || size != bufferSize) {
                callback(PixelCopy.ERROR_SOURCE_INVALID)
                return
            }
            if (index == tiles.size) {
                callback(PixelCopy.SUCCESS)
                return
            }
            val tile = tiles[index]
            val rect =
                Rect(
                    tile.left * bitmap.width / size.width,
                    tile.top * bitmap.height / size.height,
                    (tile.left + tile.width) * bitmap.width / size.width,
                    (tile.top + tile.height) * bitmap.height / size.height,
                )
            val pixels = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(surfaces[index], pixels, { result ->
                    if (result == PixelCopy.SUCCESS) canvas.drawBitmap(pixels, null, rect, null)
                    pixels.recycle()
                    if (result == PixelCopy.SUCCESS) copy(index + 1) else callback(result)
                }, handler)
            } catch (_: IllegalArgumentException) {
                pixels.recycle()
                callback(PixelCopy.ERROR_SOURCE_INVALID)
            }
        }
        handler.post { copy(0) }
    }

    private fun egl() = EGLContext.getEGL() as EGL10
}
