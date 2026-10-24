package com.example.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Payments the app could not place: one group per merchant, or one entry when there is no name. */
data class SortGroup(
    val id: String,
    val merchantKey: String?,
    val title: String,
    val kind: EntryKind,
    val items: List<LedgerTransaction>,
    val total: Long
)

/**
 * Finds what still needs a category: entries in a catch-all category ("Other", "Sent to people" ...)
 * whose merchant the person has not already decided about. Biggest amounts first.
 */
fun sortGroups(transactions: List<LedgerTransaction>, categories: List<Category>, ruleKeys: Set<String>): List<SortGroup> {
    val fallbackIds = categories.filter { it.name in StarterCategories.fallbackNames }.map { it.id }.toSet()
    val otherIds = categories.filter { it.name == "Other" || it.name == "Other income" }.map { it.id }.toSet()
    val unsure = transactions.filter { it.categoryId == null || it.categoryId in fallbackIds }

    val named = unsure.filter { it.merchantKey != null && it.merchantKey !in ruleKeys }
        .groupBy { it.merchantKey!! to it.kind }
        .map { (k, list) ->
            SortGroup("m:${k.first}:${k.second}", k.first, titleCaseName(list.first().merchant ?: k.first), k.second, list, list.sumOf { it.amountPaise })
        }
    val unnamed = unsure.filter { it.merchantKey == null && (it.categoryId == null || it.categoryId in otherIds) }
        .map { t ->
            SortGroup("t:${t.id}", null, if (t.kind == EntryKind.INCOME) "Money received" else "Payment", t.kind, listOf(t), t.amountPaise)
        }
    return (named + unnamed).sortedByDescending { it.total }
}

private fun titleCaseName(s: String): String =
    s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }

@Composable
fun SortScreen(
    groups: List<SortGroup>,
    categories: List<Category>,
    accountNames: Map<Long, String>,
    onLearn: (merchantKey: String, kind: EntryKind, categoryId: Long) -> Unit,
    onSetOne: (LedgerTransaction, Long) -> Unit,
    onOpen: (LedgerTransaction) -> Unit,
    onDismiss: () -> Unit
) {
    val skipped = remember { mutableStateListOf<String>() }
    val order = remember { StarterCategories.all.mapIndexed { i, s -> s.name to i }.toMap() }
    val byKind = remember(categories) {
        EntryKind.values().associateWith { kind ->
            categories.filter { it.kind == kind }.sortedBy { order[it.name] ?: 100 }
        }
    }
    val visible = groups.filter { it.id !in skipped }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.safeDrawingPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sort your spending", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap a category. For a named shop it is remembered for every payment there.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (visible.isEmpty()) {
                    Text(
                        "All sorted for this period. 🎉",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visible, key = { it.id }) { g ->
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(g.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val first = g.items.first()
                                        val detail = if (g.items.size > 1) "${g.items.size} payments"
                                        else formatDate(LocalDate.ofEpochDay(first.epochDay))
                                        Text(
                                            detail + " · " + (accountNames[first.accountId] ?: ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        Money.formatInr(g.total),
                                        fontWeight = FontWeight.Bold,
                                        color = if (g.kind == EntryKind.INCOME) WealthColors.income else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(byKind[g.kind].orEmpty(), key = { it.id }) { c ->
                                        AssistChip(
                                            onClick = {
                                                val key = g.merchantKey
                                                if (key != null) onLearn(key, g.kind, c.id) else onSetOne(g.items.first(), c.id)
                                                skipped.add(g.id)
                                            },
                                            label = { Text(c.name) },
                                            leadingIcon = {
                                                Icon(categoryIcon(c.name), contentDescription = null, tint = categoryColor(c))
                                            },
                                            colors = AssistChipDefaults.assistChipColors(containerColor = categoryColor(c).copy(alpha = 0.12f))
                                        )
                                    }
                                }
                                Row {
                                    if (g.merchantKey == null) {
                                        TextButton(onClick = { onOpen(g.items.first()) }) { Text("See bank message") }
                                    }
                                    TextButton(onClick = { skipped.add(g.id) }) { Text("Skip") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
