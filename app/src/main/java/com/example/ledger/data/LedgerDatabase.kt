package com.example.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Account::class,
        Category::class,
        LedgerTransaction::class,
        Budget::class,
        RecurringRule::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao

    companion object {
        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "ledger.db")
                .addCallback(SeedCallback)
                .build()
    }

    /** First-run data: a Cash account and a starter set of categories the user can edit. */
    private object SeedCallback : RoomDatabase.Callback() {
        private val expense = listOf(
            "Food & dining" to 0xFFE57373,
            "Groceries" to 0xFF81C784,
            "Transport" to 0xFF64B5F6,
            "Rent" to 0xFFBA68C8,
            "EMI" to 0xFFFFB74D,
            "Electricity & bills" to 0xFFFFD54F,
            "Clothes" to 0xFFF06292,
            "Online shopping" to 0xFF4DB6AC,
            "Health" to 0xFF7986CB,
            "Entertainment" to 0xFFA1887F,
            "Other" to 0xFF90A4AE
        )
        private val income = listOf(
            "Salary" to 0xFF388E3C,
            "Refund" to 0xFF0097A7,
            "Interest" to 0xFF5C6BC0,
            "Other income" to 0xFF78909C
        )

        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO accounts (name, type, openingBalancePaise, archived) VALUES ('Cash', 'CASH', 0, 0)")
            expense.forEach { (name, color) ->
                db.execSQL("INSERT INTO categories (name, kind, colorArgb) VALUES ('${name.replace("'", "''")}', 'EXPENSE', $color)")
            }
            income.forEach { (name, color) ->
                db.execSQL("INSERT INTO categories (name, kind, colorArgb) VALUES ('${name.replace("'", "''")}', 'INCOME', $color)")
            }
        }
    }
}
