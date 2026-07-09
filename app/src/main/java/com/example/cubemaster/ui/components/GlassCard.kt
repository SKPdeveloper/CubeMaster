package com.example.cubemaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cubemaster.ui.theme.CubeMasterColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Скляна картка. Якщо переданий [hazeState] (спільний на весь екран, від
 * `rememberHazeState()`), картка справді розмиває вміст позаду себе — той екран
 * повинен мати `Modifier.hazeSource(hazeState)` на своєму фоновому контейнері.
 * Без [hazeState] (наприклад, картка всередині модального діалогу над системним
 * scrim, де немає що розмивати) — деградує до статичного тонованого фону.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == CubeMasterColors.graphite
    val borderColor = if (isDark)
        Color.White.copy(alpha = 0.08f)
    else
        Color.White.copy(alpha = 0.6f)

    val shape = RoundedCornerShape(cornerRadius)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 4.dp else 8.dp,
                shape = shape,
                ambientColor = if (isDark) Color.Black.copy(0.4f) else Color.Black.copy(0.08f),
                spotColor = if (isDark) CubeMasterColors.redGlow else CubeMasterColors.red.copy(0.04f)
            )
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = surfaceColor,
                            tint = HazeTint(surfaceColor.copy(alpha = if (isDark) 0.55f else 0.65f)),
                            blurRadius = 20.dp,
                            noiseFactor = 0.15f
                        )
                    )
                } else {
                    Modifier.background(surfaceColor.copy(alpha = if (isDark) 0.82f else 0.92f))
                }
            )
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}
