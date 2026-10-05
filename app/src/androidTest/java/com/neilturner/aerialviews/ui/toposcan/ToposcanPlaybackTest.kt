package com.neilturner.aerialviews.ui.toposcan

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neilturner.aerialviews.models.prefs.AmazonVideoPrefs
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.CustomFeedPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.core.VideoPlayerView
import com.neilturner.aerialviews.ui.overlays.ClockOverlay
import com.neilturner.aerialviews.ui.screensaver.TestActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ToposcanPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun videoHistoryPhotosClockPauseAndNextCycle() {
        val video = File(context.cacheDir, "toposcan-test.mp4")
        instrumentation.context.assets
            .open("toposcan-test.mp4")
            .use { input -> video.outputStream().use(input::copyTo) }
        val photo = File(context.cacheDir, "toposcan-test.png")
        val bitmap = Bitmap.createBitmap(3840, 2160, Bitmap.Config.ARGB_8888)
        val row = IntArray(3840)
        for (y in 0 until bitmap.height) {
            for (x in row.indices) row[x] = Color.rgb(if (x % 2 == 0) 40 else 210, y * 255 / bitmap.height, 100)
            bitmap.setPixels(row, 0, row.size, 0, y, row.size, 1)
        }
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val server = ServerSocket(0)
        val feed = "file://${video.absolutePath},Test video\nfile://${photo.absolutePath},Test photo\n".toByteArray()
        thread(isDaemon = true) {
            try {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrBlank()) { /* Consume HTTP headers. */ }
                        socket.getOutputStream().apply {
                            write(
                                "HTTP/1.1 200 OK\r\nContent-Type: text/csv\r\nContent-Length: ${feed.size}\r\nConnection: close\r\n\r\n"
                                    .toByteArray(),
                            )
                            write(feed)
                            flush()
                        }
                    }
                }
            } catch (_: java.io.IOException) {
                // Server closes during cleanup.
            }
        }
        var activity: Activity? = null
        try {
            instrumentation.runOnMainSync {
                AppleVideoPrefs.enabled = false
                Comm1VideoPrefs.enabled = false
                Comm2VideoPrefs.enabled = false
                AmazonVideoPrefs.enabled = false
                CustomFeedPrefs.enabled = true
                CustomFeedPrefs.urlsCache = "http://127.0.0.1:${server.localPort}/test.csv"
                GeneralPrefs.shuffleVideos = false
                GeneralPrefs.playlistCache = false
                GeneralPrefs.toposcanEnabled = true
                GeneralPrefs.toposcanScan = "8"
                GeneralPrefs.toposcanFreezeDelay = "2"
                GeneralPrefs.toposcanHold = "0"
                GeneralPrefs.toposcanField = "1"
                GeneralPrefs.toposcanResolution = "3840"
                GeneralPrefs.toposcanDirection = "alternate"
            }
            activity = instrumentation.startActivitySync(Intent(context, TestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val root = activity.window.decorView
            lateinit var effect: ToposcanView
            instrumentation.runOnMainSync { effect = descendants(root).filterIsInstance<ToposcanView>().single() }
            await { effect.frameState.phase == ScanPhase.FIELD }
            // The phone emulator's UI is 960x540. Exercise a genuine 4K surface independently.
            instrumentation.runOnMainSync { effect.holder.setFixedSize(3840, 2160) }
            await { effect.renderSize == RenderSize(3840, 2160) }
            await { effect.frameState.let { it.video && it.phase == ScanPhase.REVEAL && it.time in 4.0..4.3 } }
            val a = pixels(effect)
            Thread.sleep(400)
            val b = pixels(effect)
            assertTrue("Frozen columns changed", difference(a, b, .10, .18) < 0.1)
            assertTrue("Live video stopped", difference(a, b, .40, .48) > 2.0)
            assertEquals(3840, a.width)
            assertEquals(2160, a.height)
            a.recycle()
            b.recycle()
            save("video-reveal", instrumentation.uiAutomation.takeScreenshot())
            assertTrue("Clock overlay missing", descendants(root).filterIsInstance<ClockOverlay>().any { it.isShown })

            lateinit var player: VideoPlayerView
            instrumentation.runOnMainSync {
                player = descendants(root).filterIsInstance<VideoPlayerView>().single()
                player.pause()
                effect.setPaused(true)
            }
            Thread.sleep(150)
            val paused = pixels(effect)
            val pausedTime = effect.frameState.time
            Thread.sleep(400)
            assertEquals(pausedTime, effect.frameState.time, 0.00001)
            val stillPaused = pixels(effect)
            assertTrue("Paused image changed", difference(paused, stillPaused, .1, .9) < .1)
            paused.recycle()
            stillPaused.recycle()
            instrumentation.runOnMainSync { effect.setBlackout(true) }
            Thread.sleep(300)
            val black = pixels(effect)
            assertTrue(
                "Effect remains visible in blackout",
                (0 until black.width step 32).all {
                    black.getPixel(it, black.height / 2) ==
                        Color.BLACK
                },
            )
            black.recycle()
            instrumentation.runOnMainSync {
                effect.setBlackout(false)
                player.resume()
                effect.setPaused(false)
            }
            await { effect.frameState.time > pausedTime }

            await(45_000) { effect.frameState.let { !it.video && it.phase == ScanPhase.REVEAL && it.time > 3 } }
            assertEquals(-1f, effect.frameState.direction)
            val image = pixels(effect)
            assertEquals(3840, image.width)
            val detail = (3000 until 3100).map { Color.red(image.getPixel(it, image.height / 2)) }
            assertTrue("4K one-pixel photo detail was lost", detail.zipWithNext().map { abs(it.first - it.second) }.average() > 100)
            image.recycle()
            save("photo-reveal", instrumentation.uiAutomation.takeScreenshot())
            await(35_000) { effect.frameState.let { it.video && it.phase == ScanPhase.REVEAL } }
            assertEquals(1f, effect.frameState.direction)
            assertTrue("Renderer disabled itself", GeneralPrefs.toposcanEnabled)

            instrumentation.runOnMainSync { effect.bypassUnsupportedContent() }
            await {
                var restored = false
                instrumentation.runOnMainSync {
                    restored = descendants(root).filterIsInstance<ToposcanView>().isEmpty() &&
                        descendants(root).filterIsInstance<VideoPlayerView>().single().videoSurfaceView is SurfaceView
                }
                restored
            }
            assertTrue("HDR bypass must not turn off the user's effect preference", GeneralPrefs.toposcanEnabled)
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() } }
            server.close()
        }
    }

    @Test
    fun boundedPreviewClosesWithoutChangingScreensaverSelection() {
        val before =
            android.provider.Settings.Secure
                .getString(context.contentResolver, "screensaver_components")
        val activity =
            instrumentation.startActivitySync(
                Intent(context, TestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(TestActivity.EXTRA_PREVIEW_TIMEOUT_SECONDS, 1),
            )
        try {
            await(10_000) { activity.isDestroyed }
            assertEquals(
                before,
                android.provider.Settings.Secure
                    .getString(context.contentResolver, "screensaver_components"),
            )
            assertFalse(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        } finally {
            instrumentation.runOnMainSync { if (!activity.isDestroyed) activity.finish() }
        }
    }

    private fun descendants(view: View): List<View> =
        listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun await(
        timeout: Long = 25_000,
        condition: () -> Boolean,
    ) {
        val until = System.currentTimeMillis() + timeout
        while (!condition() && System.currentTimeMillis() < until) Thread.sleep(20)
        assertTrue("Timed out waiting for scan phase", condition())
    }

    private fun pixels(view: ToposcanView): Bitmap {
        val size = view.holder.surfaceFrame
        val bitmap = Bitmap.createBitmap(size.width(), size.height(), Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var status = -1
        PixelCopy.request(view, bitmap, {
            status = it
            latch.countDown()
        }, Handler(Looper.getMainLooper()))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, status)
        return bitmap
    }

    private fun difference(
        a: Bitmap,
        b: Bitmap,
        left: Double,
        right: Double,
    ): Double {
        var sum = 0.0
        var count = 0
        for (y in a.height / 3 until a.height * 2 / 3 step 4) {
            for (x in (a.width * left).toInt() until (a.width * right).toInt() step 4) {
                sum += abs(Color.blue(a.getPixel(x, y)) - Color.blue(b.getPixel(x, y)))
                count++
            }
        }
        return sum / count
    }

    private fun save(
        name: String,
        bitmap: Bitmap,
    ) {
        val directory = File(context.getExternalFilesDir(null), "toposcan-tests").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
