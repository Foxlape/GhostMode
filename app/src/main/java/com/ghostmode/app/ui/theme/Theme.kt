package com.ghostmode.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC5C0FF),
    onPrimary = Color(0xFF282E78),
    primaryContainer = Color(0xFF3F4790),
    onPrimaryContainer = Color(0xFFE2E0FF),
    secondary = Color(0xFFC5C4DC),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFFE7BAD6),
    onTertiary = Color(0xFF45263C),
    tertiaryContainer = Color(0xFF5D3C52),
    onTertiaryContainer = Color(0xFFFFD7F0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1EC),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1EC),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D4),
    outline = Color(0xFF918F9F)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4A55C4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E0FF),
    onPrimaryContainer = Color(0xFF130065),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF171A2C),
    tertiary = Color(0xFF7D4C68),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F0),
    onTertiaryContainer = Color(0xFF330724),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF767680)
)

@Composable
fun GhostModeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
