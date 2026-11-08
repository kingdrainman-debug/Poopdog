package com.gripcam.camera

import com.gripcam.settings.PhotoEnhancement
import com.gripcam.settings.Stabilization
import com.gripcam.settings.VideoBitrate
import com.gripcam.settings.VideoResolution
import com.gripcam.ui.LevelMath
import com.gripcam.ui.meterLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomMathTest {
    private val standard = listOf(1f, 3f, 6f)

    @Test
    fun `stops fit the camera and add the ultra-wide`() {
        assertEquals(listOf(0.6f, 1f, 3f, 6f), ZoomMath.stops(standard, 0.6f, 10f))
        assertEquals(listOf(1f, 3f, 6f), ZoomMath.stops(standard, 1f, 8f))
    }

    @Test
    fun `stops beyond the camera are clamped to its maximum`() {
        assertEquals(listOf(1f, 3f, 5f), ZoomMath.stops(standard, 1f, 5f))
        assertEquals(listOf(1f, 2f), ZoomMath.stops(standard, 1f, 2f))
    }

    @Test
    fun `next stop up and down`() {
        val stops = listOf(0.6f, 1f, 3f, 6f)
        assertEquals(3f, ZoomMath.next(1f, stops, 1))
        assertEquals(6f, ZoomMath.next(3f, stops, 1))
        assertNull(ZoomMath.next(6f, stops, 1))
        assertEquals(1f, ZoomMath.next(3f, stops, -1))
        assertEquals(0.6f, ZoomMath.next(1f, stops, -1))
        // From between stops: the next one in that direction.
        assertEquals(3f, ZoomMath.next(1.4f, stops, 1))
        assertEquals(1f, ZoomMath.next(1.4f, stops, -1))
        // Just shy of a stop counts as being on it.
        assertEquals(6f, ZoomMath.next(2.95f, stops, 1))
    }

    @Test
    fun `cycle wraps to the widest`() {
        val stops = listOf(1f, 3f, 6f)
        assertEquals(3f, ZoomMath.cycle(1f, stops))
        assertEquals(1f, ZoomMath.cycle(6f, stops))
    }

    @Test
    fun `nearest stop and labels`() {
        val stops = listOf(0.6f, 1f, 3f, 6f)
        assertEquals(2, ZoomMath.nearest(3.1f, stops))
        assertEquals(-1, ZoomMath.nearest(2f, stops))
        assertEquals("0.6", ZoomMath.label(0.6f))
        assertEquals("3", ZoomMath.label(3f))
        assertEquals("2.4", ZoomMath.label(2.4f))
    }
}

class BindPlannerTest {

    @Test
    fun `everything combined first, then the split binding, then fewer features`() {
        val desired = Features(Stabilization.STEADY, hdrVideo = true, fps = 60)
        val plan = BindPlanner.plan(desired, Layout.VIDEO)
        assertEquals(BindCandidate(Layout.COMBINED, desired), plan[0])
        assertEquals(BindCandidate(Layout.VIDEO, desired), plan[1])
        // HDR goes first, then 60 fps, then Steady -> Standard -> Off.
        assertEquals(BindCandidate(Layout.COMBINED, desired.copy(hdrVideo = false)), plan[2])
        assertEquals(BindCandidate(Layout.COMBINED, desired.copy(hdrVideo = false, fps = 30)), plan[4])
        assertEquals(
            BindCandidate(Layout.COMBINED, desired.copy(hdrVideo = false, fps = 30, stabilization = Stabilization.STANDARD)),
            plan[6],
        )
        // The very last resort is a plain binding with the camera's default frame rate.
        assertEquals(BindCandidate(Layout.VIDEO, Features()), plan.last())
    }

    @Test
    fun `photo mode tries the night extension on a photo-only binding first`() {
        val desired = Features(Stabilization.STANDARD, fps = 30, enhancement = PhotoEnhancement.NIGHT)
        val plan = BindPlanner.plan(desired, Layout.PHOTO)
        assertEquals(BindCandidate(Layout.PHOTO, Features(enhancement = PhotoEnhancement.NIGHT)), plan.first())
        // Extensions never appear on a combined binding.
        assertTrue(plan.none { it.layout == Layout.COMBINED && it.features.enhancement != PhotoEnhancement.NONE })
    }

