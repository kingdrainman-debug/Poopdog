package com.gripcam.camera

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

/** Zoom stops (the 1× / 3× / 6× buttons and grip double-press targets). Pure functions. */
object ZoomMath {
    /** Two ratios closer than this are treated as the same stop. */
    private const val SAME_STOP = 1.03f

    /**
     * The stops for a camera: the user's [preset] clamped into [minZoom]..[maxZoom], plus the
     * ultra-wide (the camera's minimum) when it goes below 1×. Sorted, near-duplicates removed.
     */
    fun stops(preset: List<Float>, minZoom: Float, maxZoom: Float): List<Float> {
        if (maxZoom <= 0f || minZoom <= 0f || maxZoom < minZoom) return listOf(1f)
        val raw = buildList {
            if (minZoom < 0.95f) add(minZoom)
            preset.forEach { add(it.coerceIn(minZoom, maxZoom)) }
        }.sorted()
        val out = mutableListOf<Float>()
        for (v in raw) if (out.isEmpty() || v > out.last() * SAME_STOP) out += v
        return out
    }

    /** The next stop beyond [from] in [direction] (+1 tele, -1 wide), or null at the end. */
    fun next(from: Float, stops: List<Float>, direction: Int): Float? =
        if (direction > 0) stops.firstOrNull { it > from * SAME_STOP } else stops.lastOrNull { it < from / SAME_STOP }

    /** Next stop up, wrapping to the widest after the longest (for a single "cycle zoom" button). */
    fun cycle(from: Float, stops: List<Float>): Float? = next(from, stops, 1) ?: stops.firstOrNull()

    /** Index of the stop closest to [ratio] on a log scale, or -1 if none is within ~15%. */
    fun nearest(ratio: Float, stops: List<Float>): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        stops.forEachIndexed { i, s ->
            val d = abs(ln(ratio / s))
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return if (bestDistance <= 0.14f) best else -1
    }

    /** "0.6", "1", "2.5", "10". */
    fun label(ratio: Float): String =
        if (ratio >= 1f && abs(ratio - Math.round(ratio)) < 0.05f) {
            "${Math.round(ratio)}"
        } else {
            String.format(Locale.US, "%.1f", ratio)
        }
}
