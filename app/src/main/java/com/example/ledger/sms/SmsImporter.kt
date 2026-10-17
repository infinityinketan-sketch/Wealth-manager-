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
