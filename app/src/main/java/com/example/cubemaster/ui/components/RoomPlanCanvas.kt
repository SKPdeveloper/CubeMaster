package com.example.cubemaster.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubemaster.core.geometry.Vertex
import com.cubemaster.core.geometry.closestPointOnSegment
import com.cubemaster.core.geometry.distance
import com.cubemaster.core.geometry.projectionRatio
import com.cubemaster.core.model.Opening
import com.cubemaster.core.model.OpeningKind
import com.example.cubemaster.ui.theme.CubeMasterColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

// Кімната, вже готова до малювання: вершини у спільній системі координат виклику
// (для однієї кімнати — просто локальні координати; для плану об'єкта — вже трансформовані
// через GeometryEngine.transformVertices на originXM/originYM/rotationDeg).
data class PlacedRoom(
    val roomId: String,
    val label: String,
    val vertices: List<Vertex>,
    val openings: List<Opening> = emptyList(),
    val hasSelfIntersection: Boolean = false
)

enum class PlanInteractionMode { SingleRoomEdit, ObjectOverview }

private const val OPENING_HIT_RADIUS_DP = 24f
private const val WALL_HIT_THRESHOLD_DP = 24f
private const val TAP_SLOP_DP = 12f
private const val CANVAS_PADDING_PX = 40f

private class PlanTransform(
    val minX: Double,
    val minY: Double,
    val scaleXY: Double,
    val canvasHeight: Float,
    val padding: Float,
    val panOffset: Offset
) {
    fun toCanvas(v: Vertex): Offset {
        val x = padding + (v.x - minX) * scaleXY + panOffset.x
        val y = canvasHeight - padding - (v.y - minY) * scaleXY + panOffset.y
        return Offset(x.toFloat(), y.toFloat())
    }

    fun toScene(o: Offset): Vertex {
        val x = (o.x - padding - panOffset.x) / scaleXY + minX
        val y = (canvasHeight - padding - o.y + panOffset.y) / scaleXY + minY
        return Vertex(x, y)
    }
}

private fun computeTransform(rooms: List<PlacedRoom>, canvasSize: Size, scale: Float, panOffset: Offset): PlanTransform {
    val allVertices = rooms.flatMap { it.vertices }
    if (allVertices.isEmpty()) {
        return PlanTransform(0.0, 0.0, 1.0, canvasSize.height, CANVAS_PADDING_PX, panOffset)
    }
    val minX = allVertices.minOf { it.x }
    val maxX = allVertices.maxOf { it.x }
    val minY = allVertices.minOf { it.y }
    val maxY = allVertices.maxOf { it.y }
    val rangeX = (maxX - minX).coerceAtLeast(0.001)
    val rangeY = (maxY - minY).coerceAtLeast(0.001)
    val scaleXY = minOf(
        (canvasSize.width - CANVAS_PADDING_PX * 2) / rangeX,
        (canvasSize.height - CANVAS_PADDING_PX * 2) / rangeY
    ) * scale
    return PlanTransform(minX, minY, scaleXY, canvasSize.height, CANVAS_PADDING_PX, panOffset)
}

