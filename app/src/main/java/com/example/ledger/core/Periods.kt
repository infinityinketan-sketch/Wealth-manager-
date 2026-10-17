package com.example.ledger.core

import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The periods a person can pick at the top of the home screen. */
enum class PeriodPreset(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("3 months"),
    LAST_6_MONTHS("6 months"),
    LAST_12_MONTHS("12 months"),
    THIS_YEAR("This year"),
    FINANCIAL_YEAR("This FY"),
    CUSTOM("Custom")
}

object Periods {

    /**
     * The date range for a preset. Month presets follow the person's budget start day
     * (e.g. salary on the 5th: "This month" is 5 Oct – 4 Nov). "This FY" is the Indian
     * financial year, April to March.
     */
    fun range(preset: PeriodPreset, today: LocalDate, startDay: Int, custom: DateRange? = null): DateRange {
        val month = BudgetPeriod.containing(today, startDay)
        return when (preset) {
            PeriodPreset.THIS_MONTH -> month
            PeriodPreset.LAST_MONTH -> BudgetPeriod.previous(month)
            PeriodPreset.LAST_3_MONTHS -> DateRange(month.start.minusMonths(2), month.endInclusive)
            PeriodPreset.LAST_6_MONTHS -> DateRange(month.start.minusMonths(5), month.endInclusive)
            PeriodPreset.LAST_12_MONTHS -> DateRange(month.start.minusMonths(11), month.endInclusive)
            PeriodPreset.THIS_YEAR -> DateRange(LocalDate.of(today.year, 1, 1), LocalDate.of(today.year, 12, 31))
            PeriodPreset.FINANCIAL_YEAR -> {
                val startYear = if (today.monthValue >= 4) today.year else today.year - 1
                DateRange(LocalDate.of(startYear, 4, 1), LocalDate.of(startYear + 1, 3, 31))
            }
            PeriodPreset.CUSTOM -> custom ?: month
        }
    }

    private fun monthsIn(preset: PeriodPreset): Long? = when (preset) {
        PeriodPreset.THIS_MONTH, PeriodPreset.LAST_MONTH -> 1
        PeriodPreset.LAST_3_MONTHS -> 3
        PeriodPreset.LAST_6_MONTHS -> 6
        PeriodPreset.LAST_12_MONTHS, PeriodPreset.THIS_YEAR, PeriodPreset.FINANCIAL_YEAR -> 12
        PeriodPreset.CUSTOM -> null
    }

    /** Moves a range back (steps < 0) or forward by its own length, keeping month alignment. */
    fun shift(range: DateRange, preset: PeriodPreset, steps: Int): DateRange {
        if (steps == 0) return range
        val months = monthsIn(preset)
        return if (months != null) {
            val start = range.start.plusMonths(months * steps)
            DateRange(start, start.plusMonths(months).minusDays(1))
        } else {
            val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
            DateRange(range.start.plusDays(days * steps), range.endInclusive.plusDays(days * steps))
        }
    }
}

enum class BucketSize { DAY, WEEK, MONTH }

data class Bucket(val start: LocalDate, val endInclusive: LocalDate, val label: String)

/** Splits a period into chart bars: days up to a month, weeks up to ~4 months, then months. */
object Buckets {

    fun sizeFor(range: DateRange): BucketSize {
        val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
        return when {
            days <= 31 -> BucketSize.DAY
            days <= 124 -> BucketSize.WEEK
            else -> BucketSize.MONTH
        }
    }

    private fun shortMonth(m: Month) = m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    fun split(range: DateRange): List<Bucket> {
        val out = ArrayList<Bucket>()
        val size = sizeFor(range)
        val spansYears = range.start.year != range.endInclusive.year
        var start = range.start
        while (!start.isAfter(range.endInclusive)) {
            val next = when (size) {
                BucketSize.DAY -> start.plusDays(1)
                BucketSize.WEEK -> start.plusDays(7)
                BucketSize.MONTH -> start.plusMonths(1)
            }
            val end = minOf(next.minusDays(1), range.endInclusive)
            val label = when (size) {
                BucketSize.DAY -> start.dayOfMonth.toString()
                BucketSize.WEEK -> start.dayOfMonth.toString() + " " + shortMonth(start.month)
                BucketSize.MONTH -> shortMonth(start.month) + if (spansYears) " " + (start.year % 100).toString().padStart(2, '0') else ""
            }
            out.add(Bucket(start, end, label))
            start = next
        }
        return out
    }

    /** Which bucket a date falls in, or -1 if outside all of them. */
    fun indexOf(buckets: List<Bucket>, date: LocalDate): Int {
        var lo = 0
        var hi = buckets.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val b = buckets[mid]
            when {
                date.isBefore(b.start) -> hi = mid - 1
                date.isAfter(b.endInclusive) -> lo = mid + 1
                else -> return mid
            }
        }
        return -1
    }
}
