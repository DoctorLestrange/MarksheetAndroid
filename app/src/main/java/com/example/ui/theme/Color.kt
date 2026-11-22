package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// --- Light Theme: Warm Alabaster / Paper Off-White (gentle on eyes, zero pure #FFFFFF glare) ---
val PaperBackground = Color(0xFFF3F1EA)
val PaperSurface = Color(0xFFF3F1EA)
val PaperSurfaceLowest = Color(0xFFFAF8F3)
val PaperSurfaceLow = Color(0xFFECE9E0)
val PaperSurfaceContainer = Color(0xFFF8F6F0)
val PaperSurfaceHigh = Color(0xFFE5E1D6)
val PaperSurfaceHighest = Color(0xFFDCD7CA)

val InkPrimary = Color(0xFF1C1C1A)
val InkOnPrimary = Color(0xFFF6F4EE)
val InkPrimaryContainer = Color(0xFFE3DFD3)
val InkOnPrimaryContainer = Color(0xFF1C1C1A)

val PaperOnBackground = Color(0xFF1C1C1A)
val PaperOnSurface = Color(0xFF1C1C1A)
val PaperOnSurfaceVariant = Color(0xFF5D5B54)
val PaperOutline = Color(0xFFC5C0B4)
val PaperOutlineVariant = Color(0xFFDDD8CC)

// --- Dark Theme: Pure Neutral Graphite Grey (strictly R=G=B, zero blue tint) ---
val GraphiteBackground = Color(0xFF101010)
val GraphiteSurface = Color(0xFF101010)
val GraphiteSurfaceLowest = Color(0xFF0A0A0A)
val GraphiteSurfaceLow = Color(0xFF161616)
val GraphiteSurfaceContainer = Color(0xFF1B1B1B)
val GraphiteSurfaceHigh = Color(0xFF232323)
val GraphiteSurfaceHighest = Color(0xFF2C2C2C)

// --- Dark Theme Variant: Pure AMOLED Black (strictly neutral, zero blue tint) ---
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF000000)
val AmoledSurfaceLowest = Color(0xFF000000)
val AmoledSurfaceLow = Color(0xFF0B0B0B)
val AmoledSurfaceContainer = Color(0xFF121212)
val AmoledSurfaceHigh = Color(0xFF1A1A1A)
val AmoledSurfaceHighest = Color(0xFF242424)

val ChalkPrimary = Color(0xFFE6E6E6)
val ChalkOnPrimary = Color(0xFF111111)
val ChalkPrimaryContainer = Color(0xFF2B2B2B)
val ChalkOnPrimaryContainer = Color(0xFFECECEC)

val GraphiteOnBackground = Color(0xFFEAEAEA)
val GraphiteOnSurface = Color(0xFFEAEAEA)
val GraphiteOnSurfaceVariant = Color(0xFF9C9C9C)
val GraphiteOutline = Color(0xFF3F3F3F)
val GraphiteOutlineVariant = Color(0xFF272727)

// --- Operational Semantic Tokens ---
@Immutable
data class MarksheetSemanticColors(
    val positiveAccent: Color,
    val positiveContainer: Color,
    val onPositiveContainer: Color,
    val positiveButtonBg: Color,
    val onPositiveButton: Color,

    val negativeAccent: Color,
    val negativeContainer: Color,
    val onNegativeContainer: Color,
    val negativeButtonBg: Color,
    val onNegativeButton: Color,

    val skipAccent: Color,
    val skipContainer: Color,
    val onSkipContainer: Color,
    val skipButtonBg: Color,
    val onSkipButton: Color,

    val cardBorder: Color
)

val LightMarksheetSemanticColors = MarksheetSemanticColors(
    positiveAccent = Color(0xFF1B7349),
    positiveContainer = Color(0xFFDCEDE3),
    onPositiveContainer = Color(0xFF0E4128),
    positiveButtonBg = Color(0xFF1F7A4F),
    onPositiveButton = Color(0xFFF6F5F0),

    negativeAccent = Color(0xFFB83B2E),
    negativeContainer = Color(0xFFF5DFDC),
    onNegativeContainer = Color(0xFF631C14),
    negativeButtonBg = Color(0xFFC24134),
    onNegativeButton = Color(0xFFF9F6F2),

    skipAccent = Color(0xFF5D5952),
    skipContainer = Color(0xFFE6E2D7),
    onSkipContainer = Color(0xFF2B2925),
    skipButtonBg = Color(0xFFE5E1D6),
    onSkipButton = Color(0xFF24231F),

    cardBorder = Color(0xFFDED9CD)
)

val DarkMarksheetSemanticColors = MarksheetSemanticColors(
    positiveAccent = Color(0xFF42C782),
    positiveContainer = Color(0xFF14291E),
    onPositiveContainer = Color(0xFF8BE8B6),
    positiveButtonBg = Color(0xFF1E5C3C),
    onPositiveButton = Color(0xFFEAF9F0),

    negativeAccent = Color(0xFFEF6B5D),
    negativeContainer = Color(0xFF2B1614),
    onNegativeContainer = Color(0xFFF7A69D),
    negativeButtonBg = Color(0xFF7A2821),
    onNegativeButton = Color(0xFFFCEEEB),

    skipAccent = Color(0xFFA0A0A0),
    skipContainer = Color(0xFF222222),
    onSkipContainer = Color(0xFFD6D6D6),
    skipButtonBg = Color(0xFF242424),
    onSkipButton = Color(0xFFE2E2E2),

    cardBorder = Color(0xFF282828)
)

val AmoledMarksheetSemanticColors = DarkMarksheetSemanticColors.copy(
    skipContainer = Color(0xFF181818),
    skipButtonBg = Color(0xFF1A1A1A),
    cardBorder = Color(0xFF232323)
)
