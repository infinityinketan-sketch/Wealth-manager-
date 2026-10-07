package com.example.ledger

import android.os.Bundle
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
import androidx.compose.material3.Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLockEnabled = settings.appLockEnabled
        locked = appLockEnabled

        setContent {
            LedgerTheme {
                if (locked) {
                    LockedScreen(onUnlock = { authenticate() })
                } else {
                    LedgerRoot(
                        viewModel = viewModel,
                        appLockEnabled = appLockEnabled,
                        onAppLockChange = { setAppLock(it) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
    onAppLockChange: (Boolean) -> Unit
) {
    val period by viewModel.period.collectAsStateWithLifecycle()
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
                balances = balances,
                categories = categories,
                budgets = budgets,
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
            },
            onDelete = viewModel::deleteTransaction,
            onDismiss = { showEditor = false }
        )
    }
}
