# Єдиний редактор форми кімнати — план реалізації

> **Для агентів-виконавців:** ОБОВ'ЯЗКОВИЙ СКІЛ: використовуйте
> superpowers:subagent-driven-development (рекомендовано) або
> superpowers:executing-plans для виконання цього плану по задачах.
> Кроки використовують чекбокси (`- [ ]`) для трекінгу.

**Мета:** Замінити три окремі вкладки редактора геометрії кімнати
(Прямокутник / Довільний контур / Малювати) на один екран з живим
взаємопов'язаним редагуванням форми: перетягування вершин, вставка/
видалення вершин, і числові поля довжина+кут, синхронні з канвасом.

**Архітектура:** `RoomGeometry` зводиться до єдиного типу
`Polygon(vertices: List<Vertex>)` — абсолютні координати вершин замість
довжина+кут. Це прибирає весь клас проблем "нев'язки контуру" як
структурну гарантію. Нові чисті функції в `GeometryEngine` виконують
трансформації над списком вершин (перемістити/вставити/видалити вершину,
задати довжину/кут стіни). `RoomPlanCanvas` (вже існуючий спільний
рушій канвасу) отримує нові жести поверх наявних.

**Технології:** Kotlin, Jetpack Compose, Room (локальна БД), Firestore,
JUnit (cubemaster-core тести).

## Глобальні обмеження

- Без міграції БД для існуючих кімнат — версія Room-схеми просто
  збільшується, `fallbackToDestructiveMigration()` вже підключено
  (`di/AppModule.kt:50`).
- Короткий тап по стіні на канвасі — лишається "додати проріз"
  (не змінюється). Довге натискання по стіні — вставляє вершину.
  Довге натискання на вершині — пропонує видалити.
- Мінімум 3 вершини завжди. Видалення вершини блокується, якщо на
  суміжній стіні (до або після) є хоч один проріз.
- УІ, повідомлення, коментарі в коді — українською (CLAUDE.md проєкту).
- Ніяких заглушок/TODO — кожна задача завершується робочим, протестованим
  кодом.

---

## Task 1: Нова модель геометрії й функції GeometryEngine (cubemaster-core)

**Файли:**
- Modify: `cubemaster-core/src/main/java/com/cubemaster/core/model/Room.kt`
- Modify: `cubemaster-core/src/main/java/com/cubemaster/core/geometry/GeometryEngine.kt`
- Modify: `cubemaster-core/src/test/java/com/cubemaster/core/GeometryEngineTest.kt`

**Interfaces:**
- Produces: `RoomGeometry.Polygon(vertices: List<Vertex>)` (єдиний тип,
  `Rectangle` видалено), `GeometryEngine.moveVertex()`,
  `insertVertexOnEdge()`, `removeVertex()`, `setEdgeLength()`,
  `setInteriorAngle()`, `interiorAngleAtDeg()`. `verticesToEdges()`
  лишається без зміни сигнатури (тепер це чиста display-функція).
  `hasSelfIntersection()`, `polygonAreaM2()`, `perimeterM()`,
  `wallAreaGross/Net()`, `transformVertices()`, `rectangleVertices()`
  лишаються без змін.
- Видалено назавжди: `RoomGeometry.Rectangle`, `buildPolygon()`,
  `ClosureStatus`, `PolygonResult`, `distributeClosureError()`.

- [ ] **Step 1: Оновити модель `RoomGeometry` — прибрати `Rectangle`, `Polygon` тримає вершини**

Файл `cubemaster-core/src/main/java/com/cubemaster/core/model/Room.kt`,
рядки 21-26 замінити повністю на:

```kotlin
sealed class RoomGeometry {
    data class Polygon(val vertices: List<com.cubemaster.core.geometry.Vertex>) : RoomGeometry()
}
```

Рядок `data class Edge(val lengthMm: Int, val interiorAngleDeg: Double)`
(рядок 26) лишити без змін — `Edge` й далі використовується як
display-модель (таблиця чисел), просто не для зберігання.

- [ ] **Step 2: Прибрати `buildPolygon`/`ClosureStatus`/`PolygonResult`/`distributeClosureError` з GeometryEngine**

У файлі `cubemaster-core/src/main/java/com/cubemaster/core/geometry/GeometryEngine.kt`
видалити повністю блок рядків 10-61 (`PolygonResult`, `ClosureStatus`,
`fun buildPolygon`) і блок `distributeClosureError` (рядки 108-118).
`hasSelfIntersection` (рядки 91-106) і `segmentsIntersect` (рядки 63-88)
лишаються без змін — вони й далі потрібні для перевірки самоперетину.

- [ ] **Step 2b: Оновити `roomGeometryVertices` — прибрати виклик `buildPolygon`**

Той самий файл, `roomGeometryVertices` (рядки 154-159) викликає щойно
видалений `buildPolygon` — замінити на:

```kotlin
// Вершини кімнати — для будь-якого місця, де потрібно намалювати форму
// кімнати (прев'ю, мініатюра в списку).
fun roomGeometryVertices(geometry: RoomGeometry): List<Vertex> =
    (geometry as RoomGeometry.Polygon).vertices
```

- [ ] **Step 2c: Прибрати `rectangleAreaM2`/`rectanglePerimeterM` — стають мертвим кодом**

Після Task 2-4 жоден виклик цих двох функцій не лишається (усі місця
переходять на `polygonAreaM2`/`perimeterM` через `rectangleVertices()`).
У тому самому файлі видалити:

```kotlin
fun rectangleAreaM2(widthMm: Int, lengthMm: Int): Double =
    (widthMm / 1000.0) * (lengthMm / 1000.0)
```

(рядки 130-131) і

```kotlin
fun rectanglePerimeterM(widthMm: Int, lengthMm: Int): Double =
    2 * (widthMm + lengthMm) / 1000.0
```

(рядки 149-150). `rectangleVertices()` (рядки 133-144) лишається — вона
й далі потрібна для дефолтної кімнати (Task 2) і тестів.

- [ ] **Step 3: Додати нові vertex-операції в GeometryEngine**

Додати в кінець `GeometryEngine.kt` (після `verticesToEdges`, перед
кінцем файлу):

