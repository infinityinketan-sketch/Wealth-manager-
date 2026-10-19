package com.example.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ledger.core.BudgetPeriod
import com.example.ledger.core.Frequency
import com.example.ledger.core.Money
import com.example.ledger.data.AccountBalance
import com.example.ledger.data.AccountType
import com.example.ledger.data.Budget
import com.example.ledger.data.Category
import com.example.ledger.data.EntryKind
import com.example.ledger.data.RecurringRule
import java.time.LocalDate

private sealed interface MoreDialog {
    data object AddAccount : MoreDialog
    data class AddCategory(val kind: EntryKind) : MoreDialog
    data class RenameCategory(val category: Category) : MoreDialog
    data class DeleteCategory(val category: Category) : MoreDialog
    /** [categoryId] == Budget.OVERALL_ID is the overall monthly budget. */
    data class SetBudget(val categoryId: Long, val name: String, val currentPaise: Long?) : MoreDialog
}

private val newCategoryColors = listOf(
    0xFFE57373, 0xFF81C784, 0xFF64B5F6, 0xFFBA68C8, 0xFFFFB74D,
    0xFFFFD54F, 0xFFF06292, 0xFF4DB6AC, 0xFF7986CB, 0xFFA1887F
)

@Composable
fun MoreScreen(
    startDay: Int,
    onStartDayChange: (Int) -> Unit,
    appLockEnabled: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    balances: List<AccountBalance>,
    categories: List<Category>,
    budgets: List<Budget>,
    recurring: List<RecurringRule>,
    onAddAccount: (String, AccountType, Long) -> Unit,
    onAddCategory: (String, EntryKind, Long) -> Unit,
    onRenameCategory: (Category, String) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onSetBudget: (Long, Long) -> Unit,
    onToggleRecurring: (RecurringRule, Boolean) -> Unit,
    onDeleteRecurring: (RecurringRule) -> Unit,
    askCategory: Boolean = true,
    onAskCategoryChange: (Boolean) -> Unit = {},
    onShowSafety: () -> Unit = {},
    onProblemReport: () -> Unit = {},
    lastBackupMillis: Long = 0L,
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dialog by remember { mutableStateOf<MoreDialog?>(null) }
    val budgetByCategory = remember(budgets) { budgets.associateBy { it.categoryId } }
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val expenseCategories = categories.filter { it.kind == EntryKind.EXPENSE }
    val incomeCategories = categories.filter { it.kind == EntryKind.INCOME }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { BackupCard(lastBackupMillis, onBackup, onRestore) }

        // ---- Settings ----
        item { SectionTitle("Settings") }
        item {
            Column {
                TextButton(onClick = onShowSafety) { Text("How your data is kept safe") }
                TextButton(onClick = onProblemReport) { Text("Problem report (copy crash details)") }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerField(
                        label = "Budget month starts on day",
                        options = (BudgetPeriod.MIN_START_DAY..BudgetPeriod.MAX_START_DAY).map { it.toString() },
                        selectedIndex = startDay - BudgetPeriod.MIN_START_DAY,
                        onSelect = { onStartDayChange(it + BudgetPeriod.MIN_START_DAY) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App lock")
                            Text(
                                "Ask for fingerprint, face or screen lock when opening the app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = appLockEnabled, onCheckedChange = onAppLockChange)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ask what each payment was for")
                            Text(
                                "A notification after each new payment or credit, with one-tap categories. " +
                                    "Shops you already chose for are not asked again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = askCategory, onCheckedChange = onAskCategoryChange)
                    }
                }
            }
        }

        // ---- Budgets ----
        item { SectionTitle("Monthly budgets") }
        item {
            val overall = budgetByCategory[Budget.OVERALL_ID]?.limitPaise
            BudgetRow("Overall budget", overall) {
                dialog = MoreDialog.SetBudget(Budget.OVERALL_ID, "Overall budget", overall)
            }
        }
        items(expenseCategories, key = { "budget-" + it.id }) { category ->
            val limit = budgetByCategory[category.id]?.limitPaise
            BudgetRow(category.name, limit) {
                dialog = MoreDialog.SetBudget(category.id, category.name, limit)
            }
        }

        // ---- Categories ----
        item { SectionHeader("Expense categories") { dialog = MoreDialog.AddCategory(EntryKind.EXPENSE) } }
        items(expenseCategories, key = { "expense-" + it.id }) { category ->
            CategoryRow(
                category = category,
                onRename = { dialog = MoreDialog.RenameCategory(category) },
                onDelete = { dialog = MoreDialog.DeleteCategory(category) }
            )
        }
        item { SectionHeader("Income categories") { dialog = MoreDialog.AddCategory(EntryKind.INCOME) } }
        items(incomeCategories, key = { "income-" + it.id }) { category ->
            CategoryRow(
                category = category,
                onRename = { dialog = MoreDialog.RenameCategory(category) },
                onDelete = { dialog = MoreDialog.DeleteCategory(category) }
            )
        }

        // ---- Accounts ----
        item { SectionHeader("Accounts") { dialog = MoreDialog.AddAccount } }
        items(balances, key = { "account-" + it.accountId }) { balance ->
            ListItem(
                headlineContent = { Text(balance.name) },
                supportingContent = { Text(balance.type.label) },
                trailingContent = { Text(Money.formatInr(balance.balancePaise)) }
            )
        }

        // ---- Recurring ----
        item { SectionTitle("Repeating entries") }
        if (recurring.isEmpty()) {
            item {
                Text(
                    "None yet. When adding an entry, choose how often it repeats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(recurring, key = { "rule-" + it.id }) { rule ->
            val categoryName = rule.categoryId?.let { categoryById[it]?.name } ?: "Uncategorised"
            ListItem(
                headlineContent = { Text(categoryName + " · " + Money.formatInr(rule.amountPaise)) },
                supportingContent = {
                    val started = formatDate(LocalDate.ofEpochDay(rule.startEpochDay))
                    val state = if (rule.active) "" else " · paused"
                    Text(frequencyLabel(rule.frequency) + " from " + started + state)
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onToggleRecurring(rule, !rule.active) }) {
                            Icon(
                                if (rule.active) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (rule.active) "Pause" else "Resume"
                            )
                        }
                        IconButton(onClick = { onDeleteRecurring(rule) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Stop repeating")
                        }
                    }
                }
            )
        }
        item {
            Text(
                "Stopping a repeat keeps the entries it already created.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    when (val current = dialog) {
        null -> Unit
        MoreDialog.AddAccount -> AddAccountDialog(
            onConfirm = { name, type, paise -> onAddAccount(name, type, paise) },
            onDismiss = { dialog = null }
        )
        is MoreDialog.AddCategory -> TextInputDialog(
            title = if (current.kind == EntryKind.EXPENSE) "New expense category" else "New income category",
            label = "Name",
            initial = "",
            decimal = false,
            allowBlank = false,
            onConfirm = { text ->
                val color = newCategoryColors[categories.size % newCategoryColors.size]
                onAddCategory(text, current.kind, color)
            },
            onDismiss = { dialog = null }
        )
        is MoreDialog.RenameCategory -> TextInputDialog(
            title = "Rename category",
            label = "Name",
            initial = current.category.name,
            decimal = false,
            allowBlank = false,
            onConfirm = { text -> onRenameCategory(current.category, text) },
            onDismiss = { dialog = null }
        )
        is MoreDialog.DeleteCategory -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete \"" + current.category.name + "\"?") },
            text = { Text("Entries in this category are kept and become Uncategorised. Its budget is removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCategory(current.category)
                    dialog = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
        is MoreDialog.SetBudget -> TextInputDialog(
            title = "Monthly limit: " + current.name,
            label = "Amount (₹), leave empty for no limit",
            initial = current.currentPaise?.let { Money.formatPlain(it) } ?: "",
            decimal = true,
            allowBlank = true,
            onConfirm = { text ->
                val paise = if (text.isBlank()) 0L else Money.parseToPaise(text) ?: 0L
                onSetBudget(current.categoryId, paise)
            },
            onDismiss = { dialog = null }
        )
    }
}

private fun frequencyLabel(frequency: Frequency): String = when (frequency) {
    Frequency.WEEKLY -> "Every week"
    Frequency.MONTHLY -> "Every month"
    Frequency.YEARLY -> "Every year"
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SectionTitle(title)
        IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add") }
    }
}

@Composable
private fun BudgetRow(name: String, limitPaise: Long?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name) },
        trailingContent = { Text(if (limitPaise == null) "No limit" else Money.formatInr(limitPaise)) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun CategoryRow(category: Category, onRename: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(category.name) },
        trailingContent = {
            Row {
                IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, contentDescription = "Rename") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    decimal: Boolean,
    allowBlank: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = allowBlank || text.isNotBlank(),
                onClick = {
                    onConfirm(text.trim())
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddAccountDialog(onConfirm: (String, AccountType, Long) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var typeIndex by remember { mutableStateOf(1) }
    var opening by remember { mutableStateOf("") }
    val types = AccountType.values()
    val openingPaise = if (opening.isBlank()) 0L else Money.parseToPaise(opening)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name, e.g. HDFC savings") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PickerField(
                    label = "Type",
                    options = types.map { it.label },
                    selectedIndex = typeIndex,
                    onSelect = { typeIndex = it }
                )
                OutlinedTextField(
                    value = opening,
                    onValueChange = { opening = it },
                    label = { Text("Current balance (₹)") },
                    singleLine = true,
                    isError = openingPaise == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && openingPaise != null,
                onClick = {
                    onConfirm(name.trim(), types[typeIndex], openingPaise ?: 0L)
                    onDismiss()
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
