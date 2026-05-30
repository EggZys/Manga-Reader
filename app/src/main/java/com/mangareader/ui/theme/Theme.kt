package com.mangareader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MochaBlue,
    onPrimary = MochaBase,
    primaryContainer = MochaSurface0,
    onPrimaryContainer = MochaText,
    secondary = MochaGreen,
    onSecondary = MochaBase,
    secondaryContainer = MochaSurface1,
    onSecondaryContainer = MochaText,
    tertiary = MochaMauve,
    onTertiary = MochaBase,
    tertiaryContainer = MochaSurface1,
    onTertiaryContainer = MochaText,
    error = MochaRed,
    onError = MochaBase,
    errorContainer = MochaSurface0,
    onErrorContainer = MochaRed,
    background = MochaBase,
    onBackground = MochaText,
    surface = MochaMantle,
    onSurface = MochaText,
    surfaceVariant = MochaSurface0,
    onSurfaceVariant = MochaSubtext0,
    outline = MochaSurface2,
    outlineVariant = MochaSurface1,
    inverseSurface = MochaText,
    inverseOnSurface = MochaBase,
    inversePrimary = LatteBlue,
    surfaceTint = MochaBlue,
)

private val LightColorScheme = lightColorScheme(
    primary = LatteBlue,
    onPrimary = LatteBase,
    primaryContainer = LatteSurface0,
    onPrimaryContainer = LatteText,
    secondary = LatteGreen,
    onSecondary = LatteBase,
    secondaryContainer = LatteSurface1,
    onSecondaryContainer = LatteText,
    tertiary = LatteMauve,
    onTertiary = LatteBase,
    tertiaryContainer = LatteSurface1,
    onTertiaryContainer = LatteText,
    error = LatteRed,
    onError = LatteBase,
    errorContainer = LatteSurface0,
    onErrorContainer = LatteRed,
    background = LatteBase,
    onBackground = LatteText,
    surface = LatteMantle,
    onSurface = LatteText,
    surfaceVariant = LatteSurface0,
    onSurfaceVariant = LatteSubtext0,
    outline = LatteSurface2,
    outlineVariant = LatteSurface1,
    inverseSurface = LatteText,
    inverseOnSurface = LatteBase,
    inversePrimary = MochaBlue,
    surfaceTint = LatteBlue,
)

@Composable
fun MangaReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
