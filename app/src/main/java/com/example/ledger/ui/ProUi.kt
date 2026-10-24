package com.example.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

private fun priceText(p: ProState) = p.price?.let { "from $it" } ?: "a small payment"

/**
 * The ways to pay, as tappable cards (yearly is preselected when offered), plus the pay button.
 * Shows the subscription rules Google Play requires: price, period, renewal and how to cancel.
 */
@Composable
fun PlanPicker(p: ProState, onBuy: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (p.purchased) {
        Text(
            com.example.ledger.billing.ProStore.planName(p.plan) + " is active." +
                (if (p.subscriptionActive && !p.autoRenewing && !p.lifetime) " It will not renew; buy again when it ends." else ""),
            style = MaterialTheme.typography.bodySmall
        )
        if (p.subscriptionActive && !p.lifetime) {
            TextButton(onClick = {
                try {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.example.ledger.billing.ProStore.MANAGE_URL)))
                } catch (e: Exception) { }
            }) { Text("Manage or cancel in Google Play") }
        }
        return
    }
    if (p.plans.isEmpty()) {
        Text(
            if (p.storeReady) "Loading prices…" else "Connecting to Google Play… check your internet connection.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    var chosen by androidx.compose.runtime.saveable.rememberSaveable(p.plans.size) {
        androidx.compose.runtime.mutableStateOf((p.plans.firstOrNull { it.highlight } ?: p.plans.first()).id)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        p.plans.forEach { plan ->
            val selected = plan.id == chosen
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { chosen = plan.id }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.RadioButton(selected = selected, onClick = { chosen = plan.id })
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(plan.title, fontWeight = FontWeight.SemiBold)
                        if (plan.highlight) Text(
                            "Best value",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.secondary).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(plan.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(plan.price, fontWeight = FontWeight.Bold)
                    Text(plan.per, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val plan = p.plans.first { it.id == chosen }
        Button(
            onClick = { onBuy(plan.id) },
            enabled = !p.pending,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (plan.renews) "Subscribe · ${plan.price} ${plan.per}" else "Pay ${plan.price}") }
        Text(
            if (plan.renews) "Charged now, then every " + (if (plan.id == com.example.ledger.billing.ProStore.YEARLY) "year" else "month") +
                " until you cancel. Cancel any time in Google Play → Payments & subscriptions; you keep access until the end of the paid period."
            else "One payment through Google Play. Nothing is charged again.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
            (if (p.trialDaysLeft == 1) "Last day" else "${p.trialDaysLeft} days") + " of your free trial left. Plans " + priceText(p) + ".",
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
    onBuy: (String) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
        title = {
            Text(
                when {
                    p.purchased -> "You're unlocked"
                    trialJustEnded -> "Your free month has ended"
                    p.trialDaysLeft > 0 -> "Free for ${p.trialDaysLeft} more days"
                    else -> "Unlock Wealth Manager"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reason != null && !p.isPro) Text("$reason is part of Pro.", fontWeight = FontWeight.SemiBold)

                PRO_FEATURES.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                if (p.pending) Text("A payment is pending with Google Play.", style = MaterialTheme.typography.bodySmall)
                PlanPicker(p, onBuy)
            }
        },
        confirmButton = { if (p.purchased) TextButton(onClick = onDismiss) { Text("Close") } },
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
                Text("Your plan", fontWeight = FontWeight.SemiBold)
            }
            Text(
                when {
                    p.purchased -> com.example.ledger.billing.ProStore.planName(p.plan) + " active. Thank you for supporting the app!"
                    p.trialDaysLeft > 0 -> "Free trial: ${p.trialDaysLeft} of ${Trial.DAYS} days left. Then choose monthly, yearly, a 1-month or 1-year pass, or lifetime (${priceText(p)})."
                    else -> "Trial ended."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!p.purchased) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) { Text("See plans") }
                OutlinedButton(onClick = onRestore) { Text("Restore purchase") }
            } else if (p.subscriptionActive && !p.lifetime) PlanPicker(p) { }
        }
    }
}
