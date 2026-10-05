package com.neilturner.aerialviews.ui.toposcan

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi

data class HdrSupport(
    val available: Boolean,
    val reason: String,
) {
    companion object {
        @UnstableApi
        fun inspect(context: Context): HdrSupport {
            if (Build.VERSION.SDK_INT < 33) return evaluate(Build.VERSION.SDK_INT, false, false, false)
            val display = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return evaluate(
                Build.VERSION.SDK_INT,
                display?.hdrCapabilities?.supportedHdrTypes?.contains(Display.HdrCapabilities.HDR_TYPE_HDR10) == true,
                activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000,
                runCatching { GlUtil.isBt2020PqExtensionSupported() }.getOrDefault(false),
            )
        }

        fun evaluate(
            api: Int,
            displayHdr10: Boolean,
            gles3: Boolean,
            pqSurface: Boolean,
        ): HdrSupport =
            when {
                api < 33 -> HdrSupport(false, "HDR10 effect requires Android 13 / API 33 or newer")
                !displayHdr10 -> HdrSupport(false, "Active display does not report HDR10 support")
                !gles3 -> HdrSupport(false, "OpenGL ES 3.0 is unavailable")
                !pqSurface -> HdrSupport(false, "EGL BT.2020/PQ output is unavailable")
                else -> HdrSupport(true, "HDR10 display output available; GPU checks run at playback")
            }
    }
}
