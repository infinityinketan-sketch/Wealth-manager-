package com.example.ledger.core

import java.time.LocalDate

enum class Frequency { WEEKLY, MONTHLY, YEARLY }

object Recurrence {

    /** The n-th occurrence counted from [start]; n = 0 is the start date itself. */
    fun occurrence(start: LocalDate, frequency: Frequency, n: Long): LocalDate = when (frequency) {
        Frequency.WEEKLY -> start.plusWeeks(n)
        // Always counted from the start date, so a rule that starts on the 31st
        // gives Feb 28, then Mar 31 again, instead of drifting to the 28th for good.
        Frequency.MONTHLY -> start.plusMonths(n)
        Frequency.YEARLY -> start.plusYears(n)
    }

    /**
     * Dates a recurring rule has come due and not yet been turned into entries:
     * everything after [lastGenerated] (or from [start] if nothing was generated yet)
     * up to and including [today]. [limit] stops a runaway loop on bad data.
     */
    fun dueDates(
        start: LocalDate,
        frequency: Frequency,
        lastGenerated: LocalDate?,
        today: LocalDate,
        limit: Int = 500
    ): List<LocalDate> {
        val result = ArrayList<LocalDate>()
        var n = 0L
        while (result.size < limit) {
            val date = occurrence(start, frequency, n)
            if (date.isAfter(today)) break
            if (lastGenerated == null || date.isAfter(lastGenerated)) result.add(date)
            n++
        }
        return result
    }
}
