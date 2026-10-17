package com.example.ledger.sms

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DueKindHint { CARD, LOAN, INSURANCE, UTILITY, OTHER }

/** A bill or payment that is coming up, read from a reminder / statement SMS. */
data class DueNotice(
    val title: String,
    val digits: String?,
    val kind: DueKindHint,
    val amountPaise: Long,
    val minDuePaise: Long?,
    val dueDate: LocalDate
)

/**
 * Reads "bill due" messages: credit-card statements, electricity / gas / phone bills, loan EMIs and
 * insurance premiums. Needs an amount and a due date; anything else is ignored.
 */
object DueParser {

    private val billers = listOf(
        "MSEDCL" to "MSEB electricity", "MAHADI" to "MSEB electricity", "MSEBCL" to "MSEB electricity",
        "MAHAVT" to "MSEB electricity", "TATAPW" to "Tata Power", "ADANIE" to "Adani Electricity", "BESCOM" to "BESCOM",
        "BSESRJ" to "BSES", "BSESYM" to "BSES", "TORNTP" to "Torrent Power", "AIRTEL" to "Airtel", "AIRTLB" to "Airtel",
        "JIOINF" to "Jio", "JIOCOM" to "Jio", "VIINFO" to "Vi", "VFCARE" to "Vi", "BSNL" to "BSNL", "MNGL" to "MNGL gas",
        "MAHGAS" to "Mahanagar Gas", "ACTFIB" to "ACT Fibernet", "HATHWY" to "Hathway", "TATAPL" to "Tata Play",
        "LICIND" to "LIC", "LICHFL" to "LIC Housing", "RNLIFE" to "Reliance Nippon Life", "RELNIP" to "Reliance Nippon Life",
        "HDFCLI" to "HDFC Life", "HDFCLF" to "HDFC Life", "IPRULI" to "ICICI Pru Life", "ICIPRU" to "ICICI Pru Life",
        "SBILIF" to "SBI Life", "MAXLIF" to "Max Life", "TATAAI" to "Tata AIA", "BAJAJA" to "Bajaj Allianz",
        "STARHL" to "Star Health", "NIVABU" to "Niva Bupa", "CAREHL" to "Care Health", "HDFCER" to "HDFC Ergo"
    )

    private val promo = listOf("offer", "win ", "cashback offer", "apply now", "pre-approved", "pre approved", "otp")

    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private const val DATE =
        """(\d{4}-\d{1,2}-\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|\d{1,2}(?:st|nd|rd|th)?[-/ ]?[A-Za-z]{3,9}[-/ ,]*\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{4}|\d{1,2}(?:st|nd|rd|th)?[-/ ][A-Za-z]{3,9})"""

