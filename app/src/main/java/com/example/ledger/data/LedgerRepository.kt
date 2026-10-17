package com.example.ledger.data

import androidx.room.withTransaction
import com.example.ledger.core.Recurrence
import com.example.ledger.sms.CategoryGuesser
import com.example.ledger.sms.ParsedSms
import com.example.ledger.sms.SmsAccountKind
import com.example.ledger.sms.StarterCategories
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
        internal val rules: Map<Pair<String, EntryKind>, Long>
    )

    suspend fun newImportContext(): ImportContext {
        ensureStarterCategories()
        val cats = categories.all().associate { (it.name to it.kind) to it.id }
        val rules = merchantRules.all().associate { (it.merchantKey to it.kind) to it.categoryId }
        return ImportContext(mutableMapOf(), cats, rules)
    }

    private val minute = 60_000L
    private val day = 24 * 60 * minute

    /** Adds any starter category this install does not have yet (older installs, new app versions). */
    suspend fun ensureStarterCategories() {
        val have = categories.all().map { it.name.lowercase() to it.kind }.toSet()
        for (c in StarterCategories.all) {
            val kind = if (c.isIncome) EntryKind.INCOME else EntryKind.EXPENSE
            if ((c.name.lowercase() to kind) !in have) categories.insert(Category(name = c.name, kind = kind, colorArgb = c.color))
        }
    }

    suspend fun countSmsTransactions(): Int = transactions.countSms()

    /**
     * Forgets everything read from SMS (transactions and the accounts found in SMS) so the inbox can
     * be read again with improved rules. Manual entries stay; learned merchant rules stay.
     */
    suspend fun clearSmsData() {
        db.withTransaction {
            val cashId = accounts.cashAccountId() ?: accounts.insert(Account(name = "Cash", type = AccountType.CASH))
            transactions.deleteSms()
            transactions.moveSmsAccountsTo(cashId)
            accounts.deleteSmsAccounts()
        }
    }

    private fun accountType(kind: SmsAccountKind) = when (kind) {
        SmsAccountKind.BANK -> AccountType.BANK
        SmsAccountKind.CARD -> AccountType.CARD
        SmsAccountKind.LOAN -> AccountType.LOAN
    }

    /** "SBI|BANK|" — the part of an account key shared by all accounts of one bank and type. */
    private fun keyPrefix(parsed: ParsedSms) = parsed.bankName + "|" + parsed.accountKind.name + "|"

    /**
     * Banks show different numbers of digits (ICICI "XX123", HDFC "*1234"), so accounts are matched
     * on the last three digits. Two accounts at one bank ending in the same three digits are rare.
     */
    private fun accountKey(parsed: ParsedSms) = keyPrefix(parsed) + (parsed.accountDigits?.takeLast(3) ?: "")

    private suspend fun accountIdFor(ctx: ImportContext, parsed: ParsedSms): Long {
        val key = accountKey(parsed)
        ctx.accountIds[key]?.let { return it }
        val existing = accounts.findBySmsKey(key)
        val id = when {
            existing != null -> existing.id
            parsed.accountDigits == null -> {
                // No number in this message: if this bank has exactly one numbered account, it is that one.
                val numbered = accounts.findBySmsKeyPrefix(keyPrefix(parsed)).filter { !it.smsKey!!.endsWith("|") }
                numbered.singleOrNull()?.id ?: newAccount(parsed, key)
            }
            else -> newAccount(parsed, key)
        }
        ctx.accountIds[key] = id
        return id
    }

    private suspend fun newAccount(parsed: ParsedSms, key: String): Long {
        val suffix = parsed.accountDigits?.let { " ••$it" } ?: ""
        val name = when (parsed.accountKind) {
            SmsAccountKind.BANK -> parsed.bankName + suffix
            SmsAccountKind.CARD -> parsed.bankName + " card" + suffix
            SmsAccountKind.LOAN -> parsed.bankName + " loan" + suffix
        }
        return accounts.insert(Account(name = name, type = accountType(parsed.accountKind), smsKey = key))
    }

    /**
     * Stores one parsed bank SMS as a transaction. Finds or creates the account, picks a category
     * (the person's own rule first, then what the message makes certain, then keywords), keeps the
     * latest SMS balance and avoids counting the same money twice.
     * Returns true if a new transaction was stored.
     */
    suspend fun recordSms(
        ctx: ImportContext,
        parsed: ParsedSms,
        smsKey: String,
        millis: Long,
        bodyForGuess: String,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): Boolean {
        val accountId = accountIdFor(ctx, parsed)
        val kind = if (parsed.isIncome) EntryKind.INCOME else EntryKind.EXPENSE

        if (parsed.balancePaise != null && parsed.accountKind == SmsAccountKind.BANK) {
            accounts.updateBalanceIfNewer(accountId, parsed.balancePaise, millis)
        }

        // The same alert sent twice (some banks send from two sender ids).
        if (transactions.countNear(accountId, parsed.amountPaise, kind, smsKey, millis - 3 * minute, millis + 3 * minute) > 0) {
            return false
        }
        if (parsed.accountKind == SmsAccountKind.LOAN) {
            // The lender confirms an EMI that the bank debit already counted: keep only the bank side.
            if (transactions.countNonLoanExpense(parsed.amountPaise, millis - 4 * day, millis + 4 * day) > 0) return false
        } else if (kind == EntryKind.EXPENSE) {
            transactions.deleteLoanExpense(parsed.amountPaise, millis - 4 * day, millis + 4 * day)
        }

        val merchantKey = CategoryGuesser.merchantKey(parsed.merchant)
        val categoryId = merchantKey?.let { ctx.rules[it to kind] }
            ?: parsed.categoryHint?.let { ctx.categoryByName[it to kind] }
            ?: CategoryGuesser.guess(parsed.isIncome, parsed.merchant, bodyForGuess)?.let { ctx.categoryByName[it to kind] }
            ?: ctx.categoryByName[(if (parsed.isIncome) "Other income" else "Other") to kind]

        val epochDay = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
        val rowId = transactions.insertIgnore(
            LedgerTransaction(
                accountId = accountId,
                categoryId = categoryId,
                kind = kind,
                amountPaise = parsed.amountPaise,
                note = "",
                epochDay = epochDay,
                merchant = parsed.merchant,
                merchantKey = merchantKey,
                smsKey = smsKey,
                epochMillis = millis
            )
        )
        return rowId != -1L
    }

    /**
     * After an import: an account created from messages without a number ("ICICI Bank") is merged
     * into that bank's numbered account when there is exactly one.
     */
    suspend fun finishImport() {
        val all = accounts.smsAccounts()
        for (generic in all.filter { it.smsKey!!.endsWith("|") }) {
            val siblings = all.filter { it.id != generic.id && it.smsKey!!.startsWith(generic.smsKey!!) }
            val target = siblings.singleOrNull() ?: continue
            db.withTransaction {
                transactions.moveAccount(generic.id, target.id)
                if (target.latestBalancePaise == null && generic.latestBalancePaise != null) {
                    accounts.updateBalanceIfNewer(target.id, generic.latestBalancePaise, generic.latestBalanceMillis)
                }
                accounts.deleteById(generic.id)
            }
        }
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
