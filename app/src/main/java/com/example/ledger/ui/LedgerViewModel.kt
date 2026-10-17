package com.example.ledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.LedgerApp
import com.example.ledger.core.BudgetPeriod
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
import com.example.ledger.sms.SmsImporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LedgerApp
    private val repo = app.repository
    private val settings = app.settings

    private val startDayState = MutableStateFlow(settings.budgetStartDay)
    val startDay: StateFlow<Int> = startDayState

    private val anchor = MutableStateFlow(LocalDate.now())

    /** The budget month being viewed (it follows the user's chosen start day). */
    val period: StateFlow<DateRange> = combine(anchor, startDayState) { date, day ->
        BudgetPeriod.containing(date, day)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        BudgetPeriod.containing(LocalDate.now(), settings.budgetStartDay)
    )

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
    val recurring: StateFlow<List<RecurringRule>> =
        repo.observeRecurring().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** null = never scanned this session; otherwise the progress of reading the SMS inbox. */
    sealed interface ScanState {
        data object Idle : ScanState
        data object Running : ScanState
        data class Done(val scanned: Int, val imported: Int) : ScanState
    }

    private val scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = scanState

    /** Reads bank messages from the SMS inbox (needs the SMS permission). Safe to call repeatedly. */
    fun scanSms() {
        if (scanState.value == ScanState.Running) return
        scanState.value = ScanState.Running
        viewModelScope.launch {
            val result = SmsImporter.importInbox(app, repo, settings.lastSmsScanMillis)
            settings.lastSmsScanMillis = System.currentTimeMillis()
            scanState.value = ScanState.Done(result.scanned, result.imported)
        }
    }

    /** Saves the person's category choice for a merchant and applies it to all of that merchant's entries. */
    fun learnMerchant(merchantKey: String, kind: EntryKind, categoryId: Long) {
        viewModelScope.launch { repo.learnMerchant(merchantKey, kind, categoryId) }
    }

    init {
        // Entries that came due while the app was closed show up immediately.
        viewModelScope.launch { repo.generateDueRecurring(LocalDate.now()) }
    }

    fun previousPeriod() {
        anchor.value = BudgetPeriod.previous(period.value).start
    }

    fun nextPeriod() {
        anchor.value = BudgetPeriod.next(period.value).start
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