private fun pointInPolygon(poly: List<Offset>, p: Offset): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val vi = poly[i]
        val vj = poly[j]
        if ((vi.y > p.y) != (vj.y > p.y) &&
            p.x < (vj.x - vi.x) * (p.y - vi.y) / (vj.y - vi.y) + vi.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

fun openingColor(kind: OpeningKind): Color = when (kind) {
    OpeningKind.Door -> CubeMasterColors.graphite
    OpeningKind.Window -> CubeMasterColors.gold
    OpeningKind.Passage -> CubeMasterColors.graphiteMid
    OpeningKind.Vent -> CubeMasterColors.success
    OpeningKind.Niche -> CubeMasterColors.textSecondary
}

fun openingKindLabel(kind: OpeningKind): String = when (kind) {
    OpeningKind.Window -> "Вікно"
    OpeningKind.Door -> "Двері"
    OpeningKind.Passage -> "Прохід"
    OpeningKind.Vent -> "Вентиляція"
    OpeningKind.Niche -> "Ніша/інше"
}

// Спільний рушій рендеру й жестів для кімнатного та об'єктного редакторів — уніфікація
// GeometryScreen (один контур, інтерактивні прорізи) та ObjectPlanScreen (кілька контурів,
// інтерактивне перетягування кімнат) в одному компоненті.
@Composable
fun RoomPlanCanvas(
    rooms: List<PlacedRoom>,
    mode: PlanInteractionMode,
    modifier: Modifier = Modifier,
    onWallTap: ((roomId: String, wallIndex: Int, offsetMm: Int) -> Unit)? = null,
    onOpeningDrag: ((openingId: String, newOffsetMm: Int) -> Unit)? = null,
    onOpeningTap: ((openingId: String) -> Unit)? = null,
    onRoomTap: ((roomId: String) -> Unit)? = null,
    onRoomDrag: ((roomId: String, deltaXM: Double, deltaYM: Double) -> Unit)? = null,
    onRoomDragEnd: ((roomId: String) -> Unit)? = null,
    onVertexDrag: ((roomId: String, index: Int, newVertex: Vertex) -> Unit)? = null,
    onWallLongPress: ((roomId: String, wallIndex: Int, atPoint: Vertex) -> Unit)? = null,
    onVertexLongPress: ((roomId: String, index: Int) -> Unit)? = null
) {
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val density = LocalDensity.current
    val labelTextSizePx = with(density) { 11.sp.toPx() }
    val roomLabelTextSizePx = with(density) { 13.sp.toPx() }
    val openingHitRadiusPx = with(density) { OPENING_HIT_RADIUS_DP.dp.toPx() }
    val wallHitThresholdPx = with(density) { WALL_HIT_THRESHOLD_DP.dp.toPx() }
    val tapSlopPx = with(density) { TAP_SLOP_DP.dp.toPx() }

    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val latestRooms by rememberUpdatedState(rooms)
    // Скоуп, прив'язаний до композиції (скасовується, коли RoomPlanCanvas покидає
    // композицію) і працює на тому ж диспетчері, що й решта composition (головний потік) —
    // на відміну від GlobalScope, який не є структурним нащадком нічого і виконується
    // на Dispatchers.Default (фоновий потік). awaitPointerEvent() — restricted-suspension
    // функція (AwaitPointerEventScope позначений @RestrictsSuspension), тому цикл
    // while(true) з awaitPointerEvent() НЕ можна обгорнути в coroutineScope { launch { } }
    // (компілятор забороняє виклик restricted-функцій з вкладеної suspend-лямбди іншого
    // типу) — натомість: launch запускається на цьому scope (сам виклик launch —
    // не suspend-функція, тому обмеження не порушується), а гарантоване скасування
    // на БУДЬ-якому шляху виходу з циклу (включно із зовнішнім скасуванням жестової
    // корутини під час підвішеного awaitPointerEvent()) забезпечує try/finally навколо
    // циклу.
    val gestureScope = rememberCoroutineScope()

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor.copy(alpha = 0.5f))
            .pointerInput(mode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val gestureRooms = latestRooms
                    val transform = computeTransform(gestureRooms, Size(size.width.toFloat(), size.height.toFloat()), scale, panOffset)

                    var targetVertex: Pair<Int, PlacedRoom>? = null

                    if (mode == PlanInteractionMode.SingleRoomEdit && gestureRooms.isNotEmpty()) {
                        val room = gestureRooms.first()
                        var bestVertexDist = Float.MAX_VALUE
                        var bestVertexIndex = -1
                        room.vertices.forEachIndexed { i, v ->
                            val d = (transform.toCanvas(v) - down.position).getDistance()
                            if (d < bestVertexDist) { bestVertexDist = d; bestVertexIndex = i }
                        }
                        if (bestVertexDist <= wallHitThresholdPx && bestVertexIndex >= 0) {
                            targetVertex = bestVertexIndex to room
                        }
                    }

                    var targetOpening: Opening? = null
                    var targetRoom: PlacedRoom? = null
                    var targetWall: Pair<Int, PlacedRoom>? = null

                    if (targetVertex == null) {
                        if (mode == PlanInteractionMode.SingleRoomEdit && gestureRooms.isNotEmpty()) {
                            val room = gestureRooms.first()
                            val n = room.vertices.size
                            var bestDist = Float.MAX_VALUE
                            room.openings.forEach { o ->
                                if (o.wallEdgeIndex !in 0 until n) return@forEach
                                val a = room.vertices[o.wallEdgeIndex]
                                val b = room.vertices[(o.wallEdgeIndex + 1) % n]
                                val wallLenMm = distance(a, b) * 1000
                                if (wallLenMm <= 0) return@forEach
                                val tMid = ((o.offsetMm + o.widthMm / 2.0) / wallLenMm).coerceIn(0.0, 1.0)
                                val mid = Vertex(a.x + (b.x - a.x) * tMid, a.y + (b.y - a.y) * tMid)
                                val d = (transform.toCanvas(mid) - down.position).getDistance()
                                if (d < bestDist) { bestDist = d; targetOpening = o }
                            }
                            if (bestDist > openingHitRadiusPx) targetOpening = null

                            if (targetOpening == null && n >= 3) {
                                var bestWallDist = Float.MAX_VALUE
                                var bestWallIndex = -1
                                val scenePoint = transform.toScene(down.position)
                                for (i in 0 until n) {
                                    val a = room.vertices[i]
                                    val b = room.vertices[(i + 1) % n]
                                    val closest = closestPointOnSegment(a, b, scenePoint)
                                    val d = (transform.toCanvas(closest) - down.position).getDistance()
                                    if (d < bestWallDist) { bestWallDist = d; bestWallIndex = i }
                                }
                                if (bestWallDist <= wallHitThresholdPx && bestWallIndex >= 0) {
                                    targetWall = bestWallIndex to room
                                }
                            }
                        } else if (mode == PlanInteractionMode.ObjectOverview) {
                            targetRoom = gestureRooms.firstOrNull { room ->
                                room.vertices.size >= 3 && pointInPolygon(room.vertices.map { transform.toCanvas(it) }, down.position)
                            }
                        }
                    }

                    when {
                        targetVertex != null -> {
                            down.consume()
                            val (vertexIndex, room) = targetVertex!!
                            var totalDrag = Offset.Zero
                            var longPressFired = false
                            // gestureScope замість GlobalScope: launch без явного диспетчера
                            // успадковує головний потік композиції (Critical #3 — жодної гонки
                            // з totalDrag/longPressFired), а сам scope скасовується разом із
                            // виходом RoomPlanCanvas з композиції. Гарантоване скасування
                            // longPressJob на БУДЬ-якому шляху виходу з циклу — включно з
                            // зовнішнім скасуванням жестової корутини (зміна mode ↔
                            // pointerInput(mode), поки awaitPointerEvent() підвішений,
                            // Critical #2) — забезпечує try/finally: finally виконується
                            // і при звичайному break, і при CancellationException.
                            val longPressJob = gestureScope.launch {
                                delay(500)
                                if (totalDrag.getDistance() < tapSlopPx) {
                                    longPressFired = true
                                    onVertexLongPress?.invoke(room.roomId, vertexIndex)
                                }
                            }
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        break
                                    }
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    totalDrag += change.positionChange()
                                    change.consume()
                                    if (totalDrag.getDistance() >= tapSlopPx && !longPressFired) {
                                        longPressJob.cancel()
                                        val scenePoint = transform.toScene(change.position)
                                        onVertexDrag?.invoke(room.roomId, vertexIndex, scenePoint)
                                    }
                                }
                            } finally {
                                longPressJob.cancel()
                            }
                        }
                        targetOpening != null -> {
                            down.consume()
                            val opening = targetOpening!!
                            val room = gestureRooms.first()
                            val n = room.vertices.size
                            val a = room.vertices[opening.wallEdgeIndex]
                            val b = room.vertices[(opening.wallEdgeIndex + 1) % n]
                            val wallLenMm = distance(a, b) * 1000
                            var totalDrag = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    if (totalDrag.getDistance() < tapSlopPx) onOpeningTap?.invoke(opening.id)
                                    break
                                }
                                totalDrag += change.positionChange()
                                change.consume()
                                val scenePoint = transform.toScene(change.position)
                                val t = projectionRatio(a, b, scenePoint)
                                val rawOffset = (t * wallLenMm - opening.widthMm / 2.0).roundToInt()
                                val maxOffset = (wallLenMm - opening.widthMm).coerceAtLeast(0.0).roundToInt()
                                onOpeningDrag?.invoke(opening.id, rawOffset.coerceIn(0, maxOffset))
                            }
                        }
                        targetWall != null -> {
                            val (wallIndex, room) = targetWall!!
                            var totalDrag = Offset.Zero
                            var longPressFired = false
                            // gestureScope замість GlobalScope — те саме обґрунтування, що й
                            // для гілки вершини вище: головний потік + try/finally гарантує
                            // скасування на всіх шляхах виходу з циклу, включно із зовнішнім
                            // скасуванням жестової корутини під час підвішеного
                            // awaitPointerEvent().
                            val longPressJob = gestureScope.launch {
                                delay(500)
                                if (totalDrag.getDistance() < tapSlopPx) {
                                    longPressFired = true
                                    val n = room.vertices.size
                                    val a = room.vertices[wallIndex]
                                    val b = room.vertices[(wallIndex + 1) % n]
                                    val scenePoint = transform.toScene(down.position)
                                    val t = projectionRatio(a, b, scenePoint)
                                    val atPoint = Vertex(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                                    onWallLongPress?.invoke(room.roomId, wallIndex, atPoint)
                                }
                            }
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        break
                                    }
                                    if (!change.pressed) {
                                        if (!longPressFired) {
                                            if (totalDrag.getDistance() < tapSlopPx) {
                                                val n = room.vertices.size
                                                val a = room.vertices[wallIndex]
                                                val b = room.vertices[(wallIndex + 1) % n]
                                                val wallLenMm = distance(a, b) * 1000
                                                val scenePoint = transform.toScene(down.position)
                                                val t = projectionRatio(a, b, scenePoint)
                                                onWallTap?.invoke(room.roomId, wallIndex, (t * wallLenMm).roundToInt())
                                            } else {
                                                panOffset += totalDrag
                                            }
                                        }
                                        break
                                    }
                                    val delta = change.positionChange()
                                    totalDrag += delta
                                    if (totalDrag.getDistance() > tapSlopPx && !longPressFired) {
                                        longPressJob.cancel()
                                        change.consume()
                                        panOffset += delta
                                    }
                                }
                            } finally {
                                longPressJob.cancel()
                            }
                        }
                        targetRoom != null -> {
                            down.consume()
                            val room = targetRoom!!
                            var totalDrag = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    if (totalDrag.getDistance() < tapSlopPx) {
                                        onRoomTap?.invoke(room.roomId)
                                    } else {
                                        onRoomDragEnd?.invoke(room.roomId)
                                    }
                                    break
                                }
                                val delta = change.positionChange()
                                totalDrag += delta
                                change.consume()
                                onRoomDrag?.invoke(room.roomId, delta.x / transform.scaleXY, -delta.y / transform.scaleXY)
                            }
                        }
                        else -> {
                            // Порожнє місце — панорамування одним пальцем, масштабування двома.
                            var lastCentroid = down.position
                            var lastPinchDist: Float? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    val p0 = pressed[0]
                                    val p1 = pressed[1]
                                    val currDist = (p0.position - p1.position).getDistance()
                                    val prevDist = lastPinchDist ?: currDist
                                    if (prevDist > 0f) scale = (scale * (currDist / prevDist)).coerceIn(0.5f, 5f)
                                    lastPinchDist = currDist
                                    val centroid = (p0.position + p1.position) / 2f
                                    panOffset += centroid - lastCentroid
                                    lastCentroid = centroid
                                    pressed.forEach { it.consume() }
                                } else {
                                    lastPinchDist = null
                                    val change = pressed.first()
                                    panOffset += change.position - lastCentroid
                                    lastCentroid = change.position
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (rooms.isEmpty()) return@Canvas
        val transform = computeTransform(rooms, size, scale, panOffset)

        rooms.forEach { room ->
            if (room.vertices.size < 3) return@forEach
            val strokeColor = when (mode) {
                PlanInteractionMode.SingleRoomEdit ->
                    if (room.hasSelfIntersection) CubeMasterColors.error else CubeMasterColors.success
                PlanInteractionMode.ObjectOverview -> CubeMasterColors.gold
            }
            val fillColor = CubeMasterColors.gold
            val points = room.vertices.map { transform.toCanvas(it) }

            val path = Path()
            path.moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { path.lineTo(it.x, it.y) }
            path.close()

            drawPath(path, fillColor.copy(alpha = 0.14f))
            val wallStrokePx = 3.dp.toPx()
            drawPath(path, strokeColor, style = Stroke(width = wallStrokePx))

            room.openings.forEach { opening ->
                val n = room.vertices.size
                val wallIdx = opening.wallEdgeIndex
                if (wallIdx !in 0 until n) return@forEach
                val a = room.vertices[wallIdx]
                val b = room.vertices[(wallIdx + 1) % n]
                val wallLenMm = distance(a, b) * 1000
                if (wallLenMm <= 0.0) return@forEach
                val tStart = (opening.offsetMm / wallLenMm).coerceIn(0.0, 1.0)
                val tEnd = ((opening.offsetMm + opening.widthMm) / wallLenMm).coerceIn(0.0, 1.0)
                val gapStart = transform.toCanvas(Vertex(a.x + (b.x - a.x) * tStart, a.y + (b.y - a.y) * tStart))
                val gapEnd = transform.toCanvas(Vertex(a.x + (b.x - a.x) * tEnd, a.y + (b.y - a.y) * tEnd))
                val color = openingColor(opening.kind)

                drawLine(bgColor, gapStart, gapEnd, strokeWidth = wallStrokePx + 3f)

                when (opening.kind) {
                    OpeningKind.Window -> {
                        val dx = gapEnd.x - gapStart.x
                        val dy = gapEnd.y - gapStart.y
                        val len = hypot(dx, dy).coerceAtLeast(1f)
                        val perp = Offset(-dy / len, dx / len) * 5f
                        drawLine(color, gapStart + perp, gapEnd + perp, strokeWidth = 2.dp.toPx())
                        drawLine(color, gapStart - perp, gapEnd - perp, strokeWidth = 2.dp.toPx())
                    }
                    OpeningKind.Door -> {
                        val radius = hypot((gapEnd.x - gapStart.x), (gapEnd.y - gapStart.y))
                        val startAngleDeg = Math.toDegrees(
                            atan2((gapEnd.y - gapStart.y).toDouble(), (gapEnd.x - gapStart.x).toDouble())
                        ).toFloat()
                        drawLine(color, gapStart, gapEnd, strokeWidth = 2.dp.toPx())
                        drawArc(
                            color = color.copy(alpha = 0.6f),
                            startAngle = startAngleDeg,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(gapStart.x - radius, gapStart.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    OpeningKind.Passage -> Unit
                    OpeningKind.Vent -> {
                        val dx = gapEnd.x - gapStart.x
                        val dy = gapEnd.y - gapStart.y
                        val len = hypot(dx, dy).coerceAtLeast(1f)
                        val perp = Offset(-dy / len, dx / len) * 5f
                        val steps = 3
                        for (i in 1 until steps) {
                            val f = i / steps.toFloat()
                            val mid = Offset(gapStart.x + dx * f, gapStart.y + dy * f)
                            drawLine(color, mid - perp, mid + perp, strokeWidth = 1.5.dp.toPx())
                        }
                        drawLine(color, gapStart, gapEnd, strokeWidth = 2.dp.toPx())
                    }
                    OpeningKind.Niche -> {
                        drawLine(
                            color, gapStart, gapEnd,
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                        )
                    }
                }
            }

            if (mode == PlanInteractionMode.SingleRoomEdit) {
                val n = room.vertices.size
                val centroid = Vertex(room.vertices.sumOf { it.x } / n, room.vertices.sumOf { it.y } / n)
                val centroidCanvas = transform.toCanvas(centroid)
                val nativeCanvas = drawContext.canvas.nativeCanvas
                val paint = android.graphics.Paint().apply {
                    color = labelColor
                    textSize = labelTextSizePx
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                for (i in 0 until n) {
                    val a = room.vertices[i]
                    val b = room.vertices[(i + 1) % n]
                    val lengthMm = Math.round(distance(a, b) * 1000.0)
                    val mid = transform.toCanvas(Vertex((a.x + b.x) / 2, (a.y + b.y) / 2))
                    val outX = mid.x - centroidCanvas.x
                    val outY = mid.y - centroidCanvas.y
                    val outLen = hypot(outX, outY).coerceAtLeast(1f)
                    val labelX = mid.x + (outX / outLen) * 16f
                    val labelY = mid.y + (outY / outLen) * 16f
                    nativeCanvas.drawText("$lengthMm мм", labelX, labelY, paint)
                }
                room.vertices.forEach { v -> drawCircle(strokeColor, 4f, transform.toCanvas(v)) }
            } else {
                val n = room.vertices.size
                val centroid = Vertex(room.vertices.sumOf { it.x } / n, room.vertices.sumOf { it.y } / n)
                val nativeCanvas = drawContext.canvas.nativeCanvas
                val paint = android.graphics.Paint().apply {
                    color = CubeMasterColors.graphite.toArgb()
                    textSize = roomLabelTextSizePx
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                val c = transform.toCanvas(centroid)
                nativeCanvas.drawText(room.label, c.x, c.y, paint)
            }
        }
    }
}
