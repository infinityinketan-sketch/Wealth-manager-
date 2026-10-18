package com.example.ledger.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ledger.LedgerApp
import com.example.ledger.work.CategoryPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A category button on the "what was this for?" notification was tapped. */
class CategoryActionReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_TX = "tx"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_NOTIFICATION = "notification"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val txId = intent.getLongExtra(EXTRA_TX, -1)
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY, -1)
        val notification = intent.getIntExtra(EXTRA_NOTIFICATION, -1)
        if (notification >= 0) CategoryPrompt.cancel(context, notification)
        if (txId < 0 || categoryId < 0) return
        val repo = (context.applicationContext as LedgerApp).repository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repo.answerPrompt(txId, categoryId, rememberShop = true)
            } catch (e: Throwable) {
                com.example.ledger.data.CrashLog.save(context, e, fatal = false)
            } finally {
                pending.finish()
            }
        }
    }
}