```kotlin
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
// обходу списку (на відміну від verticesToEdges — не перевпорядковує
// вершини, тому індекс не "з'їжджає").
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
```

- [ ] **Step 4: Переписати GeometryEngineTest.kt**

Замінити повністю вміст файлу
`cubemaster-core/src/test/java/com/cubemaster/core/GeometryEngineTest.kt`:

```kotlin
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
    fun `Г-подібна кімната 6 вершин — площа 20 кв м`() {
        // 6×4 мінус виїмка 2×2 = 20 кв м
        val vertices = listOf(
            Vertex(0.0, 0.0), Vertex(6.0, 0.0), Vertex(6.0, 4.0),
            Vertex(4.0, 4.0), Vertex(4.0, 2.0), Vertex(0.0, 2.0)
        )
        assertEquals(20.0, polygonAreaM2(vertices), 0.01)
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
```

- [ ] **Step 5: Запустити тести**

Run: `./gradlew :cubemaster-core:test --console=plain`
Expected: BUILD SUCCESSFUL, усі тести `GeometryEngineTest` зелені.

- [ ] **Step 6: Commit**

```bash
git add cubemaster-core/src/main/java/com/cubemaster/core/model/Room.kt \
        cubemaster-core/src/main/java/com/cubemaster/core/geometry/GeometryEngine.kt \
        cubemaster-core/src/test/java/com/cubemaster/core/GeometryEngineTest.kt
git commit -m "feat: RoomGeometry зведено до вершин, нові операції над вершинами (Task 1/8)"
```

---

## Task 2: Шар даних — RoomEntity/Converters/Firestore/версія БД

**Файли:**
- Modify: `app/src/main/java/com/example/cubemaster/data/local/entity/Entities.kt:31-48`
- Modify: `app/src/main/java/com/example/cubemaster/data/local/converter/Converters.kt`
- Modify: `app/src/main/java/com/example/cubemaster/data/remote/FirestoreRepository.kt:57-77`
- Modify: `app/src/main/java/com/example/cubemaster/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/cubemaster/presentation/rooms/RoomsViewModel.kt:61-67,99`

**Interfaces:**
- Consumes: `RoomGeometry.Polygon(vertices: List<Vertex>)`, `rectangleVertices()` з Task 1.
- Produces: `RoomEntity.verticesJson: String` (не-nullable, завжди присутній).

- [ ] **Step 1: Оновити RoomEntity — прибрати geometryType/widthMm/lengthMm, edgesJson -> verticesJson**

Файл `app/src/main/java/com/example/cubemaster/data/local/entity/Entities.kt`,
рядки 31-48 замінити на:

```kotlin
data class RoomEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val verticesJson: String,
    val heightMode: String,
    val heightMm: Int?,
    val cornerHeightsMmJson: String?,
    val roomType: String,
    val sortOrder: Int,
    val originXM: Double? = null,
    val originYM: Double? = null,
    val rotationDeg: Double = 0.0,
    val syncState: String = SyncState.PendingUpload.name
)
```

- [ ] **Step 2: Оновити Converters.kt — серіалізація вершин замість rectangle/edges**

У файлі `app/src/main/java/com/example/cubemaster/data/local/converter/Converters.kt`
замінити `mapToRoomDomain` (рядки 47-69):

```kotlin
    fun mapToRoomDomain(e: com.example.cubemaster.data.local.entity.RoomEntity, json: Json): Room {
        val vertices = parseVertices(e.verticesJson, json)
        return Room(
            id = e.id,
            projectId = e.projectId,
            name = e.name,
            geometry = RoomGeometry.Polygon(vertices),
            heightMode = HeightMode.valueOf(e.heightMode),
            heightMm = e.heightMm,
            cornerHeightsMm = e.cornerHeightsMmJson?.let { parseIntList(it, json) },
            roomType = RoomType.valueOf(e.roomType),
            sortOrder = e.sortOrder,
            originXM = e.originXM,
            originYM = e.originYM,
            rotationDeg = e.rotationDeg,
            syncState = SyncState.valueOf(e.syncState)
        )
    }
```

Замінити `mapToRoomEntity` (рядки 71-97):

```kotlin
    fun mapToRoomEntity(d: Room, json: Json): com.example.cubemaster.data.local.entity.RoomEntity {
        val vertices = (d.geometry as RoomGeometry.Polygon).vertices
        return com.example.cubemaster.data.local.entity.RoomEntity(
            id = d.id,
            projectId = d.projectId,
            name = d.name,
            verticesJson = json.encodeToString(vertices.map { mapOf("x" to it.x, "y" to it.y) }),
            heightMode = d.heightMode.name,
            heightMm = d.heightMm,
            cornerHeightsMmJson = d.cornerHeightsMm?.let { json.encodeToString(it) },
            roomType = d.roomType.name,
            sortOrder = d.sortOrder,
            originXM = d.originXM,
            originYM = d.originYM,
            rotationDeg = d.rotationDeg,
            syncState = d.syncState.name
        )
    }
```

Замінити `parseEdges` (рядки 147-158) на `parseVertices`:

```kotlin
    private fun parseVertices(jsonStr: String, json: Json): List<com.cubemaster.core.geometry.Vertex> {
        return try {
            val arr = json.parseToJsonElement(jsonStr).jsonArray
            arr.map { el ->
                val obj = el.jsonObject
                com.cubemaster.core.geometry.Vertex(
                    obj["x"]!!.jsonPrimitive.double,
                    obj["y"]!!.jsonPrimitive.double
                )
            }
        } catch (_: Exception) { emptyList() }
    }
```

Видалити тепер невикористовуваний `data class Quadruple` (рядок 198) і
`import com.cubemaster.core.model.Edge` більше не потрібен для цієї
функції (лишається, якщо використовується деінде у файлі — перевірити
компіляцією на Step 4).

- [ ] **Step 3: Оновити FirestoreRepository.uploadRoom**

Файл `app/src/main/java/com/example/cubemaster/data/remote/FirestoreRepository.kt`,
рядки 58-75 замінити на:

