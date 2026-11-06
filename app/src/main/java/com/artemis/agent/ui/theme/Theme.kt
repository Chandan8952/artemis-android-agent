package com.artemis.agent.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF1E1E2E)
val SurfaceDark = Color(0xFF252538)
val PrimaryPurple = Color(0xFFCBA6F7)
val SecondaryTeal = Color(0xFF89DCEB)
val SuccessGreen = Color(0xFFA6E3A1)
val WarningYellow = Color(0xFFF9E2AF)
val ErrorRed = Color(0xFFF38BA8)
val TextLight = Color(0xFFCDD6F4)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    secondary = SecondaryTeal,
    background = DarkBackground,
    surface = SurfaceDark,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextLight,
    onSurface = TextLight,
    error = ErrorRed
)

@Composable
fun ArtemisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
