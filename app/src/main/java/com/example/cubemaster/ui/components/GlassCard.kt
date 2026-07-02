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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cubemaster.ui.theme.CubeMasterColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == CubeMasterColors.graphite
    val glassBg = if (isDark) CubeMasterColors.glassSurfaceDark else CubeMasterColors.glassSurfaceLight
    val borderColor = if (isDark)
        Color.White.copy(alpha = 0.08f)
    else
        Color.White.copy(alpha = 0.6f)

    val shape = RoundedCornerShape(cornerRadius)

    Column(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 4.dp else 8.dp,
                shape = shape,
                ambientColor = if (isDark) Color.Black.copy(0.4f) else Color.Black.copy(0.08f),
                spotColor = if (isDark) Color.Black.copy(0.4f) else CubeMasterColors.red.copy(0.04f)
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                    )
                )
            )
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(modifier = modifier, cornerRadius = 12.dp, onClick = onClick, content = content)
}
