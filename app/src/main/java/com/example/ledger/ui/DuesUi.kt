package com.example.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ledger.core.Money
import com.example.ledger.core.RepeatingPayment
import com.example.ledger.data.Due
import com.example.ledger.data.DueKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private fun dueIcon(kind: DueKind): ImageVector = when (kind) {
    DueKind.CARD -> Icons.Filled.CreditCard
    DueKind.LOAN -> Icons.Filled.AccountBalance
    DueKind.INSURANCE -> Icons.Filled.HealthAndSafety
    DueKind.UTILITY -> Icons.Filled.Bolt
    DueKind.REPEATING -> Icons.Filled.Repeat
    DueKind.OTHER -> Icons.Filled.NotificationsActive
}

private fun dueColor(kind: DueKind): Color = when (kind) {
    DueKind.CARD -> Color(0xFF5B3FE4)
    DueKind.LOAN -> Color(0xFFFF8A3D)
    DueKind.INSURANCE -> Color(0xFF00A88E)
    DueKind.UTILITY -> Color(0xFFFFB300)
    DueKind.REPEATING -> Color(0xFF2B6CF6)
    DueKind.OTHER -> Color(0xFFB83FD8)
}

private fun whenText(date: LocalDate, today: LocalDate): Pair<String, Boolean> {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Due today" to true
        days == 1L -> "Due tomorrow" to true
        days > 1 -> "$days days to go" to (days <= 3)
        days == -1L -> "1 day overdue" to true
        else -> "${-days} days overdue" to true
    }
}

/**
 * Upcoming bills and reminders. Only the latest bill from each biller is shown, so an old
 * statement does not sit next to the new one.
 */
@Composable
fun DuesSection(
    dues: List<Due>,
    suggestions: List<RepeatingPayment>,
    onPaid: (Due) -> Unit,
    onDismiss: (Due) -> Unit,
    onAccept: (RepeatingPayment) -> Unit,
    onDecline: (RepeatingPayment) -> Unit,
    onAdd: () -> Unit
) {
    val today = LocalDate.now()
    val latest = dues.groupBy { Triple(it.kind, it.title, it.digits) }.map { (_, list) -> list.maxBy { it.dueEpochDay } }
        .sortedBy { it.dueEpochDay }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Dues & reminders")
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add reminder") }
        }
        if (latest.isEmpty() && suggestions.isEmpty()) {
            Text(
                "No bills found in your messages yet. Tap + to add a reminder (rent, school fees, SIP…).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(latest, key = { "d" + it.id }) { d -> DueCard(d, today, onPaid, onDismiss) }
            items(suggestions, key = { "s" + it.merchantKey }) { p -> SuggestionCard(p, onAccept, onDecline) }
        }
    }
}

@Composable
private fun DueCard(d: Due, today: LocalDate, onPaid: (Due) -> Unit, onDismiss: (Due) -> Unit) {
    val date = LocalDate.ofEpochDay(d.dueEpochDay)
    val (label, urgent) = whenText(date, today)
    val color = dueColor(d.kind)
    Column(
        modifier = Modifier.width(250.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.18f)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(28.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(dueIcon(d.kind), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(date), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (urgent) WealthColors.expense else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onDismiss(d) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
            }
        }
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                d.title + (d.digits?.let { " (••$it)" } ?: ""),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(Money.formatInr(d.amountPaise), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    d.minDuePaise?.let {
                        Text("Min " + Money.formatInr(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (d.repeatMonths != null) {
                        Text(
                            when (d.repeatMonths) { 1 -> "Every month"; 3 -> "Every quarter"; 6 -> "Every 6 months"; 12 -> "Every year"; else -> "Repeats" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalButton(onClick = { onPaid(d) }) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Paid")
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(p: RepeatingPayment, onAccept: (RepeatingPayment) -> Unit, onDecline: (RepeatingPayment) -> Unit) {
    val color = dueColor(DueKind.REPEATING)
    Column(
        modifier = Modifier.width(250.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.18f)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(28.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Repeat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(p.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Text("Next around " + formatDate(p.nextDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                p.name.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(Money.formatInr(p.amountPaise), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set reminder?", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { onDecline(p) }) { Text("No") }
                TextButton(onClick = { onAccept(p) }) { Text("Yes", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private val repeatOptions = listOf("Once" to null, "Every month" to 1, "Every quarter" to 3, "Every 6 months" to 6, "Every year" to 12)

/** Add a reminder by hand: rent, school fees, a friend to pay back, a SIP date… */
@Composable
fun AddReminderDialog(onSave: (String, Long, LocalDate, Int?) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(7)) }
    var repeat by remember { mutableStateOf(0) }
    var pickDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New reminder") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("What for (e.g. Rent)") }, singleLine = true)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text("Due on " + formatDate(date)) }
                PickerField(label = "Repeat", options = repeatOptions.map { it.first }, selectedIndex = repeat, onSelect = { repeat = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val paise = Money.parseToPaise(amount)
                when {
                    title.isBlank() -> error = "Give it a name"
                    paise == null || paise <= 0 -> error = "Enter an amount like 5000"
                    else -> {
                        onSave(title, paise, date, repeatOptions[repeat].second)
                        onDismiss()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (pickDate) ReminderDatePicker(date, onPick = { date = it }, onDismiss = { pickDate = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}
