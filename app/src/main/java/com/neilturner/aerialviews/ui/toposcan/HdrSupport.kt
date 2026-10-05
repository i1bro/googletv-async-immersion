package com.neilturner.aerialviews.ui.toposcan

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import com.neilturner.aerialviews.models.prefs.GeneralPrefs

data class HdrSupport(
    val output: HdrOutput?,
    val reason: String,
) {
    val available: Boolean get() = output != null

    companion object {
        @UnstableApi
        fun inspect(
            context: Context,
            preference: String = GeneralPrefs.toposcanHdrOutput,
        ): HdrSupport {
            if (Build.VERSION.SDK_INT < 31) return evaluate(Build.VERSION.SDK_INT, false, false, false)
            val display = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val types = display?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
            return evaluate(
                Build.VERSION.SDK_INT,
                Display.HdrCapabilities.HDR_TYPE_HDR10 in types,
                activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000,
                runCatching { GlUtil.isBt2020PqExtensionSupported() }.getOrDefault(false),
                Display.HdrCapabilities.HDR_TYPE_HLG in types,
                runCatching { GlUtil.isBt2020HlgExtensionSupported() }.getOrDefault(false),
                preference,
            )
        }

        fun evaluate(
            api: Int,
            displayHdr10: Boolean,
            gles3: Boolean,
            pqSurface: Boolean,
            displayHlg: Boolean = false,
            hlgSurface: Boolean = false,
            preference: String = "auto",
        ): HdrSupport {
            if (api < 31) return HdrSupport(null, "HDR effect requires Android 12 / API 31 or newer")
            if (!gles3) return HdrSupport(null, "OpenGL ES 3.0 is unavailable")
            val pqReason =
                when {
                    api < 33 -> "PQ output requires Android 13+"
                    !displayHdr10 -> "Display does not report HDR10"
                    !pqSurface -> "EGL BT.2020/PQ output is unavailable"
                    else -> null
                }
            val hlgReason =
                when {
                    !displayHlg -> "Display does not report HLG"
                    !hlgSurface -> "EGL BT.2020/HLG output is unavailable"
                    else -> null
                }
            val output =
                when (preference) {
                    "hdr10" -> HdrOutput.HDR10.takeIf { pqReason == null }
                    "hlg" -> HdrOutput.HLG.takeIf { hlgReason == null }
                    else -> HdrOutput.HDR10.takeIf { pqReason == null } ?: HdrOutput.HLG.takeIf { hlgReason == null }
                }
            val reason =
                when {
                    output != null -> "${output.label} available; decoder/GPU checks run at playback"
                    preference == "hdr10" -> pqReason.orEmpty()
                    preference == "hlg" -> hlgReason.orEmpty()
                    else -> "$pqReason; $hlgReason"
                }
            return HdrSupport(output, reason)
        }
    }
}