```kotlin
        val data = mapOf(
            "id" to roomEntity.id,
            "projectId" to roomEntity.projectId,
            "name" to roomEntity.name,
            "verticesJson" to roomEntity.verticesJson,
            "heightMode" to roomEntity.heightMode,
            "heightMm" to roomEntity.heightMm,
            "cornerHeightsMmJson" to roomEntity.cornerHeightsMmJson,
            "roomType" to roomEntity.roomType,
            "sortOrder" to roomEntity.sortOrder,
            "originXM" to roomEntity.originXM,
            "originYM" to roomEntity.originYM,
            "rotationDeg" to roomEntity.rotationDeg,
            "updatedAt" to System.currentTimeMillis()
        )
```

- [ ] **Step 4: Підняти версію Room-бази даних**

Файл `app/src/main/java/com/example/cubemaster/data/local/AppDatabase.kt`,
рядок 23: змінити `version = 4` на `version = 5`. Коментар над
`MIGRATION_3_4` (рядки 41-43) лишити як є; нова міграція НЕ додається —
`fallbackToDestructiveMigration()` (`di/AppModule.kt:50`) обробить
збільшення версії без явного шляху міграції, як узгоджено (без
реальних користувачів у продакшені).

- [ ] **Step 5: Оновити дефолтну кімнату й площу списку в RoomsViewModel**

Файл `app/src/main/java/com/example/cubemaster/presentation/rooms/RoomsViewModel.kt`,
рядки 61-67 замінити на:

```kotlin
                    val areaM2 = polygonAreaM2((room.geometry as RoomGeometry.Polygon).vertices)
```

(прибрати `import com.cubemaster.core.geometry.buildPolygon` якщо він
використовувався лише тут — перевірити компіляцією).

Рядок 99 замінити:

```kotlin
                    geometry = RoomGeometry.Polygon(rectangleVertices(4000, 3000)),
```

- [ ] **Step 6: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: помилки компіляції в `LayersViewModel.kt`/`SummaryViewModel.kt`/
`GeometryViewModel.kt`/`GeometryScreen.kt` — це очікувано, вони
виправляються в Task 3-6. Переконатись, що САМЕ ЦІ файли — єдине
джерело помилок (нема інших несподіваних збоїв від зміни моделі).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/data/local/entity/Entities.kt \
        app/src/main/java/com/example/cubemaster/data/local/converter/Converters.kt \
        app/src/main/java/com/example/cubemaster/data/remote/FirestoreRepository.kt \
        app/src/main/java/com/example/cubemaster/data/local/AppDatabase.kt \
        app/src/main/java/com/example/cubemaster/presentation/rooms/RoomsViewModel.kt
git commit -m "feat: зберігання геометрії кімнати як список вершин (Task 2/8)"
```

---

## Task 3: Спростити споживачів площі (LayersViewModel, SummaryViewModel)

**Файли:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/layers/LayersViewModel.kt:156-176`
- Modify: `app/src/main/java/com/example/cubemaster/presentation/summary/SummaryViewModel.kt:117-134`

**Interfaces:**
- Consumes: `RoomGeometry.Polygon(vertices)`, `polygonAreaM2()`, `wallAreaGross/Net()` (Task 1).

- [ ] **Step 1: LayersViewModel.computeArea — прибрати гілку Rectangle**

Файл `app/src/main/java/com/example/cubemaster/presentation/layers/LayersViewModel.kt`,
рядки 156-176 замінити на:

```kotlin
    private fun computeArea(room: Room, surface: Surface?, openings: List<Opening>): Double {
        val vertices = (room.geometry as RoomGeometry.Polygon).vertices
        return when (surface?.kind) {
            SurfaceKind.Floor, SurfaceKind.Ceiling -> polygonAreaM2(vertices)
            SurfaceKind.Wall -> {
                val edgeIndex = surface.wallEdgeIndex ?: 0
                val n = vertices.size
                val edgeLengthMm = if (edgeIndex in 0 until n) {
                    Math.round(distance(vertices[edgeIndex], vertices[(edgeIndex + 1) % n]) * 1000.0).toInt()
                } else 3000
                val h1 = room.heightMm ?: 2700
                val gross = wallAreaGross(edgeLengthMm, h1, h1)
                val wallOpenings = openings.filter { it.wallEdgeIndex == edgeIndex }
                wallAreaNet(gross, wallOpenings)
            }
            null -> 0.0
        }
    }
```

- [ ] **Step 2: SummaryViewModel.computeSurfaceArea — прибрати гілку Rectangle**

Файл `app/src/main/java/com/example/cubemaster/presentation/summary/SummaryViewModel.kt`,
рядки 117-134 замінити на:

```kotlin
    private fun computeSurfaceArea(room: Room, surface: Surface, openings: List<Opening>): Double {
        val vertices = (room.geometry as RoomGeometry.Polygon).vertices
        return when (surface.kind) {
            SurfaceKind.Floor, SurfaceKind.Ceiling -> polygonAreaM2(vertices)
            SurfaceKind.Wall -> {
                val edgeIndex = surface.wallEdgeIndex ?: 0
                val n = vertices.size
                val edgeLengthMm = if (edgeIndex in 0 until n) {
                    Math.round(distance(vertices[edgeIndex], vertices[(edgeIndex + 1) % n]) * 1000.0).toInt()
                } else 3000
                val gross = wallAreaGross(edgeLengthMm, room.heightMm ?: 2700, room.heightMm ?: 2700)
                wallAreaNet(gross, openings.filter { it.wallEdgeIndex == edgeIndex })
            }
        }
    }
```

- [ ] **Step 3: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: помилки лишаються тільки в `GeometryViewModel.kt` і
`GeometryScreen.kt` (Task 4 і 6).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/layers/LayersViewModel.kt \
        app/src/main/java/com/example/cubemaster/presentation/summary/SummaryViewModel.kt
git commit -m "refactor: спростити розрахунок площі під єдину модель Polygon (Task 3/8)"
```

---

## Task 4: Переписати GeometryViewModel під вершини

**Файли:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/geometry/GeometryViewModel.kt` (повна заміна)

