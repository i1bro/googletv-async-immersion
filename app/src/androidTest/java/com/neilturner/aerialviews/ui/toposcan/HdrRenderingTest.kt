package com.neilturner.aerialviews.ui.toposcan

import android.annotation.SuppressLint
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLES30
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.DefaultShaderProgram
import androidx.media3.effect.DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_LINEAR
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Offscreen pixel tests, not a claim that the emulator's display can emit HDR. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
@SuppressLint("RestrictedApi")
class HdrRenderingTest {
    @Test
    fun pqRoundTripRetainsTenBitGradationsAndHighlightsThroughEffect() = pqRamp(HdrOutput.HDR10)

    @Test
    fun pqToHlgRetainsGradationsAndCompressesHighlightsThroughEffect() = pqRamp(HdrOutput.HLG)

    private fun pqRamp(mode: HdrOutput) =
        withGl {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val width = 1024
            val colour =
                ColorInfo
                    .Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .setColorRange(C.COLOR_RANGE_FULL)
                    .build()
            val input = GlUtil.createRgb10A2Texture(width, 1)
            val codes = ByteBuffer.allocateDirect(width * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
            for (i in 0 until width) codes.put(i or (i shl 10) or (i shl 20) or (3 shl 30))
            codes.position(0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, input)
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, 1, GLES20.GL_RGBA, GLES30.GL_UNSIGNED_INT_2_10_10_10_REV, codes)

            val linear = GlUtil.createTexture(width, 1, true)
            val scene = GlUtil.createTexture(width, 1, true)
            val encoded = GlUtil.createRgb10A2Texture(width, 1)
            val decoder =
                DefaultShaderProgram.createWithInternalSampler(
                    context,
                    colour,
                    colour.buildUpon().setColorTransfer(C.COLOR_TRANSFER_LINEAR).build(),
                    WORKING_COLOR_SPACE_LINEAR,
                    VideoFrameProcessor.INPUT_TYPE_TEXTURE_ID,
                )
            val output = HdrFramePipeline(context, mode)
            try {
                decoder.configure(width, 1)
                focus(linear, width, 1)
                decoder.drawFrame(input, 0)
                val values = floats(width)
                // Media3's linear working scale is 1.0 = 1000 nits, not a clamped SDR white.
                assertEquals(10f, values[(width - 1) * 4], .02f)
                assertTrue("4000-nit highlights were clipped", values[923 * 4] > 3.5f)

                focus(scene, width, 1)
                drawScene(linear, linear, ScanPhase.HOLD, 1f)
                focus(encoded, width, 1)
                output.configureOutput(width, 1)
                output.present(scene)
                val actual = ByteBuffer.allocateDirect(width * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
                GLES20.glReadPixels(0, 0, width, 1, GLES20.GL_RGBA, GLES30.GL_UNSIGNED_INT_2_10_10_10_REV, actual)
                GlUtil.checkGlError()
                val red = (0 until width).map { actual[it] and 1023 }
                if (mode == HdrOutput.HDR10) {
                    assertTrue("Output was reduced to 8-bit steps", red.toSet().size > 900)
                    assertTrue("PQ round trip changed luminance", (32 until width).all { abs(red[it] - it) <= 2 })
                } else {
                    assertTrue("HLG output was reduced to 8-bit steps", red.toSet().size > 600)
                    assertTrue("HLG ramp must be monotonic", red.zipWithNext().all { (a, b) -> b >= a })
                    assertTrue("PQ to HLG luminance mismatch", (32 until width).all { abs(red[it] - hlgReference(it)) <= 3 })
                    assertTrue("1000/4000/10000-nit highlights must remain distinct", red[769] < red[923] && red[923] < red[1023])
                    assertEquals(0, red.first())
                }
            } finally {
                decoder.release()
                output.release()
            }
        }

    @Test
    fun hlgOutputKeepsSaturatedHighlightsFiniteAndInGamut() =
        withGl {
            val source = GlUtil.createTexture(4, 1, true)
            val encoded = GlUtil.createTexture(4, 1, true)
            val output = HdrFramePipeline(InstrumentationRegistry.getInstrumentation().targetContext, HdrOutput.HLG)
            try {
                for (rgb in listOf(floatArrayOf(10f, 0f, 0f, 1f), floatArrayOf(0f, 10f, 0f, 1f), floatArrayOf(0f, 0f, 10f, 1f))) {
                    upload(source, 4, rgb)
                    focus(encoded, 4, 1)
                    output.present(source)
                    assertTrue("HLG gamut mapping produced invalid values", floats(4).all { it.isFinite() && it in 0f..1.001f })
                }
            } finally {
                output.release()
            }
        }

    // Independent double-precision reference: ST2084 EOTF -> HDR shoulder -> inverse HLG OOTF -> OETF.
    private fun hlgReference(code: Int): Int {
        val e = (code / 1023.0).pow(1.0 / (2523.0 / 32.0))
        val light = (maxOf(e - 3424.0 / 4096.0, 0.0) / (2413.0 / 128.0 - 2392.0 / 128.0 * e)).pow(1.0 / (2610.0 / 16384.0)) * 10
        val mapped = if (light <= 0.5) light else 1.0 - 0.25 / light
        val scene = mapped.pow(1.0 / 1.2)
        val hlg = if (scene <= 1.0 / 12.0) sqrt(3 * scene) else 0.17883277 * ln(12 * scene - 0.28466892) + 0.55991073
        return (hlg * 1023).roundToInt()
    }

    @Test
    fun colourFieldMixesLinearHdrLightWithoutClipping() =
        withGl {
            val a = GlUtil.createTexture(4, 1, true)
            val b = GlUtil.createTexture(4, 1, true)
            val result = GlUtil.createTexture(4, 1, true)
            upload(a, 4, floatArrayOf(4f, 2f, .1f, 1f))
            upload(b, 4, floatArrayOf(.1f, .2f, 3f, 1f))
            focus(result, 4, 1)
            drawScene(b, a, ScanPhase.FIELD, .5f)
            val pixel = floats(4)
            assertEquals(2.05f, pixel[0], .005f)
            assertEquals(1.1f, pixel[1], .005f)
            assertEquals(1.55f, pixel[2], .005f)
        }

    private fun drawScene(
        live: Int,
        previous: Int,
        phase: ScanPhase,
        progress: Float,
    ) {
        val shader =
            GlProgram(
                "attribute vec2 aPosition; varying vec2 vUV; void main() { vUV=aPosition*.5+.5; gl_Position=vec4(aPosition,0.,1.); }",
                ToposcanView.SCENE,
            )
        try {
            shader.use()
            shader.setBufferAttribute("aPosition", floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f), 2)
            shader.setSamplerTexIdUniform("uLive", live, 0)
            shader.setSamplerTexIdUniform("uHistory", live, 1)
            shader.setSamplerTexIdUniform("uPrevious", previous, 2)
            mapOf(
                "uPhase" to phase.ordinal.toFloat(),
                "uProgress" to progress,
                "uFreeze" to 1f,
                "uDirection" to 1f,
                "uPreviousDirection" to 1f,
                "uHasPrevious" to 1f,
                "uBand" to 1f,
                "uLinearLight" to 1f,
            ).forEach { (name, value) -> shader.setFloatUniform(name, value) }
            shader.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } finally {
            shader.delete()
        }
    }

