package com.example.ledger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialTest {
    private val day = 24L * 60 * 60 * 1000
    private val start = 1_760_000_000_000L

    @Test fun thirtyDaysFromFirstUse() {
        assertEquals(30, Trial.daysLeft(start, start))
        assertEquals(30, Trial.daysLeft(start, start + day - 1))
        assertEquals(29, Trial.daysLeft(start, start + day))
        assertEquals(1, Trial.daysLeft(start, start + 29 * day))
        assertEquals(0, Trial.daysLeft(start, start + 30 * day))
        assertEquals(0, Trial.daysLeft(start, start + 400 * day))
    }

    @Test fun proDuringTrialOrAfterBuying() {
        assertTrue(Trial.isPro(false, start, start + 10 * day))
        assertFalse(Trial.isPro(false, start, start + 31 * day))
        assertTrue(Trial.isPro(true, start, start + 31 * day))
    }

    @Test fun clockSetBackDoesNotBreak() {
        assertEquals(30, Trial.daysLeft(start, start - 5 * day))
    }

    @Test fun countdownOnlyInLastFiveDays() {
        assertFalse(Trial.showCountdown(false, 6))
        assertTrue(Trial.showCountdown(false, 5))
        assertTrue(Trial.showCountdown(false, 1))
        assertFalse(Trial.showCountdown(false, 0))
        assertFalse(Trial.showCountdown(true, 3))
    }
}
