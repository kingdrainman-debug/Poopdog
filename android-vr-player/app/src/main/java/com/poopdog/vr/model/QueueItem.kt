package com.poopdog.vr.model

import java.util.UUID

/**
 * One entry of the play queue.
 *
 * @param loops how many times the video plays before the queue moves on.
 *   1 = play once, N = play N times, [LOOP_FOREVER] (0) = repeat until skipped.
 * @param format forced VR format, or null to auto-detect.
 */
data class QueueItem(
    val uri: String,
    val title: String,
    val loops: Int = 1,
    val format: VideoFormat? = null,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val subtitleUri: String? = null,
    val id: String = UUID.randomUUID().toString(),
) {
    val loopsLabel: String get() = loopsLabel(loops)

    companion object {
        const val LOOP_FOREVER = 0
        const val MAX_LOOPS = 99

        fun loopsLabel(loops: Int): String = if (loops == LOOP_FOREVER) "∞" else "×$loops"

        /** Step a loop count up/down through 1..99 then ∞. */
        fun stepLoops(loops: Int, delta: Int): Int {
            if (delta > 0) {
                if (loops == LOOP_FOREVER) return LOOP_FOREVER
                return if (loops + delta > MAX_LOOPS) LOOP_FOREVER else loops + delta
            }
            if (delta < 0) {
                if (loops == LOOP_FOREVER) return MAX_LOOPS
                return (loops + delta).coerceAtLeast(1)
            }
            return loops
        }
    }
}

/** Estimated total play time of a queue, counting loops. Null when an item loops forever. */
fun List<QueueItem>.totalPlayTimeMs(): Long? {
    var total = 0L
    for (item in this) {
        if (item.loops == QueueItem.LOOP_FOREVER) return null
        total += item.durationMs * item.loops
    }
    return total
}
