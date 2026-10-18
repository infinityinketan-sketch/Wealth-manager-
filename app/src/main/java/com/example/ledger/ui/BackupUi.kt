package com.example.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId

@Composable
fun BackupCard(lastBackupMillis: Long, onBackup: () -> Unit, onRestore: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backup & restore", fontWeight = FontWeight.SemiBold)
            Text(
                if (lastBackupMillis > 0) "Last backup: " + formatDate(Instant.ofEpochMilli(lastBackupMillis).atZone(ZoneId.systemDefault()).toLocalDate())
                else "No backup yet. Your data is only on this phone: back it up before changing phones or reinstalling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "One file, locked with your password. Save it to Google Drive or anywhere you like. " +
                    "Without the password nobody (not even us) can open it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBackup) { Text("Back up now") }
                OutlinedButton(onClick = onRestore) { Text("Restore") }
            }
        }
    }
}

/** Password for a backup (asked twice) or for a restore (asked once, with a warning). */
@Composable
fun BackupPasswordDialog(forRestore: Boolean, onDone: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (forRestore) "Restore backup" else "Choose a backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (forRestore) "This replaces the data on this phone with the backup. Enter the password used when it was made."
                    else "You will need this password to restore. It cannot be recovered if forgotten, so write it down somewhere safe.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it; error = null },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (!forRestore) {
                    OutlinedTextField(
                        value = again,
                        onValueChange = { again = it; error = null },
                        label = { Text("Same password again") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = error != null,
                        supportingText = { error?.let { Text(it) } }
                    )
                } else {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pw.length < 6 -> error = "Use at least 6 characters"
                    !forRestore && pw != again -> error = "The two passwords are different"
                    else -> onDone(pw.toCharArray())
                }
            }) { Text(if (forRestore) "Restore" else "Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
