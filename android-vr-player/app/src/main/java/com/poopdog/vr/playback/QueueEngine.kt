package com.poopdog.vr.playback

import com.poopdog.vr.model.QueueItem
import kotlin.random.Random

/**
 * Pure (Android-free) state machine behind hands-free queue playback:
 * which item plays, which pass of its loop count we are on, and what happens when a
 * video ends. The player layer just executes the returned [Step]s.
 */
class QueueEngine(
    items: List<QueueItem>,
    startIndex: Int = 0,
    repeatQueue: Boolean = false,
    shuffle: Boolean = false,
    private val random: Random = Random.Default,
) {
    sealed interface Step {
        /** Start playing queue item [index] from the beginning (pass 1). */
        data class Play(val index: Int) : Step
        /** Replay the current item; [pass] is the 1-based pass now starting. */
        data class Repeat(val pass: Int) : Step
        /** Nothing left to play. */
        data object Finished : Step
    }

    private val items: MutableList<QueueItem> = items.toMutableList()
    private var order: IntArray = IntArray(0)
    private var orderPos = 0

    var repeatQueue: Boolean = repeatQueue
    var shuffle: Boolean = shuffle
        set(value) {
            if (field == value) return
            field = value
            if (items.isNotEmpty()) rebuildOrder(currentIndex)
        }

    /** 1-based pass of the current item (1 = first play). */
    var pass: Int = 1
        private set

    init {
        require(items.isNotEmpty()) { "Queue is empty" }
        rebuildOrder(startIndex.coerceIn(0, items.size - 1))
    }

    val size: Int get() = items.size
    val currentIndex: Int get() = order[orderPos]
    val current: QueueItem get() = items[currentIndex]
    val positionInOrder: Int get() = orderPos
    fun item(index: Int): QueueItem = items[index]

    /** Loops configured for the current item (0 = forever). */
    val loopsTotal: Int get() = current.loops

    /** True while the current item still has passes left after this one. */
    fun shouldRepeatCurrent(): Boolean = loopsTotal == QueueItem.LOOP_FOREVER || pass < loopsTotal

    /** Called when the current video plays to its end (or loops around). */
    fun onItemFinished(): Step {
        if (shouldRepeatCurrent()) {
            pass++
            return Step.Repeat(pass)
        }
        return advance(wrapAllowed = repeatQueue)
    }

    /** Peek at what [onItemFinished] would start next, without changing state. */
    fun peekNextIndex(): Int? {
        if (shouldRepeatCurrent()) return currentIndex
        if (orderPos + 1 < order.size) return order[orderPos + 1]
        return if (repeatQueue) order[0] else null
    }

    /** Manual skip forward. Returns [Step.Finished] at the end of a non-repeating queue. */
    fun next(): Step = advance(wrapAllowed = repeatQueue)

    /** Manual skip back. At the first item of a non-repeating queue it restarts that item. */
    fun previous(): Step {
        pass = 1
        if (orderPos > 0) {
            orderPos--
        } else if (repeatQueue) {
            orderPos = order.size - 1
        }
        return Step.Play(currentIndex)
    }

    /** Start the queue over from its first item (re-shuffling if shuffle is on). */
    fun restart(): Step {
        if (shuffle && items.size > 1) rebuildOrder(items.indices.random(random))
        orderPos = 0
        pass = 1
        return Step.Play(currentIndex)
    }

    fun jumpTo(index: Int): Step {
        require(index in items.indices)
        val pos = order.indexOf(index)
        if (pos >= 0) orderPos = pos
        pass = 1
        return Step.Play(currentIndex)
    }

    /**
     * Change the current item's loop count from inside the headset. Lowering it never
     * goes below the pass already playing, so "Loop −" means "stop looping after this pass".
     */
    fun adjustCurrentLoops(delta: Int): Int {
        var loops = QueueItem.stepLoops(current.loops, delta)
        if (loops != QueueItem.LOOP_FOREVER && loops < pass) loops = pass
        items[currentIndex] = current.copy(loops = loops)
        return loops
    }

    fun setCurrentLoops(loops: Int) {
        items[currentIndex] = current.copy(loops = loops)
    }

    fun replaceCurrent(item: QueueItem) {
        items[currentIndex] = item
    }

    fun snapshot(): List<QueueItem> = items.toList()

    private fun advance(wrapAllowed: Boolean): Step {
        pass = 1
        if (orderPos + 1 < order.size) {
            orderPos++
            return Step.Play(currentIndex)
        }
        if (!wrapAllowed) return Step.Finished
        if (shuffle && items.size > 1) {
            // New shuffle for the next round, avoiding an immediate repeat of the last item.
            val last = currentIndex
            val rest = items.indices.filter { it != last }.shuffled(random)
            order = (rest + last).toIntArray()
        }
        orderPos = 0
        return Step.Play(currentIndex)
    }

    private fun rebuildOrder(startAt: Int) {
        order = if (shuffle) {
            val rest = items.indices.filter { it != startAt }.shuffled(random)
            (listOf(startAt) + rest).toIntArray()
        } else {
            IntArray(items.size) { it }
        }
        orderPos = order.indexOf(startAt).coerceAtLeast(0)
    }
}
