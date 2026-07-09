package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TapestryRed,
    secondary = TapestryMustard,
    tertiary = TapestryGreen,
    background = TapestryDark,
    surface = Color(0xFF4C4132),
    onPrimary = TapestryLight,
    onSecondary = TapestryDark,
    onTertiary = TapestryLight,
    onBackground = TapestryLight,
    onSurface = TapestryLight
)

private val LightColorScheme = lightColorScheme(
    primary = TapestryRed,
    secondary = TapestryMustard,
    tertiary = TapestryBlue,
    background = TapestryLinenBg,
    surface = TapestryLinenCard,
    onPrimary = TapestryLight,
    onSecondary = TapestryDark,
    onTertiary = TapestryLight,
    onBackground = TapestryDark,
    onSurface = TapestryDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Let's keep the custom historical colors rather than overriding them
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
