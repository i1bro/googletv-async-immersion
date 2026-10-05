package com.neilturner.aerialviews.ui.toposcan

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

@RunWith(AndroidJUnit4::class)
class HdrEglTest {
    @Test
    fun hlgCreatesATaggedWindowAndConfirmsTheReturnedTag() = checkSurface(0x3540, true)

    @Test
    fun driverReturningPqForAnHlgRequestFailsClosed() = checkSurface(0x3340, false)

    @Suppress("UNCHECKED_CAST")
    private fun checkSurface(
        returnedColour: Int,
        expectedReady: Boolean,
    ) {
        val display = object : EGLDisplay() {}
        val config = object : EGLConfig() {}
        val surface = object : EGLSurface() {}
        var requestedColour = 0
        // Inject EGL responses without needing an HDR display on the emulator.
        val egl =
            Proxy.newProxyInstance(EGL10::class.java.classLoader, arrayOf(EGL10::class.java)) { _, method, args ->
                when (method.name) {
                    "eglChooseConfig" -> {
                        (args[4] as IntArray)[0] = 1
                        (args[2] as Array<EGLConfig?>?)?.set(0, config)
                        true
                    }

                    "eglGetConfigAttrib" -> {
                        (args[3] as IntArray)[0] = 10
                        true
                    }

                    "eglCreateWindowSurface" -> {
                        requestedColour = (args[3] as IntArray)[1]
                        surface
                    }

                    "eglQuerySurface" -> {
                        (args[3] as IntArray)[0] = returnedColour
                        true
                    }

                    "eglGetError" -> {
                        EGL10.EGL_SUCCESS
                    }

                    else -> {
                        error("Unexpected EGL call: ${method.name}")
                    }
                }
            } as EGL10
        val chooser = HdrEgl(HdrOutput.HLG)
        chooser.chooseConfig(egl, display)
        chooser.createWindowSurface(egl, display, config, Any())
        assertEquals(0x3540, requestedColour)
        assertEquals(expectedReady, chooser.ready)
    }
}
