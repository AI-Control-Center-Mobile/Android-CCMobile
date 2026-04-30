package com.ivnsrg.aicontrolcentre.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val AppBackground = Color(0xFF081019)
val SurfacePrimary = Color(0xFF0F1724)
val SurfaceSecondary = Color(0xFF142033)
val SurfaceTertiary = Color(0xFF1A2A3D)
val Stroke = Color(0xFF24364A)
val AccentPrimary = Color(0xFF36D39A)
val AccentInfo = Color(0xFF6FB6FF)
val AccentWarning = Color(0xFFFFB454)
val AccentDanger = Color(0xFFFF6B6B)
val TextPrimary = Color(0xFFF3F7FB)
val TextSecondary = Color(0xFF93A4B8)
val TextMuted = Color(0xFF6E8196)

private val AppColors = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = AppBackground,
    secondary = AccentInfo,
    onSecondary = AppBackground,
    tertiary = AccentWarning,
    onTertiary = AppBackground,
    error = AccentDanger,
    onError = TextPrimary,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfacePrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSecondary,
    onSurfaceVariant = TextSecondary,
    outline = Stroke,
    outlineVariant = SurfaceTertiary,
)

@Composable
fun AiControlCentreTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = AppColors,
            typography = AppTypography,
            content = content,
        )
    }
}
