package com.neilturner.aerialviews.ui.toposcan

import android.opengl.EGL14
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGL10

object HdrStaticMetadata {
    /** Android CTA-861.3 type 1 to EGL_EXT_surface_* metadata, in EGL's 1/50000 units. */
    fun attributes(data: ByteArray?): Map<Int, Int> {
        if (data == null || data.size != 25 || data[0] != 0.toByte()) return emptyMap()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).apply { position(1) }
        val values = IntArray(12) { buffer.short.toInt() and 0xffff }
        val result = mutableMapOf<Int, Int>()
        if (values.take(8).all { it <= 50000 } && values.take(8).any { it != 0 }) {
            for (i in 0 until 8) result[0x3341 + i] = values[i]
        }
        if (values[8] in 1..10000) {
            result[0x3349] = values[8] * 50000
            result[0x334A] = values[9] * 5
        }
        if (values[10] in 1..10000) result[0x3360] = values[10] * 50000
        if (values[11] in 1..10000) result[0x3361] = values[11] * 50000
        return result
    }

    fun apply(data: ByteArray?) {
        val display = EGL14.eglGetCurrentDisplay()
        val surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val extensions =
            EGL14
                .eglQueryString(display, EGL14.EGL_EXTENSIONS)
                .orEmpty()
                .split(' ')
                .toSet()
        val attributes = attributes(data)
        val keys = mutableListOf<Int>()
        if ("EGL_EXT_surface_SMPTE2086_metadata" in extensions) keys += (0x3341..0x334A)
        if ("EGL_EXT_surface_CTA861_3_metadata" in extensions) keys += listOf(0x3360, 0x3361)
        for (key in keys) {
            // Clear absent values so the previous clip's metadata does not leak into the next.
            if (!EGL14.eglSurfaceAttrib(display, surface, key, attributes[key] ?: EGL10.EGL_DONT_CARE)) {
                Timber.w("HDR metadata attribute %x rejected: EGL %x", key, EGL14.eglGetError())
            }
        }
        if (keys.isEmpty()) Timber.i("HDR output uses BT.2020/PQ without optional EGL static metadata extensions")
    }
}
