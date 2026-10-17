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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ledger.core.DateRange
import com.example.ledger.core.Money
import com.example.ledger.data.Account
import com.example.ledger.data.AccountBalance
import com.example.ledger.data.AccountType
import com.example.ledger.data.Budget
import com.example.ledger.data.Category
import com.example.ledger.data.EntryKind
import com.example.ledger.data.LedgerTransaction
import com.example.ledger.sms.StarterCategories
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    // Tapping an account shows only that account's money; tapping it again shows everything.
    var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }

    val accountById = remember(accounts) { accounts.associateBy { it.id } }
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val excludedIds = remember(categories) {
        categories.filter { it.name in StarterCategories.excludedNames }.mapTo(HashSet<Long?>()) { it.id }
    }
    val shown = remember(transactions, selectedAccountId) {
        if (selectedAccountId == null) transactions else transactions.filter { it.accountId == selectedAccountId }
    }
    val counted = remember(shown, excludedIds) { shown.filter { it.categoryId !in excludedIds } }
    val income = counted.filter { it.kind == EntryKind.INCOME }.sumOf { it.amountPaise }
    val expense = counted.filter { it.kind == EntryKind.EXPENSE }.sumOf { it.amountPaise }
    val moved = shown.filter { it.categoryId in excludedIds && it.kind == EntryKind.EXPENSE }.sumOf { it.amountPaise }
    val spentByCategory = remember(counted) {
        counted.filter { it.kind == EntryKind.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amountPaise } }
    }
    val budgetByCategory = remember(budgets) { budgets.associateBy { it.categoryId } }
    val overallBudget = budgetByCategory[Budget.OVERALL_ID]
    val byDay = remember(shown) { shown.groupBy { it.epochDay }.toList() }

    // Balances: a bank account counts only when its bank has told us the balance (or it is manual).
    val visibleBalances = balances.filter { b ->
        !(b.type == AccountType.CASH && b.balancePaise == 0L && transactions.none { it.accountId == b.accountId })
    }
    val bankBalances = visibleBalances.filter { it.type == AccountType.BANK || it.type == AccountType.CASH }
    val cards = visibleBalances.filter { it.type == AccountType.CARD || it.type == AccountType.UPI }
    val loans = visibleBalances.filter { it.type == AccountType.LOAN }
    val totalKnown = bankBalances.mapNotNull { knownBalance(it) }.sum()
    val unknownCount = bankBalances.count { knownBalance(it) == null }

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

        selectedAccountId?.let { id ->
            item {
                AssistChip(
                    onClick = { selectedAccountId = null },
                    label = { Text("Showing " + (accountById[id]?.name ?: "one account") + "  ·  tap to show all") }
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("You spent", style = MaterialTheme.typography.labelLarge)
                    Text(Money.formatInr(expense), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
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
                    if (moved > 0) {
                        Text(
                            "Not counted as spending: " + Money.formatInr(moved) + " (FD, investments, card bills, own transfers)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (overallBudget != null && selectedAccountId == null) {
                        BudgetBar(label = "Monthly budget", spentPaise = expense, limitPaise = overallBudget.limitPaise)
                    }
                }
            }
        }

        item {
            when {
                scanning -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    OutlinedButton(onClick = onScanAgain) { Text("Rescan") }
                }
                else -> {}
            }
        }

        if (bankBalances.isNotEmpty()) {
            item { SectionTitle("Bank accounts") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total balance", fontWeight = FontWeight.SemiBold)
                            Text(Money.formatInr(totalKnown), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        if (unknownCount > 0) {
                            Text(
                                "$unknownCount account(s) not included: their bank messages do not show a balance yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        bankBalances.forEach { b ->
                            AccountRow(
                                balance = b,
                                selected = selectedAccountId == b.accountId,
                                valueText = knownBalance(b)?.let { Money.formatInr(it) } ?: "Balance not known",
                                detail = balanceDetail(b),
                                onClick = { selectedAccountId = if (selectedAccountId == b.accountId) null else b.accountId }
                            )
                        }
                    }
                }
            }
        }

        if (cards.isNotEmpty() || loans.isNotEmpty()) {
            item { SectionTitle("Cards and loans") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        (cards + loans).forEach { b ->
                            val monthSpend = transactions.filter { it.accountId == b.accountId && it.kind == EntryKind.EXPENSE }
                                .sumOf { it.amountPaise }
                            AccountRow(
                                balance = b,
                                selected = selectedAccountId == b.accountId,
                                valueText = Money.formatInr(monthSpend),
                                detail = if (b.type == AccountType.LOAN) "Paid this month" else "Spent this month",
                                onClick = { selectedAccountId = if (selectedAccountId == b.accountId) null else b.accountId }
                            )
                        }
                    }
                }
            }
        }

        val spendingRows = spentByCategory.keys.toMutableSet().apply {
            if (selectedAccountId == null) budgetByCategory.keys.filter { it != Budget.OVERALL_ID }.forEach { add(it) }
        }.toList().sortedByDescending { spentByCategory[it] ?: 0L }

        if (spendingRows.isNotEmpty()) {
            item { SectionTitle("Spending by category") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        spendingRows.forEach { categoryId ->
                            val category = categoryId?.let { categoryById[it] }
                            BudgetBar(
                                label = category?.name ?: "Uncategorised",
                                spentPaise = spentByCategory[categoryId] ?: 0L,
                                limitPaise = categoryId?.let { budgetByCategory[it]?.limitPaise },
                                dotColor = category?.let { Color(it.colorArgb) }
                            )
                        }
                    }
                }
            }
        }

        item { SectionTitle("Transactions") }
        if (shown.isEmpty()) {
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
                val total = list.filter { it.kind == EntryKind.EXPENSE && it.categoryId !in excludedIds }.sumOf { it.amountPaise }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatDate(LocalDate.ofEpochDay(day)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (total > 0) {
                        Text(Money.formatInr(total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(list, key = { it.id }) { transaction ->
                val category = transaction.categoryId?.let { categoryById[it] }
                val excluded = transaction.categoryId in excludedIds
                ListItem(
                    headlineContent = {
                        Text(
                            transaction.merchant?.let { titleCase(it) } ?: category?.name ?: "Uncategorised",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        val parts = listOfNotNull(
                            category?.name ?: "Uncategorised",
                            accountById[transaction.accountId]?.name,
                            transaction.note.takeIf { it.isNotBlank() }
                        )
                        Text(parts.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                            color = if (excluded) MaterialTheme.colorScheme.onSurfaceVariant else amountColor(transaction.kind),
                            fontWeight = FontWeight.Medium
                        )
                    },
                    modifier = Modifier.clickable { onEdit(transaction) }
                )
            }
        }
    }
}

/** The balance to show: what the bank last reported, or for manual accounts the computed one. */
private fun knownBalance(b: AccountBalance): Long? =
    if (b.smsKey == null) b.balancePaise else b.latestBalancePaise

private fun balanceDetail(b: AccountBalance): String = when {
    b.smsKey == null -> b.type.label
    b.latestBalancePaise != null && b.latestBalanceMillis > 0 ->
        "As per bank SMS on " + formatDate(Instant.ofEpochMilli(b.latestBalanceMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    else -> "Bank SMS did not include a balance"
}

private fun titleCase(s: String): String =
    s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }

@Composable
private fun AccountRow(balance: AccountBalance, selected: Boolean, valueText: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(balance.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(valueText, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
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
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (spentPaise > limitPaise) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}
