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

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
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
                size = Size(w, layerH - 1f)
            )

            // Орнаментальна штриховка на ярусі — невелика геометрія
            val patternStep = 12f
            var px = 0f
            while (px < w) {
                drawRect(
                    color = color.copy(alpha = color.alpha * 0.5f),
                    topLeft = Offset(px, y + 1f),
                    size = Size(4f, layerH - 2f)
                )
                px += patternStep
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
