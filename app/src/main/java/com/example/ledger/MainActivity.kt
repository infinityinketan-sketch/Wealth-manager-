package com.example.ledger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import kotlinx.coroutines.launch
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.data.EntryKind
import com.example.ledger.data.LedgerTransaction
import com.example.ledger.ui.HomeScreen
import com.example.ledger.ui.LedgerTheme
import com.example.ledger.ui.LedgerViewModel
import com.example.ledger.ui.MoreScreen
import com.example.ledger.ui.CategoryScreen
import com.example.ledger.ui.excludedCategoryIds
import com.example.ledger.ui.formatDate
import com.example.ledger.ui.formatShortDate
import com.example.ledger.ui.SortScreen
import com.example.ledger.ui.AddReminderDialog
import com.example.ledger.ui.UnreadScreen
import com.example.ledger.ui.ProblemReportDialog
import com.example.ledger.ui.QuickCategoryDialog
import com.example.ledger.ui.sortGroups
import com.example.ledger.ui.TransactionEditor

/**
 * FragmentActivity (not plain ComponentActivity) because BiometricPrompt needs a FragmentActivity.
 * The app lock re-locks whenever the app goes to the background.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: LedgerViewModel by viewModels()

    private var locked by mutableStateOf(false)
    private var appLockEnabled by mutableStateOf(false)
    private var authInProgress = false

    /** True while the system file picker is open for a backup/restore: do not lock the app for that. */
    var pickingFile = false

    // BIOMETRIC_STRONG + DEVICE_CREDENTIAL is not supported on Android 9 and 10, so use WEAK.
    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private val settings get() = (application as LedgerApp).settings

    private var smsGranted by mutableStateOf(false)
    private var smsPromptDone by mutableStateOf(false)
    private var showOnboarding by mutableStateOf(false)
    /** Test builds without Firebase set up can skip sign-in. */
    private var skippedSignIn by mutableStateOf(false)

    private fun hasSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            settings.smsPromptDone = true
            smsPromptDone = true
            smsGranted = hasSmsPermission()
            if (smsGranted) viewModel.scanSms()
        }

    private fun requestSms() {
        val wanted = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        if (android.os.Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        smsPermissionLauncher.launch(wanted.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 16 always draws apps edge to edge; screens add their own system-bar padding.
        enableEdgeToEdge()
        appLockEnabled = settings.appLockEnabled
        locked = appLockEnabled
        smsGranted = hasSmsPermission()
        smsPromptDone = settings.smsPromptDone
        showOnboarding = !settings.onboardingSeen
        handlePromptIntent(intent)

        val app = application as LedgerApp
        setContent {
            val account by app.account.state.collectAsStateWithLifecycle()
            val pro by app.pro.state.collectAsStateWithLifecycle()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            LedgerTheme {
                if (locked) {
                    LockedScreen(onUnlock = { authenticate() })
                } else if (showOnboarding) {
                    // How the app keeps money data safe: shown before anything else.
                    com.example.ledger.ui.OnboardingScreen(onFinish = {
                        settings.onboardingSeen = true
                        showOnboarding = false
                    })
                } else if (!account.signedIn && !skippedSignIn) {
                    com.example.ledger.ui.SignInScreen(
                        state = account,
                        isDebugBuild = BuildConfig.DEBUG,
                        onSignIn = { consent ->
                            scope.launch {
                                if (app.account.signIn(this@MainActivity, consent)) {
                                    app.pro.refresh()
                                    app.account.onAppOpen(force = true)
                                }
                            }
                        },
                        onSkipForTest = { skippedSignIn = true }
                    )
                } else if (!pro.isPro) {
                    // Free month over and not bought: only paying, restoring and account actions.
                    com.example.ledger.ui.TrialEndedScreen(
                        p = pro,
                        account = account,
                        onBuy = {
                            if (!app.pro.buy(this@MainActivity)) {
                                Toast.makeText(this@MainActivity, "Google Play is not reachable right now. Try again when online.", Toast.LENGTH_LONG).show()
                            }
                        },
                        onRestore = { app.pro.restore() },
                        onSignOut = { scope.launch { app.account.signOut() } },
                        onDeleteAccount = { scope.launch { app.account.deleteAccount(this@MainActivity) } }
                    )
                } else if (!smsGranted && !smsPromptDone) {
                    SmsIntroScreen(
                        onAllow = { requestSms() },
                        onSkip = {
                            settings.smsPromptDone = true
                            smsPromptDone = true
                        }
                    )
                } else {
                    LedgerRoot(
                        viewModel = viewModel,
                        appLockEnabled = appLockEnabled,
                        onAppLockChange = { setAppLock(it) },
                        smsGranted = smsGranted,
                        onEnableSms = { requestSms() },
                        onShowSafety = { showOnboarding = true }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handlePromptIntent(intent)
    }

    /** Opened from a "what was this for?" notification: show the quick category picker. */
    private fun handlePromptIntent(intent: android.content.Intent?) {
        val txId = intent?.getLongExtra(com.example.ledger.work.CategoryPrompt.EXTRA_TX, -1L) ?: -1L
        if (txId >= 0) {
            com.example.ledger.work.CategoryPrompt.cancel(this, com.example.ledger.work.CategoryPrompt.notificationFor(txId))
            viewModel.openPrompt(txId)
            intent?.removeExtra(com.example.ledger.work.CategoryPrompt.EXTRA_TX)
        }
    }

    override fun onResume() {
        super.onResume()
        pickingFile = false
        // Check the Pro purchase with Google Play (works offline from the saved answer).
        (application as LedgerApp).pro.refresh()
        (application as LedgerApp).account.onAppOpen()
        smsGranted = hasSmsPermission()
        // Pick up anything that arrived while the app was closed.
        if (smsGranted && !locked) viewModel.scanSms()
        if (locked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        // The system credential screen can briefly stop this activity; do not re-lock during that.
        if (appLockEnabled && !authInProgress && !pickingFile) locked = true
    }

    private fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticate() {
        // A phone with no screen lock or fingerprint has nothing to ask for, so do not lock people out.
        if (!canAuthenticate()) {
            locked = false
            return
        }
        if (authInProgress) return
        authInProgress = true

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    locked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Stay locked; the Unlock button lets the person try again.
                    authInProgress = false
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Wealth Manager")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    private fun setAppLock(enabled: Boolean) {
        if (enabled && !canAuthenticate()) {
            Toast.makeText(this, "Set up a screen lock or fingerprint in your phone settings first", Toast.LENGTH_LONG).show()
            return
        }
        settings.appLockEnabled = enabled
        appLockEnabled = enabled
    }
}

@Composable
private fun SmsIntroScreen(onAllow: () -> Unit, onSkip: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Wealth Manager", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Your spending, tracked automatically",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Allow access to your messages and the app finds your bank, UPI and card transactions " +
                    "on its own: what you spent, where, your income and your balances.\n\n" +
                    "Your messages are read on this phone only and are never uploaded. " +
                    "Only bank and payment messages are used; personal chats are ignored.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)
            )
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Good to know", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(
                        com.example.ledger.ui.TrackingInfo.SHORT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Button(onClick = onAllow) { Text("Allow and scan my messages") }
            TextButton(onClick = onSkip) { Text("Not now, I will add entries myself") }
        }
    }
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Wealth Manager is locked", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onUnlock, modifier = Modifier.padding(top = 16.dp)) { Text("Unlock") }
        }
    }
}

@Composable
private fun LedgerRoot(
    viewModel: LedgerViewModel,
    appLockEnabled: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    smsGranted: Boolean,
    onEnableSms: () -> Unit,
    onShowSafety: () -> Unit
) {
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    var pendingLearn by remember { mutableStateOf<LedgerTransaction?>(null) }
    var showSort by remember { mutableStateOf(false) }
    // The category opened from the home screen: (category id or null for uncategorised, kind, account filter).
    var openCategory by remember { mutableStateOf<Triple<Long?, EntryKind, Long?>?>(null) }
    val ruleKeys by viewModel.ruleKeys.collectAsStateWithLifecycle()
    val dues by viewModel.dues.collectAsStateWithLifecycle()
    val countChoices by viewModel.countChoices.collectAsStateWithLifecycle()
    val trackingNoteSeen by viewModel.trackingNoteSeen.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    val scanStats by viewModel.scanStats.collectAsStateWithLifecycle()
    var showUnread by remember { mutableStateOf(false) }
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val askCategory by viewModel.askCategory.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackup.collectAsStateWithLifecycle()
    // Backup: password first, then the person picks where to save the file.
    var askBackupPassword by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf<CharArray?>(null) }
    val saveBackup = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pw = backupPassword
        if (uri != null && pw != null) {
            viewModel.getApplication<LedgerApp>().account.track("backup")
            viewModel.backup(uri, pw)
        }
        backupPassword = null
    }
    // Restore: the person picks the file, then gives its password.
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val openBackup = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> restoreUri = uri }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Details of a crash from last time are offered once, on the next start.
    var problem by remember { mutableStateOf(com.example.ledger.data.CrashLog.read(context)) }
    var showProblem by remember { mutableStateOf(problem != null) }
    val error by viewModel.error.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    var showAddReminder by remember { mutableStateOf(false) }
    val period by viewModel.period.collectAsStateWithLifecycle()
    val periodChoice by viewModel.periodChoice.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()
    val startDay by viewModel.startDay.collectAsStateWithLifecycle()

    // ---- Pro ----
    val app = context.applicationContext as LedgerApp
    val pro by app.pro.state.collectAsStateWithLifecycle()
    val accountState by app.account.state.collectAsStateWithLifecycle()
    var usageConsent by remember { mutableStateOf(app.settings.usageConsent) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // (reason, trial just ended) when the Pro sheet is open.
    var proAsk by remember { mutableStateOf<Pair<String?, Boolean>?>(null) }
    androidx.compose.runtime.LaunchedEffect(pro.isPro) {
        if (!pro.isPro) {
            // Trial over: move back to a free period, and say so politely once.
            if (periodChoice.preset !in com.example.ledger.ui.FREE_PRESETS) {
                viewModel.choosePreset(com.example.ledger.core.PeriodPreset.THIS_MONTH)
            }
            if (!app.settings.trialEndSeen && !pro.purchased) {
                app.settings.trialEndSeen = true
                proAsk = null to true
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(pro.message) {
        pro.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            app.pro.clearMessage()
        }
    }

    var tab by remember { mutableIntStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerTransaction?>(null) }

    val toSort = remember(transactions, categories, ruleKeys) { sortGroups(transactions, categories, ruleKeys) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("More") }
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = {
                    editing = null
                    showEditor = true
                }) { Icon(Icons.Filled.Add, contentDescription = "Add entry") }
            }
        }
    ) { padding ->
        if (tab == 0) {
            HomeScreen(
                period = period,
                transactions = transactions,
                accounts = accounts,
                balances = balances,
                categories = categories,
                budgets = budgets,
                smsGranted = smsGranted,
                scanning = scan is LedgerViewModel.ScanState.Running,
                scanProgress = (scan as? LedgerViewModel.ScanState.Running)?.let { it.done to it.total },
                scanStats = scanStats,
                onShowUnread = { showUnread = true },
                dues = dues,
                suggestions = suggestions,
                onDuePaid = { viewModel.closeDue(it, paid = true) },
                onDueDismiss = { viewModel.closeDue(it, paid = false) },
                onAcceptSuggestion = viewModel::acceptSuggestion,
                onDeclineSuggestion = viewModel::declineSuggestion,
                onAddReminder = { showAddReminder = true },
                lastImported = (scan as? LedgerViewModel.ScanState.Done)?.imported,
                onEnableSms = onEnableSms,
                onScanAgain = viewModel::scanSms,
                trackingNoteSeen = trackingNoteSeen,
                onDismissTrackingNote = viewModel::dismissTrackingNote,
                onAddPayment = {
                    editing = null
                    showEditor = true
                },
                preset = periodChoice.preset,
                countChoices = countChoices,
                toSortCount = toSort.sumOf { it.items.size },
                toSortTotal = toSort.sumOf { it.total },
                onSort = {
                    app.account.track("sort_screen")
                    showSort = true
                },
                onPreset = viewModel::choosePreset,
                onCustom = viewModel::chooseCustom,
                onPrevious = viewModel::previousPeriod,
                onNext = viewModel::nextPeriod,
                onOpenCategory = { id, kind, accountId ->
                    app.account.track("category_open")
                    openCategory = Triple(id, kind, accountId)
                },
                pro = pro,
                onNeedPro = { reason -> proAsk = reason to false },
                onEdit = {
                    editing = it
                    showEditor = true
                },
                modifier = Modifier.padding(padding)
            )
        } else {
            MoreScreen(
                startDay = startDay,
                onStartDayChange = viewModel::setStartDay,
                appLockEnabled = appLockEnabled,
                onAppLockChange = onAppLockChange,
                balances = balances,
                categories = categories,
                budgets = budgets,
                recurring = recurring,
                onAddAccount = viewModel::addAccount,
                onAddCategory = viewModel::addCategory,
                onRenameCategory = viewModel::renameCategory,
                onDeleteCategory = viewModel::deleteCategory,
                onSetBudget = viewModel::setBudget,
                onToggleRecurring = viewModel::setRecurringActive,
                onDeleteRecurring = viewModel::deleteRecurring,
                askCategory = askCategory,
                onAskCategoryChange = viewModel::setAskCategory,
                lastBackupMillis = lastBackup,
                onBackup = { askBackupPassword = true },
                onRestore = {
                    (context as? MainActivity)?.pickingFile = true
                    openBackup.launch(arrayOf("*/*"))
                },
                onShowSafety = onShowSafety,
                pro = pro,
                onNeedPro = { reason -> proAsk = reason to false },
                onRestorePurchase = { app.pro.restore() },
                account = accountState,
                usageConsent = usageConsent,
                onUsageConsent = { on ->
                    usageConsent = on
                    app.account.setUsageConsent(on)
                },
                onSignOut = { scope.launch { app.account.signOut() } },
                onDeleteAccount = { (context as? android.app.Activity)?.let { a -> scope.launch { app.account.deleteAccount(a) } } },
                onProblemReport = {
                    problem = com.example.ledger.data.CrashLog.read(context)
                    showProblem = true
                },
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showEditor) {
        TransactionEditor(
            existing = editing,
            accounts = accounts,
            categories = categories,
            onSave = { transaction, repeat ->
                if (repeat != null) viewModel.addRecurring(transaction, repeat) else viewModel.saveTransaction(transaction)
                // The person re-categorised a merchant's entry: offer to remember it for every such entry.
                val before = editing
                if (before != null && repeat == null && transaction.merchantKey != null &&
                    transaction.categoryId != null && transaction.categoryId != before.categoryId
                ) {
                    pendingLearn = transaction
                }
            },
            onDelete = viewModel::deleteTransaction,
            onDismiss = { showEditor = false }
        )
    }

    openCategory?.let { (catId, kind, accountId) ->
        val items = transactions.filter {
            it.categoryId == catId && it.kind == kind && (accountId == null || it.accountId == accountId)
        }
        CategoryScreen(
            category = categories.firstOrNull { it.id == catId },
            kind = kind,
            periodLabel = formatShortDate(period.start) + " – " + formatDate(period.endInclusive),
            items = items,
            categories = categories,
            accountNames = accounts.associate { it.id to it.name },
            counted = catId == null || catId !in excludedCategoryIds(categories, countChoices),
            onCountedChange = { on -> catId?.let { viewModel.setCounted(it, on) } },
            onMove = viewModel::recategorize,
            onOpen = {
                editing = it
                showEditor = true
            },
            onDismiss = { openCategory = null }
        )
    }

    prompt?.let { (t, quick) ->
        QuickCategoryDialog(
            t = t,
            suggestions = quick,
            categories = categories,
            onPick = { cat, rememberShop -> viewModel.answerPrompt(t.id, cat, rememberShop) },
            onDismiss = viewModel::closePrompt
        )
    }

    if (askBackupPassword) {
        com.example.ledger.ui.BackupPasswordDialog(
            forRestore = false,
            onDone = { pw ->
                askBackupPassword = false
                backupPassword = pw
                (context as? MainActivity)?.pickingFile = true
                saveBackup.launch("WealthManager-backup-" + java.time.LocalDate.now() + ".wmb")
            },
            onDismiss = { askBackupPassword = false }
        )
    }
    restoreUri?.let { uri ->
        com.example.ledger.ui.BackupPasswordDialog(
            forRestore = true,
            onDone = { pw ->
                restoreUri = null
                viewModel.restore(uri, pw)
            },
            onDismiss = { restoreUri = null }
        )
    }

    proAsk?.let { (reason, ended) ->
        com.example.ledger.ui.ProDialog(
            p = pro,
            reason = reason,
            trialJustEnded = ended,
            onBuy = {
                val activity = context as? android.app.Activity
                if (activity == null || !app.pro.buy(activity)) {
                    Toast.makeText(context, "Google Play is not reachable right now. Try again when online.", Toast.LENGTH_LONG).show()
                }
                proAsk = null
            },
            onRestore = { app.pro.restore() },
            onDismiss = { proAsk = null }
        )
    }

    if (showProblem) {
        ProblemReportDialog(
            report = problem,
            onClear = {
                com.example.ledger.data.CrashLog.clear(context)
                problem = null
            },
            onDismiss = { showProblem = false }
        )
    }

    if (showUnread) {
        UnreadScreen(
            items = unread,
            stats = scanStats,
            scanning = scan is LedgerViewModel.ScanState.Running,
            onFullRescan = viewModel::fullRescan,
            onDismiss = { showUnread = false }
        )
    }

    if (showAddReminder) {
        AddReminderDialog(onSave = viewModel::addReminder, onDismiss = { showAddReminder = false })
    }

    if (showSort) {
        SortScreen(
            groups = toSort,
            categories = categories,
            accountNames = accounts.associate { it.id to it.name },
            onLearn = viewModel::learnMerchant,
            onSetOne = viewModel::setCategory,
            onOpen = {
                editing = it
                showEditor = true
            },
            onDismiss = { showSort = false }
        )
    }

    pendingLearn?.let { tx ->
        val categoryName = categories.firstOrNull { it.id == tx.categoryId }?.name ?: "this category"
        AlertDialog(
            onDismissRequest = { pendingLearn = null },
            title = { Text("Remember this?") },
            text = {
                Text(
                    "Put every " + (if (tx.kind == EntryKind.INCOME) "entry from " else "payment to ") +
                        (tx.merchant ?: "this merchant") + " under " + categoryName + ", now and in future?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val key = tx.merchantKey
                    val cat = tx.categoryId
                    if (key != null && cat != null) viewModel.learnMerchant(key, tx.kind, cat)
                    pendingLearn = null
                }) { Text("Every time") }
            },
            dismissButton = { TextButton(onClick = { pendingLearn = null }) { Text("Just this one") } }
        )
    }
}

