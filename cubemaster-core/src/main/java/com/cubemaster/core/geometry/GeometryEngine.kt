package com.cubemaster.core.geometry

import com.cubemaster.core.model.Edge
import com.cubemaster.core.model.Opening
import com.cubemaster.core.model.RoomGeometry
import kotlin.math.*

data class Vertex(val x: Double, val y: Double)

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

fun polygonAreaM2(vertices: List<Vertex>): Double {
    val n = vertices.size
    var area2 = 0.0
    for (i in 0 until n) {
        val j = (i + 1) % n
        area2 += vertices[i].x * vertices[j].y - vertices[j].x * vertices[i].y
    }
    return abs(area2) / 2.0
}

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

fun distance(a: Vertex, b: Vertex) = hypot(b.x - a.x, b.y - a.y)

// Вершини кімнати — для будь-якого місця, де потрібно намалювати форму
// кімнати (прев'ю, мініатюра в списку).
fun roomGeometryVertices(geometry: RoomGeometry): List<Vertex> =
    (geometry as RoomGeometry.Polygon).vertices

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

// Чи вміщується отвір у межах стіни заданої довжини (за offsetMm/widthMm).
fun openingWithinWall(wallLengthMm: Int, opening: Opening): Boolean =
    opening.offsetMm >= 0 && opening.offsetMm + opening.widthMm <= wallLengthMm

// Чи перекриваються два отвори вздовж стіни (інтервали [offset, offset+width)).
fun openingsOverlap(a: Opening, b: Opening): Boolean {
    val aEnd = a.offsetMm + a.widthMm
    val bEnd = b.offsetMm + b.widthMm
    return a.offsetMm < bEnd && b.offsetMm < aEnd
}

// Перевіряє всі отвори однієї стіни й повертає українські повідомлення про проблеми
// (вихід за межі стіни, перекриття двох отворів) — для блокування збереження геометрії.
fun validateWallOpenings(wallLengthMm: Int, openings: List<Opening>): List<String> {
    val problems = mutableListOf<String>()
    openings.forEach { o ->
        if (!openingWithinWall(wallLengthMm, o)) {
            problems.add("Отвір (${o.widthMm} мм) з відступом ${o.offsetMm} мм виходить за межі стіни (${wallLengthMm} мм)")
        }
    }
    for (i in openings.indices) {
        for (j in i + 1 until openings.size) {
            if (openingsOverlap(openings[i], openings[j])) {
                problems.add("Отвори на стіні перекриваються між собою")
            }
        }
    }
    return problems
}

// Переносить вершини кімнати (локальні координати) у спільну систему координат
// плану об'єкта — зсув на (originXM, originYM) з поворотом на rotationDeg.
fun transformVertices(vertices: List<Vertex>, originXM: Double, originYM: Double, rotationDeg: Double): List<Vertex> {
    val rad = Math.toRadians(rotationDeg)
    val cosR = cos(rad)
    val sinR = sin(rad)
    return vertices.map { v ->
        Vertex(
            originXM + v.x * cosR - v.y * sinR,
            originYM + v.x * sinR + v.y * cosR
        )
    }
}

