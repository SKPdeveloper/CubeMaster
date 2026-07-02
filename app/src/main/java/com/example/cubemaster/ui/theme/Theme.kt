package com.example.cubemaster.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CubeMasterColors.red,
    onPrimary = CubeMasterColors.white,
    primaryContainer = CubeMasterColors.redDeep,
    onPrimaryContainer = CubeMasterColors.linen,
    secondary = CubeMasterColors.gold,
    onSecondary = CubeMasterColors.graphite,
    background = CubeMasterColors.graphite,
    onBackground = CubeMasterColors.linen,
    surface = CubeMasterColors.graphiteSoft,
    onSurface = CubeMasterColors.linen,
    surfaceVariant = CubeMasterColors.graphiteMid,
    onSurfaceVariant = CubeMasterColors.textSecondary,
    error = CubeMasterColors.error,
    outline = CubeMasterColors.graphiteMid
)

private val LightColorScheme = lightColorScheme(
    primary = CubeMasterColors.red,
    onPrimary = CubeMasterColors.white,
    primaryContainer = Color(0xFFFFE8EA),
    onPrimaryContainer = CubeMasterColors.redDeep,
    secondary = CubeMasterColors.gold,
    onSecondary = CubeMasterColors.graphite,
    background = CubeMasterColors.linen,
    onBackground = CubeMasterColors.textPrimary,
    surface = CubeMasterColors.white,
    onSurface = CubeMasterColors.textPrimary,
    surfaceVariant = Color(0xFFEFEBE3),
    onSurfaceVariant = CubeMasterColors.textSecondary,
    error = CubeMasterColors.error,
    outline = CubeMasterColors.divider
)

@Composable
fun CubeMasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
