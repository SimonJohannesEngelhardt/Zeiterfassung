package de.zeiterfassung.domain

object IntervalRules {
    fun validate(
        candidate: WorkInterval,
        existing: List<WorkInterval>,
        nowEpochMillis: Long
    ): IntervalValidation {
        if (candidate.startEpochMillis > nowEpochMillis ||
            candidate.endEpochMillis?.let { it <= candidate.startEpochMillis } == true
        ) {
            return IntervalValidation.INVALID_RANGE
        }

        if (candidate.endEpochMillis?.let { it > nowEpochMillis } == true) {
            return IntervalValidation.IN_FUTURE
        }

        val candidateEnd = candidate.endEpochMillis ?: nowEpochMillis
        val overlaps = existing.asSequence()
            .filterNot { it.id == candidate.id }
            .any { interval ->
                val existingEnd = interval.endEpochMillis ?: nowEpochMillis
                candidate.startEpochMillis < existingEnd &&
                    interval.startEpochMillis < candidateEnd
            }

        return if (overlaps) IntervalValidation.OVERLAP else IntervalValidation.VALID
    }
}

enum class IntervalValidation {
    VALID,
    INVALID_RANGE,
    IN_FUTURE,
    OVERLAP
}
