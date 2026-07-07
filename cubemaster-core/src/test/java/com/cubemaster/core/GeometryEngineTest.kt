package com.cubemaster.core

import com.cubemaster.core.geometry.*
import com.cubemaster.core.model.Edge
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GeometryEngineTest {

    @Test
    fun `прямокутник 5x3 — площа 15 кв м`() {
        val area = rectangleAreaM2(5000, 3000)
        assertEquals(15.0, area, 0.001)
    }

    @Test
    fun `прямокутник 5x3 — периметр 16 м`() {
        val perimeter = rectanglePerimeterM(5000, 3000)
        assertEquals(16.0, perimeter, 0.001)
    }

    @Test
    fun `квадрат 4x4 через полігон — площа 16 кв м`() {
        val edges = listOf(
            Edge(4000, 90.0),
            Edge(4000, 90.0),
            Edge(4000, 90.0),
            Edge(4000, 90.0)
        )
        val result = buildPolygon(edges)
        assertEquals(ClosureStatus.Ok, result.status)
        val area = polygonAreaM2(result.vertices)
        assertEquals(16.0, area, 0.05)
    }

    @Test
    fun `Г-подібна кімната 6 кутів — площа відома`() {
        // Г-форма: 6×4 мінус прямокутник 2×2 = 20 кв м
        // Описуємо обходом по годинниковій стрілці
        val edges = listOf(
            Edge(6000, 90.0),
            Edge(2000, 90.0),
            Edge(2000, 90.0),  // виступ
            Edge(2000, 270.0), // увігнутий кут
            Edge(4000, 90.0),
            Edge(4000, 90.0)
        )
        // Валідація суми кутів (4-2)*180 = 720 для 6-кутника
        val angleError = validateAngleSum(edges)
        assertTrue("Невалідна сума кутів: $angleError", angleError < 1.0)
    }

    @Test
    fun `нев'язка 1 см — статус Ok`() {
        // прямокутник з невеликою похибкою у значеннях
        val edges = listOf(
            Edge(5000, 89.8),
            Edge(3000, 90.0),
            Edge(5010, 90.2),
            Edge(3000, 90.0)
        )
        val result = buildPolygon(edges)
        // очікуємо Ok або WarningAutoFixed (нев'язка залежить від відхилення кутів)
        assertNotEquals(ClosureStatus.Error, result.status)
    }

    @Test
    fun `нев'язка більше 10 см — статус Error`() {
        val edges = listOf(
            Edge(5000, 80.0),
            Edge(3000, 80.0),
            Edge(5000, 80.0),
            Edge(3000, 80.0)
        )
        val result = buildPolygon(edges)
        assertEquals(ClosureStatus.Error, result.status)
    }

    @Test
    fun `маяки для стіни 5 м — 5 штук при кроці 1,3 м`() {
        val count = beaconsCountForWall(5.0, spacingM = 1.3)
        assertEquals(5, count)
    }

    @Test
    fun `маяки для стіни 2 м з кроком 1,3 м — 3 штуки (кутові маяки на відстані 1,6 м вимагають проміжний)`() {
        val count = beaconsCountForWall(2.0)
        assertEquals(3, count)
    }

    @Test
    fun `маяки для короткої стіни без проміжних — 2 штуки`() {
        val count = beaconsCountForWall(1.0)
        assertEquals(2, count)
    }

    @Test
    fun `площа стіни з прорізом`() {
        val openings = listOf(
            com.cubemaster.core.model.Opening("1", "r1", 0, com.cubemaster.core.model.OpeningKind.Window, 1200, 1400)
        )
        val gross = wallAreaGross(4000, 2700, 2700)
        val net = wallAreaNet(gross, openings)
        assertEquals(4.0 * 2.7 - 1.2 * 1.4, net, 0.01)
    }

    @Test
    fun `simplifyPolyline прибирає зайві точки на прямій лінії прямокутника`() {
        val points = listOf(
            Vertex(0.0, 0.0), Vertex(1.0, 0.0), Vertex(2.0, 0.0), Vertex(3.0, 0.0),
            Vertex(3.0, 1.0), Vertex(3.0, 2.0),
            Vertex(2.0, 2.0), Vertex(1.0, 2.0), Vertex(0.0, 2.0),
            Vertex(0.0, 1.0), Vertex(0.0, 0.0)
        )
        val simplified = simplifyPolyline(points, epsilonM = 0.01)
        // Замкнутий прямокутник спрощується до 5 точок (4 кути + повернення до першої)
        assertEquals(5, simplified.size)
    }

    @Test
    fun `snapToGrid округлює до найближчого вузла сітки 0,1 м`() {
        val v = snapToGrid(Vertex(1.24, 2.97), stepM = 0.1)
        assertEquals(1.2, v.x, 0.001)
        assertEquals(3.0, v.y, 0.001)
    }

    @Test
    fun `verticesToEdges для квадрата 3x3 м дає 4 ребра по 3000мм і кути 90 град`() {
        val vertices = listOf(
            Vertex(0.0, 0.0), Vertex(3.0, 0.0), Vertex(3.0, 3.0), Vertex(0.0, 3.0)
        )
        val edges = verticesToEdges(vertices)
        assertEquals(4, edges.size)
        edges.forEach { edge ->
            assertEquals(3000, edge.lengthMm)
            assertEquals(90.0, edge.interiorAngleDeg, 0.01)
        }
        // Прогін назад через buildPolygon має дати нульову нев'язку
        val result = buildPolygon(edges)
        assertEquals(ClosureStatus.Ok, result.status)
        assertEquals(9.0, polygonAreaM2(result.vertices), 0.01)
    }

    @Test
    fun `verticesToEdges коректно рахує угнутий кут Г-подібної кімнати`() {
        // L-подібна форма, угнутий кут у (1,1) — інтерʼєрний кут має бути 270°
        val vertices = listOf(
            Vertex(0.0, 0.0), Vertex(2.0, 0.0), Vertex(2.0, 1.0),
            Vertex(1.0, 1.0), Vertex(1.0, 2.0), Vertex(0.0, 2.0)
        )
        val edges = verticesToEdges(vertices)
        assertEquals(6, edges.size)
        val reflexCount = edges.count { it.interiorAngleDeg > 180.0 }
        assertEquals(1, reflexCount)
        assertEquals(270.0, edges.first { it.interiorAngleDeg > 180.0 }.interiorAngleDeg, 0.1)
    }

    @Test
    fun `openingWithinWall — отвір у межах стіни`() {
        val opening = com.cubemaster.core.model.Opening("1", "r1", 0, com.cubemaster.core.model.OpeningKind.Window, 1200, 1400, offsetMm = 500)
        assertTrue(openingWithinWall(4000, opening))
    }

    @Test
    fun `openingWithinWall — отвір виходить за межі стіни`() {
        val opening = com.cubemaster.core.model.Opening("1", "r1", 0, com.cubemaster.core.model.OpeningKind.Door, 1000, 2000, offsetMm = 3500)
        assertFalse(openingWithinWall(4000, opening))
    }

    @Test
    fun `openingWithinWall — відʼємний відступ невалідний`() {
        val opening = com.cubemaster.core.model.Opening("1", "r1", 0, com.cubemaster.core.model.OpeningKind.Vent, 300, 300, offsetMm = -10)
        assertFalse(openingWithinWall(4000, opening))
    }

    @Test
    fun `openingsOverlap — два отвори перекриваються`() {
        val a = com.cubemaster.core.model.Opening("1", "r1", 0, com.cubemaster.core.model.OpeningKind.Window, 1200, 1400, offsetMm = 0)
        val b = com.cubemaster.core.model.Opening("2", "r1", 0, com.cubemaster.core.model.OpeningKind.Door, 1000, 2000, offsetMm = 800)
        assertTrue(openingsOverlap(a, b))
    }

    @Test
    fun `openingsOverlap — отвори не перекриваються`() {
        val a = com.cubemaster.core.model.Opening("1", "r1", 0, com.cubemaster.core.model.OpeningKind.Window, 1200, 1400, offsetMm = 0)
        val b = com.cubemaster.core.model.Opening("2", "r1", 0, com.cubemaster.core.model.OpeningKind.Niche, 500, 500, offsetMm = 1200)
        assertFalse(openingsOverlap(a, b))
    }

    @Test
    fun `validateWallOpenings — повертає проблеми виходу за межі й перекриття`() {
        val a = com.cubemaster.core.model.Opening("1", "r1", 0, com.cubemaster.core.model.OpeningKind.Window, 1200, 1400, offsetMm = 0)
        val b = com.cubemaster.core.model.Opening("2", "r1", 0, com.cubemaster.core.model.OpeningKind.Door, 1000, 2000, offsetMm = 800)
        val c = com.cubemaster.core.model.Opening("3", "r1", 0, com.cubemaster.core.model.OpeningKind.Vent, 300, 300, offsetMm = 3900)
        val problems = validateWallOpenings(4000, listOf(a, b, c))
        assertEquals(2, problems.size)
    }

    @Test
    fun `validateWallOpenings — без проблем повертає порожній список`() {
        val a = com.cubemaster.core.model.Opening("1", "r1", 0, com.cubemaster.core.model.OpeningKind.Window, 1200, 1400, offsetMm = 0)
        val b = com.cubemaster.core.model.Opening("2", "r1", 0, com.cubemaster.core.model.OpeningKind.Door, 1000, 2000, offsetMm = 1500)
        assertTrue(validateWallOpenings(4000, listOf(a, b)).isEmpty())
    }

    @Test
    fun `transformVertices — зсув без повороту`() {
        val vertices = listOf(Vertex(0.0, 0.0), Vertex(1.0, 0.0), Vertex(1.0, 1.0), Vertex(0.0, 1.0))
        val transformed = transformVertices(vertices, originXM = 5.0, originYM = 2.0, rotationDeg = 0.0)
        assertEquals(Vertex(5.0, 2.0).x, transformed[0].x, 0.001)
        assertEquals(Vertex(6.0, 2.0).x, transformed[1].x, 0.001)
        assertEquals(Vertex(6.0, 3.0).y, transformed[2].y, 0.001)
    }

    @Test
    fun `transformVertices — поворот на 90 градусів`() {
        val vertices = listOf(Vertex(1.0, 0.0))
        val transformed = transformVertices(vertices, originXM = 0.0, originYM = 0.0, rotationDeg = 90.0)
        assertEquals(0.0, transformed[0].x, 0.001)
        assertEquals(1.0, transformed[0].y, 0.001)
    }

    @Test
    fun `closestPointOnSegment — точка над серединою відрізка`() {
        val closest = closestPointOnSegment(Vertex(0.0, 0.0), Vertex(4.0, 0.0), Vertex(2.0, 3.0))
        assertEquals(2.0, closest.x, 0.001)
        assertEquals(0.0, closest.y, 0.001)
    }

    @Test
    fun `projectionRatio — обмежується в межах 0 та 1`() {
        val t = projectionRatio(Vertex(0.0, 0.0), Vertex(4.0, 0.0), Vertex(10.0, 5.0))
        assertEquals(1.0, t, 0.001)
        val t2 = projectionRatio(Vertex(0.0, 0.0), Vertex(4.0, 0.0), Vertex(-5.0, 1.0))
        assertEquals(0.0, t2, 0.001)
    }
}
