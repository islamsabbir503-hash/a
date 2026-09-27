package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldNeon,
    onPrimary = DeepObsidian,
    primaryContainer = CyberGlassSurface,
    onPrimaryContainer = EmeraldNeon,
    secondary = CyberCyan,
    onSecondary = DeepObsidian,
    tertiary = ElectricIndigo,
    onTertiary = Color.White,
    background = DeepObsidian,
    onBackground = TextPrimary,
    surface = CyberGlassSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberGlassSurface,
    onSurfaceVariant = TextSecondary,
    outline = CyberGlassBorder,
    error = CrimsonCoral,
    onError = Color.White
)

@Composable
fun NitroWarpTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
