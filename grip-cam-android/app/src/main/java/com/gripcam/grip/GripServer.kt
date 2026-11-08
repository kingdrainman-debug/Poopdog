package com.gripcam.grip

import android.Manifest
import android.annotation.SuppressLint
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
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.gripcam.grip.SonyRemoteProtocol.Command
import com.gripcam.grip.SonyRemoteProtocol.StatusField
import com.gripcam.grip.SonyRemoteProtocol.toHex
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the phone presents itself to remotes. */
data class AdvertConfig(
    /** Advertise "ready to pair" (tag 0x22 bit 0x40), so a grip in pairing mode will pick us. */
    val pairingMode: Boolean = true,
    val modelCode: String = SonyRemoteProtocol.DEFAULT_MODEL_CODE,
    val protocolVersion: Int = SonyRemoteProtocol.PROTOCOL_VERSION_64,
)

sealed interface GripStatus {
    data object Stopped : GripStatus
    data object MissingPermission : GripStatus
    data object BluetoothOff : GripStatus

    /** The phone's Bluetooth chip cannot act as a BLE peripheral. */
    data object Unsupported : GripStatus
    data class Advertising(val pairingMode: Boolean) : GripStatus
    data class Connected(val name: String, val address: String, val bonded: Boolean) : GripStatus
    data class Failed(val message: String) : GripStatus
}

sealed interface GripEvent {
    data class CommandReceived(val command: Command) : GripEvent
    data class RemoteConnected(val name: String) : GripEvent
    data object RemoteDisconnected : GripEvent
}

/**
 * Makes the phone look like a Sony camera to a Bluetooth remote:
 *
 * 1. hosts the remote-commander GATT service (`8000FF00`, with `FF01` and `FF02`),
 * 2. advertises Sony camera manufacturer data with the "remote pairing" flags,
 * 3. turns writes to `FF01` into [GripEvent.CommandReceived], and
 * 4. reports focus / shutter / recording state back over `FF02` notifications.
 *
 * All Bluetooth work runs on one background thread, so no state here needs locking.
 */
@SuppressLint("MissingPermission") // every entry point checks hasPermissions() first
class GripServer(context: Context) {

    private val appContext = context.applicationContext
    private val manager: BluetoothManager? = appContext.getSystemService(BluetoothManager::class.java)
    private val thread = HandlerThread("grip-ble").apply { start() }
    private val handler = Handler(thread.looper)

    private val _status = MutableStateFlow<GripStatus>(GripStatus.Stopped)
    val status: StateFlow<GripStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<GripEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<GripEvent> = _events.asSharedFlow()

    private val _log = MutableSharedFlow<String>(extraBufferCapacity = 128)

    /** Human-readable trace of everything on the link, for the in-app control log. */
    val log: SharedFlow<String> = _log.asSharedFlow()

    // ---- state, touched only on [thread] ----
    private var wanted = false
    private var config = AdvertConfig()
    private var gattServer: BluetoothGattServer? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var serviceReady = false
    private var advertising = false
    private var lastLoggedPayload = ""
    private var receiversRegistered = false
    private val connected = LinkedHashMap<String, BluetoothDevice>()
    private val remotes = LinkedHashMap<String, BluetoothDevice>()
    private val subscribers = HashSet<String>()
    private val statusValues = mutableMapOf(
        StatusField.FOCUS to false,
        StatusField.SHUTTER to false,
        StatusField.RECORDING to false,
    )
    private val notifyQueue = ArrayDeque<Pair<BluetoothDevice, ByteArray>>()
    private var notifyInFlight = false
    private val notifyTimeout = Runnable {
        notifyInFlight = false
        pumpNotifications()
    }
    private val retryAdvertising = Runnable { refreshAdvertising() }

    // ------------------------------------------------------------------------------------------
    // Public API (thread-safe: everything is posted to the Bluetooth thread)
    // ------------------------------------------------------------------------------------------

    fun start(config: AdvertConfig) {
        handler.post {
            wanted = true
            this.config = config
            registerReceivers()
            bringUp()
        }
    }

    fun stop() {
        handler.post {
            wanted = false
            tearDown()
            unregisterReceivers()
            _status.value = GripStatus.Stopped
        }
    }

    /** Re-checks permissions / adapter and starts again, e.g. after the user grants access. */
    fun retry() {
        handler.post {
            if (!wanted) return@post
            registerReceivers()
            if (gattServer == null) bringUp() else refreshAdvertising()
        }
    }