    @Test
    fun `video mode never asks for photo-only features`() {
        val plan = BindPlanner.plan(Features(raw = true, ultraHdr = true, enhancement = PhotoEnhancement.HDR), Layout.VIDEO)
        val videoOnly = plan.filter { it.layout == Layout.VIDEO }
        assertTrue(videoOnly.isNotEmpty())
        assertTrue(videoOnly.all { !it.features.raw && !it.features.ultraHdr && it.features.enhancement == PhotoEnhancement.NONE })
    }

    @Test
    fun `photo-only bindings drop video features and preview stabilization`() {
        val plan = BindPlanner.plan(Features(Stabilization.STEADY, hdrVideo = true, fps = 60), Layout.PHOTO)
        val photo = plan.filter { it.layout == Layout.PHOTO }
        assertEquals(1, photo.size) // all levels collapse into one plain photo binding
        assertEquals(Features(), photo.single().features)
    }

    @Test
    fun `no duplicate candidates`() {
        val plan = BindPlanner.plan(Features(Stabilization.STANDARD, fps = 30), Layout.PHOTO)
        assertEquals(plan.size, plan.toSet().size)
    }
}

class VideoMathTest {
    @Test
    fun `standard bitrate leaves the encoder default alone`() {
        assertNull(VideoMath.targetBitrate(VideoResolution.UHD, 30, VideoBitrate.STANDARD))
        assertEquals(27_200_000, VideoMath.targetBitrate(VideoResolution.FHD, 30, VideoBitrate.HIGH))
    }

    @Test
    fun `60 fps needs more data`() {
        assertTrue(VideoMath.estimatedBitrate(VideoResolution.FHD, 60, VideoBitrate.STANDARD) > VideoMath.estimatedBitrate(VideoResolution.FHD, 30, VideoBitrate.STANDARD))
    }

    @Test
    fun `remaining time and formatting`() {
        // 10 GB at ~17 Mbps video + audio is a little over 1 h 17 min.
        val seconds = VideoMath.remainingSeconds(10_000_000_000L, 17_000_000L)
        assertEquals(4670L, seconds)
        assertEquals("1 h 17 min", VideoMath.formatDuration(seconds))
        assertEquals("12 min", VideoMath.formatDuration(720))
        assertEquals("40 s", VideoMath.formatDuration(40))
        assertEquals(0L, VideoMath.remainingSeconds(0, 17_000_000L))
    }
}

class LevelMathTest {
    private val g = 9.81f

    @Test
    fun `upright portrait is level`() {
        val a = LevelMath.horizonAngle(0f, g, 0f)!!
        assertEquals(0f, a, 0.01f)
        assertEquals(0f, LevelMath.deviation(a), 0.01f)
    }

    @Test
    fun `tilting the phone clockwise turns the horizon counter-clockwise on screen`() {
        val tilt = Math.toRadians(5.0)
        val a = LevelMath.horizonAngle((-g * Math.sin(tilt)).toFloat(), (g * Math.cos(tilt)).toFloat(), 0f)!!
        assertEquals(-5f, a, 0.01f)
        assertEquals(-5f, LevelMath.deviation(a), 0.01f)
    }

    @Test
    fun `landscape on the grip snaps to the side axis`() {
        val a = LevelMath.horizonAngle(g, 0.3f, 0f)!!
        assertEquals(90f, LevelMath.snap(a), 0.01f)
        assertTrue(kotlin.math.abs(LevelMath.deviation(a)) < 2f)
    }

    @Test
    fun `lying flat has no horizon`() {
        assertNull(LevelMath.horizonAngle(0.2f, 0.3f, g))
    }

    @Test
    fun `audio meter scale`() {
        assertEquals(0f, meterLevel(0f), 0f)
        assertEquals(1f, meterLevel(1f), 0.001f)
        assertEquals(0.5f, meterLevel(0.031623f), 0.001f) // -30 dB is halfway
        assertEquals(0f, meterLevel(0.001f), 0.001f) // -60 dB is the floor
        assertEquals(0f, meterLevel(0.0001f), 0f)
    }
}
