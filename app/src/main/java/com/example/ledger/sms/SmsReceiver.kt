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
                if (parsed != null) {
                    repo.recordSms(repo.newImportContext(), parsed, SmsImporter.smsKey(sender, millis, body), millis, body)
                }
                repo.afterImport()
            } finally {
                pending.finish()
            }
        }
    }
}