**Interfaces:**
- Consumes: `Vertex`, `moveVertex/insertVertexOnEdge/removeVertex/setEdgeLength/setInteriorAngle/interiorAngleAtDeg/hasSelfIntersection/polygonAreaM2/perimeterM/verticesToEdges` (Task 1), `RoomRepository`, `SurfaceRepository`, `AttachmentRepository`, `AuthRepository` (без змін).
- Produces: `GeometryUiState(vertices, edgeLengthsMm, edgeAnglesDeg, selfIntersects, floorAreaM2, perimeter, openingWarning, ...)`, дії `moveVertex(index, newPosition)`, `insertVertex(edgeIndex, atPoint)`, `removeVertex(index): Boolean` (false якщо заблоковано), `setEdgeLength(edgeIndex, mm)`, `setInteriorAngle(vertexIndex, deg)`, `addEdgeAtEnd()`.

- [ ] **Step 1: Замінити весь файл**

Повністю замінити вміст
`app/src/main/java/com/example/cubemaster/presentation/geometry/GeometryViewModel.kt`:

```kotlin
package com.example.cubemaster.presentation.geometry

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubemaster.core.geometry.*
import com.cubemaster.core.model.*
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.domain.presetLayersFor
import com.example.cubemaster.domain.repository.AttachmentRepository
import com.example.cubemaster.domain.repository.RoomRepository
import com.example.cubemaster.domain.repository.SurfaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GeometryUiState(
    val room: Room? = null,
    val openings: List<Opening> = emptyList(),
    val surfaces: List<Surface> = emptyList(),
    val vertices: List<Vertex> = emptyList(),
    val heightMm: String = "2700",
    val heightMode: HeightMode = HeightMode.Uniform,
    val floorAreaM2: Double = 0.0,
    val perimeter: Double = 0.0,
    val selfIntersects: Boolean = false,
    val openingWarning: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    // Похідна таблиця чисел (довжина+кут) для UI — рахується з vertices щоразу.
    val edges: List<Edge> get() = if (vertices.size >= 3) verticesToEdges(vertices) else emptyList()
}

@HiltViewModel
class GeometryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val roomRepo: RoomRepository,
    private val surfaceRepo: SurfaceRepository,
    private val attachmentRepo: AttachmentRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val roomId: String = savedStateHandle["roomId"]!!
    private val uid: String get() = auth.currentUserId ?: ""
    private val _state = MutableStateFlow(GeometryUiState())
    val state: StateFlow<GeometryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            roomRepo.observeRoom(roomId).filterNotNull().first().let { room ->
                _state.update { s ->
                    s.copy(
                        room = room,
                        vertices = (room.geometry as RoomGeometry.Polygon).vertices,
                        heightMm = room.heightMm?.toString() ?: s.heightMm,
                        heightMode = room.heightMode
                    )
                }
                recalculate()
            }
        }
        viewModelScope.launch {
            roomRepo.observeOpenings(roomId).collect { openings ->
                _state.update { it.copy(openings = openings) }
                recalculate()
            }
        }
        viewModelScope.launch {
            surfaceRepo.observeSurfaces(roomId).collect { surfaces ->
                _state.update { it.copy(surfaces = surfaces) }
            }
        }
    }

    fun setHeight(v: String) { _state.update { it.copy(heightMm = v, hasUnsavedChanges = true) } }

    fun moveVertexAction(index: Int, newPosition: Vertex) {
        _state.update { it.copy(vertices = moveVertex(it.vertices, index, newPosition), hasUnsavedChanges = true) }
        recalculate()
    }

    // Вставляє вершину на стіні edgeIndex і переносить прорізи: ті, що були
    // на edgeIndex, лишаються на ній, якщо їхній offset менший за відстань
    // до нової точки, інакше переїжджають на нову стіну (edgeIndex+1) з
    // відповідно скоригованим offset. Прорізи на стінах після edgeIndex
    // зсувають номер стіни на +1.
    fun insertVertex(edgeIndex: Int, atPoint: Vertex) {
        val s = _state.value
        val a = s.vertices.getOrNull(edgeIndex) ?: return
        val splitDistMm = Math.round(distance(a, atPoint) * 1000.0).toInt()
        val newVertices = insertVertexOnEdge(s.vertices, edgeIndex, atPoint)
        _state.update { it.copy(vertices = newVertices, hasUnsavedChanges = true) }
        viewModelScope.launch {
            s.openings.forEach { o ->
                when {
                    o.wallEdgeIndex < edgeIndex -> Unit
                    o.wallEdgeIndex == edgeIndex && o.offsetMm < splitDistMm -> Unit
                    o.wallEdgeIndex == edgeIndex -> roomRepo.upsertOpening(o.copy(wallEdgeIndex = edgeIndex + 1, offsetMm = o.offsetMm - splitDistMm))
                    else -> roomRepo.upsertOpening(o.copy(wallEdgeIndex = o.wallEdgeIndex + 1))
                }
            }
        }
        recalculate()
    }

    // Повертає false і не видаляє вершину, якщо на суміжній стіні (до або
    // після) є хоч один проріз — користувач має спершу прибрати прорізи.
    fun removeVertexAction(index: Int): Boolean {
        val s = _state.value
        val n = s.vertices.size
        val prevEdge = (index - 1 + n) % n
        val hasAdjacentOpening = s.openings.any { it.wallEdgeIndex == prevEdge || it.wallEdgeIndex == index }
        if (hasAdjacentOpening) {
            _state.update { it.copy(error = "Спершу приберіть проріз(и) на суміжній стіні") }
            return false
        }
        val newVertices = removeVertex(s.vertices, index) ?: run {
            _state.update { it.copy(error = "Кімната повинна мати щонайменше 3 стіни") }
            return false
        }
        _state.update { it.copy(vertices = newVertices, hasUnsavedChanges = true) }
        viewModelScope.launch {
            s.openings.forEach { o ->
                if (o.wallEdgeIndex > index) {
                    roomRepo.upsertOpening(o.copy(wallEdgeIndex = o.wallEdgeIndex - 1))
                }
            }
        }
        recalculate()
        return true
    }

    fun setEdgeLengthAction(edgeIndex: Int, newLengthMm: Int) {
        _state.update { it.copy(vertices = setEdgeLength(it.vertices, edgeIndex, newLengthMm), hasUnsavedChanges = true) }
        recalculate()
    }

    fun setInteriorAngleAction(vertexIndex: Int, newAngleDeg: Double) {
        _state.update { it.copy(vertices = setInteriorAngle(it.vertices, vertexIndex, newAngleDeg), hasUnsavedChanges = true) }
        recalculate()
    }

    // Додає нову вершину посередині останньої стіни — зручний спосіб додати
    // кут без точного довгого натискання по канвасу.
    fun addEdgeAtEnd() {
        val s = _state.value
        val n = s.vertices.size
        if (n < 3) return
        val a = s.vertices[n - 1]
        val b = s.vertices[0]
        val mid = Vertex((a.x + b.x) / 2, (a.y + b.y) / 2)
        insertVertex(n - 1, mid)
    }

    fun addOpening(wallEdgeIndex: Int, offsetMm: Int, kind: OpeningKind, widthMm: Int, heightMm: Int, sillMm: Int) {
        viewModelScope.launch {
            roomRepo.upsertOpening(
                Opening(UUID.randomUUID().toString(), roomId, wallEdgeIndex, kind, widthMm, heightMm, sillMm, offsetMm)
            )
        }
    }

    fun updateOpening(id: String, wallEdgeIndex: Int, offsetMm: Int, kind: OpeningKind, widthMm: Int, heightMm: Int, sillMm: Int) {
        viewModelScope.launch {
            roomRepo.upsertOpening(Opening(id, roomId, wallEdgeIndex, kind, widthMm, heightMm, sillMm, offsetMm))
        }
    }

    fun moveOpening(openingId: String, newOffsetMm: Int) {
        val existing = _state.value.openings.firstOrNull { it.id == openingId } ?: return
        viewModelScope.launch { roomRepo.upsertOpening(existing.copy(offsetMm = newOffsetMm)) }
    }

    fun deleteOpening(id: String) {
        viewModelScope.launch { roomRepo.deleteOpening(id) }
    }

    fun saveGeometry() {
        val s = _state.value
        if (s.selfIntersects) {
            _state.update { it.copy(error = "Стіни контуру перетинаються. Виправте форму перед збереженням.") }
            return
        }
        if (s.openingWarning != null) {
            _state.update { it.copy(error = "Виправте прорізи перед збереженням: ${s.openingWarning}") }
            return
        }
        val room = s.room ?: return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            roomRepo.updateRoom(
                room.copy(
                    geometry = RoomGeometry.Polygon(s.vertices),
                    heightMm = s.heightMm.toIntOrNull()
                )
            )
            _state.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
        }
    }

    private fun recalculate() {
        val s = _state.value
        if (s.vertices.size < 3) return
        _state.update {
            it.copy(
                floorAreaM2 = polygonAreaM2(it.vertices),
                perimeter = perimeterM(it.vertices),
                selfIntersects = hasSelfIntersection(it.vertices),
                openingWarning = computeOpeningWarning(it.vertices, it.openings)
            )
        }
    }

    private fun computeOpeningWarning(vertices: List<Vertex>, openings: List<Opening>): String? {
        val n = vertices.size
        if (n < 3) return null
        val problems = mutableListOf<String>()
        for (i in 0 until n) {
            val wallLenMm = Math.round(distance(vertices[i], vertices[(i + 1) % n]) * 1000.0).toInt()
            val wallOpenings = openings.filter { it.wallEdgeIndex == i }
            problems += validateWallOpenings(wallLenMm, wallOpenings)
        }
        return problems.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun ensureSurfaceExists(kind: SurfaceKind): Surface {
        val existing = _state.value.surfaces.firstOrNull { it.kind == kind }
        if (existing != null) return existing
        val roomType = _state.value.room?.roomType
        val layers = if (roomType != null) presetLayersFor(roomType, kind) else emptyList()
        val surface = Surface(UUID.randomUUID().toString(), roomId, kind, null, layers)
        viewModelScope.launch { surfaceRepo.upsertSurface(surface) }
        return surface
    }

    fun addFloorplanPhoto(uri: Uri) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch {
            try {
                attachmentRepo.addPhoto(uid, projectId, roomId, AttachmentParent.Room, roomId, uri)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Не вдалось завантажити план: ${e.message}") }
            }
        }
    }

    fun observeAttachments(surfaceId: String) = attachmentRepo.observeForParent(AttachmentParent.Surface, surfaceId)

    fun addPhoto(surfaceId: String, uri: Uri) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch {
            try {
                attachmentRepo.addPhoto(uid, projectId, roomId, AttachmentParent.Surface, surfaceId, uri)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Не вдалось завантажити фото: ${e.message}") }
            }
        }
    }

    fun addPdf(surfaceId: String, uri: Uri) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch {
            try {
                attachmentRepo.addPdf(uid, projectId, roomId, AttachmentParent.Surface, surfaceId, uri)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Не вдалось завантажити PDF: ${e.message}") }
            }
        }
    }

    fun addNote(surfaceId: String, text: String) {
        val projectId = _state.value.room?.projectId ?: return
        viewModelScope.launch { attachmentRepo.addNote(projectId, roomId, AttachmentParent.Surface, surfaceId, text) }
    }

    fun deleteAttachment(attachment: Attachment) {
        viewModelScope.launch { attachmentRepo.delete(uid, attachment) }
    }
}
```

