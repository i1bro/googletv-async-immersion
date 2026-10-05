package com.neilturner.aerialviews.ui.toposcan

import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi

/** Converts Media3's display-linear PQ working light (1 = 1000 nits) to HLG. */
@UnstableApi
internal class HlgOutputShader {
    private val program =
        GlProgram(VERTEX, FRAGMENT).apply {
            setBufferAttribute("aPosition", floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f), 2)
        }

    fun draw(texture: Int) {
        program.use()
        program.setSamplerTexIdUniform("uTexture", texture, 0)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    fun release() = program.delete()

    companion object {
        private const val VERTEX = """#version 300 es
            in vec2 aPosition;
            out vec2 vUV;
            void main() {
                vUV = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        // BT.2100 HLG, 1000-nit reference display (system gamma 1.2).
        // A hue-preserving shoulder above 500 nits avoids clipping PQ highlights at 1000 nits.
        private const val FRAGMENT = """#version 300 es
            precision highp float;
            uniform highp sampler2D uTexture;
            in vec2 vUV;
            out vec4 outColor;
            float hlg(float x) {
                return x <= 1.0 / 12.0 ? sqrt(3.0 * x)
                    : 0.17883277 * log(12.0 * x - 0.28466892) + 0.55991073;
            }
            void main() {
                vec3 rgb = max(texture(uTexture, vUV).rgb, vec3(0.0));
                float peak = max(max(rgb.r, rgb.g), rgb.b);
                if (peak > 0.5) {
                    float mapped = 0.5 + 0.5 * (peak - 0.5) / peak;
                    rgb *= mapped / peak;
                }
                float y = dot(rgb, vec3(0.2627, 0.6780, 0.0593));
                vec3 scene = y > 0.0 ? rgb * pow(y, 1.0 / 1.2 - 1.0) : vec3(0.0);
                // Very saturated colours can exceed the HLG cube after inverse OOTF.
                // Compress towards equal-luminance grey, preserving luminance and hue direction.
                float scenePeak = max(max(scene.r, scene.g), scene.b);
                if (scenePeak > 1.0) {
                    float grey = pow(y, 1.0 / 1.2);
                    scene = mix(vec3(grey), scene, (1.0 - grey) / (scenePeak - grey));
                }
                outColor = vec4(hlg(scene.r), hlg(scene.g), hlg(scene.b), 1.0);
            }
        """
    }
}
