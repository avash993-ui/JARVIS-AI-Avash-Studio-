package com.jarvis.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF03060C)
val Gold = Color(0xFFFFB300)
val GoldText = Color(0xFFFFE7B0)
val Panel = Color(0xFF0C1320)
val BarGray = Color(0xFF141B28)
val Cyan = Color(0xFF00E5FF)
val TileBorder = Color(0xFF0B5C6B)
val TileBg = Color(0xFF05080F)
val Muted = Color(0xFF8A94A6)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Color.Black, secondary = Cyan,
            background = Bg, onBackground = GoldText, surface = Panel, onSurface = GoldText,
            surfaceVariant = BarGray, onSurfaceVariant = Muted,
        ),
        content = content,
    )
}
