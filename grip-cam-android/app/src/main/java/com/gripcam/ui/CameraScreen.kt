package com.gripcam.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HdrOff
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.VideoStable
import androidx.compose.material.icons.filled.WbAuto
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.WbIridescent
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gripcam.DeviceStatus
import com.gripcam.LastCapture
import com.gripcam.MainViewModel
import com.gripcam.camera.CameraUiState
import com.gripcam.camera.VideoMath
import com.gripcam.camera.ZoomMath
import com.gripcam.grip.GripStatus
import com.gripcam.settings.AppSettings
import com.gripcam.settings.FrameAspect
import com.gripcam.settings.FrameRate
import com.gripcam.settings.GridStyle
import com.gripcam.settings.PhotoEnhancement
import com.gripcam.settings.Prefs
import com.gripcam.settings.SelfTimer
import com.gripcam.settings.Stabilization
import com.gripcam.settings.VideoResolution
import com.gripcam.settings.WhiteBalance
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CameraScreen(viewModel: MainViewModel, onRequestBluetoothPermission: () -> Unit) {
    val cam by viewModel.camera.state.collectAsStateWithLifecycle()
    val grip by viewModel.gripStatus.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val countdown by viewModel.countdown.collectAsStateWithLifecycle()
    val lastCapture by viewModel.lastCapture.collectAsStateWithLifecycle()
    val device by viewModel.deviceStatus.collectAsStateWithLifecycle()
    val iconTarget by viewModel.iconRotation.collectAsStateWithLifecycle()
    val iconRotation by animateFloatAsState(iconTarget, tween(300), label = "iconRotation")

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var helpDismissed by rememberSaveable { mutableStateOf(false) }
    var showExposure by rememberSaveable { mutableStateOf(false) }

    var message by remember { mutableStateOf<String?>(null) }
    var messageId by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect {
            message = it
            messageId++
        }
    }
    LaunchedEffect(messageId) {
        delay(2_800)
        message = null
    }

    val context = LocalContext.current
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.startGrip()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraPreview(viewModel, Modifier.fillMaxSize())
        FrameArea(cam.aspect) {
            GridOverlay(settings[Prefs.GRID])
            LevelOverlay(enabled = settings[Prefs.LEVEL])
        }
        FocusReticle(cam)
        ShutterFlash(cam.shutterTick)
        CountdownOverlay(countdown, iconRotation)

        // ---------------- top ----------------
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One weighted box, so the chip can use all the free width (not half of it).
                Box(Modifier.weight(1f)) {
                    GripStatusChip(
                        status = grip,
                        onClick = {
                            when (grip) {
                                GripStatus.MissingPermission -> onRequestBluetoothPermission()
                                GripStatus.BluetoothOff -> try {
                                    enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                } catch (_: SecurityException) {
                                    onRequestBluetoothPermission()
                                }
                                is GripStatus.Failed, GripStatus.Stopped -> viewModel.restartGrip()
                                else -> helpDismissed = !helpDismissed
                            }
                        },
                    )
                }
                if (!cam.recording) SetupBadge(cam, settings, onClick = { showSettings = true })
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Filled.Settings, "Settings", { showSettings = true }, rotation = iconRotation, size = 40.dp)
            }

            if (cam.recording) {
                RecordingHud(cam, settings, device, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), iconRotation)
            } else {
                QuickBar(
                    viewModel = viewModel,
                    cam = cam,
                    settings = settings,
                    exposureOpen = showExposure,
                    onToggleExposure = { showExposure = !showExposure },
                    rotation = iconRotation,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            AnimatedVisibility(
                visible = grip is GripStatus.Advertising && !helpDismissed,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                PairingHelpCard(onClose = { helpDismissed = true }, modifier = Modifier.padding(top = 10.dp))
            }

            AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        message.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Scrim)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            cam.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Scrim)
                        .padding(12.dp),
                )
            }
        }

        // ---------------- bottom ----------------
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (settings[Prefs.SHOW_LOG]) {
                ControlLog(
                    lines = log,
                    onClear = viewModel::clearLog,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp),
                )
            }
            if (cam.aeAfLocked) {
                Text(
                    "AE/AF locked · tap to unlock",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Accent)
                        .clickable { viewModel.camera.unlockAeAf() }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            AnimatedVisibility(visible = showExposure && cam.capabilities.exposureSupported) {
                ExposurePanel(cam, onChange = viewModel.camera::setExposureIndex, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            ZoomStopsRow(cam, settings, onSelect = viewModel.camera::animateZoomTo, rotation = iconRotation)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GalleryThumbnail(lastCapture, rotation = iconRotation) { capture ->
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(capture.uri, if (capture.video) "video/*" else "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No app found to open it", Toast.LENGTH_SHORT).show()
                    }
                }
                RecordButton(recording = cam.recording, onClick = viewModel.camera::toggleRecording)
                ShutterButton(
                    burstCount = cam.burstCount,
                    timerArmed = settings[Prefs.TIMER] != SelfTimer.OFF,
                    onPress = viewModel::shutterDown,
                    onRelease = viewModel::shutterUp,
                )
                RoundIconButton(
                    icon = Icons.Filled.Cameraswitch,
                    description = if (cam.recording) "Switch camera (keeps recording)" else "Switch camera",
                    rotation = iconRotation,
                    enabled = cam.canSwitchLens,
                    onClick = viewModel.camera::switchCamera,
                )
            }
        }
    }

    if (showSettings) {
        SettingsScreen(
            viewModel = viewModel,
            capabilities = cam.capabilities,
            onClose = { showSettings = false },
        )
    }
}

