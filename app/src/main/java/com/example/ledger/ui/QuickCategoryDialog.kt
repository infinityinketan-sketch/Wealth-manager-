package com.example.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ledger.core.Money
import com.example.ledger.data.Category
import com.example.ledger.data.EntryKind
import com.example.ledger.data.LedgerTransaction
import com.example.ledger.sms.StarterCategories
import java.time.LocalDate

/** "What was this for?" — opened from the notification after a new payment or credit. */
@Composable
fun QuickCategoryDialog(
    t: LedgerTransaction,
    suggestions: List<Category>,
    categories: List<Category>,
    onPick: (categoryId: Long, rememberShop: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var rememberShop by remember { mutableStateOf(true) }
    val order = remember { StarterCategories.all.mapIndexed { i, s -> s.name to i }.toMap() }
    val all = categories.filter { it.kind == t.kind }.sortedBy { order[it.name] ?: 100 }
    val who = t.merchant?.lowercase()?.split(" ")?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    val isIncome = t.kind == EntryKind.INCOME
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(Money.formatInr(t.amountPaise) + (if (isIncome) " received" else " paid") +
                    (who?.let { (if (isIncome) " from " else " to ") + it } ?: ""))
                Text(
                    formatDate(LocalDate.ofEpochDay(t.epochDay)) + " · What was this for?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (suggestions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions, key = { it.id }) { c ->
                            AssistChip(
                                onClick = { onPick(c.id, rememberShop) },
                                label = { Text(c.name, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = { Icon(categoryIcon(c.name), contentDescription = null, tint = categoryColor(c)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = categoryColor(c).copy(alpha = 0.15f))
                            )
                        }
                    }
                }
                if (who != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberShop, onCheckedChange = { rememberShop = it })
                        Text("Remember for $who", style = MaterialTheme.typography.bodySmall)
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(all, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(c.id, rememberShop) }.padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CategoryBadge(c, size = 30.dp)
                            Text(c.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
    )
}
