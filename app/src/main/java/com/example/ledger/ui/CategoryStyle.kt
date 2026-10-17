package com.example.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ledger.data.Category

/** A picture for each starter category; categories the person adds get a generic one. */
fun categoryIcon(name: String?): ImageVector = when (name) {
    "Food & dining" -> Icons.Filled.Restaurant
    "Groceries" -> Icons.Filled.LocalGroceryStore
    "Health" -> Icons.Filled.LocalHospital
    "Transport" -> Icons.Filled.DirectionsCar
    "Fuel" -> Icons.Filled.LocalGasStation
    "Rent" -> Icons.Filled.Home
    "EMI" -> Icons.Filled.AccountBalance
    "Electricity & bills" -> Icons.Filled.Bolt
    "Insurance" -> Icons.Filled.HealthAndSafety
    "Clothes" -> Icons.Filled.Checkroom
    "Online shopping" -> Icons.Filled.ShoppingBag
    "Home & household" -> Icons.Filled.Weekend
    "Personal care" -> Icons.Filled.Spa
    "Entertainment" -> Icons.Filled.Movie
    "Travel" -> Icons.Filled.Flight
    "Education" -> Icons.Filled.School
    "Donations" -> Icons.Filled.VolunteerActivism
    "Cash withdrawal" -> Icons.Filled.LocalAtm
    "Sent to people", "Received from people" -> Icons.Filled.People
    "Investments & FD" -> Icons.Filled.Savings
    "Credit card bill" -> Icons.Filled.CreditCard
    "Self transfer", "Transfer in (not income)" -> Icons.Filled.SwapHoriz
    "Salary" -> Icons.Filled.Work
    "Refund" -> Icons.Filled.Replay
    "Interest" -> Icons.Filled.Percent
    "Other income" -> Icons.Filled.CallReceived
    null -> Icons.Filled.MoreHoriz
    else -> Icons.Filled.Payments
}

fun categoryColor(category: Category?): Color = category?.let { Color(it.colorArgb) } ?: Color(0xFF90A4AE)

/** A soft circle in the category's colour with its icon, used in lists and legends. */
@Composable
fun CategoryBadge(category: Category?, size: Dp = 40.dp) {
    val color = categoryColor(category)
    Box(
        modifier = Modifier.size(size).background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(categoryIcon(category?.name), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * Categories left out of income / spending totals: the money-movement ones (FD, card bills, own
 * transfers) unless the person switched them on, plus any the person switched off.
 */
fun excludedCategoryIds(categories: List<Category>, choices: Map<Long, Boolean>): Set<Long?> =
    categories.filter { c ->
        val byDefault = c.name in com.example.ledger.sms.StarterCategories.excludedNames
        !(choices[c.id] ?: !byDefault)
    }.mapTo(HashSet<Long?>()) { it.id }
