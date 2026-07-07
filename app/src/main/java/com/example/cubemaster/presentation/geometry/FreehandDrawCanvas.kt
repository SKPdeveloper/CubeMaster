package com.example.cubemaster.presentation.geometry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cubemaster.core.geometry.Vertex
import com.cubemaster.core.geometry.hasSelfIntersection
import com.cubemaster.core.geometry.simplifyPolyline
import com.cubemaster.core.geometry.snapToGrid
import com.cubemaster.core.geometry.verticesToEdges
import com.cubemaster.core.model.Edge
import com.example.cubemaster.ui.theme.CubeMasterColors

private const val GRID_STEP_M = 0.1
private val GRID_STEP_DP = 32.dp
private val SIMPLIFY_EPSILON_DP = 12.dp
private const val MIN_CLOSE_DISTANCE_DP = 40f
private const val VERTEX_HIT_RADIUS_DP = 28f

@Composable
fun FreehandDrawCanvas(
    onShapeConfirmed: (List<Edge>) -> Unit,
    modifier: Modifier = Modifier,
    backgroundImage: ImageBitmap? = null,
    calibratedPxPerMeter: Float? = null
) {
    val rawPath = remember { mutableStateListOf<Offset>() }
    var resultVertices by remember { mutableStateOf<List<Vertex>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var draggingVertexIndex by remember { mutableStateOf(-1) }
    val isCalibrated = calibratedPxPerMeter != null

    val density = LocalDensity.current
    val gridStepPx = with(density) { GRID_STEP_DP.toPx() }
    val pxPerMeter: Float = calibratedPxPerMeter ?: (gridStepPx / GRID_STEP_M).toFloat()
    val epsilonM = with(density) { SIMPLIFY_EPSILON_DP.toPx() } / pxPerMeter
    val vertexHitRadiusPx = with(density) { VERTEX_HIT_RADIUS_DP.dp.toPx() }

    fun finalizeDrawing() {
        if (rawPath.size < 3) {
            errorMessage = "Замало точок — спробуйте намалювати контур ще раз"
            rawPath.clear()
            return
        }
        val rawVertices = rawPath.map { Vertex((it.x / pxPerMeter).toDouble(), (it.y / pxPerMeter).toDouble()) }
        val simplified = simplifyPolyline(rawVertices, epsilonM = epsilonM.toDouble())
        var closed = simplified
        if (closed.size >= 2) {
            val first = closed.first()
            val last = closed.last()
            val closeDistPx = kotlin.math.hypot((first.x - last.x) * pxPerMeter, (first.y - last.y) * pxPerMeter)
            closed = if (closeDistPx < MIN_CLOSE_DISTANCE_DP) closed.dropLast(1) else closed
        }
        // Без каліброваного фото прилипаємо до сітки (немає іншого орієнтира);
        // з каліброваним фото сітка не прив'язана до реальних стін — використовуємо форму як є.
        val finalVertices = (if (isCalibrated) closed else closed.map { snapToGrid(it, GRID_STEP_M) }).distinct()
        if (finalVertices.size < 3) {
            errorMessage = "Не вдалось розпізнати контур — намалюйте кімнату чіткіше"
            rawPath.clear()
            return
        }
        if (hasSelfIntersection(finalVertices)) {
            errorMessage = "Стіни контуру перетинаються — намалюйте кімнату без самоперетинів"
            rawPath.clear()
            return
        }
        errorMessage = null
        resultVertices = finalVertices
    }

    Column(modifier = modifier) {
        Text(
            text = when {
                resultVertices != null -> "Перетягніть будь-яку точку, щоб виправити форму, потім натисніть \"Використати\""
                isCalibrated -> "Обведіть контур кімнати поверх плану одним рухом пальця"
                else -> "1 клітинка ≈ ${(GRID_STEP_M * 1000).toInt()} мм — намалюйте контур кімнати одним рухом пальця"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .pointerInput(resultVertices == null) {
                    if (resultVertices != null) return@pointerInput
                    detectDragGestures(
                        onDragStart = {
                            rawPath.clear()
                            errorMessage = null
                            rawPath.add(it)
                        },
                        onDrag = { change, _ -> rawPath.add(change.position) },
                        onDragEnd = { finalizeDrawing() }
                    )
                }
                .pointerInput(resultVertices != null) {
                    if (resultVertices == null) return@pointerInput
                    detectDragGestures(
                        onDragStart = { start ->
                            val points = resultVertices.orEmpty().map {
                                Offset((it.x * pxPerMeter).toFloat(), (it.y * pxPerMeter).toFloat())
                            }
                            val nearest = points.indices.minByOrNull { i -> (points[i] - start).getDistance() }
                            draggingVertexIndex = if (nearest != null && (points[nearest] - start).getDistance() <= vertexHitRadiusPx) nearest else -1
                        },
                        onDrag = { change, _ ->
                            val idx = draggingVertexIndex
                            val vertices = resultVertices
                            if (idx >= 0 && vertices != null) {
                                val updated = vertices.toMutableList()
                                updated[idx] = Vertex(
                                    (change.position.x / pxPerMeter).toDouble(),
                                    (change.position.y / pxPerMeter).toDouble()
                                )
                                resultVertices = updated
                            }
                        },
                        onDragEnd = {
                            val idx = draggingVertexIndex
                            val vertices = resultVertices
                            if (idx >= 0 && vertices != null && !isCalibrated) {
                                val updated = vertices.toMutableList()
                                updated[idx] = snapToGrid(updated[idx], GRID_STEP_M)
                                resultVertices = updated
                            }
                            draggingVertexIndex = -1
                        }
                    )
                }
        ) {
            backgroundImage?.let { bitmap ->
                drawImage(
                    image = bitmap,
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = 0.5f
                )
            }

            if (!isCalibrated) {
                val gridColor = Color.Gray.copy(alpha = 0.25f)
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                    x += gridStepPx
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
                    y += gridStepPx
                }
            }

            // Сирий шлях пальця (наживо під час малювання)
            if (resultVertices == null && rawPath.isNotEmpty()) {
                val path = Path()
                path.moveTo(rawPath.first().x, rawPath.first().y)
                rawPath.drop(1).forEach { path.lineTo(it.x, it.y) }
                drawPath(path, CubeMasterColors.red.copy(alpha = 0.6f), style = Stroke(width = 4f))
            }

            // Розпізнаний (спрощений і приліплений до сітки) контур
            resultVertices?.let { vertices ->
                val path = Path()
                val points = vertices.map { Offset((it.x * pxPerMeter).toFloat(), (it.y * pxPerMeter).toFloat()) }
                path.moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { path.lineTo(it.x, it.y) }
                path.close()
                drawPath(path, CubeMasterColors.success.copy(alpha = 0.15f))
                drawPath(path, CubeMasterColors.success, style = Stroke(width = 5f))
                points.forEachIndexed { i, p ->
                    val isDragged = i == draggingVertexIndex
                    drawCircle(CubeMasterColors.success, if (isDragged) 14f else 8f, p)
                    if (isDragged) drawCircle(CubeMasterColors.success.copy(alpha = 0.3f), vertexHitRadiusPx, p)
                }
            }
        }

        errorMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(8.dp))
        if (resultVertices != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        rawPath.clear()
                        resultVertices = null
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Стерти і намалювати знову") }
                Button(
                    onClick = {
                        val vertices = resultVertices ?: return@Button
                        onShapeConfirmed(verticesToEdges(vertices))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CubeMasterColors.red)
                ) { Text("Використати") }
            }
        }
    }
}
