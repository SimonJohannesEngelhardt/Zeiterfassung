package de.zeiterfassung.domain

interface WorkIntervalStore {
    fun getAll(): List<WorkInterval>
    fun save(interval: WorkInterval): WorkInterval
    fun delete(id: Long)
}

class TimeTracking(private val store: WorkIntervalStore) {
    fun intervals(): List<WorkInterval> =
        store.getAll().sortedByDescending { it.startEpochMillis }

    fun activeInterval(): WorkInterval? =
        store.getAll().singleOrNull(WorkInterval::isRunning)

    fun start(nowEpochMillis: Long = System.currentTimeMillis()): WorkInterval {
        val intervals = store.getAll()
        if (intervals.any(WorkInterval::isRunning)) {
            throw TimeTrackingException(TrackingError.ALREADY_RUNNING)
        }

        val candidate = WorkInterval(0, nowEpochMillis, null)
        validate(candidate, intervals, nowEpochMillis)
        return store.save(candidate)
    }

    fun stop(nowEpochMillis: Long = System.currentTimeMillis()): WorkInterval {
        val running = activeInterval()
            ?: throw TimeTrackingException(TrackingError.NOT_RUNNING)
        val candidate = running.copy(endEpochMillis = nowEpochMillis)
        validate(candidate, store.getAll(), nowEpochMillis)
        return store.save(candidate)
    }

    fun saveManual(
        startEpochMillis: Long,
        endEpochMillis: Long,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): WorkInterval {
        val candidate = WorkInterval(0, startEpochMillis, endEpochMillis)
        validate(candidate, store.getAll(), nowEpochMillis)
        return store.save(candidate)
    }

    fun update(
        interval: WorkInterval,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): WorkInterval {
        if (interval.id == 0L || interval.isRunning) {
            throw TimeTrackingException(TrackingError.INVALID_RANGE)
        }
        validate(interval, store.getAll(), nowEpochMillis)
        return store.save(interval)
    }

    fun delete(id: Long) = store.delete(id)

    private fun validate(
        candidate: WorkInterval,
        existing: List<WorkInterval>,
        nowEpochMillis: Long
    ) {
        val validation = IntervalRules.validate(candidate, existing, nowEpochMillis)
        if (validation != IntervalValidation.VALID) {
            throw TimeTrackingException(
                when (validation) {
                    IntervalValidation.INVALID_RANGE -> TrackingError.INVALID_RANGE
                    IntervalValidation.IN_FUTURE -> TrackingError.IN_FUTURE
                    IntervalValidation.OVERLAP -> TrackingError.OVERLAP
                    IntervalValidation.VALID -> error("Unreachable")
                }
            )
        }
    }
}

class TimeTrackingException(val reason: TrackingError) : RuntimeException(reason.message)

enum class TrackingError(val message: String) {
    ALREADY_RUNNING("Es läuft bereits eine Arbeitszeiterfassung."),
    NOT_RUNNING("Es läuft derzeit keine Arbeitszeiterfassung."),
    INVALID_RANGE("Die Endzeit muss nach der Startzeit liegen."),
    IN_FUTURE("Arbeitszeiten dürfen nicht in der Zukunft liegen."),
    OVERLAP("Arbeitszeiten dürfen sich nicht überschneiden.")
}
