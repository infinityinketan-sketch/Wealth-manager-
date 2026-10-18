package com.example.ledger

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ledger.data.CrashLog
import com.example.ledger.data.LedgerDatabase
import com.example.ledger.data.LedgerRepository
import com.example.ledger.data.Settings
import com.example.ledger.work.RecurringWorker
import java.util.concurrent.TimeUnit

class LedgerApp : Application() {
    lateinit var repository: LedgerRepository
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        // Remember why the app closed, so it can be shown (and copied) on the next start.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try { CrashLog.save(this, e, fatal = true) } catch (ignored: Throwable) { }
            previous?.uncaughtException(thread, e)
        }
        repository = LedgerRepository(LedgerDatabase.build(this))
        settings = Settings(this)

        // Once a day, turn any recurring entries that have come due into real entries.
        val request = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("recurring_entries", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
