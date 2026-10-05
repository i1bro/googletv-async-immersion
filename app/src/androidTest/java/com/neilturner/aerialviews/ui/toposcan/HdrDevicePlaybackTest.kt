package com.neilturner.aerialviews.ui.toposcan

import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Run on the target HDR device. An SDR emulator skips, never pretends to verify HDR presentation. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class HdrDevicePlaybackTest {
    private lateinit var view: ToposcanView
    private lateinit var player: ExoPlayer

    @Test
    fun decodes4kHdr10IntoTheHdrEffectSurface() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val support = HdrSupport.inspect(context)
        assumeTrue(support.reason, support.available)
        val video = File(context.cacheDir, "toposcan-hdr10-test.mp4")
        instrumentation.context.assets
            .open(video.name)
            .use { input -> video.outputStream().use(input::copyTo) }
        instrumentation.runOnMainSync {
            GeneralPrefs.startScreensaverOnLaunch = false
            GeneralPrefs.toposcanEnabled = true
            GeneralPrefs.toposcanHdrEnabled = true
            GeneralPrefs.toposcanResolution = "3840"
            GeneralPrefs.toposcanScan = "8"
            GeneralPrefs.toposcanFreezeDelay = "2"
            GeneralPrefs.toposcanField = "1"
        }
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val error = AtomicReference<String?>()
        try {
            instrumentation.runOnMainSync {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                player = ExoPlayer.Builder(context).build()
                view = ToposcanView(activity, hdrMode = true)
                view.onFailure = { error.set("Graphics failure") }
                view.onUnsupportedContent = { error.set(it) }
                view.onSurfaceReady = {
                    player.setVideoSurface(it)
                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    player.volume = 0f
                    player.prepare()
                    player.play()
                }
                player.addListener(
                    object : Player.Listener {
                        private var started = false

                        override fun onRenderedFirstFrame() {
                            if (started) return
                            started = true
                            val format = checkNotNull(player.videoFormat)
                            if (!view.acceptsVideo(format)) {
                                error.set("HDR fixture rejected")
                            } else {
                                view.beginVideo(format.width.toFloat() / format.height, format.frameRate, format.colorInfo)
                            }
                        }

                        override fun onPlayerError(exception: PlaybackException) {
                            error.set(exception.toString())
                        }
                    },
                )
                activity.setContentView(view)
            }
            val deadline = System.currentTimeMillis() + 60_000

            fun revealed() = view.frameState.let { it.video && it.phase == ScanPhase.REVEAL && it.time >= 4 }
            while (error.get() == null && !revealed() && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertTrue(error.get() ?: "HDR decoder/effect did not advance", error.get() == null && revealed())
            assertTrue(GeneralPrefs.toposcanHdrStatus.contains("RGB10_A2"))
            assertTrue(GeneralPrefs.toposcanHdrStatus.contains(checkNotNull(support.output).label))
        } finally {
            instrumentation.runOnMainSync {
                if (::player.isInitialized) player.release()
                if (::view.isInitialized) view.release()
                activity.finish()
            }
        }
    }
}
