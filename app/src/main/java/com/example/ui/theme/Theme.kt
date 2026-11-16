package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.AppThemeMode

private val JarvisDarkColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = Color(0xFF00222A),
    primaryContainer = Color(0xFF0C3847),
    onPrimaryContainer = Color(0xFFB6F4FF),
    secondary = ElectricAmber,
    onSecondary = Color(0xFF281800),
    secondaryContainer = Color(0xFF3B2606),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = EmeraldPulse,
    onTertiary = Color(0xFF00291B),
    background = ObsidianBg,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = ObsidianTextSecondary,
    outline = ObsidianOutline,
    error = CoralAlert,
    onError = Color(0xFF3A0010)
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = ArcCyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF083344),
    secondary = ElectricAmberDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = EmeraldPulseDeep,
    onTertiary = Color.White,
    background = DaylightBg,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceHigh,
    onSurfaceVariant = DaylightTextSecondary,
    outline = DaylightOutline,
    error = CoralAlertDeep,
    onError = Color.White
)

private val JarvisShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun JarvisTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) JarvisDarkColorScheme else JarvisLightColorScheme,
        shapes = JarvisShapes,
        typography = JarvisTypography,
        content = content
    )
}
