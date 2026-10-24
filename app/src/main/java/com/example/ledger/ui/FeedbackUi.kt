package com.example.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FEEDBACK_TYPES = listOf(
    "problem" to "Something's wrong",
    "category" to "Wrong category / missed payment",
    "idea" to "Idea",
    "praise" to "I like it",
    "other" to "Other"
)

/**
 * Feedback form. Sent to the developer's Firebase; long numbers and UPI ids are hidden before sending.
 * [crashReport] is offered as an attachment when the app recorded a crash.
 */
@Composable
fun FeedbackDialog(
    email: String?,
    crashReport: String?,
    attachCrashFirst: Boolean,
    onSend: (type: String, rating: Int?, message: String, wantsReply: Boolean, crash: String?, done: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(if (attachCrashFirst) "problem" else "idea") }
    var rating by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf("") }
    var wantsReply by remember { mutableStateOf(false) }
    var attachCrash by remember { mutableStateOf(attachCrashFirst && crashReport != null) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    if (sent) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Thank you!") },
            text = { Text(if (wantsReply) "We got it and will reply to $email." else "We got it. Every message is read.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Send feedback") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    FEEDBACK_TYPES.take(3).forEach { (id, label) -> FilterChip(selected = type == id, onClick = { type = id }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FEEDBACK_TYPES.drop(3).forEach { (id, label) -> FilterChip(selected = type == id, onClick = { type = id }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }) }
                }
                Text("How do you like the app? (optional)", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { n ->
                        IconButton(onClick = { rating = if (rating == n) null else n }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                if ((rating ?: 0) >= n) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "$n stars",
                                tint = if ((rating ?: 0) >= n) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 2000) message = it },
                    placeholder = { Text("Tell us what happened or what you'd like…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
                Text(
                    "Please don't paste OTPs, card or account numbers. Long numbers are hidden automatically before sending.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (email != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wantsReply, onCheckedChange = { wantsReply = it })
                    Text("You can reply to me at $email", style = MaterialTheme.typography.bodySmall)
                }
                if (crashReport != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = attachCrash, onCheckedChange = { attachCrash = it })
                    Text("Attach the last crash details (no messages or amounts)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (sending) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Button(
                onClick = {
                    sending = true
                    onSend(type, rating, message, wantsReply, if (attachCrash) crashReport else null) { ok ->
                        sending = false
                        if (ok) sent = true
                    }
                },
                enabled = message.isNotBlank() || rating != null || attachCrash
            ) { Text("Send") }
        },
        dismissButton = { if (!sending) TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
