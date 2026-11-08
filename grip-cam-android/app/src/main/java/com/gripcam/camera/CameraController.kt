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
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.TorchState
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.gripcam.settings.AppSettings
import com.gripcam.settings.CaptureSpeed
import com.gripcam.settings.FrameAspect
import com.gripcam.settings.FrameRate
import com.gripcam.settings.PhotoEnhancement
import com.gripcam.settings.PhotoFormat
import com.gripcam.settings.Prefs
import com.gripcam.settings.Stabilization
import com.gripcam.settings.VideoBitrate
import com.gripcam.settings.VideoResolution
import com.gripcam.settings.WhiteBalance
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
import kotlin.math.min
import kotlin.math.pow

enum class FocusUi { IDLE, FOCUSING, FOCUSED, FAILED }

/** What the camera is actually running right now, after any step-down. Drives the HUD badges. */
data class ActiveSetup(
    val layout: Layout? = null,
    val videoLabel: String? = null,
    val fps: Int? = null,
    val hdr: Boolean = false,
    val stabilization: Stabilization = Stabilization.OFF,
    val photoFormat: PhotoFormat = PhotoFormat.JPEG,
    val enhancement: PhotoEnhancement = PhotoEnhancement.NONE,
)

/** The subset of settings the camera engine needs. */
data class CaptureSettings(
    val resolution: VideoResolution = VideoResolution.FHD,
    val frameRate: FrameRate = FrameRate.FPS_30,
    val stabilization: Stabilization = Stabilization.STANDARD,
    val hdrVideo: Boolean = false,
    val bitrate: VideoBitrate = VideoBitrate.STANDARD,
    val audio: Boolean = true,
    val lowLight: Boolean = false,
    val photoFormat: PhotoFormat = PhotoFormat.JPEG,
    val enhancement: PhotoEnhancement = PhotoEnhancement.NONE,
    val captureSpeed: CaptureSpeed = CaptureSpeed.FAST,
    val jpegQuality: Int = 95,
    val aspect: FrameAspect = FrameAspect.R16_9,
    val mirrorFront: Boolean = false,
) {
    /** Fields that only matter when a recording starts or can be changed live. */
    fun withoutLiveFields() = copy(audio = true, lowLight = false)

    fun features() = Features(
        stabilization = stabilization,
        hdrVideo = hdrVideo,
        fps = frameRate.fps,
        ultraHdr = photoFormat == PhotoFormat.ULTRA_HDR,
        raw = photoFormat == PhotoFormat.RAW_JPEG,
        enhancement = enhancement,
    )

    companion object {
        fun from(s: AppSettings) = CaptureSettings(
            resolution = s[Prefs.VIDEO_RESOLUTION],
            frameRate = s[Prefs.FRAME_RATE],
            stabilization = s[Prefs.STABILIZATION],
            hdrVideo = s[Prefs.HDR_VIDEO],
            bitrate = s[Prefs.BITRATE],
            audio = s[Prefs.RECORD_AUDIO],
            lowLight = s[Prefs.LOW_LIGHT],
            photoFormat = s[Prefs.PHOTO_FORMAT],
            enhancement = s[Prefs.PHOTO_MODE],
            captureSpeed = s[Prefs.CAPTURE_SPEED],
            jpegQuality = s[Prefs.JPEG_QUALITY].toInt(),
            aspect = s[Prefs.ASPECT],
            mirrorFront = s[Prefs.MIRROR_FRONT],
        )
    }
}

