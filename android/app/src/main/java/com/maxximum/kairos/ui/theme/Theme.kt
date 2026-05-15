package com.maxximum.kairos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = KairosPurpleDark,
    onPrimary = KairosBackgroundDark,
    primaryContainer = KairosPurpleContainerDark,
    onPrimaryContainer = KairosTextDark,
    secondary = KairosTealDark,
    onSecondary = KairosBackgroundDark,
    secondaryContainer = KairosSurfaceVariantDark,
    onSecondaryContainer = KairosTextDark,
    tertiary = KairosAmberDark,
    onTertiary = KairosBackgroundDark,
    background = KairosBackgroundDark,
    onBackground = KairosTextDark,
    surface = KairosSurfaceDark,
    onSurface = KairosTextDark,
    surfaceVariant = KairosSurfaceVariantDark,
    onSurfaceVariant = KairosMutedDark,
    outline = KairosOutlineDark,
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF4A1F27),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = KairosPurpleLight,
    onPrimary = Color.White,
    primaryContainer = KairosPurpleContainerLight,
    onPrimaryContainer = KairosTextLight,
    secondary = KairosTealLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F5EF),
    onSecondaryContainer = KairosTextLight,
    tertiary = KairosAmberLight,
    onTertiary = Color.White,
    background = KairosBackgroundLight,
    onBackground = KairosTextLight,
    surface = KairosSurfaceLight,
    onSurface = KairosTextLight,
    surfaceVariant = KairosSurfaceVariantLight,
    onSurfaceVariant = KairosMutedLight,
    outline = KairosOutlineLight,
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

@Composable
fun KairosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
