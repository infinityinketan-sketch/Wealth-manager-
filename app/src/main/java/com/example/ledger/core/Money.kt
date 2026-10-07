package com.example.ledger.core

/**
 * All money in the app is stored as a Long number of paise (1 rupee = 100 paise),
 * so there is never any floating-point rounding.
 */
object Money {

    /** Formats paise as rupees with Indian grouping, e.g. 12345678 -> "₹1,23,456.78". */
    fun formatInr(paise: Long, showSymbol: Boolean = true): String {
        val negative = paise < 0
        val absolute = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(paise)
        val rupees = absolute / 100
        val fraction = (absolute % 100).toInt()

        val grouped = groupIndian(rupees.toString())
        val number = if (fraction == 0) grouped else grouped + "." + fraction.toString().padStart(2, '0')

        val sign = if (negative) "-" else ""
        val symbol = if (showSymbol) "₹" else ""
        return sign + symbol + number
    }

    /** Groups a string of digits the Indian way: last three digits, then pairs. */
    fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + lastThree
    }

    /**
     * Reads what a person types into an amount box ("1,250.50", "₹ 500", "75") and
     * returns paise, or null if it is not a valid amount. More than two decimal
     * places is rejected rather than silently rounded.
     */
    fun parseToPaise(input: String): Long? {
        val cleaned = input.replace("₹", "").replace(",", "").replace(" ", "").trim()
        if (cleaned.isEmpty()) return null
        if (!Regex("^\\d*(\\.\\d{0,2})?$").matches(cleaned)) return null
        if (cleaned == ".") return null

        val dot = cleaned.indexOf('.')
        val wholePart = if (dot >= 0) cleaned.substring(0, dot) else cleaned
        val fractionPart = if (dot >= 0) cleaned.substring(dot + 1) else ""

        if (wholePart.length > 13) return null // keeps the result well inside Long range
        val whole = if (wholePart.isEmpty()) 0L else wholePart.toLong()
        val fraction = fractionPart.padEnd(2, '0').toLong()
        return whole * 100 + fraction
    }

    /** Plain text for an amount box when editing: 125050 -> "1250.50", 150000 -> "1500". */
    fun formatPlain(paise: Long): String {
        val rupees = paise / 100
        val fraction = (paise % 100).toInt()
        return if (fraction == 0) rupees.toString() else rupees.toString() + "." + fraction.toString().padStart(2, '0')
    }
}
