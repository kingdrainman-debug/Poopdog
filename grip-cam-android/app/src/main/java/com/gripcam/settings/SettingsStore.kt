package com.gripcam.settings

import android.content.Context
import com.gripcam.grip.AdvertConfig
import com.gripcam.grip.C1Action
import com.gripcam.grip.MapperConfig
import com.gripcam.grip.SonyRemoteProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val c1Action: C1Action = C1Action.SWITCH_CAMERA,
    val focusJogsAsZoom: Boolean = true,
    val invertZoom: Boolean = false,
    /** Multiplier on grip zoom speed, 0.25..2. */
    val zoomSpeed: Float = 1f,
    val pairingMode: Boolean = true,
    val modelCode: String = SonyRemoteProtocol.DEFAULT_MODEL_CODE,
    val protocolVersion: Int = SonyRemoteProtocol.PROTOCOL_VERSION_64,
    val sounds: Boolean = true,
    val showLog: Boolean = false,
) {
    val mapperConfig: MapperConfig
        get() = MapperConfig(focusJogsAsZoom = focusJogsAsZoom, invertZoom = invertZoom, c1Action = c1Action)

    val advertConfig: AdvertConfig
        get() = AdvertConfig(pairingMode = pairingMode, modelCode = modelCode, protocolVersion = protocolVersion)

    companion object {
        val MODEL_CODES = listOf("E1", "U1", "A1")
        val PROTOCOL_VERSIONS = listOf(SonyRemoteProtocol.PROTOCOL_VERSION_64, SonyRemoteProtocol.PROTOCOL_VERSION_65)
    }
}

/** Small SharedPreferences-backed store; settings are few and read synchronously at start. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gripcam", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        save(next)
        _settings.value = next
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            c1Action = prefs.getString(KEY_C1, null)
                ?.let { name -> C1Action.entries.firstOrNull { it.name == name } } ?: d.c1Action,
            focusJogsAsZoom = prefs.getBoolean(KEY_FOCUS_AS_ZOOM, d.focusJogsAsZoom),
            invertZoom = prefs.getBoolean(KEY_INVERT_ZOOM, d.invertZoom),
            zoomSpeed = prefs.getFloat(KEY_ZOOM_SPEED, d.zoomSpeed).coerceIn(0.25f, 2f),
            pairingMode = prefs.getBoolean(KEY_PAIRING, d.pairingMode),
            modelCode = prefs.getString(KEY_MODEL, null)?.takeIf { it in AppSettings.MODEL_CODES } ?: d.modelCode,
            protocolVersion = prefs.getInt(KEY_PROTOCOL, d.protocolVersion)
                .takeIf { it in AppSettings.PROTOCOL_VERSIONS } ?: d.protocolVersion,
            sounds = prefs.getBoolean(KEY_SOUNDS, d.sounds),
            showLog = prefs.getBoolean(KEY_SHOW_LOG, d.showLog),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString(KEY_C1, s.c1Action.name)
            .putBoolean(KEY_FOCUS_AS_ZOOM, s.focusJogsAsZoom)
            .putBoolean(KEY_INVERT_ZOOM, s.invertZoom)
            .putFloat(KEY_ZOOM_SPEED, s.zoomSpeed)
            .putBoolean(KEY_PAIRING, s.pairingMode)
            .putString(KEY_MODEL, s.modelCode)
            .putInt(KEY_PROTOCOL, s.protocolVersion)
            .putBoolean(KEY_SOUNDS, s.sounds)
            .putBoolean(KEY_SHOW_LOG, s.showLog)
            .apply()
    }

    private companion object {
        const val KEY_C1 = "c1_action"
        const val KEY_FOCUS_AS_ZOOM = "focus_jogs_as_zoom"
        const val KEY_INVERT_ZOOM = "invert_zoom"
        const val KEY_ZOOM_SPEED = "zoom_speed"
        const val KEY_PAIRING = "pairing_mode"
        const val KEY_MODEL = "model_code"
        const val KEY_PROTOCOL = "protocol_version"
        const val KEY_SOUNDS = "sounds"
        const val KEY_SHOW_LOG = "show_log"
    }
}
