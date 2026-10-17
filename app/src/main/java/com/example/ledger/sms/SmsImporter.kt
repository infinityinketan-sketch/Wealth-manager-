package com.example.ledger.sms

import android.content.Context
import android.provider.Telephony
import com.example.ledger.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the phone's SMS inbox once, on the phone, and turns bank messages into transactions. */
object SmsImporter {

    private const val DAY = 24L * 60 * 60 * 1000

    /** A bank-looking message that was not turned into a transaction or a bill, and why. */
    data class Unread(val sender: String, val body: String, val millis: Long, val reason: String)

    /**
     * [scanned] messages read, [found] bank transactions in them (new or already stored), [imported]
     * new ones, [unread] bank messages that were not understood (newest first, at most 300).
     */
    data class Result(val scanned: Int, val found: Int, val imported: Int, val unread: List<Unread>)

    fun smsKey(sender: String, millis: Long, body: String) = sender + "|" + millis + "|" + body.hashCode()

    /** Finds the original message text for a transaction read from SMS (shown in the edit screen). */
    fun findBody(context: Context, smsKey: String): String? {
        val parts = smsKey.split("|")
        if (parts.size < 3) return null
        val sender = parts.dropLast(2).joinToString("|")
        val millis = parts[parts.size - 2].toLongOrNull() ?: return null
        val hash = parts.last()
        return try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY),
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} = ?",
                arrayOf(sender, millis.toString()),
                null
            )?.use { c ->
                var found: String? = null
                while (found == null && c.moveToNext()) {
                    val body = c.getString(0) ?: continue
                    if (body.hashCode().toString() == hash) found = body
                }
                found
            }
        } catch (e: SecurityException) {
            null
        }
    }

    suspend fun importInbox(
        context: Context,
        repo: LedgerRepository,
        lastScanMillis: Long,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            // The first scan reads the whole inbox, however old (like Walnut does).
            val since = if (lastScanMillis <= 0L) 0L else lastScanMillis - 2 * DAY
            var found = 0
            val unread = ArrayDeque<Unread>()
            val ctx = repo.newImportContext()
            var scanned = 0
            var imported = 0
            try {
                context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    "${Telephony.Sms.DATE} >= ?",
                    arrayOf(since.toString()),
                    "${Telephony.Sms.DATE} ASC"
                )?.use { cursor ->
                    val iAddress = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val iBody = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val iDate = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    val total = cursor.count
                    val zone = java.time.ZoneId.systemDefault()
                    while (cursor.moveToNext()) {
                        scanned++
                        if (scanned % 100 == 0 || scanned == total) onProgress(scanned, total)
                        val sender = cursor.getString(iAddress) ?: continue
                        val body = cursor.getString(iBody) ?: continue
                        val millis = cursor.getLong(iDate)
                        val smsDate = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        val due = DueParser.parse(sender, body, smsDate)
                        if (due != null) repo.recordDue(due, millis)
                        val (parsed, why) = SmsParser.parseWhy(sender, body)
                        if (parsed == null) {
                            val bankSender = SmsParser.bankFromSender(sender) != null || SmsParser.lenderFromSender(sender) != null
                            val interesting = due == null && why != null && when (why) {
                                SmsParser.Skip.OTP, SmsParser.Skip.PROMO -> false
                                SmsParser.Skip.NOT_BANK -> SmsParser.looksLikeMoneyAlert(body)
                                else -> bankSender || SmsParser.looksLikeMoneyAlert(body)
                            }
                            if (interesting) {
                                unread.addFirst(Unread(sender, body, millis, why!!.label))
                                if (unread.size > 300) unread.removeLast()
                            }
                            continue
                        }
                        found++
                        if (repo.recordSms(ctx, parsed, smsKey(sender, millis, body), millis, body)) imported++
                    }
                }
            } catch (e: SecurityException) {
                // Permission was withdrawn; nothing to import.
            }
            Result(scanned, found, imported, unread.toList())
        }
}
