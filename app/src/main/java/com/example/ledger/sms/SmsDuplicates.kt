package com.example.ledger.sms

/**
 * Tells a bank sending the same alert twice apart from two real payments of the same amount.
 * SMS keys look like "VM-HDFCBK-S|1697012345000|-123456789" (sender | time | text hash).
 */
object SmsDuplicates {
    private fun senderCore(sender: String) =
        sender.uppercase().replace(Regex("^[A-Z0-9]{2}-"), "").replace(Regex("-[A-Z]$"), "")

    fun isRepeat(existingKey: String, newKey: String): Boolean {
        val a = existingKey.split("|")
        val b = newKey.split("|")
        if (a.size < 3 || b.size < 3) return false
        val sameText = a.last() == b.last()
        val otherSenderId = senderCore(a.dropLast(2).joinToString("|")) != senderCore(b.dropLast(2).joinToString("|"))
        return sameText || otherSenderId
    }
}
