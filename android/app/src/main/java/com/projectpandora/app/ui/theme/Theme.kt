package com.projectpandora.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PandoraColors {
    val Amber = Color(0xFFFDC848)
    val AmberPale = Color(0xFFFFF3D6)
    val Ink = Color(0xFF1C1F24)
    val Muted = Color(0xFF666666)
    val Line = Color(0xFFE2E7EC)
    val Orange = Color(0xFFC88622)
    val Selected = Color(0xFFBA6110)
    val Pressed = Color(0xFFD76E12)
    val Error = Color(0xFFB3261E)
    val ErrorPale = Color(0xFFFFF0EE)
    val ErrorBorder = Color(0xFFF1B9B5)
    val Blue = Color(0xFF175A9E)
    val BluePale = Color(0xFFEAF3FF)
    val Teal = Color(0xFF126B57)
    val TealPale = Color(0xFFE7F8F3)
    val Violet = Color(0xFF7356BB)
    val VioletPale = Color(0xFFF1EDFF)
}

@Composable
fun PandoraAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PandoraColors.Amber,
            onPrimary = PandoraColors.Ink,
            primaryContainer = PandoraColors.AmberPale,
            onPrimaryContainer = PandoraColors.Ink,
            background = Color.White,
            surface = Color.White,
            onSurface = PandoraColors.Ink,
            onBackground = PandoraColors.Ink,
            onSurfaceVariant = PandoraColors.Muted,
            outline = PandoraColors.Line,
            error = PandoraColors.Error,
            errorContainer = PandoraColors.ErrorPale,
        ),
        typography = Typography,
        content = content,
    )
}
