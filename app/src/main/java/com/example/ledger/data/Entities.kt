package com.example.ledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ledger.core.Frequency

enum class AccountType(val label: String) {
    CASH("Cash"),
    BANK("Bank account"),
    UPI("UPI"),
    CARD("Credit card"),
    LOAN("Loan")
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
    val archived: Boolean = false,
    /** Set for accounts found from SMS, e.g. "HDFC Bank|1234"; used to match later messages. */
    val smsKey: String? = null,
    /** Latest "available balance" reported by a bank SMS; when set it is shown instead of a computed balance. */
    val latestBalancePaise: Long? = null,
    val latestBalanceMillis: Long = 0
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
    indices = [Index("epochDay"), Index("accountId"), Index("categoryId"), Index(value = ["smsKey"], unique = true)]
)
data class LedgerTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long?,
    val kind: EntryKind,
    val amountPaise: Long,
    val note: String,
    val epochDay: Long,
    val recurringRuleId: Long? = null,
    /** Who was paid / who paid (from SMS or typed); drives "remember this merchant". */
    val merchant: String? = null,
    val merchantKey: String? = null,
    /** Unique id of the SMS this came from (stops the same message being imported twice). Null for manual entries. */
    val smsKey: String? = null,
    val epochMillis: Long = 0
)

/** "Every time this merchant appears, use this category" — learned from the person's corrections. */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val categoryId: Long,
    val kind: EntryKind
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
    val balancePaise: Long,
    /** Non-null for accounts the app found in SMS. */
    val smsKey: String?,
    /** The balance a bank SMS reported, if any (null = the bank never said). */
    val latestBalancePaise: Long?,
    val latestBalanceMillis: Long
)