- [ ] **Step 2: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: помилки лишаються тільки в `GeometryScreen.kt` (використовує
старі поля `state.isRectangle`/`state.widthMm`/`state.edges: List<EdgeInput>` —
виправляється в Task 6) і `RoomPlanCanvas.kt`/`ClosureStatus` (Task 5).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/geometry/GeometryViewModel.kt
git commit -m "feat: переписати GeometryViewModel під вершини з живим взаємозв'язком (Task 4/8)"
```

---

## Task 5: RoomPlanCanvas — жести перетягування/вставки/видалення вершини

**Файли:**
- Modify: `app/src/main/java/com/example/cubemaster/ui/components/RoomPlanCanvas.kt`

**Interfaces:**
- Consumes: без нових зовнішніх залежностей (тільки внутрішня зміна `PlacedRoom.status`).
- Produces: `PlacedRoom(hasSelfIntersection: Boolean = false)` (замість `status: ClosureStatus`), нові колбеки `onVertexDrag: ((roomId, index, newVertex: Vertex) -> Unit)?`, `onWallLongPress: ((roomId, wallIndex, atPoint: Vertex) -> Unit)?`, `onVertexLongPress: ((roomId, index) -> Unit)?`.

- [ ] **Step 1: Замінити `status: ClosureStatus` на `hasSelfIntersection: Boolean`**

Файл `RoomPlanCanvas.kt`, рядки 45-51:

```kotlin
data class PlacedRoom(
    val roomId: String,
    val label: String,
    val vertices: List<Vertex>,
    val openings: List<Opening> = emptyList(),
    val hasSelfIntersection: Boolean = false
)
```

Прибрати `import com.cubemaster.core.geometry.ClosureStatus` (рядок 30).

Рядки 326-333 (визначення `strokeColor`) замінити на:

```kotlin
            val strokeColor = when (mode) {
                PlanInteractionMode.SingleRoomEdit ->
                    if (room.hasSelfIntersection) CubeMasterColors.error else CubeMasterColors.success
                PlanInteractionMode.ObjectOverview -> CubeMasterColors.gold
            }
