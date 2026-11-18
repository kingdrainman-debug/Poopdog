package com.poopdog.vr.vr

/**
 * GLSL ES 1.00 sources. Kept free of Android types so they can be compile-checked outside
 * the device (the WebGL harness in tools/ swaps samplerExternalOES for sampler2D).
 */
object Shaders {
    private const val PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""

    const val MESH_VERTEX = """
uniform mat4 uMvp;
attribute vec4 aPosition;
attribute vec2 aTexCoord;
varying vec2 vUv;
void main() {
    gl_Position = uMvp * aPosition;
    vUv = aTexCoord;
}
"""

    /**
     * Video fragment shader body. `uEyeRect` (offset.xy, scale.zw) selects this eye's part of
     * a stereo frame; `uDepth` (signed per eye, 0 = off) synthesises parallax for 2D→3D
     * conversion using a lower-is-nearer + brighter-is-nearer depth heuristic, with the
     * brightness term blurred and the shift faded out at the frame edges.
     */
    private const val VIDEO_BODY = """
uniform mat4 uTexMatrix;
uniform vec4 uEyeRect;
uniform float uDepth;
uniform float uBrightness;
varying vec2 vUv;

vec2 frameUv(vec2 uv) {
    vec2 f = uEyeRect.xy + clamp(uv, 0.0, 1.0) * uEyeRect.zw;
    return (uTexMatrix * vec4(f, 0.0, 1.0)).xy;
}

float lumAt(vec2 uv) {
    return dot(SAMPLE(frameUv(uv)).rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 uv = vUv;
    if (uDepth != 0.0) {
        // Low-frequency brightness (4 wide taps) so fine detail doesn't ghost.
        float lum = 0.25 * (lumAt(uv + vec2(0.04, 0.04)) + lumAt(uv + vec2(-0.04, 0.04)) +
                            lumAt(uv + vec2(0.04, -0.04)) + lumAt(uv + vec2(-0.04, -0.04)));
        float depth = 0.7 * (1.0 - uv.y) + 0.3 * lum;
        float edge = smoothstep(0.0, 0.06, uv.x) * smoothstep(0.0, 0.06, 1.0 - uv.x);
        uv.x += uDepth * (depth - 0.5) * edge;
    }
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    } else {
        gl_FragColor = vec4(SAMPLE(frameUv(uv)).rgb * uBrightness, 1.0);
    }
}
"""

    const val VIDEO_FRAGMENT_OES = "#extension GL_OES_EGL_image_external : require\n" + PRECISION +
        "uniform samplerExternalOES uTex;\n#define SAMPLE(st) texture2D(uTex, st)\n" + VIDEO_BODY

    /** Same shader for a regular texture (used by the export pipeline and the test harness). */
    const val VIDEO_FRAGMENT_2D = PRECISION +
        "uniform sampler2D uTex;\n#define SAMPLE(st) texture2D(uTex, st)\n" + VIDEO_BODY

    /** Bitmap panels (HUD, subtitles). Bitmaps are uploaded top-row-first. */
    const val PANEL_FRAGMENT = PRECISION + """
uniform sampler2D uTex;
uniform float uAlpha;
varying vec2 vUv;
void main() {
    vec4 c = texture2D(uTex, vec2(vUv.x, 1.0 - vUv.y));
    gl_FragColor = vec4(c.rgb, c.a * uAlpha);
}
"""

    /** Gaze reticle: a dot plus a ring that fills clockwise with dwell progress. */
    const val RETICLE_FRAGMENT = PRECISION + """
uniform float uProgress;
varying vec2 vUv;
void main() {
    vec2 p = vUv * 2.0 - 1.0;
    float r = length(p);
    float dotMask = 1.0 - smoothstep(0.16, 0.22, r);
    float ringMask = smoothstep(0.55, 0.6, r) * (1.0 - smoothstep(0.85, 0.9, r));
    float ang = atan(p.x, p.y);
    if (ang < 0.0) ang += 6.2831853;
    float filled = step(ang, uProgress * 6.2831853) * step(0.001, uProgress);
    vec3 col = mix(vec3(1.0), vec3(1.0, 0.70, 0.28), filled);
    float a = max(dotMask, ringMask * (0.35 + 0.65 * filled));
    gl_FragColor = vec4(mix(vec3(1.0), col, ringMask), a * 0.95);
}
"""

    const val DISTORT_VERTEX = """
attribute vec2 aPosition;
attribute vec2 aTexCoord;
varying vec2 vUv;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vUv = aTexCoord;
}
"""

    /** Barrel pre-warp pass; fades to black at the edge of the rendered field of view. */
    const val DISTORT_FRAGMENT = PRECISION + """
uniform sampler2D uTex;
varying vec2 vUv;
void main() {
    float edge = min(min(vUv.x, 1.0 - vUv.x), min(vUv.y, 1.0 - vUv.y));
    if (edge <= 0.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    } else {
        float vig = clamp(edge / 0.025, 0.0, 1.0);
        gl_FragColor = vec4(texture2D(uTex, vUv).rgb * vig, 1.0);
    }
}
"""
}
