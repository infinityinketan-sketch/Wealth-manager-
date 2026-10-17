package com.example.ledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FE4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1C0870),
    secondary = Color(0xFF00A88E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F4EA),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFFF6B4A),
    tertiaryContainer = Color(0xFFFFDCD3),
    background = Color(0xFFF6F4FE),
    surface = Color(0xFFF6F4FE),
    surfaceVariant = Color(0xFFE9E5F7),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEDE9FA),
    onSurfaceVariant = Color(0xFF5A5670)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7A6FF),
    onPrimary = Color(0xFF2A1690),
    primaryContainer = Color(0xFF3F2BB0),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFF4FE0C3),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF00524A),
    onSecondaryContainer = Color(0xFFC9F4EA),
    tertiary = Color(0xFFFF9C83),
    tertiaryContainer = Color(0xFF6E2414),
    background = Color(0xFF110F1E),
    surface = Color(0xFF110F1E),
    surfaceVariant = Color(0xFF26223C),
    surfaceContainer = Color(0xFF1C1930),
    surfaceContainerLow = Color(0xFF1A172C),
    surfaceContainerHighest = Color(0xFF2B2744),
    onSurfaceVariant = Color(0xFFC0BAD8)
)

/** Colours used by the money screens, the same in light and dark. */
object WealthColors {
    val income = Color(0xFF16C784)
    val expense = Color(0xFFFF5C7A)
    val heroGradient = Brush.linearGradient(listOf(Color(0xFF5B3FE4), Color(0xFFA03FD8), Color(0xFFFF6B6B)))
    val accountGradients = listOf(
        listOf(Color(0xFF00A88E), Color(0xFF2BC0E4)),
        listOf(Color(0xFF5B3FE4), Color(0xFF8E7CFF)),
        listOf(Color(0xFFFF8A3D), Color(0xFFFF5C7A)),
        listOf(Color(0xFF2B6CF6), Color(0xFF00C2FF)),
        listOf(Color(0xFFB83FD8), Color(0xFFFF6BB5))
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
