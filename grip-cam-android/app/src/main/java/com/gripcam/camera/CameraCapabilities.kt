package com.gripcam.camera

import com.gripcam.settings.FrameRate
import com.gripcam.settings.PhotoEnhancement
import com.gripcam.settings.PhotoFormat
import com.gripcam.settings.VideoResolution
import com.gripcam.settings.WhiteBalance

/**
 * What the current camera can do. Settings use it to hide options the phone doesn't support;
 * before the camera is open ([known] = false) everything is offered.
 */
data class CameraCapabilities(
    val known: Boolean = false,
    val videoResolutions: Set<VideoResolution> = VideoResolution.entries.toSet(),
    val hdrVideo: Boolean = true,
    val frameRates: Set<FrameRate> = FrameRate.entries.toSet(),
    val videoStabilization: Boolean = true,
    val previewStabilization: Boolean = true,
    val photoFormats: Set<PhotoFormat> = PhotoFormat.entries.toSet(),
    val enhancements: Set<PhotoEnhancement> = PhotoEnhancement.entries.toSet(),
    val zeroShutterLag: Boolean = true,
    val lowLightBoost: Boolean = true,
    val flash: Boolean = true,
    val exposureMin: Int = 0,
    val exposureMax: Int = 0,
    /** EV per exposure-compensation index step. */
    val exposureStep: Float = 0f,
    val whiteBalance: Set<WhiteBalance> = setOf(WhiteBalance.AUTO),
    /** Closest focus in diopters; 0 means fixed focus (no manual focus). */
    val maxFocusDiopters: Float = 0f,
) {
    val exposureSupported: Boolean get() = exposureMax > exposureMin && exposureStep > 0f

    companion object {
        val UNKNOWN = CameraCapabilities()
    }
}
