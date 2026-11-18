package com.poopdog.vr.convert

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.poopdog.vr.model.StereoLayout
import com.poopdog.vr.model.VideoFormat
import com.poopdog.vr.model.flatEyeAspect
import kotlin.math.max
import kotlin.math.roundToInt

enum class ConvertOutput(val label: String, val tag: String) {
    FULL_SBS("Full side-by-side (best quality, 2× wide)", "3D_SBS"),
    HALF_SBS("Half side-by-side (same size, most compatible)", "3D_HSBS"),
}

/** Output frame size for converting a flat video of [w]x[h] in [input] layout. */
fun convertOutputSize(w: Int, h: Int, input: VideoFormat, output: ConvertOutput, maxW: Int = 3840, maxH: Int = 2160): Pair<Int, Int> {
    // Un-squeezed size of one eye's picture: full-width, height from its display aspect.
    val eyeW = w
    val eyeH = when (input.layout) {
        StereoLayout.TB -> (w / flatEyeAspect(input, w.toFloat() / h)).roundToInt()
        else -> h
    }
    var outW = if (output == ConvertOutput.FULL_SBS) eyeW * 2 else eyeW
    var outH = eyeH
    val scale = minOf(1f, maxW.toFloat() / outW, maxH.toFloat() / outH)
    outW = (outW * scale).roundToInt()
    outH = (outH * scale).roundToInt()
    return Pair(max(2, outW - outW % 2), max(2, outH - outH % 2))
}

/**
 * Media3 GL effect that rewrites each frame as a side-by-side stereo frame:
 * 2D input is duplicated per eye (optionally with synthesised 2D→3D parallax),
 * top/bottom 3D input is re-packed as left|right.
 */
@OptIn(UnstableApi::class)
class SbsEffect(
    private val input: VideoFormat,
    private val output: ConvertOutput,
    /** 0..1 strength of the 2D→3D effect; 0 = plain duplicate. */
    private val depth: Float,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        SbsShaderProgram(useHdr, input, output, depth)
}

@OptIn(UnstableApi::class)
internal class SbsShaderProgram(
    useHdr: Boolean,
    private val input: VideoFormat,
    private val output: ConvertOutput,
    private val depth: Float,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program = GlProgram(VERTEX, FRAGMENT).apply {
        setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val (w, h) = convertOutputSize(inputWidth, inputHeight, input, output)
        return Size(w, h)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setFloatUniform("uDepth", if (input.layout == StereoLayout.MONO) 0.035f * depth else 0f)
        program.setFloatUniform("uInputTb", if (input.layout == StereoLayout.TB) 1f else 0f)
        program.setFloatUniform("uSwap", if (input.swapEyes) 1f else 0f)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        program.delete()
    }

    companion object {
        const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
    gl_Position = aFramePosition;
    vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        const val FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform float uDepth;
uniform float uInputTb;
uniform float uSwap;
varying vec2 vTexSamplingCoord;
void main() {
    vec2 p = vTexSamplingCoord;
    float right = step(0.5, p.x);
    vec2 uv = vec2(clamp(p.x * 2.0 - right, 0.0, 1.0), p.y);
    float srcEye = abs(right - uSwap);
    if (uInputTb > 0.5) {
        float y = srcEye < 0.5 ? 0.5 + uv.y * 0.5 : uv.y * 0.5;
        gl_FragColor = texture2D(uTexSampler, vec2(uv.x, y));
    } else {
        if (uDepth != 0.0) {
            vec3 w = vec3(0.299, 0.587, 0.114);
            float lum = 0.25 * (dot(texture2D(uTexSampler, clamp(uv + vec2(0.04, 0.04), 0.0, 1.0)).rgb, w) +
                                dot(texture2D(uTexSampler, clamp(uv + vec2(-0.04, 0.04), 0.0, 1.0)).rgb, w) +
                                dot(texture2D(uTexSampler, clamp(uv + vec2(0.04, -0.04), 0.0, 1.0)).rgb, w) +
                                dot(texture2D(uTexSampler, clamp(uv + vec2(-0.04, -0.04), 0.0, 1.0)).rgb, w));
            float d = 0.7 * (1.0 - uv.y) + 0.3 * lum;
            float edge = smoothstep(0.0, 0.06, uv.x) * smoothstep(0.0, 0.06, 1.0 - uv.x);
            float dir = right < 0.5 ? -1.0 : 1.0;
            uv.x = clamp(uv.x + dir * uDepth * (d - 0.5) * edge, 0.0, 1.0);
        }
        gl_FragColor = texture2D(uTexSampler, uv);
    }
}
"""
    }
}
