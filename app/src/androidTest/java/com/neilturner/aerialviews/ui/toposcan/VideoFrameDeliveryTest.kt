package com.neilturner.aerialviews.ui.toposcan

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class VideoFrameDeliveryTest {
    @Test
    fun videoStartsWithoutDecoderOrSurfaceTextureFrameNotifications() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val video = File(context.cacheDir, "frame-delivery-test.mp4")
        instrumentation.context.assets
            .open("toposcan-test.mp4")
            .use { input -> video.outputStream().use(input::copyTo) }
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        lateinit var view: ToposcanView
        lateinit var player: ExoPlayer
        try {
            instrumentation.runOnMainSync {
                GeneralPrefs.toposcanField = "1"
                GeneralPrefs.toposcanScan = "8"
                GeneralPrefs.toposcanResolution = "1920"
                view = ToposcanView(activity)
                player = ExoPlayer.Builder(activity).build()
                // No Player.Listener: startup is driven exclusively by a frame latched in GL.
                view.onVideoFrameReady = {
                    val format = checkNotNull(player.videoFormat)
                    view.beginVideo(format.width.toFloat() / format.height, format.frameRate, format.colorInfo)
                }
                view.onSurfaceReady = { surface ->
                    view.queueEvent {
                        // Fault injection: simulate firmware that never sends frame-available callbacks.
                        val renderer =
                            ToposcanView::class.java
                                .getDeclaredField("renderer")
                                .apply { isAccessible = true }
                                .get(view)
                        val texture =
                            renderer.javaClass
                                .getDeclaredField("surfaceTexture")
                                .apply { isAccessible = true }
                                .get(renderer)
                        (texture as SurfaceTexture).setOnFrameAvailableListener(null)
                        view.post {
                            player.setVideoSurface(surface)
                            player.setMediaItem(MediaItem.fromUri(video.toURI().toString()))
                            player.prepare()
                            player.play()
                        }
                    }
                }
                view.awaitVideoFrame()
                activity.setContentView(view)
            }
            val deadline = System.currentTimeMillis() + 20_000
            while (System.currentTimeMillis() < deadline && view.frameState.let { it.phase != ScanPhase.REVEAL || it.time < 1 }) {
                Thread.sleep(25)
            }
            assertEquals(ScanPhase.REVEAL, view.frameState.phase)
            assertTrue(view.hasVideoFrame)
            assertTrue(GeneralPrefs.toposcanPlaybackStatus.contains("polling fallback"))
            assertTrue(GeneralPrefs.toposcanPlaybackStatus.contains("Callbacks: 0"))
            val bitmap = Bitmap.createBitmap(view.renderSize.width, view.renderSize.height, Bitmap.Config.ARGB_8888)
            val copied = CountDownLatch(1)
            var result = -1
            PixelCopy.request(view, bitmap, {
                result = it
                copied.countDown()
            }, Handler(Looper.getMainLooper()))
            assertTrue(copied.await(5, TimeUnit.SECONDS))
            assertEquals(PixelCopy.SUCCESS, result)
            assertTrue("Video remained black", Color.red(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 20)
            bitmap.recycle()
        } finally {
            instrumentation.runOnMainSync {
                player.release()
                view.release()
                activity.finish()
            }
        }
    }
}
