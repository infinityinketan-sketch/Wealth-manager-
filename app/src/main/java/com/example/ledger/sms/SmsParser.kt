package com.example.ledger.sms

/** Where the money moved, as far as the SMS tells us. */
enum class SmsAccountKind { BANK, CARD, LOAN }

/** What one bank / UPI / card / loan SMS says, in plain values. Money is in paise. */
data class ParsedSms(
    /** True when money came INTO the account named in the message. */
    val isIncome: Boolean,
    val amountPaise: Long,
    val bankName: String,
    /** Digits of the account / card number the message shows (3 to 6 of them), if any. */
    val accountDigits: String?,
    val accountKind: SmsAccountKind,
    /** Who was paid / who paid, as best the message tells us (e.g. "SWIGGY", "RAMESH KUMAR"). */
    val merchant: String?,
    /** Balance after this transaction, when the message includes it (bank accounts only). */
    val balancePaise: Long?,
    /** A category the message itself makes certain (e.g. "EMI" for a loan repayment), else null. */
    val categoryHint: String? = null
)

/**
 * Reads Indian bank SMS (debit/credit alerts, UPI, cards, loan repayments). Everything runs on the
 * phone. Returns null for anything that is not a completed money movement in the person's own
 * bank, card or loan account: OTPs, offers, reminders, failed payments, merchant receipts
 * (MSEDCL, MakeMyTrip, Juspay ...) and messages from ordinary phone numbers.
 */
object SmsParser {

    /** Bump when the reading rules change: the app then re-reads old messages with the new rules. */
    const val VERSION = 4

    private val amountRegex = Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")

