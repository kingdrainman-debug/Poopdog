package com.poopdog.vr.model

/** How the picture is wrapped around the viewer. */
enum class Projection(val label: String, val spherical: Boolean, val fovDeg: Float) {
    FLAT("Flat", false, 0f),
    EQUIRECT_180("180°", true, 180f),
    EQUIRECT_360("360°", true, 360f),
    FISHEYE_180("Fisheye 180°", true, 180f),
    FISHEYE_190("Fisheye 190°", true, 190f),
    FISHEYE_200("Fisheye 200°", true, 200f);

    val isFisheye: Boolean get() = this == FISHEYE_180 || this == FISHEYE_190 || this == FISHEYE_200
}

/** How the two eye views are packed into one video frame. */
enum class StereoLayout(val label: String) {
    MONO("2D"),
    SBS("3D Side-by-side"),
    TB("3D Top/Bottom"),
}

/**
 * Full description of how a source video must be converted for display in a phone headset.
 *
 * @param swapEyes the frame stores the right eye first (RL / BT files).
 * @param depth3d synthesise depth for flat 2D video (2D→3D conversion).
 */
data class VideoFormat(
    val projection: Projection,
    val layout: StereoLayout,
    val swapEyes: Boolean = false,
    val depth3d: Boolean = false,
) {
    val isStereo: Boolean get() = layout != StereoLayout.MONO

    val shortLabel: String
        get() = buildString {
            when {
                depth3d && layout == StereoLayout.MONO -> append("2D→3D")
                layout == StereoLayout.MONO -> append("2D")
                layout == StereoLayout.SBS -> append(if (swapEyes) "3D RL" else "3D SBS")
                else -> append(if (swapEyes) "3D BT" else "3D TB")
            }
            if (projection != Projection.FLAT) append(" · ").append(projection.label)
        }

    /** Stable string used for persistence. */
    fun key(): String = "${projection.name}|${layout.name}|${if (swapEyes) 1 else 0}|${if (depth3d) 1 else 0}"

    companion object {
        val FLAT_2D = VideoFormat(Projection.FLAT, StereoLayout.MONO)

        fun fromKey(key: String?): VideoFormat? {
            if (key.isNullOrBlank()) return null
            val p = key.split('|')
            if (p.size < 2) return null
            return try {
                VideoFormat(
                    Projection.valueOf(p[0]),
                    StereoLayout.valueOf(p[1]),
                    swapEyes = p.getOrNull(2) == "1",
                    depth3d = p.getOrNull(3) == "1",
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /** The order the in-headset "Format" button cycles through. */
        val CYCLE: List<VideoFormat> = listOf(
            VideoFormat(Projection.FLAT, StereoLayout.MONO),
            VideoFormat(Projection.FLAT, StereoLayout.MONO, depth3d = true),
            VideoFormat(Projection.FLAT, StereoLayout.SBS),
            VideoFormat(Projection.FLAT, StereoLayout.TB),
            VideoFormat(Projection.EQUIRECT_180, StereoLayout.SBS),
            VideoFormat(Projection.EQUIRECT_180, StereoLayout.TB),
            VideoFormat(Projection.EQUIRECT_180, StereoLayout.MONO),
            VideoFormat(Projection.EQUIRECT_360, StereoLayout.MONO),
            VideoFormat(Projection.EQUIRECT_360, StereoLayout.TB),
            VideoFormat(Projection.EQUIRECT_360, StereoLayout.SBS),
            VideoFormat(Projection.FISHEYE_190, StereoLayout.SBS),
            VideoFormat(Projection.FISHEYE_180, StereoLayout.SBS),
            VideoFormat(Projection.FISHEYE_200, StereoLayout.SBS),
        )

        fun nextInCycle(current: VideoFormat): VideoFormat {
            val base = current.copy(swapEyes = false)
            val i = CYCLE.indexOf(base)
            return CYCLE[(i + 1).mod(CYCLE.size)]
        }
    }
}

/** Optional forced aspect ratio for flat video. */
enum class AspectMode(val label: String, val ratio: Float) {
    AUTO("Auto", 0f),
    R16_9("16:9", 16f / 9f),
    R4_3("4:3", 4f / 3f),
    R21_9("21:9", 21f / 9f),
    R1_1("1:1", 1f),
}

/** Per-eye display aspect ratio for a flat video, compensating for half-SBS/half-TB squeeze. */
fun flatEyeAspect(format: VideoFormat, frameAspect: Float, mode: AspectMode = AspectMode.AUTO): Float {
    if (mode != AspectMode.AUTO) return mode.ratio
    if (frameAspect <= 0f) return 16f / 9f
    return when (format.layout) {
        StereoLayout.MONO -> frameAspect
        // Full SBS (e.g. 3840x1080) halves to 16:9; half SBS (1920x1080) is squeezed 16:9.
        StereoLayout.SBS -> {
            val perEye = frameAspect / 2f
            if (perEye < 1.2f) frameAspect else perEye
        }
        // Full TB (1920x2160) doubles to 16:9; half TB (1920x1080) is squeezed 16:9.
        StereoLayout.TB -> {
            val perEye = frameAspect * 2f
            if (perEye > 2.5f) frameAspect else perEye
        }
    }
}
