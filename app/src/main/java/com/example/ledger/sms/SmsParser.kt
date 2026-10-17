package com.example.ledger.sms

/** What one bank / UPI / card SMS says, in plain values. Money is in paise. */
data class ParsedSms(
    val isIncome: Boolean,
    val amountPaise: Long,
    val bankName: String,
    /** Last four digits of the account or card, when the message names one. */
    val last4: String?,
    val isCreditCard: Boolean,
    /** Who was paid / who paid, as best the message tells us (e.g. "SWIGGY", "Ramesh Kumar"). */
    val merchant: String?,
    /** Balance after this transaction, when the message includes it. */
    val balancePaise: Long?
)

/**
 * Reads Indian bank SMS (debit/credit alerts, UPI, card spends). Everything runs on the phone.
 * Returns null for anything that is not a completed money movement: OTPs, offers, due reminders,
 * failed payments, and messages from ordinary phone numbers.
 */
object SmsParser {

    private val amountRegex = Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")

    private val skipWords = listOf(
        "otp", "one time password", "one-time password", "will be debited", "will be credited",
        "is due", "are due", "due on", "due date", "min due", "minimum due", "total due", "payment due",
        "failed", "unsuccessful", "declined", "not successful", "could not", "insufficient",
        "request received", "requested", "collect request", "pre-approved", "pre approved",
        "apply now", "click here", "offer", "cashback offer", "win ", "reminder", "mandate"
    )

    private val debitWords = listOf(
        "debited", "spent", "withdrawn", "purchase", "paid", "sent", "payment of", "debit", "txn of"
    )
    private val creditWords = listOf(
        "credited", "received", "deposited", "refund", "reversed", "credit of", "cashback of"
    )

    private val banks = listOf(
        "HDFC" to "HDFC Bank", "SBI" to "SBI", "ICICI" to "ICICI Bank", "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank", "KMB" to "Kotak Bank", "PNB" to "PNB", "BOB" to "Bank of Baroda",
        "BARODA" to "Bank of Baroda", "CANBNK" to "Canara Bank", "CANARA" to "Canara Bank",
        "IDFC" to "IDFC First Bank", "YESBNK" to "Yes Bank", "YESB" to "Yes Bank",
        "INDUS" to "IndusInd Bank", "PAYTM" to "Paytm Bank", "PHONEPE" to "PhonePe",
        "UNION" to "Union Bank", "UBI" to "Union Bank", "BOI" to "Bank of India", "IDBI" to "IDBI Bank",
        "FEDBNK" to "Federal Bank", "FEDERAL" to "Federal Bank", "AUBANK" to "AU Bank",
        "RBL" to "RBL Bank", "SCBANK" to "Standard Chartered", "CITI" to "Citi", "HSBC" to "HSBC",
        "AMEX" to "Amex", "BANDHAN" to "Bandhan Bank", "IOB" to "Indian Overseas Bank",
        "CBOI" to "Central Bank", "UCOBNK" to "UCO Bank", "SIB" to "South Indian Bank",
        "KVB" to "Karur Vysya Bank", "JKB" to "J&K Bank", "DCB" to "DCB Bank", "SLICE" to "Slice",
        "ONECARD" to "OneCard", "AIRTEL" to "Airtel Payments Bank", "JIOPAY" to "Jio Payments Bank"
    )

