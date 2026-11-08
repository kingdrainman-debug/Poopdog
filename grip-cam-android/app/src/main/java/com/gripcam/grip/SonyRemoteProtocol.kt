package com.gripcam.grip

import java.util.UUID

/**
 * The Bluetooth LE protocol spoken between a Sony camera and its Bluetooth remotes
 * (RMT-P1BT remote commander, GP-VPT2BT shooting grip).
 *
 * The remote is the BLE *central*: it scans for a camera, connects, bonds, subscribes to
 * [STATUS_CHARACTERISTIC] and writes button presses to [COMMAND_CHARACTERISTIC]. The camera
 * is the *peripheral*. GripCam therefore makes the phone look like a camera.
 *
 * Nothing here is official Sony documentation. It is assembled from community reverse
 * engineering (Greg Leeds; coral/freemote; Staacks/alpharemote; gkoh/furble;
 * sarnau/sony-camera-protocol; ekutner/camera-gps-link), cross-checked between sources.
 * Where the sources disagree the code accepts every reported variant; see the README.
 */
object SonyRemoteProtocol {

    /** Sony Corporation's Bluetooth SIG company identifier. */
    const val SONY_COMPANY_ID = 0x012D

    /** The "remote commander" service every Sony camera with Bluetooth remote support exposes. */
    val REMOTE_SERVICE: UUID = UUID.fromString("8000ff00-ff00-ffff-ffff-ffffffffffff")

    /** Remote -> camera: button and jog commands (write). 16-bit UUID on the SIG base. */
    val COMMAND_CHARACTERISTIC: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")

    /** Camera -> remote: focus / shutter / recording flags (notify). */
    val STATUS_CHARACTERISTIC: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

    /** Standard Client Characteristic Configuration Descriptor. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ---------------------------------------------------------------------------------------
    // Advertisement
    // ---------------------------------------------------------------------------------------

    private const val DEVICE_TYPE_CAMERA_LO = 0x03
    private const val DEVICE_TYPE_CAMERA_HI = 0x00

    private const val TAG_PAIRING = 0x22
    private const val TAG_POWER = 0x21

    // Tag 0x22 bits. Remotes check "pairing enabled" plus a "remote control enabled" bit; the
    // sources disagree on whether that is 0x02 (freemote/furble) or 0x04 (alpharemote, on an
    // a6400, and ekutner on protocol 0x65 bodies), so both are set. A real camera in remote
    // pairing mode advertises 0xEF, and 0xAF otherwise, which is exactly what this produces.
    private const val P22_PAIRING_SUPPORTED = 0x80
    private const val P22_PAIRING_ENABLED = 0x40
    private const val P22_LOCATION_SUPPORTED = 0x20
    private const val P22_UNKNOWN_08 = 0x08
    private const val P22_REMOTE_ENABLED_04 = 0x04
    private const val P22_REMOTE_ENABLED_02 = 0x02
    private const val P22_UNKNOWN_01 = 0x01

    // Tag 0x21 bits.
    private const val P21_CAMERA_ON = 0x40

    /** Protocol byte used by bodies paired with Imaging Edge (a6400 era). */
    const val PROTOCOL_VERSION_64 = 0x64

    /** Protocol byte used by bodies from ~2023 that pair with Creators' App. */
    const val PROTOCOL_VERSION_65 = 0x65

    /** Two-character ASCII "model code". "E1" is what E-mount bodies (a6400, A7 III) send. */
    const val DEFAULT_MODEL_CODE = "E1"

    /**
     * Manufacturer-specific data, *without* the company ID (Android's
     * `AdvertiseData.Builder.addManufacturerData(SONY_COMPANY_ID, payload)` prepends it).
     *
     * Layout (after `2D 01`):
     * ```
     * 03 00     device type: camera
     * 64        protocol version
     * 00        reserved
     * 45 31     model code, ASCII ("E1")
     * 22 xx 00  tag 0x22: pairing / remote flags
     * 21 xx 00  tag 0x21: power / Wi-Fi flags
     * ```
     */
    fun advertisementPayload(
        pairingMode: Boolean,
        modelCode: String = DEFAULT_MODEL_CODE,
        protocolVersion: Int = PROTOCOL_VERSION_64,
    ): ByteArray {
        require(modelCode.length == 2 && modelCode.all { it.code in 0x20..0x7E }) {
            "Model code must be two printable ASCII characters"
        }
        return byteArrayOf(
            DEVICE_TYPE_CAMERA_LO.toByte(), DEVICE_TYPE_CAMERA_HI.toByte(),
            protocolVersion.toByte(), 0x00,
            modelCode[0].code.toByte(), modelCode[1].code.toByte(),
            TAG_PAIRING.toByte(), pairingFlags(pairingMode).toByte(), 0x00,
            TAG_POWER.toByte(), P21_CAMERA_ON.toByte(), 0x00,
        )
    }

    /** Value of the tag-0x22 byte. 0xEF while accepting a new remote, 0xAF otherwise. */
    fun pairingFlags(pairingMode: Boolean): Int {
        var flags = P22_PAIRING_SUPPORTED or P22_LOCATION_SUPPORTED or P22_UNKNOWN_08 or
            P22_REMOTE_ENABLED_04 or P22_REMOTE_ENABLED_02 or P22_UNKNOWN_01
        if (pairingMode) flags = flags or P22_PAIRING_ENABLED
        return flags
    }

