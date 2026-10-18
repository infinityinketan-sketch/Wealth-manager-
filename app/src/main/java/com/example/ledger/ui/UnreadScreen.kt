package com.example.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ledger.sms.SmsImporter
import java.time.Instant
import java.time.ZoneId

/** Hides long numbers (account, card, reference) before a message is copied to share. */
fun maskNumbers(text: String): String =
    text.replace(Regex("""\d{5,}""")) { m -> "X".repeat(m.value.length - 2) + m.value.takeLast(2) }

/**
 * Bank messages the app could not turn into a transaction or bill, with the reason. The person can
 * copy them (long numbers hidden) and send them so the reading rules can be improved.
 */
@Composable
fun UnreadScreen(
    items: List<SmsImporter.Unread>,
    stats: Triple<Int, Int, Int>?,
    scanning: Boolean,
    onFullRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Messages not understood", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        stats?.let { (read, found, _) ->
                            Text(
                                "Read %,d messages · %,d bank transactions found".format(read, found),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    "These look like bank messages but the app could not read them as a payment or a bill. " +
                        "Copy them (account and reference numbers are hidden) and send them, so the app learns these formats.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = items.isNotEmpty(),
                        onClick = {
                            val text = items.take(40).joinToString("\n\n") { u ->
                                "[" + u.sender + " · " + u.reason + "]\n" + maskNumbers(u.body)
                            }
                            clipboard.setText(AnnotatedString(text))
                            copied = true
                        }
                    ) { Text(if (copied) "Copied ✓" else "Copy all") }
                    OutlinedButton(enabled = !scanning, onClick = onFullRescan) {
                        Text(if (scanning) "Reading…" else "Read whole inbox again")
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (items.isEmpty()) {
                        item {
                            Text(
                                if (scanning) "Reading your messages…"
                                else "Nothing here. If you expected some, tap \"Read whole inbox again\".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(items) { u ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(u.sender, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(
                                    formatDate(Instant.ofEpochMilli(u.millis).atZone(ZoneId.systemDefault()).toLocalDate()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                u.reason,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            SelectionContainer {
                                Text(maskNumbers(u.body), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