// -------------------------------------------------------------------------------------------
// Viewfinder
// -------------------------------------------------------------------------------------------

/** The part of the screen the picture occupies (the preview is letterboxed: what you see is what you get). */
@Composable
private fun FrameArea(aspect: FrameAspect, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Portrait UI: the frame is taller than wide.
        val ratio = aspect.longOverShort
        var w = maxWidth
        var h = maxWidth * ratio
        if (h > maxHeight) {
            h = maxHeight
            w = maxHeight / ratio
        }
        Box(Modifier.size(w, h)) { content() }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun CameraPreview(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = viewModel.camera
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            contentDescription = "Camera viewfinder. Tap to focus, long-press to lock, double-tap to switch camera, pinch to zoom."
            val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    controller.pinchZoom(detector.scaleFactor)
                    return true
                }
            })
            val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (width == 0 || height == 0) return false
                    viewModel.tapToFocus(meteringPointFactory.createPoint(e.x, e.y), (e.x / width) to (e.y / height))
                    performClick()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    controller.switchCamera()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    if (width == 0 || height == 0) return
                    viewModel.longPressLock(meteringPointFactory.createPoint(e.x, e.y), (e.x / width) to (e.y / height))
                }
            })
            setOnTouchListener { _, event ->
                pinch.onTouchEvent(event)
                if (!pinch.isInProgress) gestures.onTouchEvent(event)
                true
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.attach(lifecycleOwner, previewView.surfaceProvider)
        onDispose { controller.detach(lifecycleOwner) }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun ShutterFlash(tick: Int) {
    val alpha = remember { Animatable(0f) }
    var lastTick by remember { mutableIntStateOf(tick) }
    LaunchedEffect(tick) {
        if (tick != lastTick) {
            lastTick = tick
            alpha.snapTo(0.6f)
            alpha.animateTo(0f, tween(200))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Color.White),
    )
}

// -------------------------------------------------------------------------------------------
// Top: status, setup badge, quick controls, recording HUD
// -------------------------------------------------------------------------------------------

@Composable
private fun GripStatusChip(status: GripStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, tint, text) = when (status) {
        is GripStatus.Connected -> Triple(
            Icons.Filled.BluetoothConnected,
            FocusGreen,
            if (status.bonded) status.name else "${status.name} · not paired",
        )
        is GripStatus.Advertising -> Triple(
            Icons.Filled.BluetoothSearching,
            Accent,
            if (status.pairingMode) "Ready to pair" else "Waiting for grip",
        )
        GripStatus.BluetoothOff -> Triple(Icons.Filled.BluetoothDisabled, RecordRed, "Bluetooth is off · tap to turn on")
        GripStatus.MissingPermission -> Triple(Icons.Filled.BluetoothDisabled, RecordRed, "Allow Nearby devices · tap")
        GripStatus.Unsupported -> Triple(Icons.Filled.BluetoothDisabled, RecordRed, "This phone can't host a Bluetooth grip")
        is GripStatus.Failed -> Triple(Icons.Filled.BluetoothDisabled, RecordRed, status.message)
        GripStatus.Stopped -> Triple(Icons.Filled.Bluetooth, Color.White, "Grip off · tap to start")
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 2)
    }
}

