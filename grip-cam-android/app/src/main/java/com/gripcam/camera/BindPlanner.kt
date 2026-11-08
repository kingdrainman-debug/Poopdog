package com.gripcam.camera

import com.gripcam.settings.PhotoEnhancement
import com.gripcam.settings.Stabilization

/** Which use cases are bound together. */
enum class Layout(val hasPhoto: Boolean, val hasVideo: Boolean) {
    /** Preview + photo + video: PHOTO and REC both work instantly, stills during video. */
    COMBINED(true, true),
    PHOTO(true, false),
    VIDEO(false, true),
}

/** The features a binding asks the camera for. */
data class Features(
    val stabilization: Stabilization = Stabilization.OFF,
    val hdrVideo: Boolean = false,
    /** Fixed session frame rate, or null for the camera's default. */
    val fps: Int? = null,
    val ultraHdr: Boolean = false,
    val raw: Boolean = false,
    val enhancement: PhotoEnhancement = PhotoEnhancement.NONE,
)

data class BindCandidate(val layout: Layout, val features: Features)

/**
 * Orders what to try when binding the camera. Phones differ wildly in what they can combine
 * (4K60 + HDR + stabilization + photo), so the controller walks this list, checks each with
 * `CameraInfo.isSessionConfigSupported`, and binds the first that fits.
 *
 * Priorities: features the user picked beat the convenience of a combined photo+video binding,
 * and features are given up least-important first. Pure logic, unit tested.
 */
object BindPlanner {

    /**
     * @param mode whether the user is about to take photos or record ([Layout.PHOTO] / [Layout.VIDEO]).
     */
    fun plan(desired: Features, mode: Layout): List<BindCandidate> {
        val split = if (mode == Layout.VIDEO) Layout.VIDEO else Layout.PHOTO
        val out = LinkedHashSet<BindCandidate>()
        // Night / HDR / Portrait photo modes only exist on a photo-only binding.
        if (split == Layout.PHOTO && desired.enhancement != PhotoEnhancement.NONE) {
            out += BindCandidate(Layout.PHOTO, normalize(Layout.PHOTO, desired))
        }
        for (level in degradeLevels(desired)) {
            out += BindCandidate(Layout.COMBINED, normalize(Layout.COMBINED, level))
            out += BindCandidate(split, normalize(split, level))
        }
        return out.toList()
    }

    /** [desired], then progressively fewer features, least important dropped first. */
    fun degradeLevels(desired: Features): List<Features> {
        val levels = mutableListOf(desired)
        fun step(next: Features) {
            if (next != levels.last()) levels += next
        }
        step(levels.last().copy(ultraHdr = false))
        step(levels.last().copy(hdrVideo = false))
        levels.last().fps?.let { if (it > 30) step(levels.last().copy(fps = 30)) }
        if (levels.last().stabilization == Stabilization.STEADY) step(levels.last().copy(stabilization = Stabilization.STANDARD))
        step(levels.last().copy(stabilization = Stabilization.OFF))
        step(levels.last().copy(raw = false))
        step(levels.last().copy(fps = null))
        return levels
    }

    /** Drops what a layout can't use, so equivalent candidates collapse into one. */
    fun normalize(layout: Layout, f: Features): Features = when (layout) {
        // Preview stabilization would make the viewfinder tighter than the photo, so photo-only
        // bindings run without it.
        Layout.PHOTO -> f.copy(hdrVideo = false, fps = null, stabilization = Stabilization.OFF)
        Layout.VIDEO -> f.copy(ultraHdr = false, raw = false, enhancement = PhotoEnhancement.NONE)
        Layout.COMBINED -> f.copy(enhancement = PhotoEnhancement.NONE)
    }
}
