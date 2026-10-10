package com.example.samsungcalendarclone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF346BD6), onPrimary = Color.White,
    background = Color(0xFFFAFAFC), surface = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF0F1F5), surfaceContainerLow = Color.White,
    onSurface = Color(0xFF202126), onSurfaceVariant = Color(0xFF71747C),
    outlineVariant = Color(0xFFE6E7EC), secondaryContainer = Color(0xFFE9EFFC),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ABBFF), onPrimary = Color(0xFF102F66),
    background = Color(0xFF131416), surface = Color(0xFF131416),
    surfaceContainer = Color(0xFF232529), surfaceContainerLow = Color(0xFF1B1D20),
    onSurface = Color(0xFFF1F1F4), onSurfaceVariant = Color(0xFFABADB5),
    outlineVariant = Color(0xFF303238), secondaryContainer = Color(0xFF253650),
)

@Composable
fun SamsungCalendarCloneTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography, content = content)
}
