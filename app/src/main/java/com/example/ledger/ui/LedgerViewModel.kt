package com.example.ledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.LedgerApp
import com.example.ledger.core.PeriodPreset
import com.example.ledger.core.Periods
import com.example.ledger.core.DateRange
import com.example.ledger.core.Frequency
import com.example.ledger.data.Account
import com.example.ledger.data.AccountBalance
import com.example.ledger.data.AccountType
import com.example.ledger.data.Budget
import com.example.ledger.data.Category
import com.example.ledger.data.EntryKind
import com.example.ledger.data.LedgerTransaction
import com.example.ledger.data.RecurringRule
import com.example.ledger.data.Due
import com.example.ledger.core.RepeatingPayment
import com.example.ledger.sms.SmsImporter
import com.example.ledger.sms.SmsParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LedgerApp
    private val repo = app.repository
    private val settings = app.settings

    private val startDayState = MutableStateFlow(settings.budgetStartDay)
    val startDay: StateFlow<Int> = startDayState

    /** What the person picked at the top: a preset, how many steps back/forward, and custom dates. */
    data class PeriodChoice(val preset: PeriodPreset, val offset: Int, val custom: DateRange?)

    private val choiceState = MutableStateFlow(PeriodChoice(PeriodPreset.THIS_MONTH, 0, null))
    val periodChoice: StateFlow<PeriodChoice> = choiceState

    private fun resolve(choice: PeriodChoice, day: Int): DateRange =
        Periods.shift(Periods.range(choice.preset, LocalDate.now(), day, choice.custom), choice.preset, choice.offset)

    /** The date range being viewed. */
    val period: StateFlow<DateRange> = combine(choiceState, startDayState) { choice, day -> resolve(choice, day) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolve(choiceState.value, settings.budgetStartDay))

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<LedgerTransaction>> = period
        .flatMapLatest { repo.observeTransactions(it.start.toEpochDay(), it.endInclusive.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<Account>> =
        repo.observeAccounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val balances: StateFlow<List<AccountBalance>> =
        repo.observeBalances().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<Category>> =
        repo.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets: StateFlow<List<Budget>> =
        repo.observeBudgets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Merchants the person has already chosen a category for ("every time"). */
    val ruleKeys: StateFlow<Set<String>> = repo.observeRuleKeys().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Sets the category of one entry only (for entries without a merchant name). */
    fun setCategory(transaction: LedgerTransaction, categoryId: Long) {
        viewModelScope.launch { repo.saveTransaction(transaction.copy(categoryId = categoryId)) }
    }

    val recurring: StateFlow<List<RecurringRule>> =
        repo.observeRecurring().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** null = never scanned this session; otherwise the progress of reading the SMS inbox. */
    sealed interface ScanState {
        data object Idle : ScanState
        data class Running(val done: Int, val total: Int) : ScanState
        data class Done(val scanned: Int, val imported: Int) : ScanState
    }

    private val scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = scanState

    /** Reads bank messages from the SMS inbox (needs the SMS permission). Safe to call repeatedly. */
    fun scanSms() {
        if (scanState.value is ScanState.Running) return
        scanState.value = ScanState.Running(0, 0)
        viewModelScope.launch {
            repo.ensureStarterCategories()
            // New reading rules: forget old SMS results and read the whole inbox again.
            // Learned "every time" merchant choices and manual entries are kept.
            val rulesChanged = settings.smsParserVersion != SmsParser.VERSION
            if (rulesChanged) repo.clearSmsData()
            val full = rulesChanged || repo.countSmsTransactions() == 0
            val result = SmsImporter.importInbox(app, repo, if (full) 0L else settings.lastSmsScanMillis) { done, total ->
                scanState.value = ScanState.Running(done, total)
            }
            repo.afterImport()
            refreshSuggestions()
            settings.smsParserVersion = SmsParser.VERSION
            settings.lastSmsScanMillis = System.currentTimeMillis()
            scanState.value = ScanState.Done(result.scanned, result.imported)
        }
    }

    // ---- what counts in totals ----

    private val countState = MutableStateFlow(settings.countChoices)
    val countChoices: StateFlow<Map<Long, Boolean>> = countState

    fun setCounted(categoryId: Long, counted: Boolean) {
        settings.countChoices = settings.countChoices + (categoryId to counted)
        countState.value = settings.countChoices
    }

    // ---- dues & reminders ----

    val dues: StateFlow<List<Due>> = repo.observeOpenDues().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val dueMerchantKeys: StateFlow<Set<String>> = repo.observeDueMerchantKeys().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val repeatingState = MutableStateFlow<List<RepeatingPayment>>(emptyList())
    private val declinedState = MutableStateFlow(settings.declinedReminders)

    /** Regular payments the app spotted that have no reminder yet and were not declined. */
    val suggestions: StateFlow<List<RepeatingPayment>> = combine(repeatingState, dueMerchantKeys, declinedState) { list, keys, no ->
        list.filter { it.merchantKey !in keys && it.merchantKey !in no }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun refreshSuggestions() {
        viewModelScope.launch { repeatingState.value = repo.findRepeating() }
    }

    fun acceptSuggestion(p: RepeatingPayment) {
        viewModelScope.launch { repo.addReminder(titleCase(p.name), p.amountPaise, p.nextDate, p.months, p.merchantKey) }
    }

    fun declineSuggestion(p: RepeatingPayment) {
        settings.declinedReminders = settings.declinedReminders + p.merchantKey
        declinedState.value = settings.declinedReminders
    }

    fun addReminder(title: String, amountPaise: Long, date: LocalDate, repeatMonths: Int?) {
        viewModelScope.launch { repo.addReminder(title, amountPaise, date, repeatMonths) }
    }

    fun closeDue(due: Due, paid: Boolean) {
        viewModelScope.launch { repo.closeDue(due, paid) }
    }

    private fun titleCase(s: String) = s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    /** Saves the person's category choice for a merchant and applies it to all of that merchant's entries. */
    fun recategorize(items: List<LedgerTransaction>, categoryId: Long, remember: Boolean) {
        viewModelScope.launch { repo.recategorize(items, categoryId, remember) }
    }

    fun learnMerchant(merchantKey: String, kind: EntryKind, categoryId: Long) {
        viewModelScope.launch { repo.learnMerchant(merchantKey, kind, categoryId) }
    }

    init {
        viewModelScope.launch { repo.ensureStarterCategories() }
        refreshSuggestions()
        // Entries that came due while the app was closed show up immediately.
        viewModelScope.launch { repo.generateDueRecurring(LocalDate.now()) }
    }

    fun previousPeriod() {
        choiceState.value = choiceState.value.let { it.copy(offset = it.offset - 1) }
    }

    fun nextPeriod() {
        choiceState.value = choiceState.value.let { it.copy(offset = it.offset + 1) }
    }

    fun choosePreset(preset: PeriodPreset) {
        choiceState.value = choiceState.value.copy(preset = preset, offset = 0)
    }

    fun chooseCustom(start: LocalDate, endInclusive: LocalDate) {
        val (a, b) = if (endInclusive.isBefore(start)) endInclusive to start else start to endInclusive
        choiceState.value = PeriodChoice(PeriodPreset.CUSTOM, 0, DateRange(a, b))
    }

    fun setStartDay(day: Int) {
        settings.budgetStartDay = day
        startDayState.value = settings.budgetStartDay
    }

    fun saveTransaction(transaction: LedgerTransaction) {
        viewModelScope.launch { repo.saveTransaction(transaction) }
    }

    fun deleteTransaction(transaction: LedgerTransaction) {
        viewModelScope.launch { repo.deleteTransaction(transaction) }
    }

    /** A new entry that repeats: stored as a rule, and the first entry is created from it. */
    fun addRecurring(transaction: LedgerTransaction, frequency: Frequency) {
        val rule = RecurringRule(
            accountId = transaction.accountId,
            categoryId = transaction.categoryId,
            kind = transaction.kind,
            amountPaise = transaction.amountPaise,
            note = transaction.note,
            frequency = frequency,
            startEpochDay = transaction.epochDay
        )
        viewModelScope.launch { repo.addRecurring(rule, LocalDate.now()) }
    }

    fun setRecurringActive(rule: RecurringRule, active: Boolean) {
        viewModelScope.launch { repo.setRecurringActive(rule, active) }
    }

    fun deleteRecurring(rule: RecurringRule) {
        viewModelScope.launch { repo.deleteRecurring(rule.id) }
    }

    fun addAccount(name: String, type: AccountType, openingBalancePaise: Long) {
        viewModelScope.launch { repo.addAccount(name, type, openingBalancePaise) }
    }

    fun addCategory(name: String, kind: EntryKind, colorArgb: Long) {
        viewModelScope.launch { repo.addCategory(name, kind, colorArgb) }
    }

    fun renameCategory(category: Category, newName: String) {
        viewModelScope.launch { repo.renameCategory(category, newName) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repo.deleteCategory(category) }
    }

    fun setBudget(categoryId: Long, limitPaise: Long) {
        viewModelScope.launch { repo.setBudget(categoryId, limitPaise) }
    }
}
