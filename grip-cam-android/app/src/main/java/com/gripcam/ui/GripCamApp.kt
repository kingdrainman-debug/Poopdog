package com.gripcam.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gripcam.MainViewModel

private val GripCamColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF1B1B1B),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF0E1114),
    surface = Color(0xFF161A1E),
    surfaceContainerLow = Color(0xFF1B2025),
    error = Color(0xFFFF6B6B),
)

@Composable
fun GripCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GripCamColors, content = content)
}

/** Runtime permissions the grip needs (Android 12+). Older versions grant Bluetooth at install. */
private val BLUETOOTH_PERMISSIONS: List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        emptyList()
    }

/** Nice to have: sound in videos, and gallery writes on Android 9 and older. */
private val OPTIONAL_PERMISSIONS: List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun GripCamApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(context.granted(Manifest.permission.CAMERA)) }
    var bluetoothGranted by remember { mutableStateOf(BLUETOOTH_PERMISSIONS.all(context::granted)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        cameraGranted = context.granted(Manifest.permission.CAMERA)
        bluetoothGranted = BLUETOOTH_PERMISSIONS.all(context::granted)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
    }
    val everything = listOf(Manifest.permission.CAMERA) + BLUETOOTH_PERMISSIONS + OPTIONAL_PERMISSIONS

    // Permissions can change in system Settings while we're in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!askedOnce && everything.any { !context.granted(it) }) {
            askedOnce = true
            launcher.launch(everything.toTypedArray())
        }
    }

    // Start (or re-check) the Bluetooth side whenever its permission state changes.
    LaunchedEffect(bluetoothGranted) { viewModel.startGrip() }

    if (cameraGranted) {
        CameraScreen(
            viewModel = viewModel,
            onRequestBluetoothPermission = {
                if (BLUETOOTH_PERMISSIONS.isNotEmpty()) launcher.launch(BLUETOOTH_PERMISSIONS.toTypedArray())
            },
        )
    } else {
        PermissionScreen(
            onGrant = { launcher.launch(everything.toTypedArray()) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("GripCam needs the camera", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "It turns your phone into the camera your Bluetooth shooting grip controls. " +
                "Allow Camera, plus Nearby devices so the grip can connect, and Microphone for video sound.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text("Allow access") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings) { Text("Open app settings") }
    }
}
