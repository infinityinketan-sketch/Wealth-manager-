package com.example.ledger.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.ledger.LedgerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Picks up each new bank SMS as it arrives, so the app stays up to date without being opened. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        val sender = messages[0].originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val millis = messages[0].timestampMillis
        val repo = (context.applicationContext as LedgerApp).repository

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val smsDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                DueParser.parse(sender, body, smsDate)?.let { repo.recordDue(it, millis) }
                val parsed = SmsParser.parse(sender, body)
                var newKey: String? = null
                if (parsed != null) {
                    val key = SmsImporter.smsKey(sender, millis, body)
                    if (repo.recordSms(repo.newImportContext(), parsed, key, millis, body)) newKey = key
                }
                repo.afterImport()
                // Politely ask what it was for (after self-transfer matching, so own transfers are not asked).
                val app = context.applicationContext as LedgerApp
                newKey?.let { k -> repo.transactionBySmsKey(k) }?.let { t ->
                    com.example.ledger.work.CategoryPrompt.maybeAsk(context, repo, app.settings, t)
                }
            } catch (e: Throwable) {
                com.example.ledger.data.CrashLog.save(context, e, fatal = false)
            } finally {
                pending.finish()
            }
        }
    }
}