data class CameraUiState(
    val ready: Boolean = false,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val canSwitchLens: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val recording: Boolean = false,
    val recordedNanos: Long = 0L,
    val recordedBytes: Long = 0L,
    /** Microphone level 0..1 while recording. */
    val audioLevel: Float = 0f,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val torchOn: Boolean = false,
    val focus: FocusUi = FocusUi.IDLE,
    /** Where the focus reticle is drawn, in frame fractions; null = centre. */
    val focusPoint: Pair<Float, Float>? = null,
    val aeAfLocked: Boolean = false,
    val manualFocus: Boolean = false,
    /** 0 = infinity, 1 = closest focus. */
    val manualFocusPosition: Float = 0f,
    val exposureIndex: Int = 0,
    val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
    /** Increments on every shutter release, to drive the on-screen flash. */
    val shutterTick: Int = 0,
    val burstCount: Int = 0,
    val capabilities: CameraCapabilities = CameraCapabilities.UNKNOWN,
    val active: ActiveSetup = ActiveSetup(),
    val aspect: FrameAspect = FrameAspect.R16_9,
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
 * Everything the grip and the screen can do to the phone camera, on top of CameraX 1.6.
 *
 * Binding: each time the camera (re)starts, [BindPlanner] lists combinations from "everything
 * the user asked for, photo + video together" down to "plain photo or plain video", and the
 * first one `CameraInfo.isSessionConfigSupported` accepts is bound. What actually runs is
 * published as [CameraUiState.active], and step-downs are announced.
 *
 * Recording is always persistent, so the camera can be switched mid-recording (C1 by default):
 * the same VideoCapture is rebound to the other lens and the file carries on.
 */
@OptIn(ExperimentalCamera2Interop::class, ExperimentalPersistentRecording::class, ExperimentalZeroShutterLag::class)
class CameraController(context: Context) {

    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val _signals = MutableSharedFlow<CameraSignal>(extraBufferCapacity = 64)
    val signals: SharedFlow<CameraSignal> = _signals.asSharedFlow()

    var soundsEnabled = true

    private var settings = CaptureSettings()

    @Volatile
    private var lastLensDistance: Float? = null
    private val lensDistanceCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.let { lastLensDistance = it }
        }
    }

    // ---- bound state ----
    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var owner: LifecycleOwner? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var active = ActiveSetup()
    private var mode = Layout.PHOTO
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var boundFacing = -1
    private var rebindAfterRecording = false

    // ---- activity ----
    private var activeRecording: Recording? = null
    private var photosInFlight = 0
    private var burstJob: Job? = null
    private var focusRequest = 0
    private var zoomJob: Job? = null
    private var focusJob: Job? = null
    private var zoomBeforeJog = 1f
    private var lastJogEndedAt = 0L
    private var manualDiopters = 0f
    private var torchWanted = false

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            // The camera stops when we leave the screen; end the file cleanly rather than
            // leaving a persistent recording waiting for frames.
            stopBurst()
            activeRecording?.stop()
        }
    }

    private val sound = MediaActionSound().apply {
        load(MediaActionSound.SHUTTER_CLICK)
        load(MediaActionSound.START_VIDEO_RECORDING)
        load(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    // ------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------

    fun attach(lifecycleOwner: LifecycleOwner, previewSurface: Preview.SurfaceProvider) {
        owner?.lifecycle?.removeObserver(lifecycleObserver)
        owner = lifecycleOwner
        surfaceProvider = previewSurface
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (provider != null) {
            bindBest()
            return
        }
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                provider = future.get()
                initExtensions()
                if (owner != null) bindBest()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Camera unavailable: ${e.message}") }
            }
        }, mainExecutor)
    }

    fun detach(lifecycleOwner: LifecycleOwner) {
        if (owner !== lifecycleOwner) return
        stopJogs()
        stopBurst()
        activeRecording?.stop()
        provider?.unbindAll()
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        camera = null
        owner = null
        surfaceProvider = null
        _state.update { it.copy(ready = false) }
    }

    fun release() {
        stopJogs()
        stopBurst()
        activeRecording?.stop()
        scope.cancel()
        sound.release()
    }

    /** New settings from the user. Rebinds only when needed, and never mid-recording. */
    fun applySettings(next: CaptureSettings) {
        val previous = settings
        settings = next
        _state.update { it.copy(aspect = next.aspect) }
        if (previous.withoutLiveFields() != next.withoutLiveFields()) {
            if (activeRecording != null) {
                rebindAfterRecording = true
                message("New video settings apply when this recording stops")
            } else {
                bindBest()
            }
        } else if (previous.lowLight != next.lowLight) {
            applyLowLight()
        }
    }

    private fun initExtensions() {
        val provider = provider ?: return
        val future = ExtensionsManager.getInstanceAsync(appContext, provider)
        future.addListener({
            extensions = try {
                future.get()
            } catch (_: Exception) {
                null
            }
            refreshCapabilities()
            if (settings.enhancement != PhotoEnhancement.NONE && activeRecording == null) bindBest()
        }, mainExecutor)
    }

    // ------------------------------------------------------------------------------------------
    // Binding
    // ------------------------------------------------------------------------------------------

    private fun baseSelector(facing: Int = lensFacing) = CameraSelector.Builder().requireLensFacing(facing).build()

    private fun selectorFor(features: Features): CameraSelector {
        val base = baseSelector()
        val ext = extensions
        val modeId = features.enhancement.extensionMode
        return if (features.enhancement != PhotoEnhancement.NONE && ext != null && ext.isExtensionAvailable(base, modeId)) {
            ext.getExtensionEnabledCameraSelector(base, modeId)
        } else {
            base
        }
    }

    private fun aspectSelector(highest: Boolean): ResolutionSelector =
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy(settings.aspect.cameraX, AspectRatioStrategy.FALLBACK_RULE_AUTO))
            .apply { if (highest) setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY) }
            .build()

    private fun buildPreview(f: Features): Preview = Preview.Builder()
        .setResolutionSelector(aspectSelector(highest = false))
        .apply {
            when (f.stabilization) {
                Stabilization.STEADY -> setPreviewStabilizationEnabled(true)
                Stabilization.OFF -> setPreviewStabilizationEnabled(false)
                Stabilization.STANDARD -> Unit
            }
            Camera2Interop.Extender(this).setSessionCaptureCallback(lensDistanceCallback)
        }
        .build()
        .also { it.setSurfaceProvider(surfaceProvider) }

    private fun buildImageCapture(f: Features): ImageCapture = ImageCapture.Builder()
        .setCaptureMode(
            when (settings.captureSpeed) {
                CaptureSpeed.BEST -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                CaptureSpeed.FAST -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                CaptureSpeed.ZERO_LAG -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
            },
        )
        .setJpegQuality(settings.jpegQuality.coerceIn(1, 100))
        .setFlashMode(_state.value.flashMode)
        .setTargetRotation(targetRotation)
        .setResolutionSelector(aspectSelector(highest = true))
        .setOutputFormat(
            when {
                f.raw -> ImageCapture.OUTPUT_FORMAT_RAW_JPEG
                f.ultraHdr -> ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR
                else -> ImageCapture.OUTPUT_FORMAT_JPEG
            },
        )
        .build()

    private fun buildVideoCapture(f: Features): VideoCapture<Recorder> {
        val quality = when (settings.resolution) {
            VideoResolution.UHD -> Quality.UHD
            VideoResolution.FHD -> Quality.FHD
            VideoResolution.HD -> Quality.HD
            VideoResolution.SD -> Quality.SD
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .setAspectRatio(settings.aspect.cameraX)
            .apply {
                VideoMath.targetBitrate(settings.resolution, f.fps ?: 30, settings.bitrate)?.let {
                    setTargetVideoEncodingBitRate(it)
                }
            }
            .build()
        return VideoCapture.Builder(recorder)
            .apply {
                when (f.stabilization) {
                    Stabilization.STANDARD -> setVideoStabilizationEnabled(true)
                    Stabilization.OFF -> setVideoStabilizationEnabled(false)
                    Stabilization.STEADY -> Unit // preview stabilization covers the video stream
                }
            }
            .setDynamicRange(if (f.hdrVideo) DynamicRange.HLG_10_BIT else DynamicRange.SDR)
            .setMirrorMode(if (settings.mirrorFront) MirrorMode.MIRROR_MODE_ON_FRONT_ONLY else MirrorMode.MIRROR_MODE_OFF)
            .setTargetRotation(targetRotation)
            .build()
    }

    private fun sessionConfig(useCases: List<UseCase>, fps: Int?): SessionConfig =
        SessionConfig.Builder(useCases)
            .apply { if (fps != null) setFrameRateRange(Range(fps, fps)) }
            .build()

    /** Binds the best combination for [mode] and the current settings. Not used mid-recording. */
    private fun bindBest() {
        val provider = provider ?: return
        val owner = owner ?: return
        if (surfaceProvider == null || activeRecording != null) return
        stopJogs()
        // Keep the zoom when only the settings or photo/video mode change, not the lens.
        val keepZoom = camera?.takeIf { boundFacing == lensFacing }?.cameraInfo?.zoomState?.value?.zoomRatio

        fixLensFacing(provider)
        val desired = settings.features()
        val candidates = BindPlanner.plan(desired, mode)
        var bound: BindCandidate? = null

        // Pass 1: only what the camera says it supports. Pass 2: try anyway, in case the
        // support check is stricter than the device (seen on some older phones).
        for (pass in 0..1) {
            for (candidate in candidates) {
                if (tryBind(provider, owner, candidate, checkSupport = pass == 0)) {
                    bound = candidate
                    break
                }
            }
            if (bound != null) break
        }
        if (bound == null) {
            _state.update { it.copy(ready = false, error = "Could not start the camera") }
            return
        }
        boundFacing = lensFacing
        announceStepDown(desired, bound)
        afterBind()
        if (keepZoom != null && keepZoom != 1f) camera?.cameraControl?.setZoomRatio(keepZoom)
    }

    private fun tryBind(provider: ProcessCameraProvider, owner: LifecycleOwner, candidate: BindCandidate, checkSupport: Boolean): Boolean {
        val f = candidate.features
        val selector = selectorFor(f)
        val newPreview = buildPreview(f)
        val newImage = if (candidate.layout.hasPhoto) buildImageCapture(f) else null
        val newVideo = if (candidate.layout.hasVideo) buildVideoCapture(f) else null
        val useCases = listOfNotNull(newPreview, newImage, newVideo)
        val config = sessionConfig(useCases, if (candidate.layout.hasVideo) f.fps else null)
        if (checkSupport) {
            val supported = try {
                provider.getCameraInfo(selector).isSessionConfigSupported(config)
            } catch (_: Exception) {
                false
            }
            if (!supported) return false
        }
        return try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(owner, selector, config)
            preview = newPreview
            imageCapture = newImage
            videoCapture = newVideo
            active = ActiveSetup(
                layout = candidate.layout,
                videoLabel = newVideo?.let { videoLabel(it) },
                fps = if (candidate.layout.hasVideo) f.fps else null,
                hdr = candidate.layout.hasVideo && f.hdrVideo,
                stabilization = f.stabilization,
                photoFormat = when {
                    !candidate.layout.hasPhoto -> settings.photoFormat
                    f.raw -> PhotoFormat.RAW_JPEG
                    f.ultraHdr -> PhotoFormat.ULTRA_HDR
                    else -> PhotoFormat.JPEG
                },
                enhancement = if (selector != baseSelector()) f.enhancement else PhotoEnhancement.NONE,
            )
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    /** Tells the user when the phone couldn't do everything they asked for. */
    private fun announceStepDown(desired: Features, bound: BindCandidate) {
        val got = bound.features
        val lost = buildList {
            if (bound.layout.hasVideo) {
                if (desired.hdrVideo && !got.hdrVideo) add("HDR video")
                if ((desired.fps ?: 30) > 30 && (got.fps ?: 30) <= 30) add("${desired.fps} fps")
                if (desired.stabilization != got.stabilization && desired.stabilization != Stabilization.OFF) {
                    add("${desired.stabilization.label} stabilization")
                }
            }
            if (bound.layout.hasPhoto) {
                if (desired.raw && !got.raw) add("RAW")
                if (desired.ultraHdr && !got.ultraHdr) add("Ultra HDR")
                if (desired.enhancement != PhotoEnhancement.NONE && active.enhancement == PhotoEnhancement.NONE) {
                    add("${desired.enhancement.label} mode")
                }
            }
        }
        if (lost.isNotEmpty()) message("Not available together on this camera: ${lost.joinToString()}")
    }

    private fun afterBind() {
        val camera = camera ?: return
        val owner = owner ?: return
        val info = camera.cameraInfo
        info.zoomState.removeObservers(owner)
        info.torchState.removeObservers(owner)
        info.zoomState.observe(owner) { z ->
            _state.update { it.copy(zoomRatio = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio) }
        }
        info.torchState.observe(owner) { t -> _state.update { it.copy(torchOn = t == TorchState.ON) } }
        manualDiopters = 0f
        val caps = computeCapabilities(info)
        val exposure = _state.value.exposureIndex.coerceIn(caps.exposureMin, caps.exposureMax)
        _state.update {
            it.copy(
                ready = true,
                error = null,
                lensFacing = lensFacing,
                canSwitchLens = hasLens(CameraSelector.LENS_FACING_BACK) && hasLens(CameraSelector.LENS_FACING_FRONT),
                manualFocus = false,
                aeAfLocked = false,
                focus = FocusUi.IDLE,
                focusPoint = null,
                exposureIndex = exposure,
                whiteBalance = if (it.whiteBalance in caps.whiteBalance) it.whiteBalance else WhiteBalance.AUTO,
                capabilities = caps,
                active = active,
                aspect = settings.aspect,
            )
        }
        if (exposure != 0) camera.cameraControl.setExposureCompensationIndex(exposure)
        if (torchWanted && info.hasFlashUnit()) camera.cameraControl.enableTorch(true)
        applyCamera2Overrides()
        applyLowLight()
    }

    private fun fixLensFacing(provider: ProcessCameraProvider) {
        val hasBack = hasLens(CameraSelector.LENS_FACING_BACK, provider)
        val hasFront = hasLens(CameraSelector.LENS_FACING_FRONT, provider)
        if (lensFacing == CameraSelector.LENS_FACING_BACK && !hasBack && hasFront) lensFacing = CameraSelector.LENS_FACING_FRONT
        if (lensFacing == CameraSelector.LENS_FACING_FRONT && !hasFront && hasBack) lensFacing = CameraSelector.LENS_FACING_BACK
    }

    private fun hasLens(facing: Int, provider: ProcessCameraProvider? = this.provider): Boolean = try {
        provider?.hasCamera(baseSelector(facing)) == true
    } catch (_: Exception) {
        false
    }

    private fun videoLabel(vc: VideoCapture<Recorder>): String? {
        val size: Size = vc.resolutionInfo?.resolution ?: return settings.resolution.label
        val short = min(size.width, size.height)
        return when {
            short >= 2160 -> "4K"
            short >= 1080 -> "1080p"
            short >= 720 -> "720p"
            else -> "${short}p"
        }
    }

    private fun refreshCapabilities() {
        val camera = camera ?: return
        _state.update { it.copy(capabilities = computeCapabilities(camera.cameraInfo)) }
    }

    private fun computeCapabilities(boundInfo: CameraInfo): CameraCapabilities {
        val info = try {
            provider?.getCameraInfo(baseSelector()) ?: boundInfo
        } catch (_: Exception) {
            boundInfo
        }
        val videoCaps = try {
            Recorder.getVideoCapabilities(info)
        } catch (_: Exception) {
            null
        }
        val qualities = videoCaps?.getSupportedQualities(DynamicRange.SDR).orEmpty()
        val resolutions = buildSet {
            if (Quality.UHD in qualities) add(VideoResolution.UHD)
            if (Quality.FHD in qualities) add(VideoResolution.FHD)
            if (Quality.HD in qualities) add(VideoResolution.HD)
            if (Quality.SD in qualities) add(VideoResolution.SD)
            if (isEmpty()) add(VideoResolution.FHD)
        }
        val fpsRanges = try {
            info.supportedFrameRateRanges
        } catch (_: Exception) {
            emptySet()
        }
        val frameRates = FrameRate.entries.filter { fr -> fpsRanges.any { it.lower == fr.fps && it.upper == fr.fps } }.toSet()
        val formats = try {
            ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats
        } catch (_: Exception) {
            emptySet()
        }
        val photoFormats = buildSet {
            add(PhotoFormat.JPEG)
            if (ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR in formats) add(PhotoFormat.ULTRA_HDR)
            if (ImageCapture.OUTPUT_FORMAT_RAW_JPEG in formats) add(PhotoFormat.RAW_JPEG)
        }
        val ext = extensions
        val enhancements = PhotoEnhancement.entries.filter {
            it == PhotoEnhancement.NONE || (ext != null && runCatching { ext.isExtensionAvailable(baseSelector(), it.extensionMode) }.getOrDefault(false))
        }.toSet()
        val c2 = runCatching { Camera2CameraInfo.from(boundInfo) }.getOrNull()
        val awbModes = c2?.let { runCatching { it.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) }.getOrNull() }
        val whiteBalance = WhiteBalance.entries.filter { it == WhiteBalance.AUTO || awbModes?.contains(it.camera2Mode) == true }.toSet()
        val minFocus = c2?.let { runCatching { it.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) }.getOrNull() } ?: 0f
        val exposure = boundInfo.exposureState
        return CameraCapabilities(
            known = true,
            videoResolutions = resolutions,
            hdrVideo = videoCaps?.supportedDynamicRanges?.contains(DynamicRange.HLG_10_BIT) == true,
            frameRates = frameRates,
            videoStabilization = videoCaps?.isStabilizationSupported == true,
            previewStabilization = runCatching { Preview.getPreviewCapabilities(info).isStabilizationSupported }.getOrDefault(false),
            photoFormats = photoFormats,
            enhancements = enhancements,
            zeroShutterLag = runCatching { info.isZslSupported }.getOrDefault(false),
            lowLightBoost = runCatching { info.isLowLightBoostSupported }.getOrDefault(false),
            flash = boundInfo.hasFlashUnit(),
            exposureMin = if (exposure.isExposureCompensationSupported) exposure.exposureCompensationRange.lower else 0,
            exposureMax = if (exposure.isExposureCompensationSupported) exposure.exposureCompensationRange.upper else 0,
            exposureStep = if (exposure.isExposureCompensationSupported) exposure.exposureCompensationStep.toFloat() else 0f,
            whiteBalance = whiteBalance,
            maxFocusDiopters = minFocus,
        )
    }

    // ------------------------------------------------------------------------------------------
    // Photo
    // ------------------------------------------------------------------------------------------

    fun takePhoto() {
        if (camera == null) return
        if (imageCapture == null) {
            if (activeRecording != null) {
                message("This phone can't take photos while recording with these video settings")
                return
            }
            mode = Layout.PHOTO
            bindBest()
        }
        val capture = imageCapture ?: return
        val raw = active.photoFormat == PhotoFormat.RAW_JPEG && active.layout?.hasPhoto == true
        if (raw && photosInFlight > 0) return // RAW is slow; one at a time
        val base = "GripCam_${timestamp()}"
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = settings.mirrorFront && lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        val jpeg = outputOptions(base, "image/jpeg", metadata)

        photosInFlight++
        _signals.tryEmit(CameraSignal.Shutter(true))
        _state.update { it.copy(shutterTick = it.shutterTick + 1) }
        if (soundsEnabled) sound.play(MediaActionSound.SHUTTER_CLICK)

        var pendingOutputs = if (raw) 2 else 1
        val callback = object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                if (pendingOutputs <= 0) return
                output.savedUri?.let { uri ->
                    // Show the JPEG, not the DNG, in the gallery thumbnail.
                    if (!raw || appContext.contentResolver.getType(uri)?.contains("jpeg") != false) {
                        _signals.tryEmit(CameraSignal.Saved(uri, video = false))
                    }
                }
                if (--pendingOutputs == 0) photoDone()
            }

            override fun onError(exception: ImageCaptureException) {
                if (pendingOutputs <= 0) return
                pendingOutputs = 0
                photoDone()
                message("Photo failed: ${exception.message}")
            }
        }
        try {
            if (raw) {
                capture.takePicture(outputOptions(base, "image/x-adobe-dng", metadata), jpeg, mainExecutor, callback)
            } else {
                capture.takePicture(jpeg, mainExecutor, callback)
            }
        } catch (e: Exception) {
            callback.onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, e.message ?: "capture failed", e))
        }
    }

    private fun outputOptions(baseName: String, mime: String, metadata: ImageCapture.Metadata): ImageCapture.OutputFileOptions {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, baseName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/GripCam")
        }
        return ImageCapture.OutputFileOptions.Builder(
            appContext.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).setMetadata(metadata).build()
    }

    private fun photoDone() {
        photosInFlight = (photosInFlight - 1).coerceAtLeast(0)
        if (photosInFlight == 0) _signals.tryEmit(CameraSignal.Shutter(false))
    }

    /** Continuous shooting while the shutter is held. */
    fun startBurst() {
        if (burstJob?.isActive == true) return
        _state.update { it.copy(burstCount = 0) }
        burstJob = scope.launch {
            var shots = 0
            while (isActive && shots < MAX_BURST) {
                val limit = if (active.photoFormat == PhotoFormat.RAW_JPEG) 1 else 2
                if (photosInFlight < limit) {
                    takePhoto()
                    shots++
                    _state.update { it.copy(burstCount = shots) }
                }
                delay(BURST_INTERVAL_MS)
            }
        }
    }

    fun stopBurst() {
        burstJob?.cancel()
        burstJob = null
        if (_state.value.burstCount > 0) {
            scope.launch {
                delay(1_200)
                if (burstJob == null) _state.update { it.copy(burstCount = 0) }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Video
    // ------------------------------------------------------------------------------------------

    fun toggleRecording() {
        if (activeRecording != null) activeRecording?.stop() else startRecording()
    }

    @SuppressLint("MissingPermission") // audio is only enabled after an explicit permission check
    private fun startRecording() {
        if (camera == null) return
        if (videoCapture == null) {
            if (photosInFlight > 0) {
                message("Wait for the photo to save")
                return
            }
            mode = Layout.VIDEO
            bindBest()
        }
        val vc = videoCapture ?: return
        vc.targetRotation = targetRotation

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "GripCam_${timestamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/GripCam")
        }
        val output = MediaStoreOutputOptions.Builder(appContext.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()

        // Persistent: survives rebinding, which is what lets C1 flip the camera mid-recording.
        var pending = vc.output.prepareRecording(appContext, output).asPersistentRecording()
        val audioAllowed = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (settings.audio && audioAllowed) pending = pending.withAudioEnabled()

        activeRecording = try {
            pending.start(mainExecutor) { event -> onRecordEvent(event) }
        } catch (e: IllegalStateException) {
            message("Could not start recording: ${e.message}")
            null
        }
    }

    private fun onRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                _state.update { it.copy(recording = true, recordedNanos = 0L, recordedBytes = 0L, audioLevel = 0f) }
                _signals.tryEmit(CameraSignal.Recording(true))
                if (soundsEnabled) sound.play(MediaActionSound.START_VIDEO_RECORDING)
            }

            is VideoRecordEvent.Status -> {
                val stats = event.recordingStats
                val amplitude = stats.audioStats.audioAmplitude
                _state.update {
                    it.copy(
                        recordedNanos = stats.recordedDurationNanos,
                        recordedBytes = stats.numBytesRecorded,
                        audioLevel = if (amplitude.isNaN() || amplitude < 0) 0f else amplitude.toFloat().coerceIn(0f, 1f),
                    )
                }
            }

            is VideoRecordEvent.Finalize -> {
                activeRecording = null
                _state.update { it.copy(recording = false, audioLevel = 0f) }
                _signals.tryEmit(CameraSignal.Recording(false))
                if (soundsEnabled) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                val uri = event.outputResults.outputUri
                if (uri != Uri.EMPTY) _signals.tryEmit(CameraSignal.Saved(uri, video = true))
                if (event.hasError() && uri == Uri.EMPTY) message("Recording failed (error ${event.error})")
                if (rebindAfterRecording) {
                    rebindAfterRecording = false
                    bindBest()
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Switching cameras, including mid-recording
    // ------------------------------------------------------------------------------------------

    fun switchCamera() {
        if (!_state.value.canSwitchLens) {
            message("This phone has only one camera")
            return
        }
        val next = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        exitManualFocus()
        if (activeRecording == null) {
            lensFacing = next
            bindBest()
        } else {
            switchWhileRecording(next)
        }
    }

    /**
     * Rebinds the *same* VideoCapture (and so the same persistent recording) to the other lens.
     * The file continues; the switch costs a fraction of a second of footage.
     */
    private fun switchWhileRecording(next: Int) {
        val provider = provider ?: return
        val owner = owner ?: return
        val video = videoCapture ?: return
        val previous = lensFacing
        val features = Features(stabilization = active.stabilization, hdrVideo = active.hdr, fps = active.fps)

        fun attempt(facing: Int): Boolean {
            val selector = baseSelector(facing)
            val photo = imageCapture
            val plans = buildList {
                val p = preview
                if (p != null && photo != null) add(listOf(p, photo, video))
                if (p != null) add(listOf(p, video))
                // A fresh, unstabilized preview, in case the other lens can't stabilize it.
                add(listOf(buildPreview(features.copy(stabilization = Stabilization.OFF)), video))
            }
            for (useCases in plans) {
                val config = sessionConfig(useCases, active.fps)
                val supported = runCatching { provider.getCameraInfo(selector).isSessionConfigSupported(config) }.getOrDefault(false)
                if (!supported) continue
                try {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(owner, selector, config)
                    val freshPreview = useCases.first() !== preview
                    preview = useCases.first() as Preview
                    imageCapture = useCases.firstOrNull { it is ImageCapture } as ImageCapture?
                    active = active.copy(
                        layout = if (imageCapture != null) Layout.COMBINED else Layout.VIDEO,
                        // Steady lives in the preview; a fresh unstabilized preview means none.
                        stabilization = if (freshPreview && active.stabilization == Stabilization.STEADY) {
                            Stabilization.OFF
                        } else {
                            active.stabilization
                        },
                    )
                    lensFacing = facing
                    boundFacing = facing
                    return true
                } catch (_: Exception) {
                    // try the next plan
                }
            }
            return false
        }

        if (attempt(next)) {
            afterBind()
        } else {
            message("The other camera can't continue this recording with the current video settings")
            if (attempt(previous)) afterBind()
        }
    }

    // ------------------------------------------------------------------------------------------
    // Focus and exposure
    // ------------------------------------------------------------------------------------------

    /** Half press / AF-ON / tap: focus on the centre (or [point]) and hold it until [releaseFocus]. */
    fun startAutoFocus(point: MeteringPoint? = null, viewFraction: Pair<Float, Float>? = null) {
        val camera = camera ?: return
        if (_state.value.aeAfLocked && point == null) {
            // A locked focus wins over the grip's half press.
            _signals.tryEmit(CameraSignal.FocusResult(true))
            return
        }
        exitManualFocus()
        _state.update { it.copy(aeAfLocked = false) }
        val meteringPoint = point ?: SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(meteringPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .apply { if (point == null) disableAutoCancel() }
            .build()
        val request = ++focusRequest
        _state.update { it.copy(focus = FocusUi.FOCUSING, focusPoint = viewFraction) }
        if (!camera.cameraInfo.isFocusMeteringSupported(action)) {
            _state.update { it.copy(focus = FocusUi.FOCUSED) }
            _signals.tryEmit(CameraSignal.FocusResult(true))
            return
        }
        val future = camera.cameraControl.startFocusAndMetering(action)
        future.addListener({
            if (request != focusRequest) return@addListener
            val acquired = runCatching { future.get().isFocusSuccessful }.getOrDefault(false)
            _state.update { it.copy(focus = if (acquired) FocusUi.FOCUSED else FocusUi.FAILED) }
            _signals.tryEmit(CameraSignal.FocusResult(acquired))
            if (point != null) {
                scope.launch {
                    delay(1_200)
                    if (request == focusRequest) _state.update { it.copy(focus = FocusUi.IDLE) }
                }
            }
        }, mainExecutor)
    }

    /** Half press released. */
    fun releaseFocus() {
        if (_state.value.aeAfLocked) return
        focusRequest++
        if (!_state.value.manualFocus) camera?.cameraControl?.cancelFocusAndMetering()
        _state.update { it.copy(focus = FocusUi.IDLE) }
        _signals.tryEmit(CameraSignal.FocusResult(false))
    }

    /** Long press / C1: lock focus and exposure at [point] (default centre) until unlocked. */
    fun lockAeAf(point: MeteringPoint? = null, viewFraction: Pair<Float, Float>? = null) {
        val camera = camera ?: return
        exitManualFocus()
        val meteringPoint = point ?: SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB,
        ).disableAutoCancel().build()
        val request = ++focusRequest
        _state.update { it.copy(aeAfLocked = true, focus = FocusUi.FOCUSING, focusPoint = viewFraction) }
        val future = camera.cameraControl.startFocusAndMetering(action)
        future.addListener({
            if (request != focusRequest) return@addListener
            val acquired = runCatching { future.get().isFocusSuccessful }.getOrDefault(false)
            _state.update { it.copy(focus = if (acquired) FocusUi.FOCUSED else FocusUi.FAILED) }
        }, mainExecutor)
    }

    fun unlockAeAf() {
        if (!_state.value.aeAfLocked) return
        focusRequest++
        camera?.cameraControl?.cancelFocusAndMetering()
        _state.update { it.copy(aeAfLocked = false, focus = FocusUi.IDLE, focusPoint = null) }
    }

    fun toggleAeAfLock() = if (_state.value.aeAfLocked) unlockAeAf() else lockAeAf()

    fun setExposureIndex(index: Int) {
        val camera = camera ?: return
        val caps = _state.value.capabilities
        if (!caps.exposureSupported) return
        val clamped = index.coerceIn(caps.exposureMin, caps.exposureMax)
        _state.update { it.copy(exposureIndex = clamped) }
        camera.cameraControl.setExposureCompensationIndex(clamped)
    }

    fun setWhiteBalance(wb: WhiteBalance) {
        _state.update { it.copy(whiteBalance = wb) }
        applyCamera2Overrides()
    }

    fun cycleWhiteBalance() {
        val available = WhiteBalance.entries.filter { it in _state.value.capabilities.whiteBalance }
        if (available.size <= 1) {
            message("White balance presets aren't available on this camera")
            return
        }
        val next = available[(available.indexOf(_state.value.whiteBalance) + 1) % available.size]
        setWhiteBalance(next)
    }

    /**
     * Continuous manual focus. direction +1 = nearer, -1 = farther, 0 = stop.
     * Uses Camera2 directly because CameraX has no manual-focus API.
     */
    fun focusJog(direction: Int, speed: Float) {
        focusJob?.cancel()
        focusJob = null
        if (direction == 0) return
        val maxDiopters = _state.value.capabilities.maxFocusDiopters
        if (camera == null || maxDiopters <= 0f) {
            message("This camera has a fixed-focus lens")
            return
        }
        if (!_state.value.manualFocus) {
            focusRequest++
            manualDiopters = (lastLensDistance ?: 0f).coerceIn(0f, maxDiopters)
            _state.update { it.copy(manualFocus = true, aeAfLocked = false, focus = FocusUi.IDLE) }
        }
        val fractionPerSecond = 0.12f + 0.88f * speed.coerceIn(0f, 1f)
        focusJob = scope.launch {
            var last = SystemClock.uptimeMillis()
            while (isActive) {
                val now = SystemClock.uptimeMillis()
                manualDiopters = (manualDiopters + direction * fractionPerSecond * maxDiopters * (now - last) / 1000f)
                    .coerceIn(0f, maxDiopters)
                last = now
                applyCamera2Overrides()
                _state.update { it.copy(manualFocusPosition = manualDiopters / maxDiopters) }
                if ((manualDiopters <= 0f && direction < 0) || (manualDiopters >= maxDiopters && direction > 0)) break
                delay(33)
            }
        }
    }

    private fun exitManualFocus() {
        if (!_state.value.manualFocus) return
        focusJob?.cancel()
        _state.update { it.copy(manualFocus = false) }
        applyCamera2Overrides()
    }

    /** Camera2 overrides replace each other, so manual focus and white balance are sent together. */
    private fun applyCamera2Overrides() {
        val camera = camera ?: return
        val s = _state.value
        val options = CaptureRequestOptions.Builder().apply {
            if (s.manualFocus) {
                setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, manualDiopters)
            }
            if (s.whiteBalance != WhiteBalance.AUTO) {
                setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, s.whiteBalance.camera2Mode)
            }
        }.build()
        runCatching { Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(options) }
    }

    private fun applyLowLight() {
        val camera = camera ?: return
        if (!_state.value.capabilities.lowLightBoost) return
        val future = camera.cameraControl.enableLowLightBoostAsync(settings.lowLight)
        future.addListener({
            if (runCatching { future.get() }.isFailure && settings.lowLight) {
                message("Low-light boost isn't available with the current video settings")
            }
        }, mainExecutor)
    }

    // ------------------------------------------------------------------------------------------
    // Zoom
    // ------------------------------------------------------------------------------------------

    /** Continuous zoom while the grip's lever is held. direction +1 tele, -1 wide, 0 stop. */
    fun zoomJog(direction: Int, speed: Float, multiplier: Float) {
        val wasJogging = zoomJob?.isActive == true
        zoomJob?.cancel()
        zoomJob = null
        val camera = camera ?: return
        if (direction == 0) {
            lastJogEndedAt = SystemClock.uptimeMillis()
            return
        }
        val zoom = camera.cameraInfo.zoomState.value
        if (!wasJogging) zoomBeforeJog = zoom?.zoomRatio ?: 1f
        var linear = zoom?.linearZoom ?: 0f
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

    /**
     * Grip double press: jump to the next stop. Measured from where the zoom was before the
     * first tap of the double press, so the tap's own small zoom doesn't skip a stop.
     */
    fun snapZoom(direction: Int, preset: List<Float>) {
        val camera = camera ?: return
        zoomJob?.cancel()
        val z = camera.cameraInfo.zoomState.value ?: return
        val recentJog = SystemClock.uptimeMillis() - lastJogEndedAt < 1_500
        val from = if (recentJog) zoomBeforeJog else z.zoomRatio
        val stops = ZoomMath.stops(preset, z.minZoomRatio, z.maxZoomRatio)
        val target = ZoomMath.next(from, stops, direction)
        if (target == null) {
            message(if (direction > 0) "Already at the longest zoom stop" else "Already at the widest zoom stop")
            return
        }
        animateZoomTo(target)
    }

    /** C1 "next zoom stop": up through the stops, then back to the widest. */
    fun cycleZoomStop(preset: List<Float>) {
        val z = camera?.cameraInfo?.zoomState?.value ?: return
        val stops = ZoomMath.stops(preset, z.minZoomRatio, z.maxZoomRatio)
        ZoomMath.cycle(z.zoomRatio, stops)?.let(::animateZoomTo)
    }

    /** Smooth, constant-feel zoom to [target] (interpolated on a log scale). */
    fun animateZoomTo(target: Float) {
        val camera = camera ?: return
        val z = camera.cameraInfo.zoomState.value ?: return
        val start = z.zoomRatio
        val end = target.coerceIn(z.minZoomRatio, z.maxZoomRatio)
        zoomJob?.cancel()
        zoomJob = scope.launch {
            val duration = 320f
            val t0 = SystemClock.uptimeMillis()
            while (isActive) {
                val t = ((SystemClock.uptimeMillis() - t0) / duration).coerceIn(0f, 1f)
                val eased = 1f - (1f - t).pow(3)
                camera.cameraControl.setZoomRatio(start * (end / start).pow(eased))
                if (t >= 1f) break
                delay(16)
            }
        }
    }

    /** Pinch on the viewfinder. */
    fun pinchZoom(scaleFactor: Float) {
        val camera = camera ?: return
        val z = camera.cameraInfo.zoomState.value ?: return
        zoomJob?.cancel()
        camera.cameraControl.setZoomRatio((z.zoomRatio * scaleFactor).coerceIn(z.minZoomRatio, z.maxZoomRatio))
    }

    private fun stopJogs() {
        zoomJob?.cancel()
        focusJob?.cancel()
        zoomJob = null
        focusJob = null
    }

    // ------------------------------------------------------------------------------------------
    // Torch, flash, rotation
    // ------------------------------------------------------------------------------------------

    fun toggleTorch() {
        val camera = camera ?: return
        if (!camera.cameraInfo.hasFlashUnit()) {
            message("No torch on this camera")
            return
        }
        torchWanted = !_state.value.torchOn
        camera.cameraControl.enableTorch(torchWanted)
    }

    fun cycleFlash() {
        if (camera?.cameraInfo?.hasFlashUnit() != true) {
            message("No flash on this camera")
            return
        }
        val next = when (_state.value.flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = next
        _state.update { it.copy(flashMode = next) }
    }

    private var targetRotation = Surface.ROTATION_0

    /** Device orientation, so photos and videos come out upright when the grip is turned. */
    fun setTargetRotation(rotation: Int) {
        if (rotation !in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) return
        targetRotation = rotation
        imageCapture?.targetRotation = rotation
        // Rotation is fixed when a recording starts.
        if (activeRecording == null) videoCapture?.targetRotation = rotation
    }

    private fun message(text: String) {
        _signals.tryEmit(CameraSignal.Message(text))
    }

    /** Lets UI screenshot tests show a realistic camera without a camera. */
    @VisibleForTesting
    internal fun overrideStateForTest(transform: (CameraUiState) -> CameraUiState) = _state.update(transform)

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    private companion object {
        const val MAX_BURST = 60
        const val BURST_INTERVAL_MS = 110L
    }
}
