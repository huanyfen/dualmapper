package com.example.dualmapper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 导出颜色方案以便在非主题上下文中使用
val DualMapperColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = TextPrimary,
    onSurface = TextPrimary,
    error = TextError,
    onBackground = TextPrimary,
    onSecondary = TextPrimary,
    onError = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryDark,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onPrimary = Color.White,
    onSurface = Color(0xFF1C1B1F),
    error = TextError,
    onBackground = Color(0xFF1C1B1F),
    onSecondary = Color.White,
    onError = Color.White
)

@Composable
fun DualGameMapperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DualMapperColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}