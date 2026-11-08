package com.gripcam.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FocusUi { IDLE, FOCUSING, FOCUSED, FAILED }

data class CameraUiState(
    val ready: Boolean = false,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val canSwitchLens: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val recording: Boolean = false,
    val recordedNanos: Long = 0L,
    val hasFlashUnit: Boolean = false,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val torchOn: Boolean = false,
    val focus: FocusUi = FocusUi.IDLE,
    /** Where the focus reticle is drawn, in view fractions; null = centre. */
    val focusPoint: Pair<Float, Float>? = null,
    val manualFocus: Boolean = false,
    /** 0 = infinity, 1 = closest focus. */
    val manualFocusPosition: Float = 0f,
    /** Increments on every shutter release, to drive the on-screen flash. */
    val shutterTick: Int = 0,
    val error: String? = null,
)

/** One-off events the rest of the app reacts to (grip feedback, messages). */
sealed interface CameraSignal {
    data class FocusResult(val acquired: Boolean) : CameraSignal
    data class Shutter(val open: Boolean) : CameraSignal
    data class Recording(val active: Boolean) : CameraSignal
    data class Saved(val uri: Uri, val video: Boolean) : CameraSignal
    data class Message(val text: String) : CameraSignal
}

/**
 * Everything the grip can do to the phone camera, on top of CameraX.
 *
 * Tries to bind Preview + ImageCapture + VideoCapture together so PHOTO and REC both work
 * instantly (and stills can be taken while recording). Phones that cannot stream all three
 * fall back to two use cases and rebind when the other button is pressed.
 */
@OptIn(ExperimentalCamera2Interop::class)
class CameraController(context: Context) {

    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val _signals = MutableSharedFlow<CameraSignal>(extraBufferCapacity = 64)
    val signals: SharedFlow<CameraSignal> = _signals.asSharedFlow()

    var soundsEnabled = true

    private enum class Binding { NONE, COMBINED, PHOTO, VIDEO }

    @Volatile
    private var lastLensDistance: Float? = null

