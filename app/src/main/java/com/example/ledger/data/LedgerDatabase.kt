package com.example.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ledger.sms.StarterCategories

@Database(
    entities = [
        Account::class,
        Category::class,
        LedgerTransaction::class,
        Budget::class,
        RecurringRule::class,
        MerchantRule::class,
        Due::class,
        EntryOverride::class
    ],
    version = 4,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun dueDao(): DueDao
    abstract fun overrideDao(): OverrideDao

    companion object {
        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "ledger.db")
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                // Test builds only: moving from the empty stage-1 database simply starts fresh.
                .fallbackToDestructiveMigration()
                .build()
    }

    /** Version 3 adds the dues table; everything already stored (and learned) is kept. */
    private object MIGRATION_2_3 : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dues` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dueKey` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `digits` TEXT, `kind` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, " +
                    "`minDuePaise` INTEGER, `dueEpochDay` INTEGER NOT NULL, `status` TEXT NOT NULL, `merchantKey` TEXT, " +
                    "`repeatMonths` INTEGER, `createdMillis` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dues_dueKey` ON `dues` (`dueKey`)")
        }
    }

    /** Version 4 keeps the person's own fixes to single SMS entries. */
    private object MIGRATION_3_4 : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `overrides` (`smsKey` TEXT NOT NULL, `categoryId` INTEGER, " +
                    "`note` TEXT NOT NULL, PRIMARY KEY(`smsKey`))"
            )
        }
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
