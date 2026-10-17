package com.example.ledger.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DueParserTest {
    private val day = LocalDate.of(2026, 10, 1)
    private fun parse(sender: String, body: String, on: LocalDate = day): DueNotice {
        val d = DueParser.parse(sender, body, on)
        assertNotNull(d)
        return d!!
    }

    @Test fun electricityBill() {
        val d = parse("VM-MSEDCL", "Dear Consumer, your electricity bill for Consumer No 170012349719 of Rs. 940.00 is due on 17-10-2026. Pay online at wss.mahadiscom.in", day)
        assertEquals("MSEB electricity", d.title)
        assertEquals(DueKindHint.UTILITY, d.kind)
        assertEquals(94_000L, d.amountPaise)
        assertEquals(LocalDate.of(2026, 10, 17), d.dueDate)
        assertEquals("9719", d.digits)
    }

    @Test fun creditCardStatementWithMinimum() {
        val d = parse("VM-ICICIB-S", "ICICI Bank Credit Card XX1320 statement for Sep: Total amount due INR 5,952.00; Minimum amount due INR 120.00; Due date 05-Oct-26.")
        assertEquals("ICICI Bank credit card", d.title)
        assertEquals(DueKindHint.CARD, d.kind)
        assertEquals(595_200L, d.amountPaise)
        assertEquals(12_000L, d.minDuePaise)
        assertEquals(LocalDate.of(2026, 10, 5), d.dueDate)
        assertEquals("1320", d.digits)
    }

    @Test fun hdfcCardPayBy() {
        val d = parse("AD-HDFCBK", "Your HDFC Bank Credit Card XX9876 Total Amt Due Rs.12,450.00, Min Amt Due Rs.620.00. Pay by 20/10/2026 to avoid charges.")
        assertEquals(1_245_000L, d.amountPaise)
        assertEquals(62_000L, d.minDuePaise)
        assertEquals(LocalDate.of(2026, 10, 20), d.dueDate)
    }

    @Test fun loanEmi() {
        val d = parse("VM-ICICIB-S", "Dear Customer, EMI of Rs 20,399 for your ICICI Loan a/c XX6823 is due on 05-Dec-26. Please maintain sufficient balance.", LocalDate.of(2026, 11, 28))
        assertEquals("ICICI Bank loan", d.title)
        assertEquals(DueKindHint.LOAN, d.kind)
        assertEquals(2_039_900L, d.amountPaise)
        assertEquals("6823", d.digits)
    }

    @Test fun lenderEmiWillBeDebited() {
        val d = parse("VM-BAJAJF-S", "Your EMI of Rs 8,450 will be debited on 05-11-26 from a/c XX1234.")
        assertEquals("Bajaj Finance loan", d.title)
        assertEquals(LocalDate.of(2026, 11, 5), d.dueDate)
    }

    @Test fun insurancePremium() {
        val d = parse("VM-RNLIFE", "Premium of Rs.2,245 for policy 52411141 is due on 23 Dec 2026. Pay now to keep your cover. Reliance Nippon Life", LocalDate.of(2026, 12, 1))
        assertEquals("Reliance Nippon Life", d.title)
        assertEquals(DueKindHint.INSURANCE, d.kind)
        assertEquals(224_500L, d.amountPaise)
        assertEquals("1141", d.digits)
    }

    @Test fun dateWithoutYear() {
        val d = parse("JM-AIRTEL", "Your Airtel postpaid bill of Rs 499 for mobile 98XXXX4321 is due on 25 Oct.")
        assertEquals(LocalDate.of(2026, 10, 25), d.dueDate)
        assertEquals("Airtel", d.title)
    }

    @Test fun ignoresReceiptsPromosAndUndatedMessages() {
        assertNull(DueParser.parse("VM-MSEDCL", "Thank you for your payment of Rs 940 received on 05-10-2026.", day))
        assertNull(DueParser.parse("VM-HDFCBK", "Pay your bills before due date 20-10-2026 and win cashback offer up to Rs 500!", day))
        assertNull(DueParser.parse("VM-HDFCBK", "Rs.250.00 debited from A/c XX1234 to SWIGGY. Avl bal Rs 1,000", day))
        assertNull(DueParser.parse("+919876543210", "Bill of Rs 500 is due on 20-10-2026", day))
    }

    @Test fun dateFormats() {
        val ref = LocalDate.of(2026, 10, 1)
        assertEquals(LocalDate.of(2026, 10, 17), DueParser.parseDate("17Oct26", ref))
        assertEquals(LocalDate.of(2026, 10, 17), DueParser.parseDate("2026-10-17", ref))
        assertEquals(LocalDate.of(2026, 10, 17), DueParser.parseDate("Oct 17, 2026", ref))
        assertEquals(LocalDate.of(2026, 10, 17), DueParser.parseDate("17th October 2026", ref))
        assertEquals(LocalDate.of(2027, 1, 5), DueParser.parseDate("05 Jan", ref))
    }
}
