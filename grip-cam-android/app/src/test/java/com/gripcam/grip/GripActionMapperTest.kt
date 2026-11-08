package com.gripcam.grip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GripActionMapperTest {

    private lateinit var mapper: GripActionMapper
    private var config = MapperConfig()

    @Before
    fun setUp() {
        mapper = GripActionMapper()
        config = MapperConfig()
    }

    private fun send(vararg v: Int): List<CameraAction> {
        val raw = ByteArray(v.size) { v[it].toByte() }
        return SonyRemoteProtocol.parseCommands(raw).flatMap { mapper.map(it, config) }
    }

    @Test
    fun `full shutter cycle focuses, captures once, then releases focus`() {
        assertEquals(listOf(CameraAction.FocusStart), send(0x01, 0x07))
        assertEquals(listOf(CameraAction.Capture), send(0x01, 0x09))
        assertEquals(emptyList<CameraAction>(), send(0x01, 0x08))
        assertEquals(listOf(CameraAction.FocusRelease), send(0x01, 0x06))
    }

    @Test
    fun `repeated half press does not refocus`() {
        send(0x01, 0x07)
        assertTrue(send(0x01, 0x07).isEmpty())
    }

    @Test
    fun `stray half release is ignored`() {
        assertTrue(send(0x01, 0x06).isEmpty())
    }

    @Test
    fun `full press without half press still captures`() {
        assertEquals(listOf(CameraAction.Capture), send(0x01, 0x09))
    }

    @Test
    fun `AF-ON and half press share one focus lock`() {
        assertEquals(listOf(CameraAction.FocusStart), send(0x01, 0x15))
        assertTrue(send(0x01, 0x07).isEmpty())
        assertTrue(send(0x01, 0x06).isEmpty())
        assertEquals(listOf(CameraAction.FocusRelease), send(0x01, 0x14))
    }

    @Test
    fun `record toggles once per press`() {
        assertEquals(listOf(CameraAction.ToggleRecording), send(0x01, 0x0F))
        assertTrue(send(0x01, 0x0E).isEmpty())
        assertEquals(listOf(CameraAction.ToggleRecording), send(0x01, 0x0F))
        assertTrue(send(0x01, 0x0E).isEmpty())
    }

    @Test
    fun `record release without press toggles for toggle-only senders`() {
        assertEquals(listOf(CameraAction.ToggleRecording), send(0x01, 0x0E))
    }

    @Test
    fun `zoom lever on the 0x44 family`() {
        assertEquals(listOf(CameraAction.Zoom(1, 1f)), send(0x02, 0x45, 0x7F))
        assertEquals(listOf(CameraAction.Zoom(0, 0f)), send(0x02, 0x44, 0x00))
        assertEquals(listOf(CameraAction.Zoom(-1, 0f)), send(0x02, 0x47, 0x10))
        assertEquals(listOf(CameraAction.Zoom(0, 0f)), send(0x02, 0x46, 0x00))
    }

    @Test
    fun `zoom lever on the 0x6A family is zoom by default`() {
        // Greg Leeds / CameraSync: 0x6D = zoom in (tele), 0x6B = zoom out (wide).
        assertEquals(listOf(CameraAction.Zoom(1, 0x20 / 127f)), send(0x02, 0x6D, 0x20))
        assertEquals(listOf(CameraAction.Zoom(0, 0f)), send(0x02, 0x6C, 0x00))
        assertEquals(listOf(CameraAction.Zoom(-1, 0x20 / 127f)), send(0x02, 0x6B, 0x20))
        assertEquals(listOf(CameraAction.Zoom(0, 0f)), send(0x02, 0x6A, 0x00))
    }

    @Test
    fun `focus family drives manual focus when configured`() {
        config = config.copy(focusJogsAsZoom = false)
        assertEquals(listOf(CameraAction.ManualFocus(1, 1f)), send(0x02, 0x6B, 0x7F))
        assertEquals(listOf(CameraAction.ManualFocus(0, 0f)), send(0x02, 0x6A, 0x00))
        assertEquals(listOf(CameraAction.ManualFocus(-1, 1f)), send(0x02, 0x6D, 0x7F))
        assertEquals(listOf(CameraAction.ManualFocus(0, 0f)), send(0x02, 0x6C, 0x00))
    }

    @Test
    fun `invert zoom swaps directions`() {
        config = config.copy(invertZoom = true)
        assertEquals(listOf(CameraAction.Zoom(-1, 1f)), send(0x02, 0x45, 0x7F))
    }

    @Test
    fun `releasing a jog that is not running does not stop the other one`() {
        send(0x02, 0x45, 0x40)
        assertTrue(send(0x02, 0x46, 0x00).isEmpty())
        assertEquals(listOf(CameraAction.Zoom(0, 0f)), send(0x02, 0x44, 0x00))
    }

    @Test
    fun `speed updates while held`() {
        send(0x02, 0x45, 0x10)
        assertEquals(listOf(CameraAction.Zoom(1, 1f)), send(0x02, 0x45, 0x7F))
    }

    @Test
    fun `C1 runs the configured action on press only`() {
        assertEquals(listOf(CameraAction.Custom(C1Action.SWITCH_CAMERA)), send(0x01, 0x21))
        assertTrue(send(0x01, 0x20).isEmpty())
        config = config.copy(c1Action = C1Action.NONE)
        assertTrue(send(0x01, 0x21).isEmpty())
    }

    @Test
    fun `reset releases everything that is held`() {
        send(0x01, 0x07)
        send(0x02, 0x45, 0x40)
        val actions = mapper.reset()
        assertEquals(listOf(CameraAction.Zoom(0, 0f), CameraAction.FocusRelease), actions)
        assertTrue(mapper.reset().isEmpty())
        // After a reset a new half press focuses again.
        assertEquals(listOf(CameraAction.FocusStart), send(0x01, 0x07))
    }

    @Test
    fun `unknown commands map to nothing`() {
        assertTrue(send(0x01, 0x30).isEmpty())
    }
}
