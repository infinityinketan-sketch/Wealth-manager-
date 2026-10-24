package com.example.ledger.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ledger.LedgerApp
import java.time.LocalDate

class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LedgerApp
        return try {
            app.repository.generateDueRecurring(LocalDate.now())
            app.repository.checkDuesPaid()
            if (app.settings.isPro) DueNotifier.notifySoon(applicationContext, app.repository.openDues())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
