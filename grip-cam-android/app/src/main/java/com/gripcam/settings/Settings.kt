package com.gripcam.settings

import android.content.Context
import android.content.SharedPreferences
import com.gripcam.camera.CameraCapabilities
import com.gripcam.grip.AdvertConfig
import com.gripcam.grip.MapperConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * One user preference. To add a setting: declare it in [Prefs], put it in a section of
 * [SettingsCatalog], and read it with `settings[Prefs.YOUR_SETTING]`. Persistence and the
 * settings screen pick it up automatically.
 */
sealed class Setting<T : Any>(
    val key: String,
    val default: T,
    val title: String,
    val summary: String?,
    /** Hide the whole row when the current camera can't use it. */
    val visibleWhen: (CameraCapabilities) -> Boolean,
)

class ToggleSetting(
    key: String,
    default: Boolean,
    title: String,
    summary: String? = null,
    visibleWhen: (CameraCapabilities) -> Boolean = { true },
) : Setting<Boolean>(key, default, title, summary, visibleWhen)

class SliderSetting(
    key: String,
    default: Float,
    title: String,
    summary: String? = null,
    val range: ClosedFloatingPointRange<Float>,
    /** Discrete steps between the ends, as Compose's Slider counts them (0 = continuous). */
    val steps: Int = 0,
    val format: (Float) -> String,
    visibleWhen: (CameraCapabilities) -> Boolean = { true },
) : Setting<Float>(key, default, title, summary, visibleWhen)

class ChoiceSetting<E>(
    key: String,
    default: E,
    title: String,
    summary: String? = null,
    val options: List<E>,
    /** Whether the current camera supports this option; unsupported ones are hidden. */
    val isSupported: (CameraCapabilities, E) -> Boolean = { _, _ -> true },
    visibleWhen: (CameraCapabilities) -> Boolean = { true },
) : Setting<E>(key, default, title, summary, visibleWhen) where E : Enum<E>, E : Labeled {
    fun decode(name: String?): E? = options.firstOrNull { it.name == name }

    fun available(caps: CameraCapabilities): List<E> = options.filter { isSupported(caps, it) }
}

/** Immutable snapshot of every setting. */
class AppSettings internal constructor(private val values: Map<String, Any>) {

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(setting: Setting<T>): T = (values[setting.key] as T?) ?: setting.default

    /** Untyped read for generic UI code holding a `Setting<*>`. */
    fun valueOf(setting: Setting<*>): Any = values[setting.key] ?: setting.default

    fun <T : Any> with(setting: Setting<T>, value: T): AppSettings = AppSettings(values + (setting.key to value))

    override fun equals(other: Any?) = other is AppSettings && other.values == values

    override fun hashCode() = values.hashCode()

    val mapperConfig: MapperConfig
        get() = MapperConfig(
            focusJogsAsZoom = this[Prefs.FOCUS_AS_ZOOM],
            invertZoom = this[Prefs.INVERT_ZOOM],
            zoomSnap = this[Prefs.ZOOM_SNAP],
        )

    val advertConfig: AdvertConfig
        get() = AdvertConfig(
            pairingMode = this[Prefs.PAIRING],
            modelCode = this[Prefs.MODEL_CODE].name,
            protocolVersion = this[Prefs.PROTOCOL].byte,
        )

    companion object {
        val DEFAULTS = AppSettings(emptyMap())
    }
}

/** Every setting in the app. */
object Prefs {
    // ---- Video ----
    val VIDEO_RESOLUTION = ChoiceSetting(
        "video_resolution", VideoResolution.FHD, "Resolution",
        options = VideoResolution.entries,
        isSupported = { c, v -> v in c.videoResolutions },
    )
    val FRAME_RATE = ChoiceSetting(
        "frame_rate", FrameRate.FPS_30, "Frame rate",
        summary = "If a combination isn't possible on this phone, GripCam steps down and tells you.",
        options = FrameRate.entries,
        isSupported = { c, v -> v == FrameRate.FPS_30 || v in c.frameRates },
    )
    val STABILIZATION = ChoiceSetting(
        "stabilization", Stabilization.STANDARD, "Stabilization",
        options = Stabilization.entries,
        isSupported = { c, v ->
            when (v) {
                Stabilization.OFF -> true
                Stabilization.STANDARD -> c.videoStabilization
                Stabilization.STEADY -> c.previewStabilization
            }
        },
    )
    val HDR_VIDEO = ToggleSetting(
        "hdr_video", false, "HDR video (HLG 10-bit)",
        "Richer highlights and colour on HDR screens. Recorded as HEVC.",
        visibleWhen = { it.hdrVideo },
    )
    val BITRATE = ChoiceSetting("video_bitrate", VideoBitrate.STANDARD, "Bitrate", options = VideoBitrate.entries)
    val RECORD_AUDIO = ToggleSetting("record_audio", true, "Record sound")
    val LOW_LIGHT = ToggleSetting(
        "low_light_boost", false, "Low-light boost",
        "Brightens dark scenes in the viewfinder and video. Can lower the frame rate.",
        visibleWhen = { it.lowLightBoost },
    )