/** "1080p 30 · STD" — what the camera is set to, tap for settings. */
@Composable
private fun SetupBadge(cam: CameraUiState, settings: AppSettings, onClick: () -> Unit) {
    val a = cam.active
    val text = buildString {
        append(a.videoLabel ?: settings[Prefs.VIDEO_RESOLUTION].label)
        append(' ')
        append(a.fps ?: settings[Prefs.FRAME_RATE].fps)
        if (a.hdr) append(" HDR")
    }
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun QuickBar(
    viewModel: MainViewModel,
    cam: CameraUiState,
    settings: AppSettings,
    exposureOpen: Boolean,
    onToggleExposure: () -> Unit,
    rotation: Float,
    modifier: Modifier = Modifier,
) {
    val caps = cam.capabilities
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (caps.flash) {
            QuickToggle(
                icon = when (cam.flashMode) {
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                    ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                    else -> Icons.Filled.FlashOff
                },
                label = "Flash",
                active = cam.flashMode != ImageCapture.FLASH_MODE_OFF,
                rotation = rotation,
                onClick = viewModel.camera::cycleFlash,
            )
        }
        val stab = settings[Prefs.STABILIZATION]
        QuickToggle(
            icon = Icons.Filled.VideoStable,
            label = when (stab) {
                Stabilization.OFF -> "Stab off"
                Stabilization.STANDARD -> "Stab"
                Stabilization.STEADY -> "Steady"
            },
            active = stab != Stabilization.OFF,
            rotation = rotation,
            onClick = { viewModel.cycle(Prefs.STABILIZATION) },
        )
        if (caps.hdrVideo) {
            val hdr = settings[Prefs.HDR_VIDEO]
            QuickToggle(
                icon = if (hdr) Icons.Filled.HdrOn else Icons.Filled.HdrOff,
                label = "HDR",
                active = hdr,
                rotation = rotation,
                onClick = { viewModel.set(Prefs.HDR_VIDEO, !hdr) },
            )
        }
        if (caps.enhancements.size > 1) {
            val mode = settings[Prefs.PHOTO_MODE]
            QuickToggle(
                icon = Icons.Filled.Nightlight,
                label = if (mode == PhotoEnhancement.NONE) "Mode" else mode.label,
                active = mode != PhotoEnhancement.NONE,
                rotation = rotation,
                onClick = { viewModel.cycle(Prefs.PHOTO_MODE) },
            )
        }
        val timer = settings[Prefs.TIMER]
        QuickToggle(
            icon = when (timer) {
                SelfTimer.OFF -> Icons.Filled.TimerOff
                SelfTimer.S3 -> Icons.Filled.Timer3
                SelfTimer.S10 -> Icons.Filled.Timer10
            },
            label = "Timer",
            active = timer != SelfTimer.OFF,
            rotation = rotation,
            onClick = { viewModel.cycle(Prefs.TIMER) },
        )
        val grid = settings[Prefs.GRID]
        QuickToggle(
            icon = if (grid == GridStyle.OFF) Icons.Filled.GridOff else Icons.Filled.GridOn,
            label = "Grid",
            active = grid != GridStyle.OFF,
            rotation = rotation,
            onClick = { viewModel.cycle(Prefs.GRID) },
        )
        if (caps.exposureSupported) {
            val ev = cam.exposureIndex * caps.exposureStep
            QuickToggle(
                icon = Icons.Filled.Exposure,
                label = if (cam.exposureIndex == 0) "EV" else String.format(Locale.US, "%+.1f", ev),
                active = exposureOpen || cam.exposureIndex != 0,
                rotation = rotation,
                onClick = onToggleExposure,
            )
        }
        if (caps.whiteBalance.size > 1) {
            QuickToggle(
                icon = when (cam.whiteBalance) {
                    WhiteBalance.AUTO -> Icons.Filled.WbAuto
                    WhiteBalance.DAYLIGHT -> Icons.Filled.WbSunny
                    WhiteBalance.CLOUDY -> Icons.Filled.WbCloudy
                    WhiteBalance.TUNGSTEN -> Icons.Filled.WbIncandescent
                    WhiteBalance.FLUORESCENT -> Icons.Filled.WbIridescent
                },
                label = if (cam.whiteBalance == WhiteBalance.AUTO) "WB" else cam.whiteBalance.label,
                active = cam.whiteBalance != WhiteBalance.AUTO,
                rotation = rotation,
                onClick = viewModel.camera::cycleWhiteBalance,
            )
        }
        QuickTextToggle(
            text = settings[Prefs.ASPECT].label,
            label = "Frame",
            rotation = rotation,
            onClick = { viewModel.cycle(Prefs.ASPECT) },
        )
    }
}

