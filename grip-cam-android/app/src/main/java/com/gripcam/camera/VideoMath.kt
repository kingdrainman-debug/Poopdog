package com.gripcam.camera

import com.gripcam.settings.VideoBitrate
import com.gripcam.settings.VideoResolution

/** Bitrate targets and storage estimates. Pure functions. */
object VideoMath {
    /** Typical phone encoder bitrates at 30 fps, in bits per second. */
    fun baseBitrate(resolution: VideoResolution): Long = when (resolution) {
        VideoResolution.UHD -> 42_000_000L
        VideoResolution.FHD -> 17_000_000L
        VideoResolution.HD -> 10_000_000L
        VideoResolution.SD -> 3_500_000L
    }

    fun estimatedBitrate(resolution: VideoResolution, fps: Int, bitrate: VideoBitrate): Long {
        val fpsFactor = if (fps > 30) 1.6 else 1.0
        return (baseBitrate(resolution) * fpsFactor * bitrate.multiplier).toLong()
    }

    /** What to ask the encoder for, or null to keep the phone's own default. */
    fun targetBitrate(resolution: VideoResolution, fps: Int, bitrate: VideoBitrate): Int? =
        if (bitrate == VideoBitrate.STANDARD) null else estimatedBitrate(resolution, fps, bitrate).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Seconds of recording that fit in [freeBytes], allowing for ~128 kbps of audio. */
    fun remainingSeconds(freeBytes: Long, videoBitsPerSecond: Long): Long {
        val total = videoBitsPerSecond + 128_000L
        if (total <= 0 || freeBytes <= 0) return 0
        return freeBytes * 8 / total
    }

    /** "2 h 05 min", "12 min", "40 s". */
    fun formatDuration(seconds: Long): String = when {
        seconds >= 3600 -> "%d h %02d min".format(seconds / 3600, (seconds % 3600) / 60)
        seconds >= 60 -> "${seconds / 60} min"
        else -> "$seconds s"
    }
}
