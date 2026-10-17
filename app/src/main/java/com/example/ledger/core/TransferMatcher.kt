package com.example.ledger.core

/** The few facts needed to spot money moving between the person's own accounts. */
data class MoneyMove(val id: Long, val accountId: Long, val isIncome: Boolean, val amountPaise: Long, val millis: Long)

/**
 * Pairs a debit from one of the person's accounts with a credit of the same amount into another of
 * their accounts, close in time. Such pairs are self-transfers: not spending and not income.
 * Quick pairs (within 30 minutes) count from ₹500; slower ones (bank transfers that land the next
 * day) only from ₹5,000, to avoid pairing two unrelated small payments.
 */
object TransferMatcher {
    private const val MINUTE = 60_000L
    private const val QUICK_WINDOW = 30 * MINUTE
    private const val SLOW_WINDOW = 2 * 24 * 60 * MINUTE
    private const val QUICK_MIN = 500_00L
    private const val SLOW_MIN = 5_000_00L

    /** Returns (debit id, credit id) pairs; each entry is used at most once. */
    fun pairs(moves: List<MoneyMove>): List<Pair<Long, Long>> {
        val credits = moves.filter { it.isIncome }.sortedBy { it.millis }.toMutableList()
        val out = ArrayList<Pair<Long, Long>>()
        for (d in moves.filter { !it.isIncome }.sortedBy { it.millis }) {
            if (d.amountPaise < QUICK_MIN) continue
            val match = credits
                .filter { c -> c.accountId != d.accountId && c.amountPaise == d.amountPaise }
                .map { c -> c to Math.abs(c.millis - d.millis) }
                .filter { (_, gap) -> gap <= QUICK_WINDOW || (gap <= SLOW_WINDOW && d.amountPaise >= SLOW_MIN) }
                .minByOrNull { it.second }
                ?.first ?: continue
            credits.remove(match)
            out.add(d.id to match.id)
        }
        return out
    }
}
