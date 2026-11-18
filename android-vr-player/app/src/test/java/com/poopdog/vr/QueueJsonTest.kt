package com.poopdog.vr

import com.poopdog.vr.data.QueueRepository
import com.poopdog.vr.model.Projection
import com.poopdog.vr.model.QueueItem
import com.poopdog.vr.model.StereoLayout
import com.poopdog.vr.model.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueJsonTest {
    @Test
    fun queueItemsSurviveJsonRoundTrip() {
        val items = listOf(
            QueueItem("content://media/1", "Beach_180_LR", loops = 3,
                format = VideoFormat(Projection.EQUIRECT_180, StereoLayout.SBS), durationMs = 61_000,
                width = 5760, height = 2880, subtitleUri = "content://subs/1"),
            QueueItem("https://example.com/x.m3u8", "Stream", loops = QueueItem.LOOP_FOREVER),
        )
        val json = QueueRepository.itemsToJson(items).toString()
        assertEquals(items, QueueRepository.itemsFromJson(json))
    }
}
