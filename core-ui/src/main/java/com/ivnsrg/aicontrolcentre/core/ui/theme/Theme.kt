package com.ivnsrg.aicontrolcentre.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val stroke: Color,
    val accentPrimary: Color,
    val accentInfo: Color,
    val accentWarning: Color,
    val accentDanger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
)

private val OperationalPalette = AppColors(
    background = Color(0xFF081019),
    surface1 = Color(0xFF0F1724),
    surface2 = Color(0xFF142033),
    surface3 = Color(0xFF1A2A3D),
    stroke = Color(0xFF24364A),
    accentPrimary = Color(0xFF36D39A),
    accentInfo = Color(0xFF6FB6FF),
    accentWarning = Color(0xFFFFB454),
    accentDanger = Color(0xFFFF6B6B),
    textPrimary = Color(0xFFF3F7FB),
    textSecondary = Color(0xFF93A4B8),
    textMuted = Color(0xFF6E8196),
)

private val AppColorScheme: ColorScheme = darkColorScheme(
    primary = OperationalPalette.accentPrimary,
    onPrimary = OperationalPalette.background,
    primaryContainer = OperationalPalette.surface3,
    onPrimaryContainer = OperationalPalette.textPrimary,
    secondary = OperationalPalette.accentInfo,
    onSecondary = OperationalPalette.background,
    secondaryContainer = OperationalPalette.surface2,
    onSecondaryContainer = OperationalPalette.textPrimary,
    tertiary = OperationalPalette.accentWarning,
    onTertiary = OperationalPalette.background,
    background = OperationalPalette.background,
    onBackground = OperationalPalette.textPrimary,
    surface = OperationalPalette.surface1,
    onSurface = OperationalPalette.textPrimary,
    surfaceVariant = OperationalPalette.surface2,
    onSurfaceVariant = OperationalPalette.textSecondary,
    error = OperationalPalette.accentDanger,
    onError = OperationalPalette.textPrimary,
    outline = OperationalPalette.stroke,
    outlineVariant = OperationalPalette.surface3,
    surfaceTint = OperationalPalette.accentPrimary,
)

val LocalAppColors = staticCompositionLocalOf { OperationalPalette }

@Composable
fun AiControlCentreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = OperationalPalette
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalAppColors provides colors,
    ) {
        MaterialTheme(
            colorScheme = AppColorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
