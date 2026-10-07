package com.example.ledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ledger.core.Frequency

enum class AccountType(val label: String) {
    CASH("Cash"),
    BANK("Bank account"),
    UPI("UPI"),
    CARD("Card")
}

enum class EntryKind(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income")
}

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalancePaise: Long = 0,
    val archived: Boolean = false
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: EntryKind,
    val colorArgb: Long
)

/** One income or expense entry. [epochDay] is the date as days since 1970-01-01. */
@Entity(
    tableName = "transactions",
    indices = [Index("epochDay"), Index("accountId"), Index("categoryId")]
)
data class LedgerTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long?,
    val kind: EntryKind,
    val amountPaise: Long,
    val note: String,
    val epochDay: Long,
    val recurringRuleId: Long? = null
)

/** Monthly limit for one expense category. [categoryId] == [OVERALL_ID] is the overall monthly budget. */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val categoryId: Long,
    val limitPaise: Long
) {
    companion object {
        const val OVERALL_ID = 0L
    }
}

@Entity(tableName = "recurring_rules")
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long?,
    val kind: EntryKind,
    val amountPaise: Long,
    val note: String,
    val frequency: Frequency,
    val startEpochDay: Long,
    val lastGeneratedEpochDay: Long? = null,
    val active: Boolean = true
)

/** Result row for the per-account balance query. */
data class AccountBalance(
    val accountId: Long,
    val name: String,
    val type: AccountType,
    val balancePaise: Long
)
