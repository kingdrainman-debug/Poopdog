package com.gripcam.grip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GripActionMapperTest {

    private lateinit var mapper: GripActionMapper
    private var config = MapperConfig()
    private var now = 10_000L

    @Before
    fun setUp() {
        mapper = GripActionMapper()
        config = MapperConfig()
        now = 10_000L
    }

    /** Sends raw bytes at the current fake time, then advances the clock by [after] ms. */
    private fun send(vararg v: Int, after: Long = 0): List<CameraAction> {
        val raw = ByteArray(v.size) { v[it].toByte() }
        val out = SonyRemoteProtocol.parseCommands(raw).flatMap { mapper.map(it, config, now) }
        now += after
        return out
    }

    private fun advance(ms: Long) {
        now += ms
    }

    // ---- shutter -------------------------------------------------------------------------

    @Test
    fun `full shutter cycle focuses, presses, releases`() {
        assertEquals(listOf(CameraAction.FocusStart), send(0x01, 0x07))
        assertEquals(listOf(CameraAction.ShutterPress), send(0x01, 0x09))
        assertEquals(listOf(CameraAction.ShutterRelease), send(0x01, 0x08))
        assertEquals(listOf(CameraAction.FocusRelease), send(0x01, 0x06))
    }

    @Test
    fun `repeated half press does not refocus`() {
        send(0x01, 0x07)
        assertTrue(send(0x01, 0x07).isEmpty())
    }

    @Test
    fun `stray releases are ignored`() {
        assertTrue(send(0x01, 0x06).isEmpty())
        assertTrue(send(0x01, 0x08).isEmpty())
    }

    @Test
    fun `repeated full press while held is one press`() {
        assertEquals(listOf(CameraAction.ShutterPress), send(0x01, 0x09))
        assertTrue(send(0x01, 0x09).isEmpty())
        assertEquals(listOf(CameraAction.ShutterRelease), send(0x01, 0x08))
        assertEquals(listOf(CameraAction.ShutterPress), send(0x01, 0x09))
    }

    @Test
    fun `AF-ON and half press share one focus lock`() {
        assertEquals(listOf(CameraAction.FocusStart), send(0x01, 0x15))
        assertTrue(send(0x01, 0x07).isEmpty())
        assertTrue(send(0x01, 0x06).isEmpty())
        assertEquals(listOf(CameraAction.FocusRelease), send(0x01, 0x14))
    }

    // ---- record and C1 -------------------------------------------------------------------

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
    fun `C1 reports presses only`() {
        assertEquals(listOf(CameraAction.C1Press), send(0x01, 0x21))
        assertTrue(send(0x01, 0x20).isEmpty())
    }

    // ---- continuous zoom -----------------------------------------------------------------

    @Test
    fun `zoom lever on the 0x44 family`() {
        assertEquals(listOf(CameraAction.Zoom(1, 1f)), send(0x02, 0x45, 0x7F, after = 600))
        assertEquals(listOf(CameraAction.Zoom(0, 0f)), send(0x02, 0x44, 0x00, after = 600))
        assertEquals(listOf(CameraAction.Zoom(-1, 0f)), send(0x02, 0x47, 0x10, after = 600))
        assertEquals(listOf(CameraAction.Zoom(0, 0f)), send(0x02, 0x46, 0x00))
    }

    @Test
    fun `zoom lever on the 0x6A family is zoom by default`() {
        assertEquals(listOf(CameraAction.Zoom(1, 0x20 / 127f)), send(0x02, 0x6D, 0x20, after = 600))
        assertEquals(listOf(CameraAction.Zoom(0, 0f)), send(0x02, 0x6C, 0x00, after = 600))
        assertEquals(listOf(CameraAction.Zoom(-1, 0x20 / 127f)), send(0x02, 0x6B, 0x20, after = 600))
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
    fun `speed updates while held are not new presses`() {
        send(0x02, 0x45, 0x10, after = 100)
        assertEquals(listOf(CameraAction.Zoom(1, 1f)), send(0x02, 0x45, 0x7F, after = 100))
    }

    // ---- double press snapping -----------------------------------------------------------

    @Test
    fun `double press T snaps to the next stop`() {
        assertEquals(listOf(CameraAction.Zoom(1, 1f)), send(0x02, 0x45, 0x7F, after = 120))
        assertEquals(listOf(CameraAction.Zoom(0, 0f)), send(0x02, 0x44, 0x00, after = 150))
        assertEquals(listOf(CameraAction.ZoomSnap(1)), send(0x02, 0x45, 0x7F, after = 100))
        // The snap's own release does nothing: the animation carries on.
        assertTrue(send(0x02, 0x44, 0x00).isEmpty())
    }

    @Test
    fun `double press W snaps back`() {
        send(0x02, 0x47, 0x40, after = 100)
        send(0x02, 0x46, 0x00, after = 200)
        assertEquals(listOf(CameraAction.ZoomSnap(-1)), send(0x02, 0x47, 0x40))
    }

    @Test
    fun `double press works on the 0x6A family too`() {
        send(0x02, 0x6D, 0x20, after = 100)
        send(0x02, 0x6C, 0x00, after = 100)
        assertEquals(listOf(CameraAction.ZoomSnap(1)), send(0x02, 0x6D, 0x20))
    }

    @Test
    fun `a long hold followed by a press is not a double press`() {
        send(0x02, 0x45, 0x7F, after = 800) // held: a real zoom, not a tap
        send(0x02, 0x44, 0x00, after = 100)
        assertEquals(listOf(CameraAction.Zoom(1, 1f)), send(0x02, 0x45, 0x7F))
    }

    @Test
    fun `a slow second press is not a double press`() {
        send(0x02, 0x45, 0x7F, after = 100)
        send(0x02, 0x44, 0x00)
        advance(GripActionMapper.DOUBLE_PRESS_WINDOW_MS + 50)
        assertEquals(listOf(CameraAction.Zoom(1, 1f)), send(0x02, 0x45, 0x7F))
    }

    @Test
    fun `T then W is not a double press`() {
        send(0x02, 0x45, 0x7F, after = 100)
        send(0x02, 0x44, 0x00, after = 100)
        assertEquals(listOf(CameraAction.Zoom(-1, 1f)), send(0x02, 0x47, 0x7F))
    }

    @Test
    fun `triple press snaps once then zooms`() {
        send(0x02, 0x45, 0x7F, after = 100)
        send(0x02, 0x44, 0x00, after = 100)
        assertEquals(listOf(CameraAction.ZoomSnap(1)), send(0x02, 0x45, 0x7F, after = 100))
        send(0x02, 0x44, 0x00, after = 100)
        // Snap presses don't arm another double press.
        assertEquals(listOf(CameraAction.Zoom(1, 1f)), send(0x02, 0x45, 0x7F))
    }

    @Test
    fun `snapping can be turned off`() {
        config = config.copy(zoomSnap = false)
        send(0x02, 0x45, 0x7F, after = 100)
        send(0x02, 0x44, 0x00, after = 100)
        assertEquals(listOf(CameraAction.Zoom(1, 1f)), send(0x02, 0x45, 0x7F))
    }

    // ---- cleanup -------------------------------------------------------------------------

    @Test
    fun `reset releases everything that is held`() {
        send(0x01, 0x07)
        send(0x01, 0x09)
        send(0x02, 0x45, 0x40)
        val actions = mapper.reset()
        assertEquals(listOf(CameraAction.Zoom(0, 0f), CameraAction.ShutterRelease, CameraAction.FocusRelease), actions)
        assertTrue(mapper.reset().isEmpty())
        assertEquals(listOf(CameraAction.FocusStart), send(0x01, 0x07))
    }

    @Test
    fun `unknown commands map to nothing`() {
        assertTrue(send(0x01, 0x30).isEmpty())
    }
}
