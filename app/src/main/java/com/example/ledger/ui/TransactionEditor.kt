package com.example.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.example.ledger.sms.SmsImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ledger.core.Frequency
import com.example.ledger.core.Money
import com.example.ledger.data.Account
import com.example.ledger.data.Category
import com.example.ledger.data.EntryKind
import com.example.ledger.data.LedgerTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val repeatLabels = listOf("Does not repeat", "Every week", "Every month", "Every year")
private val repeatValues = listOf<Frequency?>(null, Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY)

/**
 * Add or edit one income/expense entry. Editing changes only this entry. For a new entry the
 * person can pick a repeat, which creates a recurring rule (managed later under More).
 */
@Composable
fun TransactionEditor(
    existing: LedgerTransaction?,
    accounts: List<Account>,
    categories: List<Category>,
    onSave: (transaction: LedgerTransaction, repeat: Frequency?) -> Unit,
    onDelete: (LedgerTransaction) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: EntryKind.EXPENSE) }
    var amountText by remember(existing) {
        mutableStateOf(if (existing != null) Money.formatPlain(existing.amountPaise) else "")
    }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    var date by remember(existing) {
        mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.epochDay) } ?: LocalDate.now())
    }
    var accountIndex by remember(existing, accounts) {
        mutableStateOf(accounts.indexOfFirst { it.id == existing?.accountId }.coerceAtLeast(0))
    }
    // Category options depend on the kind; index 0 is always "Uncategorised".
    val kindCategories = categories.filter { it.kind == kind }
    var categoryId by remember(existing) { mutableStateOf(existing?.categoryId) }
    val categoryIndex = (kindCategories.indexOfFirst { it.id == categoryId } + 1).coerceAtLeast(0)
    var repeatIndex by remember(existing) { mutableStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val smsBody by produceState<String?>(initialValue = null, existing?.smsKey) {
        val key = existing?.smsKey
        value = if (key == null) null else withContext(Dispatchers.IO) { SmsImporter.findBody(context, key) }
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add entry" else "Edit entry") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (existing?.merchant != null) {
                    Text(
                        (if (existing.kind == EntryKind.INCOME) "From " else "Paid to ") + existing.merchant +
                            (if (existing.smsKey != null) "  ·  read from SMS" else ""),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                smsBody?.let { body ->
                    Text("Bank message (long-press to copy)", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryKind.values().forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = {
                                kind = option
                                categoryId = null
                            },
                            label = { Text(option.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = null
                    },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Moving money to an FD, your own account or a card bill? Pick Investments & FD, Self transfer " +
                        "or Credit card bill and it will not count as spending.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PickerField(
                    label = "Category",
                    options = listOf("Uncategorised") + kindCategories.map { it.name },
                    selectedIndex = categoryIndex,
                    onSelect = { index -> categoryId = if (index == 0) null else kindCategories[index - 1].id }
                )

                PickerField(
                    label = "Account",
                    options = accounts.map { it.name },
                    selectedIndex = accountIndex,
                    onSelect = { accountIndex = it }
                )

                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: " + formatDate(date))
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (existing == null) {
                    PickerField(
                        label = "Repeat",
                        options = repeatLabels,
                        selectedIndex = repeatIndex,
                        onSelect = { repeatIndex = it }
                    )
                } else if (existing.recurringRuleId != null) {
                    Text(
                        "Created by a repeating entry. Changes here affect only this one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val paise = Money.parseToPaise(amountText)
                if (paise == null || paise <= 0L) {
                    error = "Enter an amount like 250 or 99.50"
                } else if (accounts.isEmpty()) {
                    error = "Add an account first"
                } else {
                    val accountId = accounts[accountIndex.coerceIn(0, accounts.size - 1)].id
                    val transaction = existing?.copy(
                        accountId = accountId,
                        categoryId = categoryId,
                        kind = kind,
                        amountPaise = paise,
                        note = note.trim(),
                        epochDay = date.toEpochDay()
                    ) ?: LedgerTransaction(
                        accountId = accountId,
                        categoryId = categoryId,
                        kind = kind,
                        amountPaise = paise,
                        note = note.trim(),
                        epochDay = date.toEpochDay()
                    )
                    onSave(transaction, if (existing == null) repeatValues[repeatIndex] else null)
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = {
                        onDelete(existing)
                        onDismiss()
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (showDatePicker) {
        DatePickerFor(
            initial = date,
            onPick = { date = it },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerFor(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The Material date picker works in UTC milliseconds, so convert both ways in UTC.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}
