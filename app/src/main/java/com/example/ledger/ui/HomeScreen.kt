package com.example.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ledger.core.DateRange
import com.example.ledger.core.Money
import com.example.ledger.data.Account
import com.example.ledger.data.AccountBalance
import com.example.ledger.data.Budget
import com.example.ledger.data.Category
import com.example.ledger.data.EntryKind
import com.example.ledger.data.LedgerTransaction
import java.time.LocalDate

@Composable
fun HomeScreen(
    period: DateRange,
    transactions: List<LedgerTransaction>,
    accounts: List<Account>,
    balances: List<AccountBalance>,
    categories: List<Category>,
    budgets: List<Budget>,
    smsGranted: Boolean,
    scanning: Boolean,
    lastImported: Int?,
    onEnableSms: () -> Unit,
    onScanAgain: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEdit: (LedgerTransaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val accountById = remember(accounts) { accounts.associateBy { it.id } }
    val totalBalance = remember(balances) { balances.sumOf { it.balancePaise } }
    val byDay = remember(transactions) { transactions.groupBy { it.epochDay }.toList() }
    val income = remember(transactions) { transactions.filter { it.kind == EntryKind.INCOME }.sumOf { it.amountPaise } }
    val expense = remember(transactions) { transactions.filter { it.kind == EntryKind.EXPENSE }.sumOf { it.amountPaise } }
    val spentByCategory = remember(transactions) {
        transactions.filter { it.kind == EntryKind.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amountPaise } }
    }
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val budgetByCategory = remember(budgets) { budgets.associateBy { it.categoryId } }
    val overallBudget = budgetByCategory[Budget.OVERALL_ID]

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPrevious) { Icon(Icons.Filled.ArrowBack, contentDescription = "Previous month") }
                Text(
                    text = formatShortDate(period.start) + " – " + formatDate(period.endInclusive),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onNext) { Icon(Icons.Filled.ArrowForward, contentDescription = "Next month") }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("You spent", style = MaterialTheme.typography.labelLarge)
                    Text(
                        Money.formatInr(expense),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Income", style = MaterialTheme.typography.labelMedium)
                            Text(Money.formatInr(income), fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Saved", style = MaterialTheme.typography.labelMedium)
                            Text(Money.formatInr(income - expense), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (overallBudget != null) {
                        BudgetBar(
                            label = "Monthly budget",
                            spentPaise = expense,
                            limitPaise = overallBudget.limitPaise
                        )
                    }
                }
            }
        }

        item {
            when {
                scanning -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Reading your bank messages…", style = MaterialTheme.typography.bodyMedium)
                }
                !smsGranted -> Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Track spending automatically", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Allow SMS access and Wealth Manager finds your bank and UPI transactions by itself. " +
                                "Messages are read on this phone only; nothing is uploaded.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onEnableSms) { Text("Allow and scan messages") }
                    }
                }
                lastImported != null -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (lastImported > 0) "Found $lastImported new transactions in your messages" else "Messages are up to date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onScanAgain) { Text("Rescan") }
                }
                else -> {}
            }
        }

        if (balances.isNotEmpty()) {
            item { SectionTitle("Accounts") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SummaryRow("Total balance", Money.formatInr(totalBalance), MaterialTheme.colorScheme.primary, bold = true)
                        balances.forEach { balance ->
                            SummaryRow(
                                label = balance.name + " · " + balance.type.label,
                                value = Money.formatInr(balance.balancePaise),
                                valueColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        val spendingRows = spentByCategory.keys.toMutableSet().apply {
            budgetByCategory.keys.filter { it != Budget.OVERALL_ID }.forEach { add(it) }
        }.toList().sortedByDescending { spentByCategory[it] ?: 0L }

        if (spendingRows.isNotEmpty()) {
            item { SectionTitle("Spending by category") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        spendingRows.forEach { categoryId ->
                            val category = categoryId?.let { categoryById[it] }
                            val limit = categoryId?.let { budgetByCategory[it]?.limitPaise }
                            BudgetBar(
                                label = category?.name ?: "Uncategorised",
                                spentPaise = spentByCategory[categoryId] ?: 0L,
                                limitPaise = limit,
                                dotColor = category?.let { Color(it.colorArgb) }
                            )
                        }
                    }
                }
            }
        }

        item { SectionTitle("Transactions") }
        if (transactions.isEmpty()) {
            item {
                Text(
                    if (smsGranted) "No transactions found for this month yet. Tap + to add one, or Rescan to read messages again."
                    else "Nothing in this month yet. Allow SMS access above, or tap + to add your first entry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        byDay.forEach { (day, list) ->
            item(key = "day$day") {
                val total = list.filter { it.kind == EntryKind.EXPENSE }.sumOf { it.amountPaise }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDate(LocalDate.ofEpochDay(day)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (total > 0) {
                        Text(
                            Money.formatInr(total),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(list, key = { it.id }) { transaction ->
                val category = transaction.categoryId?.let { categoryById[it] }
                ListItem(
                    headlineContent = {
                        Text(
                            transaction.merchant?.lowercase()?.split(" ")
                                ?.joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
                                ?: category?.name ?: "Uncategorised"
                        )
                    },
                    supportingContent = {
                        val account = accountById[transaction.accountId]?.name
                        val parts = listOfNotNull(
                            category?.name ?: "Uncategorised",
                            account,
                            transaction.note.takeIf { it.isNotBlank() }
                        )
                        Text(parts.joinToString(" · "))
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(category?.let { Color(it.colorArgb) } ?: Color.Gray, CircleShape)
                        )
                    },
                    trailingContent = {
                        val sign = if (transaction.kind == EntryKind.INCOME) "+" else "-"
                        Text(
                            text = sign + Money.formatInr(transaction.amountPaise),
                            color = amountColor(transaction.kind),
                            fontWeight = FontWeight.Medium
                        )
                    },
                    modifier = Modifier.clickable { onEdit(transaction) }
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        Text(value, color = valueColor, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** A label with the amount spent and, when a limit is set, a progress bar that turns red when exceeded. */
@Composable
private fun BudgetBar(label: String, spentPaise: Long, limitPaise: Long?, dotColor: Color? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dotColor != null) {
                    Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
                }
                Text(label)
            }
            val amountText = if (limitPaise != null) {
                Money.formatInr(spentPaise) + " of " + Money.formatInr(limitPaise)
            } else {
                Money.formatInr(spentPaise)
            }
            Text(amountText, style = MaterialTheme.typography.bodyMedium)
        }
        if (limitPaise != null && limitPaise > 0) {
            val fraction = (spentPaise.toDouble() / limitPaise.toDouble()).coerceIn(0.0, 1.0).toFloat()
            val over = spentPaise > limitPaise
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}
