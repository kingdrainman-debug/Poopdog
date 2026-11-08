package com.gripcam.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.gripcam.MainViewModel
import com.gripcam.camera.ActiveSetup
import com.gripcam.camera.CameraCapabilities
import com.gripcam.camera.Layout
import com.gripcam.grip.GripServer
import com.gripcam.grip.SonyRemoteProtocol
import com.gripcam.settings.FrameRate
import com.gripcam.settings.GridStyle
import com.gripcam.settings.PhotoEnhancement
import com.gripcam.settings.PhotoFormat
import com.gripcam.settings.Prefs
import com.gripcam.settings.Stabilization
import com.gripcam.settings.VideoResolution
import com.gripcam.settings.WhiteBalance
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real screens with a realistic (fake) camera state and saves PNGs to
 * app/build/screenshots, to check layout without a phone. Not an assertion test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class UiScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private lateinit var vm: MainViewModel
    private val outDir = File("build/screenshots").apply { mkdirs() }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("gripcam", 0).edit().clear().commit()
        shadowOf(app).grantPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.RECORD_AUDIO,
        )
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        shadowOf(adapter).setEnabled(true)
        shadowOf(adapter).setIsMultipleAdvertisementSupported(true)
        vm = MainViewModel(app)
    }

    private fun grip(): GripServer =
        MainViewModel::class.java.getDeclaredField("grip").apply { isAccessible = true }.get(vm) as GripServer

    private fun idleBluetooth() {
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(grip().looper).idle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Brings the grip to "advertising", optionally all the way to "connected". */
    private fun startGrip(connect: Boolean) {
        vm.startGrip()
        idleBluetooth()
        val server = grip().gattServerForTest ?: return
        val callback = shadowOf(server).gattServerCallback
        val service = server.getService(SonyRemoteProtocol.REMOTE_SERVICE)
        callback.onServiceAdded(BluetoothGatt.GATT_SUCCESS, service)
        idleBluetooth()
        if (!connect) return
        val device = app.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice("D8:A3:5C:00:12:34")
        shadowOf(device).setName("GP-VPT2BT")
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)
        callback.onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        val cccd = service.getCharacteristic(SonyRemoteProtocol.STATUS_CHARACTERISTIC).getDescriptor(SonyRemoteProtocol.CCCD)
        callback.onDescriptorWriteRequest(device, 1, cccd, false, true, 0, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        idleBluetooth()
    }

    private fun fakeCamera(recording: Boolean = false) {
        vm.camera.overrideStateForTest {
            it.copy(
                ready = true,
                error = null,
                canSwitchLens = true,
                zoomRatio = 3.0f,
                minZoom = 0.6f,
                maxZoom = 10f,
                recording = recording,
                recordedNanos = if (recording) 83_000_000_000L else 0L,
                recordedBytes = if (recording) 440_000_000L else 0L,
                audioLevel = if (recording) 0.12f else 0f,
                capabilities = CameraCapabilities(
                    known = true,
                    videoResolutions = VideoResolution.entries.toSet(),
                    hdrVideo = true,
                    frameRates = setOf(FrameRate.FPS_24, FrameRate.FPS_30, FrameRate.FPS_60),
                    videoStabilization = true,
                    previewStabilization = true,
                    photoFormats = setOf(PhotoFormat.JPEG, PhotoFormat.ULTRA_HDR),
                    enhancements = setOf(PhotoEnhancement.NONE, PhotoEnhancement.NIGHT, PhotoEnhancement.HDR),
                    zeroShutterLag = true,
                    lowLightBoost = true,
                    flash = true,
                    exposureMin = -12,
                    exposureMax = 12,
                    exposureStep = 1f / 6f,
                    whiteBalance = WhiteBalance.entries.toSet(),
                    maxFocusDiopters = 10f,
                ),
                active = ActiveSetup(
                    layout = Layout.COMBINED,
                    videoLabel = "4K",
                    fps = 30,
                    hdr = true,
                    stabilization = Stabilization.STEADY,
                ),
            )
        }
    }

    /** Software-draws the whole window (captureToImage needs a hardware frame Robolectric never produces). */
    private fun shoot(name: String) {
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun viewfinder() {
        vm.set(Prefs.GRID, GridStyle.THIRDS)
        vm.set(Prefs.HDR_VIDEO, true)
        vm.set(Prefs.STABILIZATION, Stabilization.STEADY)
        compose.setContent { GripCamTheme { CameraScreen(vm, onRequestBluetoothPermission = {}) } }
        startGrip(connect = true)
        fakeCamera()
        compose.waitForIdle()
        shoot("1-viewfinder")

        compose.onNodeWithContentDescription("EV").performClick()
        fakeCamera()
        shoot("2-exposure-panel")
    }

    @Test
    fun recording() {
        compose.setContent { GripCamTheme { CameraScreen(vm, onRequestBluetoothPermission = {}) } }
        startGrip(connect = true)
        fakeCamera(recording = true)
        shoot("3-recording")
    }

    @Test
    fun pairing() {
        compose.setContent { GripCamTheme { CameraScreen(vm, onRequestBluetoothPermission = {}) } }
        startGrip(connect = false)
        fakeCamera()
        shoot("4-pairing-help")
    }

    @Test
    fun settings() {
        compose.setContent { GripCamTheme { CameraScreen(vm, onRequestBluetoothPermission = {}) } }
        fakeCamera()
        compose.onNodeWithContentDescription("Settings").performClick()
        fakeCamera()
        shoot("5-settings")
        // Opens the choice dialog (a separate window, so not in the capture): just checks it doesn't crash.
        compose.onNodeWithText("Stabilization").performClick()
        compose.waitForIdle()
    }
}
