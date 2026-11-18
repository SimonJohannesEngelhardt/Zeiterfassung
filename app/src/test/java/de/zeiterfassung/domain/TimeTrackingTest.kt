package de.zeiterfassung.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TimeTrackingTest {
    @Test
    fun givenNoActiveTimer_whenStartingAndStopping_thenPersistsOneCompletedInterval() {
        val store = FakeWorkIntervalStore()
        val cut = TimeTracking(store)
        val start = 1_800_000_000_000L
        val end = start + 90_000L

        cut.start(start)
        val stopped = cut.stop(end)

        assertEquals(start, stopped.startEpochMillis)
        assertEquals(end, stopped.endEpochMillis)
        assertEquals(1, store.intervals.size)
        assertEquals(stopped, store.intervals.single())
    }

    @Test
    fun givenTimerAlreadyRunning_whenStartingAgain_thenRejectsSecondTimer() {
        val store = FakeWorkIntervalStore()
        val cut = TimeTracking(store)
        val now = 1_800_000_000_000L
        cut.start(now)

        assertThrows(TimeTrackingException::class.java) {
            cut.start(now + 60_000L)
        }
        assertEquals(1, store.intervals.size)
    }

    @Test
    fun givenInvalidManualInterval_whenSaving_thenDoesNotChangeStoredIntervals() {
        val store = FakeWorkIntervalStore()
        val cut = TimeTracking(store)
        val now = 1_800_000_000_000L
        assertThrows(TimeTrackingException::class.java) {
            cut.saveManual(now - 60_000L, now + 60_000L, now)
        }
        assertEquals(0, store.intervals.size)
    }
}

private class FakeWorkIntervalStore : WorkIntervalStore {
    val intervals = mutableListOf<WorkInterval>()
    private var nextId = 1L

    override fun getAll(): List<WorkInterval> = intervals.toList()

    override fun save(interval: WorkInterval): WorkInterval {
        val saved = interval.copy(id = if (interval.id == 0L) nextId++ else interval.id)
        intervals.removeAll { it.id == saved.id }
        intervals += saved
        return saved
    }

    override fun delete(id: Long) {
        intervals.removeAll { it.id == id }
    }
}
