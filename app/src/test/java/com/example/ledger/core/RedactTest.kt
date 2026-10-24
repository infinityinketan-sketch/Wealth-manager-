package com.example.ledger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactTest {
    @Test fun hidesAccountCardOtpAndPhone() {
        assertFalse(Redact.text("a/c 123456789012 debited").contains("123456"))
        assertFalse(Redact.text("card 4111 1111 1111 1111").contains("1111 1111"))
        assertFalse(Redact.text("OTP is 482913").contains("482913"))
        assertFalse(Redact.text("call 98765-43210").contains("43210"))
    }

    @Test fun keepsShortNumbersAndWords() {
        assertEquals("Swiggy 450 went to Shopping in 2026", Redact.text("Swiggy 450 went to Shopping in 2026"))
    }

    @Test fun hidesUpiAndEmailIds() {
        assertFalse(Redact.text("paid to ravi.k@okaxis").contains("ravi.k"))
    }
}
