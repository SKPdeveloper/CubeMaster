package com.cubemaster.core

import com.cubemaster.core.geometry.*
import org.junit.Assert.*
import org.junit.Test

class GeometryEngineTest {

    @Test
    fun `прямокутник 5x3 через rectangleVertices — площа 15 кв м`() {
        val vertices = rectangleVertices(5000, 3000)
        assertEquals(15.0, polygonAreaM2(vertices), 0.001)
    }

    @Test
    fun `прямокутник 5x3 — периметр 16 м`() {
        val vertices = rectangleVertices(5000, 3000)
        assertEquals(16.0, perimeterM(vertices), 0.001)
    }

    @Test
    fun `Г-подібна кімната 6 вершин — площа 16 кв м`() {
        // 6×4 мінус виїмка 2×2 = 16 кв м (нижня частина 6×2=12, верхня права частина 2×2=4, разом 16)
        val vertices = listOf(
            Vertex(0.0, 0.0), Vertex(6.0, 0.0), Vertex(6.0, 4.0),
            Vertex(4.0, 4.0), Vertex(4.0, 2.0), Vertex(0.0, 2.0)
        )
        assertEquals(16.0, polygonAreaM2(vertices), 0.01)
    }

    @Test
    fun `маяки для стіни 5 м — 5 штук при кроці 1,3 м`() {
        assertEquals(5, beaconsCountForWall(5.0, spacingM = 1.3))
    }

    @Test
    fun `маяки для стіни 2 м з кроком 1,3 м — 3 штуки`() {
        assertEquals(3, beaconsCountForWall(2.0))
    }

    @Test
    fun `маяки для короткої стіни без проміжних — 2 штуки`() {
        assertEquals(2, beaconsCountForWall(1.0))
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
        assertEquals(5, simplifyPolyline(points, epsilonM = 0.01).size)
    }

    @Test
    fun `snapToGrid округлює до найближчого вузла сітки 0,1 м`() {
        val v = snapToGrid(Vertex(1.24, 2.97), stepM = 0.1)
        assertEquals(1.2, v.x, 0.001)
        assertEquals(3.0, v.y, 0.001)
    }

    @Test
    fun `verticesToEdges для квадрата 3x3 м дає 4 ребра по 3000мм і кути 90 град`() {
        val vertices = listOf(Vertex(0.0, 0.0), Vertex(3.0, 0.0), Vertex(3.0, 3.0), Vertex(0.0, 3.0))
        val edges = verticesToEdges(vertices)
        assertEquals(4, edges.size)
        edges.forEach { edge ->
            assertEquals(3000, edge.lengthMm)
            assertEquals(90.0, edge.interiorAngleDeg, 0.01)
        }
    }

    @Test
    fun `verticesToEdges коректно рахує угнутий кут Г-подібної кімнати`() {
        val vertices = listOf(
            Vertex(0.0, 0.0), Vertex(2.0, 0.0), Vertex(2.0, 1.0),
            Vertex(1.0, 1.0), Vertex(1.0, 2.0), Vertex(0.0, 2.0)
        )
        val edges = verticesToEdges(vertices)
        assertEquals(6, edges.size)
        assertEquals(1, edges.count { it.interiorAngleDeg > 180.0 })
        assertEquals(270.0, edges.first { it.interiorAngleDeg > 180.0 }.interiorAngleDeg, 0.1)
    }

    @Test
    fun `moveVertex рухає лише задану вершину`() {
        val vertices = rectangleVertices(4000, 3000)
        val moved = moveVertex(vertices, 1, Vertex(5.0, 0.5))
        assertEquals(Vertex(5.0, 0.5), moved[1])
        assertEquals(vertices[0], moved[0])
        assertEquals(vertices[2], moved[2])
        assertEquals(vertices[3], moved[3])
    }

    @Test
    fun `insertVertexOnEdge вставляє вершину одразу після заданого ребра`() {
        val vertices = rectangleVertices(4000, 3000)
        val result = insertVertexOnEdge(vertices, edgeIndex = 0, atPoint = Vertex(2.0, 0.0))
        assertEquals(5, result.size)
        assertEquals(Vertex(2.0, 0.0), result[1])
        assertEquals(vertices[1], result[2])
    }

    @Test
    fun `insertVertexOnEdge на останньому ребрі додає вершину в кінець списку`() {
        val vertices = rectangleVertices(4000, 3000)
        val lastEdge = vertices.size - 1
        val result = insertVertexOnEdge(vertices, edgeIndex = lastEdge, atPoint = Vertex(0.0, 1.5))
        assertEquals(5, result.size)
        assertEquals(vertices[0], result[0])
        assertEquals(vertices[3], result[3])
        assertEquals(Vertex(0.0, 1.5), result[4])
    }

