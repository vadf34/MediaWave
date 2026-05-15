package com.mediawave.downloader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Brand colors - deep space purple palette
val Purple10 = Color(0xFF0D0A1F)
val Purple15 = Color(0xFF110E28)
val Purple20 = Color(0xFF1A1535)
val Purple30 = Color(0xFF252046)
val Purple40 = Color(0xFF3A3270)
val Purple50 = Color(0xFF4E458F)
val Purple60 = Color(0xFF6C63FF)
val Purple70 = Color(0xFF8880FF)
val Purple80 = Color(0xFFB3AFFF)
val Purple90 = Color(0xFFD9D7FF)
val Purple95 = Color(0xFFECEBFF)

val Accent = Color(0xFF6C63FF)
val AccentLight = Color(0xFF8880FF)
val AccentDark = Color(0xFF4A43CF)

val DarkBg = Color(0xFF0D0D1A)
val DarkSurface = Color(0xFF16162A)
val DarkSurfaceVariant = Color(0xFF1E1E3A)
val DarkCard = Color(0xFF1A1A2E)
val DarkOutline = Color(0xFF2D2D50)

val TextPrimary = Color(0xFFE8E8FF)
val TextSecondary = Color(0xFF9999CC)
val TextTertiary = Color(0xFF5C5C8A)

val ErrorColor = Color(0xFFFF6B6B)
val SuccessColor = Color(0xFF6BFF9E)
val WarningColor = Color(0xFFFFD166)

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Purple30,
    onPrimaryContainer = Purple90,
    secondary = Purple70,
    onSecondary = Color.White,
    secondaryContainer = Purple20,
    onSecondaryContainer = Purple80,
    tertiary = AccentLight,
    onTertiary = Color.White,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkOutline,
    outlineVariant = Color(0xFF232340),
    error = ErrorColor,
    onError = Color.White,
    scrim = Color(0x80000000),
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Purple90,
    onPrimaryContainer = Purple20,
    secondary = Purple50,
    onSecondary = Color.White,
    secondaryContainer = Purple95,
    onSecondaryContainer = Purple20,
    tertiary = AccentDark,
    onTertiary = Color.White,
    background = Color(0xFFF5F5FF),
    onBackground = Color(0xFF1A1A35),
    surface = Color.White,
    onSurface = Color(0xFF1A1A35),
    surfaceVariant = Color(0xFFECEBFF),
    onSurfaceVariant = Color(0xFF4A4870),
    outline = Color(0xFFB8B6D9),
    error = Color(0xFFB00020),
    onError = Color.White,
)

@Composable
fun MediaWaveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MediaWaveTypography,
        content = content,
    )
}
