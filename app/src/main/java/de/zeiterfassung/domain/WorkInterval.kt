package de.zeiterfassung.domain

data class WorkInterval(
    val id: Long,
    val startEpochMillis: Long,
    val endEpochMillis: Long?
) {
    val isRunning: Boolean
        get() = endEpochMillis == null
}

data class DayTotal(
    val date: java.time.LocalDate,
    val workedMillis: Long
) {
    val roundedMinutes: Long
        get() = (workedMillis + MILLIS_PER_MINUTE / 2) / MILLIS_PER_MINUTE

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}

data class WorkReport(
    val days: List<DayTotal>
) {
    val totalWorkedMillis: Long
        get() = days.sumOf(DayTotal::workedMillis)

    val totalRoundedMinutes: Long
        get() = (totalWorkedMillis + 30_000L) / 60_000L
}
