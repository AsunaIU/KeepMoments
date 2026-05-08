package com.example.myapplication.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
val Blue40 = Color(0xFF2456CB)
val SunColor = Color(0xFFFF6F61)

val ScreenBg = Color(0xFFF8F8FC)
val PrimarySoft = Color(0xFFF2EEFF)
val Border = Color(0xFFD7D3F5)
val TextSecondary = Color(0xFF8B8B9A)

@Composable
fun appBackground(): Color = MaterialTheme.colorScheme.background

@Composable
fun appAuthBackground(): Color = if (MaterialTheme.colorScheme.background == ScreenBg) {
    Color(0xDDE7DFDF)
} else {
    MaterialTheme.colorScheme.background
}

@Composable
fun appSurface(): Color = MaterialTheme.colorScheme.surface

@Composable
fun appSurfaceVariant(): Color = MaterialTheme.colorScheme.surfaceVariant

@Composable
fun appTextPrimary(): Color = MaterialTheme.colorScheme.onBackground

@Composable
fun appTextSecondary(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun appBorder(): Color = MaterialTheme.colorScheme.outlineVariant
