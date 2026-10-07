package com.example.ledger.core

import java.time.LocalDate

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)
}

/**
 * A budget month that starts on a day the user picks (for example salary day, the 1st
 * to the 28th) instead of always the 1st. Days above 28 are not offered, so every
 * month has the chosen day.
 */
object BudgetPeriod {

    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 28

    fun containing(date: LocalDate, startDay: Int): DateRange {
        val day = startDay.coerceIn(MIN_START_DAY, MAX_START_DAY)
        val startThisMonth = date.withDayOfMonth(day)
        val start = if (date.dayOfMonth >= day) startThisMonth else startThisMonth.minusMonths(1)
        val end = start.plusMonths(1).minusDays(1)
        return DateRange(start, end)
    }

    fun previous(range: DateRange): DateRange {
        val start = range.start.minusMonths(1)
        return DateRange(start, range.start.minusDays(1))
    }

    fun next(range: DateRange): DateRange {
        val start = range.start.plusMonths(1)
        return DateRange(start, start.plusMonths(1).minusDays(1))
    }
}
