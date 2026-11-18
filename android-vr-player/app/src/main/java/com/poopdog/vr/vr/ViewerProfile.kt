package com.poopdog.vr.vr

import kotlin.math.tan

/**
 * Optical description of a phone VR viewer, in the same terms as Google Cardboard viewer
 * profiles (lens spacing, screen-to-lens distance, radial distortion k1/k2, field of view).
 *
 * Distortion model (Cardboard's polynomial radial model, in tan-angle units):
 * a pixel at tan-angle r from the lens centre is seen at r·(1 + k1·r² + k2·r⁴).
 * To cancel the pin-cushion of the lens we pre-warp each eye image with that function.
 */
data class ViewerProfile(
    val interLensMm: Float,
    val screenToLensMm: Float,
    val k1: Float,
    val k2: Float,
    /** Half-angle of the per-eye render frustum, degrees. */
    val fovHalfDeg: Float,
    /** Lens centre offset from the screen's vertical middle, mm (+ = up). */
    val verticalOffsetMm: Float = 0f,
) {
    fun distortFactor(r2: Float): Float = 1f + k1 * r2 + k2 * r2 * r2

    val tanHalfFov: Float get() = tan(Math.toRadians(fovHalfDeg.toDouble())).toFloat()

    companion object {
        /** Google Cardboard 2015 parameters — matches most cheap viewers reasonably well. */
        val DEFAULT = ViewerProfile(64f, 39.3f, 0.34f, 0.55f, 60f)
    }

    enum class Preset(val label: String, val profile: ViewerProfile?) {
        CARDBOARD_V2("Google Cardboard (2015+) / most viewers", DEFAULT),
        CARDBOARD_V1("Google Cardboard (2014 original)", ViewerProfile(60f, 42f, 0.441f, 0.156f, 40f)),
        PLASTIC_HEADSET("Plastic headset (VR Box style, adjustable lenses)", ViewerProfile(63f, 42f, 0.22f, 0.24f, 50f)),
        WIDE_FOV("Wide-FOV headset (large lenses)", ViewerProfile(64f, 37f, 0.40f, 0.70f, 65f)),
        NO_LENSES("No lens correction", ViewerProfile(63f, 40f, 0f, 0f, 45f)),
        CUSTOM("Custom", null);

        companion object {
            fun byName(name: String?): Preset = entries.firstOrNull { it.name == name } ?: CARDBOARD_V2
        }
    }
}

/** Physical screen description needed to place the lens centres on the panel. */
data class ScreenGeometry(val widthPx: Int, val heightPx: Int, val widthM: Float, val heightM: Float) {
    companion object {
        /** Build from DisplayMetrics-like values, guarding against bogus xdpi/ydpi reports. */
        fun fromDpi(widthPx: Int, heightPx: Int, xdpi: Float, ydpi: Float, densityDpi: Int): ScreenGeometry {
            fun sane(dpi: Float) = if (dpi < 50f || dpi > 1200f ||
                kotlin.math.abs(dpi - densityDpi) / densityDpi > 0.35f) densityDpi.toFloat() else dpi
            val xd = sane(xdpi)
            val yd = sane(ydpi)
            return ScreenGeometry(widthPx, heightPx, widthPx / xd * 0.0254f, heightPx / yd * 0.0254f)
        }
    }
}

/**
 * Mesh that maps each eye's half of the screen to texture coordinates in that eye's
 * off-screen render (a square texture spanning ±tanHalfFov in tan-angle space).
 */
object DistortionMesh {
    const val GRID = 40

    class Mesh(
        /** Interleaved x, y (viewport NDC), u, v (eye texture) per vertex. */
        val vertices: FloatArray,
        val indices: ShortArray,
    )

    /**
     * @param rightEye build for the right half of the screen.
     * @param correct when false only the lens-centred projection is applied (no warp).
     */
    fun build(profile: ViewerProfile, screen: ScreenGeometry, rightEye: Boolean, correct: Boolean = true): Mesh {
        val n = GRID
        val verts = FloatArray((n + 1) * (n + 1) * 4)
        val halfW = screen.widthM / 2f
        val ipd = profile.interLensMm / 1000f
        val s2l = profile.screenToLensMm / 1000f
        val lensX = if (rightEye) halfW + ipd / 2f else halfW - ipd / 2f
        val lensY = screen.heightM / 2f + profile.verticalOffsetMm / 1000f
        val vpX0 = if (rightEye) halfW else 0f
        val t = profile.tanHalfFov
        var k = 0
        for (j in 0..n) {
            val fy = j.toFloat() / n
            for (i in 0..n) {
                val fx = i.toFloat() / n
                val xm = vpX0 + fx * halfW - lensX
                val ym = fy * screen.heightM - lensY
                val tx = xm / s2l
                val ty = ym / s2l
                val d = if (correct) profile.distortFactor(tx * tx + ty * ty) else 1f
                verts[k++] = fx * 2f - 1f
                verts[k++] = fy * 2f - 1f
                verts[k++] = (tx * d / t + 1f) / 2f
                verts[k++] = (ty * d / t + 1f) / 2f
            }
        }
        val idx = ShortArray(n * n * 6)
        var m = 0
        for (j in 0 until n) for (i in 0 until n) {
            val a = (j * (n + 1) + i).toShort()
            val b = (j * (n + 1) + i + 1).toShort()
            val c = ((j + 1) * (n + 1) + i).toShort()
            val e = ((j + 1) * (n + 1) + i + 1).toShort()
            idx[m++] = a; idx[m++] = b; idx[m++] = c
            idx[m++] = b; idx[m++] = e; idx[m++] = c
        }
        return Mesh(verts, idx)
    }
}
