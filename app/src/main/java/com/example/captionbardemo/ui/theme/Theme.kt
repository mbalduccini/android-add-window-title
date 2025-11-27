package com.example.captionbardemo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlueDark,
    onPrimaryContainer = Color.White,
    background = DeepNavyDark,
    onBackground = Color.White,
    surface = DeepNavy,
    onSurface = Color.White,
    secondary = AccentBlue,
    onSecondary = Color.White,
)

@Composable
fun CaptionBarDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
