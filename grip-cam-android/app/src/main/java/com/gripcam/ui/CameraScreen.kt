package com.gripcam.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gripcam.MainViewModel
import com.gripcam.camera.CameraController
import com.gripcam.camera.CameraUiState
import com.gripcam.camera.FocusUi
import com.gripcam.grip.GripStatus
import kotlinx.coroutines.delay
import java.util.Locale

private val Scrim = Color.Black.copy(alpha = 0.55f)
private val RecordRed = Color(0xFFFF3B30)
private val FocusGreen = Color(0xFF4CD964)

@Composable
fun CameraScreen(viewModel: MainViewModel, onRequestBluetoothPermission: () -> Unit) {
    val cam by viewModel.camera.state.collectAsStateWithLifecycle()
    val grip by viewModel.gripStatus.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val iconTarget by viewModel.iconRotation.collectAsStateWithLifecycle()
    val iconRotation by animateFloatAsState(iconTarget, tween(300), label = "iconRotation")

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var helpDismissed by rememberSaveable { mutableStateOf(false) }

    var message by remember { mutableStateOf<String?>(null) }
    var messageId by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect {
            message = it
            messageId++
        }
    }
    LaunchedEffect(messageId) {
        delay(2_500)
        message = null
    }

    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.startGrip()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraPreview(viewModel.camera, Modifier.fillMaxSize())
        FocusReticle(cam)
        ShutterFlash(cam.shutterTick)

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                if (cam.recording) RecordingTimer(cam.recordedNanos, Modifier.rotate(iconRotation))
                Spacer(Modifier.width(8.dp))
                RoundIconButton(
                    icon = Icons.Filled.Settings,
                    description = "Settings",
                    rotation = iconRotation,
                    onClick = { showSettings = true },
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
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
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

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (settings.showLog) {
                ControlLog(
                    lines = log,
                    onClear = viewModel::clearLog,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                )
            }
            ZoomPill(cam, Modifier.rotate(iconRotation))
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(
                    icon = when (cam.flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                        ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                        else -> Icons.Filled.FlashOff
                    },
                    description = "Photo flash",
                    rotation = iconRotation,
                    enabled = cam.hasFlashUnit,
                    onClick = viewModel.camera::cycleFlash,
                )
                RecordButton(recording = cam.recording, onClick = viewModel.camera::toggleRecording)
                ShutterButton(onClick = viewModel.camera::takePhoto)
                RoundIconButton(
                    icon = Icons.Filled.Cameraswitch,
                    description = "Switch camera",
                    rotation = iconRotation,
                    enabled = cam.canSwitchLens && !cam.recording,
                    onClick = viewModel.camera::switchCamera,
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onChange = viewModel::updateSettings,
            onRestartBluetooth = viewModel::restartGrip,
            onDismiss = { showSettings = false },
        )
    }
}

// -------------------------------------------------------------------------------------------
// Viewfinder
// -------------------------------------------------------------------------------------------

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun CameraPreview(controller: CameraController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            contentDescription = "Camera viewfinder. Tap to focus, pinch to zoom."
            val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    controller.pinchZoom(detector.scaleFactor)
                    return true
                }
            })
            val tap = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (width == 0 || height == 0) return false
                    controller.startAutoFocus(
                        point = meteringPointFactory.createPoint(e.x, e.y),
                        viewFraction = (e.x / width) to (e.y / height),
                    )
                    performClick()
                    return true
                }
            })
            setOnTouchListener { _, event ->
                pinch.onTouchEvent(event)
                if (!pinch.isInProgress) tap.onTouchEvent(event)
                true
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.attach(lifecycleOwner, previewView)
        onDispose { controller.detach(lifecycleOwner) }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun FocusReticle(cam: CameraUiState) {
    val visible = cam.focus != FocusUi.IDLE
    val color by animateColorAsState(
        when (cam.focus) {
            FocusUi.FOCUSED -> FocusGreen
            FocusUi.FAILED -> RecordRed
            else -> Color.White
        },
        label = "reticleColor",
    )
    val size by animateDpAsState(if (cam.focus == FocusUi.FOCUSING) 84.dp else 68.dp, label = "reticleSize")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (visible) {
            val (fx, fy) = cam.focusPoint ?: (0.5f to 0.5f)
            val half = with(LocalDensity.current) { (size / 2).roundToPx() }
            Box(
                Modifier
                    .offset { IntOffset((constraints.maxWidth * fx).roundToInt() - half, (constraints.maxHeight * fy).roundToInt() - half) }
                    .size(size)
                    .border(2.dp, color, RoundedCornerShape(6.dp)),
            )
        }
        if (cam.manualFocus) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("MF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(6.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Scrim),
                ) {
                    // Near at the top, infinity at the bottom.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(160.dp * cam.manualFocusPosition)
                            .background(Color(0xFFFFB74D)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShutterFlash(tick: Int) {
    val alpha = remember { Animatable(0f) }
    var lastTick by remember { mutableIntStateOf(tick) }
    LaunchedEffect(tick) {
        if (tick != lastTick) {
            lastTick = tick
            alpha.snapTo(0.7f)
            alpha.animateTo(0f, tween(220))
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
// Status
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
            Color(0xFFFFB74D),
            if (status.pairingMode) "Waiting for grip · pairing open" else "Waiting for grip",
        )
        GripStatus.BluetoothOff -> Triple(Icons.Filled.BluetoothDisabled, RecordRed, "Bluetooth is off · tap to turn on")
        GripStatus.MissingPermission -> Triple(Icons.Filled.BluetoothDisabled, RecordRed, "Allow Nearby devices · tap")
        GripStatus.Unsupported -> Triple(
            Icons.Filled.BluetoothDisabled,
            RecordRed,
            "This phone can't host a Bluetooth grip",
        )
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

@Composable
private fun RecordingTimer(nanos: Long, modifier: Modifier = Modifier) {
    val seconds = nanos / 1_000_000_000L
    val text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(RecordRed),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ZoomPill(cam: CameraUiState, modifier: Modifier = Modifier) {
    val text = String.format(Locale.US, "%.1f×", cam.zoomRatio)
    Text(
        text,
        color = if (cam.zoomRatio > cam.minZoom + 0.05f) Color(0xFFFFB74D) else Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun ControlLog(lines: List<String>, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
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
        IconButton(onClick = onClear, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear log", tint = Color.White.copy(alpha = 0.8f))
        }
    }
}

// -------------------------------------------------------------------------------------------
// Buttons
// -------------------------------------------------------------------------------------------

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    rotation: Float = 0f,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .size(48.dp)
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

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Take photo" }
            .padding(8.dp)
            .clip(CircleShape)
            .background(Color.White),
    )
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
