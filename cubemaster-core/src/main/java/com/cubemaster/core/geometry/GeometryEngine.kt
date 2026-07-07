package com.cubemaster.core.geometry

import com.cubemaster.core.model.Edge
import com.cubemaster.core.model.Opening
import com.cubemaster.core.model.RoomGeometry
import kotlin.math.*

data class Vertex(val x: Double, val y: Double)

data class PolygonResult(
    val vertices: List<Vertex>,
    val closureErrorM: Double,
    val status: ClosureStatus,
    val selfIntersects: Boolean = false
)

enum class ClosureStatus {
    Ok,
    WarningAutoFixed,
    Error
}

fun buildPolygon(edges: List<Edge>): PolygonResult {
    val n = edges.size
    val vertices = mutableListOf(Vertex(0.0, 0.0))
    var heading = 0.0
    var x = 0.0
    var y = 0.0

    for (i in 0 until n) {
        val lengthM = edges[i].lengthMm / 1000.0
        x += lengthM * cos(heading)
        y += lengthM * sin(heading)
        vertices.add(Vertex(x, y))
        if (i + 1 < n) {
            val exterior = PI - Math.toRadians(edges[(i + 1) % n].interiorAngleDeg)
            heading -= exterior
        }
    }

    val errorX = vertices.last().x - vertices.first().x
    val errorY = vertices.last().y - vertices.first().y
    val closureErrorM = hypot(errorX, errorY)

    val result = when {
        closureErrorM <= 0.02 -> PolygonResult(
            vertices.dropLast(1),
            closureErrorM,
            ClosureStatus.Ok
        )
        closureErrorM <= 0.10 -> {
            val corrected = distributeClosureError(
                vertices.dropLast(1),
                Vertex(errorX, errorY)
            )
            PolygonResult(corrected, closureErrorM, ClosureStatus.WarningAutoFixed)
        }
        else -> PolygonResult(vertices.dropLast(1), closureErrorM, ClosureStatus.Error)
    }
    return result.copy(selfIntersects = hasSelfIntersection(result.vertices))
}

// Перевіряє, чи перетинаються дві непарні (несуміжні) відрізки-стіни — стандартний
// орієнтаційний тест computational geometry (Cormen et al.).
private fun segmentsIntersect(p1: Vertex, p2: Vertex, p3: Vertex, p4: Vertex): Boolean {
    fun orientation(a: Vertex, b: Vertex, c: Vertex): Int {
        val v = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        return when {
            v > 1e-9 -> 1
            v < -1e-9 -> -1
            else -> 0
        }
    }
    fun onSegment(a: Vertex, b: Vertex, c: Vertex): Boolean {
        return min(a.x, b.x) - 1e-9 <= c.x && c.x <= max(a.x, b.x) + 1e-9 &&
            min(a.y, b.y) - 1e-9 <= c.y && c.y <= max(a.y, b.y) + 1e-9
    }
    val o1 = orientation(p1, p2, p3)
    val o2 = orientation(p1, p2, p4)
    val o3 = orientation(p3, p4, p1)
    val o4 = orientation(p3, p4, p2)
    if (o1 != o2 && o3 != o4) return true
    if (o1 == 0 && onSegment(p1, p2, p3)) return true
    if (o2 == 0 && onSegment(p1, p2, p4)) return true
    if (o3 == 0 && onSegment(p3, p4, p1)) return true
    if (o4 == 0 && onSegment(p3, p4, p2)) return true
    return false
}

// Стіни (несуміжні ребра контуру) не можуть перетинатись — реальна кімната не буває "бантиком".
fun hasSelfIntersection(vertices: List<Vertex>): Boolean {
    val n = vertices.size
    if (n < 4) return false
    for (i in 0 until n) {
        val a1 = vertices[i]
        val a2 = vertices[(i + 1) % n]
        for (j in i + 1 until n) {
            val adjacent = j == i + 1 || (i == 0 && j == n - 1)
            if (adjacent) continue
            val b1 = vertices[j]
            val b2 = vertices[(j + 1) % n]
            if (segmentsIntersect(a1, a2, b1, b2)) return true
        }
    }
    return false
}

fun distributeClosureError(vertices: List<Vertex>, errorVector: Vertex): List<Vertex> {
    val perimeter = vertices.indices.sumOf { i ->
        distance(vertices[i], vertices[(i + 1) % vertices.size])
    }
    var cumulative = 0.0
    return vertices.mapIndexed { i, v ->
        val fraction = cumulative / perimeter
        if (i + 1 < vertices.size) cumulative += distance(vertices[i], vertices[i + 1])
        Vertex(v.x - errorVector.x * fraction, v.y - errorVector.y * fraction)
    }
}

fun polygonAreaM2(vertices: List<Vertex>): Double {
    val n = vertices.size
    var area2 = 0.0
    for (i in 0 until n) {
        val j = (i + 1) % n
        area2 += vertices[i].x * vertices[j].y - vertices[j].x * vertices[i].y
    }
    return abs(area2) / 2.0
}

fun rectangleAreaM2(widthMm: Int, lengthMm: Int): Double =
    (widthMm / 1000.0) * (lengthMm / 1000.0)

// Вершини прямокутника за годинниковою стрілкою, той самий Vertex, що й у полігон-рушії —
// щоб прямокутний і довільний режими малювались одним компонентом плану кімнати.
fun rectangleVertices(widthMm: Int, lengthMm: Int): List<Vertex> {
    val w = widthMm / 1000.0
    val l = lengthMm / 1000.0
    return listOf(
        Vertex(0.0, 0.0),
        Vertex(w, 0.0),
        Vertex(w, l),
        Vertex(0.0, l)
    )
}

