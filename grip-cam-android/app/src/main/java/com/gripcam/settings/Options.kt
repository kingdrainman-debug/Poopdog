package com.gripcam.settings

import android.hardware.camera2.CameraMetadata
import androidx.camera.core.AspectRatio
import androidx.camera.extensions.ExtensionMode

/** Anything that can be shown as a choice in the settings screen. */
interface Labeled {
    val label: String
    val detail: String? get() = null
}

// ---------------------------------------------------------------------------------------------
// Video
// ---------------------------------------------------------------------------------------------

enum class VideoResolution(override val label: String, val height: Int) : Labeled {
    UHD("4K", 2160),
    FHD("1080p", 1080),
    HD("720p", 720),
    SD("480p", 480),
}

enum class FrameRate(override val label: String, val fps: Int, override val detail: String? = null) : Labeled {
    FPS_24("24 fps", 24, "Cinematic motion"),
    FPS_25("25 fps", 25, "PAL regions"),
    FPS_30("30 fps", 30, "Standard"),
    FPS_60("60 fps", 60, "Smooth motion, sports"),
}

enum class Stabilization(override val label: String, override val detail: String) : Labeled {
    OFF("Off", "No digital stabilization. Optical stabilization still works if the lens has it."),
    STANDARD("Standard", "Smooths the recorded video."),
    STEADY("Steady", "Strongest. Steadies the viewfinder and the video, with a slightly tighter crop."),
}

enum class VideoBitrate(override val label: String, override val detail: String, val multiplier: Float) : Labeled {
    STANDARD("Standard", "The phone's default quality.", 1f),
    HIGH("High", "About 1.6× the data for finer detail.", 1.6f),
    MAX("Max", "About 2.5× the data. Large files.", 2.5f),
}

// ---------------------------------------------------------------------------------------------
// Photo
// ---------------------------------------------------------------------------------------------

enum class PhotoFormat(override val label: String, override val detail: String) : Labeled {
    JPEG("JPEG", "Works everywhere."),
    ULTRA_HDR("Ultra HDR", "JPEG with extra highlight detail on HDR screens."),
    RAW_JPEG("RAW + JPEG", "Also saves a DNG for editing in Lightroom or Snapseed."),
}

enum class PhotoEnhancement(override val label: String, override val detail: String, val extensionMode: Int) : Labeled {
    NONE("Standard", "No processing mode.", ExtensionMode.NONE),
    AUTO("Auto", "The phone picks HDR or Night for each shot.", ExtensionMode.AUTO),
    HDR("HDR", "Merges exposures for high-contrast scenes.", ExtensionMode.HDR),
    NIGHT("Night", "Brighter, cleaner low-light photos. Hold still.", ExtensionMode.NIGHT),
    BOKEH("Portrait", "Blurs the background behind people.", ExtensionMode.BOKEH),
    FACE_RETOUCH("Retouch", "Smooths skin.", ExtensionMode.FACE_RETOUCH),
}

enum class CaptureSpeed(override val label: String, override val detail: String) : Labeled {
    BEST("Best quality", "More processing per photo."),
    FAST("Fast", "Shortest delay between press and photo."),
    ZERO_LAG("Zero shutter lag", "Captures the moment you press. Not available while video is set up."),
}

enum class SelfTimer(override val label: String, val seconds: Int) : Labeled {
    OFF("Off", 0),
    S3("3 seconds", 3),
    S10("10 seconds", 10),
}

enum class DriveMode(override val label: String, override val detail: String) : Labeled {
    SINGLE("Single shot", "One photo per press."),
    BURST("Burst while held", "Keeps shooting while PHOTO is held down, like a Sony body in continuous mode."),
}

// ---------------------------------------------------------------------------------------------
// Framing and viewfinder
// ---------------------------------------------------------------------------------------------

enum class FrameAspect(override val label: String, override val detail: String, val longOverShort: Float, val cameraX: Int) : Labeled {
    R16_9("16:9", "Widescreen. Photos and video match the viewfinder exactly.", 16f / 9f, AspectRatio.RATIO_16_9),
    R4_3("4:3", "Uses the whole sensor for photos.", 4f / 3f, AspectRatio.RATIO_4_3),
}

enum class GridStyle(override val label: String) : Labeled {
    OFF("Off"),
    THIRDS("Rule of thirds"),
    GOLDEN("Golden ratio"),
    CENTER("Centre cross"),
}

enum class ZoomStops(override val label: String, val stops: List<Float>) : Labeled {
    STANDARD("1× · 3× · 6×", listOf(1f, 3f, 6f)),
    FINE("1× · 2× · 3× · 6×", listOf(1f, 2f, 3f, 6f)),
    TELE("1× · 3× · 6× · 10×", listOf(1f, 3f, 6f, 10f)),
    CLASSIC("1× · 2× · 5× · 10×", listOf(1f, 2f, 5f, 10f)),
    ;

    override val detail: String get() = "The ultra-wide lens is added automatically if your phone has one."
}

enum class VolumeKeyAction(override val label: String) : Labeled {
    SHUTTER("Take photo"),
    RECORD("Start / stop video"),
    ZOOM("Zoom (up = in, down = out)"),
    OFF("Change volume"),
}

// ---------------------------------------------------------------------------------------------
// Grip and connection
// ---------------------------------------------------------------------------------------------

enum class GripButtonAction(override val label: String) : Labeled {
    SWITCH_CAMERA("Switch front / back camera (works while recording)"),
    NEXT_ZOOM_STOP("Jump to the next zoom stop"),
    LOCK_AE_AF("Lock / unlock focus and exposure"),
    TOGGLE_TORCH("Toggle torch"),
    CYCLE_FLASH("Cycle photo flash"),
    TOGGLE_GRID("Toggle grid"),
    NONE("Nothing"),
}

enum class ModelCode(override val label: String) : Labeled {
    E1("E1 (E-mount, default)"),
    U1("U1"),
    A1("A1 (A-mount)"),
}

enum class ProtocolVersion(override val label: String, val byte: Int) : Labeled {
    V64("0x64 (default)", 0x64),
    V65("0x65 (2023+ bodies)", 0x65),
}

// ---------------------------------------------------------------------------------------------
// Session-only quick controls (not persisted)
// ---------------------------------------------------------------------------------------------

enum class WhiteBalance(override val label: String, val camera2Mode: Int) : Labeled {
    AUTO("Auto", CameraMetadata.CONTROL_AWB_MODE_AUTO),
    DAYLIGHT("Daylight", CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY("Cloudy", CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    TUNGSTEN("Tungsten", CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT),
    FLUORESCENT("Fluorescent", CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT),
}
