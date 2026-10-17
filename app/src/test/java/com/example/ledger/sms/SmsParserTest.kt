package com.example.ledger.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    @Test fun hdfcUpiDebit() {
        val p = SmsParser.parse(
            "VM-HDFCBK-S",
            "Sent Rs.250.00 From HDFC Bank A/c *1234 To SWIGGY On 12/10/26 Ref 628811223344 Not You? Call 18002586161 Avl bal:Rs.12,345.67"
        )
        assertNotNull(p)
        p!!
        assertFalse(p.isIncome)
        assertEquals(25_000L, p.amountPaise)
        assertEquals("HDFC Bank", p.bankName)
        assertEquals("1234", p.last4)
        assertEquals("SWIGGY", p.merchant)
        assertEquals(1_234_567L, p.balancePaise)
    }

    @Test fun sbiDebitWithVpa() {
        val p = SmsParser.parse(
            "AX-SBIUPI-S",
            "Dear UPI user A/C X5678 debited by 1200.0 on date 11Oct26 trf to RAMESH KUMAR Refno 628800112233. If not u? call 1800111109. -SBI"
        )!!
        assertFalse(p.isIncome)
        assertEquals(120_000L, p.amountPaise)
        assertEquals("SBI", p.bankName)
        assertEquals("5678", p.last4)
    }

    @Test fun iciciDebitCardSpend() {
        val p = SmsParser.parse(
            "JD-ICICIT-S",
            "INR 1,499.00 spent on ICICI Bank Card XX9876 on 10-Oct-26 at AMAZON PAY. Avl Limit: INR 55,000.00. If not you, call 1800 1080."
        )!!
        assertFalse(p.isIncome)
        assertEquals(149_900L, p.amountPaise)
        assertEquals("9876", p.last4)
        assertEquals("AMAZON PAY", p.merchant)
        assertNull(p.balancePaise)
    }

    @Test fun salaryCredit() {
        val p = SmsParser.parse(
            "VK-AXISBK-S",
            "INR 52,000.00 credited to A/c no. XX4321 on 01-10-26 by NEFT from ACME TECH PVT LTD SALARY. Avl Bal INR 61,250.10"
        )!!
        assertTrue(p.isIncome)
        assertEquals(5_200_000L, p.amountPaise)
        assertEquals("Axis Bank", p.bankName)
        assertEquals("4321", p.last4)
        assertEquals(6_125_010L, p.balancePaise)
        assertEquals("Salary", CategoryGuesser.guess(true, p.merchant, "salary"))
    }

    @Test fun upiCreditFromPerson() {
        val p = SmsParser.parse(
            "VM-KOTAKB-S",
            "Received Rs.500.00 in your Kotak Bank AC XX2222 from priya@okaxis on 09-10-26. UPI Ref:628899001122."
        )!!
        assertTrue(p.isIncome)
        assertEquals(50_000L, p.amountPaise)
        assertEquals("Kotak Bank", p.bankName)
        assertEquals("2222", p.last4)
        assertEquals("PRIYA", p.merchant)
    }

    @Test fun ignoresOtp() {
        assertNull(SmsParser.parse("VM-HDFCBK-S", "123456 is your OTP for txn of Rs.500.00 at AMAZON. Do not share."))
    }

    @Test fun ignoresDueReminder() {
        assertNull(SmsParser.parse("VM-HDFCBK-S", "Total due Rs.4,500.00 on your HDFC credit card is due on 20-Oct. Pay now."))
    }

    @Test fun ignoresPersonalNumber() {
        assertNull(SmsParser.parse("+919876543210", "I sent Rs.500 to you, debited from my account"))
    }

    @Test fun ignoresPromo() {
        assertNull(SmsParser.parse("AX-AMZNIN", "Get Rs.200 cashback offer on your next order. Shop now!"))
    }

    @Test fun ignoresFailedPayment() {
        assertNull(SmsParser.parse("VM-HDFCBK-S", "Payment of Rs.300.00 failed. Amount will be refunded if debited."))
    }

    @Test fun emiDebit() {
        val p = SmsParser.parse(
            "VM-HDFCBK-S",
            "Rs.8,450.00 debited from A/c XX1234 on 05-10-26 towards NACH EMI BAJAJ FINANCE. Avl bal Rs.20,000.00"
        )!!
        assertFalse(p.isIncome)
        assertEquals(845_000L, p.amountPaise)
        assertEquals("EMI", CategoryGuesser.guess(false, p.merchant, "towards NACH EMI BAJAJ FINANCE"))
    }

    @Test fun categoryKeywords() {
        assertEquals("Food & dining", CategoryGuesser.guess(false, "SWIGGY", ""))
        assertEquals("Groceries", CategoryGuesser.guess(false, "BLINKIT", ""))
        assertEquals("Transport", CategoryGuesser.guess(false, "UBER INDIA", ""))
        assertEquals("Online shopping", CategoryGuesser.guess(false, "AMAZON PAY", ""))
        assertEquals("Entertainment", CategoryGuesser.guess(false, "NETFLIX", ""))
        assertEquals("Clothes", CategoryGuesser.guess(false, "MYNTRA", ""))
        assertEquals("Electricity & bills", CategoryGuesser.guess(false, "MSEDCL", "electricity bill"))
        assertNull(CategoryGuesser.guess(false, "RAMESH KUMAR", "trf to RAMESH KUMAR"))
    }

    @Test fun merchantKeyNormalises() {
        assertEquals("swiggyltd", CategoryGuesser.merchantKey("Swiggy Ltd."))
        assertEquals(CategoryGuesser.merchantKey("SWIGGY"), CategoryGuesser.merchantKey("swiggy"))
        assertNull(CategoryGuesser.merchantKey(null))
    }

    @Test fun bankFromSender() {
        assertEquals("HDFC Bank", SmsParser.bankFromSender("VM-HDFCBK-S"))
        assertEquals("SBI", SmsParser.bankFromSender("AX-SBIUPI-S"))
        assertNull(SmsParser.bankFromSender("+919876543210"))
    }
}