```

- [ ] **Step 2: Додати нові параметри й хіт-тест вершини в жест-диспетчер**

Сигнатура `RoomPlanCanvas` (рядки 135-145) — додати нові параметри в
кінець списку (перед закриваючою дужкою):

```kotlin
    onRoomDragEnd: ((roomId: String) -> Unit)? = null,
    onVertexDrag: ((roomId: String, index: Int, newVertex: Vertex) -> Unit)? = null,
    onWallLongPress: ((roomId: String, wallIndex: Int, atPoint: Vertex) -> Unit)? = null,
    onVertexLongPress: ((roomId: String, index: Int) -> Unit)? = null
```

Усередині `awaitEachGesture` (після обчислення `transform`, рядок 168),
перед існуючим блоком хіт-тесту openings/wall (рядок 170), додати
хіт-тест вершини з НАЙВИЩИМ пріоритетом (вершина ближче до пальця, ніж
поріг для стіни/отвору):

```kotlin
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
```

(Далі лишити наявний код обчислення `targetOpening`/`targetWall`
без змін, але обгорнути його умовою `if (targetVertex == null)`, щоб
вершина мала пріоритет — весь наявний блок рядків 174-210 огорнути в
`if (targetVertex == null) { ... }`.)

У блок `when { ... }` (рядок 212) додати ПЕРШИЙ гілкою (перед
`targetOpening != null ->`):

```kotlin
                        targetVertex != null -> {
                            down.consume()
                            val (vertexIndex, room) = targetVertex!!
                            var totalDrag = Offset.Zero
                            var longPressFired = false
                            val longPressJob = kotlinx.coroutines.GlobalScope.launch {
                                kotlinx.coroutines.delay(500)
                                if (totalDrag.getDistance() < tapSlopPx) {
                                    longPressFired = true
                                    onVertexLongPress?.invoke(room.roomId, vertexIndex)
                                }
                            }
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    longPressJob.cancel()
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
                        }
```

Аналогічно для довгого натискання по СТІНІ (вставка вершини) — у гілці
`targetWall != null ->` (рядок 239), поточна логіка вже розрізняє
короткий тап (< tapSlopPx, викликає `onWallTap`) і панорамування
(перетягування). Додати ще одну гілку через `withTimeoutOrNull`: якщо
палець не рухався і минуло 500мс без відпускання — викликати
`onWallLongPress`, інакше на відпусканні без руху — короткий тап (як
зараз, `onWallTap`). Замінити тіло гілки `targetWall != null ->` на:

```kotlin
                        targetWall != null -> {
                            val (wallIndex, room) = targetWall!!
                            var totalDrag = Offset.Zero
                            var longPressFired = false
                            val longPressJob = kotlinx.coroutines.GlobalScope.launch {
                                kotlinx.coroutines.delay(500)
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
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    longPressJob.cancel()
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
                                if (totalDrag.getDistance() > tapSlopPx) {
                                    longPressJob.cancel()
                                    change.consume()
                                    panOffset += delta
                                }
                            }
                        }
```

Додати імпорт `kotlinx.coroutines.launch` і `kotlinx.coroutines.delay`
на початку файлу (поруч з іншими `androidx.compose` імпортами).

**Примітка для виконавця:** `GlobalScope.launch` тут прийнятний, бо
job явно скасовується (`cancel()`) у кожній гілці виходу з жесту —
витоку немає, а `awaitEachGesture`/`pointerInput` не надає власного
`CoroutineScope` для дочірніх задач із затримкою. Якщо в проєкті вже є
патерн `rememberCoroutineScope()` для подібного — використати його
замість `GlobalScope` (перевірити інші файли компонентів на такий
патерн перед реалізацією цього кроку).

- [ ] **Step 3: Оновити виклик у ObjectPlanScreen.kt (дефолтне значення полів)**

Файл `app/src/main/java/com/example/cubemaster/presentation/objectplan/ObjectPlanScreen.kt`,
рядок 49 — `PlacedRoom(...)` там не задає `status` явно, тож після
перейменування поля на `hasSelfIntersection` з дефолтом `false`
додаткових змін не потрібно. Перевірити компіляцією.

- [ ] **Step 4: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: помилки лишаються тільки в `GeometryScreen.kt` (Task 6).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/ui/components/RoomPlanCanvas.kt \
        app/src/main/java/com/example/cubemaster/presentation/objectplan/ObjectPlanScreen.kt
git commit -m "feat: жести перетягування/вставки/видалення вершини на RoomPlanCanvas (Task 5/8)"
```

---

## Task 6: Переписати GeometryScreen — один екран замість трьох вкладок

