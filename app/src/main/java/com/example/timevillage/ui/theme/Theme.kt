package com.example.timevillage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784), // Зеленый для деревни
    secondary = Color(0xFFFFB74D), // Оранжевый для таймера
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

@Composable
fun SemestrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}