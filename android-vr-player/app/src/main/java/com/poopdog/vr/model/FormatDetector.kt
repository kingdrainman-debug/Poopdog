package com.poopdog.vr.model

import kotlin.math.abs

/**
 * Guesses a video's VR format from its file name and dimensions, the way DeoVR / Skybox /
 * VaR's style players do. File-name tags win; container metadata (MP4 st3d/sv3d) refines;
 * aspect ratio is the last resort. The user can always override per video.
 *
 * Recognised tags (case-insensitive, any separator):
 *  - projection: `180`, `360`, `vr180`, `vr360`, `fisheye`, `fisheye190`, `fisheye200`,
 *    `mkx200`, `vrca220`, `rf52`, `flat`, `2d`
 *  - stereo: `lr`, `sbs`, `3dh`, `hsbs`, `fsbs`, `sidebyside`, `rl`, `tb`, `ou`, `3dv`,
 *    `htb`, `hou`, `ftb`, `overunder`, `topbottom`, `bt`, `mono`
 */
object FormatDetector {

    /** Stereo mode reported by container metadata (mirrors Media3 C.STEREO_MODE_*). */
    enum class MetaStereo { NONE, MONO, TOP_BOTTOM, LEFT_RIGHT }

    private val SBS_TAGS = setOf("lr", "sbs", "3dh", "hsbs", "fsbs", "sidebyside", "halfsbs", "fullsbs", "h-sbs", "f-sbs")
    private val RL_TAGS = setOf("rl")
    private val TB_TAGS = setOf("tb", "ou", "3dv", "htb", "hou", "ftb", "fou", "overunder", "topbottom", "halfou", "halftb", "fulltb", "fullou")
    private val BT_TAGS = setOf("bt")
    private val MONO_TAGS = setOf("mono", "2d", "flat")

    /** Lower-case alphanumeric tokens; file extensions are harmless extra tokens. */
    fun tokens(fileName: String): List<String> =
        fileName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    fun detect(
        fileName: String,
        width: Int = 0,
        height: Int = 0,
        meta: MetaStereo = MetaStereo.NONE,
        metaSpherical: Boolean = false,
    ): VideoFormat {
        val t = tokens(fileName)
        val joined = t.joinToString(" ")

        var projection: Projection? = when {
            t.any { it == "mkx200" || it == "vrca220" || it == "fisheye200" } -> Projection.FISHEYE_200
            t.any { it == "fisheye190" || it == "rf52" || it == "fisheye190lr" } -> Projection.FISHEYE_190
            t.any { it.startsWith("fisheye") } -> Projection.FISHEYE_180
            t.any { it == "360" || it == "vr360" || it == "360x180" || it == "360vr" || it == "equirect360" } -> Projection.EQUIRECT_360
            t.any { it == "180" || it == "vr180" || it == "180x180" || it == "180vr" || it == "equirect180" } -> Projection.EQUIRECT_180
            t.any { it == "flat" } -> Projection.FLAT
            else -> null
        }

        var layout: StereoLayout? = null
        var swap = false
        when {
            t.any { it in SBS_TAGS } || joined.contains("side by side") -> layout = StereoLayout.SBS
            t.any { it in RL_TAGS } -> { layout = StereoLayout.SBS; swap = true }
            t.any { it in TB_TAGS } || joined.contains("over under") || joined.contains("top bottom") -> layout = StereoLayout.TB
            t.any { it in BT_TAGS } -> { layout = StereoLayout.TB; swap = true }
            t.any { it in MONO_TAGS } -> layout = StereoLayout.MONO
        }
        // "3d" alone (e.g. "Movie.3D.mkv") usually means half side-by-side.
        if (layout == null && t.any { it == "3d" }) layout = StereoLayout.SBS

        // Container metadata.
        if (layout == null) {
            layout = when (meta) {
                MetaStereo.LEFT_RIGHT -> StereoLayout.SBS
                MetaStereo.TOP_BOTTOM -> StereoLayout.TB
                MetaStereo.MONO -> StereoLayout.MONO
                MetaStereo.NONE -> null
            }
        }

        val aspect = if (width > 0 && height > 0) width.toFloat() / height else 0f

        if (projection == null && metaSpherical) {
            projection = sphericalFromAspect(aspect, layout)
        }

        // Projection known, stereo unknown: 180/fisheye releases are almost always SBS.
        if (projection != null && projection != Projection.FLAT && layout == null) {
            layout = when (projection) {
                Projection.EQUIRECT_360 -> when {
                    aspect == 0f -> StereoLayout.MONO
                    near(aspect, 1f, 0.08f) -> StereoLayout.TB
                    near(aspect, 4f, 0.3f) -> StereoLayout.SBS
                    else -> StereoLayout.MONO
                }
                else -> when {
                    aspect == 0f -> StereoLayout.SBS
                    near(aspect, 1f, 0.08f) -> StereoLayout.MONO
                    near(aspect, 0.5f, 0.06f) -> StereoLayout.TB
                    else -> StereoLayout.SBS
                }
            }
        }

        // Stereo known, projection unknown: tagged stereo with no projection is usually flat 3D,
        // except DeoVR-style "_LR"/"_TB" on near-square-per-eye frames which are 180°.
        if (projection == null && layout != null) {
            projection = Projection.FLAT
            if (aspect > 0f && layout != StereoLayout.MONO) {
                val perEye = if (layout == StereoLayout.SBS) aspect / 2f else aspect * 2f
                val bareTag = t.any { it == "lr" || it == "tb" || it == "rl" || it == "bt" }
                if (bareTag && near(perEye, 1f, 0.08f) && maxOf(width, height) >= 2880) {
                    projection = Projection.EQUIRECT_180
                }
            }
        }

        // Nothing tagged: fall back to the frame shape.
        if (projection == null && layout == null) {
            return fromAspectOnly(width, height)
        }

        return VideoFormat(projection ?: Projection.FLAT, layout ?: StereoLayout.MONO, swapEyes = swap)
    }

    private fun sphericalFromAspect(aspect: Float, layout: StereoLayout?): Projection {
        if (aspect == 0f) return Projection.EQUIRECT_360
        val perEye = when (layout) {
            StereoLayout.SBS -> aspect / 2f
            StereoLayout.TB -> aspect * 2f
            else -> aspect
        }
        return if (near(perEye, 1f, 0.1f)) Projection.EQUIRECT_180 else Projection.EQUIRECT_360
    }

    /** Untagged files: only very distinctive shapes are treated as VR. */
    fun fromAspectOnly(width: Int, height: Int): VideoFormat {
        if (width <= 0 || height <= 0) return VideoFormat.FLAT_2D
        val aspect = width.toFloat() / height
        return when {
            // 2:1 high-res — typical 360° camera output (Insta360, GoPro Max, Theta).
            near(aspect, 2f, 0.03f) && width >= 2880 -> VideoFormat(Projection.EQUIRECT_360, StereoLayout.MONO)
            // 1:1 high-res — typical 360° top/bottom stereo.
            near(aspect, 1f, 0.02f) && width >= 2880 -> VideoFormat(Projection.EQUIRECT_360, StereoLayout.TB)
            // 32:9 — full side-by-side flat 3D.
            near(aspect, 32f / 9f, 0.06f) -> VideoFormat(Projection.FLAT, StereoLayout.SBS)
            else -> VideoFormat.FLAT_2D
        }
    }

    private fun near(v: Float, target: Float, tol: Float) = abs(v - target) <= tol
}
