package com.example.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ledger.billing.ProState
import com.example.ledger.core.Trial

/** What Pro adds. Everything else (SMS tracking, categories, balances, this/last month, restore) stays free. */
val PRO_FEATURES = listOf(
    "Any period: 3, 6, 12 months, this year, financial year or your own dates",
    "All charts: over time, trend and top merchants",
    "Bill dues and reminders, with alerts before the due date",
    "\"What was this for?\" after each payment",
    "Encrypted backup files"
)

private fun priceText(p: ProState) = p.price ?: "₹149"

/** Small lock shown on Pro-only chips and buttons once the trial is over. */
@Composable
fun ProLock() = Icon(Icons.Filled.Lock, contentDescription = "Pro", modifier = Modifier.size(16.dp))

/** Gentle note in the last 5 days of the trial. */
@Composable
fun TrialCountdown(p: ProState, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer).clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            (if (p.trialDaysLeft == 1) "Last day" else "${p.trialDaysLeft} days") + " of free Pro left. Keep it for " + priceText(p) + ", once.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        Text("See", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/**
 * The one place that asks for money. Polite, says exactly what is free, and can always be closed.
 * [reason] is what the person just tapped, or null when opened from More or at the end of the trial.
 */
@Composable
fun ProDialog(
    p: ProState,
    reason: String?,
    trialJustEnded: Boolean,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
        title = {
            Text(
                when {
                    p.purchased -> "You have Pro"
                    trialJustEnded -> "Your free month of Pro has ended"
                    p.trialDaysLeft > 0 -> "Pro is free for ${p.trialDaysLeft} more days"
                    else -> "Wealth Manager Pro"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reason != null && !p.isPro) Text("$reason is part of Pro.", fontWeight = FontWeight.SemiBold)
                if (trialJustEnded) {
                    Text("Thanks for trying it. Tracking your spending stays free, always; only the extras below need Pro.")
                }
                PRO_FEATURES.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                if (!p.purchased) {
                    Text(
                        "One payment of ${priceText(p)}. No subscription and no ads. " +
                            "Payment is handled by Google Play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!p.storeReady) {
                        Text(
                            "Google Play is not reachable right now (or this copy was not installed from Play). " +
                                "Connect to the internet and open the app again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (p.pending) Text("A payment is pending with Google Play.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (!p.purchased) Button(onClick = onBuy, enabled = p.storeReady && !p.pending) { Text("Get Pro · " + priceText(p)) }
            else TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            if (!p.purchased) Row {
                TextButton(onClick = onRestore) { Text("Restore") }
                TextButton(onClick = onDismiss) { Text(if (p.isPro) "Later" else "No thanks") }
            }
        }
    )
}

/** Pro status card on the More tab. */
@Composable
fun ProCard(p: ProState, onOpen: () -> Unit, onRestore: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color(0xFFF9A825))
                Text("Wealth Manager Pro", fontWeight = FontWeight.SemiBold)
            }
            Text(
                when {
                    p.purchased -> "Unlocked. Thank you for supporting the app!"
                    p.trialDaysLeft > 0 -> "Free trial: ${p.trialDaysLeft} of ${Trial.DAYS} days left. After that, one payment of ${priceText(p)} keeps Pro."
                    else -> "Trial ended. Tracking stays free; Pro extras are ${priceText(p)}, once."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!p.purchased) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) { Text("See Pro") }
                OutlinedButton(onClick = onRestore) { Text("Restore purchase") }
            }
        }
    }
}
