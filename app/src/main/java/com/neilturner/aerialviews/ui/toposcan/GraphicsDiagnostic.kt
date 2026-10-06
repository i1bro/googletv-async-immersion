package com.neilturner.aerialviews.ui.toposcan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.GLES20
import java.nio.ByteBuffer
import kotlin.math.abs

/** Opt-in, one-shot readback. Never used during ordinary screensaver playback. */
class GraphicsDiagnostic(
    val size: RenderSize,
    val input: Input,
    val onResult: (Result) -> Unit,
) {
    data class Result(
        val detail: String,
        val gpu: String = "",
        val output: String = "",
    )

    enum class Input {
        DIRECT,
        IMAGE,
        EXTERNAL,
    }

    fun bitmap(): Bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).also { paint(Canvas(it)) }

    fun paint(canvas: Canvas) {
        val paint = Paint()
        colours.forEachIndexed { index, colour ->
            paint.color = colour
            canvas.drawRect(index * canvas.width / 4f, 0f, (index + 1) * canvas.width / 4f, canvas.height.toFloat(), paint)
        }
    }

    fun clearPattern(tile: OutputTile = OutputTile(0, 0, size.width, size.height)) {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        colours.forEachIndexed { index, colour ->
            val left = maxOf(index * size.width / 4, tile.left)
            val right = minOf((index + 1) * size.width / 4, tile.left + tile.width)
            if (right <= left) return@forEachIndexed
            GLES20.glScissor(left - tile.left, 0, right - left, tile.height)
            GLES20.glClearColor(channel(colour, 16) / 255f, channel(colour, 8) / 255f, channel(colour, 0) / 255f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    /** Read all four bars at three heights, including the part beyond a 1080p viewport. */
    fun probe(tile: OutputTile = OutputTile(0, 0, size.width, size.height)): String {
        val pixel = ByteBuffer.allocateDirect(4)
        val samples = mutableListOf<Int>()
        for (row in 0..2) {
            for (column in 0..3) {
                pixel.clear()
                GLES20.glReadPixels(
                    (2 * column + 1) * tile.width / 8,
                    (2 * row + 1) * tile.height / 6,
                    1,
                    1,
                    GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE,
                    pixel,
                )
                samples += (pixel.get(0).toInt() and 255 shl 16) or
                    (pixel.get(1).toInt() and 255 shl 8) or (pixel.get(2).toInt() and 255)
            }
        }
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) return "GL error 0x${error.toString(16)}"
        val bad =
            samples.withIndex().firstOrNull { (index, sample) ->
                val x = tile.left + (2 * (index % 4) + 1) * tile.width / 8
                !matchesColour(sample, colours[(x * 4 / size.width).coerceIn(0, 3)])
            }
        return if (bad == null) {
            "OK"
        } else {
            "FAIL row ${bad.index / 4 + 1}, bar ${bad.index % 4 + 1}: ${bad.value.toString(16).padStart(6, '0')}"
        }
    }

    companion object {
        private val colours = intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xffffffff.toInt())

        internal fun matches(samples: List<Int>): Boolean =
            samples.size == 12 &&
                samples.withIndex().all { (index, sample) ->
                    matchesColour(sample, colours[index % 4])
                }

        internal fun matches(bitmap: Bitmap): Boolean =
            matches(
                (0..2).flatMap { row ->
                    (0..3).map { column ->
                        bitmap.getPixel((2 * column + 1) * bitmap.width / 8, (2 * row + 1) * bitmap.height / 6)
                    }
                },
            )

        private fun matchesColour(
            sample: Int,
            expected: Int,
        ): Boolean = listOf(16, 8, 0).all { shift -> abs(channel(sample, shift) - channel(expected, shift)) <= 12 }

        private fun channel(
            colour: Int,
            shift: Int,
        ): Int = (colour ushr shift) and 255
    }
}
