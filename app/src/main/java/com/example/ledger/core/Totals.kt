package com.example.ledger.core

/** One entry as the totals see it. [counted] = its category counts in income / spending. */
data class TotalsItem(val isIncome: Boolean, val amountPaise: Long, val categoryName: String?, val counted: Boolean)

/**
 * The summary numbers, worked out one way everywhere:
 *  - income: money earned (salary, interest, money from people…). Refunds are not income.
 *  - spent: spending minus refunds (a refund gives back money already counted as spent).
 *  - saved: income − spent. Part of it may have gone into investments / FD ([invested]).
 *  - not counted: investments / FD, card bills, own transfers going out ([movedOut]) and money
 *    coming back from investments or own accounts ([movedIn]).
 */
data class Totals(
    val income: Long,
    val spentGross: Long,
    val refunds: Long,
    val spent: Long,
    val saved: Long,
    val invested: Long,
    val movedOut: Long,
    val movedIn: Long
)

object TotalsCalc {
    const val REFUND = "Refund"
    const val INVESTMENTS = "Investments & FD"

    fun of(items: List<TotalsItem>): Totals {
        var income = 0L
        var spentGross = 0L
        var refunds = 0L
        var invested = 0L
        var movedOut = 0L
        var movedIn = 0L
        for (t in items) {
            when {
                t.counted && t.isIncome && t.categoryName == REFUND -> refunds += t.amountPaise
                t.counted && t.isIncome -> income += t.amountPaise
                t.counted -> spentGross += t.amountPaise
                t.isIncome -> movedIn += t.amountPaise
                t.categoryName == INVESTMENTS -> { invested += t.amountPaise; movedOut += t.amountPaise }
                else -> movedOut += t.amountPaise
            }
        }
        val spent = (spentGross - refunds).coerceAtLeast(0)
        return Totals(income, spentGross, refunds, spent, income - spent, invested, movedOut, movedIn)
    }
}
