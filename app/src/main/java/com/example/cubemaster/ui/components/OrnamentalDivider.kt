package com.example.cubemaster.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cubemaster.ui.theme.CubeMasterColors

// Геометричний розділювач у стилі кролевецького орнаменту
// Повторюваний зигзаг з ромбами — горизонтальна смуга
@Composable
fun OrnamentalDivider(
    modifier: Modifier = Modifier,
    color: Color = CubeMasterColors.red.copy(alpha = 0.35f),
    height: Dp = 8.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        val stepW = 20f
        val midY = h / 2f
        val amplitude = h * 0.4f

        val path = Path()
        var x = 0f
        var up = true
        path.moveTo(x, midY)
        while (x < w) {
            val nextX = (x + stepW).coerceAtMost(w)
            val nextY = if (up) midY - amplitude else midY + amplitude
            path.lineTo(nextX, nextY)
            x = nextX
            up = !up
        }
        drawPath(path, color, style = Stroke(width = 1.dp.toPx()))

        // Маленькі ромби на піках
        x = stepW
        up = false
        val diamondSize = 3f
        while (x < w - stepW) {
            val cy = if (!up) midY - amplitude else midY + amplitude
            val diamondPath = Path().apply {
                moveTo(x, cy - diamondSize)
                lineTo(x + diamondSize, cy)
                lineTo(x, cy + diamondSize)
                lineTo(x - diamondSize, cy)
                close()
            }
            drawPath(diamondPath, color)
            x += stepW * 2
            up = !up
        }
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