    // ---- Photo ----
    val PHOTO_MODE = ChoiceSetting(
        "photo_mode", PhotoEnhancement.NONE, "Photo mode",
        summary = "Night, HDR and Portrait use the phone maker's processing. Photos only.",
        options = PhotoEnhancement.entries,
        isSupported = { c, v -> v in c.enhancements },
        visibleWhen = { it.enhancements.size > 1 },
    )
    val PHOTO_FORMAT = ChoiceSetting(
        "photo_format", PhotoFormat.JPEG, "Format",
        options = PhotoFormat.entries,
        isSupported = { c, v -> v in c.photoFormats },
    )
    val CAPTURE_SPEED = ChoiceSetting(
        "capture_speed", CaptureSpeed.FAST, "Shutter priority",
        options = CaptureSpeed.entries,
        isSupported = { c, v -> v != CaptureSpeed.ZERO_LAG || c.zeroShutterLag },
    )
    val JPEG_QUALITY = SliderSetting(
        "jpeg_quality", 95f, "JPEG quality",
        range = 70f..100f, steps = 5, format = { "${it.toInt()}" },
    )
    val TIMER = ChoiceSetting(
        "self_timer", SelfTimer.OFF, "Self-timer",
        summary = "Also applies to the grip's PHOTO button. Press again to cancel.",
        options = SelfTimer.entries,
    )
    val DRIVE = ChoiceSetting("drive_mode", DriveMode.SINGLE, "Drive mode", options = DriveMode.entries)
    val MIRROR_FRONT = ToggleSetting(
        "mirror_front", false, "Mirror front camera",
        "Save selfies and front-camera video as the viewfinder shows them.",
    )
    val SOUNDS = ToggleSetting(
        "sounds", true, "Shutter and record sounds",
        "Audible feedback when the phone is out of reach on the grip.",
    )

    // ---- Viewfinder ----
    val ASPECT = ChoiceSetting(
        "aspect", FrameAspect.R16_9, "Frame",
        summary = "The viewfinder shows exactly what will be recorded.",
        options = FrameAspect.entries,
    )
    val GRID = ChoiceSetting("grid", GridStyle.OFF, "Grid", options = GridStyle.entries)
    val LEVEL = ToggleSetting("level", true, "Level indicator", "A horizon line that turns green when the shot is level.")
    val ZOOM_STOPS = ChoiceSetting(
        "zoom_stops", ZoomStops.STANDARD, "Zoom stops",
        summary = "The zoom buttons above the shutter. Double-press T or W on the grip to jump between them.",
        options = ZoomStops.entries,
    )
    val VOLUME_KEYS = ChoiceSetting(
        "volume_keys", VolumeKeyAction.SHUTTER, "Volume buttons",
        options = VolumeKeyAction.entries,
    )

    // ---- Grip ----
    val C1_ACTION = ChoiceSetting("c1_action", GripButtonAction.SWITCH_CAMERA, "C1 press", options = GripButtonAction.entries)
    val C1_DOUBLE = ChoiceSetting(
        "c1_double_action", GripButtonAction.NONE, "C1 double-press",
        summary = "When set, a single C1 press waits a third of a second for a possible second press.",
        options = GripButtonAction.entries,
    )
    val ZOOM_SNAP = ToggleSetting(
        "zoom_snap", true, "Double-press T / W to jump zoom",
        "Quickly press T twice for the next zoom stop (1× → 3× → 6×), W twice to go back. Holding still zooms smoothly.",
    )
    val ZOOM_SPEED = SliderSetting(
        "zoom_speed", 1f, "Zoom lever speed",
        range = 0.25f..2f, format = { String.format(Locale.US, "%.2g×", it) },
    )
    val INVERT_ZOOM = ToggleSetting("invert_zoom", false, "Reverse zoom direction", "Swap T and W.")
    val FOCUS_AS_ZOOM = ToggleSetting(
        "focus_jogs_as_zoom", true, "Treat focus codes as zoom",
        "Keep on for the GP-VPT2BT. Turn off for a remote with Focus +/– buttons to get manual focus.",
    )

    // ---- Connection ----
    val PAIRING = ToggleSetting(
        "pairing_mode", true, "Accept new grip pairings",
        "Advertise as a camera in pairing mode. Turn off once paired if another remote keeps grabbing the phone.",
    )
    val SHOW_LOG = ToggleSetting(
        "show_log", false, "Show control log",
        "Lists every command the grip sends, to check what each button does.",
    )
    val MODEL_CODE = ChoiceSetting(
        "model_code", ModelCode.E1, "Advertised model code",
        summary = "Only change this if the grip won't connect.",
        options = ModelCode.entries,
    )
    val PROTOCOL = ChoiceSetting(
        "protocol", ProtocolVersion.V64, "Advertised protocol version",
        summary = "Only change this if the grip won't connect.",
        options = ProtocolVersion.entries,
    )
}

