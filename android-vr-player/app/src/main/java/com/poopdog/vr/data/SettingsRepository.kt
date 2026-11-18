package com.poopdog.vr.data

import android.content.Context
import com.poopdog.vr.model.AspectMode
import com.poopdog.vr.vr.ViewMode
import com.poopdog.vr.vr.ViewerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All user preferences. Defaults are tuned for a Cardboard-style viewer. */
data class VrSettings(
    // Headset / lenses
    val viewerPreset: ViewerProfile.Preset = ViewerProfile.Preset.CARDBOARD_V2,
    val interLensMm: Float = ViewerProfile.DEFAULT.interLensMm,
    val screenToLensMm: Float = ViewerProfile.DEFAULT.screenToLensMm,
    val k1: Float = ViewerProfile.DEFAULT.k1,
    val k2: Float = ViewerProfile.DEFAULT.k2,
    val fovHalfDeg: Float = ViewerProfile.DEFAULT.fovHalfDeg,
    val verticalOffsetMm: Float = 0f,
    val distortionCorrection: Boolean = true,
    val swapEyes: Boolean = false,
    // Display
    val viewMode: ViewMode = ViewMode.VR,
    val screenSizeDeg: Float = 80f,
    val screenDistanceM: Float = 4f,
    val curvedScreen: Boolean = false,
    val headLockedScreen: Boolean = false,
    val auto2dTo3d: Boolean = false,
    val depthStrength: Float = 0.5f,
    val aspectMode: AspectMode = AspectMode.AUTO,
    val maxBrightness: Boolean = true,
    // Controls
    val headTracking: Boolean = true,
    val gazeControl: Boolean = true,
    val dwellMs: Int = 1500,
    val lookDownMenu: Boolean = true,
    val showReticle: Boolean = true,
    val volumeKeysControl: Boolean = false,
    val hudTimeoutSec: Int = 8,
    val recenterFull: Boolean = false,
    // Playback
    val defaultLoops: Int = 1,
    val autoAdvanceDelaySec: Int = 3,
    val resumePlayback: Boolean = true,
    val autoDetectFormat: Boolean = true,
    val showSubtitles: Boolean = true,
    val skipOnError: Boolean = true,
) {
    val profile: ViewerProfile
        get() = ViewerProfile(interLensMm, screenToLensMm, k1, k2, fovHalfDeg, verticalOffsetMm)

    fun withPreset(p: ViewerProfile.Preset): VrSettings {
        val prof = p.profile ?: return copy(viewerPreset = p)
        return copy(
            viewerPreset = p,
            interLensMm = prof.interLensMm,
            screenToLensMm = prof.screenToLensMm,
            k1 = prof.k1,
            k2 = prof.k2,
            fovHalfDeg = prof.fovHalfDeg,
            verticalOffsetMm = prof.verticalOffsetMm,
            distortionCorrection = p != ViewerProfile.Preset.NO_LENSES,
        )
    }
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<VrSettings> = _settings.asStateFlow()
    val current: VrSettings get() = _settings.value

    fun update(transform: (VrSettings) -> VrSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        save(next)
    }

    private fun load(): VrSettings {
        val d = VrSettings()
        val p = prefs
        return VrSettings(
            viewerPreset = ViewerProfile.Preset.byName(p.getString("viewerPreset", d.viewerPreset.name)),
            interLensMm = p.getFloat("interLensMm", d.interLensMm),
            screenToLensMm = p.getFloat("screenToLensMm", d.screenToLensMm),
            k1 = p.getFloat("k1", d.k1),
            k2 = p.getFloat("k2", d.k2),
            fovHalfDeg = p.getFloat("fovHalfDeg", d.fovHalfDeg),
            verticalOffsetMm = p.getFloat("verticalOffsetMm", d.verticalOffsetMm),
            distortionCorrection = p.getBoolean("distortionCorrection", d.distortionCorrection),
            swapEyes = p.getBoolean("swapEyes", d.swapEyes),
            viewMode = enumOr(p.getString("viewMode", null), d.viewMode),
            screenSizeDeg = p.getFloat("screenSizeDeg", d.screenSizeDeg),
            screenDistanceM = p.getFloat("screenDistanceM", d.screenDistanceM),
            curvedScreen = p.getBoolean("curvedScreen", d.curvedScreen),
            headLockedScreen = p.getBoolean("headLockedScreen", d.headLockedScreen),
            auto2dTo3d = p.getBoolean("auto2dTo3d", d.auto2dTo3d),
            depthStrength = p.getFloat("depthStrength", d.depthStrength),
            aspectMode = enumOr(p.getString("aspectMode", null), d.aspectMode),
            maxBrightness = p.getBoolean("maxBrightness", d.maxBrightness),
            headTracking = p.getBoolean("headTracking", d.headTracking),
            gazeControl = p.getBoolean("gazeControl", d.gazeControl),
            dwellMs = p.getInt("dwellMs", d.dwellMs),
            lookDownMenu = p.getBoolean("lookDownMenu", d.lookDownMenu),
            showReticle = p.getBoolean("showReticle", d.showReticle),
            volumeKeysControl = p.getBoolean("volumeKeysControl", d.volumeKeysControl),
            hudTimeoutSec = p.getInt("hudTimeoutSec", d.hudTimeoutSec),
            recenterFull = p.getBoolean("recenterFull", d.recenterFull),
            defaultLoops = p.getInt("defaultLoops", d.defaultLoops),
            autoAdvanceDelaySec = p.getInt("autoAdvanceDelaySec", d.autoAdvanceDelaySec),
            resumePlayback = p.getBoolean("resumePlayback", d.resumePlayback),
            autoDetectFormat = p.getBoolean("autoDetectFormat", d.autoDetectFormat),
            showSubtitles = p.getBoolean("showSubtitles", d.showSubtitles),
            skipOnError = p.getBoolean("skipOnError", d.skipOnError),
        )
    }

    private fun save(s: VrSettings) {
        prefs.edit()
            .putString("viewerPreset", s.viewerPreset.name)
            .putFloat("interLensMm", s.interLensMm)
            .putFloat("screenToLensMm", s.screenToLensMm)
            .putFloat("k1", s.k1)
            .putFloat("k2", s.k2)
            .putFloat("fovHalfDeg", s.fovHalfDeg)
            .putFloat("verticalOffsetMm", s.verticalOffsetMm)
            .putBoolean("distortionCorrection", s.distortionCorrection)
            .putBoolean("swapEyes", s.swapEyes)
            .putString("viewMode", s.viewMode.name)
            .putFloat("screenSizeDeg", s.screenSizeDeg)
            .putFloat("screenDistanceM", s.screenDistanceM)
            .putBoolean("curvedScreen", s.curvedScreen)
            .putBoolean("headLockedScreen", s.headLockedScreen)
            .putBoolean("auto2dTo3d", s.auto2dTo3d)
            .putFloat("depthStrength", s.depthStrength)
            .putString("aspectMode", s.aspectMode.name)
            .putBoolean("maxBrightness", s.maxBrightness)
            .putBoolean("headTracking", s.headTracking)
            .putBoolean("gazeControl", s.gazeControl)
            .putInt("dwellMs", s.dwellMs)
            .putBoolean("lookDownMenu", s.lookDownMenu)
            .putBoolean("showReticle", s.showReticle)
            .putBoolean("volumeKeysControl", s.volumeKeysControl)
            .putInt("hudTimeoutSec", s.hudTimeoutSec)
            .putBoolean("recenterFull", s.recenterFull)
            .putInt("defaultLoops", s.defaultLoops)
            .putInt("autoAdvanceDelaySec", s.autoAdvanceDelaySec)
            .putBoolean("resumePlayback", s.resumePlayback)
            .putBoolean("autoDetectFormat", s.autoDetectFormat)
            .putBoolean("showSubtitles", s.showSubtitles)
            .putBoolean("skipOnError", s.skipOnError)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
