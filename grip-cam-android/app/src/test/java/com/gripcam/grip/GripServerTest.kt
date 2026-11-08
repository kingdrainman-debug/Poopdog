package com.gripcam.grip

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Build
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gripcam.grip.SonyRemoteProtocol.Button
import com.gripcam.grip.SonyRemoteProtocol.Command
import com.gripcam.grip.SonyRemoteProtocol.Jog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBluetoothGattServer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Drives [GripServer] the way a GP-VPT2BT would: connect, subscribe to FF02, write button
 * presses to FF01, disconnect. Runs on both the Android 12+ permission model and the old one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R, Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class GripServerTest {

    private lateinit var app: Application
    private lateinit var adapter: BluetoothAdapter
    private lateinit var server: GripServer
    private val events = CopyOnWriteArrayList<GripEvent>()
    private val logLines = CopyOnWriteArrayList<String>()
    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        adapter = app.getSystemService(BluetoothManager::class.java).adapter
        shadowOf(adapter).setEnabled(true)
        shadowOf(adapter).setIsMultipleAdvertisementSupported(true)

        server = GripServer(app)
        scope.launch { server.events.collect { events += it } }
        scope.launch { server.log.collect { logLines += it } }
    }

    @After
    fun tearDown() {
        server.stop()
        idle()
        server.release() // quits the Bluetooth thread, so no idling after this
        scope.cancel()
    }

    // -------------------------------------------------------------------------------------------

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(server.looper).idle()
    }

    private fun gatt(): BluetoothGattServer = checkNotNull(server.gattServerForTest) { "GATT server not open" }

    private fun shadowGatt(): ShadowBluetoothGattServer = shadowOf(gatt())

    private fun callback(): BluetoothGattServerCallback = shadowGatt().gattServerCallback

    private fun remoteService(): BluetoothGattService = gatt().getService(SonyRemoteProtocol.REMOTE_SERVICE)

    private fun advertiseCount(): Int = shadowOf(adapter.bluetoothLeAdvertiser).advertisementRequestCount

    /** Starts the server and completes service registration, as the stack would. */
    private fun startAndRegister() {
        server.start(AdvertConfig())
        idle()
        callback().onServiceAdded(BluetoothGatt.GATT_SUCCESS, remoteService())
        idle()
    }

    private fun grip(address: String = "D8:A3:5C:00:12:34", name: String = "GP-VPT2BT"): BluetoothDevice =
        adapter.getRemoteDevice(address).also {
            shadowOf(it).setName(name)
            shadowOf(it).setBondState(BluetoothDevice.BOND_BONDED)
        }

    private fun connect(device: BluetoothDevice) {
        callback().onConnectionStateChange(device, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        idle()
    }

    private fun subscribe(device: BluetoothDevice) {
        val cccd = remoteService().getCharacteristic(SonyRemoteProtocol.STATUS_CHARACTERISTIC)
            .getDescriptor(SonyRemoteProtocol.CCCD)
        callback().onDescriptorWriteRequest(device, 1, cccd, false, true, 0, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        idle()
    }

    private fun write(device: BluetoothDevice, vararg bytes: Int) {
        val ff01 = remoteService().getCharacteristic(SonyRemoteProtocol.COMMAND_CHARACTERISTIC)
        val value = ByteArray(bytes.size) { bytes[it].toByte() }
        callback().onCharacteristicWriteRequest(device, 2, ff01, false, true, 0, value)
        idle()
    }

    private fun commands(): List<Command> =
        events.filterIsInstance<GripEvent.CommandReceived>().map { it.command }

    // -------------------------------------------------------------------------------------------

    @Test
    fun `hosts the remote-commander service exactly like a camera`() {
        server.start(AdvertConfig())
        idle()
        val service = remoteService()
        assertEquals(BluetoothGattService.SERVICE_TYPE_PRIMARY, service.type)

        val ff01 = service.getCharacteristic(SonyRemoteProtocol.COMMAND_CHARACTERISTIC)
        assertTrue(ff01.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
        assertTrue(ff01.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)

        val ff02 = service.getCharacteristic(SonyRemoteProtocol.STATUS_CHARACTERISTIC)
        assertEquals(BluetoothGattCharacteristic.PROPERTY_NOTIFY, ff02.properties)
        assertNotNull(ff02.getDescriptor(SonyRemoteProtocol.CCCD))
    }

    @Test
    fun `advertises only once the service is registered`() {
        server.start(AdvertConfig())
        idle()
        assertEquals(0, advertiseCount())

        callback().onServiceAdded(BluetoothGatt.GATT_SUCCESS, remoteService())
        idle()
        assertEquals(1, advertiseCount())
        assertEquals(GripStatus.Advertising(pairingMode = true), server.status.value)
        assertTrue(logLines.any { it.contains("2D 01 03 00 64 00 45 31 22 EF 00") })
    }

    @Test
    fun `grip session end to end`() {
        startAndRegister()
        val grip = grip()

        connect(grip)
        // Just a link: not "the grip" until it talks to our service.
        assertTrue(server.status.value is GripStatus.Advertising)
        assertTrue(events.none { it is GripEvent.RemoteConnected })

        subscribe(grip)
        assertEquals(GripStatus.Connected("GP-VPT2BT", grip.address, bonded = true), server.status.value)
        assertEquals(GripEvent.RemoteConnected("GP-VPT2BT"), events.first())
        assertTrue(logLines.any { it.contains("Grip subscribed") })

        // Half press, full press, full release, half release, then zoom T at full speed.
        write(grip, 0x01, 0x07)
        write(grip, 0x01, 0x09)
        write(grip, 0x01, 0x08)
        write(grip, 0x01, 0x06)
        write(grip, 0x02, 0x45, 0x7F)
        write(grip, 0x02, 0x44, 0x00)

        val got = commands()
        assertEquals(6, got.size)
        assertEquals(Button.SHUTTER_HALF, (got[0] as Command.ButtonEvent).button)
        assertTrue((got[0] as Command.ButtonEvent).pressed)
        assertEquals(Button.SHUTTER_FULL, (got[1] as Command.ButtonEvent).button)
        assertEquals(Jog.ZOOM_TELE, (got[4] as Command.JogEvent).jog)
        assertEquals(1f, (got[4] as Command.JogEvent).speed)

        // Every write request (and the CCCD write) was answered.
        assertEquals(7, shadowGatt().responses.size)

        // The grip goes away: one disconnect event, and we advertise again so it can come back.
        val before = advertiseCount()
        callback().onConnectionStateChange(grip, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        idle()
        assertEquals(GripEvent.RemoteDisconnected, events.last())
        assertTrue(advertiseCount() > before)
        assertTrue(server.status.value is GripStatus.Advertising)
    }

    @Test
    fun `unknown bytes are logged but do not break the session`() {
        startAndRegister()
        val grip = grip()
        connect(grip)
        write(grip, 0x01, 0x30)
        write(grip, 0x01, 0x0F)
        val got = commands()
        assertTrue(got[0] is Command.Unknown)
        assertEquals(Button.RECORD, (got[1] as Command.ButtonEvent).button)
        assertTrue(logLines.any { it.contains("01 30") && it.contains("unknown") })
    }

    @Test
    fun `other phone connections do not count as a grip`() {
        startAndRegister()
        val watch = grip(address = "11:22:33:44:55:66", name = "Watch")
        connect(watch)
        callback().onConnectionStateChange(watch, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        idle()
        assertTrue(events.isEmpty())
        assertTrue(server.status.value is GripStatus.Advertising)
    }

    @Test
    fun `pairing toggle changes the advertised flags`() {
        startAndRegister()
        server.updateConfig(AdvertConfig(pairingMode = false))
        idle()
        assertEquals(GripStatus.Advertising(pairingMode = false), server.status.value)
        assertTrue(logLines.any { it.contains("22 AF 00") })
    }

    @Test
    fun `bluetooth off and back on`() {
        startAndRegister()
        val grip = grip()
        connect(grip)
        subscribe(grip)

        app.sendBroadcast(Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF))
        idle()
        assertEquals(GripStatus.BluetoothOff, server.status.value)
        assertEquals(GripEvent.RemoteDisconnected, events.last())

        app.sendBroadcast(Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON))
        idle()
        // A fresh GATT server was opened; once its service registers we advertise again.
        callback().onServiceAdded(BluetoothGatt.GATT_SUCCESS, remoteService())
        idle()
        assertTrue(server.status.value is GripStatus.Advertising)
    }

    @Test
    fun `reports bluetooth off at start`() {
        shadowOf(adapter).setEnabled(false)
        server.start(AdvertConfig())
        idle()
        assertEquals(GripStatus.BluetoothOff, server.status.value)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `asks for permission when Nearby devices is denied`() {
        shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_ADVERTISE)
        server.start(AdvertConfig())
        idle()
        assertEquals(GripStatus.MissingPermission, server.status.value)

        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_ADVERTISE)
        server.retry()
        idle()
        callback().onServiceAdded(BluetoothGatt.GATT_SUCCESS, remoteService())
        idle()
        assertTrue(server.status.value is GripStatus.Advertising)
    }
}
