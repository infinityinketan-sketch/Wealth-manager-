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
import com.example.ledger.data.Due
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Phone notifications for bills and reminders that are due soon. Nothing leaves the phone. */
object DueNotifier {
    private const val CHANNEL = "dues"

    fun notifySoon(context: Context, dues: List<Due>, today: LocalDate = LocalDate.now()) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Bills and reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        for (d in dues) {
            val days = ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(d.dueEpochDay))
            if (days !in 0..2) continue
            val whenText = when (days) { 0L -> "due today"; 1L -> "due tomorrow"; else -> "due in $days days" }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(d.title + " " + whenText)
                .setContentText(Money.formatInr(d.amountPaise) + (d.minDuePaise?.let { " (minimum " + Money.formatInr(it) + ")" } ?: ""))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(d.id.toInt(), n)
            } catch (e: SecurityException) {
                return
            }
        }
    }
}