    @Test
    fun `removeVertex видаляє задану вершину`() {
        val vertices = listOf(Vertex(0.0, 0.0), Vertex(4.0, 0.0), Vertex(4.0, 3.0), Vertex(2.0, 3.0), Vertex(0.0, 3.0))
        val result = removeVertex(vertices, 3)
        assertNotNull(result)
        assertEquals(4, result!!.size)
        assertFalse(result.contains(Vertex(2.0, 3.0)))
    }

    @Test
    fun `removeVertex повертає null, якщо лишиться менше 3 вершин`() {
        val triangle = listOf(Vertex(0.0, 0.0), Vertex(1.0, 0.0), Vertex(0.0, 1.0))
        assertNull(removeVertex(triangle, 0))
    }

    @Test
    fun `setEdgeLength рухає лише наступну вершину, попередня нерухома`() {
        val vertices = rectangleVertices(4000, 3000)
        val result = setEdgeLength(vertices, edgeIndex = 0, newLengthMm = 5000)
        assertEquals(vertices[0], result[0])
        assertEquals(5.0, distance(result[0], result[1]), 0.001)
        assertEquals(vertices[2], result[2])
        assertEquals(vertices[3], result[3])
    }

    @Test
    fun `setInteriorAngle досягає цільового кута, зберігаючи попередню вершину й довжину попередньої стіни`() {
        val vertices = rectangleVertices(4000, 3000)
        val originalLength01 = distance(vertices[0], vertices[1])
        val result = setInteriorAngle(vertices, vertexIndex = 1, newAngleDeg = 80.0)
        assertEquals(vertices[0], result[0])
        assertEquals(vertices[2], result[2])
        assertEquals(vertices[3], result[3])
        assertEquals(originalLength01, distance(result[0], result[1]), 0.001)
        assertEquals(80.0, interiorAngleAtDeg(result, 1), 0.1)
    }

    @Test
    fun `interiorAngleAtDeg для прямокутника дає 90 градусів у кожній вершині`() {
        val vertices = rectangleVertices(4000, 3000)
        for (i in vertices.indices) {
            assertEquals(90.0, interiorAngleAtDeg(vertices, i), 0.01)
        }
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
    fun `openingsOverlap — два отвори перекриваються`() {
        val a = com.cubemaster.core.model.Opening("1", "r1", 0, com.cubemaster.core.model.OpeningKind.Window, 1200, 1400, offsetMm = 0)
        val b = com.cubemaster.core.model.Opening("2", "r1", 0, com.cubemaster.core.model.OpeningKind.Door, 1000, 2000, offsetMm = 800)
        assertTrue(openingsOverlap(a, b))
    }

    @Test
    fun `validateWallOpenings — повертає проблеми виходу за межі й перекриття`() {
        val a = com.cubemaster.core.model.Opening("1", "r1", 0, com.cubemaster.core.model.OpeningKind.Window, 1200, 1400, offsetMm = 0)
        val b = com.cubemaster.core.model.Opening("2", "r1", 0, com.cubemaster.core.model.OpeningKind.Door, 1000, 2000, offsetMm = 800)
        val c = com.cubemaster.core.model.Opening("3", "r1", 0, com.cubemaster.core.model.OpeningKind.Vent, 300, 300, offsetMm = 3900)
        assertEquals(2, validateWallOpenings(4000, listOf(a, b, c)).size)
    }

    @Test
    fun `transformVertices — зсув без повороту`() {
        val vertices = listOf(Vertex(0.0, 0.0), Vertex(1.0, 0.0), Vertex(1.0, 1.0), Vertex(0.0, 1.0))
        val transformed = transformVertices(vertices, originXM = 5.0, originYM = 2.0, rotationDeg = 0.0)
        assertEquals(5.0, transformed[0].x, 0.001)
        assertEquals(6.0, transformed[1].x, 0.001)
        assertEquals(3.0, transformed[2].y, 0.001)
    }

    @Test
    fun `closestPointOnSegment — точка над серединою відрізка`() {
        val closest = closestPointOnSegment(Vertex(0.0, 0.0), Vertex(4.0, 0.0), Vertex(2.0, 3.0))
        assertEquals(2.0, closest.x, 0.001)
        assertEquals(0.0, closest.y, 0.001)
    }

    @Test
    fun `projectionRatio — обмежується в межах 0 та 1`() {
        assertEquals(1.0, projectionRatio(Vertex(0.0, 0.0), Vertex(4.0, 0.0), Vertex(10.0, 5.0)), 0.001)
        assertEquals(0.0, projectionRatio(Vertex(0.0, 0.0), Vertex(4.0, 0.0), Vertex(-5.0, 1.0)), 0.001)
    }
}