    private fun upload(
        texture: Int,
        width: Int,
        rgba: FloatArray,
    ) {
        val values = ByteBuffer.allocateDirect(width * 16).order(ByteOrder.nativeOrder()).asFloatBuffer()
        repeat(width) { values.put(rgba) }
        values.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, 1, GLES20.GL_RGBA, GLES20.GL_FLOAT, values)
    }

    private fun focus(
        texture: Int,
        width: Int,
        height: Int,
    ) {
        val fbo = GlUtil.createFboForTexture(texture)
        GlUtil.focusFramebufferUsingCurrentContext(fbo, width, height)
        assertEquals(GLES20.GL_FRAMEBUFFER_COMPLETE, GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER))
    }

    private fun floats(width: Int): FloatArray {
        val buffer = ByteBuffer.allocateDirect(width * 16).order(ByteOrder.nativeOrder()).asFloatBuffer()
        GLES20.glReadPixels(0, 0, width, 1, GLES20.GL_RGBA, GLES20.GL_FLOAT, buffer)
        GlUtil.checkGlError()
        return FloatArray(width * 4).also { buffer.get(it) }
    }

    private fun withGl(block: () -> Unit) {
        val display = GlUtil.getDefaultEglDisplay()
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        assertTrue(
            EGL14.eglChooseConfig(
                display,
                intArrayOf(
                    EGL14.EGL_RED_SIZE,
                    8,
                    EGL14.EGL_GREEN_SIZE,
                    8,
                    EGL14.EGL_BLUE_SIZE,
                    8,
                    EGL14.EGL_RENDERABLE_TYPE,
                    0x40,
                    EGL14.EGL_SURFACE_TYPE,
                    EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE,
                ),
                0,
                configs,
                0,
                1,
                count,
                0,
            ),
        )
        assertEquals(1, count[0])
        val context =
            EGL14.eglCreateContext(
                display,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
                0,
            )
        val surface =
            EGL14.eglCreatePbufferSurface(
                display,
                configs[0],
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
                0,
            )
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
        try {
            block()
        } finally {
            GlUtil.destroyEglSurface(display, surface)
            GlUtil.destroyEglContext(display, context)
        }
    }
}
