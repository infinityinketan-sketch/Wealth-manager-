package com.example.ledger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: LedgerTransaction): Long

    /** Returns -1 when a transaction with the same smsKey already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: LedgerTransaction): Long

    /** SMS entries the app was unsure about (no category or a catch-all one); re-guessed when it learns. */
    @Query("SELECT * FROM transactions WHERE smsKey IS NOT NULL AND merchant IS NOT NULL AND (categoryId IS NULL OR categoryId IN (:ids))")
    suspend fun uncertain(ids: List<Long>): List<LedgerTransaction>

    @Query("SELECT COUNT(*) FROM transactions WHERE smsKey IS NOT NULL")
    suspend fun countSms(): Int

    @Query("DELETE FROM transactions WHERE smsKey IS NOT NULL")
    suspend fun deleteSms()

    /** Same account, amount and direction within a few minutes: the bank sent the same alert twice. */
    @Query(
        "SELECT COUNT(*) FROM transactions WHERE accountId = :accountId AND amountPaise = :amount AND kind = :kind " +
            "AND smsKey IS NOT NULL AND smsKey != :smsKey AND epochMillis BETWEEN :fromMillis AND :toMillis"
    )
    suspend fun countNear(accountId: Long, amount: Long, kind: EntryKind, smsKey: String, fromMillis: Long, toMillis: Long): Int

    /** A bank or card debit of the same amount near this date (the EMI already counted from the bank side). */
    @Query(
        "SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id = t.accountId WHERE t.kind = 'EXPENSE' " +
            "AND t.amountPaise = :amount AND t.epochMillis BETWEEN :fromMillis AND :toMillis AND a.type != 'LOAN'"
    )
    suspend fun countNonLoanExpense(amount: Long, fromMillis: Long, toMillis: Long): Int

    /** Removes a lender's "EMI received" entry once the matching bank debit is known. */
    @Query(
        "DELETE FROM transactions WHERE kind = 'EXPENSE' AND amountPaise = :amount " +
            "AND epochMillis BETWEEN :fromMillis AND :toMillis AND accountId IN (SELECT id FROM accounts WHERE type = 'LOAN')"
    )
    suspend fun deleteLoanExpense(amount: Long, fromMillis: Long, toMillis: Long)

    @Query("UPDATE transactions SET accountId = :toId WHERE accountId = :fromId")
    suspend fun moveAccount(fromId: Long, toId: Long)

    @Query("UPDATE transactions SET accountId = :cashId WHERE accountId IN (SELECT id FROM accounts WHERE smsKey IS NOT NULL)")
    suspend fun moveSmsAccountsTo(cashId: Long)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE merchantKey = :merchantKey AND kind = :kind")
    suspend fun setCategoryForMerchant(merchantKey: String, kind: EntryKind, categoryId: Long)

    @Update
    suspend fun update(transaction: LedgerTransaction)

    @Delete
    suspend fun delete(transaction: LedgerTransaction)

    @Query("SELECT * FROM transactions WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay DESC, id DESC")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<LedgerTransaction>>

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)
}

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY id")
    fun observeActive(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE smsKey = :smsKey LIMIT 1")
    suspend fun findBySmsKey(smsKey: String): Account?

    @Query("SELECT * FROM accounts WHERE smsKey LIKE :prefix || '%'")
    suspend fun findBySmsKeyPrefix(prefix: String): List<Account>

    @Query("SELECT * FROM accounts WHERE smsKey IS NOT NULL")
    suspend fun smsAccounts(): List<Account>

    @Query("SELECT id FROM accounts WHERE type = 'CASH' ORDER BY id LIMIT 1")
    suspend fun cashAccountId(): Long?

    @Query("DELETE FROM accounts WHERE smsKey IS NOT NULL")
    suspend fun deleteSmsAccounts()

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE accounts SET latestBalancePaise = :balance, latestBalanceMillis = :millis WHERE id = :id AND latestBalanceMillis <= :millis")
    suspend fun updateBalanceIfNewer(id: Long, balance: Long, millis: Long)

    @Query(
        "SELECT a.id AS accountId, a.name AS name, a.type AS type, " +
            "CASE WHEN a.latestBalancePaise IS NOT NULL THEN a.latestBalancePaise ELSE " +
            "a.openingBalancePaise + COALESCE(SUM(CASE WHEN t.kind = 'INCOME' THEN t.amountPaise " +
            "WHEN t.kind = 'EXPENSE' THEN -t.amountPaise ELSE 0 END), 0) END AS balancePaise, " +
            "a.smsKey AS smsKey, a.latestBalancePaise AS latestBalancePaise, a.latestBalanceMillis AS latestBalanceMillis " +
            "FROM accounts a LEFT JOIN transactions t ON t.accountId = a.id " +
            "WHERE a.archived = 0 GROUP BY a.id ORDER BY a.id"
    )
    fun observeBalances(): Flow<List<AccountBalance>>
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories")
    suspend fun all(): List<Category>

    @Query("SELECT * FROM categories ORDER BY kind, name")
    fun observeAll(): Flow<List<Category>>
}

@Dao
interface MerchantRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRule)

    @Query("SELECT * FROM merchant_rules")
    suspend fun all(): List<MerchantRule>

    @Query("SELECT merchantKey FROM merchant_rules")
    fun observeKeys(): Flow<List<String>>

    @Query(
        "SELECT DISTINCT t.merchant AS merchant, r.categoryId AS categoryId, r.kind AS kind FROM merchant_rules r " +
            "JOIN transactions t ON t.merchantKey = r.merchantKey WHERE t.merchant IS NOT NULL"
    )
    suspend fun examples(): List<MerchantExample>
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: Budget)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: Long)

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<Budget>>
}

@Dao
interface RecurringDao {
    @Insert
    suspend fun insert(rule: RecurringRule): Long

    @Update
    suspend fun update(rule: RecurringRule)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM recurring_rules ORDER BY id")
    fun observeAll(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules WHERE active = 1")
    suspend fun activeRules(): List<RecurringRule>
}
