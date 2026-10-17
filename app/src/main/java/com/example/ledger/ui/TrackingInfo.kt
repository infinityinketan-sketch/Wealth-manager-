package com.example.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** What SMS tracking can and cannot see. Told to the person up front, not after they find gaps. */
object TrackingInfo {
    const val SHORT =
        "Some payments never send an SMS, so no SMS app can see them: small UPI payments (HDFC sends no SMS " +
            "up to ₹100), UPI Lite, cash and wallet payments. Add these with +."

    val points = listOf(
        "Small UPI payments" to "Some banks no longer send an SMS for small UPI payments (for example HDFC Bank, up to ₹100). " +
            "Other banks may follow.",
        "UPI Lite" to "UPI Lite payments come from a small wallet on your phone, so the bank sends no SMS for each one.",
        "Cash and wallets" to "Cash, and payments from wallets such as Paytm or Amazon Pay balance, do not reach your bank SMS.",
        "Shop names" to "Bank messages for QR payments often carry only a code, not the shop's name. These show as " +
            "\"Payment\" until you sort them once; the app then remembers the shop.",
        "How to fill the gaps" to "Tap + to add a payment in seconds. Import of Google Pay / PhonePe and bank statements " +
            "is coming, to fill in history automatically."
    )
}

@Composable
fun TrackingInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What the app can and cannot see") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Wealth Manager reads your bank's SMS. It sees every payment your bank sends a message for, " +
                        "and only those.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TrackingInfo.points.forEach { (title, text) ->
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}
