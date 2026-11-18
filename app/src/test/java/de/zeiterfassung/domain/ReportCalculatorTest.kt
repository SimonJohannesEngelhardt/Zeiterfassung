package de.zeiterfassung.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCalculatorTest {
    private val cut = ReportCalculator

    @Test
    fun givenWeekWithOvernightShift_whenCalculating_thenSplitsAtLocalMidnightAndIncludesWeekdayZeroes() {
        val intervals = listOf(
            WorkInterval(
                id = 1,
                startEpochMillis = Instant.parse("2026-03-27T22:00:00Z").toEpochMilli(),
                endEpochMillis = Instant.parse("2026-03-28T02:00:00Z").toEpochMilli()
            )
        )

        val report = cut.calculate(
            intervals = intervals,
            from = LocalDate.parse("2026-03-27"),
            to = LocalDate.parse("2026-03-31"),
            zone = ZoneId.of("Europe/Berlin")
        )

        assertEquals(
            listOf(
                LocalDate.parse("2026-03-27") to 60L,
                LocalDate.parse("2026-03-30") to 0L,
                LocalDate.parse("2026-03-31") to 0L
            ),
            report.days.map { it.date to it.roundedMinutes }
        )
        assertEquals(60L, report.totalRoundedMinutes)
    }

    @Test
    fun givenSpringForwardCrossingShift_whenCalculating_thenUsesElapsedTimeForTheReportedDay() {
        val intervals = listOf(
            WorkInterval(
                id = 1,
                startEpochMillis = Instant.parse("2026-03-29T00:30:00Z").toEpochMilli(),
                endEpochMillis = Instant.parse("2026-03-30T08:30:00Z").toEpochMilli()
            )
        )

        val report = cut.calculate(
            intervals = intervals,
            from = LocalDate.parse("2026-03-30"),
            to = LocalDate.parse("2026-03-30"),
            zone = ZoneId.of("Europe/Berlin")
        )

        assertEquals(630L, report.totalRoundedMinutes)
    }
}
