package com.dynamicisland.android.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppleDarkColorScheme = darkColorScheme(
    primary = AppleBlue,
    secondary = ApplePurple,
    tertiary = AppleGreen,
    background = AppleBlack,
    surface = AppleDarkCard,
    surfaceVariant = AppleDarkCardSecondary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = AppleTextPrimary,
    onSurface = AppleTextPrimary,
    outline = AppleGlassBorder
)

@Composable
fun DynamicIslandTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppleDarkColorScheme,
        typography = Typography,
        content = content
    )
}

