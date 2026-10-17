package com.example.ledger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MatchersTest {
    private val h = 3_600_000L

    @Test fun pairsTransferBetweenOwnAccounts() {
        val moves = listOf(
            MoneyMove(1, accountId = 10, isIncome = false, amountPaise = 2_500_000, millis = 100 * h),
            MoneyMove(2, accountId = 20, isIncome = true, amountPaise = 2_500_000, millis = 100 * h + 60_000),
            MoneyMove(3, accountId = 10, isIncome = false, amountPaise = 50_000, millis = 101 * h)
        )
        assertEquals(listOf(1L to 2L), TransferMatcher.pairs(moves))
    }

    @Test fun ignoresSameAccountAndSlowSmallOnes() {
        val sameAccount = listOf(
            MoneyMove(1, 10, false, 2_500_000, 0), MoneyMove(2, 10, true, 2_500_000, 60_000)
        )
        assertTrue(TransferMatcher.pairs(sameAccount).isEmpty())
        val slowSmall = listOf(MoneyMove(1, 10, false, 50_000, 0), MoneyMove(2, 20, true, 50_000, 20 * h))
        assertTrue(TransferMatcher.pairs(slowSmall).isEmpty())
        val slowBig = listOf(MoneyMove(1, 10, false, 1_000_000, 0), MoneyMove(2, 20, true, 1_000_000, 20 * h))
        assertEquals(1, TransferMatcher.pairs(slowBig).size)
    }

    @Test fun eachCreditUsedOnce() {
        val moves = listOf(
            MoneyMove(1, 10, false, 1_000_000, 0), MoneyMove(2, 11, false, 1_000_000, 60_000),
            MoneyMove(3, 20, true, 1_000_000, 30_000)
        )
        assertEquals(1, TransferMatcher.pairs(moves).size)
    }

    @Test fun findsQuarterlyPayment() {
        val pts = listOf(
            PaymentPoint("ashok", "MR ASHOK BALU L", 174_000, LocalDate.of(2026, 1, 2)),
            PaymentPoint("ashok", "MR ASHOK BALU L", 174_000, LocalDate.of(2026, 4, 2)),
            PaymentPoint("ashok", "MR ASHOK BALU L", 174_000, LocalDate.of(2026, 7, 3)),
            PaymentPoint("ashok", "MR ASHOK BALU L", 175_000, LocalDate.of(2026, 10, 2))
        )
        val r = RecurringDetector.find(pts, LocalDate.of(2026, 10, 17))
        assertEquals(1, r.size)
        assertEquals("Repeats quarterly", r[0].label)
        assertEquals(LocalDate.of(2027, 1, 2), r[0].nextDate)
    }

    @Test fun findsMonthlyAndSkipsIrregular() {
        val monthly = (0..4).map { PaymentPoint("netflix", "NETFLIX", 64_900, LocalDate.of(2026, 5 + it, 12)) }
        val irregular = listOf(5, 9, 40, 41, 100).map { PaymentPoint("swiggy", "SWIGGY", 30_000, LocalDate.of(2026, 5, 1).plusDays(it.toLong())) }
        val r = RecurringDetector.find(monthly + irregular, LocalDate.of(2026, 10, 17))
        assertEquals(listOf("netflix"), r.map { it.merchantKey })
        assertEquals("Repeats monthly", r[0].label)
    }

    @Test fun skipsStoppedPayments() {
        val old = (0..3).map { PaymentPoint("gym", "GYM", 100_000, LocalDate.of(2025, 1 + it, 5)) }
        assertTrue(RecurringDetector.find(old, LocalDate.of(2026, 10, 17)).isEmpty())
    }
}
