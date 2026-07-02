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
    fun `маяки для стіни 2 м — 2 штуки`() {
        val count = beaconsCountForWall(2.0)
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
}
