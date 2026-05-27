package com.example.myapplication.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FAEFF),
    onPrimary = Color(0xFF071B4D),
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF111318),
    onBackground = Color(0xFFF2F4FA),
    surface = Color(0xFF1A1D24),
    onSurface = Color(0xFFF2F4FA),
    surfaceVariant = Color(0xFF252A33),
    onSurfaceVariant = Color(0xFFC3C7D4),
    outline = Color(0xFF687083),
    outlineVariant = Color(0xFF343A46),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = ScreenBg,
    onBackground = Color(0xFF1F1F1F),
    surface = Color.White,
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFF0F1F5),
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = Color(0xFFF0F1F5)
)

@Composable
fun KeepMomentsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
