package com.steady90.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF101A18)
val Panel = Color(0xFF1C2925)
val Lime = Color(0xFFD7EEA3)
val Mist = Color(0xFFA9BAB1)
val Peach = Color(0xFFF3BE98)
val Lilac = Color(0xFFCDC6EF)
val Cream = Color(0xFFF2F2E8)

@Composable fun SteadyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Ink, background = Ink,
        surface = Panel, onSurface = Cream, onBackground = Cream, secondary = Peach,
        surfaceVariant = Color(0xFF293931), onSurfaceVariant = Mist, outline = Color(0xFF4B5E52)),
        typography = Typography(
            displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 52.sp, lineHeight = 56.sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 39.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 1.sp)
        ), content = content)
}
