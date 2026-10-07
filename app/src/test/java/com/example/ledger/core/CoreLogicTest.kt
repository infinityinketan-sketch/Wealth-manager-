package com.example.ledger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyTest {
    @Test fun smallAmountsHaveNoSeparator() {
        assertEquals("₹0", Money.formatInr(0))
        assertEquals("₹999", Money.formatInr(99_900))
    }

    @Test fun groupsInLakhsAndCrores() {
        assertEquals("₹1,000", Money.formatInr(100_000))
        assertEquals("₹12,345", Money.formatInr(1_234_500))
        assertEquals("₹1,23,456", Money.formatInr(12_345_600))
        assertEquals("₹12,34,567", Money.formatInr(123_456_700))
        assertEquals("₹1,23,45,678", Money.formatInr(1_234_567_800))
        assertEquals("₹12,34,56,789", Money.formatInr(12_345_678_900))
    }

    @Test fun showsPaiseOnlyWhenPresent() {
        assertEquals("₹1,250.50", Money.formatInr(125_050))
        assertEquals("₹0.05", Money.formatInr(5))
        assertEquals("₹1,23,456.78", Money.formatInr(12_345_678))
    }

    @Test fun negativeAndNoSymbol() {
        assertEquals("-₹1,500", Money.formatInr(-150_000))
        assertEquals("1,23,456", Money.formatInr(12_345_600, showSymbol = false))
    }

    @Test fun parsesTypedAmounts() {
        assertEquals(125_050L, Money.parseToPaise("1,250.50"))
        assertEquals(50_000L, Money.parseToPaise("₹ 500"))
        assertEquals(7_500L, Money.parseToPaise("75"))
        assertEquals(50L, Money.parseToPaise(".5"))
        assertEquals(1_000L, Money.parseToPaise("10."))
        assertEquals(1_205L, Money.parseToPaise("12.05"))
    }

    @Test fun rejectsBadAmounts() {
        assertNull(Money.parseToPaise(""))
        assertNull(Money.parseToPaise("."))
        assertNull(Money.parseToPaise("abc"))
        assertNull(Money.parseToPaise("12.345"))
        assertNull(Money.parseToPaise("-5"))
        assertNull(Money.parseToPaise("1.2.3"))
    }

    @Test fun plainFormatForEditing() {
        assertEquals("1250.50", Money.formatPlain(125_050))
        assertEquals("1500", Money.formatPlain(150_000))
        assertEquals("0.05", Money.formatPlain(5))
        assertEquals(125_050L, Money.parseToPaise(Money.formatPlain(125_050)))
    }

    @Test fun parseThenFormatRoundTrips() {
        val paise = Money.parseToPaise("12,34,567.89")
        assertEquals(123_456_789L, paise)
        assertEquals("₹12,34,567.89", Money.formatInr(paise!!))
    }
}

class BudgetPeriodTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun startDayOneIsTheCalendarMonth() {
        val r = BudgetPeriod.containing(d(2026, 10, 17), 1)
        assertEquals(d(2026, 10, 1), r.start)
        assertEquals(d(2026, 10, 31), r.endInclusive)
    }

    @Test fun salaryDayAfterTodayLooksBackToLastMonth() {
        val r = BudgetPeriod.containing(d(2026, 10, 3), 5)
        assertEquals(d(2026, 9, 5), r.start)
        assertEquals(d(2026, 10, 4), r.endInclusive)
    }

    @Test fun startDayItselfBelongsToTheNewPeriod() {
        val r = BudgetPeriod.containing(d(2026, 10, 5), 5)
        assertEquals(d(2026, 10, 5), r.start)
        assertEquals(d(2026, 11, 4), r.endInclusive)
    }

    @Test fun crossesTheYearBoundary() {
        val r = BudgetPeriod.containing(d(2027, 1, 2), 25)
        assertEquals(d(2026, 12, 25), r.start)
        assertEquals(d(2027, 1, 24), r.endInclusive)
    }

    @Test fun februaryWorksWithDay28() {
        val r = BudgetPeriod.containing(d(2027, 2, 27), 28)
        assertEquals(d(2027, 1, 28), r.start)
        assertEquals(d(2027, 2, 27), r.endInclusive)
    }

    @Test fun outOfRangeStartDayIsClamped() {
        assertEquals(d(2026, 10, 28), BudgetPeriod.containing(d(2026, 10, 30), 31).start)
        assertEquals(d(2026, 10, 1), BudgetPeriod.containing(d(2026, 10, 30), 0).start)
    }

    @Test fun previousAndNextAreContiguous() {
        val r = BudgetPeriod.containing(d(2026, 10, 17), 5)
        val prev = BudgetPeriod.previous(r)
        val next = BudgetPeriod.next(r)
        assertEquals(r.start.minusDays(1), prev.endInclusive)
        assertEquals(r.endInclusive.plusDays(1), next.start)
        assertTrue(r.contains(d(2026, 10, 17)))
        assertTrue(!r.contains(d(2026, 11, 5)))
    }
}

class RecurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun monthlyRuleFromTheStart() {
        val due = Recurrence.dueDates(d(2026, 7, 5), Frequency.MONTHLY, null, d(2026, 10, 7))
        assertEquals(listOf(d(2026, 7, 5), d(2026, 8, 5), d(2026, 9, 5), d(2026, 10, 5)), due)
    }

    @Test fun onlyNewDatesAfterLastGenerated() {
        val due = Recurrence.dueDates(d(2026, 7, 5), Frequency.MONTHLY, d(2026, 9, 5), d(2026, 10, 7))
        assertEquals(listOf(d(2026, 10, 5)), due)
    }

    @Test fun nothingDueBeforeTheStartDate() {
        assertEquals(emptyList<LocalDate>(), Recurrence.dueDates(d(2026, 11, 1), Frequency.MONTHLY, null, d(2026, 10, 7)))
    }

    @Test fun nothingDueWhenUpToDate() {
        assertEquals(emptyList<LocalDate>(), Recurrence.dueDates(d(2026, 7, 5), Frequency.MONTHLY, d(2026, 10, 5), d(2026, 10, 7)))
    }

    @Test fun monthEndRuleDoesNotDriftToThe28th() {
        val due = Recurrence.dueDates(d(2026, 1, 31), Frequency.MONTHLY, null, d(2026, 4, 30))
        assertEquals(listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30)), due)
    }

    @Test fun weeklyRule() {
        val due = Recurrence.dueDates(d(2026, 10, 1), Frequency.WEEKLY, null, d(2026, 10, 22))
        assertEquals(listOf(d(2026, 10, 1), d(2026, 10, 8), d(2026, 10, 15), d(2026, 10, 22)), due)
    }

    @Test fun yearlyRuleOnLeapDay() {
        val due = Recurrence.dueDates(d(2024, 2, 29), Frequency.YEARLY, null, d(2026, 12, 31))
        assertEquals(listOf(d(2024, 2, 29), d(2025, 2, 28), d(2026, 2, 28)), due)
    }

    @Test fun limitStopsRunawayRules() {
        val due = Recurrence.dueDates(d(2000, 1, 1), Frequency.WEEKLY, null, d(2026, 10, 7), limit = 10)
        assertEquals(10, due.size)
    }
}