@Composable
private fun QuickToggle(icon: ImageVector, label: String, active: Boolean, rotation: Float, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Accent.copy(alpha = 0.25f) else Scrim)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Accent else Color.White, modifier = Modifier.size(20.dp).rotate(rotation))
        Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun QuickTextToggle(text: String, label: String, rotation: Float, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Scrim)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label $text" }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.height(20.dp).rotate(rotation))
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun RecordingHud(cam: CameraUiState, settings: AppSettings, device: DeviceStatus, modifier: Modifier, rotation: Float) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Scrim)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val seconds = cam.recordedNanos / 1_000_000_000L
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(RecordRed),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            if (settings[Prefs.RECORD_AUDIO]) {
                Spacer(Modifier.width(10.dp))
                AudioMeter(cam.audioLevel)
            }
        }
        Spacer(Modifier.height(4.dp))
        val a = cam.active
        val badges = listOfNotNull(
            a.videoLabel,
            a.fps?.let { "$it fps" },
            if (a.hdr) "HDR" else null,
            when (a.stabilization) {
                Stabilization.OFF -> null
                Stabilization.STANDARD -> "STAB"
                Stabilization.STEADY -> "STEADY"
            },
        )
        val bitsPerSecond = if (cam.recordedNanos > 2_000_000_000L) {
            cam.recordedBytes * 8 * 1_000_000_000L / cam.recordedNanos
        } else {
            VideoMath.estimatedBitrate(
                VideoResolution.entries.firstOrNull { it.label == a.videoLabel } ?: settings[Prefs.VIDEO_RESOLUTION],
                a.fps ?: FrameRate.FPS_30.fps,
                settings[Prefs.BITRATE],
            )
        }
        val info = listOfNotNull(
            badges.joinToString(" · ").ifEmpty { null },
            if (device.freeBytes > 0) "${VideoMath.formatDuration(VideoMath.remainingSeconds(device.freeBytes, bitsPerSecond))} left" else null,
            if (device.batteryPercent >= 0) "${device.batteryPercent}%" else null,
        ).joinToString("   ")
        Text(info, color = Color.White, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Scrim).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun PairingHelpCard(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xE6161A1E),
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pair your grip", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Hide pairing help")
                }
            }
            Spacer(Modifier.height(6.dp))
            val steps = listOf(
                "Keep this screen open, with Bluetooth on.",
                "On the grip, make sure the lock switch is off.",
                "Hold PHOTO and the T side of the zoom lever together for about 7 seconds.",
                "If your phone asks to pair, tap Pair.",
            )
            steps.forEachIndexed { i, step ->
                Text("${i + 1}.  $step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Already paired? Press any grip button to reconnect. If it doesn't, pair again.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// Bottom: exposure, zoom stops, shutter row, log
// -------------------------------------------------------------------------------------------

@Composable
private fun ExposurePanel(cam: CameraUiState, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val caps = cam.capabilities
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            String.format(Locale.US, "%+.1f EV", cam.exposureIndex * caps.exposureStep),
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(64.dp),
        )
        Slider(
            value = cam.exposureIndex.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = caps.exposureMin.toFloat()..caps.exposureMax.toFloat(),
            steps = (caps.exposureMax - caps.exposureMin - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange(0) }) { Text("0", color = Color.White) }
    }
}

