package com.example.ledger.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ledger.account.AccountState
import com.example.ledger.billing.ProState

const val PRIVACY_URL = "https://infinityinketan-sketch.github.io/Wealth-manager-/privacy-policy.html"

@Composable
private fun PolicyLink(text: String = "Privacy policy") {
    val context = LocalContext.current
    TextButton(onClick = {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) } catch (e: Exception) { }
    }) { Text(text) }
}

/**
 * Sign in with Google, with consent asked the way India's DPDP Act expects: it says what is kept and
 * why, account details are needed for the account itself, and usage sharing is a separate choice.
 */
@Composable
fun SignInScreen(state: AccountState, isDebugBuild: Boolean, onSignIn: (usageConsent: Boolean) -> Unit, onSkipForTest: () -> Unit) {
    var agree by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Sign in to start your free month", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(
                "Your Google account holds your 30-day free trial and your Pro purchase.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What we keep on our server", fontWeight = FontWeight.SemiBold)
                    Text("• Your Google name and email: to run your account and trial", style = MaterialTheme.typography.bodySmall)
                    Text("• Sign-up and last-open dates, app and Android version, phone model, Pro status: to run the trial and support you", style = MaterialTheme.typography.bodySmall)
                    Text("What never leaves your phone", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Text("• Your SMS, amounts, balances, shops, categories and bank details", style = MaterialTheme.typography.bodySmall)
                    Text("Not sold or shared. Delete your account any time from More.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = agree, onCheckedChange = { agree = it })
                Text("I agree to the account details above being kept as described in the privacy policy.", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = usage, onCheckedChange = { usage = it })
                Text("Optional: also share which features I use (counts only, no money data) to help improve the app.", style = MaterialTheme.typography.bodySmall)
            }
            PolicyLink("Read the privacy policy")
            if (state.busy) CircularProgressIndicator()
            else Button(
                onClick = { onSignIn(usage) },
                enabled = agree && state.configured,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Continue with Google") }
            if (!state.configured) {
                Text(
                    "Google sign-in is not set up in this build yet (see RELEASE.md, Firebase step).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center
                )
            }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            if (isDebugBuild && !state.configured) TextButton(onClick = onSkipForTest) { Text("Skip (test build only)") }
        }
    }
}

/** Shown instead of the whole app once the free month is over and Pro was not bought. */
@Composable
fun TrialEndedScreen(
    p: ProState,
    account: AccountState,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(WealthColors.heroGradient),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            Text("Your free month has ended", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(
                "Thank you for trying Wealth Manager. To keep using it, unlock it once. Your data is safe on this phone and " +
                    "the app keeps noting new bank messages, so nothing is missed when you come back.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("One payment, everything included", fontWeight = FontWeight.SemiBold)
                    (listOf("Automatic tracking from bank, UPI and card SMS", "Per-account balances, budgets and categories") + PRO_FEATURES)
                        .forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    Text("No subscription, no ads. Payment by Google Play.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (p.pending) Text("A payment is pending with Google Play. The app unlocks as soon as it is confirmed.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onBuy,
                enabled = p.storeReady && !p.pending,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Unlock for " + (p.price ?: "₹149")) }
            if (!p.storeReady) {
                Text("Connecting to Google Play… check your internet connection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("I already paid: restore purchase") }
            Spacer(Modifier.height(8.dp))
            account.email?.let { Text("Signed in as $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row {
                TextButton(onClick = onSignOut) { Text("Sign out") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete account") }
                PolicyLink()
            }
        }
    }
    if (confirmDelete) DeleteAccountDialog(onConfirm = { confirmDelete = false; onDeleteAccount() }, onDismiss = { confirmDelete = false })
}

@Composable
fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete your account?") },
        text = {
            Text(
                "This deletes your name, email, device and usage details from our server, and signs you out. " +
                    "A one-way code of your email is kept only so the free trial cannot be used again. " +
                    "Your transactions on this phone are not touched; uninstall the app to remove them. " +
                    "A Pro purchase stays with your Google Play account."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Account card on the More tab. */
@Composable
fun AccountCard(
    account: AccountState,
    usageConsent: Boolean,
    onUsageConsent: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.name ?: "Your account", fontWeight = FontWeight.SemiBold)
                    account.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Share feature use", style = MaterialTheme.typography.bodyMedium)
                    Text("Counts only, never money data. Turning off also deletes counts already sent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = usageConsent, onCheckedChange = onUsageConsent)
            }
            Row {
                TextButton(onClick = onSignOut) { Text("Sign out") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
                PolicyLink()
            }
        }
    }
    if (confirmDelete) DeleteAccountDialog(onConfirm = { confirmDelete = false; onDeleteAccount() }, onDismiss = { confirmDelete = false })
}
