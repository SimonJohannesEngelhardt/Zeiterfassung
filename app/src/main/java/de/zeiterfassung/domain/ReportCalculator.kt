package de.zeiterfassung.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ReportCalculator {
    fun calculate(
        intervals: List<WorkInterval>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): WorkReport {
        require(!to.isBefore(from)) { "Das Enddatum muss am oder nach dem Startdatum liegen." }

        val reportDates = generateSequence(from) { date ->
            date.plusDays(1).takeUnless { it.isAfter(to) }
        }.filter { it.dayOfWeek.value <= 5 }

        val days = reportDates.map { date ->
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val workedMillis = intervals.sumOf { interval ->
                val intervalStart = maxOf(interval.startEpochMillis, dayStart)
                val intervalEnd = minOf(interval.endEpochMillis ?: nowEpochMillis, dayEnd)
                (intervalEnd - intervalStart).coerceAtLeast(0L)
            }
            DayTotal(date, workedMillis)
        }.toList()

        return WorkReport(days)
    }
}
