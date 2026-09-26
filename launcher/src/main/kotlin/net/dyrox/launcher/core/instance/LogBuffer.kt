package net.dyrox.launcher.core.instance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.dyrox.launcher.core.process.LogLine

/**
 * Bounded, thread-safe log of one instance's session. Entries carry increasing sequence numbers so
 * viewers can fetch only what's new ([after]) instead of copying the whole buffer.
 */
class LogBuffer(private val capacity: Int = 20_000) {
    data class Entry(val seq: Long, val line: LogLine)

    private val entries = ArrayDeque<Entry>(capacity)
    private var nextSeq = 0L
    private val _lastSeq = MutableStateFlow(-1L)

    /** Sequence number of the newest entry (-1 when empty); changes on every append. */
    val lastSeq: StateFlow<Long> = _lastSeq.asStateFlow()

    @Synchronized
    fun append(line: LogLine) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(Entry(nextSeq, line))
        _lastSeq.value = nextSeq++
    }

    /** Entries newer than [seq] (pass -1 for everything still buffered), at most [max]. */
    @Synchronized
    fun after(seq: Long, max: Int = 5_000): List<Entry> {
        if (entries.isEmpty() || seq >= entries.last().seq) return emptyList()
        val firstSeq = entries.first().seq
        val start = (seq + 1 - firstSeq).coerceAtLeast(0).toInt()
        val end = minOf(entries.size, start + max)
        return List(end - start) { entries[start + it] }
    }

    @Synchronized
    fun snapshot(): List<LogLine> = entries.map { it.line }

    val size: Int @Synchronized get() = entries.size
}
