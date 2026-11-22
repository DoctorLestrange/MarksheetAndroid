package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.domain.ThemeMode

val LocalMarksheetColors = staticCompositionLocalOf { LightMarksheetSemanticColors }

private val MarksheetLightColorScheme = lightColorScheme(
    primary = InkPrimary,
    onPrimary = InkOnPrimary,
    primaryContainer = InkPrimaryContainer,
    onPrimaryContainer = InkOnPrimaryContainer,
    secondary = PaperOnSurfaceVariant,
    onSecondary = PaperSurfaceLowest,
    secondaryContainer = PaperSurfaceHigh,
    onSecondaryContainer = PaperOnSurface,
    tertiary = LightMarksheetSemanticColors.positiveAccent,
    onTertiary = LightMarksheetSemanticColors.onPositiveButton,
    tertiaryContainer = LightMarksheetSemanticColors.positiveContainer,
    onTertiaryContainer = LightMarksheetSemanticColors.onPositiveContainer,
    error = LightMarksheetSemanticColors.negativeAccent,
    onError = LightMarksheetSemanticColors.onNegativeButton,
    errorContainer = LightMarksheetSemanticColors.negativeContainer,
    onErrorContainer = LightMarksheetSemanticColors.onNegativeContainer,
    background = PaperBackground,
    onBackground = PaperOnBackground,
    surface = PaperSurface,
    onSurface = PaperOnSurface,
    surfaceVariant = PaperSurfaceHigh,
    onSurfaceVariant = PaperOnSurfaceVariant,
    surfaceContainerLowest = PaperSurfaceLowest,
    surfaceContainerLow = PaperSurfaceLow,
    surfaceContainer = PaperSurfaceContainer,
    surfaceContainerHigh = PaperSurfaceHigh,
    surfaceContainerHighest = PaperSurfaceHighest,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant
)

private val MarksheetGraphiteDarkColorScheme = darkColorScheme(
    primary = ChalkPrimary,
    onPrimary = ChalkOnPrimary,
    primaryContainer = ChalkPrimaryContainer,
    onPrimaryContainer = ChalkOnPrimaryContainer,
    secondary = GraphiteOnSurfaceVariant,
    onSecondary = GraphiteBackground,
    secondaryContainer = GraphiteSurfaceHigh,
    onSecondaryContainer = GraphiteOnSurface,
    tertiary = DarkMarksheetSemanticColors.positiveAccent,
    onTertiary = DarkMarksheetSemanticColors.onPositiveButton,
    tertiaryContainer = DarkMarksheetSemanticColors.positiveContainer,
    onTertiaryContainer = DarkMarksheetSemanticColors.onPositiveContainer,
    error = DarkMarksheetSemanticColors.negativeAccent,
    onError = DarkMarksheetSemanticColors.onNegativeButton,
    errorContainer = DarkMarksheetSemanticColors.negativeContainer,
    onErrorContainer = DarkMarksheetSemanticColors.onNegativeContainer,
    background = GraphiteBackground,
    onBackground = GraphiteOnBackground,
    surface = GraphiteSurface,
    onSurface = GraphiteOnSurface,
    surfaceVariant = GraphiteSurfaceHigh,
    onSurfaceVariant = GraphiteOnSurfaceVariant,
    surfaceContainerLowest = GraphiteSurfaceLowest,
    surfaceContainerLow = GraphiteSurfaceLow,
    surfaceContainer = GraphiteSurfaceContainer,
    surfaceContainerHigh = GraphiteSurfaceHigh,
    surfaceContainerHighest = GraphiteSurfaceHighest,
    outline = GraphiteOutline,
    outlineVariant = GraphiteOutlineVariant
)

private val MarksheetAmoledDarkColorScheme = MarksheetGraphiteDarkColorScheme.copy(
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceVariant = AmoledSurfaceHigh,
    surfaceContainerLowest = AmoledSurfaceLowest,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = AmoledSurfaceHighest
)

val MarksheetShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MarksheetTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = themeMode == ThemeMode.DARK
    val colorScheme = when {
        !isDark -> MarksheetLightColorScheme
        amoledBlack -> MarksheetAmoledDarkColorScheme
        else -> MarksheetGraphiteDarkColorScheme
    }
    val semanticColors = when {
        !isDark -> LightMarksheetSemanticColors
        amoledBlack -> AmoledMarksheetSemanticColors
        else -> DarkMarksheetSemanticColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalMarksheetColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MarksheetTypography,
            shapes = MarksheetShapes,
            content = content
        )
    }
}