@Composable
private fun ZoomStopsRow(cam: CameraUiState, settings: AppSettings, onSelect: (Float) -> Unit, rotation: Float) {
    val stops = ZoomMath.stops(settings[Prefs.ZOOM_STOPS].stops, cam.minZoom, cam.maxZoom)
    val nearest = ZoomMath.nearest(cam.zoomRatio, stops)
    val selected = if (nearest >= 0) nearest else stops.indexOfLast { it <= cam.zoomRatio }.coerceAtLeast(0)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stops.forEachIndexed { i, stop ->
            val isSelected = i == selected
            val size by animateDpAsState(if (isSelected) 40.dp else 32.dp, label = "zoomChip")
            val text = if (isSelected) {
                "${ZoomMath.label(cam.zoomRatio)}×"
            } else {
                ZoomMath.label(stop)
            }
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.Black.copy(alpha = 0.6f) else Color.Transparent)
                    .clickable(role = Role.Button) { onSelect(stop) }
                    .semantics { contentDescription = "Zoom ${ZoomMath.label(stop)}×" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    color = if (isSelected) Accent else Color.White,
                    fontSize = if (isSelected) 12.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun GalleryThumbnail(capture: LastCapture?, rotation: Float, onOpen: (LastCapture) -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Scrim)
            .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
            .clickable(enabled = capture != null, role = Role.Button) { capture?.let(onOpen) }
            .semantics { contentDescription = "Open last photo or video" },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = capture?.thumbnail
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation),
            )
            if (capture.video) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        } else {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ControlLog(lines: List<String>, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.7f)),
    ) {
        if (lines.isEmpty()) {
            Text(
                "Control log: grip traffic shows up here.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp),
            )
        }
        LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            items(lines) { line ->
                Text(line, color = Color(0xFFB2FF59), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
        IconButton(
            onClick = onClear,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(36.dp),
        ) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear log", tint = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    rotation: Float = 0f,
    enabled: Boolean = true,
    size: Dp = 48.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Scrim)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.rotate(rotation),
        )
    }
}

/** Press = photo (or burst start / timer); release ends a burst. */
@Composable
private fun ShutterButton(burstCount: Int, timerArmed: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "shutterScale")
    Box(
        Modifier
            .size(78.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    press()
                    tryAwaitRelease()
                    pressed = false
                    release()
                })
            }
            .semantics { contentDescription = "Take photo" }
            .padding(8.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        when {
            burstCount > 0 -> Text("$burstCount", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            timerArmed -> Icon(Icons.Filled.Timer, contentDescription = null, tint = Color.Black)
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val inner by animateDpAsState(if (recording) 24.dp else 40.dp, label = "recInner")
    val corner by animateDpAsState(if (recording) 6.dp else 20.dp, label = "recCorner")
    Box(
        Modifier
            .size(62.dp)
            .clip(CircleShape)
            .border(3.dp, Color.White, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (recording) "Stop recording" else "Start recording" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(inner)
                .clip(RoundedCornerShape(corner))
                .background(RecordRed),
        )
    }
}
