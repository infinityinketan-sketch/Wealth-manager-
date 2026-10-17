package com.example.ledger.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TotalsTest {
    @Test fun investmentsAndTransfersAreNotIncomeOrSpending() {
        val t = TotalsCalc.of(
            listOf(
                TotalsItem(true, 50_000_00, "Salary", true),
                TotalsItem(false, 20_000_00, "Food & dining", true),
                TotalsItem(false, 10_000_00, "Investments & FD", false),   // SIP: saving, not spending
                TotalsItem(true, 25_000_00, "Transfer in (not income)", false), // MF redemption: not income
                TotalsItem(false, 5_000_00, "Self transfer", false),
                TotalsItem(true, 500_00, "Refund", true)                     // Amazon refund
            )
        )
        assertEquals(50_000_00L, t.income)
        assertEquals(20_000_00L, t.spentGross)
        assertEquals(500_00L, t.refunds)
        assertEquals(19_500_00L, t.spent)
        assertEquals(30_500_00L, t.saved)
        assertEquals(10_000_00L, t.invested)
        assertEquals(15_000_00L, t.movedOut)
        assertEquals(25_000_00L, t.movedIn)
    }

    @Test fun refundsNeverMakeSpendingNegative() {
        val t = TotalsCalc.of(listOf(TotalsItem(true, 900_00, "Refund", true), TotalsItem(false, 100_00, "Other", true)))
        assertEquals(0L, t.spent)
        assertEquals(0L, t.income)
    }
}
