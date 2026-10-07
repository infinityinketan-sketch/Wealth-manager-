package com.example.ledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E4B),
    onPrimary = Color.White,
    secondary = Color(0xFF4A6358)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7ED3B5),
    onPrimary = Color(0xFF00382B),
    secondary = Color(0xFFB1CCBF)
)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
