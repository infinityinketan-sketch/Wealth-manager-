package com.example.ledger.sms

import android.content.Context
import android.provider.Telephony
import com.example.ledger.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the phone's SMS inbox once, on the phone, and turns bank messages into transactions. */
object SmsImporter {

    private const val DAY = 24L * 60 * 60 * 1000

    /** How far back the very first scan looks. */
    const val FIRST_SCAN_DAYS = 365L

    data class Result(val scanned: Int, val imported: Int)

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

    suspend fun importInbox(context: Context, repo: LedgerRepository, lastScanMillis: Long): Result =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val since = if (lastScanMillis <= 0L) now - FIRST_SCAN_DAYS * DAY else lastScanMillis - 2 * DAY
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
                    while (cursor.moveToNext()) {
                        scanned++
                        val sender = cursor.getString(iAddress) ?: continue
                        val body = cursor.getString(iBody) ?: continue
                        val millis = cursor.getLong(iDate)
                        val parsed = SmsParser.parse(sender, body) ?: continue
                        if (repo.recordSms(ctx, parsed, smsKey(sender, millis, body), millis, body)) imported++
                    }
                }
            } catch (e: SecurityException) {
                // Permission was withdrawn; nothing to import.
            }
            Result(scanned, imported)
        }
}
