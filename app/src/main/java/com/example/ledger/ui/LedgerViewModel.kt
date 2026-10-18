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
import com.example.ledger.data.CrashLog
import com.example.ledger.core.RepeatingPayment
import com.example.ledger.sms.SmsImporter
import com.example.ledger.sms.SmsParser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
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

    /** A short message for the screen when something failed in the background (instead of closing the app). */
    private val errorState = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = errorState

    fun clearError() {
        errorState.value = null
    }

    private val errorHandler = CoroutineExceptionHandler { _, e ->
        CrashLog.save(app, e, fatal = false)
        if (scanState.value is ScanState.Running) scanState.value = ScanState.Idle
        errorState.value = "Something went wrong (" + (e.javaClass.simpleName) + "). Details were saved; open More › Problem report to copy them."
    }

    private fun launchSafe(block: suspend CoroutineScope.() -> Unit) = viewModelScope.launch(errorHandler, block = block)

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
        launchSafe { repo.saveTransaction(transaction.copy(categoryId = categoryId)) }
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

    /** Bank messages the last scans could not read, for the "not understood" screen. */
    private val unreadState = MutableStateFlow<List<SmsImporter.Unread>>(emptyList())
    val unread: StateFlow<List<SmsImporter.Unread>> = unreadState

    /** From the last full read of the inbox: messages read, bank transactions found, not understood. */
    private val statsState = MutableStateFlow(settings.lastFullScan)
    val scanStats: StateFlow<Triple<Int, Int, Int>?> = statsState

    /** Reads the whole inbox again from the start (stored entries and the person's fixes are kept). */
    fun fullRescan() {
        settings.lastSmsScanMillis = 0L
        scanSms()
    }
    val scan: StateFlow<ScanState> = scanState

    /** Reads bank messages from the SMS inbox (needs the SMS permission). Safe to call repeatedly. */
    fun scanSms() {
        if (scanState.value is ScanState.Running) return
        scanState.value = ScanState.Running(0, 0)
        launchSafe {
            repo.ensureStarterCategories()
            // New reading rules: forget old SMS results and read the whole inbox again.
            // Learned "every time" merchant choices and manual entries are kept.
            val rulesChanged = settings.smsParserVersion != SmsParser.VERSION
            if (rulesChanged) repo.clearSmsData()
            val full = rulesChanged || repo.countSmsTransactions() == 0 || settings.lastSmsScanMillis == 0L
            val result = SmsImporter.importInbox(app, repo, if (full) 0L else settings.lastSmsScanMillis) { done, total ->
                scanState.value = ScanState.Running(done, total)
            }
            repo.afterImport()
            refreshSuggestions()
            settings.smsParserVersion = SmsParser.VERSION
            settings.lastSmsScanMillis = System.currentTimeMillis()
            if (full) {
                settings.lastFullScan = Triple(result.scanned, result.found, result.unread.size)
                unreadState.value = result.unread
            } else if (result.unread.isNotEmpty()) {
                unreadState.value = (result.unread + unreadState.value).distinctBy { it.sender + it.millis }.take(300)
            }
            statsState.value = settings.lastFullScan
            scanState.value = ScanState.Done(result.scanned, result.imported)
        }
    }

    // ---- "what was this for?" ----

    private val promptState = MutableStateFlow<Pair<LedgerTransaction, List<Category>>?>(null)
    /** The payment opened from the notification, with quick category choices. */
    val prompt: StateFlow<Pair<LedgerTransaction, List<Category>>?> = promptState

    fun openPrompt(txId: Long) {
        launchSafe { repo.transactionById(txId)?.let { promptState.value = it to repo.quickCategories(it, 4) } }
    }

    fun answerPrompt(txId: Long, categoryId: Long, rememberShop: Boolean) {
        promptState.value = null
        launchSafe { repo.answerPrompt(txId, categoryId, rememberShop) }
    }

    fun closePrompt() {
        promptState.value = null
    }

    private val askState = MutableStateFlow(settings.askCategory)
    val askCategory: StateFlow<Boolean> = askState

    fun setAskCategory(on: Boolean) {
        settings.askCategory = on
        askState.value = on
    }

    // ---- "some payments send no SMS" note ----

    private val noteState = MutableStateFlow(settings.trackingNoteSeen)
    val trackingNoteSeen: StateFlow<Boolean> = noteState

    fun dismissTrackingNote() {
        settings.trackingNoteSeen = true
        noteState.value = true
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
        launchSafe { repeatingState.value = repo.findRepeating() }
    }

    fun acceptSuggestion(p: RepeatingPayment) {
        launchSafe { repo.addReminder(titleCase(p.name), p.amountPaise, p.nextDate, p.months, p.merchantKey) }
    }

    fun declineSuggestion(p: RepeatingPayment) {
        settings.declinedReminders = settings.declinedReminders + p.merchantKey
        declinedState.value = settings.declinedReminders
    }

    fun addReminder(title: String, amountPaise: Long, date: LocalDate, repeatMonths: Int?) {
        launchSafe { repo.addReminder(title, amountPaise, date, repeatMonths) }
    }

    fun closeDue(due: Due, paid: Boolean) {
        launchSafe { repo.closeDue(due, paid) }
    }

    private fun titleCase(s: String) = s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    /** Saves the person's category choice for a merchant and applies it to all of that merchant's entries. */
    fun recategorize(items: List<LedgerTransaction>, categoryId: Long, remember: Boolean) {
        launchSafe { repo.recategorize(items, categoryId, remember) }
    }

    fun learnMerchant(merchantKey: String, kind: EntryKind, categoryId: Long) {
        launchSafe { repo.learnMerchant(merchantKey, kind, categoryId) }
    }

    init {
        launchSafe { repo.ensureStarterCategories() }
        refreshSuggestions()
        // Entries that came due while the app was closed show up immediately.
        launchSafe { repo.generateDueRecurring(LocalDate.now()) }
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
        launchSafe { repo.saveTransaction(transaction) }
    }

    fun deleteTransaction(transaction: LedgerTransaction) {
        launchSafe { repo.deleteTransaction(transaction) }
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
        launchSafe { repo.addRecurring(rule, LocalDate.now()) }
    }

    fun setRecurringActive(rule: RecurringRule, active: Boolean) {
        launchSafe { repo.setRecurringActive(rule, active) }
    }

    fun deleteRecurring(rule: RecurringRule) {
        launchSafe { repo.deleteRecurring(rule.id) }
    }

    fun addAccount(name: String, type: AccountType, openingBalancePaise: Long) {
        launchSafe { repo.addAccount(name, type, openingBalancePaise) }
    }

    fun addCategory(name: String, kind: EntryKind, colorArgb: Long) {
        launchSafe { repo.addCategory(name, kind, colorArgb) }
    }

    fun renameCategory(category: Category, newName: String) {
        launchSafe { repo.renameCategory(category, newName) }
    }

    fun deleteCategory(category: Category) {
        launchSafe { repo.deleteCategory(category) }
    }

    fun setBudget(categoryId: Long, limitPaise: Long) {
        launchSafe { repo.setBudget(categoryId, limitPaise) }
    }
}
