package com.gripcam

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.ToneGenerator
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import androidx.camera.core.MeteringPoint
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gripcam.camera.CameraController
import com.gripcam.camera.CameraSignal
import com.gripcam.camera.CaptureSettings
import com.gripcam.grip.CameraAction
import com.gripcam.grip.GripActionMapper
import com.gripcam.grip.GripEvent
import com.gripcam.grip.GripServer
import com.gripcam.grip.GripStatus
import com.gripcam.grip.SonyRemoteProtocol.StatusField
import com.gripcam.settings.AppSettings
import com.gripcam.settings.ChoiceSetting
import com.gripcam.settings.DriveMode
import com.gripcam.settings.GridStyle
import com.gripcam.settings.GripButtonAction
import com.gripcam.settings.Prefs
import com.gripcam.settings.SelfTimer
import com.gripcam.settings.Setting
import com.gripcam.settings.SettingsStore
import com.gripcam.settings.VolumeKeyAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The most recent photo or video, for the gallery thumbnail. */
data class LastCapture(val uri: Uri, val video: Boolean, val thumbnail: Bitmap?)

/** Phone status shown in the recording HUD. */
data class DeviceStatus(val batteryPercent: Int = -1, val freeBytes: Long = -1)

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

    private val _countdown = MutableStateFlow(0)

    /** Seconds left on the self-timer, 0 when idle. */
    val countdown: StateFlow<Int> = _countdown.asStateFlow()
    private var countdownJob: Job? = null

    private val _lastCapture = MutableStateFlow<LastCapture?>(null)
    val lastCapture: StateFlow<LastCapture?> = _lastCapture.asStateFlow()

    private val _deviceStatus = MutableStateFlow(DeviceStatus())
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    private val _iconRotation = MutableStateFlow(0f)

    /** Degrees to rotate on-screen icons so they stay upright in the user's hand (UI is portrait-locked). */
    val iconRotation: StateFlow<Float> = _iconRotation.asStateFlow()

    /** While the settings screen is open, hardware keys keep their normal meaning. */
    var settingsOpen = false

    private var pendingC1: Job? = null
    private val tones: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val miscPrefs = app.getSharedPreferences("gripcam_state", Context.MODE_PRIVATE)

    init {
        viewModelScope.launch { grip.events.collect(::onGripEvent) }
        viewModelScope.launch { grip.log.collect(::appendLog) }
        viewModelScope.launch { camera.signals.collect(::onCameraSignal) }
        viewModelScope.launch {
            settings.map { it.advertConfig }.distinctUntilChanged().drop(1).collect { grip.updateConfig(it) }
        }
        viewModelScope.launch {
            settings.map { CaptureSettings.from(it) }.distinctUntilChanged().collect { camera.applySettings(it) }
        }
        viewModelScope.launch { settings.collect { camera.soundsEnabled = it[Prefs.SOUNDS] } }
        viewModelScope.launch {
            while (isActive) {
                refreshDeviceStatus()
                delay(10_000)
            }
        }
        restoreLastCapture()
    }

    // ------------------------------------------------------------------------------------------
    // Bluetooth grip lifecycle
    // ------------------------------------------------------------------------------------------

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

    // ------------------------------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------------------------------

    fun <T : Any> set(setting: Setting<T>, value: T) = settingsStore.set(setting, value)

    fun setAny(setting: Setting<*>, value: Any) = settingsStore.setAny(setting, value)

    fun resetSettings() = settingsStore.resetAll()

    /** Quick-bar helper: the next option this camera supports. */
    fun <E> cycle(setting: ChoiceSetting<E>) where E : Enum<E>, E : com.gripcam.settings.Labeled {
        val options = setting.available(camera.state.value.capabilities)
        if (options.isEmpty()) return
        val current = settings.value[setting]
        val next = options[(options.indexOf(current) + 1) % options.size]
        set(setting, next)
        _messages.tryEmit("${setting.title}: ${next.label}")
    }

    fun clearLog() = _log.update { emptyList() }

    // ------------------------------------------------------------------------------------------
    // Screen and hardware buttons
    // ------------------------------------------------------------------------------------------

    /** On-screen shutter or a shutter key went down. */
    fun shutterDown() {
        if (countdownJob?.isActive == true) {
            cancelCountdown()
            return
        }
        val timer = settings.value[Prefs.TIMER]
        when {
            timer != SelfTimer.OFF -> startCountdown(timer.seconds)
            settings.value[Prefs.DRIVE] == DriveMode.BURST -> camera.startBurst()
            else -> camera.takePhoto()
        }
    }

    fun shutterUp() = camera.stopBurst()

    fun tapToFocus(point: MeteringPoint, fraction: Pair<Float, Float>) = camera.startAutoFocus(point, fraction)

    fun longPressLock(point: MeteringPoint, fraction: Pair<Float, Float>) = camera.lockAeAf(point, fraction)

    /** Returns true when the key was used. */
    fun onVolumeKey(up: Boolean, pressed: Boolean, repeatCount: Int): Boolean {
        if (settingsOpen) return false
        when (settings.value[Prefs.VOLUME_KEYS]) {
            VolumeKeyAction.OFF -> return false
            VolumeKeyAction.SHUTTER -> if (pressed && repeatCount == 0) shutterDown() else if (!pressed) shutterUp()
            VolumeKeyAction.RECORD -> if (pressed && repeatCount == 0) camera.toggleRecording()
            VolumeKeyAction.ZOOM -> if (pressed && repeatCount == 0) {
                camera.zoomJog(if (up) 1 else -1, 0.35f, settings.value[Prefs.ZOOM_SPEED])
            } else if (!pressed) {
                camera.zoomJog(0, 0f, 1f)
            }
        }
        return true
    }

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
            is GripEvent.CommandReceived -> mapper
                .map(event.command, settings.value.mapperConfig, SystemClock.uptimeMillis())
                .forEach(::perform)

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
            CameraAction.ShutterPress -> shutterDown()
            CameraAction.ShutterRelease -> shutterUp()
            CameraAction.ToggleRecording -> camera.toggleRecording()
            is CameraAction.Zoom -> camera.zoomJog(action.direction, action.speed, settings.value[Prefs.ZOOM_SPEED])
            is CameraAction.ZoomSnap -> camera.snapZoom(action.direction, settings.value[Prefs.ZOOM_STOPS].stops)
            is CameraAction.ManualFocus -> camera.focusJog(action.direction, action.speed)
            CameraAction.C1Press -> onC1Press()
        }
    }

    /** Single press acts at once unless a double-press action is set; then it waits briefly. */
    private fun onC1Press() {
        val single = settings.value[Prefs.C1_ACTION]
        val double = settings.value[Prefs.C1_DOUBLE]
        if (double == GripButtonAction.NONE) {
            runButtonAction(single)
            return
        }
        val pending = pendingC1
        if (pending?.isActive == true) {
            pending.cancel()
            pendingC1 = null
            runButtonAction(double)
        } else {
            pendingC1 = viewModelScope.launch {
                delay(C1_DOUBLE_PRESS_MS)
                pendingC1 = null
                runButtonAction(single)
            }
        }
    }

    fun runButtonAction(action: GripButtonAction) {
        when (action) {
            GripButtonAction.SWITCH_CAMERA -> camera.switchCamera()
            GripButtonAction.NEXT_ZOOM_STOP -> camera.cycleZoomStop(settings.value[Prefs.ZOOM_STOPS].stops)
            GripButtonAction.LOCK_AE_AF -> {
                camera.toggleAeAfLock()
                _messages.tryEmit(if (camera.state.value.aeAfLocked) "Focus and exposure locked" else "Focus and exposure unlocked")
            }
            GripButtonAction.TOGGLE_TORCH -> camera.toggleTorch()
            GripButtonAction.CYCLE_FLASH -> camera.cycleFlash()
            GripButtonAction.TOGGLE_GRID -> set(
                Prefs.GRID,
                if (settings.value[Prefs.GRID] == GridStyle.OFF) GridStyle.THIRDS else GridStyle.OFF,
            )
            GripButtonAction.NONE -> Unit
        }
    }

    // ------------------------------------------------------------------------------------------
    // Self-timer
    // ------------------------------------------------------------------------------------------

    private fun startCountdown(seconds: Int) {
        countdownJob = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _countdown.value = left
                if (settings.value[Prefs.SOUNDS]) tones?.startTone(ToneGenerator.TONE_PROP_BEEP, if (left <= 2) 250 else 120)
                delay(1_000)
            }
            _countdown.value = 0
            if (settings.value[Prefs.DRIVE] == DriveMode.BURST) {
                camera.startBurst()
                delay(1_500)
                camera.stopBurst()
            } else {
                camera.takePhoto()
            }
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _countdown.value = 0
        _messages.tryEmit("Self-timer cancelled")
    }

    // ------------------------------------------------------------------------------------------
    // Camera -> grip (the same three flags a Sony camera reports), thumbnails
    // ------------------------------------------------------------------------------------------

    private fun onCameraSignal(signal: CameraSignal) {
        when (signal) {
            is CameraSignal.FocusResult -> grip.setStatus(StatusField.FOCUS, signal.acquired)
            is CameraSignal.Shutter -> grip.setStatus(StatusField.SHUTTER, signal.open)
            is CameraSignal.Recording -> {
                grip.setStatus(StatusField.RECORDING, signal.active)
                if (!signal.active) refreshDeviceStatus()
            }
            is CameraSignal.Saved -> onSaved(signal.uri, signal.video)
            is CameraSignal.Message -> _messages.tryEmit(signal.text)
        }
    }

    private fun onSaved(uri: Uri, video: Boolean) {
        miscPrefs.edit().putString(KEY_LAST_URI, uri.toString()).putBoolean(KEY_LAST_VIDEO, video).apply()
        viewModelScope.launch {
            _lastCapture.value = LastCapture(uri, video, loadThumbnail(uri, video))
        }
    }

    private fun restoreLastCapture() {
        val uri = miscPrefs.getString(KEY_LAST_URI, null)?.let(Uri::parse) ?: return
        val video = miscPrefs.getBoolean(KEY_LAST_VIDEO, false)
        viewModelScope.launch {
            // If the file was deleted in the gallery, the thumbnail won't load: forget it.
            val thumb = loadThumbnail(uri, video) ?: return@launch
            _lastCapture.value = LastCapture(uri, video, thumb)
        }
    }

    private suspend fun loadThumbnail(uri: Uri, video: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.loadThumbnail(uri, Size(240, 240), null)
            } else if (video) {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(getApplication(), uri)
                        getFrameAtTime(0)
                    } finally {
                        release()
                    }
                }
            } else {
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 16 })
                }
            }
        }.getOrNull()
    }

    private fun refreshDeviceStatus() {
        val context = getApplication<Application>()
        val battery = runCatching {
            context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(-1)
        val free = runCatching {
            StatFs((context.getExternalFilesDir(null) ?: context.filesDir).path).availableBytes
        }.getOrDefault(-1L)
        _deviceStatus.value = DeviceStatus(battery, free)
    }

    private fun appendLog(line: String) {
        val stamped = "${timeFormat.format(Date())}  $line"
        _log.update { (it + stamped).takeLast(LOG_LINES) }
    }

    override fun onCleared() {
        grip.release()
        camera.release()
        tones?.release()
    }

    private companion object {
        const val LOG_LINES = 80
        const val C1_DOUBLE_PRESS_MS = 330L
        const val KEY_LAST_URI = "last_uri"
        const val KEY_LAST_VIDEO = "last_video"
    }
}
