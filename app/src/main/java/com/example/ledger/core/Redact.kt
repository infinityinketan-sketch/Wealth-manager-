package com.example.ledger.core

/** Hides things that must never reach the server inside free text (feedback, crash notes). */
object Redact {
    // 6+ digits, also when written in groups ("1234 5678 9012", "12-34-56"): account, card, OTP, phone numbers.
    private val longNumber = Regex("""\d(?:[ -]?\d){5,}""")
    private val upiId = Regex("""\b[\w.\-]{2,}@[a-zA-Z]{2,}\b""")

    fun text(s: String): String =
        s.replace(longNumber) { m -> "•".repeat(m.value.count { it.isDigit() }.coerceAtMost(8)) }
            .replace(upiId, "[id hidden]")
}
