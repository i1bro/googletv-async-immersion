package com.neilturner.aerialviews.ui.toposcan

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.Matrix
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.DefaultShaderProgram
import androidx.media3.effect.DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_LINEAR

@UnstableApi
// Media3's colour shaders are isolated here and pinned to 1.11.1; they are library-group APIs.
@SuppressLint("RestrictedApi")
class HdrFramePipeline(
    private val context: Context,
) {
    private var input: DefaultShaderProgram? = null
    private var inputColour: ColorInfo? = null
    private val output =
        DefaultShaderProgram.createApplyingOetf(
            context,
            emptyList(),
            emptyList(),
            ColorInfo
                .Builder()
                .setColorSpace(C.COLOR_SPACE_BT2020)
                .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                .setColorRange(C.COLOR_RANGE_FULL)
                .build(),
            WORKING_COLOR_SPACE_LINEAR,
        )
    private val crop = FloatArray(16)
    private val sampling = FloatArray(16)

    fun configureInput(
        colour: ColorInfo,
        width: Int,
        height: Int,
    ) {
        check(GlUtil.isYuvTargetExtensionSupported()) { "GL_EXT_YUV_target is required for HDR decoder frames" }
        if (inputColour != colour) {
            input?.release()
            input =
                DefaultShaderProgram.createWithExternalSampler(
                    context,
                    colour,
                    colour
                        .buildUpon()
                        .setColorTransfer(C.COLOR_TRANSFER_LINEAR)
                        .setColorRange(C.COLOR_RANGE_FULL)
                        .build(),
                    WORKING_COLOR_SPACE_LINEAR,
                    false,
                )
            inputColour = colour
            HdrStaticMetadata.apply(colour.hdrStaticInfo)
        }
        input?.configure(width, height)
        configureOutput(width, height)
    }

    fun copyVideo(
        texture: Int,
        transform: FloatArray,
        width: Int,
        height: Int,
        sourceAspect: Float,
    ) {
        val aspect = width.toFloat() / height
        val x = minOf(aspect / sourceAspect, 1f)
        val y = minOf(sourceAspect / aspect, 1f)
        Matrix.setIdentityM(crop, 0)
        Matrix.translateM(crop, 0, (1f - x) / 2, (1f - y) / 2, 0f)
        Matrix.scaleM(crop, 0, x, y, 1f)
        Matrix.multiplyMM(sampling, 0, transform, 0, crop, 0)
        checkNotNull(input).apply {
            setTextureTransformMatrix(sampling)
            drawFrame(texture, 0)
        }
    }

    fun present(linearTexture: Int) {
        output.drawFrame(linearTexture, 0)
    }

    internal fun configureOutput(
        width: Int,
        height: Int,
    ) {
        output.configure(width, height)
    }

    fun release() {
        input?.release()
        output.release()
    }
}