enum class SectionId { VIDEO, PHOTO, VIEWFINDER, GRIP, CONNECTION }

class SettingsSection(val id: SectionId, val title: String, val settings: List<Setting<*>>)

object SettingsCatalog {
    val sections: List<SettingsSection> = listOf(
        SettingsSection(
            SectionId.VIDEO, "Video",
            listOf(
                Prefs.VIDEO_RESOLUTION, Prefs.FRAME_RATE, Prefs.STABILIZATION, Prefs.HDR_VIDEO,
                Prefs.BITRATE, Prefs.LOW_LIGHT, Prefs.RECORD_AUDIO,
            ),
        ),
        SettingsSection(
            SectionId.PHOTO, "Photo",
            listOf(
                Prefs.PHOTO_MODE, Prefs.PHOTO_FORMAT, Prefs.CAPTURE_SPEED, Prefs.JPEG_QUALITY,
                Prefs.DRIVE, Prefs.TIMER, Prefs.MIRROR_FRONT, Prefs.SOUNDS,
            ),
        ),
        SettingsSection(
            SectionId.VIEWFINDER, "Viewfinder",
            listOf(Prefs.ASPECT, Prefs.GRID, Prefs.LEVEL, Prefs.ZOOM_STOPS, Prefs.VOLUME_KEYS),
        ),
        SettingsSection(
            SectionId.GRIP, "Grip buttons",
            listOf(
                Prefs.C1_ACTION, Prefs.C1_DOUBLE, Prefs.ZOOM_SNAP, Prefs.ZOOM_SPEED,
                Prefs.INVERT_ZOOM, Prefs.FOCUS_AS_ZOOM,
            ),
        ),
        SettingsSection(
            SectionId.CONNECTION, "Connection",
            listOf(Prefs.PAIRING, Prefs.SHOW_LOG, Prefs.MODEL_CODE, Prefs.PROTOCOL),
        ),
    )

    val all: List<Setting<*>> = sections.flatMap { it.settings }
}

/** SharedPreferences persistence for [SettingsCatalog.all]. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("gripcam", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun <T : Any> set(setting: Setting<T>, value: T) {
        prefs.edit().also { write(it, setting, value) }.apply()
        _settings.update { it.with(setting, value) }
    }

    /** For generic UI code holding a `Setting<*>`; rejects values of the wrong kind. */
    fun setAny(setting: Setting<*>, value: Any) {
        when (setting) {
            is ToggleSetting -> set(setting, value as Boolean)
            is SliderSetting -> set(setting, (value as Float).coerceIn(setting.range))
            is ChoiceSetting<*> -> {
                require(value in setting.options) { "${setting.key}: $value is not an option" }
                @Suppress("UNCHECKED_CAST")
                (setting as Setting<Any>).let { s ->
                    prefs.edit().putString(s.key, (value as Enum<*>).name).apply()
                    _settings.update { it.with(s, value) }
                }
            }
        }
    }

    fun resetAll() {
        prefs.edit().also { e -> SettingsCatalog.all.forEach { e.remove(it.key) } }.apply()
        _settings.value = AppSettings.DEFAULTS
    }

    private fun load(): AppSettings {
        var snapshot = AppSettings.DEFAULTS
        for (setting in SettingsCatalog.all) {
            if (!prefs.contains(setting.key)) continue
            try {
                @Suppress("UNCHECKED_CAST")
                when (setting) {
                    is ToggleSetting -> snapshot = snapshot.with(setting, prefs.getBoolean(setting.key, setting.default))
                    is SliderSetting -> snapshot = snapshot.with(
                        setting,
                        prefs.getFloat(setting.key, setting.default).coerceIn(setting.range),
                    )
                    is ChoiceSetting<*> -> setting.decode(prefs.getString(setting.key, null))?.let {
                        snapshot = snapshot.with(setting as Setting<Any>, it)
                    }
                }
            } catch (_: ClassCastException) {
                // A key that changed type between versions: fall back to the default.
            }
        }
        return snapshot
    }

    private fun <T : Any> write(editor: SharedPreferences.Editor, setting: Setting<T>, value: T) {
        when (setting) {
            is ToggleSetting -> editor.putBoolean(setting.key, value as Boolean)
            is SliderSetting -> editor.putFloat(setting.key, value as Float)
            is ChoiceSetting<*> -> editor.putString(setting.key, (value as Enum<*>).name)
        }
    }
}
