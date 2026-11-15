package com.abdulaziz.netpass.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryIndigo = Color(0xFF4F46E5)
val PrimaryIndigoDark = Color(0xFF6366F1)
val EmeraldGreen = Color(0xFF10B981)
val CrimsonRed = Color(0xFFEF4444)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigoDark,
    secondary = EmeraldGreen,
    error = CrimsonRed,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onPrimary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    secondary = EmeraldGreen,
    error = CrimsonRed,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

@Composable
fun NetPassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
