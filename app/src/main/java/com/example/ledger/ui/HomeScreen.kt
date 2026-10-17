package com.example.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ledger.core.Buckets
import com.example.ledger.core.DateRange
import com.example.ledger.core.Money
import com.example.ledger.core.PeriodPreset
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
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private enum class ChartKind(val label: String) { CATEGORIES("Categories"), OVER_TIME("Over time"), TREND("Trend"), MERCHANTS("Top merchants") }

@Composable
fun HomeScreen(
    period: DateRange,
    preset: PeriodPreset,
    toSortCount: Int,
    toSortTotal: Long,
    onSort: () -> Unit,
    transactions: List<LedgerTransaction>,
    accounts: List<Account>,
    balances: List<AccountBalance>,
    categories: List<Category>,
    budgets: List<Budget>,
    smsGranted: Boolean,
    scanning: Boolean,
    scanProgress: Pair<Int, Int>?,
    dues: List<com.example.ledger.data.Due>,
    suggestions: List<com.example.ledger.core.RepeatingPayment>,
    onDuePaid: (com.example.ledger.data.Due) -> Unit,
    onDueDismiss: (com.example.ledger.data.Due) -> Unit,
    onAcceptSuggestion: (com.example.ledger.core.RepeatingPayment) -> Unit,
    onDeclineSuggestion: (com.example.ledger.core.RepeatingPayment) -> Unit,
    onAddReminder: () -> Unit,
    lastImported: Int?,
    onEnableSms: () -> Unit,
    onScanAgain: () -> Unit,
    onPreset: (PeriodPreset) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEdit: (LedgerTransaction) -> Unit,
    onOpenCategory: (categoryId: Long?, kind: EntryKind, accountId: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Tapping an account shows only that account's money; tapping it again shows everything.
    var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var chart by rememberSaveable { mutableStateOf(ChartKind.CATEGORIES) }
    var showRangePicker by remember { mutableStateOf(false) }

    val accountById = remember(accounts) { accounts.associateBy { it.id } }
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val excludedIds = remember(categories) {
        categories.filter { it.name in StarterCategories.excludedNames }.mapTo(HashSet<Long?>()) { it.id }
    }
    val shown = remember(transactions, selectedAccountId) {
        if (selectedAccountId == null) transactions else transactions.filter { it.accountId == selectedAccountId }
    }
    val counted = remember(shown, excludedIds) { shown.filter { it.categoryId !in excludedIds } }
    val expenses = remember(counted) { counted.filter { it.kind == EntryKind.EXPENSE } }
    val incomes = remember(counted) { counted.filter { it.kind == EntryKind.INCOME } }
    val income = incomes.sumOf { it.amountPaise }
    val expense = expenses.sumOf { it.amountPaise }
    val moved = shown.filter { it.categoryId in excludedIds && it.kind == EntryKind.EXPENSE }.sumOf { it.amountPaise }
    val spentByCategory = remember(expenses) {
        expenses.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amountPaise } }
            .toList().sortedByDescending { it.second }
    }
    val incomeByCategory = remember(incomes) {
        incomes.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amountPaise } }
            .toList().sortedByDescending { it.second }
    }
    val budgetByCategory = remember(budgets) { budgets.associateBy { it.categoryId } }
    val monthly = preset == PeriodPreset.THIS_MONTH || preset == PeriodPreset.LAST_MONTH
    val overallBudget = if (monthly) budgetByCategory[Budget.OVERALL_ID] else null
    val byDay = remember(shown) { shown.groupBy { it.epochDay }.toList() }

    val today = LocalDate.now()
    val daysSoFar = (ChronoUnit.DAYS.between(period.start, minOf(today, period.endInclusive)) + 1).coerceAtLeast(1)
    val perDay = if (today.isBefore(period.start)) 0L else expense / daysSoFar

    // Chart data for the chosen period.
    val buckets = remember(period) { Buckets.split(period) }
    val bucketExpense = remember(buckets, expenses) { sumByBucket(buckets, expenses) }
    val bucketIncome = remember(buckets, incomes) { sumByBucket(buckets, incomes) }
    val slices = remember(spentByCategory, categoryById) {
        val top = spentByCategory.take(7).map { (id, v) ->
            val c = id?.let { categoryById[it] }
            ChartSlice(c?.name ?: "Uncategorised", v, categoryColor(c), c)
        }
        val rest = spentByCategory.drop(7).sumOf { it.second }
        if (rest > 0) top + ChartSlice("Everything else", rest, Color(0xFF90A4AE), null, isRest = true) else top
    }
    val merchants = remember(expenses, categoryById) {
        expenses.filter { it.merchant != null }
            .groupBy { titleCase(it.merchant!!) }
            .map { (name, list) -> Triple(name, list.sumOf { it.amountPaise }, list.first().categoryId?.let { categoryById[it] }) }
            .sortedByDescending { it.second }
            .take(8)
    }

    val visibleBalances = balances.filter { b ->
        !(b.type == AccountType.CASH && b.balancePaise == 0L && transactions.none { it.accountId == b.accountId })
    }
    val bankBalances = visibleBalances.filter { it.type == AccountType.BANK || it.type == AccountType.CASH }
    val cardsAndLoans = visibleBalances.filter { it.type == AccountType.CARD || it.type == AccountType.UPI || it.type == AccountType.LOAN }
    val totalKnown = bankBalances.mapNotNull { knownBalance(it) }.sum()
    val unknownCount = bankBalances.count { knownBalance(it) == null }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- period picker ----
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Earlier") }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rangeLabel(period), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${ChronoUnit.DAYS.between(period.start, period.endInclusive) + 1} days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = "Later") }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PeriodPreset.values().toList()) { p ->
                    FilterChip(
                        selected = preset == p,
                        onClick = { if (p == PeriodPreset.CUSTOM) showRangePicker = true else onPreset(p) },
                        label = { Text(p.label) },
                        leadingIcon = if (p == PeriodPreset.CUSTOM) {
                            { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        selectedAccountId?.let { id ->
            item {
                FilterChip(
                    selected = true,
                    onClick = { selectedAccountId = null },
                    label = { Text("Only " + (accountById[id]?.name ?: "one account") + "  ✕") }
                )
            }
        }

        // ---- hero summary ----
        item {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(WealthColors.heroGradient)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("You spent", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                    Text(
                        Money.formatInr(expense),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "About " + Money.formatInr(perDay) + " a day",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Whole rupees here so the amounts fit the tiles.
                        HeroTile(Icons.Filled.ArrowDownward, "Income", Money.formatInr(income / 100 * 100), Modifier.weight(1f))
                        HeroTile(Icons.Filled.Savings, "Saved", Money.formatInr((income - expense) / 100 * 100), Modifier.weight(1f))
                    }
                    if (moved > 0) {
                        Text(
                            "Not counted as spending: " + Money.formatInr(moved) + " (FD, investments, card bills, own transfers)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    if (overallBudget != null && selectedAccountId == null) {
                        val f = (expense.toDouble() / overallBudget.limitPaise).coerceIn(0.0, 1.0).toFloat()
                        Text(
                            "Budget: " + Money.formatInr(expense) + " of " + Money.formatInr(overallBudget.limitPaise),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        LinearProgressIndicator(
                            progress = { f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (expense > overallBudget.limitPaise) Color(0xFFFFD54F) else Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }

        // ---- things to sort ----
        if (toSortCount > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .clickable(onClick = onSort)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Category, contentDescription = null, tint = Color.White) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "$toSortCount payments need a category",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            Money.formatInr(toSortTotal) + " in this period · takes a minute, the app remembers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Button(onClick = onSort) { Text("Sort") }
                }
            }
        }

        // ---- SMS status ----
        item {
            when {
                scanning -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (done, total) = scanProgress ?: (0 to 0)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            if (total > 0) "Reading your messages $done/$total" else "Reading your messages…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                !smsGranted -> SoftCard {
                    Text("Track spending automatically", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Allow SMS access and Wealth Manager finds your bank and UPI transactions by itself. " +
                            "Messages are read on this phone only; nothing is uploaded.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onEnableSms) { Text("Allow and scan messages") }
                }
                lastImported != null -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
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

        // ---- dues & reminders ----
        if (selectedAccountId == null) {
            item {
                DuesSection(
                    dues = dues,
                    suggestions = suggestions,
                    onPaid = onDuePaid,
                    onDismiss = onDueDismiss,
                    onAccept = onAcceptSuggestion,
                    onDecline = onDeclineSuggestion,
                    onAdd = onAddReminder
                )
            }
        }

        // ---- charts ----
        item {
            SoftCard {
                Text("Where your money went", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ChartKind.values().toList()) { k ->
                        FilterChip(
                            selected = chart == k,
                            onClick = { chart = k },
                            label = { Text(k.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
                if (expense == 0L && income == 0L) {
                    Text(
                        "Nothing to chart in this period yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else when (chart) {
                    ChartKind.CATEGORIES -> DonutChart(slices, expense, onSliceClick = { sl -> onOpenCategory(sl.category?.id, EntryKind.EXPENSE, selectedAccountId) })
                    ChartKind.OVER_TIME -> BarChart(buckets.map { it.label }, bucketExpense, bucketIncome)
                    ChartKind.TREND -> TrendChart(buckets.map { it.label }, bucketExpense, bucketIncome)
                    ChartKind.MERCHANTS -> TopMerchants(merchants)
                }
            }
        }

        // ---- accounts ----
        if (bankBalances.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    SectionTitle("Bank accounts")
                    Text(
                        "  Total " + Money.formatInr(totalKnown),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (unknownCount > 0) {
                    Text(
                        "$unknownCount account(s) not in the total: their bank messages do not show a balance yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(bankBalances, key = { it.accountId }) { b ->
                        AccountCard(
                            balance = b,
                            index = bankBalances.indexOf(b),
                            selected = selectedAccountId == b.accountId,
                            value = knownBalance(b)?.let { Money.formatInr(it) } ?: "Balance not known",
                            detail = balanceDetail(b),
                            onClick = { selectedAccountId = if (selectedAccountId == b.accountId) null else b.accountId }
                        )
                    }
                }
            }
        }
        if (cardsAndLoans.isNotEmpty()) {
            item { SectionTitle("Cards and loans") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(cardsAndLoans, key = { it.accountId }) { b ->
                        val spent = transactions.filter { it.accountId == b.accountId && it.kind == EntryKind.EXPENSE }.sumOf { it.amountPaise }
                        AccountCard(
                            balance = b,
                            index = cardsAndLoans.indexOf(b) + 2,
                            selected = selectedAccountId == b.accountId,
                            value = Money.formatInr(spent),
                            detail = if (b.type == AccountType.LOAN) "Paid in this period" else "Spent in this period",
                            onClick = { selectedAccountId = if (selectedAccountId == b.accountId) null else b.accountId }
                        )
                    }
                }
            }
        }

        // ---- categories ----
        if (spentByCategory.isNotEmpty()) {
            item { SectionTitle("Spending by category") }
            item {
                SoftCard {
                    val maxCat = spentByCategory.first().second
                    spentByCategory.forEach { (id, value) ->
                        val c = id?.let { categoryById[it] }
                        val limit = if (monthly) id?.let { budgetByCategory[it]?.limitPaise } else null
                        CategoryRow(c, value, maxCat, limit, if (expense > 0) value * 100 / expense else 0L) {
                            onOpenCategory(id, EntryKind.EXPENSE, selectedAccountId)
                        }
                    }
                    Text(
                        "Tap a category to see its payments and move them.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (incomeByCategory.isNotEmpty()) {
            item { SectionTitle("Income by category") }
            item {
                SoftCard {
                    val maxInc = incomeByCategory.first().second
                    incomeByCategory.forEach { (id, value) ->
                        val c = id?.let { categoryById[it] }
                        CategoryRow(c, value, maxInc, null, if (income > 0) value * 100 / income else 0L, ofWhat = "income") {
                            onOpenCategory(id, EntryKind.INCOME, selectedAccountId)
                        }
                    }
                }
            }
        }

        // ---- transactions ----
        item { SectionTitle("Transactions") }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (smsGranted) "No transactions in this period. Tap + to add one, or pick another period above."
                    else "Nothing here yet. Allow SMS access above, or tap + to add your first entry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        byDay.forEach { (day, list) ->
            item(key = "day$day") {
                val total = list.filter { it.kind == EntryKind.EXPENSE && it.categoryId !in excludedIds }.sumOf { it.amountPaise }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatDate(LocalDate.ofEpochDay(day)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (total > 0) {
                        Text(Money.formatInr(total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(list, key = { it.id }) { t ->
                TransactionRow(
                    t = t,
                    category = t.categoryId?.let { categoryById[it] },
                    accountName = accountById[t.accountId]?.name,
                    excluded = t.categoryId in excludedIds,
                    onClick = { onEdit(t) }
                )
            }
        }
    }

    if (showRangePicker) {
        RangePickerDialog(
            initial = period,
            onPick = { a, b -> onCustom(a, b) },
            onDismiss = { showRangePicker = false }
        )
    }
}

private fun sumByBucket(buckets: List<com.example.ledger.core.Bucket>, list: List<LedgerTransaction>): List<Long> {
    val out = LongArray(buckets.size)
    for (t in list) {
        val i = Buckets.indexOf(buckets, LocalDate.ofEpochDay(t.epochDay))
        if (i >= 0) out[i] += t.amountPaise
    }
    return out.toList()
}

private fun rangeLabel(r: DateRange): String =
    if (r.start.year == r.endInclusive.year) formatShortDate(r.start) + " – " + formatDate(r.endInclusive)
    else formatDate(r.start) + " – " + formatDate(r.endInclusive)

/** The balance to show: what the bank last reported, or for manual accounts the computed one. */
private fun knownBalance(b: AccountBalance): Long? = if (b.smsKey == null) b.balancePaise else b.latestBalancePaise

private fun balanceDetail(b: AccountBalance): String = when {
    b.smsKey == null -> b.type.label
    b.latestBalancePaise != null && b.latestBalanceMillis > 0 ->
        "As of " + formatDate(Instant.ofEpochMilli(b.latestBalanceMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    else -> "SMS shows no balance"
}

private fun titleCase(s: String): String =
    s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }

@Composable
private fun SoftCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

@Composable
private fun HeroTile(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.16f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun AccountCard(balance: AccountBalance, index: Int, selected: Boolean, value: String, detail: String, onClick: () -> Unit) {
    val colors = WealthColors.accountGradients[index.mod(WealthColors.accountGradients.size)]
    Box(
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors))
            .then(if (selected) Modifier.border(3.dp, Color.White, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(balance.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(balance.type.label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category?,
    value: Long,
    maxValue: Long,
    limit: Long?,
    percent: Long,
    ofWhat: String = "spending",
    onClick: () -> Unit
) {
    val color = categoryColor(category)
    Row(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryBadge(category, size = 38.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                Text(category?.name ?: "Uncategorised", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Money.formatInr(value) + (limit?.let { " / " + Money.formatCompact(it) } ?: ""),
                    fontWeight = FontWeight.SemiBold
                )
            }
            val over = limit != null && value > limit
            val fraction = if (limit != null && limit > 0) (value.toFloat() / limit).coerceIn(0f, 1f)
            else if (maxValue > 0) (value.toFloat() / maxValue).coerceIn(0.02f, 1f) else 0f
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))) {
                Box(
                    modifier = Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (over) MaterialTheme.colorScheme.error else color)
                )
            }
            Text("$percent% of $ofWhat  ›", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransactionRow(t: LedgerTransaction, category: Category?, accountName: String?, excluded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryBadge(category, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                t.merchant?.let { titleCase(it) } ?: category?.name ?: "Uncategorised",
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(category?.name ?: "Uncategorised", accountName, t.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val isIncome = t.kind == EntryKind.INCOME
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                contentDescription = null,
                tint = when {
                    excluded -> MaterialTheme.colorScheme.onSurfaceVariant
                    isIncome -> WealthColors.income
                    else -> WealthColors.expense
                },
                modifier = Modifier.size(14.dp)
            )
            Text(
                Money.formatInr(t.amountPaise),
                color = when {
                    excluded -> MaterialTheme.colorScheme.onSurfaceVariant
                    isIncome -> WealthColors.income
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(initial: DateRange, onPick: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The Material picker works in UTC milliseconds, so convert both ways in UTC.
    fun millis(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun date(m: Long) = Instant.ofEpochMilli(m).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = millis(initial.start),
        initialSelectedEndDateMillis = millis(initial.endInclusive)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis
                    if (s != null) onPick(date(s), date(state.selectedEndDateMillis ?: s))
                    onDismiss()
                }
            ) { Text("Show") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(
            state = state,
            title = { Text("Choose dates", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            modifier = Modifier.weight(1f)
        )
    }
}
