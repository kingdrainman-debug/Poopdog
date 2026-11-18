package com.poopdog.vr

import com.poopdog.vr.model.AspectMode
import com.poopdog.vr.model.FormatDetector
import com.poopdog.vr.model.FormatDetector.MetaStereo
import com.poopdog.vr.model.Projection
import com.poopdog.vr.model.StereoLayout
import com.poopdog.vr.model.VideoFormat
import com.poopdog.vr.model.flatEyeAspect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatDetectorTest {
    private fun f(p: Projection, l: StereoLayout, swap: Boolean = false) = VideoFormat(p, l, swapEyes = swap)

    @Test
    fun deoVrStyleTags() {
        assertEquals(f(Projection.EQUIRECT_180, StereoLayout.SBS), FormatDetector.detect("Beach_180_LR.mp4"))
        assertEquals(f(Projection.EQUIRECT_360, StereoLayout.TB), FormatDetector.detect("city-360-TB.mp4"))
        assertEquals(f(Projection.EQUIRECT_360, StereoLayout.MONO), FormatDetector.detect("tour_360_mono.mp4"))
        assertEquals(f(Projection.FISHEYE_190, StereoLayout.SBS), FormatDetector.detect("trip_FISHEYE190_LR.mp4"))
        assertEquals(f(Projection.FISHEYE_200, StereoLayout.SBS), FormatDetector.detect("show_MKX200.mp4"))
        assertEquals(f(Projection.FISHEYE_180, StereoLayout.SBS), FormatDetector.detect("x_fisheye_sbs.mkv"))
        assertEquals(f(Projection.EQUIRECT_180, StereoLayout.SBS, swap = true), FormatDetector.detect("clip_180_RL.mp4"))
        assertEquals(f(Projection.EQUIRECT_360, StereoLayout.TB, swap = true), FormatDetector.detect("clip_360_BT.mp4"))
    }

    @Test
    fun flat3dMovieTags() {
        assertEquals(f(Projection.FLAT, StereoLayout.SBS), FormatDetector.detect("Avatar.2009.1080p.HSBS.mkv"))
        assertEquals(f(Projection.FLAT, StereoLayout.SBS), FormatDetector.detect("Movie.3D.Half-SBS.mkv"))
        assertEquals(f(Projection.FLAT, StereoLayout.TB), FormatDetector.detect("Movie 3D H-OU.mkv"))
        assertEquals(f(Projection.FLAT, StereoLayout.TB), FormatDetector.detect("movie_3dv.mp4"))
        assertEquals(f(Projection.FLAT, StereoLayout.SBS), FormatDetector.detect("Movie.3D.mkv"))
        assertEquals(f(Projection.FLAT, StereoLayout.SBS), FormatDetector.detect("my clip_3D_SBS.mp4"))
    }

    @Test
    fun bareLrOnSquarePerEyeHighResIs180() {
        assertEquals(f(Projection.EQUIRECT_180, StereoLayout.SBS), FormatDetector.detect("scene_LR.mp4", 5760, 2880))
        assertEquals(f(Projection.FLAT, StereoLayout.SBS), FormatDetector.detect("scene_LR.mp4", 3840, 1080))
    }

    @Test
    fun projectionWithoutStereoUsesShape() {
        assertEquals(f(Projection.EQUIRECT_180, StereoLayout.SBS), FormatDetector.detect("vr180 trip.mp4", 5760, 2880))
        assertEquals(f(Projection.EQUIRECT_180, StereoLayout.MONO), FormatDetector.detect("trip_180.mp4", 3840, 3840))
        assertEquals(f(Projection.EQUIRECT_360, StereoLayout.TB), FormatDetector.detect("trip_360.mp4", 4096, 4096))
        assertEquals(f(Projection.EQUIRECT_360, StereoLayout.MONO), FormatDetector.detect("trip_360.mp4", 5760, 2880))
    }

    @Test
    fun untaggedFilesFallBackToShape() {
        assertEquals(VideoFormat.FLAT_2D, FormatDetector.detect("holiday.mp4", 1920, 1080))
        assertEquals(VideoFormat.FLAT_2D, FormatDetector.detect("insta.mp4", 1080, 1080))
        assertEquals(VideoFormat.FLAT_2D, FormatDetector.detect("VID_2024.mp4", 1080, 1920))
        assertEquals(f(Projection.EQUIRECT_360, StereoLayout.MONO), FormatDetector.detect("VID_0001.mp4", 5760, 2880))
        assertEquals(f(Projection.EQUIRECT_360, StereoLayout.TB), FormatDetector.detect("VID_0002.mp4", 3840, 3840))
        assertEquals(f(Projection.FLAT, StereoLayout.SBS), FormatDetector.detect("VID_0003.mp4", 3840, 1080))
    }

    @Test
    fun containerMetadataRefinesUntaggedFiles() {
        assertEquals(f(Projection.FLAT, StereoLayout.TB),
            FormatDetector.detect("clip.mp4", 1920, 2160, MetaStereo.TOP_BOTTOM))
        assertEquals(f(Projection.EQUIRECT_180, StereoLayout.SBS),
            FormatDetector.detect("clip.mp4", 5760, 2880, MetaStereo.LEFT_RIGHT, metaSpherical = true))
        assertEquals(f(Projection.EQUIRECT_360, StereoLayout.MONO),
            FormatDetector.detect("clip.mp4", 3840, 1920, MetaStereo.MONO, metaSpherical = true))
    }

    @Test
    fun wordsContainingTagsDoNotTrigger() {
        // "tb" inside "football", "lr" inside "world", "360" inside "13600" must not match.
        assertEquals(VideoFormat.FLAT_2D, FormatDetector.detect("football world cup 13600.mp4", 1920, 1080))
    }

    @Test
    fun formatKeyRoundTrip() {
        for (fmt in VideoFormat.CYCLE + VideoFormat(Projection.EQUIRECT_180, StereoLayout.SBS, swapEyes = true)) {
            assertEquals(fmt, VideoFormat.fromKey(fmt.key()))
        }
        assertEquals(null, VideoFormat.fromKey("garbage"))
    }

    @Test
    fun formatCycleVisitsEveryEntry() {
        var cur = VideoFormat.CYCLE.first()
        val seen = mutableSetOf<VideoFormat>()
        repeat(VideoFormat.CYCLE.size) { seen += cur; cur = VideoFormat.nextInCycle(cur) }
        assertEquals(VideoFormat.CYCLE.toSet(), seen)
        assertEquals(VideoFormat.CYCLE.first(), cur)
    }

    @Test
    fun flatEyeAspectHandlesHalfAndFullStereo() {
        val sbs = f(Projection.FLAT, StereoLayout.SBS)
        val tb = f(Projection.FLAT, StereoLayout.TB)
        assertEquals(16f / 9f, flatEyeAspect(sbs, 1920f / 1080f), 0.01f)  // half SBS
        assertEquals(16f / 9f, flatEyeAspect(sbs, 3840f / 1080f), 0.01f)  // full SBS
        assertEquals(16f / 9f, flatEyeAspect(tb, 1920f / 1080f), 0.01f)   // half TB
        assertEquals(16f / 9f, flatEyeAspect(tb, 1920f / 2160f), 0.01f)   // full TB
        assertEquals(4f / 3f, flatEyeAspect(VideoFormat.FLAT_2D, 2f, AspectMode.R4_3), 0.001f)
        assertTrue(flatEyeAspect(VideoFormat.FLAT_2D, 0f) > 1f)
    }
}