**Файли:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/geometry/GeometryScreen.kt`
- Modify: `app/src/main/java/com/example/cubemaster/presentation/geometry/FreehandDrawCanvas.kt`

**Interfaces:**
- Consumes: `GeometryUiState`/`GeometryViewModel` (Task 4), `RoomPlanCanvas` з новими колбеками (Task 5).

- [ ] **Step 1: Прибрати перемикач вкладок/режимів у `GeometryTab`, лишити один екран**

**Примітка щодо спрощення проти мокапу зі спеку:** спек показував вибір
"Прямокутник / Імпортувати фото плану" саме в діалозі створення нової
кімнати (`RoomsScreen.kt`). Оскільки нова кімната (Task 2) завжди
одразу отримує дефолтний прямокутник, а `RoomsScreen.kt` не входить у
файли цього плану, реалізовано простіший еквівалент: кнопка
"Імпортувати фото плану" лишається доступною ЗАВЖДИ на екрані геометрії
(не лише для нової кімнати) — і замінює поточну форму на обведену по
фото. Це дає ту саму цінність (обвести кімнату по фото замість ручного
введення) з меншим обсягом змін, і додатково дозволяє замінити форму
пізніше, а не тільки при створенні.

Файл `GeometryScreen.kt`, замінити функцію `GeometryTab` цілком (рядки
160-326) на:

```kotlin
@Composable
private fun GeometryTab(
    state: GeometryUiState,
    viewModel: GeometryViewModel,
    onWallTap: (wallIndex: Int, offsetMm: Int) -> Unit,
    onOpeningTap: (openingId: String) -> Unit
) {
    var pendingRemoveVertex by remember { mutableStateOf<Int?>(null) }
    var showPhotoImport by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showPhotoImport = !showPhotoImport },
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (showPhotoImport) "Сховати імпорт фото плану" else "Імпортувати фото плану (замінить поточну форму)") }
    if (showPhotoImport) {
        FloorplanDrawFlow(
            onShapeConfirmed = { edges ->
                val vertices = buildVerticesFromEdges(edges)
                viewModel.applyInitialVertices(vertices)
                showPhotoImport = false
            },
            onImagePicked = { uri -> viewModel.addFloorplanPhoto(uri) },
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    if (state.vertices.size < 3) return

    Text(
        "Перетягніть вершину, щоб змінити форму. Довге натискання по стіні додає кут, довге натискання на вершині — прибирає її. Короткий тап по стіні додає проріз.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    RoomPlanCanvas(
        rooms = listOf(
            PlacedRoom(
                roomId = state.room?.id ?: "",
                label = state.room?.name ?: "",
                vertices = state.vertices,
                openings = state.openings,
                hasSelfIntersection = state.selfIntersects
            )
        ),
        mode = PlanInteractionMode.SingleRoomEdit,
        onWallTap = { _, wallIndex, offsetMm -> onWallTap(wallIndex, offsetMm) },
        onOpeningDrag = { openingId, newOffsetMm -> viewModel.moveOpening(openingId, newOffsetMm) },
        onOpeningTap = onOpeningTap,
        onVertexDrag = { _, index, newVertex -> viewModel.moveVertexAction(index, newVertex) },
        onWallLongPress = { _, wallIndex, atPoint -> viewModel.insertVertex(wallIndex, atPoint) },
        onVertexLongPress = { _, index -> pendingRemoveVertex = index },
        modifier = Modifier.fillMaxWidth().height(280.dp)
    )

    // Таблиця чисел — синхронна з канвасом в обидва боки
    state.edges.forEachIndexed { i, edge ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${i + 1}.", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(24.dp))
            NumberInputField(
                value = edge.lengthMm.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { viewModel.setEdgeLengthAction(i, it) } },
                label = "Довжина",
                unit = "мм",
                helperText = "Довжина цієї стіни",
                modifier = Modifier.weight(1f)
            )
            NumberInputField(
                value = String.format("%.1f", edge.interiorAngleDeg),
                onValueChange = { v -> v.replace(",", ".").toDoubleOrNull()?.let { viewModel.setInteriorAngleAction(i, it) } },
                label = "Кут",
                unit = "°",
                helperText = "Внутрішній кут у вершині на початку цієї стіни, 90° — прямий",
                modifier = Modifier.weight(1f)
            )
        }
    }
    OutlinedButton(onClick = { viewModel.addEdgeAtEnd() }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Стіна")
    }

    NumberInputField(
        value = state.heightMm,
        onValueChange = viewModel::setHeight,
        label = "Висота стелі",
        unit = "мм"
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Площа підлоги: ${String.format("%.2f", state.floorAreaM2)} м²", style = MaterialTheme.typography.bodyMedium)
            Text("Периметр: ${String.format("%.2f", state.perimeter)} м", style = MaterialTheme.typography.bodyMedium)
        }
    }

    pendingRemoveVertex?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingRemoveVertex = null },
            title = { Text("Прибрати кут?") },
            text = { Text("Дві сусідні стіни об'єднаються в одну.") },
            confirmButton = {
                TextButton(onClick = {
                    // Якщо видалення заблоковано (сусідній проріз або мінімум 3 стіни),
                    // viewModel.removeVertexAction сама виставляє state.error — той самий
                    // LaunchedEffect(state.error) на початку GeometryScreen (рядки 44-49)
                    // покаже Snackbar, окремої обробки результату тут не потрібно.
                    viewModel.removeVertexAction(index)
                    pendingRemoveVertex = null
                }) { Text("Прибрати") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoveVertex = null }) { Text("Скасувати") } }
        )
    }
}

private fun buildVerticesFromEdges(edges: List<Edge>): List<Vertex> {
    val vertices = mutableListOf(Vertex(0.0, 0.0))
    var heading = 0.0
    var x = 0.0
    var y = 0.0
    for (i in edges.indices) {
        val lengthM = edges[i].lengthMm / 1000.0
        x += lengthM * kotlin.math.cos(heading)
        y += lengthM * kotlin.math.sin(heading)
        if (i < edges.size - 1) vertices.add(Vertex(x, y))
        if (i + 1 < edges.size) {
            val exterior = Math.PI - Math.toRadians(edges[(i + 1) % edges.size].interiorAngleDeg)
            heading -= exterior
        }
    }
    return vertices
}
```

- [ ] **Step 2: Додати `applyInitialVertices` у GeometryViewModel**

У файл `GeometryViewModel.kt` (з Task 4) додати нову дію (поруч із
`moveVertexAction`):

```kotlin
    fun applyInitialVertices(vertices: List<Vertex>) {
        _state.update { it.copy(vertices = vertices, hasUnsavedChanges = true) }
        recalculate()
    }
```

- [ ] **Step 3: Прибрати непотрібний імпорт `ClosureStatus` у GeometryScreen.kt**

Рядок 23 (`import com.cubemaster.core.geometry.ClosureStatus`) видалити
— більше не використовується після заміни `GeometryTab`.

- [ ] **Step 4: Спростити `OpeningsTab` під єдину модель і перейменувати `polygonVertices` -> `vertices`**

Файл `GeometryScreen.kt`, функція `OpeningsTab`, рядок 389
(`val edgeCount = if (state.isRectangle) 4 else state.edges.size` —
на цей момент вже не компілюється, бо `state.isRectangle` видалено в
Task 4) замінити на:

```kotlin
    val edgeCount = state.edges.size
