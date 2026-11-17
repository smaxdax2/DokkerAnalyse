package com.dacia.dokkeranalyse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DokkerBlue = Color(0xFF38BDF8)
val DokkerCyan = Color(0xFF06B6D4)
val DokkerGreen = Color(0xFF22C55E)
val DokkerAmber = Color(0xFFF59E0B)
val DokkerRed = Color(0xFFEF4444)
val DarkBg = Color(0xFF0F172A)
val CardSurface = Color(0xFF1E293B)
val CardSurfaceElevated = Color(0xFF334155)

private val AutomotiveDarkColorScheme = darkColorScheme(
    primary = DokkerBlue,
    secondary = DokkerCyan,
    tertiary = DokkerGreen,
    background = DarkBg,
    surface = CardSurface,
    surfaceVariant = CardSurfaceElevated,
    onPrimary = Color(0xFF082F49),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = DokkerRed
)

@Composable
fun DokkerAnalyseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AutomotiveDarkColorScheme,
        content = content
    )
}
