package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = CrimsonLive,
    onPrimary = Color.White,
    primaryContainer = CrimsonLiveContainer,
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = CyanSignal,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = CyanSignalContainer,
    onSecondaryContainer = Color(0xFF9DF7EB),
    tertiary = AmberStatus,
    onTertiary = Color(0xFF281800),
    tertiaryContainer = AmberStatusContainer,
    onTertiaryContainer = Color(0xFFFFE0A6),
    background = StudioObsidian,
    onBackground = TextOnDark,
    surface = StudioSurface,
    onSurface = TextOnDark,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextMutedOnDark,
    outline = StudioCardBorder,
    outlineVariant = Color(0xFF232838),
    error = Color(0xFFFF5A5F),
    onError = Color.White
)

private val StudioLightColorScheme = lightColorScheme(
    primary = CrimsonLiveLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DF),
    onPrimaryContainer = Color(0xFF400010),
    secondary = CyanSignalLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF7F1),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFB87400),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextOnLight,
    surface = LightSurface,
    onSurface = TextOnLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextMutedOnLight,
    outline = Color(0xFFD6DCE8),
    outlineVariant = Color(0xFFE7EBF3)
)

val LiveVaultShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LiveVaultTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LiveVaultTypography,
        shapes = LiveVaultShapes,
        content = content
    )
}