    private val skipWords = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "will be debited", "will be credited", "would be debited", "to be debited", "scheduled",
        "is due", "are due", "due on", "due date", "due by", "min due", "minimum due", "total due",
        "payment due", "amount due", "overdue", "bill generated", "statement generated", "statement for",
        "failed", "unsuccessful", "declined", "not successful", "could not", "insufficient", "rejected",
        "request received", "has requested", "collect request", "is requesting", "pre-approved",
        "pre approved", "apply now", "click here", "offer", "win ", "reminder", "avail now", "eligible for",
        "bounced", "dishonour", "returned unpaid", "presented"
    )

    private val debitWords = listOf(
        "debited", "debit by", "has a debit", "spent", "withdrawn", "withdrawal", "purchase", "paid",
        "sent", "payment of", "txn of", "dr ", "dr.", "transferred"
    )
    private val creditWords = listOf(
        "credited", "credit by", "has a credit", "received", "deposited", "deposit of", "refund",
        "reversed", "credit of", "cashback of", "cr ", "cr."
    )

    /** A biller / lender confirming it got paid. Not money into the person's account. */
    private val receiptWords = listOf(
        "thank you for", "we have received", "payment received", "received your payment",
        "received payment", "has been received", "towards your"
    )

    private val banks = listOf(
        "HDFC" to "HDFC Bank", "SBI" to "SBI", "ICICI" to "ICICI Bank", "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank", "KMBL" to "Kotak Bank", "PNB" to "PNB", "BOB" to "Bank of Baroda",
        "BARODA" to "Bank of Baroda", "CANBNK" to "Canara Bank", "CANARA" to "Canara Bank",
        "IDFC" to "IDFC First Bank", "YESBNK" to "Yes Bank", "YESBK" to "Yes Bank",
        "INDUS" to "IndusInd Bank", "UNIONB" to "Union Bank", "UBOI" to "Union Bank",
        "BOIIND" to "Bank of India", "BOINDA" to "Bank of India", "IDBI" to "IDBI Bank",
        "FEDBNK" to "Federal Bank", "FEDFIB" to "Federal Bank", "AUBANK" to "AU Bank", "AUSFB" to "AU Bank",
        "RBLBNK" to "RBL Bank", "RBLCRD" to "RBL Bank", "SCBANK" to "Standard Chartered", "STANCB" to "Standard Chartered",
        "CITIBK" to "Citi", "CITIBA" to "Citi", "HSBC" to "HSBC", "AMEX" to "Amex", "BANDHN" to "Bandhan Bank",
        "IOBCHN" to "Indian Overseas Bank", "IOBBNK" to "Indian Overseas Bank", "CBOI" to "Central Bank",
        "CENTBK" to "Central Bank", "UCOBNK" to "UCO Bank", "SIBSMS" to "South Indian Bank",
        "KVBANK" to "Karur Vysya Bank", "JKBANK" to "J&K Bank", "DCBBNK" to "DCB Bank", "SLICEB" to "Slice",
        "SLCEIT" to "Slice", "ONECRD" to "OneCard", "AIRBNK" to "Airtel Payments Bank",
        "INDBNK" to "Indian Bank", "MAHABK" to "Bank of Maharashtra", "MAHBNK" to "Bank of Maharashtra",
        "BOMBNK" to "Bank of Maharashtra", "COSMOS" to "Cosmos Bank", "SARBNK" to "Saraswat Bank",
        "SARASW" to "Saraswat Bank", "TJSB" to "TJSB Bank", "JANATA" to "Janata Sahakari Bank",
        "PDCCBK" to "PDCC Bank", "EQUITS" to "Equitas Bank", "UJJIVN" to "Ujjivan Bank", "JUPITR" to "Jupiter",
        "FIMONY" to "Fi", "NIYOBK" to "Niyo"
    )

    /** Bank names as written inside messages, for alerts sent from a sender id we do not recognise. */
    private val bankNamesInBody = listOf(
        "hdfc bank" to "HDFC Bank", "icici bank" to "ICICI Bank", "axis bank" to "Axis Bank",
        "kotak" to "Kotak Bank", "state bank" to "SBI", "sbi" to "SBI", "bank of baroda" to "Bank of Baroda",
        "canara bank" to "Canara Bank", "idfc first" to "IDFC First Bank", "yes bank" to "Yes Bank",
        "indusind" to "IndusInd Bank", "union bank" to "Union Bank", "bank of india" to "Bank of India",
        "bank of maharashtra" to "Bank of Maharashtra", "punjab national" to "PNB", "pnb" to "PNB",
        "idbi" to "IDBI Bank", "federal bank" to "Federal Bank", "cosmos" to "Cosmos Bank",
        "saraswat" to "Saraswat Bank"
    )

    private val lenders = listOf(
        "BAJAJF" to "Bajaj Finance", "BAJFIN" to "Bajaj Finance", "BFLLTD" to "Bajaj Finance",
        "TATACP" to "Tata Capital", "TATACA" to "Tata Capital", "HDBFSL" to "HDB Financial", "HDBFIN" to "HDB Financial",
        "LICHFL" to "LIC Housing Finance", "HMCRDT" to "Home Credit", "HOMECR" to "Home Credit",
        "ABFLTD" to "Aditya Birla Finance", "ADTBRL" to "Aditya Birla Finance", "LNTFIN" to "L&T Finance",
        "LTFINC" to "L&T Finance", "MUTHOT" to "Muthoot Finance", "IIFLFN" to "IIFL Finance", "PIRAML" to "Piramal Finance",
        "PNBHFL" to "PNB Housing", "CHOLAF" to "Chola Finance", "MMFSL" to "Mahindra Finance", "SHRIRM" to "Shriram Finance",
        "POONAW" to "Poonawalla Fincorp", "KREDTB" to "KreditBee", "MONEYV" to "MoneyView", "NAVIFN" to "Navi"
    )

    private fun senderCore(sender: String): String? {
        val core = sender.trim().uppercase()
        if (core.none { it.isLetter() }) return null // an ordinary phone number
        return core.replace(Regex("^[A-Z0-9]{2}-"), "").replace(Regex("-[A-Z]$"), "")
    }

    /** "VM-HDFCBK-S" -> "HDFC Bank". Null when the sender is not a bank we know. */
    fun bankFromSender(sender: String): String? {
        val core = senderCore(sender) ?: return null
        for ((key, name) in banks) if (core.contains(key)) return name
        return null
    }

    fun lenderFromSender(sender: String): String? {
        val core = senderCore(sender) ?: return null
        for ((key, name) in lenders) if (core.contains(key)) return name
        return null
    }

    // "A/c XX1234", "A/cX9023", "Acct XX123", "AC X2222", "Card ending 5678", "a/c no. XXXXXX1234"
    private val accountRegex = Regex(
        """(?i)(?<![a-z])(?:a/c|acct|account|ac|card|loan)(?![a-wyz])\s*(?:no\.?|number|num|ending(?:\s*with)?|:|-|\s)*[x*•.]*\s*(\d{3,6})(?!\d)"""
    )
    private val balanceRegex = Regex(
        """(?i)\b(?:avl\.?\s*bal(?:ance)?|available\s*bal(?:ance)?|avbl\.?\s*bal(?:ance)?|a/c\s*bal(?:ance)?|clear\s*bal(?:ance)?|bal(?:ance)?)\b[^0-9]{0,20}?([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    )

    fun parse(sender: String, body: String): ParsedSms? {
        if (senderCore(sender) == null) return null
        val text = body.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        val lower = text.lowercase()
        if (skipWords.any { lower.contains(it) }) return null

        val amount = transactionAmount(text, lower) ?: return null

        // Only a message about the person's own loan account counts, not an ad footer like "Get an instant loan".
        val loanAccountPhrase = Regex("""\bloan\s*(?:a/c|acct|account|ac\b|no\.?|number)|towards\s+(?:your\s+|the\s+)?(?:loan|emi)\b""")
            .containsMatchIn(lower)
        val mentionsLoan = loanAccountPhrase ||
            (lenderFromSender(sender) != null && (Regex("""\bloan\b""").containsMatchIn(lower) || lower.contains("emi ")))
        val isReceipt = receiptWords.any { lower.contains(it) }
        val balance = balanceRegex.find(text)?.groupValues?.get(1)?.let { toPaise(it) }
        val isDisbursal = lower.contains("disburs")
        val isCard = !lower.contains("debit card") && (
            lower.contains("credit card") ||
                (lower.contains("card") && (lower.contains("limit") || lower.contains("lmt"))) ||
                (lower.contains("card") && lower.contains("spent") && balance == null)
            )

        var bank = bankFromSender(sender)
        val lender = lenderFromSender(sender)
        val digits = accountRegex.find(text)?.groupValues?.get(1)

        val debitAt = debitWords.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val creditAt = creditWords.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        if (debitAt == null && creditAt == null && !isReceipt) return null
        val creditFirst = when {
            debitAt == null -> true
            creditAt == null -> false
            else -> creditAt < debitAt
        }

        // A lender (or a bank's loan desk) saying it received the EMI: for the person that is an EMI spend.
        if (mentionsLoan && !isDisbursal && (creditFirst || debitAt == null) && (lender != null || bank != null)) {
            return ParsedSms(
                isIncome = false,
                amountPaise = amount,
                bankName = lender ?: bank!!,
                accountDigits = digits,
                accountKind = SmsAccountKind.LOAN,
                merchant = lender?.uppercase(),
                balancePaise = null,
                categoryHint = "EMI"
            )
        }

        if (bank == null) {
            // Unknown sender id: accept only a real account alert that names a bank we know.
            if (lender != null || digits == null) return null
            bank = bankNamesInBody.firstOrNull { lower.contains(it.first) }?.second ?: return null
            if (!Regex("""(?i)(?:a/c|acct|account|ac|card)\s*(?:no\.?)?\s*[x*]+\d""").containsMatchIn(text)) return null
        }

        val fromBankAccount = Regex("""(?i)(?<![a-z])(?:a/c|acct|account|ac)(?![a-wyz])""").containsMatchIn(text)
        if (isCard && !creditFirst && fromBankAccount &&
            (lower.contains("towards") || lower.contains("bill") || lower.contains("payment to") || lower.contains("credit card payment"))
        ) {
            // Money left a bank account to pay a credit card bill: a transfer, not spending.
            return ParsedSms(
                isIncome = false,
                amountPaise = amount,
                bankName = bank,
                accountDigits = digits,
                accountKind = SmsAccountKind.BANK,
                merchant = null,
                balancePaise = balance,
                categoryHint = CategoryGuesser.CARD_BILL
            )
        }

        if (isCard) {
            // On a credit card, "payment received" means the bill was paid: a transfer, not income.
            val paymentToCard = isReceipt || (creditFirst && lower.contains("payment"))
            return ParsedSms(
                isIncome = creditFirst || paymentToCard,
                amountPaise = amount,
                bankName = bank,
                accountDigits = digits,
                accountKind = SmsAccountKind.CARD,
                merchant = if (paymentToCard) null else findMerchant(text, creditFirst),
                balancePaise = null,
                categoryHint = if (paymentToCard) CategoryGuesser.TRANSFER_IN else null
            )
        }

        // A bank's own "thank you for your payment" style receipt with no account movement wording.
        if (isReceipt && debitAt == null && !lower.contains("credited")) return null

        return ParsedSms(
            isIncome = creditFirst,
            amountPaise = amount,
            bankName = bank,
            accountDigits = digits,
            accountKind = SmsAccountKind.BANK,
            merchant = findMerchant(text, creditFirst),
            balancePaise = balance,
            // Loan money landing in the account is not income.
            categoryHint = if (isDisbursal && creditFirst) CategoryGuesser.TRANSFER_IN else null
        )
    }

    private fun transactionAmount(text: String, lower: String): Long? {
        for (m in amountRegex.findAll(text)) {
            val before = lower.substring(maxOf(0, m.range.first - 22), m.range.first)
            if (before.contains("bal") || before.contains("limit") || before.contains("lmt") ||
                before.contains("avl") || before.contains("available") || before.contains("outstanding")
            ) continue
            val v = toPaise(m.groupValues[1])
            if (v != null && v > 0) return v
        }
        // Some banks (SBI) write "debited by 1200.0" with no Rs / INR in front.
        val bare = Regex("""(?i)(?:debited|credited|debit|credit)\s+(?:by|for|with)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""").find(text)
        return bare?.groupValues?.get(1)?.let { toPaise(it) }?.takeIf { it > 0 }
    }

    private fun toPaise(raw: String): Long? {
        val cleaned = raw.replace(",", "").trimEnd('.')
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(".")
        val whole = parts[0].toLongOrNull() ?: return null
        val frac = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L else 0L
        return whole * 100 + frac
    }

    private const val NAME = """[A-Za-z0-9][A-Za-z0-9 &.\-'@_*]{1,38}?"""
    private const val STOP = """(?=\s+(?:on|ref|refno|upi|via|using|from|for|avl|bal|if|not|txn|trf|vpa|a/c|ac|is|has|was|dt|date|info|towards|thru|through|credited|debited|at)\b|\s*[.,;:(]|\s*$)"""

    private val vpaRegex = Regex("""(?i)(?:vpa|upi id)\s*:?\s*([a-z0-9.\-_]{2,}@[a-z0-9]+)(?:\s*\(([^)]{2,40})\))?""")
    private val upiSlashRegex = Regex("""(?i)upi/(?:dr/|cr/|p2a/|p2m/)?\d{6,}/([A-Za-z][A-Za-z0-9 &.\-]{1,30})""")
    private val infoRegex = Regex("""(?i)info[:\- ]+(?:upi[/\-]|nach[/\-]|imps[/\-]|neft[/\-]|pos[/\-])?\s*([A-Za-z][A-Za-z0-9 &.\-/]{2,34}?)$STOP""")
    // ICICI: "Acct XX123 debited for Rs 250.00 on 15-Oct-26; SWIGGY credited."
    private val semicolonCreditedRegex = Regex("""(?i);\s*([A-Za-z][A-Za-z0-9 &.\-']{1,38}?)\s+credited""")
    // ICICI card: "... on 15-Oct-26 on AMAZON. Avl Limit"
    private val onDateOnRegex = Regex("""(?i)\bon\s+\d{1,2}[-/ ][A-Za-z0-9]{2,3}[-/ ]\d{2,4}\s+(?:on|at)\s+($NAME)$STOP""")
    private val atRegex = Regex("""(?i)\b(?:at|@)\s+($NAME)$STOP""")
    private val toRegex = Regex("""(?i)\b(?:trf to|transfer to|paid to|sent to|payment to|debited for|to)\s+($NAME)$STOP""")
    private val fromRegex = Regex("""(?i)\b(?:received from|credited by|credit from|deposited by|transfer from|trf from|from|by)\s+($NAME)$STOP""")

    private val notMerchant = listOf(
        "your", "a/c", "ac ", "account", "card", "bank", "ending", "xx", "the ", "my ", "upi", "rs", "inr", "sb ",
        "transfer", "neft", "imps", "rtgs", "cash", "ref", "mobile", "net banking", "netbanking", "self", "you",
        "customer", "user", "beneficiary", "debit", "credit", "amount", "dear", "info"
    )

    private fun clean(raw: String?): String? {
        val s = raw?.substringBefore("@")?.trim()?.trim('.', '-', '/', ' ', '*') ?: return null
        if (s.length < 2) return null
        val l = s.lowercase()
        if (notMerchant.any { l == it.trim() || l.startsWith(it) }) return null
        if (s.all { it.isDigit() || it == 'x' || it == 'X' || it == '*' || it == ' ' }) return null
        return s.uppercase().take(32)
    }

    private fun firstClean(regex: Regex, text: String): String? =
        regex.findAll(text).firstNotNullOfOrNull { clean(it.groupValues[1]) }

    private fun findMerchant(text: String, isIncome: Boolean): String? {
        vpaRegex.find(text)?.let { m ->
            clean(m.groupValues[2])?.let { return it }
            val handle = m.groupValues[1].substringBefore("@")
            if (handle.count { it.isLetter() } >= 3) return handle.uppercase()
        }
        firstClean(upiSlashRegex, text)?.let { return it }
        if (!isIncome) firstClean(semicolonCreditedRegex, text)?.let { return it }
        firstClean(infoRegex, text)?.let { return it }
        if (isIncome) {
            firstClean(fromRegex, text)?.let { return it }
        } else {
            firstClean(onDateOnRegex, text)?.let { return it }
            firstClean(atRegex, text)?.let { return it }
            firstClean(toRegex, text)?.let { return it }
        }
        return null
    }
}
