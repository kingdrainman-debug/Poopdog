package com.gripcam.grip

import com.gripcam.grip.SonyRemoteProtocol.Button
import com.gripcam.grip.SonyRemoteProtocol.Command
import com.gripcam.grip.SonyRemoteProtocol.Jog
import com.gripcam.grip.SonyRemoteProtocol.StatusField
import com.gripcam.grip.SonyRemoteProtocol.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyRemoteProtocolTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `pairing advertisement matches a real camera in remote pairing mode`() {
        // freemote documents a camera open to pairing as 2D 01 03 00 64 00 45 31 22 EF 00.
        val payload = SonyRemoteProtocol.advertisementPayload(pairingMode = true)
        assertEquals("03 00 64 00 45 31 22 EF 00 21 40 00", payload.toHex())
    }

    @Test
    fun `non-pairing advertisement clears only the pairing-enabled bit`() {
        // ...and 22 AF 00 when it is not in pairing mode.
        val payload = SonyRemoteProtocol.advertisementPayload(pairingMode = false)
        assertEquals(0xAF, payload[7].toInt() and 0xFF)
    }

    @Test
    fun `advertisement satisfies every published remote-side check`() {
        val p = SonyRemoteProtocol.advertisementPayload(pairingMode = true)
        val withCompany = bytes(0x2D, 0x01) + p
        // furble: company 0x012D, type 0x0003, and pairing supported|enabled|remote(0x02).
        assertEquals(0x012D, (withCompany[0].toInt() and 0xFF) or ((withCompany[1].toInt() and 0xFF) shl 8))
        assertEquals(0x0003, (withCompany[2].toInt() and 0xFF) or ((withCompany[3].toInt() and 0xFF) shl 8))
        val mode22 = withCompany[9].toInt() and 0xFF
        assertEquals(0xC2, mode22 and 0xC2)
        // freemote: first 0x22 tag, then (flags & 0x42) == 0x42.
        val tagIndex = withCompany.indexOfFirst { it == 0x22.toByte() }
        assertEquals(8, tagIndex)
        assertEquals(0x42, (withCompany[tagIndex + 1].toInt() and 0x42))
        // alpharemote companion filter: type 03 00 and 0x22 tag with bit 0x40 (model ignored).
        assertEquals(0x22, p[6].toInt() and 0xFF)
        assertEquals(0x40, p[7].toInt() and 0x40)
        // ekutner: 0x04 is "remote control enabled" on protocol 0x65 bodies.
        assertEquals(0x04, p[7].toInt() and 0x04)
    }

    @Test
    fun `model code and protocol version are configurable`() {
        val p = SonyRemoteProtocol.advertisementPayload(true, "U1", SonyRemoteProtocol.PROTOCOL_VERSION_65)
        assertEquals("03 00 65 00 55 31", p.copyOfRange(0, 6).toHex())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `model code must be two characters`() {
        SonyRemoteProtocol.advertisementPayload(true, "E")
    }

    @Test
    fun `parses every button press and release`() {
        val cases = mapOf(
            bytes(0x01, 0x07) to (Button.SHUTTER_HALF to true),
            bytes(0x01, 0x06) to (Button.SHUTTER_HALF to false),
            bytes(0x01, 0x09) to (Button.SHUTTER_FULL to true),
            bytes(0x01, 0x08) to (Button.SHUTTER_FULL to false),
            bytes(0x01, 0x0F) to (Button.RECORD to true),
            bytes(0x01, 0x0E) to (Button.RECORD to false),
            bytes(0x01, 0x15) to (Button.AF_ON to true),
            bytes(0x01, 0x14) to (Button.AF_ON to false),
            bytes(0x01, 0x21) to (Button.C1 to true),
            bytes(0x01, 0x20) to (Button.C1 to false),
        )
        for ((raw, expected) in cases) {
            val cmd = SonyRemoteProtocol.parseCommands(raw).single() as Command.ButtonEvent
            assertEquals(raw.toHex(), expected.first, cmd.button)
            assertEquals(raw.toHex(), expected.second, cmd.pressed)
        }
    }

    @Test
    fun `parses jogs with speed`() {
        val tele = SonyRemoteProtocol.parseCommands(bytes(0x02, 0x45, 0x7F)).single() as Command.JogEvent
        assertEquals(Jog.ZOOM_TELE, tele.jog)
        assertTrue(tele.pressed)
        assertEquals(1f, tele.speed, 0.0001f)

        val teleSlow = SonyRemoteProtocol.parseCommands(bytes(0x02, 0x45, 0x10)).single() as Command.JogEvent
        assertEquals(0f, teleSlow.speed, 0.0001f)

        val wideRelease = SonyRemoteProtocol.parseCommands(bytes(0x02, 0x46, 0x00)).single() as Command.JogEvent
        assertEquals(Jog.ZOOM_WIDE, wideRelease.jog)
        assertFalse(wideRelease.pressed)
        assertEquals(0f, wideRelease.speed, 0.0001f)

        val far = SonyRemoteProtocol.parseCommands(bytes(0x02, 0x6D, 0x20)).single() as Command.JogEvent
        assertEquals(Jog.FOCUS_FAR, far.jog)
        assertTrue(far.pressed)
        assertEquals(0x20 / 127f, far.speed, 0.0001f)
    }

    @Test
    fun `out of range step is clamped`() {
        val e = SonyRemoteProtocol.parseCommands(bytes(0x02, 0x45, 0x8F)).single() as Command.JogEvent
        assertEquals(1f, e.speed, 0.0001f)
    }

    @Test
    fun `parses concatenated commands`() {
        val cmds = SonyRemoteProtocol.parseCommands(bytes(0x01, 0x07, 0x01, 0x09, 0x02, 0x47, 0x30))
        assertEquals(3, cmds.size)
        assertEquals(Button.SHUTTER_HALF, (cmds[0] as Command.ButtonEvent).button)
        assertEquals(Button.SHUTTER_FULL, (cmds[1] as Command.ButtonEvent).button)
        assertEquals(Jog.ZOOM_WIDE, (cmds[2] as Command.JogEvent).jog)
    }

    @Test
    fun `unknown and malformed input is reported, not thrown`() {
        assertTrue(SonyRemoteProtocol.parseCommands(bytes(0x01, 0x30)).single() is Command.Unknown)
        assertTrue(SonyRemoteProtocol.parseCommands(bytes(0x02, 0x45)).single() is Command.Unknown)
        assertTrue(SonyRemoteProtocol.parseCommands(bytes(0x00)).single() is Command.Unknown)
        assertTrue(SonyRemoteProtocol.parseCommands(bytes(0x03, 0x01, 0x02, 0x03)).single() is Command.Unknown)
        assertTrue(SonyRemoteProtocol.parseCommands(ByteArray(0)).isEmpty())
    }

    @Test
    fun `status payloads`() {
        assertArrayEquals(bytes(0x02, 0x3F, 0x20), SonyRemoteProtocol.statusPayload(StatusField.FOCUS, true))
        assertArrayEquals(bytes(0x02, 0x3F, 0x00), SonyRemoteProtocol.statusPayload(StatusField.FOCUS, false))
        assertArrayEquals(bytes(0x02, 0xA0, 0x20), SonyRemoteProtocol.statusPayload(StatusField.SHUTTER, true))
        assertArrayEquals(bytes(0x02, 0xD5, 0x00), SonyRemoteProtocol.statusPayload(StatusField.RECORDING, false))
    }
}
