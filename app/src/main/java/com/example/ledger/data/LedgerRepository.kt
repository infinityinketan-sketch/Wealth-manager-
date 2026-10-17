package com.example.ledger.data

import androidx.room.withTransaction
import com.example.ledger.core.Recurrence
import com.example.ledger.sms.CategoryGuesser
import com.example.ledger.sms.ParsedSms
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class LedgerRepository(private val db: LedgerDatabase) {

    private val transactions = db.transactionDao()
    private val accounts = db.accountDao()
    private val categories = db.categoryDao()
    private val budgets = db.budgetDao()
    private val recurring = db.recurringDao()
    private val merchantRules = db.merchantRuleDao()

    // ---- reads ----
    fun observeTransactions(fromDay: Long, toDay: Long): Flow<List<LedgerTransaction>> =
        transactions.observeBetween(fromDay, toDay)

    fun observeAccounts(): Flow<List<Account>> = accounts.observeActive()
    fun observeBalances(): Flow<List<AccountBalance>> = accounts.observeBalances()
    fun observeCategories(): Flow<List<Category>> = categories.observeAll()
    fun observeBudgets(): Flow<List<Budget>> = budgets.observeAll()
    fun observeRecurring(): Flow<List<RecurringRule>> = recurring.observeAll()

    // ---- transactions ----
    suspend fun saveTransaction(transaction: LedgerTransaction) {
        if (transaction.id == 0L) transactions.insert(transaction) else transactions.update(transaction)
    }

    suspend fun deleteTransaction(transaction: LedgerTransaction) = transactions.delete(transaction)

    /**
     * Adds an entry that repeats. The rule is stored first and the entry itself is created by
     * [generateDueRecurring], so the first occurrence and every later one come from one code path.
     */
    suspend fun addRecurring(rule: RecurringRule, today: LocalDate) {
        recurring.insert(rule)
        generateDueRecurring(today)
    }

    suspend fun setRecurringActive(rule: RecurringRule, active: Boolean) = recurring.update(rule.copy(active = active))

    /** Deletes the rule only; entries it already created stay in the ledger. */
    suspend fun deleteRecurring(ruleId: Long) = recurring.delete(ruleId)

    /** Turns every due occurrence of every active rule into an entry. Safe to call repeatedly. */
    suspend fun generateDueRecurring(today: LocalDate): Int {
        var created = 0
        for (rule in recurring.activeRules()) {
            val last = rule.lastGeneratedEpochDay?.let { LocalDate.ofEpochDay(it) }
            val dueDates = Recurrence.dueDates(
                start = LocalDate.ofEpochDay(rule.startEpochDay),
                frequency = rule.frequency,
                lastGenerated = last,
                today = today
            )
            if (dueDates.isEmpty()) continue
            db.withTransaction {
                for (date in dueDates) {
                    transactions.insert(
                        LedgerTransaction(
                            accountId = rule.accountId,
                            categoryId = rule.categoryId,
                            kind = rule.kind,
                            amountPaise = rule.amountPaise,
                            note = rule.note,
                            epochDay = date.toEpochDay(),
                            recurringRuleId = rule.id
                        )
                    )
                }
                recurring.update(rule.copy(lastGeneratedEpochDay = dueDates.last().toEpochDay()))
            }
            created += dueDates.size
        }
        return created
    }

    // ---- SMS import ----

    /** In-memory lookups used while importing many messages, so each message needs no extra queries. */
    class ImportContext internal constructor(
        internal val accountIds: MutableMap<String, Long>,
        internal val categoryByName: Map<Pair<String, EntryKind>, Long>,
        internal val rules: Map<String, Long>
    )

    suspend fun newImportContext(): ImportContext {
        val cats = categories.all().associate { (it.name to it.kind) to it.id }
        val rules = merchantRules.all().associate { it.merchantKey to it.categoryId }
        return ImportContext(mutableMapOf(), cats, rules)
    }

    /**
     * Stores one parsed bank SMS as a transaction. Creates the bank account on first sight, picks a
     * category (the person's own rule first, then keywords), and keeps the latest SMS balance.
     * Returns true if it was new, false if this exact SMS was imported before.
     */
    suspend fun recordSms(
        ctx: ImportContext,
        parsed: ParsedSms,
        smsKey: String,
        millis: Long,
        bodyForGuess: String,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): Boolean {
        val accountKey = parsed.bankName + "|" + (parsed.last4 ?: "")
        val accountId = ctx.accountIds[accountKey] ?: run {
            val found = accounts.findBySmsKey(accountKey)
            val id = found?.id ?: accounts.insert(
                Account(
                    name = parsed.bankName + (parsed.last4?.let { " ••$it" } ?: ""),
                    type = if (parsed.isCreditCard) AccountType.CARD else AccountType.BANK,
                    smsKey = accountKey
                )
            )
            ctx.accountIds[accountKey] = id
            id
        }

        val kind = if (parsed.isIncome) EntryKind.INCOME else EntryKind.EXPENSE
        val merchantKey = CategoryGuesser.merchantKey(parsed.merchant)
        val categoryId = merchantKey?.let { ctx.rules[it] }
            ?: CategoryGuesser.guess(parsed.isIncome, parsed.merchant, bodyForGuess)?.let { ctx.categoryByName[it to kind] }
            ?: ctx.categoryByName[(if (parsed.isIncome) "Other income" else "Other") to kind]

        val day = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
        val rowId = transactions.insertIgnore(
            LedgerTransaction(
                accountId = accountId,
                categoryId = categoryId,
                kind = kind,
                amountPaise = parsed.amountPaise,
                note = "",
                epochDay = day,
                merchant = parsed.merchant,
                merchantKey = merchantKey,
                smsKey = smsKey,
                epochMillis = millis
            )
        )
        if (parsed.balancePaise != null) accounts.updateBalanceIfNewer(accountId, parsed.balancePaise, millis)
        return rowId != -1L
    }

    /**
     * The person fixed a category on a merchant's transaction. With [applyToAll] every transaction
     * from that merchant gets the category now, and future ones follow the same rule.
     */
    suspend fun learnMerchant(merchantKey: String, kind: EntryKind, categoryId: Long) {
        db.withTransaction {
            merchantRules.upsert(MerchantRule(merchantKey, categoryId, kind))
            transactions.setCategoryForMerchant(merchantKey, kind, categoryId)
        }
    }

    // ---- accounts ----
    suspend fun addAccount(name: String, type: AccountType, openingBalancePaise: Long) {
        accounts.insert(Account(name = name.trim(), type = type, openingBalancePaise = openingBalancePaise))
    }

    // ---- categories ----
    suspend fun addCategory(name: String, kind: EntryKind, colorArgb: Long) {
        categories.insert(Category(name = name.trim(), kind = kind, colorArgb = colorArgb))
    }

    suspend fun renameCategory(category: Category, newName: String) {
        categories.update(category.copy(name = newName.trim()))
    }

    /** Entries in the deleted category become "Uncategorised"; nothing is lost. */
    suspend fun deleteCategory(category: Category) {
        db.withTransaction {
            transactions.clearCategory(category.id)
            budgets.delete(category.id)
            categories.delete(category)
        }
    }

    // ---- budgets ----
    suspend fun setBudget(categoryId: Long, limitPaise: Long) {
        if (limitPaise <= 0) budgets.delete(categoryId) else budgets.upsert(Budget(categoryId, limitPaise))
    }
}
