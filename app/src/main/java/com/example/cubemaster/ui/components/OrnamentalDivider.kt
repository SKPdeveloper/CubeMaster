package com.example.cubemaster.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cubemaster.ui.theme.CubeMasterColors

// Технічна hairline-лінія з акцентним сегментом по центру — читається як
// UI-індикатор/маркер прогресу, а не як декоративний орнамент.
@Composable
fun OrnamentalDivider(
    modifier: Modifier = Modifier,
    color: Color = CubeMasterColors.red,
    height: Dp = 8.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val midY = size.height / 2f

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    color.copy(alpha = 0f),
                    CubeMasterColors.redMuted,
                    color.copy(alpha = 0f)
                )
            ),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx()
        )

        val segmentWidth = 24.dp.toPx()
        val segmentHeight = 4.dp.toPx()
        val segmentLeft = (w - segmentWidth) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(segmentLeft, midY - segmentHeight / 2f),
            size = androidx.compose.ui.geometry.Size(segmentWidth, segmentHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(segmentHeight / 2f)
        )
    }
}

// Горизонтальна тонка лінія — простий розділювач для таблиць
@Composable
fun ThinDivider(
    modifier: Modifier = Modifier,
    color: Color = CubeMasterColors.divider
) {
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx()
        )
    }
}