    /** Turns a sender id like "VM-HDFCBK-S" into a bank name, or null if the sender is not alphabetic. */
    fun bankFromSender(sender: String): String? {
        val core = sender.trim().uppercase()
        if (core.none { it.isLetter() }) return null // a normal phone number
        val stripped = core.replace(Regex("^[A-Z0-9]{2}-"), "").replace(Regex("-[A-Z]$"), "")
        for ((key, name) in banks) if (stripped.contains(key)) return name
        return stripped.replace("-", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    fun parse(sender: String, body: String): ParsedSms? {
        val bank = bankFromSender(sender) ?: return null
        val text = body.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        val lower = text.lowercase()

        if (skipWords.any { lower.contains(it) }) return null

        // Amounts: the ones labelled as balance / limit are not the transaction amount.
        val amounts = amountRegex.findAll(text).toList()
        var txnAmount: Long? = null
        for (m in amounts) {
            val before = lower.substring(maxOf(0, m.range.first - 22), m.range.first)
            if (before.contains("bal") || before.contains("limit") || before.contains("avl") || before.contains("available")) continue
            txnAmount = toPaise(m.groupValues[1])
            break
        }
        if (txnAmount == null) {
            // Some banks (SBI) write "debited by 1200.0" with no Rs / INR in front.
            val bare = Regex("""(?i)(?:debited|credited)\s+(?:by|for|with)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""").find(text)
            txnAmount = bare?.groupValues?.get(1)?.let { toPaise(it) }
        }
        val amount = txnAmount ?: return null
        if (amount <= 0) return null

        // Direction: whichever of the debit / credit words comes first.
        val debitAt = debitWords.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val creditAt = creditWords.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        if (debitAt == null && creditAt == null) return null
        val isIncome = when {
            debitAt == null -> true
            creditAt == null -> false
            else -> creditAt < debitAt
        }

        val isCard = lower.contains("credit card") || lower.contains("cc ")
        val last4 = Regex("""(?i)(?:a/c|acct|account|card|ac)[^0-9]{0,25}?(\d{4})(?!\d)""").find(text)?.groupValues?.get(1)
        val balance = Regex("""(?i)(?:avl\.?\s*bal(?:ance)?|available\s*bal(?:ance)?|avbl\.?\s*bal(?:ance)?|bal(?:ance)?)[^0-9]{0,20}?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
            .find(text)?.groupValues?.get(1)?.let { toPaise(it) }

        return ParsedSms(
            isIncome = isIncome,
            amountPaise = amount,
            bankName = bank,
            last4 = last4,
            isCreditCard = isCard,
            merchant = findMerchant(text, isIncome),
            balancePaise = balance
        )
    }

    private fun toPaise(raw: String): Long? {
        val cleaned = raw.replace(",", "").trimEnd('.')
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(".")
        val whole = parts[0].toLongOrNull() ?: return null
        val frac = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L else 0L
        return whole * 100 + frac
    }

    private const val NAME = """[A-Za-z0-9][A-Za-z0-9 &.\-'@_]{1,38}?"""
    private const val STOP = """(?=\s+(?:on|ref|refno|upi|via|using|from|for|avl|bal|if|not|txn|trf|vpa|a/c|ac|is|has|was|dt|date|info|towards|thru|through)\b|\s*[.,;(]|\s*$)"""

    private val vpaRegex = Regex("""(?i)(?:vpa|upi id)\s*:?\s*([a-z0-9.\-_]{2,}@[a-z0-9]+)(?:\s*\(([^)]{2,40})\))?""")
    private val upiSlashRegex = Regex("""(?i)upi/(?:dr/|cr/|p2a/|p2m/)?\d{6,}/([A-Za-z][A-Za-z0-9 &.\-]{1,30})""")
    private val infoRegex = Regex("""(?i)info[:\- ]+(?:upi[/\-]|nach[/\-]|imps[/\-]|neft[/\-]|pos[/\-])?\s*([A-Za-z][A-Za-z0-9 &.\-/]{2,34}?)""" + STOP)
    private val atRegex = Regex("""(?i)\b(?:at|@)\s+($NAME)$STOP""")
    private val toRegex = Regex("""(?i)\b(?:trf to|transfer to|paid to|sent to|payment to|debited for|to)\s+($NAME)$STOP""")
    private val fromRegex = Regex("""(?i)\b(?:received from|credited by|credit from|deposited by|transfer from|trf from|from|by)\s+($NAME)$STOP""")

    private val notMerchant = listOf("your", "a/c", "ac ", "account", "card", "bank", "ending", "xx", "the ", "my ", "upi", "rs", "inr", "sb ")

    private fun clean(raw: String?): String? {
        val s = raw?.substringBefore("@")?.trim()?.trim('.', '-', '/', ' ') ?: return null
        if (s.length < 2) return null
        val l = s.lowercase()
        if (notMerchant.any { l == it.trim() || l.startsWith(it) }) return null
        if (s.all { it.isDigit() || it == 'x' || it == 'X' || it == '*' }) return null
        return s.uppercase().take(32)
    }

    private fun findMerchant(text: String, isIncome: Boolean): String? {
        vpaRegex.find(text)?.let { m ->
            val named = clean(m.groupValues[2])
            if (named != null) return named
            val handle = m.groupValues[1].substringBefore("@")
            if (handle.any { it.isLetter() } && handle.length > 2 && !handle.all { it.isDigit() }) return handle.uppercase()
        }
        upiSlashRegex.find(text)?.let { clean(it.groupValues[1])?.let { n -> return n } }
        infoRegex.find(text)?.let { clean(it.groupValues[1])?.let { n -> return n } }
        if (isIncome) {
            fromRegex.find(text)?.let { clean(it.groupValues[1])?.let { n -> return n } }
        } else {
            atRegex.find(text)?.let { clean(it.groupValues[1])?.let { n -> return n } }
            toRegex.find(text)?.let { clean(it.groupValues[1])?.let { n -> return n } }
        }
        return null
    }
}
