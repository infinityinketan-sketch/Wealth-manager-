package com.example.ledger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

    // BIOMETRIC_STRONG + DEVICE_CREDENTIAL is not supported on Android 9 and 10, so use WEAK.
    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private val settings get() = (application as LedgerApp).settings

    private var smsGranted by mutableStateOf(false)
    private var smsPromptDone by mutableStateOf(false)

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
        smsPermissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLockEnabled = settings.appLockEnabled
        locked = appLockEnabled
        smsGranted = hasSmsPermission()
        smsPromptDone = settings.smsPromptDone

        setContent {
            LedgerTheme {
                if (locked) {
                    LockedScreen(onUnlock = { authenticate() })
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
                        onEnableSms = { requestSms() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        smsGranted = hasSmsPermission()
        // Pick up anything that arrived while the app was closed.
        if (smsGranted && !locked) viewModel.scanSms()
        if (locked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        // The system credential screen can briefly stop this activity; do not re-lock during that.
        if (appLockEnabled && !authInProgress) locked = true
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
            modifier = Modifier.fillMaxSize().padding(28.dp),
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
                    "Your messages are read on this phone only. This app has no internet access, so nothing is uploaded, ever. " +
                    "Only bank and payment messages are used; personal chats are ignored.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 28.dp)
            )
            Button(onClick = onAllow) { Text("Allow and scan my messages") }
            TextButton(onClick = onSkip) { Text("Not now, I will add entries myself") }
        }
    }
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
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
    onEnableSms: () -> Unit
) {
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    var pendingLearn by remember { mutableStateOf<LedgerTransaction?>(null) }
    val period by viewModel.period.collectAsStateWithLifecycle()
    val periodChoice by viewModel.periodChoice.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()
    val startDay by viewModel.startDay.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerTransaction?>(null) }

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
                lastImported = (scan as? LedgerViewModel.ScanState.Done)?.imported,
                onEnableSms = onEnableSms,
                onScanAgain = viewModel::scanSms,
                preset = periodChoice.preset,
                onPreset = viewModel::choosePreset,
                onCustom = viewModel::chooseCustom,
                onPrevious = viewModel::previousPeriod,
                onNext = viewModel::nextPeriod,
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

