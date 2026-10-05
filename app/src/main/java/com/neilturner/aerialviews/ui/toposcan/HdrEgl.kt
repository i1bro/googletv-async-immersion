package com.neilturner.aerialviews.ui.toposcan

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

/** Config/context/window must all agree on 10-bit PQ. Failure never masquerades as HDR. */
class HdrEgl :
    GLSurfaceView.EGLConfigChooser,
    GLSurfaceView.EGLContextFactory,
    GLSurfaceView.EGLWindowSurfaceFactory {
    @Volatile
    var ready = false
        private set

    @Volatile
    var failure = "HDR EGL initialization incomplete"
        private set

    override fun chooseConfig(
        egl: EGL10,
        display: EGLDisplay,
    ): EGLConfig {
        ready = false
        val hdr = choose(egl, display, 10, 2, 0x40)
        if (hdr != null) {
            ready = true
            return hdr
        }
        failure = "No 10-bit OpenGL ES 3 EGL window configuration"
        return checkNotNull(choose(egl, display, 8, 0, 4)) { "No RGB EGL configuration" }
    }

    private fun choose(
        egl: EGL10,
        display: EGLDisplay,
        bits: Int,
        alpha: Int,
        renderable: Int,
    ): EGLConfig? {
        val attributes =
            intArrayOf(
                EGL10.EGL_RED_SIZE,
                bits,
                EGL10.EGL_GREEN_SIZE,
                bits,
                EGL10.EGL_BLUE_SIZE,
                bits,
                EGL10.EGL_ALPHA_SIZE,
                alpha,
                EGL10.EGL_DEPTH_SIZE,
                0,
                EGL10.EGL_STENCIL_SIZE,
                0,
                EGL10.EGL_RENDERABLE_TYPE,
                renderable,
                EGL10.EGL_SURFACE_TYPE,
                EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_NONE,
            )
        val count = IntArray(1)
        if (!egl.eglChooseConfig(display, attributes, null, 0, count) || count[0] == 0) return null
        val configs = arrayOfNulls<EGLConfig>(count[0])
        if (!egl.eglChooseConfig(display, attributes, configs, configs.size, count)) return null
        return configs.firstOrNull { config ->
            config != null &&
                intArrayOf(EGL10.EGL_RED_SIZE, EGL10.EGL_GREEN_SIZE, EGL10.EGL_BLUE_SIZE).all {
                    val value = IntArray(1)
                    egl.eglGetConfigAttrib(display, config, it, value) && value[0] == bits
                }
        }
    }

    override fun createContext(
        egl: EGL10,
        display: EGLDisplay,
        config: EGLConfig,
    ): EGLContext {
        var result = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, intArrayOf(0x3098, if (ready) 3 else 2, EGL10.EGL_NONE))
        if (result == EGL10.EGL_NO_CONTEXT && ready) {
            failure = "OpenGL ES 3 context failed: ${egl.eglGetError()}"
            ready = false
            result = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, intArrayOf(0x3098, 2, EGL10.EGL_NONE))
        }
        return result
    }

    override fun createWindowSurface(
        egl: EGL10,
        display: EGLDisplay,
        config: EGLConfig,
        nativeWindow: Any,
    ): EGLSurface {
        if (ready) {
            val surface =
                try {
                    egl.eglCreateWindowSurface(display, config, nativeWindow, intArrayOf(0x309D, 0x3340, EGL10.EGL_NONE))
                } catch (_: IllegalArgumentException) {
                    EGL10.EGL_NO_SURFACE
                }
            if (surface != EGL10.EGL_NO_SURFACE) {
                val colour = IntArray(1)
                if (!egl.eglQuerySurface(display, surface, 0x309D, colour) || colour[0] != 0x3340) {
                    failure = "EGL surface did not confirm BT.2020/PQ output"
                    egl.eglGetError()
                    ready = false
                }
                return surface
            }
            failure = "BT.2020/PQ window surface failed: ${egl.eglGetError()}"
            ready = false
        }
        return try {
            egl.eglCreateWindowSurface(display, config, nativeWindow, null)
        } catch (_: IllegalArgumentException) {
            EGL10.EGL_NO_SURFACE
        }
    }

    override fun destroyContext(
        egl: EGL10,
        display: EGLDisplay,
        context: EGLContext,
    ) {
        egl.eglDestroyContext(display, context)
    }

    override fun destroySurface(
        egl: EGL10,
        display: EGLDisplay,
        surface: EGLSurface,
    ) {
        egl.eglDestroySurface(display, surface)
    }
}
