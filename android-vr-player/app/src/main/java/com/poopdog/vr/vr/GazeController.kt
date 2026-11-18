package com.poopdog.vr.vr

/**
 * Dwell-to-click logic for hands-free control: look at a target for [dwellMs] and it fires.
 * Non-repeatable targets need the gaze to leave and come back before firing again, so a
 * "Next" button can't skip through the whole queue while you keep staring at it.
 */
class GazeController(var dwellMs: Long = 1500L) {
    private var target: Int = NONE
    private var since = 0L
    private var armed = true

    /** 0..1 fill of the dwell ring for the current target. */
    var progress: Float = 0f
        private set

    val currentTarget: Int get() = target

    /**
     * @param hit id under the reticle, or [NONE].
     * @return id that fired this frame, or null.
     */
    fun update(hit: Int, nowMs: Long, repeatable: (Int) -> Boolean = { false }): Int? {
        if (hit != target) {
            target = hit
            since = nowMs
            armed = true
            progress = 0f
            return null
        }
        if (target == NONE || !armed) {
            progress = 0f
            return null
        }
        progress = ((nowMs - since).toFloat() / dwellMs).coerceIn(0f, 1f)
        if (progress >= 1f) {
            progress = 0f
            if (repeatable(target)) since = nowMs else armed = false
            return target
        }
        return null
    }

    fun reset() {
        target = NONE
        armed = true
        progress = 0f
    }

    companion object {
        const val NONE = -1
    }
}
