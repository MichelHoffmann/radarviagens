package com.radardecorridas.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Emerald500,
    secondary = Cyan400,
    tertiary = Amber500,
    background = Slate950,
    surface = Slate900,
    onPrimary = Slate950,
    onSecondary = Slate950,
    onTertiary = Slate950,
    onBackground = Slate100,
    onSurface = Slate100,
)

@Composable
fun RadarDeCorridasTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
