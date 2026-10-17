package com.example.ledger.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A payment the person makes regularly, e.g. "Mr Ashok Balu L, ₹1,740 every quarter". */
data class RepeatingPayment(
    val merchantKey: String,
    val name: String,
    val amountPaise: Long,
    val intervalDays: Int,
    val months: Int,
    val label: String,
    val lastDate: LocalDate,
    val nextDate: LocalDate
)

data class PaymentPoint(val merchantKey: String, val name: String, val amountPaise: Long, val date: LocalDate)

object RecurringDetector {

    private data class Cadence(val days: Int, val min: Int, val max: Int, val label: String, val months: Long)

    private val cadences = listOf(
        Cadence(30, 25, 35, "Repeats monthly", 1),
        Cadence(91, 80, 100, "Repeats quarterly", 3),
        Cadence(182, 170, 195, "Repeats every 6 months", 6),
        Cadence(365, 350, 380, "Repeats yearly", 12)
    )

    /**
     * Finds merchants paid at a steady rhythm (monthly / quarterly / half-yearly / yearly) and a
     * steady amount (within 15%), at least three times, and still active.
     */
    fun find(points: List<PaymentPoint>, today: LocalDate): List<RepeatingPayment> {
        val out = ArrayList<RepeatingPayment>()
        for ((key, list) in points.groupBy { it.merchantKey }) {
            // Several payments to the same merchant on one day count once (the biggest).
            val byDay = list.groupBy { it.date }.map { (_, same) -> same.maxBy { it.amountPaise } }.sortedBy { it.date }
            if (byDay.size < 3) continue
            val gaps = byDay.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
            val cadence = cadences.firstOrNull { c -> gaps.count { it in c.min..c.max } * 3 >= gaps.size * 2 && gaps.last() in c.min..c.max }
                ?: continue
            val amounts = byDay.map { it.amountPaise }.sorted()
            val median = amounts[amounts.size / 2]
            val steady = amounts.count { Math.abs(it - median) * 100 <= median * 15 }
            if (steady * 3 < amounts.size * 2) continue
            val last = byDay.last()
            val next = last.date.plusMonths(cadence.months)
            // Stopped long ago: not worth a reminder.
            if (next.isBefore(today.minusDays((cadence.days / 2).toLong()))) continue
            out.add(RepeatingPayment(key, last.name, median, cadence.days, cadence.months.toInt(), cadence.label, last.date, next))
        }
        return out.sortedBy { it.nextDate }
    }
}
