package com.gripcam.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gripcam.camera.CameraCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("gripcam", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `defaults when nothing is stored`() {
        val s = SettingsStore(context).settings.value
        assertEquals(VideoResolution.FHD, s[Prefs.VIDEO_RESOLUTION])
        assertEquals(Stabilization.STANDARD, s[Prefs.STABILIZATION])
        assertEquals(GripButtonAction.SWITCH_CAMERA, s[Prefs.C1_ACTION])
        assertTrue(s[Prefs.ZOOM_SNAP])
        assertEquals(95f, s[Prefs.JPEG_QUALITY])
    }

    @Test
    fun `values survive a restart`() {
        val store = SettingsStore(context)
        store.set(Prefs.VIDEO_RESOLUTION, VideoResolution.UHD)
        store.set(Prefs.HDR_VIDEO, true)
        store.set(Prefs.ZOOM_SPEED, 1.5f)
        store.setAny(Prefs.C1_DOUBLE, GripButtonAction.TOGGLE_TORCH)

        val reloaded = SettingsStore(context).settings.value
        assertEquals(VideoResolution.UHD, reloaded[Prefs.VIDEO_RESOLUTION])
        assertTrue(reloaded[Prefs.HDR_VIDEO])
        assertEquals(1.5f, reloaded[Prefs.ZOOM_SPEED])
        assertEquals(GripButtonAction.TOGGLE_TORCH, reloaded[Prefs.C1_DOUBLE])
    }

    @Test
    fun `settings from the first version carry over`() {
        context.getSharedPreferences("gripcam", Context.MODE_PRIVATE).edit()
            .putString("c1_action", "TOGGLE_TORCH")
            .putBoolean("invert_zoom", true)
            .putFloat("zoom_speed", 0.5f)
            .putString("model_code", "U1")
            .putInt("protocol_version", 0x65) // old int key: ignored, not a crash
            .putBoolean("show_log", true)
            .commit()
        val s = SettingsStore(context).settings.value
        assertEquals(GripButtonAction.TOGGLE_TORCH, s[Prefs.C1_ACTION])
        assertTrue(s[Prefs.INVERT_ZOOM])
        assertEquals(0.5f, s[Prefs.ZOOM_SPEED])
        assertEquals(ModelCode.U1, s[Prefs.MODEL_CODE])
        assertEquals(ProtocolVersion.V64, s[Prefs.PROTOCOL])
        assertTrue(s[Prefs.SHOW_LOG])
    }

    @Test
    fun `corrupt or out of range values fall back safely`() {
        context.getSharedPreferences("gripcam", Context.MODE_PRIVATE).edit()
            .putString("video_resolution", "8K")
            .putString("hdr_video", "yes") // wrong type
            .putFloat("jpeg_quality", 400f)
            .commit()
        val s = SettingsStore(context).settings.value
        assertEquals(VideoResolution.FHD, s[Prefs.VIDEO_RESOLUTION])
        assertFalse(s[Prefs.HDR_VIDEO])
        assertEquals(100f, s[Prefs.JPEG_QUALITY])
    }

    @Test
    fun `reset restores every default`() {
        val store = SettingsStore(context)
        store.set(Prefs.GRID, GridStyle.GOLDEN)
        store.resetAll()
        assertEquals(GridStyle.OFF, store.settings.value[Prefs.GRID])
        assertEquals(GridStyle.OFF, SettingsStore(context).settings.value[Prefs.GRID])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setAny rejects values from another setting`() {
        SettingsStore(context).setAny(Prefs.GRID, VideoResolution.UHD)
    }

    @Test
    fun `catalog keys are unique and every setting is reachable`() {
        val keys = SettingsCatalog.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(Prefs.ZOOM_STOPS in SettingsCatalog.all)
        assertTrue(Prefs.STABILIZATION in SettingsCatalog.all)
    }

    @Test
    fun `options follow the camera's capabilities`() {
        val caps = CameraCapabilities(
            known = true,
            videoResolutions = setOf(VideoResolution.FHD, VideoResolution.HD),
            videoStabilization = true,
            previewStabilization = false,
            frameRates = setOf(FrameRate.FPS_30),
            hdrVideo = false,
        )
        assertEquals(listOf(VideoResolution.FHD, VideoResolution.HD), Prefs.VIDEO_RESOLUTION.available(caps))
        assertEquals(listOf(Stabilization.OFF, Stabilization.STANDARD), Prefs.STABILIZATION.available(caps))
        assertEquals(listOf(FrameRate.FPS_30), Prefs.FRAME_RATE.available(caps))
        assertFalse(Prefs.HDR_VIDEO.visibleWhen(caps))
        // Before the camera is open, everything is offered.
        assertEquals(VideoResolution.entries, Prefs.VIDEO_RESOLUTION.available(CameraCapabilities.UNKNOWN))
    }
}