    // ---------------------------------------------------------------------------------------
    // Commands (remote -> camera, written to FF01)
    // ---------------------------------------------------------------------------------------

    /**
     * Two-byte button commands: `01 <code>`, code = base when released, base | 1 when pressed.
     */
    enum class Button(val base: Int, val label: String) {
        SHUTTER_HALF(0x06, "Shutter half"),
        SHUTTER_FULL(0x08, "Shutter full"),
        RECORD(0x0E, "REC"),
        AF_ON(0x14, "AF-ON"),
        C1(0x20, "C1"),
    }

    /**
     * Three-byte jog commands: `02 <code> <step>`, code = base when released, base | 1 when
     * pressed; step is a speed, 0x00 on release.
     *
     * Names follow Staacks/alpharemote and sarnau/sony-camera-protocol (0x44 zoom, 0x6A focus).
     * Greg Leeds and rock3r/CameraSync list the two families the other way round; the mapper
     * can treat the focus family as zoom so a grip works whichever is right.
     */
    enum class Jog(val base: Int, val minStep: Int, val maxStep: Int, val label: String) {
        ZOOM_TELE(0x44, 0x10, 0x7F, "Zoom T"),
        ZOOM_WIDE(0x46, 0x10, 0x7F, "Zoom W"),
        FOCUS_NEAR(0x6A, 0x00, 0x7F, "Focus near"),
        FOCUS_FAR(0x6C, 0x00, 0x7F, "Focus far"),
    }

    sealed interface Command {
        val raw: ByteArray

        data class ButtonEvent(
            val button: Button,
            val pressed: Boolean,
            override val raw: ByteArray,
        ) : Command {
            override fun equals(other: Any?) =
                other is ButtonEvent && other.button == button && other.pressed == pressed &&
                    other.raw.contentEquals(raw)

            override fun hashCode() = 31 * (31 * button.hashCode() + pressed.hashCode()) + raw.contentHashCode()
        }

        data class JogEvent(
            val jog: Jog,
            val pressed: Boolean,
            val step: Int,
            override val raw: ByteArray,
        ) : Command {
            /** Speed in 0..1 derived from the step byte (0 when released). */
            val speed: Float
                get() {
                    if (!pressed) return 0f
                    val span = (jog.maxStep - jog.minStep).coerceAtLeast(1)
                    return ((step - jog.minStep).toFloat() / span).coerceIn(0f, 1f)
                }

            override fun equals(other: Any?) =
                other is JogEvent && other.jog == jog && other.pressed == pressed &&
                    other.step == step && other.raw.contentEquals(raw)

            override fun hashCode() =
                31 * (31 * (31 * jog.hashCode() + pressed.hashCode()) + step) + raw.contentHashCode()
        }

        data class Unknown(override val raw: ByteArray) : Command {
            override fun equals(other: Any?) = other is Unknown && other.raw.contentEquals(raw)
            override fun hashCode() = raw.contentHashCode()
        }
    }

    /**
     * Parses one write to FF01. A write normally carries one length-prefixed command; several
     * concatenated commands are also accepted. Anything unrecognised comes back as [Command.Unknown]
     * so it can be shown in the control log.
     */
    fun parseCommands(value: ByteArray): List<Command> {
        if (value.isEmpty()) return emptyList()
        val out = mutableListOf<Command>()
        var i = 0
        while (i < value.size) {
            val len = value[i].toInt() and 0xFF
            val end = i + 1 + len
            if (len == 0 || end > value.size) {
                out += Command.Unknown(value.copyOfRange(i, value.size))
                break
            }
            val chunk = value.copyOfRange(i, end)
            out += parseOne(chunk)
            i = end
        }
        return out
    }

    private fun parseOne(chunk: ByteArray): Command {
        val len = chunk[0].toInt() and 0xFF
        val code = chunk[1].toInt() and 0xFF
        val base = code and 0xFE
        val pressed = (code and 0x01) == 0x01
        if (len == 1) {
            val button = Button.entries.firstOrNull { it.base == base }
            if (button != null) return Command.ButtonEvent(button, pressed, chunk)
        } else if (len == 2) {
            val jog = Jog.entries.firstOrNull { it.base == base }
            if (jog != null) return Command.JogEvent(jog, pressed, chunk[2].toInt() and 0xFF, chunk)
        }
        return Command.Unknown(chunk)
    }

    // ---------------------------------------------------------------------------------------
    // Status (camera -> remote, notified on FF02)
    // ---------------------------------------------------------------------------------------

    /** `02 <field> <flag>`; bit 0x20 of the flag byte is the boolean. */
    enum class StatusField(val code: Int, val label: String) {
        FOCUS(0x3F, "focus"),
        SHUTTER(0xA0, "shutter"),
        RECORDING(0xD5, "recording"),
    }

    fun statusPayload(field: StatusField, active: Boolean): ByteArray =
        byteArrayOf(0x02, field.code.toByte(), (if (active) 0x20 else 0x00).toByte())

    fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
