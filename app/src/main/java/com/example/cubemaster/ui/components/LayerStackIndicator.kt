package com.example.cubemaster.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cubemaster.core.model.Layer
import com.example.cubemaster.ui.theme.CubeMasterColors

// Ярусний індикатор шарів — метафора ярусної смуги рушника
@Composable
fun LayerStackIndicator(
    layers: List<Layer>,
    modifier: Modifier = Modifier
) {
    if (layers.isEmpty()) return

    val layerColors = listOf(
        CubeMasterColors.red.copy(alpha = 0.7f),
        CubeMasterColors.gold.copy(alpha = 0.6f),
        Color(0xFF5AC8FA).copy(alpha = 0.6f),
        Color(0xFF34C759).copy(alpha = 0.6f),
        Color(0xFF9B59B6).copy(alpha = 0.6f),
        CubeMasterColors.graphiteMid.copy(alpha = 0.5f)
    )

    // Розмір повністю визначає викликач через modifier — раніше тут форсовано
    // ставились fillMaxWidth()+height(32.dp), які перекривали передані менші
    // значення (16-24dp) і фіксовану ширину, через що смуга виглядала товстіше,
    // ніж задумано на кожному конкретному екрані.
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val totalLayers = layers.size
        val layerH = h / totalLayers

        layers.forEachIndexed { index, layer ->
            val color = layerColors[index % layerColors.size]
            val y = h - (index + 1) * layerH

            // Ярус
            drawRect(
                color = color,
                topLeft = Offset(0f, y),
                size = Size(w, (layerH - 1f).coerceAtLeast(1f))
            )

            // Орнаментальна штриховка — лише якщо ярус досить товстий, щоб її було видно;
            // на тонкій смузі з багатьма шарами штрихування лише зашумлює суцільний колір.
            val hatchHeight = layerH - 2f
            if (hatchHeight >= 2f) {
                val patternStep = 12f
                var px = 0f
                while (px < w) {
                    drawRect(
                        color = color.copy(alpha = color.alpha * 0.5f),
                        topLeft = Offset(px, y + 1f),
                        size = Size(4f, hatchHeight)
                    )
                    px += patternStep
                }
            }
        }
    }
}

@Composable
fun CompletionBadge(layersCount: Int, modifier: Modifier = Modifier) {
    val text = when {
        layersCount == 0 -> "Пустий пиріг"
        layersCount < 3 -> "$layersCount шар."
        else -> "$layersCount шарів"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
