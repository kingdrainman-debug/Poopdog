package com.poopdog.vr

import com.poopdog.vr.model.QueueItem
import com.poopdog.vr.model.totalPlayTimeMs
import com.poopdog.vr.playback.QueueEngine
import com.poopdog.vr.playback.QueueEngine.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueEngineTest {
    private fun item(name: String, loops: Int = 1, dur: Long = 1000) =
        QueueItem(uri = "content://$name", title = name, loops = loops, durationMs = dur, id = name)

    /** Drive the engine as the player would and record every pass that plays. */
    private fun playAll(e: QueueEngine, maxSteps: Int = 100): List<String> {
        val played = mutableListOf(e.current.title)
        repeat(maxSteps) {
            when (e.onItemFinished()) {
                is Step.Repeat, is Step.Play -> played += e.current.title
                Step.Finished -> return played
            }
        }
        return played
    }

    @Test
    fun loopsEachItemTheConfiguredNumberOfTimesThenAdvances() {
        val e = QueueEngine(listOf(item("a", 2), item("b", 1), item("c", 3)))
        assertEquals(listOf("a", "a", "b", "c", "c", "c"), playAll(e))
    }

    @Test
    fun reportsPassNumbers() {
        val e = QueueEngine(listOf(item("a", 3), item("b")))
        assertEquals(1, e.pass)
        assertEquals(Step.Repeat(2), e.onItemFinished())
        assertEquals(Step.Repeat(3), e.onItemFinished())
        assertEquals(Step.Play(1), e.onItemFinished())
        assertEquals(1, e.pass)
        assertEquals(Step.Finished, e.onItemFinished())
    }

    @Test
    fun infiniteLoopNeverAdvancesOnItsOwn() {
        val e = QueueEngine(listOf(item("a", QueueItem.LOOP_FOREVER), item("b")))
        repeat(50) { assertTrue(e.onItemFinished() is Step.Repeat) }
        assertEquals("a", e.current.title)
        assertEquals(Step.Play(1), e.next())
        assertEquals("b", e.current.title)
    }

    @Test
    fun repeatQueueWrapsAround() {
        val e = QueueEngine(listOf(item("a"), item("b", 2)), repeatQueue = true)
        val played = playAll(e, maxSteps = 6)
        assertEquals(listOf("a", "b", "b", "a", "b", "b", "a"), played)
    }

    @Test
    fun startIndexSkipsEarlierItemsWithoutRepeat() {
        val e = QueueEngine(listOf(item("a"), item("b"), item("c")), startIndex = 1)
        assertEquals(listOf("b", "c"), playAll(e))
    }

    @Test
    fun previousRestartsFirstItemAndGoesBackOtherwise() {
        val e = QueueEngine(listOf(item("a"), item("b")))
        assertEquals(Step.Play(0), e.previous())
        e.next()
        assertEquals(Step.Play(0), e.previous())
        assertEquals("a", e.current.title)
    }

    @Test
    fun manualNextResetsLoopPass() {
        val e = QueueEngine(listOf(item("a", 3), item("b", 2)))
        e.onItemFinished() // a pass 2
        e.next()
        assertEquals("b", e.current.title)
        assertEquals(1, e.pass)
    }

    @Test
    fun loopMinusStopsAfterCurrentPassButNotBelowIt() {
        val e = QueueEngine(listOf(item("a", 5), item("b")))
        e.onItemFinished(); e.onItemFinished() // pass 3 of 5
        assertEquals(3, e.pass)
        assertEquals(4, e.adjustCurrentLoops(-1))
        assertEquals(3, e.adjustCurrentLoops(-1))
        assertEquals(3, e.adjustCurrentLoops(-1)) // can't go below the pass playing
        assertFalse(e.shouldRepeatCurrent())
        assertEquals(Step.Play(1), e.onItemFinished())
    }

    @Test
    fun loopPlusFromForeverStaysForeverAndMinusComesBack() {
        val e = QueueEngine(listOf(item("a", QueueItem.MAX_LOOPS)))
        assertEquals(QueueItem.LOOP_FOREVER, e.adjustCurrentLoops(+1))
        assertEquals(QueueItem.LOOP_FOREVER, e.adjustCurrentLoops(+1))
        assertEquals(QueueItem.MAX_LOOPS, e.adjustCurrentLoops(-1))
    }

    @Test
    fun loopMinusFromForeverDuringLatePassKeepsCurrentPassAsLast() {
        val e = QueueEngine(listOf(item("a", QueueItem.LOOP_FOREVER), item("b")))
        repeat(120) { e.onItemFinished() } // pass 121
        assertEquals(121, e.pass)
        assertEquals(121, e.adjustCurrentLoops(-1))
        assertEquals(Step.Play(1), e.onItemFinished())
    }

    @Test
    fun shufflePlaysEveryItemExactlyOncePerRound() {
        val items = (0 until 10).map { item("v$it") }
        val e = QueueEngine(items, startIndex = 4, shuffle = true, random = Random(42))
        val played = playAll(e)
        assertEquals("v4", played.first())
        assertEquals(items.map { it.title }.toSet(), played.toSet())
        assertEquals(10, played.size)
    }

    @Test
    fun shuffleRepeatReshufflesWithoutImmediateRepeat() {
        val items = (0 until 5).map { item("v$it") }
        val e = QueueEngine(items, shuffle = true, repeatQueue = true, random = Random(7))
        val played = playAll(e, maxSteps = 40)
        for (i in 1 until played.size) assertTrue("back-to-back repeat at $i", played[i] != played[i - 1])
        played.chunked(5).filter { it.size == 5 }.forEach { assertEquals(5, it.toSet().size) }
    }

    @Test
    fun restartBeginsAgainAfterFinishing() {
        val e = QueueEngine(listOf(item("a"), item("b")))
        playAll(e)
        assertEquals(Step.Play(0), e.restart())
        assertEquals(1, e.pass)
    }

    @Test
    fun peekNextIndex() {
        val e = QueueEngine(listOf(item("a", 2), item("b")))
        assertEquals(0, e.peekNextIndex())
        e.onItemFinished()
        assertEquals(1, e.peekNextIndex())
        e.onItemFinished()
        assertNull(e.peekNextIndex())
    }

    @Test
    fun stepLoopsAndTotals() {
        assertEquals(2, QueueItem.stepLoops(1, 1))
        assertEquals(1, QueueItem.stepLoops(1, -1))
        assertEquals(QueueItem.LOOP_FOREVER, QueueItem.stepLoops(99, 1))
        assertEquals("×3", QueueItem.loopsLabel(3))
        assertEquals("∞", QueueItem.loopsLabel(0))
        assertEquals(5000L, listOf(item("a", 2, 1000), item("b", 3, 1000)).totalPlayTimeMs())
        assertNull(listOf(item("a", 0)).totalPlayTimeMs())
    }
}
