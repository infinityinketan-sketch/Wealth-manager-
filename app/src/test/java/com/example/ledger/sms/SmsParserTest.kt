package com.example.ledger.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private fun parse(sender: String, body: String): ParsedSms {
        val p = SmsParser.parse(sender, body)
        assertNotNull(p)
        return p!!
    }

    @Test fun hdfcUpiDebit() {
        val p = parse(
            "VM-HDFCBK-S",
            "Sent Rs.250.00 From HDFC Bank A/c *1234 To SWIGGY On 12/10/26 Ref 628811223344 Not You? Call 18002586161 Avl bal:Rs.12,345.67"
        )
        assertFalse(p.isIncome)
        assertEquals(25_000L, p.amountPaise)
        assertEquals("HDFC Bank", p.bankName)
        assertEquals("1234", p.accountDigits)
        assertEquals(SmsAccountKind.BANK, p.accountKind)
        assertEquals("SWIGGY", p.merchant)
        assertEquals(1_234_567L, p.balancePaise)
    }

    @Test fun sbiDebitNoRupeeSign() {
        val p = parse(
            "AX-SBIUPI-S",
            "Dear UPI user A/C X9023 debited by 1200.0 on date 11Oct26 trf to RAMESH KUMAR Refno 628800112233. If not u? call 1800111109. -SBI"
        )
        assertFalse(p.isIncome)
        assertEquals(120_000L, p.amountPaise)
        assertEquals("SBI", p.bankName)
        assertEquals("9023", p.accountDigits)
        assertEquals("RAMESH KUMAR", p.merchant)
        assertEquals("Sent to people", CategoryGuesser.guess(false, p.merchant, ""))
    }

    @Test fun sbiHasACreditBy() {
        val p = parse(
            "JM-SBIINB",
            "Dear Customer, Your A/C XXXXX9023 has a credit by Transfer of Rs 52,000.00 on 01/10/26 by ACME TECH PVT LTD. Avl Bal Rs 1,65,279.00 -SBI"
        )
        assertTrue(p.isIncome)
        assertEquals(5_200_000L, p.amountPaise)
        assertEquals("9023", p.accountDigits)
        assertEquals(16_527_900L, p.balancePaise)
        assertEquals("ACME TECH PVT LTD", p.merchant)
    }

    @Test fun iciciThreeDigitAccountAndMerchantBeforeCredited() {
        val p = parse(
            "VM-ICICIB-S",
            "ICICI Bank Acct XX123 debited for Rs 250.00 on 15-Oct-26; SWIGGY credited. UPI:628812345678. Call 18002662 for dispute."
        )
        assertFalse(p.isIncome)
        assertEquals("123", p.accountDigits)
        assertEquals("SWIGGY", p.merchant)
        assertEquals("Food & dining", CategoryGuesser.guess(false, p.merchant, ""))
    }

    @Test fun iciciCreditCardSpend() {
        val p = parse(
            "JD-ICICIT-S",
            "INR 1,499.00 spent using ICICI Bank Card XX9876 on 10-Oct-26 on AMAZON. Avl Limit: INR 55,000.00. If not you, call 1800 1080."
        )
        assertFalse(p.isIncome)
        assertEquals(149_900L, p.amountPaise)
        assertEquals(SmsAccountKind.CARD, p.accountKind)
        assertEquals("9876", p.accountDigits)
        assertEquals("AMAZON", p.merchant)
        assertNull(p.balancePaise)
    }

    @Test fun salaryCredit() {
        val p = parse(
            "VK-AXISBK-S",
            "INR 52,000.00 credited to A/c no. XX4321 on 01-10-26 by NEFT from ACME TECH SALARY. Avl Bal INR 61,250.10"
        )
        assertTrue(p.isIncome)
        assertEquals("4321", p.accountDigits)
        assertEquals(6_125_010L, p.balancePaise)
        assertEquals("Salary", CategoryGuesser.guess(true, p.merchant, "by NEFT from ACME TECH SALARY"))
    }

    @Test fun upiCreditFromHandle() {
        val p = parse(
            "VM-KOTAKB-S",
            "Received Rs.500.00 in your Kotak Bank AC XX2222 from priya@okaxis on 09-10-26. UPI Ref:628899001122."
        )
        assertTrue(p.isIncome)
        assertEquals("2222", p.accountDigits)
        assertEquals("PRIYA", p.merchant)
    }

    @Test fun fixedDepositIsNotSpending() {
        val body = "Rs.50,000.00 debited from A/c XX1234 on 05-10-26 towards FD No 501234 booking. Avl bal Rs.20,000.00"
        val p = parse("VM-HDFCBK-S", body)
        assertFalse(p.isIncome)
        assertEquals(CategoryGuesser.INVESTMENTS, CategoryGuesser.guess(false, p.merchant, body))
        assertTrue(CategoryGuesser.INVESTMENTS in StarterCategories.excludedNames)
    }

    @Test fun fdMaturityIsNotIncome() {
        val body = "Rs.51,200.00 credited to A/c XX1234 on 05-10-26 on FD closure/maturity. Avl bal Rs.71,200.00"
        val p = parse("VM-HDFCBK-S", body)
        assertTrue(p.isIncome)
        assertEquals(CategoryGuesser.TRANSFER_IN, CategoryGuesser.guess(true, p.merchant, body))
    }

    @Test fun loanCreditIsAnEmiPayment() {
        val p = parse(
            "VM-BAJAJF-S",
            "Dear Customer, we have received payment of Rs 8,450 towards your loan a/c XX7788. Thank you. Bajaj Finance"
        )
        assertFalse(p.isIncome)
        assertEquals(SmsAccountKind.LOAN, p.accountKind)
        assertEquals("EMI", p.categoryHint)
        assertEquals(845_000L, p.amountPaise)
    }

    @Test fun bankLoanAccountCreditIsAnEmiPayment() {
        val p = parse("VM-ICICIB-S", "Rs 12,000.00 credited to your Loan Account XX456 on 05-Oct-26. Thank you.")
        assertFalse(p.isIncome)
        assertEquals(SmsAccountKind.LOAN, p.accountKind)
        assertEquals("456", p.accountDigits)
    }

    @Test fun emiDebitFromBank() {
        val body = "Rs.8,450.00 debited from A/c XX1234 on 05-10-26 towards NACH EMI BAJAJ FINANCE. Avl bal Rs.20,000.00"
        val p = parse("VM-HDFCBK-S", body)
        assertEquals(SmsAccountKind.BANK, p.accountKind)
        assertEquals("EMI", CategoryGuesser.guess(false, p.merchant, body))
    }

    @Test fun loanDisbursalIsNotIncome() {
        val p = parse("VM-HDFCBK-S", "Rs.5,00,000.00 credited to A/c XX1234 towards loan disbursal on 05-10-26. Avl bal Rs.5,20,000.00")
        assertTrue(p.isIncome)
        assertEquals(SmsAccountKind.BANK, p.accountKind)
        assertEquals(CategoryGuesser.TRANSFER_IN, p.categoryHint)
    }

    @Test fun creditCardBillPaidFromBank() {
        val p = parse("VM-SBIINB", "Your A/c X9023 is debited by Rs 15,000.00 towards credit card XX9876 payment on 05Oct26. Avl Bal Rs 50,000.00")
        assertEquals(SmsAccountKind.BANK, p.accountKind)
        assertEquals("9023", p.accountDigits)
        assertEquals(CategoryGuesser.CARD_BILL, p.categoryHint)
    }

    @Test fun creditCardPaymentReceived() {
        val p = parse("VM-HDFCBK-S", "Payment of Rs.15,000.00 has been received on your HDFC Bank Credit Card XX9876. Thank you.")
        assertTrue(p.isIncome)
        assertEquals(SmsAccountKind.CARD, p.accountKind)
        assertEquals(CategoryGuesser.TRANSFER_IN, p.categoryHint)
    }

    @Test fun merchantReceiptsAreIgnored() {
        assertNull(SmsParser.parse("VM-MSEDCL", "Thank you for payment of Rs.1,240.00 for consumer no 170012345678 received on 05-10-26."))
        assertNull(SmsParser.parse("AD-JUSPAY", "Rs 500.00 paid to Swiggy via UPI. Txn ID 12345."))
        assertNull(SmsParser.parse("VM-MMTRIP", "Payment of Rs 4,500 received for booking NF123. Thank you for choosing MakeMyTrip."))
        assertNull(SmsParser.parse("VM-IFBSVC", "Rs 47,000 received towards service request 123456."))
    }

    @Test fun transactionWordIsNotAnAccountNumber() {
        val p = parse("VM-HDFCBK-S", "Your transaction of Rs 1234.00 is debited from HDFC Bank A/c XX5555 at DMART. Avl bal Rs 100.00")
        assertEquals("5555", p.accountDigits)
        assertEquals("Groceries", CategoryGuesser.guess(false, p.merchant, ""))
    }

    @Test fun ignoresOtpRemindersPromosAndFailures() {
        assertNull(SmsParser.parse("VM-HDFCBK-S", "123456 is your OTP for txn of Rs.500.00 at AMAZON. Do not share."))
        assertNull(SmsParser.parse("VM-HDFCBK-S", "Total due Rs.4,500.00 on your HDFC credit card is due on 20-Oct. Pay now."))
        assertNull(SmsParser.parse("+919876543210", "I sent Rs.500 to you, debited from my account"))
        assertNull(SmsParser.parse("AX-AMZNIN", "Get Rs.200 cashback offer on your next order. Shop now!"))
        assertNull(SmsParser.parse("VM-HDFCBK-S", "Payment of Rs.300.00 failed. Amount will be refunded if debited."))
        assertNull(SmsParser.parse("VM-BAJAJF-S", "Your EMI of Rs 8,450 will be debited on 05-11-26 from a/c XX1234."))
    }

    @Test fun keywordsMatchWholeWordsOnly() {
        assertEquals("Insurance", CategoryGuesser.guess(false, "LIC PREMIUM", ""))
        assertEquals("Health", CategoryGuesser.guess(false, "SAI CHEMIST", ""))
        assertNull(CategoryGuesser.guess(false, null, "Call toll free 1800 for dispute. Do not share ATM PIN treatment"))
        assertEquals("Food & dining", CategoryGuesser.guess(false, "SWIGGY", "convert to EMI by sms"))
    }

    @Test fun categoryKeywords() {
        assertEquals("Groceries", CategoryGuesser.guess(false, "BLINKIT", ""))
        assertEquals("Transport", CategoryGuesser.guess(false, "UBER INDIA", ""))
        assertEquals("Online shopping", CategoryGuesser.guess(false, "AMAZON PAY", ""))
        assertEquals("Entertainment", CategoryGuesser.guess(false, "NETFLIX", ""))
        assertEquals("Clothes", CategoryGuesser.guess(false, "MYNTRA", ""))
        assertEquals("Electricity & bills", CategoryGuesser.guess(false, "MSEDCL", ""))
        assertEquals("Fuel", CategoryGuesser.guess(false, "INDIANOIL PETROL PUMP", ""))
        assertEquals("Investments & FD", CategoryGuesser.guess(false, "ZERODHA BROKING", ""))
        assertNull(CategoryGuesser.guess(false, "XYZ1234", ""))
    }

    @Test fun merchantKeyNormalises() {
        assertEquals("swiggyltd", CategoryGuesser.merchantKey("Swiggy Ltd."))
        assertEquals(CategoryGuesser.merchantKey("SWIGGY"), CategoryGuesser.merchantKey("swiggy"))
        assertNull(CategoryGuesser.merchantKey(null))
    }

    @Test fun senders() {
        assertEquals("HDFC Bank", SmsParser.bankFromSender("VM-HDFCBK-S"))
        assertEquals("SBI", SmsParser.bankFromSender("AX-SBIUPI-S"))
        assertNull(SmsParser.bankFromSender("+919876543210"))
        assertNull(SmsParser.bankFromSender("VM-JUSPAY"))
        assertNull(SmsParser.bankFromSender("VM-MSEDCL"))
    }

    @Test fun creditWithLoanAdFooterStaysIncome() {
        val p = parse(
            "VM-ICICIB-S",
            "Dear Customer, Acct XX374 is credited with Rs 5,000.00 on 15-Oct-26 from RAHUL PATIL. UPI:6288. Get instant personal loan at 10.5%*"
        )
        assertTrue(p.isIncome)
        assertEquals(SmsAccountKind.BANK, p.accountKind)
        assertEquals("Received from people", CategoryGuesser.guess(true, p.merchant, ""))
    }

    @Test fun medicalWordsMeanHealth() {
        for (m in listOf("SAI MEDICAL", "SHREE CHEMIST", "SAIMEDICALS", "OM MEDICOS", "CITYHOSPITAL", "APOLLO PHARMACY",
            "DR LAL PATHLABS", "VISION OPTICALS", "DHANVANTARI AUSHADHALAYA", "SMILE DENTAL CLINIC")) {
            assertEquals(m + " -> Health", m + " -> " + CategoryGuesser.guess(false, m, ""))
        }
    }

    @Test fun everydayIndianShops() {
        assertEquals("Groceries", CategoryGuesser.guess(false, "BALAJI KIRANA STORES", ""))
        assertEquals("Groceries", CategoryGuesser.guess(false, "GOKUL DAIRY", ""))
        assertEquals("Food & dining", CategoryGuesser.guess(false, "AMRUTTULYA", ""))
        assertEquals("Food & dining", CategoryGuesser.guess(false, "OM SWEETS", ""))
        assertEquals("Food & dining", CategoryGuesser.guess(false, "HOTEL SAI PALACE", ""))
        assertEquals("Food & dining", CategoryGuesser.guess(false, "SKYLINE HOSPITALITY", ""))
        assertEquals("Fuel", CategoryGuesser.guess(false, "SHIVAMPETROLPUMP", ""))
        assertEquals("Personal care", CategoryGuesser.guess(false, "STYLE UNISEX SALON", ""))
        assertEquals("Home & household", CategoryGuesser.guess(false, "SHREE HARDWARE", ""))
        assertEquals("Donations", CategoryGuesser.guess(false, "DAGDUSHETH HALWAI GANPATI TRUST", ""))
        assertEquals("Education", CategoryGuesser.guess(false, "BRIGHT FUTURE CLASSES", ""))
        assertEquals("Health", CategoryGuesser.guess(false, "SHIRDI SAI MEDICAL", ""))
        assertEquals("Groceries", CategoryGuesser.guess(false, "TIRUPATI KIRANA", ""))
        assertEquals("Transport", CategoryGuesser.guess(false, "NEXA SERVICE", ""))
        assertEquals("Electricity & bills", CategoryGuesser.guess(false, "MAHAVITARAN", ""))
        assertEquals("Clothes", CategoryGuesser.guess(false, "ZUDIO", ""))
    }

    @Test fun bankCodesInBodyAreNotCategories() {
        assertNull(CategoryGuesser.guess(false, null, "INR 250.00 debited A/c no. XX1234 UPI/DR/628812345678 Not you? Report online"))
    }

    @Test fun learnsWordsFromCorrections() {
        val learned = CategoryGuesser.learnWords(
            listOf("PATIL AGRO" to "Groceries", "SHREE AGRO" to "Groceries", "GURU XEROX" to "Education", "SHREE MOTORS" to "Transport")
        )
        assertEquals("Groceries", learned["agro"])
        assertNull(learned["shree"]) // generic shop word, never learned
        assertEquals("Groceries", CategoryGuesser.guess(false, "JADHAV AGRO", "", learned))
        val ambiguous = CategoryGuesser.learnWords(listOf("NEEL TOUCH" to "Health", "AURA TOUCH" to "Personal care"))
        assertNull(ambiguous["touch"])
    }

    @Test fun shopNamesAreNotPeople() {
        assertFalse(CategoryGuesser.looksLikePerson("SHREE VIGNESHWA"))
        assertFalse(CategoryGuesser.looksLikePerson("SAI KRUPA"))
        assertTrue(CategoryGuesser.looksLikePerson("SHREENATH PARKHI"))
    }

    @Test fun qrCodesAreNotMerchantNames() {
        val p = parse("VM-ICICIB-S", "ICICI Bank Acct XX374 debited for Rs 80.01 on 15-Oct-26; paytmqr281005050101@paytm credited. UPI:628812345678.")
        assertNull(p.merchant)
        val q = parse("VM-ICICIB-S", "ICICI Bank Acct XX374 debited for Rs 290.00 on 15-Oct-26; Q123456789@ybl credited. UPI:628812345679.")
        assertNull(q.merchant)
        val r = parse("VM-ICICIB-S", "ICICI Bank Acct XX374 debited for Rs 55.00 on 16-Oct-26; zomato@icici credited. UPI:628812345670.")
        assertEquals("ZOMATO", r.merchant)
    }

    @Test fun duplicateAlertsVersusRealRepeats() {
        // Same text arriving twice is one payment.
        assertTrue(SmsDuplicates.isRepeat("VM-HDFCBK-S|1000|555", "AD-HDFCBK-S|1060|555"))
        // The bank's second sender id with different wording is one payment.
        assertTrue(SmsDuplicates.isRepeat("VM-HDFCBK-S|1000|555", "VM-HDFCUP-S|1030|777"))
        // Two real ₹20 teas from the same bank sender, different messages: two payments.
        assertFalse(SmsDuplicates.isRepeat("VM-ICICIB-S|1000|555", "VM-ICICIB-S|1090|666"))
    }

    @Test fun investmentMoneyBackIsNotIncome() {
        assertEquals(CategoryGuesser.TRANSFER_IN, CategoryGuesser.guess(true, "INDIAN CLEARING CORPORATION", ""))
        assertEquals(CategoryGuesser.TRANSFER_IN, CategoryGuesser.guess(true, "ZERODHA BROKING", ""))
        assertEquals(CategoryGuesser.TRANSFER_IN, CategoryGuesser.guess(true, "NEXTBILLION TECHNOLOGY", ""))
        assertEquals(CategoryGuesser.TRANSFER_IN, CategoryGuesser.guess(true, null, "credited by ICCL MF redemption"))
        assertEquals(CategoryGuesser.TRANSFER_IN, CategoryGuesser.guess(true, null, "credited towards FD maturity incl interest"))
        assertEquals("Interest", CategoryGuesser.guess(true, null, "Int.Pd for the quarter"))
        assertFalse(CategoryGuesser.looksLikePerson("INDIAN CLEARING CORPORATION"))
    }

    @Test fun realAlertsWithFootersAreKept() {
        val a = parse("VM-ICICIT-S", "ICICI Bank Acct XX374 debited for Rs 800.00 on 16-Oct-26; SHENDE CARDIODIA credited. UPI:629012345678. Call 18002662 for dispute. Never share OTP/PIN with anyone.")
        assertEquals(80_000L, a.amountPaise)
        assertEquals("SHENDE CARDIODIA", a.merchant)
        val b = parse("JD-SBIUPI-S", "Dear UPI user A/C X6141 debited by 650.0 on date 16Oct26 trf to PRAJKTA SHENDE Refno 629012345679. If not u? call 1800111109. Get pre-approved personal loan, apply now -SBI")
        assertEquals(65_000L, b.amountPaise)
        val c = parse("AX-HDFCBK-S", "Sent Rs.488.00 From HDFC Bank A/C *1234 To SHRIKRISHNA MEDICAL On 16/10/26 Ref 629012345670. Not You? Call 18002586161. Click here to view offers")
        assertEquals("Health", CategoryGuesser.guess(false, c.merchant, ""))
        val d = parse("VM-KOTAKB-S", "Rs.365.00 debited from Kotak Bank AC X2222 to pradeepsweets@okicici on 16-10-26. EMI due on your card? Pay now. UPI Ref 629012345671")
        assertEquals("PRADEEPSWEETS", d.merchant)
    }

    @Test fun skipReasonsAreReported() {
        assertEquals(SmsParser.Skip.OTP, SmsParser.parseWhy("VM-HDFCBK-S", "123456 is your OTP for txn of Rs.500.00 at AMAZON. Do not share.").second)
        assertEquals(SmsParser.Skip.FAILED, SmsParser.parseWhy("VM-HDFCBK-S", "Payment of Rs.300.00 failed. Amount will be refunded if debited.").second)
        assertEquals(SmsParser.Skip.PENDING, SmsParser.parseWhy("VM-BAJAJF-S", "Your EMI of Rs 8,450 will be debited on 05-11-26 from a/c XX1234.").second)
        assertEquals(SmsParser.Skip.NOT_BANK, SmsParser.parseWhy("+919876543210", "debited Rs 500").second)
        assertEquals(SmsParser.Skip.PROMO, SmsParser.parseWhy("VM-ICICIB-S", "Get a personal loan of up to Rs 25 lakh. Apply now!").second)
    }
}