    private val preview: Preview = Preview.Builder().also { builder ->
        // Track the lens position AF settles on, so manual focus starts from there, not a jump.
        Camera2Interop.Extender(builder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.let { lastLensDistance = it }
            }
        })
    }.build()

    private val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .build()

    private val recorder: Recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
        )
        .build()
    private val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private var camera: Camera? = null
    private var binding = Binding.NONE
    private var combinedUnsupported = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var activeRecording: Recording? = null
    private var photosInFlight = 0
    private var focusRequest = 0
    private var zoomJob: Job? = null
    private var focusJob: Job? = null
    private var manualDiopters = 0f

    private val sound = MediaActionSound().apply {
        load(MediaActionSound.SHUTTER_CLICK)
        load(MediaActionSound.START_VIDEO_RECORDING)
        load(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    // ------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------

    fun attach(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        owner = lifecycleOwner
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val existing = provider
        if (existing != null) {
            bind(Binding.PHOTO)
            return
        }
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                provider = future.get()
                if (owner != null) bind(Binding.PHOTO)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Camera unavailable: ${e.message}") }
            }
        }, mainExecutor)
    }

    fun detach(lifecycleOwner: LifecycleOwner) {
        if (owner !== lifecycleOwner) return
        stopJogs()
        activeRecording?.stop()
        provider?.unbindAll()
        camera?.cameraInfo?.let {
            it.zoomState.removeObservers(lifecycleOwner)
            it.torchState.removeObservers(lifecycleOwner)
        }
        camera = null
        binding = Binding.NONE
        owner = null
        preview.setSurfaceProvider(null)
        _state.update { it.copy(ready = false) }
    }

    fun release() {
        stopJogs()
        activeRecording?.stop()
        scope.cancel()
        sound.release()
    }

    private fun bind(preferred: Binding) {
        val provider = provider ?: return
        val owner = owner ?: return
        camera?.cameraInfo?.let {
            it.zoomState.removeObservers(owner)
            it.torchState.removeObservers(owner)
        }
        stopJogs()
        exitManualFocus() // Camera2 overrides would otherwise survive the rebind
        manualDiopters = 0f

        val hasBack = provider.hasCameraSafe(CameraSelector.DEFAULT_BACK_CAMERA)
        val hasFront = provider.hasCameraSafe(CameraSelector.DEFAULT_FRONT_CAMERA)
        if (lensFacing == CameraSelector.LENS_FACING_BACK && !hasBack && hasFront) {
            lensFacing = CameraSelector.LENS_FACING_FRONT
        } else if (lensFacing == CameraSelector.LENS_FACING_FRONT && !hasFront && hasBack) {
            lensFacing = CameraSelector.LENS_FACING_BACK
        }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val attempts = buildList {
            if (!combinedUnsupported) add(Binding.COMBINED)
            add(if (preferred == Binding.VIDEO) Binding.VIDEO else Binding.PHOTO)
        }
        provider.unbindAll()
        camera = null
        binding = Binding.NONE
        var lastError: Exception? = null
        for (attempt in attempts) {
            try {
                camera = when (attempt) {
                    Binding.COMBINED -> provider.bindToLifecycle(owner, selector, preview, imageCapture, videoCapture)
                    Binding.VIDEO -> provider.bindToLifecycle(owner, selector, preview, videoCapture)
                    else -> provider.bindToLifecycle(owner, selector, preview, imageCapture)
                }
                binding = attempt
                break
            } catch (e: IllegalArgumentException) {
                if (attempt == Binding.COMBINED) combinedUnsupported = true
                lastError = e
            } catch (e: IllegalStateException) {
                lastError = e
            }
        }
        val camera = camera
        if (camera == null) {
            _state.update { it.copy(ready = false, error = "Could not start the camera: ${lastError?.message}") }
            return
        }

        val info = camera.cameraInfo
        info.zoomState.observe(owner) { z ->
            _state.update { it.copy(zoomRatio = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio) }
        }
        info.torchState.observe(owner) { t -> _state.update { it.copy(torchOn = t == TorchState.ON) } }
        _state.update {
            it.copy(
                ready = true,
                error = null,
                lensFacing = lensFacing,
                canSwitchLens = hasBack && hasFront,
                hasFlashUnit = info.hasFlashUnit(),
                manualFocus = false,
                focus = FocusUi.IDLE,
                focusPoint = null,
            )
        }
    }

    private fun ProcessCameraProvider.hasCameraSafe(selector: CameraSelector): Boolean = try {
        hasCamera(selector)
    } catch (_: Exception) {
        false
    }

    // ------------------------------------------------------------------------------------------
    // Photo
    // ------------------------------------------------------------------------------------------

    fun takePhoto() {
        if (camera == null) return
        if (binding == Binding.VIDEO) {
            if (activeRecording != null) {
                message("This phone can't take photos while recording video")
                return
            }
            bind(Binding.PHOTO)
            if (binding != Binding.PHOTO && binding != Binding.COMBINED) return
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "GripCam_${timestamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/GripCam")
            }
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            appContext.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()

        photosInFlight++
        _signals.tryEmit(CameraSignal.Shutter(true))
        _state.update { it.copy(shutterTick = it.shutterTick + 1) }
        if (soundsEnabled) sound.play(MediaActionSound.SHUTTER_CLICK)

        imageCapture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                photoDone()
                output.savedUri?.let { _signals.tryEmit(CameraSignal.Saved(it, video = false)) }
            }

            override fun onError(exception: ImageCaptureException) {
                photoDone()
                message("Photo failed: ${exception.message}")
            }
        })
    }

    private fun photoDone() {
        photosInFlight = (photosInFlight - 1).coerceAtLeast(0)
        if (photosInFlight == 0) _signals.tryEmit(CameraSignal.Shutter(false))
    }

    // ------------------------------------------------------------------------------------------
    // Video
    // ------------------------------------------------------------------------------------------

    fun toggleRecording() {
        if (activeRecording != null) stopRecording() else startRecording()
    }

    @SuppressLint("MissingPermission") // audio is only enabled after an explicit permission check
    private fun startRecording() {
        if (camera == null) return
        if (binding == Binding.PHOTO) {
            if (photosInFlight > 0) {
                message("Wait for the photo to save")
                return
            }
            bind(Binding.VIDEO)
        }
        if (binding != Binding.VIDEO && binding != Binding.COMBINED) return

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "GripCam_${timestamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/GripCam")
            }
        }
        val output = MediaStoreOutputOptions.Builder(
            appContext.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()

        var pending = recorder.prepareRecording(appContext, output)
        val audioAllowed = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (audioAllowed) pending = pending.withAudioEnabled()

        activeRecording = try {
            pending.start(mainExecutor) { event -> onRecordEvent(event) }
        } catch (e: IllegalStateException) {
            message("Could not start recording: ${e.message}")
            null
        }
    }

    private fun stopRecording() {
        activeRecording?.stop()
    }

    private fun onRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                _state.update { it.copy(recording = true, recordedNanos = 0L) }
                _signals.tryEmit(CameraSignal.Recording(true))
                if (soundsEnabled) sound.play(MediaActionSound.START_VIDEO_RECORDING)
            }

            is VideoRecordEvent.Status -> {
                val nanos = event.recordingStats.recordedDurationNanos
                _state.update { it.copy(recordedNanos = nanos) }
            }

            is VideoRecordEvent.Finalize -> {
                activeRecording = null
                _state.update { it.copy(recording = false) }
                _signals.tryEmit(CameraSignal.Recording(false))
                if (soundsEnabled) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                val uri = event.outputResults.outputUri
                if (uri != Uri.EMPTY) _signals.tryEmit(CameraSignal.Saved(uri, video = true))
                if (event.hasError() && uri == Uri.EMPTY) {
                    message("Recording failed (error ${event.error})")
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Focus
    // ------------------------------------------------------------------------------------------

    /** Half press / AF-ON: focus on the centre (or [point]) and hold it until [releaseFocus]. */
    fun startAutoFocus(point: MeteringPoint? = null, viewFraction: Pair<Float, Float>? = null) {
        val camera = camera ?: return
        exitManualFocus()
        val meteringPoint = point ?: SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        ).apply {
            if (point == null) disableAutoCancel() // a held half press keeps the lock
        }.build()
        val request = ++focusRequest
        _state.update { it.copy(focus = FocusUi.FOCUSING, focusPoint = viewFraction) }

        if (!camera.cameraInfo.isFocusMeteringSupported(action)) {
            // Fixed-focus lens: it is always "in focus" as far as the grip is concerned.
            _state.update { it.copy(focus = FocusUi.FOCUSED) }
            _signals.tryEmit(CameraSignal.FocusResult(true))
            return
        }
        val future = camera.cameraControl.startFocusAndMetering(action)
        future.addListener({
            if (request != focusRequest) return@addListener
            val acquired = try {
                future.get().isFocusSuccessful
            } catch (_: Exception) {
                false
            }
            _state.update { it.copy(focus = if (acquired) FocusUi.FOCUSED else FocusUi.FAILED) }
            _signals.tryEmit(CameraSignal.FocusResult(acquired))
            if (point != null) {
                // Tap-to-focus: fade the reticle out after a moment.
                scope.launch {
                    delay(1_200)
                    if (request == focusRequest) _state.update { it.copy(focus = FocusUi.IDLE) }
                }
            }
        }, mainExecutor)
    }

    /** Half press released. */
    fun releaseFocus() {
        focusRequest++
        if (!_state.value.manualFocus) camera?.cameraControl?.cancelFocusAndMetering()
        _state.update { it.copy(focus = FocusUi.IDLE) }
        _signals.tryEmit(CameraSignal.FocusResult(false))
    }

    /**
     * Continuous manual focus. direction +1 = nearer, -1 = farther, 0 = stop.
     * Uses Camera2 directly because CameraX has no manual-focus API.
     */
    fun focusJog(direction: Int, speed: Float) {
        focusJob?.cancel()
        focusJob = null
        if (direction == 0) return
        val camera = camera ?: return
        val maxDiopters = Camera2CameraInfo.from(camera.cameraInfo)
            .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        if (maxDiopters <= 0f) {
            message("This camera has a fixed-focus lens")
            return
        }
        if (!_state.value.manualFocus) {
            focusRequest++
            manualDiopters = (lastLensDistance ?: 0f).coerceIn(0f, maxDiopters)
            _state.update { it.copy(manualFocus = true, focus = FocusUi.IDLE) }
        }
        val fractionPerSecond = 0.12f + 0.88f * speed.coerceIn(0f, 1f)
        val control = Camera2CameraControl.from(camera.cameraControl)
        focusJob = scope.launch {
            var last = SystemClock.uptimeMillis()
            while (isActive) {
                val now = SystemClock.uptimeMillis()
                val dt = (now - last) / 1000f
                last = now
                manualDiopters = (manualDiopters + direction * fractionPerSecond * maxDiopters * dt)
                    .coerceIn(0f, maxDiopters)
                control.setCaptureRequestOptions(
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                        .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, manualDiopters)
                        .build(),
                )
                _state.update { it.copy(manualFocusPosition = manualDiopters / maxDiopters) }
                if (manualDiopters <= 0f && direction < 0 || manualDiopters >= maxDiopters && direction > 0) break
                delay(33)
            }
        }
    }

    private fun exitManualFocus() {
        if (!_state.value.manualFocus) return
        focusJob?.cancel()
        camera?.let { Camera2CameraControl.from(it.cameraControl).clearCaptureRequestOptions() }
        _state.update { it.copy(manualFocus = false) }
    }

    // ------------------------------------------------------------------------------------------
    // Zoom
    // ------------------------------------------------------------------------------------------

    /** Continuous zoom while the grip's lever is held. direction +1 tele, -1 wide, 0 stop. */
    fun zoomJog(direction: Int, speed: Float, multiplier: Float) {
        zoomJob?.cancel()
        zoomJob = null
        if (direction == 0) return
        val camera = camera ?: return
        var linear = camera.cameraInfo.zoomState.value?.linearZoom ?: 0f
        // Full wide-to-tele sweep in ~1.1 s at top speed, ~8 s at the slowest.
        val perSecond = (0.12f + 0.78f * speed.coerceIn(0f, 1f)) * multiplier
        zoomJob = scope.launch {
            var last = SystemClock.uptimeMillis()
            while (isActive) {
                delay(33)
                val now = SystemClock.uptimeMillis()
                linear = (linear + direction * perSecond * (now - last) / 1000f).coerceIn(0f, 1f)
                last = now
                camera.cameraControl.setLinearZoom(linear)
                if (linear <= 0f || linear >= 1f) break
            }
        }
    }

    /** Pinch on the viewfinder. */
    fun pinchZoom(scaleFactor: Float) {
        val camera = camera ?: return
        val z = camera.cameraInfo.zoomState.value ?: return
        camera.cameraControl.setZoomRatio((z.zoomRatio * scaleFactor).coerceIn(z.minZoomRatio, z.maxZoomRatio))
    }

    private fun stopJogs() {
        zoomJob?.cancel()
        focusJob?.cancel()
        zoomJob = null
        focusJob = null
    }

    // ------------------------------------------------------------------------------------------
    // C1 actions and misc
    // ------------------------------------------------------------------------------------------

    fun switchCamera() {
        if (activeRecording != null) {
            message("Stop recording to switch cameras")
            return
        }
        if (!_state.value.canSwitchLens) {
            message("This phone has only one camera")
            return
        }
        exitManualFocus()
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bind(if (binding == Binding.VIDEO) Binding.VIDEO else Binding.PHOTO)
    }

    fun toggleTorch() {
        val camera = camera ?: return
        if (!camera.cameraInfo.hasFlashUnit()) {
            message("No torch on this camera")
            return
        }
        camera.cameraControl.enableTorch(!_state.value.torchOn)
    }

    fun cycleFlash() {
        if (camera?.cameraInfo?.hasFlashUnit() != true) {
            message("No flash on this camera")
            return
        }
        val next = when (imageCapture.flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture.flashMode = next
        _state.update { it.copy(flashMode = next) }
    }

    /** Device orientation, so photos and videos come out upright when the grip is turned. */
    fun setTargetRotation(rotation: Int) {
        if (rotation !in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) return
        imageCapture.targetRotation = rotation
        // Rotation is fixed when a recording starts; changing it mid-recording is ignored anyway.
        if (activeRecording == null) videoCapture.targetRotation = rotation
    }

    private fun message(text: String) {
        _signals.tryEmit(CameraSignal.Message(text))
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}
