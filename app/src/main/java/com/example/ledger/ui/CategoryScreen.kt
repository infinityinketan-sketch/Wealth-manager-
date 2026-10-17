package com.example.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ledger.core.Money
import com.example.ledger.data.Category
import com.example.ledger.data.EntryKind
import com.example.ledger.data.LedgerTransaction
import com.example.ledger.sms.StarterCategories
import java.time.LocalDate

/**
 * Everything in one category for the chosen period. Tick entries (or Select all) and move them to
 * another category in one go; tap an entry to edit it on its own.
 */
@Composable
fun CategoryScreen(
    category: Category?,
    kind: EntryKind,
    periodLabel: String,
    items: List<LedgerTransaction>,
    categories: List<Category>,
    accountNames: Map<Long, String>,
    counted: Boolean,
    onCountedChange: (Boolean) -> Unit,
    onMove: (List<LedgerTransaction>, Long, Boolean) -> Unit,
    onOpen: (LedgerTransaction) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Long>() }
    var showPicker by remember { mutableStateOf(false) }

    val visible = items.filter { t ->
        query.isBlank() || (t.merchant ?: "").contains(query.trim(), ignoreCase = true) ||
            Money.formatPlain(t.amountPaise).startsWith(query.trim().removePrefix("₹")) ||
            t.note.contains(query.trim(), ignoreCase = true)
    }
    // Entries that left this category (moved) simply drop out of the selection.
    val chosen = items.filter { it.id in selected }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    CategoryBadge(category, size = 36.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(category?.name ?: "Uncategorised", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${items.size} entries · " + Money.formatInr(items.sumOf { it.amountPaise }) + " · " + periodLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (category != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Count in totals", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (counted) (if (kind == EntryKind.INCOME) "Counted as income" else "Counted as spending")
                                else "Left out: treated as your own money moving",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = counted, onCheckedChange = onCountedChange)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search shop, amount or note") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val allVisibleSelected = visible.isNotEmpty() && visible.all { it.id in selected }
                    Checkbox(
                        checked = allVisibleSelected,
                        onCheckedChange = { on ->
                            if (on) visible.forEach { if (it.id !in selected) selected.add(it.id) }
                            else selected.removeAll(visible.map { it.id }.toSet())
                        }
                    )
                    Text("Select all" + if (query.isNotBlank()) " shown" else "", modifier = Modifier.weight(1f))
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = { selected.clear() }) { Text("Clear") }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                if (items.isEmpty()) "Nothing left here. 🎉" else "No match for \"$query\".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    }
                    items(visible, key = { it.id }) { t ->
                        val isSel = t.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer
                                )
                                .clickable { onOpen(t) }
                                .padding(end = 14.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSel,
                                onCheckedChange = { on -> if (on) selected.add(t.id) else selected.remove(t.id) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    t.merchant?.lowercase()?.split(" ")?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                                        ?: (if (t.kind == EntryKind.INCOME) "Money received" else "Payment"),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatDate(LocalDate.ofEpochDay(t.epochDay)) + " · " + (accountNames[t.accountId] ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Text(
                                Money.formatInr(t.amountPaise),
                                fontWeight = FontWeight.SemiBold,
                                color = if (t.kind == EntryKind.INCOME) WealthColors.income else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (chosen.isNotEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${chosen.size} selected · " + Money.formatInr(chosen.sumOf { it.amountPaise }),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Button(onClick = { showPicker = true }) { Text("Move to…") }
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        MovePicker(
            kind = kind,
            current = category?.id,
            categories = categories,
            count = chosen.size,
            hasShops = chosen.any { it.merchantKey != null },
            onPick = { target, remember ->
                onMove(chosen, target, remember)
                selected.clear()
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun MovePicker(
    kind: EntryKind,
    current: Long?,
    categories: List<Category>,
    count: Int,
    hasShops: Boolean,
    onPick: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val order = remember { StarterCategories.all.mapIndexed { i, s -> s.name to i }.toMap() }
    val options = categories.filter { it.kind == kind && it.id != current }.sortedBy { order[it.name] ?: 100 }
    var rememberShops by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move $count to…") },
        text = {
            Column {
                if (hasShops) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberShops, onCheckedChange = { rememberShops = it })
                        Text("Remember for these shops (past and future payments)", style = MaterialTheme.typography.bodySmall)
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(options, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(c.id, hasShops && rememberShops) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CategoryBadge(c, size = 32.dp)
                            Text(c.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
