package com.example.ledger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private val today = d(2026, 10, 17)

    @Test fun monthPresetsFollowStartDay() {
        assertEquals(DateRange(d(2026, 10, 1), d(2026, 10, 31)), Periods.range(PeriodPreset.THIS_MONTH, today, 1))
        assertEquals(DateRange(d(2026, 9, 1), d(2026, 9, 30)), Periods.range(PeriodPreset.LAST_MONTH, today, 1))
        assertEquals(DateRange(d(2026, 8, 1), d(2026, 10, 31)), Periods.range(PeriodPreset.LAST_3_MONTHS, today, 1))
        assertEquals(DateRange(d(2025, 11, 1), d(2026, 10, 31)), Periods.range(PeriodPreset.LAST_12_MONTHS, today, 1))
        assertEquals(DateRange(d(2026, 10, 5), d(2026, 11, 4)), Periods.range(PeriodPreset.THIS_MONTH, today, 5))
    }

    @Test fun yearsAndFinancialYear() {
        assertEquals(DateRange(d(2026, 1, 1), d(2026, 12, 31)), Periods.range(PeriodPreset.THIS_YEAR, today, 1))
        assertEquals(DateRange(d(2026, 4, 1), d(2027, 3, 31)), Periods.range(PeriodPreset.FINANCIAL_YEAR, today, 1))
        assertEquals(DateRange(d(2025, 4, 1), d(2026, 3, 31)), Periods.range(PeriodPreset.FINANCIAL_YEAR, d(2026, 2, 10), 1))
    }

    @Test fun customFallsBackToThisMonth() {
        val custom = DateRange(d(2026, 9, 10), d(2026, 9, 20))
        assertEquals(custom, Periods.range(PeriodPreset.CUSTOM, today, 1, custom))
        assertEquals(DateRange(d(2026, 10, 1), d(2026, 10, 31)), Periods.range(PeriodPreset.CUSTOM, today, 1, null))
    }

    @Test fun shiftKeepsMonthAlignment() {
        val q = Periods.range(PeriodPreset.LAST_3_MONTHS, today, 1)
        assertEquals(DateRange(d(2026, 5, 1), d(2026, 7, 31)), Periods.shift(q, PeriodPreset.LAST_3_MONTHS, -1))
        val feb = Periods.shift(Periods.range(PeriodPreset.THIS_MONTH, today, 1), PeriodPreset.THIS_MONTH, -8)
        assertEquals(DateRange(d(2026, 2, 1), d(2026, 2, 28)), feb)
        val fy = Periods.shift(Periods.range(PeriodPreset.FINANCIAL_YEAR, today, 1), PeriodPreset.FINANCIAL_YEAR, -1)
        assertEquals(DateRange(d(2025, 4, 1), d(2026, 3, 31)), fy)
    }

    @Test fun shiftCustomByItsLength() {
        val r = DateRange(d(2026, 9, 10), d(2026, 9, 20)) // 11 days
        assertEquals(DateRange(d(2026, 8, 30), d(2026, 9, 9)), Periods.shift(r, PeriodPreset.CUSTOM, -1))
    }

    @Test fun bucketsByLength() {
        val month = Buckets.split(DateRange(d(2026, 10, 1), d(2026, 10, 31)))
        assertEquals(31, month.size)
        assertEquals("1", month[0].label)
        val quarter = Buckets.split(DateRange(d(2026, 8, 1), d(2026, 10, 31)))
        assertEquals(14, quarter.size)
        assertEquals(d(2026, 10, 31), quarter.last().endInclusive)
        val year = Buckets.split(DateRange(d(2025, 11, 1), d(2026, 10, 31)))
        assertEquals(12, year.size)
        assertEquals("Nov 25", year[0].label)
        assertEquals(1, Buckets.indexOf(year, d(2025, 12, 15)))
        assertEquals(-1, Buckets.indexOf(year, d(2026, 11, 1)))
    }

    @Test fun compactMoney() {
        assertEquals("₹950", Money.formatCompact(95_000))
        assertEquals("₹12.5k", Money.formatCompact(1_250_000))
        assertEquals("₹1.2L", Money.formatCompact(12_000_000))
        assertEquals("₹100k", Money.formatCompact(9_999_999))
        assertEquals("₹3.4Cr", Money.formatCompact(3_400_000_000))
        assertTrue(Money.formatCompact(-50_000).startsWith("-₹"))
    }
}
