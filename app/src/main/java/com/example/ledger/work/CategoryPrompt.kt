package com.example.ledger.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.ledger.MainActivity
import com.example.ledger.core.Money
import com.example.ledger.data.EntryKind
import com.example.ledger.data.LedgerRepository
import com.example.ledger.data.LedgerTransaction
import com.example.ledger.data.Settings
import com.example.ledger.sms.CategoryActionReceiver
import com.example.ledger.sms.StarterCategories

/**
 * After a new payment or credit is read from SMS, a polite notification asks what it was for, with
 * one-tap category buttons. Shops the person already decided about, and own-money movements
 * (self transfers, FD, card bills), are not asked about.
 */
object CategoryPrompt {
    private const val CHANNEL = "ask_category"
    const val EXTRA_TX = "categorize_tx"

    private fun notificationId(txId: Long) = 100_000 + (txId % 1_000_000).toInt()

    suspend fun maybeAsk(context: Context, repo: LedgerRepository, settings: Settings, t: LedgerTransaction) {
        if (!settings.askCategory || !settings.isPro) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val key = t.merchantKey
        if (key != null && repo.hasRule(key, t.kind)) return
        val cats = repo.allCategories()
        val current = cats.firstOrNull { it.id == t.categoryId }
        if (current != null && current.name in StarterCategories.excludedNames) return

        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "What was this payment for?", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val who = t.merchant?.lowercase()?.split(" ")?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        val isIncome = t.kind == EntryKind.INCOME
        val title = Money.formatInr(t.amountPaise) + (if (isIncome) " received" else " paid") +
            (who?.let { (if (isIncome) " from " else " to ") + it } ?: "")
        val text = if (who != null) "What was this for? Tap a category, and I'll remember it for $who next time."
        else "What was this for? Tap a category, or tap here for more choices."

        val open = PendingIntent.getActivity(
            context, notificationId(t.id),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_TX, t.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        repo.quickCategories(t, 3).forEachIndexed { i, c ->
            val action = Intent(context, CategoryActionReceiver::class.java)
                .putExtra(CategoryActionReceiver.EXTRA_TX, t.id)
                .putExtra(CategoryActionReceiver.EXTRA_CATEGORY, c.id)
                .putExtra(CategoryActionReceiver.EXTRA_NOTIFICATION, notificationId(t.id))
            val pi = PendingIntent.getBroadcast(
                context, notificationId(t.id) * 4 + i, action,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, c.name, pi)
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId(t.id), builder.build())
        } catch (e: SecurityException) {
            // Notifications turned off: nothing to do.
        }
    }

    fun cancel(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun notificationFor(txId: Long) = notificationId(txId)
}