```

`GeometryUiState` у Task 4 має поле `vertices`, а не `polygonVertices`
(старе поле перейменовано). У цьому ж файлі лишились ще два місця зі
старою назвою — замінити `state.polygonVertices` на `state.vertices`:

- Рядок 123 (у головному composable `GeometryScreen`):
  `val wallLen = wallLengthMm(state.polygonVertices, request.wallIndex)`
  → `val wallLen = wallLengthMm(state.vertices, request.wallIndex)`
- Рядок 391 (у `OpeningsTab`):
  `val wallLen = wallLengthMm(state.polygonVertices, i)`
  → `val wallLen = wallLengthMm(state.vertices, i)`

- [ ] **Step 5: Прибрати з FreehandDrawCanvas.kt тепер невикористовуваний параметр `initialVertices`**

Блок-порожній-аркуш (`FreehandDrawCanvas` без `backgroundImage`) більше
не викликається з `GeometryScreen.kt` — компонент лишається лише для
`FloorplanDrawFlow` (обведення по фото), де `initialVertices` ніколи не
передавався. У файлі
`app/src/main/java/com/example/cubemaster/presentation/geometry/FreehandDrawCanvas.kt`:

Сигнатуру (рядки 34-40) повернути без `initialVertices`:

```kotlin
@Composable
fun FreehandDrawCanvas(
    onShapeConfirmed: (List<Edge>) -> Unit,
    modifier: Modifier = Modifier,
    backgroundImage: ImageBitmap? = null,
    calibratedPxPerMeter: Float? = null
) {
    val rawPath = remember { mutableStateListOf<Offset>() }
    var resultVertices by remember { mutableStateOf<List<Vertex>?>(null) }
```

(Прибрати рядки коментаря 42-43 про "стартуємо з уже існуючого
контуру" — більше не актуально для цього компонента.)

- [ ] **Step 6: Замінити попередження нев'язки на попередження самоперетину в головному composable**

Файл `GeometryScreen.kt`, головна функція `GeometryScreen` (не
`GeometryTab`), рядки 116-118:

```kotlin
            // Попередження нев'язки
            state.closureWarning?.let { WarningCard(it) }
            state.openingWarning?.let { WarningCard("Прорізи: $it") }
```

замінити на:

```kotlin
            if (state.selfIntersects) {
                WarningCard("Стіни контуру перетинаються — виправте форму перед збереженням")
            }
            state.openingWarning?.let { WarningCard("Прорізи: $it") }
```

- [ ] **Step 7: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL, без помилок.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/geometry/GeometryScreen.kt \
        app/src/main/java/com/example/cubemaster/presentation/geometry/GeometryViewModel.kt \
        app/src/main/java/com/example/cubemaster/presentation/geometry/FreehandDrawCanvas.kt
git commit -m "feat: єдиний екран редагування форми кімнати замість трьох вкладок (Task 6/8)"
```

---

## Task 7: Повний прогін тестів і компіляції

**Файли:** немає нових — це задача перевірки.

- [ ] **Step 1: Повний тестовий прогін**

Run: `./gradlew :cubemaster-core:test :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL, усі тести зелені.

- [ ] **Step 2: Пошук залишкових посилань на видалені типи**

Run: `grep -rn "RoomGeometry.Rectangle\|ClosureStatus\|buildPolygon\|distributeClosureError\|isRectangle\|EdgeInput" app/src cubemaster-core/src`
Expected: порожній результат (жодних збігів). Якщо щось знайдено —
виправити перед комітом.

- [ ] **Step 3: Commit (якщо були правки за Step 2)**

```bash
git add -A
git commit -m "fix: прибрати залишкові посилання на стару модель геометрії (Task 7/8)"
```

(Пропустити коміт, якщо Step 2 не знайшов нічого для правки.)

---

## Task 8: Перевірка на пристрої

**Файли:** немає — ручна QA-перевірка через adb (скіл `verify` у
`.claude/skills/verify/`).

- [ ] **Step 1: Зібрати й встановити debug-збірку**

Run:
```bash
./gradlew :app:installDebug --console=plain
adb shell pm clear com.example.cubemaster
adb shell am start -n com.example.cubemaster/.MainActivity
```

- [ ] **Step 2: Пройти сценарій — нова кімната відкривається з готовим прямокутником**

Створити проєкт → кімнату → відкрити геометрію. Очікується: канвас
одразу показує прямокутник 4000×3000 (не порожній), таблиця чисел
показує 4 стіни з довжинами 4000/3000/4000/3000 і кутами 90°.

- [ ] **Step 3: Перевірити перетягування вершини**

Перетягнути один кут прямокутника вбік. Очікується: сусідні дві стіни
міняють довжину в таблиці чисел у реальному часі, дві протилежні стіни
не змінюються.

- [ ] **Step 4: Перевірити довге натискання по стіні (додати кут) і на вершині (прибрати кут)**

Довге натискання по стіні — з'являється нова вершина, стіна ділиться на
дві (таблиця показує на одну стіну більше). Довге натискання на
вершині — з'являється діалог "Прибрати кут?", підтвердження зменшує
кількість стін на одну.

- [ ] **Step 5: Перевірити, що короткий тап по стіні й далі додає проріз**

Короткий тап (без утримання) по вільній стіні відкриває діалог
додавання проріму, як і раніше.

- [ ] **Step 6: Перевірити блокування видалення вершини з прорізом на суміжній стіні**

Додати вікно на стіну, потім спробувати довгим натисканням прибрати
вершину, суміжну з цією стіною. Очікується: видалення не відбувається
(або з'являється повідомлення про блокування).

- [ ] **Step 7: Прибрати тестові дані**

Run: `adb shell pm clear com.example.cubemaster`

Немає кроку коміту — це лише верифікація; якщо Step 2-6 виявлять баги,
повернутись до відповідної задачі (4, 5 або 6), виправити, і повторно
пройти Task 7 і 8.
