package com.gripcam

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gripcam.camera.CameraController
import com.gripcam.camera.CameraSignal
import com.gripcam.grip.C1Action
import com.gripcam.grip.CameraAction
import com.gripcam.grip.GripActionMapper
import com.gripcam.grip.GripEvent
import com.gripcam.grip.GripServer
import com.gripcam.grip.GripStatus
import com.gripcam.grip.SonyRemoteProtocol.StatusField
import com.gripcam.settings.AppSettings
import com.gripcam.settings.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    val settings: StateFlow<AppSettings> = settingsStore.settings

    val camera = CameraController(app)
    private val grip = GripServer(app)
    val gripStatus: StateFlow<GripStatus> = grip.status

    private val mapper = GripActionMapper()
    private var gripStarted = false

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    init {
        viewModelScope.launch { grip.events.collect(::onGripEvent) }
        viewModelScope.launch { grip.log.collect(::appendLog) }
        viewModelScope.launch { camera.signals.collect(::onCameraSignal) }
        viewModelScope.launch {
            settings.map { it.advertConfig }.distinctUntilChanged().drop(1).collect { grip.updateConfig(it) }
        }
        viewModelScope.launch { settings.collect { camera.soundsEnabled = it.sounds } }
    }

    /** Called once the Bluetooth permissions are in place (and again whenever they change). */
    fun startGrip() {
        if (gripStarted) {
            grip.retry()
        } else {
            gripStarted = true
            grip.start(settings.value.advertConfig)
        }
    }

    fun restartGrip() {
        grip.stop()
        gripStarted = false
        startGrip()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    fun clearLog() = _log.update { emptyList() }

    private val _iconRotation = MutableStateFlow(0f)

    /** Degrees to rotate on-screen icons so they stay upright in the user's hand (UI is portrait-locked). */
    val iconRotation: StateFlow<Float> = _iconRotation.asStateFlow()

    /** [degrees] is the device orientation bucket: 0, 90, 180 or 270 (OrientationEventListener). */
    fun onDeviceOrientation(degrees: Int) {
        camera.setTargetRotation(
            when (degrees) {
                90 -> Surface.ROTATION_270
                180 -> Surface.ROTATION_180
                270 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            },
        )
        _iconRotation.value = when (degrees) {
            90 -> -90f
            180 -> 180f
            270 -> 90f
            else -> 0f
        }
    }

    // ------------------------------------------------------------------------------------------
    // Grip -> camera
    // ------------------------------------------------------------------------------------------

    private fun onGripEvent(event: GripEvent) {
        when (event) {
            is GripEvent.CommandReceived ->
                mapper.map(event.command, settings.value.mapperConfig).forEach(::perform)

            is GripEvent.RemoteConnected -> _messages.tryEmit("${event.name} connected")
            GripEvent.RemoteDisconnected -> {
                mapper.reset().forEach(::perform)
                _messages.tryEmit("Grip disconnected")
            }
        }
    }

    private fun perform(action: CameraAction) {
        when (action) {
            CameraAction.FocusStart -> camera.startAutoFocus()
            CameraAction.FocusRelease -> camera.releaseFocus()
            CameraAction.Capture -> camera.takePhoto()
            CameraAction.ToggleRecording -> camera.toggleRecording()
            is CameraAction.Zoom -> camera.zoomJog(action.direction, action.speed, settings.value.zoomSpeed)
            is CameraAction.ManualFocus -> camera.focusJog(action.direction, action.speed)
            is CameraAction.Custom -> when (action.action) {
                C1Action.SWITCH_CAMERA -> camera.switchCamera()
                C1Action.TOGGLE_TORCH -> camera.toggleTorch()
                C1Action.CYCLE_FLASH -> camera.cycleFlash()
                C1Action.NONE -> Unit
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Camera -> grip (the same three flags a Sony camera reports)
    // ------------------------------------------------------------------------------------------

    private fun onCameraSignal(signal: CameraSignal) {
        when (signal) {
            is CameraSignal.FocusResult -> grip.setStatus(StatusField.FOCUS, signal.acquired)
            is CameraSignal.Shutter -> grip.setStatus(StatusField.SHUTTER, signal.open)
            is CameraSignal.Recording -> grip.setStatus(StatusField.RECORDING, signal.active)
            is CameraSignal.Saved -> Unit
            is CameraSignal.Message -> _messages.tryEmit(signal.text)
        }
    }

    private fun appendLog(line: String) {
        val stamped = "${timeFormat.format(Date())}  $line"
        _log.update { (it + stamped).takeLast(LOG_LINES) }
    }

    override fun onCleared() {
        grip.release()
        camera.release()
    }

    private companion object {
        const val LOG_LINES = 80
    }
}
