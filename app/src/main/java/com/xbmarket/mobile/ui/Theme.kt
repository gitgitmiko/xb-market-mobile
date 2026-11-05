package com.xbmarket.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palet hijau Xbox di atas latar arang kehijauan. */
private val Colors = darkColorScheme(
    primary = Color(0xFF3DDC84),
    onPrimary = Color(0xFF06140C),
    primaryContainer = Color(0xFF173A26),
    onPrimaryContainer = Color(0xFFA8F5C8),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF1A1405),
    secondaryContainer = Color(0xFF1F4A31),
    onSecondaryContainer = Color(0xFFC4F7D8),
    tertiary = Color(0xFF6FB7FF),
    background = Color(0xFF0E1210),
    surface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFF232A26),
    onSurfaceVariant = Color(0xFFBCC8C0),
    outline = Color(0xFF4F5A53),
    onBackground = Color(0xFFEDF2EE),
    onSurface = Color(0xFFEDF2EE),
    error = Color(0xFFFF6B6B),
)

/** Warna badge diskon. */
val SaleColor = Color(0xFFFFC857)

@Composable
fun XbMarketTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        Surface(color = Colors.background, contentColor = Colors.onBackground, content = content)
    }
}
