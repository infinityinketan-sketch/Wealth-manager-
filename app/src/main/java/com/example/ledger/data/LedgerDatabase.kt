package com.example.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ledger.sms.StarterCategories

@Database(
    entities = [
        Account::class,
        Category::class,
        LedgerTransaction::class,
        Budget::class,
        RecurringRule::class,
        MerchantRule::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "ledger.db")
                .addCallback(SeedCallback)
                // Test builds only: moving from the empty stage-1 database simply starts fresh.
                .fallbackToDestructiveMigration()
                .build()
    }

    /** First-run data: a Cash account and the starter categories the person can edit. */
    private object SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO accounts (name, type, openingBalancePaise, archived, latestBalanceMillis) VALUES ('Cash', 'CASH', 0, 0, 0)")
            StarterCategories.all.forEach { c ->
                val kind = if (c.isIncome) "INCOME" else "EXPENSE"
                db.execSQL("INSERT INTO categories (name, kind, colorArgb) VALUES ('${c.name.replace("'", "''")}', '$kind', ${c.color})")
            }
        }
    }
}