    fun updateConfig(config: AdvertConfig) {
        handler.post {
            if (config == this.config) return@post
            this.config = config
            if (advertising) {
                stopAdvertising()
                startAdvertising()
            } else {
                updateIdleStatus()
            }
        }
    }

    /**
     * Reports camera state to the grip, as a Sony camera would. Sent even when unchanged: a
     * camera answers a half press with "focus: false" when it cannot focus.
     */
    fun setStatus(field: StatusField, active: Boolean) {
        handler.post {
            statusValues[field] = active
            val payload = SonyRemoteProtocol.statusPayload(field, active)
            for (address in subscribers) {
                connected[address]?.let { enqueueNotification(it, payload) }
            }
        }
    }

    fun release() {
        stop()
        handler.post { thread.quitSafely() }
    }

    @VisibleForTesting
    internal val looper: Looper get() = thread.looper

    @VisibleForTesting
    internal val gattServerForTest: BluetoothGattServer? get() = gattServer

    // ------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE).all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private fun bringUp() {
        if (!wanted) return
        if (!hasPermissions()) {
            _status.value = GripStatus.MissingPermission
            return
        }
        val adapter = adapter
        if (manager == null || adapter == null) {
            _status.value = GripStatus.Unsupported
            return
        }
        if (!adapter.isEnabled) {
            _status.value = GripStatus.BluetoothOff
            return
        }
        if (adapter.bluetoothLeAdvertiser == null) {
            _status.value = GripStatus.Unsupported
            log("This phone's Bluetooth cannot advertise as a peripheral.")
            return
        }
        if (gattServer != null) {
            refreshAdvertising()
            return
        }
        val server = try {
            manager.openGattServer(appContext, gattCallback)
        } catch (e: SecurityException) {
            _status.value = GripStatus.MissingPermission
            return
        }
        if (server == null) {
            _status.value = GripStatus.Failed("Could not open the Bluetooth GATT server")
            handler.postDelayed({ bringUp() }, 2_000)
            return
        }
        gattServer = server
        serviceReady = false
        server.addService(buildService())
        // Advertising starts from onServiceAdded, once the service is really there.
    }

    private fun buildService(): BluetoothGattService {
        val service = BluetoothGattService(
            SonyRemoteProtocol.REMOTE_SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        val command = BluetoothGattCharacteristic(
            SonyRemoteProtocol.COMMAND_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val status = BluetoothGattCharacteristic(
            SonyRemoteProtocol.STATUS_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        status.addDescriptor(
            BluetoothGattDescriptor(
                SonyRemoteProtocol.CCCD,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(command)
        service.addCharacteristic(status)
        statusCharacteristic = status
        return service
    }

    private fun tearDown() {
        handler.removeCallbacks(retryAdvertising)
        stopAdvertising()
        val hadRemote = remotes.isNotEmpty()
        try {
            gattServer?.clearServices()
            gattServer?.close()
        } catch (_: SecurityException) {
        }
        gattServer = null
        statusCharacteristic = null
        serviceReady = false
        connected.clear()
        remotes.clear()
        subscribers.clear()
        notifyQueue.clear()
        notifyInFlight = false
        if (hadRemote) _events.tryEmit(GripEvent.RemoteDisconnected)
    }

    // ------------------------------------------------------------------------------------------
    // Advertising
    // ------------------------------------------------------------------------------------------

    private val advertiser: BluetoothLeAdvertiser? get() = adapter?.bluetoothLeAdvertiser

    /**
     * Advertise while no grip is attached, stay quiet while one is. Restarts the advertisement
     * because a connection silently ends a connectable legacy advertisement on most stacks.
     */
    private fun refreshAdvertising() {
        handler.removeCallbacks(retryAdvertising)
        if (!wanted || !serviceReady) return
        stopAdvertising()
        if (remotes.isEmpty()) startAdvertising()
        updateIdleStatus()
    }

    private fun startAdvertising() {
        if (!wanted || !serviceReady || advertising) return
        val advertiser = advertiser ?: run {
            _status.value = if (adapter?.isEnabled == true) GripStatus.Unsupported else GripStatus.BluetoothOff
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val payload = SonyRemoteProtocol.advertisementPayload(
            pairingMode = config.pairingMode,
            modelCode = config.modelCode,
            protocolVersion = config.protocolVersion,
        )
        // Sources disagree on whether cameras put this in the advertisement or the scan
        // response, so it goes in both; a passive scanner sees the first, an active one both.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(SonyRemoteProtocol.SONY_COMPANY_ID, payload)
            .build()
        try {
            advertiser.startAdvertising(settings, data, data, advertiseCallback)
            advertising = true
            val hex = payload.toHex()
            if (hex != lastLoggedPayload) {
                lastLoggedPayload = hex
                log("Advertising as a Sony camera: 2D 01 $hex")
            }
        } catch (e: SecurityException) {
            _status.value = GripStatus.MissingPermission
        } catch (e: IllegalStateException) {
            // Adapter switched off underneath us; the state receiver will bring us back.
            _status.value = GripStatus.BluetoothOff
        }
    }

    private fun stopAdvertising() {
        if (!advertising) return
        advertising = false
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            handler.post { updateIdleStatus() }
        }

        override fun onStartFailure(errorCode: Int) {
            handler.post {
                if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) return@post
                advertising = false
                val reason = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "advertisement too large"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many apps advertising"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "not supported on this phone"
                    else -> "internal Bluetooth error"
                }
                log("Advertising failed ($errorCode): $reason")
                if (errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED) {
                    _status.value = GripStatus.Unsupported
                } else {
                    _status.value = GripStatus.Failed("Bluetooth advertising failed: $reason. Retrying…")
                    handler.postDelayed(retryAdvertising, 3_000)
                }
            }
        }
    }

    private fun updateIdleStatus() {
        if (!wanted) return
        val remote = remotes.values.lastOrNull()
        _status.value = when {
            remote != null -> GripStatus.Connected(nameOf(remote), remote.address, isBonded(remote))
            advertising -> GripStatus.Advertising(config.pairingMode)
            else -> _status.value
        }
    }

    // ------------------------------------------------------------------------------------------
    // GATT server
    // ------------------------------------------------------------------------------------------

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    serviceReady = true
                    log("Remote-commander service ready")
                    refreshAdvertising()
                } else {
                    _status.value = GripStatus.Failed("Could not add the Bluetooth service ($status)")
                }
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            handler.post { onConnectionChanged(device, newState) }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            handler.post { onCommandWrite(device, requestId, characteristic, responseNeeded, offset, value ?: ByteArray(0)) }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            handler.post { onCccdWrite(device, requestId, descriptor, responseNeeded, offset, value ?: ByteArray(0)) }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            handler.post {
                val value = if (device.address in subscribers) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset.coerceAtMost(value.size), value.size))
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handler.post { respond(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null) }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            handler.post { respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            handler.post {
                handler.removeCallbacks(notifyTimeout)
                notifyInFlight = false
                pumpNotifications()
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            handler.post { log("MTU ${nameOf(device)}: $mtu") }
        }
    }

    private fun onConnectionChanged(device: BluetoothDevice, newState: Int) {
        val address = device.address
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                if (connected.put(address, device) == null) {
                    log("Link up: ${nameOf(device)} ($address)")
                }
                // Ties the link to this server so the stack keeps it up while the grip is idle.
                try {
                    gattServer?.connect(device, false)
                } catch (_: SecurityException) {
                }
                refreshAdvertising()
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                connected.remove(address)
                subscribers.remove(address)
                notifyQueue.removeAll { it.first.address == address }
                val wasRemote = remotes.remove(address) != null
                if (wasRemote) {
                    log("Grip disconnected: ${nameOf(device)}")
                    if (remotes.isEmpty()) _events.tryEmit(GripEvent.RemoteDisconnected)
                }
                refreshAdvertising()
            }
        }
    }

    /** A device becomes "the grip" the first time it talks to our service. */
    private fun markRemote(device: BluetoothDevice) {
        if (remotes.containsKey(device.address)) return
        connected.putIfAbsent(device.address, device)
        remotes[device.address] = device
        val name = nameOf(device)
        log("Grip connected: $name (${if (isBonded(device)) "paired" else "not paired yet"})")
        _events.tryEmit(GripEvent.RemoteConnected(name))
        stopAdvertising()
        updateIdleStatus()
        // Sony cameras only accept bonded remotes, so a grip normally starts pairing itself.
        // If it has not after a few seconds, ask it to (sends an SMP Security Request).
        handler.postDelayed({
            if (remotes.containsKey(device.address) && device.bondState == BluetoothDevice.BOND_NONE) {
                log("Requesting pairing with $name")
                try {
                    device.createBond()
                } catch (_: SecurityException) {
                }
            }
        }, 4_000)
    }

    private fun onCommandWrite(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
    ) {
        if (characteristic.uuid != SonyRemoteProtocol.COMMAND_CHARACTERISTIC) {
            if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
            return
        }
        if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
        markRemote(device)
        for (command in SonyRemoteProtocol.parseCommands(value)) {
            log("◀ ${command.raw.toHex()}  ${describe(command)}")
            _events.tryEmit(GripEvent.CommandReceived(command))
        }
    }

    private fun onCccdWrite(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
    ) {
        if (descriptor.uuid != SonyRemoteProtocol.CCCD) {
            if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
            return
        }
        if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
        val enabled = value.isNotEmpty() && (value[0].toInt() and 0x03) != 0
        markRemote(device)
        if (enabled) {
            subscribers += device.address
            log("Grip subscribed to status")
            // A real camera pushes its current state as soon as the remote subscribes.
            for ((field, active) in statusValues) {
                enqueueNotification(device, SonyRemoteProtocol.statusPayload(field, active))
            }
        } else {
            subscribers -= device.address
        }
    }

    private fun respond(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray?) {
        try {
            gattServer?.sendResponse(device, requestId, status, offset, value)
        } catch (_: SecurityException) {
        }
    }

    // ------------------------------------------------------------------------------------------
    // Notifications: one in flight at a time, as Android requires.
    // ------------------------------------------------------------------------------------------

    private fun enqueueNotification(device: BluetoothDevice, value: ByteArray) {
        notifyQueue.addLast(device to value)
        pumpNotifications()
    }

    private fun pumpNotifications() {
        while (!notifyInFlight) {
            val (device, value) = notifyQueue.removeFirstOrNull() ?: return
            val server = gattServer ?: return
            val characteristic = statusCharacteristic ?: return
            if (device.address !in subscribers) continue
            val sent = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, characteristic, false, value) ==
                        BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = value
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, characteristic, false)
                }
            } catch (_: SecurityException) {
                false
            }
            if (sent) {
                notifyInFlight = true
                log("▶ ${value.toHex()}")
                handler.postDelayed(notifyTimeout, 500)
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // System broadcasts
    // ------------------------------------------------------------------------------------------

    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            handler.post {
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        log("Bluetooth on")
                        bringUp()
                    }

                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                        tearDown()
                        if (wanted) _status.value = GripStatus.BluetoothOff
                    }
                }
            }
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice = intent.bluetoothDevice() ?: return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
            handler.post {
                // Only report on devices that are actually on our link.
                if (!connected.containsKey(device.address)) return@post
                when (state) {
                    BluetoothDevice.BOND_BONDING -> log("Pairing with ${nameOf(device)}…")
                    BluetoothDevice.BOND_BONDED -> log("Paired with ${nameOf(device)}")
                    BluetoothDevice.BOND_NONE -> if (previous == BluetoothDevice.BOND_BONDING) {
                        log("Pairing with ${nameOf(device)} failed or was declined")
                    }
                }
                updateIdleStatus()
            }
        }
    }

    private fun registerReceivers() {
        if (receiversRegistered) return
        // Both are protected system broadcasts, so exporting the receiver exposes nothing.
        ContextCompat.registerReceiver(
            appContext, adapterReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            appContext, bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiversRegistered = true
    }

    private fun unregisterReceivers() {
        if (!receiversRegistered) return
        appContext.unregisterReceiver(adapterReceiver)
        appContext.unregisterReceiver(bondReceiver)
        receiversRegistered = false
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private fun nameOf(device: BluetoothDevice): String = try {
        device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth grip"
    } catch (_: SecurityException) {
        "Bluetooth grip"
    }

    private fun isBonded(device: BluetoothDevice): Boolean = try {
        device.bondState == BluetoothDevice.BOND_BONDED
    } catch (_: SecurityException) {
        false
    }

    private fun log(line: String) {
        _log.tryEmit(line)
    }

    private fun describe(command: Command): String = when (command) {
        is Command.ButtonEvent -> "${command.button.label} ${if (command.pressed) "pressed" else "released"}"
        is Command.JogEvent -> if (command.pressed) {
            "${command.jog.label} pressed, speed ${(command.speed * 100).toInt()}%"
        } else {
            "${command.jog.label} released"
        }
        is Command.Unknown -> "unknown command"
    }
}

private fun Intent.bluetoothDevice(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
