package com.example.dndsound.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.dndsound.core.repo.AppSettings

/**
 * Tavern/amber palette: smoked-oak dark surfaces, candle-amber primary and
 * parchment text. Both schemes stay dark — a true light theme arrives with
 * the settings screen (stage 10).
 */
private val TavernAmber = darkColorScheme(
    primary = Color(0xFFE8B45A),
    onPrimary = Color(0xFF241A0C),
    primaryContainer = Color(0xFF5C3D14),
    onPrimaryContainer = Color(0xFFFFE0A6),
    secondary = Color(0xFFD3B078),
    onSecondary = Color(0xFF2A1F10),
    secondaryContainer = Color(0xFF4A3620),
    onSecondaryContainer = Color(0xFFF2DDB8),
    tertiary = Color(0xFFCC7B58),
    onTertiary = Color(0xFF2B130A),
    tertiaryContainer = Color(0xFF5C2E1C),
    onTertiaryContainer = Color(0xFFFFD9C9),
    background = Color(0xFF171310),
    onBackground = Color(0xFFF2E9D8),
    surface = Color(0xFF1F1914),
    onSurface = Color(0xFFF2E9D8),
    surfaceVariant = Color(0xFF32291F),
    onSurfaceVariant = Color(0xFFD4C5A8),
    error = Color(0xFFF28B82),
    onError = Color(0xFF2B0B0B),
    errorContainer = Color(0xFF5C1A14),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = Color(0xFF9C8A6C),
)

/** Neutral dark scheme kept for the secondary "Тёмная" look. */
private val NeutralDark = darkColorScheme()

/** Warm gradient painted behind every screen. */
val AppBackdrop: Brush = Brush.verticalGradient(
    listOf(Color(0xFF261C11), Color(0xFF191410), Color(0xFF110E0A)),
)

/**
 * Serif display for titles (fantasy-tavern feel), the platform sans for body
 * text. No bundled font files — system serifs keep the APK lean and the
 * license clean.
 */
val AppTypography: Typography = run {
    val base = Typography()
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Serif),
        displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Serif),
        displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif),
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun DnDSoundTheme(theme: AppSettings.Theme = AppSettings.Theme.AMBER, content: @Composable () -> Unit) {
    val colorScheme = when (theme) {
        AppSettings.Theme.DARK -> NeutralDark
        AppSettings.Theme.AMBER -> TavernAmber
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
    ) {
        // Surface sets LocalContentColor for every plain Text: without it the
        // default black text lands on the dark backdrop and becomes unreadable.
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            content()
        }
    }
}
