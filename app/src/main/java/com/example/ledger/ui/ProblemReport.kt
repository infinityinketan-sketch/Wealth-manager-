package com.example.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/** Shows the saved details of the last crash so the person can copy and send them. */
@Composable
fun ProblemReportDialog(report: String?, onClear: () -> Unit, onDismiss: () -> Unit, onSend: (() -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (report != null) "The app had a problem" else "Problem report") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (report == null) {
                    Text("No problems recorded.")
                } else {
                    Text(
                        "Sorry about that. Send these details so it can be fixed. " +
                            "They contain no messages, amounts or account numbers.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SelectionContainer {
                        Text(report, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (report != null) {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy") }
                    if (onSend != null) androidx.compose.material3.Button(onClick = onSend) { Text("Send to developer") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (report != null) onClear(); onDismiss() }) { Text(if (report != null) "Done" else "Close") }
        }
    )
}
