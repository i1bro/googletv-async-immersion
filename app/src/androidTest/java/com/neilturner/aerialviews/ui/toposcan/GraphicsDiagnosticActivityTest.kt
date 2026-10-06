package com.neilturner.aerialviews.ui.toposcan

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GraphicsDiagnosticActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun rgbaStagesRenderKnownPixelsAndLeavePlaybackSettingsUnchanged() = verifyStages(SdrSurfaceMode.RGBA)

    @Test
    fun legacyStagesStillRenderKnownPixels() = verifyStages(SdrSurfaceMode.LEGACY)

    @Test
    fun tiledStagesRenderAllFourSubmittedWindows() = verifyStages(SdrSurfaceMode.TILED)

    @Suppress("DEPRECATION")
    private fun verifyStages(mode: SdrSurfaceMode) {
        instrumentation.setInTouchMode(false)
        val oldSurface = GeneralPrefs.toposcanSdrSurface
        instrumentation.runOnMainSync { GeneralPrefs.toposcanSdrSurface = mode.preference }
        val width = GeneralPrefs.toposcanResolution
        val hdr = GeneralPrefs.toposcanHdrEnabled
        val enabled = GeneralPrefs.toposcanEnabled
        val status = GeneralPrefs.toposcanPlaybackStatus
        val activity =
            instrumentation.startActivitySync(
                Intent(context, GraphicsDiagnosticActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        try {
            for (stage in 0..5) {
                var button: Button? = null
                val deadline = System.currentTimeMillis() + 20_000
                while (button == null && System.currentTimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        button =
                            descendants(activity.window.decorView).filterIsInstance<Button>().firstOrNull {
                                it.isEnabled && it.text == context.getString(R.string.toposcan_graphics_visible)
                            }
                    }
                    Thread.sleep(50)
                }
                assertTrue("Stage $stage never finished: ${GeneralPrefs.toposcanGraphicsStatus}", button != null)
                val report = GeneralPrefs.toposcanGraphicsStatus
                for (error in listOf("FAIL", "failed", "TIMEOUT", "SKIPPED", "GL error")) assertFalse(report, report.contains(error))
                assertTrue(report, report.contains("PixelCopy: OK"))
                assertTrue(report, report.contains(mode.label))
                assertTrue(report, report.contains("EGL RGBA 8/8/8/${mode.alphaBits}"))
                lateinit var surface: ToposcanView
                instrumentation.runOnMainSync {
                    surface = descendants(activity.window.decorView).filterIsInstance<ToposcanView>().single()
                }
                assertEquals(if (stage < 3) RenderSize(1920, 1080) else RenderSize(3840, 2160), surface.renderSize)
                val pixels = Bitmap.createBitmap(surface.renderSize.width, surface.renderSize.height, Bitmap.Config.ARGB_8888)
                val copied = CountDownLatch(1)
                var copyResult = -1
                surface.copyPresented(pixels, Handler(Looper.getMainLooper())) {
                    copyResult = it
                    copied.countDown()
                }
                assertTrue(copied.await(5, TimeUnit.SECONDS))
                assertEquals(PixelCopy.SUCCESS, copyResult)
                val samples =
                    (0..2).flatMap { row ->
                        (0..3).map { column ->
                            pixels.getPixel(
                                (2 * column + 1) * pixels.width / 8,
                                (2 * row + 1) * pixels.height / 6,
                            )
                        }
                    }
                assertTrue("Displayed buffer has incorrect pixels at stage $stage", GraphicsDiagnostic.matches(samples))
                pixels.recycle()
                instrumentation.runOnMainSync {
                    val windows = surface.output.surfaces
                    assertEquals(if (mode == SdrSurfaceMode.TILED) 4 else 1, windows.size)
                    val divisor = if (mode == SdrSurfaceMode.TILED) 2 else 1
                    for (window in windows) {
                        assertEquals(surface.renderSize.width / divisor, window.holder.surfaceFrame.width())
                        assertEquals(surface.renderSize.height / divisor, window.holder.surfaceFrame.height())
                    }
                }
                if (stage == 5) save("graphics-${mode.preference}-4k-external", instrumentation.uiAutomation.takeScreenshot())
                if (stage == 5 && mode == SdrSurfaceMode.TILED) {
                    instrumentation.runOnMainSync { surface.visibility = View.INVISIBLE }
                    instrumentation.waitForIdleSync()
                    Thread.sleep(200)
                    instrumentation.runOnMainSync { surface.visibility = View.VISIBLE }
                    val restored = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
                    var valid = false
                    val restoreDeadline = System.currentTimeMillis() + 10_000
                    while (!valid && System.currentTimeMillis() < restoreDeadline) {
                        val latch = CountDownLatch(1)
                        surface.copyPresented(restored, Handler(Looper.getMainLooper())) { result ->
                            valid = result == PixelCopy.SUCCESS && GraphicsDiagnostic.matches(restored)
                            latch.countDown()
                        }
                        assertTrue(latch.await(5, TimeUnit.SECONDS))
                        if (!valid) Thread.sleep(100)
                    }
                    restored.recycle()
                    assertTrue("Tiled surfaces did not recover after hide/show", valid)
                }
                assertTrue("Remote focus is missing", checkNotNull(button).hasFocus())
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                instrumentation.waitForIdleSync()
            }
            val report = GeneralPrefs.toposcanGraphicsStatus
            assertEquals(6, Regex(": visible").findAll(report).count())
            assertEquals(6, Regex("PixelCopy: OK").findAll(report).count())
            assertFalse(report, report.contains("unconfirmed"))
            assertEquals(width, GeneralPrefs.toposcanResolution)
            assertEquals(hdr, GeneralPrefs.toposcanHdrEnabled)
            assertEquals(enabled, GeneralPrefs.toposcanEnabled)
            assertEquals(status, GeneralPrefs.toposcanPlaybackStatus)
            assertEquals(mode.preference, GeneralPrefs.toposcanSdrSurface)
            Thread.sleep(300)
            save("graphics-${mode.preference}-results", instrumentation.uiAutomation.takeScreenshot())
        } finally {
            instrumentation.runOnMainSync {
                activity.finish()
                GeneralPrefs.toposcanSdrSurface = oldSurface
            }
        }
    }

    @Test
    fun earlyExitPreservesAnUnconfirmedResult() {
        val activity =
            instrumentation.startActivitySync(
                Intent(context, GraphicsDiagnosticActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        instrumentation.runOnMainSync { activity.finish() }
        instrumentation.waitForIdleSync()
        assertTrue(GeneralPrefs.toposcanGraphicsStatus.contains("unconfirmed"))
        assertFalse(GeneralPrefs.toposcanGraphicsStatus.contains(": visible"))
    }

    private fun descendants(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun save(
        name: String,
        bitmap: Bitmap,
    ) {
        val folder = File(context.getExternalFilesDir(null), "toposcan-tests").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