fun perimeterM(vertices: List<Vertex>): Double =
    vertices.indices.sumOf { i -> distance(vertices[i], vertices[(i + 1) % vertices.size]) }

fun rectanglePerimeterM(widthMm: Int, lengthMm: Int): Double =
    2 * (widthMm + lengthMm) / 1000.0

fun distance(a: Vertex, b: Vertex) = hypot(b.x - a.x, b.y - a.y)

// Вершини кімнати незалежно від того, прямокутник вона чи довільний контур —
// для будь-якого місця, де потрібно намалювати форму кімнати (прев'ю, мініатюра в списку).
fun roomGeometryVertices(geometry: RoomGeometry): List<Vertex> = when (geometry) {
    is RoomGeometry.Rectangle -> rectangleVertices(geometry.widthMm, geometry.lengthMm)
    is RoomGeometry.Polygon -> buildPolygon(geometry.edges).vertices
}

fun wallAreaGross(edgeLengthMm: Int, heightAtStartMm: Int, heightAtEndMm: Int): Double {
    val l = edgeLengthMm / 1000.0
    val h1 = heightAtStartMm / 1000.0
    val h2 = heightAtEndMm / 1000.0
    return l * (h1 + h2) / 2.0
}

fun wallAreaNet(gross: Double, openings: List<Opening>): Double {
    val openingArea = openings.sumOf { o ->
        (o.widthMm / 1000.0) * (o.heightMm / 1000.0)
    }
    return (gross - openingArea).coerceAtLeast(0.0)
}

fun beaconsCountForWall(
    wallLengthM: Double,
    spacingM: Double = 1.3,
    cornerOffsetM: Double = 0.2
): Int {
    val usable = wallLengthM - 2 * cornerOffsetM
    if (usable <= 0) return 2
    val intermediate = floor(usable / spacingM).toInt()
    return intermediate + 2
}

fun validateAngleSum(edges: List<Edge>): Double {
    val n = edges.size
    val expected = (n - 2) * 180.0
    val actual = edges.sumOf { it.interiorAngleDeg }
    return abs(actual - expected)
}

// Знак повороту — для визначення внутрішнього/зовнішнього кута
fun isConvexCorner(vertices: List<Vertex>, index: Int): Boolean {
    val n = vertices.size
    val prev = vertices[(index - 1 + n) % n]
    val curr = vertices[index]
    val next = vertices[(index + 1) % n]
    val cross = (curr.x - prev.x) * (next.y - curr.y) - (curr.y - prev.y) * (next.x - curr.x)
    return cross > 0
}

fun countConcaveCorners(vertices: List<Vertex>): Int =
    vertices.indices.count { !isConvexCorner(vertices, it) }

// Спрощення "сирого" шляху пальця (Douglas–Peucker) до домінантних кутів.
fun simplifyPolyline(points: List<Vertex>, epsilonM: Double): List<Vertex> {
    if (points.size < 3) return points
    var maxDist = 0.0
    var index = 0
    val first = points.first()
    val last = points.last()
    for (i in 1 until points.size - 1) {
        val d = perpendicularDistance(points[i], first, last)
        if (d > maxDist) {
            maxDist = d
            index = i
        }
    }
    return if (maxDist > epsilonM) {
        val left = simplifyPolyline(points.subList(0, index + 1), epsilonM)
        val right = simplifyPolyline(points.subList(index, points.size), epsilonM)
        left.dropLast(1) + right
    } else {
        listOf(first, last)
    }
}

private fun perpendicularDistance(point: Vertex, lineStart: Vertex, lineEnd: Vertex): Double {
    val dx = lineEnd.x - lineStart.x
    val dy = lineEnd.y - lineStart.y
    if (dx == 0.0 && dy == 0.0) return distance(point, lineStart)
    val t = ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / (dx * dx + dy * dy)
    val projX = lineStart.x + t * dx
    val projY = lineStart.y + t * dy
    return distance(point, Vertex(projX, projY))
}

// Прилипання намальованої вершини до вузла сітки з кроком stepM (метри).
fun snapToGrid(v: Vertex, stepM: Double): Vertex =
    Vertex(Math.round(v.x / stepM) * stepM, Math.round(v.y / stepM) * stepM)

// Обернена до buildPolygon: реальні вершини (в будь-якому напрямку обходу) -> ребра з довжиною і внутрішнім кутом.
// Кути коректно рахуються і для угнутих вершин (>180°) незалежно від напрямку обходу вхідного списку.
fun verticesToEdges(vertices: List<Vertex>): List<Edge> {
    val n = vertices.size
    require(n >= 3) { "Полігон повинен мати щонайменше 3 вершини" }

    var area2 = 0.0
    for (i in 0 until n) {
        val j = (i + 1) % n
        area2 += vertices[i].x * vertices[j].y - vertices[j].x * vertices[i].y
    }
    val ordered = if (area2 < 0) vertices.reversed() else vertices

    return ordered.indices.map { i ->
        val prev = ordered[(i - 1 + n) % n]
        val curr = ordered[i]
        val next = ordered[(i + 1) % n]

        val ax = curr.x - prev.x
        val ay = curr.y - prev.y
        val bx = next.x - curr.x
        val by = next.y - curr.y

        val crossAB = ax * by - ay * bx
        val dotAB = ax * bx + ay * by
        val exteriorTurnDeg = Math.toDegrees(atan2(crossAB, dotAB))
        val interiorAngleDeg = 180.0 - exteriorTurnDeg

        val lengthMm = Math.round(distance(curr, next) * 1000.0).toInt()
        Edge(lengthMm, interiorAngleDeg)
    }
}
