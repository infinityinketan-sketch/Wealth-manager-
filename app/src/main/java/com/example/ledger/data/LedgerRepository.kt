package com.example.ledger.data

import androidx.room.withTransaction
import com.example.ledger.core.Recurrence
import com.example.ledger.core.MoneyMove
import com.example.ledger.core.PaymentPoint
import com.example.ledger.core.RecurringDetector
import com.example.ledger.core.RepeatingPayment
import com.example.ledger.core.TransferMatcher
import com.example.ledger.sms.CategoryGuesser
import com.example.ledger.sms.DueKindHint
import com.example.ledger.sms.DueNotice
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
    private val dues = db.dueDao()
    private val overrides = db.overrideDao()

    // ---- reads ----
    fun observeTransactions(fromDay: Long, toDay: Long): Flow<List<LedgerTransaction>> =
        transactions.observeBetween(fromDay, toDay)

    fun observeAccounts(): Flow<List<Account>> = accounts.observeActive()
    fun observeBalances(): Flow<List<AccountBalance>> = accounts.observeBalances()
    fun observeCategories(): Flow<List<Category>> = categories.observeAll()
    fun observeBudgets(): Flow<List<Budget>> = budgets.observeAll()
    fun observeRecurring(): Flow<List<RecurringRule>> = recurring.observeAll()
    fun observeRuleKeys(): Flow<List<String>> = merchantRules.observeKeys()
    fun observeOpenDues(): Flow<List<Due>> = dues.observeOpen()
    fun observeDueMerchantKeys(): Flow<List<String>> = dues.observeMerchantKeys()

    // ---- transactions ----
    suspend fun saveTransaction(transaction: LedgerTransaction) {
        if (transaction.id == 0L) transactions.insert(transaction) else transactions.update(transaction)
        // A fix to an SMS entry is remembered, so reading the inbox again never undoes it.
        transaction.smsKey?.let { overrides.upsert(EntryOverride(it, transaction.categoryId, transaction.note)) }
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
        internal val rules: Map<Pair<String, EntryKind>, Long>,
        /** Words learned from the person's own choices, e.g. "medical" -> "Health", per income / expense. */
        internal val learned: Map<EntryKind, Map<String, String>>,
        /** The person's own fixes to single entries, by SMS key. */
        internal val overrides: Map<String, EntryOverride> = emptyMap()
    )

    suspend fun newImportContext(): ImportContext {
        ensureStarterCategories()
        val cats = categories.all().associate { (it.name to it.kind) to it.id }
        val rules = merchantRules.all().associate { (it.merchantKey to it.kind) to it.categoryId }
        val nameById = categories.all().associate { it.id to it.name }
        val examples = merchantRules.examples()
        val learned = EntryKind.values().associateWith { kind ->
            CategoryGuesser.learnWords(
                examples.filter { it.kind == kind }.mapNotNull { e -> nameById[e.categoryId]?.let { e.merchant to it } }
            )
        }
        return ImportContext(mutableMapOf(), cats, rules, learned, overrides.all().associateBy { it.smsKey })
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
        val suffix = parsed.accountDigits?.let { " ••" + it.takeLast(4) } ?: ""
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

        // The same alert twice: identical text, or the bank's second sender id. Two real payments of
        // the same amount from the same sender (two ₹20 teas) are both kept.
        val near = transactions.keysNear(accountId, parsed.amountPaise, kind, smsKey, millis - 3 * minute, millis + 3 * minute)
        if (near.any { SmsDuplicates.isRepeat(it, smsKey) }) return false
        if (parsed.accountKind == SmsAccountKind.LOAN) {
            // The lender confirms an EMI that the bank debit already counted: keep only the bank side.
            if (transactions.countNonLoanExpense(parsed.amountPaise, millis - 4 * day, millis + 4 * day) > 0) return false
        } else if (kind == EntryKind.EXPENSE) {
            transactions.deleteLoanExpense(parsed.amountPaise, millis - 4 * day, millis + 4 * day)
        }

        val merchantKey = CategoryGuesser.merchantKey(parsed.merchant)
        val fix = ctx.overrides[smsKey]
        val categoryId = if (fix != null) fix.categoryId else merchantKey?.let { ctx.rules[it to kind] }
            ?: parsed.categoryHint?.let { ctx.categoryByName[it to kind] }
            ?: CategoryGuesser.guess(parsed.isIncome, parsed.merchant, bodyForGuess, ctx.learned[kind] ?: emptyMap())
                ?.let { ctx.categoryByName[it to kind] }
            ?: ctx.categoryByName[(if (parsed.isIncome) "Other income" else "Other") to kind]

        val epochDay = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
        val rowId = transactions.insertIgnore(
            LedgerTransaction(
                accountId = accountId,
                categoryId = categoryId,
                kind = kind,
                amountPaise = parsed.amountPaise,
                note = fix?.note ?: "",
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
    /** Everything that should run after new messages were read. */
    suspend fun afterImport() {
        finishImport()
        matchSelfTransfers()
        checkDuesPaid()
    }

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
    /**
     * Moves several entries to one category at once. With [remember], every shop among them is
     * learned too, so its other and future payments follow.
     */
    suspend fun recategorize(items: List<LedgerTransaction>, categoryId: Long, remember: Boolean) {
        if (items.isEmpty()) return
        db.withTransaction {
            transactions.setCategory(items.map { it.id }, categoryId)
            overrides.upsertAll(items.mapNotNull { t -> t.smsKey?.let { EntryOverride(it, categoryId, t.note) } })
            if (remember) {
                items.filter { it.merchantKey != null }.map { it.merchantKey!! to it.kind }.distinct().forEach { (key, kind) ->
                    merchantRules.upsert(MerchantRule(key, categoryId, kind))
                    transactions.setCategoryForMerchant(key, kind, categoryId)
                    overrides.clearForMerchant(key, kind)
                }
            }
        }
        if (remember) reguessUncertain()
    }

    suspend fun learnMerchant(merchantKey: String, kind: EntryKind, categoryId: Long) {
        db.withTransaction {
            merchantRules.upsert(MerchantRule(merchantKey, categoryId, kind))
            transactions.setCategoryForMerchant(merchantKey, kind, categoryId)
            overrides.clearForMerchant(merchantKey, kind)
        }
        reguessUncertain()
    }

    /**
     * Entries the app was unsure about get another look with everything learned so far: a choice of
     * Health for "SAI MEDICAL" also moves "OM MEDICAL" from Other to Health.
     */
    suspend fun reguessUncertain() {
        val ctx = newImportContext()
        val fallbackIds = ctx.categoryByName.filterKeys { it.first in StarterCategories.fallbackNames }.values.toList()
        for (t in transactions.uncertain(fallbackIds)) {
            if (t.smsKey != null && t.smsKey in ctx.overrides) continue // the person chose this one themselves
            val merchant = t.merchant ?: continue
            val newId = t.merchantKey?.let { ctx.rules[it to t.kind] }
                ?: CategoryGuesser.guess(t.kind == EntryKind.INCOME, merchant, "", ctx.learned[t.kind] ?: emptyMap())
                    ?.let { ctx.categoryByName[it to t.kind] }
                ?: continue
            if (newId != t.categoryId) transactions.update(t.copy(categoryId = newId))
        }
    }

    // ---- self-transfers ----

    /**
     * Money that left one of the person's accounts and arrived in another of their accounts (same
     * amount, close in time) is a self-transfer: marked so it counts as neither spending nor income.
     * Only entries the app was unsure about are touched; the person's own choices stay.
     */
    suspend fun matchSelfTransfers(nowMillis: Long = System.currentTimeMillis()) {
        val cats = categories.all()
        fun idOf(name: String, kind: EntryKind) = cats.firstOrNull { it.name == name && it.kind == kind }?.id
        val selfId = idOf(CategoryGuesser.SELF_TRANSFER, EntryKind.EXPENSE) ?: return
        val inId = idOf(CategoryGuesser.TRANSFER_IN, EntryKind.INCOME) ?: return
        val cardBillId = idOf(CategoryGuesser.CARD_BILL, EntryKind.EXPENSE) ?: selfId
        val fallbackIds = cats.filter { it.name in StarterCategories.fallbackNames }.map { it.id }.toSet()
        val ruled = merchantRules.all().map { it.merchantKey }.toSet()
        val fixed = overrides.all().map { it.smsKey }.toSet()
        val types = accounts.smsAccounts().associate { it.id to it.type }

        val candidates = transactions.ownAccountSmsSince(nowMillis - 400 * day)
            .filter { (it.categoryId == null || it.categoryId in fallbackIds) && (it.merchantKey == null || it.merchantKey !in ruled) && it.smsKey !in fixed }
        val byId = candidates.associateBy { it.id }
        val pairs = TransferMatcher.pairs(candidates.map { MoneyMove(it.id, it.accountId, it.kind == EntryKind.INCOME, it.amountPaise, it.epochMillis) })
        if (pairs.isEmpty()) return
        db.withTransaction {
            for ((debitId, creditId) in pairs) {
                val credit = byId[creditId] ?: continue
                val toCard = types[credit.accountId] == AccountType.CARD
                transactions.setCategory(listOf(debitId), if (toCard) cardBillId else selfId)
                transactions.setCategory(listOf(creditId), inId)
            }
        }
    }

    // ---- dues & reminders ----

    private fun dueKind(h: DueKindHint) = when (h) {
        DueKindHint.CARD -> DueKind.CARD
        DueKindHint.LOAN -> DueKind.LOAN
        DueKindHint.INSURANCE -> DueKind.INSURANCE
        DueKindHint.UTILITY -> DueKind.UTILITY
        DueKindHint.OTHER -> DueKind.OTHER
    }

    /** Stores a bill read from SMS; the same bill seen again is ignored. */
    suspend fun openDues(): List<Due> = dues.open()

    suspend fun recordDue(notice: DueNotice, smsMillis: Long) {
        val kind = dueKind(notice.kind)
        dues.insertIgnore(
            Due(
                dueKey = listOf(kind.name, notice.title, notice.digits ?: "", notice.dueDate.toEpochDay()).joinToString("|"),
                title = notice.title,
                digits = notice.digits,
                kind = kind,
                amountPaise = notice.amountPaise,
                minDuePaise = notice.minDuePaise,
                dueEpochDay = notice.dueDate.toEpochDay(),
                merchantKey = null,
                repeatMonths = null,
                createdMillis = smsMillis
            )
        )
    }

    suspend fun addReminder(title: String, amountPaise: Long, date: LocalDate, repeatMonths: Int?, merchantKey: String? = null) {
        val kind = if (merchantKey != null) DueKind.REPEATING else DueKind.OTHER
        dues.insertIgnore(
            Due(
                dueKey = "REMINDER|" + (merchantKey ?: title.lowercase()) + "|" + date.toEpochDay(),
                title = title.trim(),
                digits = null,
                kind = kind,
                amountPaise = amountPaise,
                minDuePaise = null,
                dueEpochDay = date.toEpochDay(),
                merchantKey = merchantKey,
                repeatMonths = repeatMonths,
                createdMillis = System.currentTimeMillis()
            )
        )
    }

    /** Marks a due paid (or dismissed); a repeating one moves on to its next date. */
    suspend fun closeDue(due: Due, paid: Boolean) {
        dues.update(due.copy(status = if (paid) DueStatus.PAID else DueStatus.DISMISSED))
        val months = due.repeatMonths
        if (months != null) {
            val next = LocalDate.ofEpochDay(due.dueEpochDay).plusMonths(months.toLong())
            dues.insertIgnore(
                due.copy(
                    id = 0,
                    dueKey = due.dueKey.substringBeforeLast("|") + "|" + next.toEpochDay(),
                    dueEpochDay = next.toEpochDay(),
                    status = DueStatus.OPEN,
                    createdMillis = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Looks for the payment of each open due among the transactions (same amount, card payment
     * received, or the same merchant) and closes it. Old one-off dues nobody paid are put away.
     */
    suspend fun checkDuesPaid(today: LocalDate = LocalDate.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()) {
        val cats = categories.all()
        val cardBillId = cats.firstOrNull { it.name == CategoryGuesser.CARD_BILL }?.id
        val accountById = accounts.smsAccounts().associateBy { it.id }
        for (d in dues.open()) {
            val dueDate = LocalDate.ofEpochDay(d.dueEpochDay)
            val from = minOf(d.createdMillis, dueDate.minusDays(10).atStartOfDay(zone).toInstant().toEpochMilli()) - day
            val to = dueDate.plusDays(5).atStartOfDay(zone).toInstant().toEpochMilli()
            val txs = transactions.betweenMillis(from, to)
            val paid = txs.any { t ->
                val near = Math.abs(t.amountPaise - d.amountPaise) <= 100
                when {
                    d.merchantKey != null -> t.kind == EntryKind.EXPENSE && t.merchantKey == d.merchantKey
                    d.kind == DueKind.CARD -> {
                        val acc = accountById[t.accountId]
                        val cardMatch = acc != null && acc.type == AccountType.CARD && t.kind == EntryKind.INCOME &&
                            (d.digits == null || acc.smsKey?.endsWith(d.digits!!.takeLast(3)) == true)
                        val billPaid = t.kind == EntryKind.EXPENSE && t.categoryId == cardBillId &&
                            t.amountPaise >= (d.minDuePaise ?: d.amountPaise)
                        cardMatch || billPaid || (t.kind == EntryKind.EXPENSE && near)
                    }
                    else -> t.kind == EntryKind.EXPENSE && near && t.epochMillis >= d.createdMillis - day
                }
            }
            when {
                paid -> closeDue(d, paid = true)
                d.repeatMonths == null && dueDate.isBefore(today.minusDays(30)) -> dues.update(d.copy(status = DueStatus.DISMISSED))
                d.repeatMonths != null && dueDate.isBefore(today.minusDays(20)) -> closeDue(d, paid = false)
            }
        }
    }

    /** Payments that repeat at a steady rhythm, for "Set reminder?" suggestions. */
    suspend fun findRepeating(today: LocalDate = LocalDate.now()): List<RepeatingPayment> {
        val excluded = categories.all().filter { it.name in StarterCategories.excludedNames }.map { it.id }.toSet()
        val points = transactions.merchantExpensesSince(today.minusDays(400).toEpochDay())
            .filter { it.categoryId !in excluded }
            .map { PaymentPoint(it.merchantKey!!, it.merchant ?: it.merchantKey!!, it.amountPaise, LocalDate.ofEpochDay(it.epochDay)) }
        return RecurringDetector.find(points, today)
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