    private val dueDateRegex = Regex(
        """(?i)(?:due\s*(?:date)?\s*(?:is|on|by)?|pay(?:able)?\s*(?:on|by|before)|on\s+or\s+before|before|will\s+be\s+(?:debited|presented|deducted)\s+on|scheduled\s+(?:on|for)|debited\s+on)\s*[:\-]?\s*$DATE"""
    )
    private val totalRegex = Regex(
        """(?i)(?:total\s*(?:amt|amount)?\s*(?:due|payable|outstanding)|amount\s*(?:due|payable)|amt\s*due|bill\s*(?:amt|amount|of)|outstanding\s*(?:amt|amount)?|emi\s*(?:amt|amount)?\s*(?:of)?|premium\s*(?:amt|amount)?\s*(?:of)?)[^0-9]{0,18}?([0-9][0-9,]*(?:\.\d{1,2})?)"""
    )
    private val minRegex = Regex("""(?i)min(?:imum|\.)?\s*(?:amt|amount)?\s*(?:due|payable)[^0-9]{0,18}?([0-9][0-9,]*(?:\.\d{1,2})?)""")
    private val anyAmount = Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.\d{1,2})?)""")
    private val numberRegex = Regex(
        """(?i)(?:card|a/c|acct|account|loan|policy|consumer|ca|mobile|no\.?|number|ending)[^0-9]{0,20}?[x*]*(\d{3,16})"""
    )

    fun parse(sender: String, body: String, smsDate: LocalDate): DueNotice? {
        val core = sender.trim().uppercase()
        if (core.none { it.isLetter() }) return null
        val text = body.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        val lower = text.lowercase()
        if (promo.any { lower.contains(it) }) return null
        // Already paid / receipts are not upcoming dues.
        if (listOf("has been paid", "payment received", "received your payment", "thank you for your payment", "successfully paid", "is paid").any { lower.contains(it) }) return null

        val dueMatch = dueDateRegex.find(text) ?: return null
        val due = parseDate(dueMatch.groupValues[1], smsDate) ?: return null
        val days = ChronoUnit.DAYS.between(smsDate, due)
        if (days < -45 || days > 120) return null

        val minDue = minRegex.find(text)?.groupValues?.get(1)?.let { toPaise(it) }
        val total = totalRegex.findAll(text).mapNotNull { toPaise(it.groupValues[1]) }.firstOrNull { it > 0 && it != minDue }
            ?: anyAmount.findAll(text).mapNotNull { m ->
                val before = lower.substring(maxOf(0, m.range.first - 20), m.range.first)
                if (before.contains("min") || before.contains("limit") || before.contains("bal")) null else toPaise(m.groupValues[1])
            }.firstOrNull { it > 0 }
            ?: minDue
            ?: return null

        val kind = when {
            lower.contains("credit card") || (lower.contains("card") && (lower.contains("statement") || minDue != null)) -> DueKindHint.CARD
            Regex("""\b(?:emi|loan)\b""").containsMatchIn(lower) -> DueKindHint.LOAN
            lower.contains("premium") || lower.contains("policy") -> DueKindHint.INSURANCE
            listOf("electric", "bill", "postpaid", "broadband", "gas", "water", "consumer", "recharge").any { lower.contains(it) } -> DueKindHint.UTILITY
            else -> DueKindHint.OTHER
        }

        val stripped = core.replace(Regex("^[A-Z0-9]{2}-"), "").replace(Regex("-[A-Z]$"), "")
        val biller = billers.firstOrNull { stripped.contains(it.first) }?.second
        val bank = SmsParser.bankFromSender(sender)
        val lender = SmsParser.lenderFromSender(sender)
        val title = when {
            biller != null -> biller
            lender != null -> "$lender loan"
            bank != null && kind == DueKindHint.CARD -> "$bank credit card"
            bank != null && kind == DueKindHint.LOAN -> "$bank loan"
            bank != null -> bank
            kind == DueKindHint.OTHER -> return null // an unknown sender with no bill words: not trusted
            else -> stripped.lowercase().replaceFirstChar { it.uppercase() }
        }
        val digits = numberRegex.find(text)?.groupValues?.get(1)?.takeLast(4)
        return DueNotice(title, digits, kind, total, minDue?.takeIf { it < total }, due)
    }

    /** Reads "17-01-2025", "17/01/25", "17-Jan-25", "17 Jan 2025", "17Jan25", "2025-01-17", "Jan 17, 2025", "17 Jan". */
    fun parseDate(raw: String, ref: LocalDate): LocalDate? {
        val s = raw.trim().trimEnd('.', ',').lowercase().replace(Regex("(\\d)(st|nd|rd|th)"), "$1")
        fun year(y: String): Int = if (y.length == 2) 2000 + y.toInt() else y.toInt()
        fun safe(y: Int, m: Int, d: Int): LocalDate? = try { LocalDate.of(y, m, d) } catch (e: Exception) { null }

        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""").find(s)?.let { m ->
            return safe(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})$""").find(s)?.let { m ->
            return safe(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
        Regex("""^(\d{1,2})[-/ ]?([a-z]{3,9})[-/ ,]*(\d{2,4})$""").find(s)?.let { m ->
            val month = months[m.groupValues[2].take(3)] ?: return null
            return safe(year(m.groupValues[3]), month, m.groupValues[1].toInt())
        }
        Regex("""^([a-z]{3,9})\s+(\d{1,2}),?\s+(\d{4})$""").find(s)?.let { m ->
            val month = months[m.groupValues[1].take(3)] ?: return null
            return safe(m.groupValues[3].toInt(), month, m.groupValues[2].toInt())
        }
        Regex("""^(\d{1,2})[-/ ]([a-z]{3,9})$""").find(s)?.let { m ->
            val month = months[m.groupValues[2].take(3)] ?: return null
            // No year: the next such date on or after the message date (with a little slack).
            val d = safe(ref.year, month, m.groupValues[1].toInt()) ?: return null
            return if (d.isBefore(ref.minusDays(15))) d.plusYears(1) else d
        }
        return null
    }

    private fun toPaise(raw: String): Long? {
        val cleaned = raw.replace(",", "").trimEnd('.')
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(".")
        val whole = parts[0].toLongOrNull() ?: return null
        val frac = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L else 0L
        return whole * 100 + frac
    }
}
