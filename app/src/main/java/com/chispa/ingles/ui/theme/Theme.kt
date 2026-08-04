package com.chispa.ingles.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val LocalChispaColors = staticCompositionLocalOf { LightChispaColors }

private val LightScheme = lightColorScheme(
    primary = Violet500,
    onPrimary = White,
    primaryContainer = Violet100,
    onPrimaryContainer = Indigo800,
    secondary = Coral500,
    onSecondary = White,
    secondaryContainer = Coral50,
    onSecondaryContainer = Coral700,
    tertiary = Amber500,
    onTertiary = Ink900,
    tertiaryContainer = Amber100,
    onTertiaryContainer = Color6(0x5A3A00),
    error = Rose600,
    onError = White,
    errorContainer = Rose100,
    onErrorContainer = Color6(0x6E1013),
    background = Paper,
    onBackground = Ink900,
    surface = White,
    onSurface = Ink900,
    surfaceVariant = Violet50,
    onSurfaceVariant = Ink700,
    outline = Ink300,
    outlineVariant = Ink100,
    inverseSurface = Indigo900,
    inverseOnSurface = Violet50,
    inversePrimary = Violet300,
    surfaceTint = Violet500,
    scrim = Color6(0x000000),
)

private val DarkScheme = darkColorScheme(
    primary = Violet400,
    onPrimary = Indigo950,
    primaryContainer = Indigo700,
    onPrimaryContainer = Violet100,
    secondary = Coral400,
    onSecondary = Indigo950,
    secondaryContainer = Color6(0x5A2118),
    onSecondaryContainer = Coral200,
    tertiary = Amber400,
    onTertiary = Indigo950,
    tertiaryContainer = Color6(0x5A3A00),
    onTertiaryContainer = Amber100,
    error = Rose400,
    onError = Indigo950,
    errorContainer = Color6(0x4A1417),
    onErrorContainer = Rose100,
    background = Indigo950,
    onBackground = Violet50,
    surface = Indigo900,
    onSurface = Violet50,
    surfaceVariant = Indigo800,
    onSurfaceVariant = Violet200,
    outline = Color6(0x4A4664),
    outlineVariant = Color6(0x332E52),
    inverseSurface = Violet50,
    inverseOnSurface = Indigo900,
    inversePrimary = Violet600,
    surfaceTint = Violet400,
    scrim = Color6(0x000000),
)

/** Helper corto para no repetir `Color(0xFF...)` en las tablas de arriba. */
private fun Color6(rgb: Int) = androidx.compose.ui.graphics.Color(rgb or 0xFF000000.toInt())

val ChispaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun ChispaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extended = if (darkTheme) DarkChispaColors else LightChispaColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = scheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalChispaColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ChispaTypography,
            shapes = ChispaShapes,
            content = content
        )
    }
}

/** Acceso corto a los colores extendidos: `ChispaTheme.colors.streak`. */
object ChispaThemeTokens {
    val colors: ChispaColors
        @Composable get() = LocalChispaColors.current
}
