package com.ghostmode.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// "Night ink + spectral violet" palette. Violet marks the active (ghost) state, mint marks the
// normal "reachable" state.

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCA8FF),
    onPrimary = Color(0xFF24115C),
    primaryContainer = Color(0xFF3B2A86),
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondary = Color(0xFF7FE0CC),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF104F44),
    onSecondaryContainer = Color(0xFFA2F4E1),
    tertiary = Color(0xFFFFB0CB),
    onTertiary = Color(0xFF5B1133),
    tertiaryContainer = Color(0xFF77294A),
    onTertiaryContainer = Color(0xFFFFD9E3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D0E16),
    onBackground = Color(0xFFE5E1F0),
    surface = Color(0xFF0D0E16),
    onSurface = Color(0xFFE5E1F0),
    surfaceVariant = Color(0xFF2A2A3C),
    onSurfaceVariant = Color(0xFFC8C3DA),
    surfaceContainerLowest = Color(0xFF08090F),
    surfaceContainerLow = Color(0xFF14151F),
    surfaceContainer = Color(0xFF181A25),
    surfaceContainerHigh = Color(0xFF20222F),
    surfaceContainerHighest = Color(0xFF2A2C3A),
    surfaceBright = Color(0xFF33354A),
    surfaceDim = Color(0xFF0D0E16),
    outline = Color(0xFF928DA6),
    outlineVariant = Color(0xFF3A394D),
    inverseSurface = Color(0xFFE5E1F0),
    inverseOnSurface = Color(0xFF2B2A36),
    inversePrimary = Color(0xFF5B45D6)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B45D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0063),
    secondary = Color(0xFF006B5B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFA2F2DE),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFF9A3A60),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF3E001D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F6FD),
    onBackground = Color(0xFF1B1A23),
    surface = Color(0xFFF8F6FD),
    onSurface = Color(0xFF1B1A23),
    surfaceVariant = Color(0xFFE5E0F0),
    onSurfaceVariant = Color(0xFF48455A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2EFF9),
    surfaceContainer = Color(0xFFECE9F5),
    surfaceContainerHigh = Color(0xFFE6E3F0),
    surfaceContainerHighest = Color(0xFFE0DCEA),
    surfaceBright = Color(0xFFF8F6FD),
    surfaceDim = Color(0xFFD9D5E3),
    outline = Color(0xFF79758C),
    outlineVariant = Color(0xFFCAC4DA),
    inverseSurface = Color(0xFF302F39),
    inverseOnSurface = Color(0xFFF3EFFA),
    inversePrimary = Color(0xFFBCA8FF)
)

/** Colors outside the Material scheme used for the mode state visuals. */
@Immutable
data class GhostColors(
    val activeGlow: Color,
    val activeGlowSoft: Color,
    val idleAccent: Color,
    val success: Color,
    val warning: Color
)

private val DarkGhostColors = GhostColors(
    activeGlow = Color(0xFF9B7CFF),
    activeGlowSoft = Color(0xFF5E3FD6),
    idleAccent = Color(0xFF7FE0CC),
    success = Color(0xFF7FE0A0),
    warning = Color(0xFFFFCC80)
)

private val LightGhostColors = GhostColors(
    activeGlow = Color(0xFF7B5CFF),
    activeGlowSoft = Color(0xFFB6A4FF),
    idleAccent = Color(0xFF00897B),
    success = Color(0xFF2E7D32),
    warning = Color(0xFFB26A00)
)

val LocalGhostColors = staticCompositionLocalOf { DarkGhostColors }

private val GhostShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val BaseTypography = Typography()

private val GhostTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

val MonoStyle = TextStyle(
    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 17.sp
)

@Composable
fun GhostModeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val ghostColors = if (darkTheme) DarkGhostColors else LightGhostColors
    androidx.compose.runtime.CompositionLocalProvider(LocalGhostColors provides ghostColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GhostTypography,
            shapes = GhostShapes,
            content = content
        )
    }
}
