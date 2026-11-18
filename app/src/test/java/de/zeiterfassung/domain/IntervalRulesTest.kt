package de.zeiterfassung.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalRulesTest {
    private val cut = IntervalRules
    private val now = 1_800_000_000_000L

    @Test
    fun givenOverlappingInterval_whenValidating_thenRejectsCandidate() {
        val existing = listOf(WorkInterval(1, now - 3_600_000L, now - 1_800_000L))
        val candidate = WorkInterval(2, now - 2_400_000L, now)

        assertEquals(
            IntervalValidation.OVERLAP,
            cut.validate(candidate, existing, now)
        )
    }

    @Test
    fun givenTouchingIntervals_whenValidating_thenAcceptsCandidate() {
        val existing = listOf(WorkInterval(1, now - 3_600_000L, now - 1_800_000L))
        val candidate = WorkInterval(2, now - 1_800_000L, now)

        assertEquals(
            IntervalValidation.VALID,
            cut.validate(candidate, existing, now)
        )
    }

    @Test
    fun givenFutureInterval_whenValidating_thenRejectsCandidate() {
        val candidate = WorkInterval(1, now - 60_000L, now + 60_000L)

        assertEquals(
            IntervalValidation.IN_FUTURE,
            cut.validate(candidate, emptyList(), now)
        )
    }
}