// Найближча до точки p точка на відрізку [a, b].
fun closestPointOnSegment(a: Vertex, b: Vertex, p: Vertex): Vertex {
    val t = projectionRatio(a, b, p)
    return Vertex(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

// Проекція точки p на пряму через a-b, як частка довжини відрізка, обмежена [0, 1].
// Використовується для хіт-тесту тапу/перетягування по стіні (перетворення точки канваси на offset вздовж стіни).
fun projectionRatio(a: Vertex, b: Vertex, p: Vertex): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lenSq = dx * dx + dy * dy
    if (lenSq <= 1e-12) return 0.0
    val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq
    return t.coerceIn(0.0, 1.0)
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

// Display-функція: реальні вершини (в будь-якому напрямку обходу) -> ребра з довжиною і внутрішнім кутом.
// Кути коректно рахуються і для угнутих вершин (>180°) незалежно від напрямку обходу вхідного списку.
// Вершини НЕ перевпорядковуються — edges[i] завжди відповідає стіні vertices[i] -> vertices[(i+1)%n],
// тому індекс рядка таблиці лишається синхронізованим з raw-індексом вершини (див. GeometryViewModel,
// OpeningsTab, FreehandDrawCanvas — усі використовують той самий, не перевпорядкований, порядок).
fun verticesToEdges(vertices: List<Vertex>): List<Edge> {
    val n = vertices.size
    require(n >= 3) { "Полігон повинен мати щонайменше 3 вершини" }

    return vertices.indices.map { i ->
        val next = vertices[(i + 1) % n]
        val lengthMm = Math.round(distance(vertices[i], next) * 1000.0).toInt()
        val interiorAngleDeg = interiorAngleAtDeg(vertices, i)
        Edge(lengthMm, interiorAngleDeg)
    }
}

// Пряме переміщення вершини — решта кімнати нерухома. Основний спосіб
// "змінити кут": перетягнути вершину на канвасі.
fun moveVertex(vertices: List<Vertex>, index: Int, newPosition: Vertex): List<Vertex> {
    require(index in vertices.indices) { "Індекс вершини поза межами" }
    return vertices.toMutableList().also { it[index] = newPosition }
}

// Вставляє нову вершину одразу після vertices[edgeIndex] (тобто на стіні
// edgeIndex, що з'єднує vertices[edgeIndex] і vertices[(edgeIndex+1)%n]).
// Прорізи на цій та наступних стінах треба переіндексувати окремо
// (див. GeometryViewModel.insertVertex) — ця функція суто геометрична.
fun insertVertexOnEdge(vertices: List<Vertex>, edgeIndex: Int, atPoint: Vertex): List<Vertex> {
    val n = vertices.size
    require(n >= 3 && edgeIndex in 0 until n) { "Некоректний індекс стіни" }
    val result = vertices.toMutableList()
    result.add(edgeIndex + 1, atPoint)
    return result
}

// Видаляє вершину. Повертає null, якщо лишиться менше 3 вершин
// (мінімальна кімната — трикутник). Виклик має бути заблокований на
// рівні ViewModel, якщо на суміжних стінах є прорізи.
fun removeVertex(vertices: List<Vertex>, index: Int): List<Vertex>? {
    if (vertices.size <= 3) return null
    require(index in vertices.indices) { "Індекс вершини поза межами" }
    return vertices.toMutableList().also { it.removeAt(index) }
}

// Рухає vertices[edgeIndex + 1] вздовж поточного напрямку стіни edgeIndex
// на нову довжину. vertices[edgeIndex] лишається нерухомим. Довжина
// НАСТУПНОЇ стіни (edgeIndex+1) зміниться як похідний наслідок — обидва
// її кінці визначені (нова позиція vertices[edgeIndex+1] і нерухома
// vertices[edgeIndex+2]).
fun setEdgeLength(vertices: List<Vertex>, edgeIndex: Int, newLengthMm: Int): List<Vertex> {
    val n = vertices.size
    require(n >= 3 && edgeIndex in 0 until n) { "Некоректний індекс стіни" }
    val a = vertices[edgeIndex]
    val bIndex = (edgeIndex + 1) % n
    val b = vertices[bIndex]
    val currentLength = distance(a, b)
    if (currentLength < 1e-9) return vertices
    val ratio = (newLengthMm / 1000.0) / currentLength
    val newB = Vertex(a.x + (b.x - a.x) * ratio, a.y + (b.y - a.y) * ratio)
    return vertices.toMutableList().also { it[bIndex] = newB }
}

// Внутрішній кут у вершині vertexIndex, коректний незалежно від напрямку
// обходу списку — не перевпорядковує вершини, тому індекс не "з'їжджає"
// (те саме стосується і verticesToEdges, яка використовує цю функцію).
fun interiorAngleAtDeg(vertices: List<Vertex>, vertexIndex: Int): Double {
    val n = vertices.size
    val prev = vertices[(vertexIndex - 1 + n) % n]
    val curr = vertices[vertexIndex]
    val next = vertices[(vertexIndex + 1) % n]
    val ax = curr.x - prev.x
    val ay = curr.y - prev.y
    val bx = next.x - curr.x
    val by = next.y - curr.y
    val crossAB = ax * by - ay * bx
    val dotAB = ax * bx + ay * by
    val exteriorTurnDeg = Math.toDegrees(atan2(crossAB, dotAB))
    var area2 = 0.0
    for (i in 0 until n) {
        val j = (i + 1) % n
        area2 += vertices[i].x * vertices[j].y - vertices[j].x * vertices[i].y
    }
    val orientedExterior = if (area2 < 0) -exteriorTurnDeg else exteriorTurnDeg
    return 180.0 - orientedExterior
}

// Обертає vertices[vertexIndex] навколо vertices[vertexIndex-1] (нерухомої),
// зберігаючи довжину стіни (vertexIndex-1 -> vertexIndex), поки внутрішній
// кут у vertexIndex не стане newAngleDeg. vertices[vertexIndex+1] лишається
// нерухомим — довжина стіни (vertexIndex -> vertexIndex+1) зміниться як
// похідний наслідок. Розв'язується бісекцією (кут монотонний в межах
// ±170° від поточного положення для простого багатокутника) — якщо ціль
// поза цим діапазоном, повертає вершини без змін (безпечніше за хибний
// результат).
fun setInteriorAngle(vertices: List<Vertex>, vertexIndex: Int, newAngleDeg: Double): List<Vertex> {
    val n = vertices.size
    require(n >= 3 && vertexIndex in 0 until n) { "Некоректний індекс вершини" }
    val prevIndex = (vertexIndex - 1 + n) % n
    val prev = vertices[prevIndex]
    val curr = vertices[vertexIndex]
    val radius = distance(prev, curr)
    if (radius < 1e-9) return vertices
    val baseAngle = atan2(curr.y - prev.y, curr.x - prev.x)

    fun angleAtTheta(theta: Double): Double {
        val candidate = Vertex(prev.x + radius * cos(baseAngle + theta), prev.y + radius * sin(baseAngle + theta))
        val trial = vertices.toMutableList().also { it[vertexIndex] = candidate }
        return interiorAngleAtDeg(trial, vertexIndex)
    }

    var lo = Math.toRadians(-170.0)
    var hi = Math.toRadians(170.0)
    val loVal = angleAtTheta(lo)
    val hiVal = angleAtTheta(hi)
    val inRange = (newAngleDeg - loVal) * (newAngleDeg - hiVal) <= 0.0
    if (!inRange) return vertices

    repeat(60) {
        val mid = (lo + hi) / 2.0
        val midVal = angleAtTheta(mid)
        if ((midVal < newAngleDeg) == (loVal < newAngleDeg)) lo = mid else hi = mid
    }
    val theta = (lo + hi) / 2.0
    val newCurr = Vertex(prev.x + radius * cos(baseAngle + theta), prev.y + radius * sin(baseAngle + theta))
    return vertices.toMutableList().also { it[vertexIndex] = newCurr }
}
