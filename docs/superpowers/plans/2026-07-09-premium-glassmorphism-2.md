# Premium Glassmorphism 2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Замінити фейкову (градієнт+альфа) glassmorphism-реалізацію CubeMaster на справжній backdrop-blur (Haze), відновити коректну кольорову палітру бренду, техно-стилізувати орнамент, виправити застосування табличних цифр і уніфікувати мову картки на чотирьох ключових екранах.

**Architecture:** Один спільний `HazeState` на кожен екран (`rememberHazeState()`), фоновий контейнер екрана позначається `Modifier.hazeSource(state)`, кожна `GlassCard` отримує той самий `state` і малює розмитий фон через `Modifier.hazeEffect(...)`. Без переданого `state` `GlassCard` деградує до тонованого напівпрозорого фону (сумісний дефолт для діалогів/карток без фону для розмиття).

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2026.02.01), Material 3 (тільки інфраструктура), Haze 1.7.2 (`dev.chrisbanes.haze`).

## Global Constraints

- minSdk піднімається з 29 до 31 (Android 12) — рішення users; Haze сам вимикає blur нижче SDK 31 (`isBlurEnabledByDefault() = SDK_INT >= 31`), тож нижче 31 сенсу підтримувати немає.
- Haze версія фіксується на `1.7.2` (остання стабільна на Maven Central, перевірено). Ліцензія Apache 2.0 — сумісна з субпідрядним використанням.
- Бренд-кольори: `red = #CE1126`, `gold = #C9A227` — завжди різні константи, ніколи не прирівнювати одну до одної.
- Українською — лише коментарі в коді; жодних нових user-facing рядків цей план не додає.
- Немає інфраструктури Compose UI-тестів у цьому репозиторії (перевірено — жодного `*Test.kt` у `presentation/` чи `ui/`). Автоматична перевірка для чистого Kotlin-коду (кольори) — JUnit на JVM. Візуальна поведінка (blur, вирівняні цифри) — вручну через `verify` skill (adb) в останній задачі.
- Кожна задача повинна залишати проєкт компільованим: `./gradlew :app:compileDebugKotlin --console=plain`.

---

### Task 1: Піднятий minSdk і залежність Haze

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:19`

**Interfaces:**
- Produces: Gradle-залежність `dev.chrisbanes.haze:haze:1.7.2`, доступна для `import dev.chrisbanes.haze.*` у наступних задачах.

- [ ] **Step 1: Додати версію й координати Haze в `gradle/libs.versions.toml`**

У секції `[versions]` (після рядка `firebasePerf = "2.0.2"`, рядок 24) додати:

```toml
haze = "1.7.2"
```

У секції `[libraries]` (після блоку Coil, після рядка `coil-compose = ...`, рядок 80) додати:

```toml

# Haze (справжній backdrop blur для glassmorphism)
haze = { group = "dev.chrisbanes.haze", name = "haze", version.ref = "haze" }
```

- [ ] **Step 2: Підняти minSdk і додати залежність в `app/build.gradle.kts`**

Рядок 19, було:

```kotlin
        minSdk = 29
```

стає:

```kotlin
        minSdk = 31
```

У блоці `dependencies { ... }`, після рядка `implementation(libs.coil.compose)` (рядок 93), додати:

```kotlin
    implementation(libs.haze)
```

- [ ] **Step 3: Перевірити, що проєкт синхронізується і компілюється**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL` (Haze ще не використовується в коді на цьому кроці, тож жодних нових помилок компіляції бути не може — перевіряємо лише що Gradle резолвить нову залежність і що зміна minSdk нічого не зламала).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: підняти minSdk до 31, додати залежність Haze для справжнього backdrop blur"
```

---

### Task 2: Кольори — прибрати мертвий код, виправити бренд-палітру

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/ui/theme/Color.kt`
- Test: `app/src/test/java/com/example/cubemaster/ui/theme/CubeMasterColorsTest.kt`

**Interfaces:**
- Consumes: нічого (базовий шар).
- Produces: `CubeMasterColors.red`, `.gold`, `.redMuted`, `.redGlow`, `.graphite`, `.graphiteSoft`, `.graphiteMid` — імена і типи (`androidx.compose.ui.graphics.Color`) не змінюються, змінюються лише значення; `glassSurfaceLight`, `glassSurfaceDark`, `goldMuted` — видаляються (жодних споживачів немає, перевірено `grep`).

- [ ] **Step 1: Написати тест, що фіксує баг `redMuted` і розділення red/gold**

Створити `app/src/test/java/com/example/cubemaster/ui/theme/CubeMasterColorsTest.kt`:

```kotlin
package com.example.cubemaster.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CubeMasterColorsTest {

    @Test
    fun `red and gold are distinct brand colors`() {
        assertNotEquals(CubeMasterColors.red, CubeMasterColors.gold)
    }

    @Test
    fun `redMuted keeps the red hue, only alpha is reduced`() {
        val base = CubeMasterColors.red
        val muted = CubeMasterColors.redMuted
        assertEquals(base.red, muted.red, 0.001f)
        assertEquals(base.green, muted.green, 0.001f)
        assertEquals(base.blue, muted.blue, 0.001f)
        assertNotEquals(1f, muted.alpha)
    }
}
```

- [ ] **Step 2: Запустити тест і переконатись, що другий тест падає**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.cubemaster.ui.theme.CubeMasterColorsTest" --console=plain`
Expected: FAIL на `redMuted keeps the red hue...` — поточний `Color(0xFFCE11264D)` через `shl 32` у конструкторі `Color(Long)` обрізає старший байт, і результат — R=17,G=38,B=77 (темна бірюза), а не червоний з альфою. Перший тест (`red != gold`) вже проходить (у поточному коді вони й так різні) — це очікувано, він тут як регресійний засторог на майбутнє.

- [ ] **Step 3: Переписати `Color.kt`**

Замінити весь вміст `app/src/main/java/com/example/cubemaster/ui/theme/Color.kt` на:

```kotlin
package com.example.cubemaster.ui.theme

import androidx.compose.ui.graphics.Color

object CubeMasterColors {
    val red = Color(0xFFCE1126)
    val redDeep = Color(0xFF8B0F1F)
    // 30% прозорість, той самий відтінок — альфа йде першим байтом (0xAARRGGBB),
    // не останнім: Color(Long) робить (value shl 32), тож зайві біти в кінці
    // обрізаються і колір "зʼїжджає" на випадковий RGB, якщо альфу дописати в хвіст.
    val redMuted = Color(0x4DCE1126)
    // World glow для тіні картки в темній темі — м'якший за redMuted.
    val redGlow = Color(0x33CE1126)
    val white = Color(0xFFFFFFFF)
    val linen = Color(0xFFF7F3EC)
    // Глибший OLED-чорний — краще тримає контраст на реальних AMOLED-екранах
    // будівельників на об'єкті, ніж попередній #1C1C1E.
    val graphite = Color(0xFF0A0A0C)
    val graphiteSoft = Color(0xFF16161A)
    val graphiteMid = Color(0xFF2C2C35)
    val gold = Color(0xFFC9A227)
    val textPrimary = Color(0xFF1C1C1E)
    val textSecondary = Color(0xFF6C6C70)
    val textOnDark = Color(0xFFF7F3EC)
    val divider = Color(0xFFE5E0D8)
    val success = Color(0xFF34C759)
    val warning = Color(0xFFFF9500)
    val error = Color(0xFFFF3B30)
}
```

Видалено: `glassSurfaceLight`, `glassSurfaceDark`, `goldMuted` (жодних споживачів у коді). Додано: `redGlow`. Виправлено: `redMuted` (правильний порядок байтів). Змінено: `graphite`, `graphiteSoft`, `graphiteMid` (глибший OLED).

- [ ] **Step 4: Запустити тест знову і переконатись, що проходить**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.cubemaster.ui.theme.CubeMasterColorsTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 2 тести пройдено.

- [ ] **Step 5: Перевірити компіляцію всього застосунку**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL` — `glassSurfaceLight`/`glassSurfaceDark` ще використовуються в `GlassCard.kt` на цьому кроці, тож якщо збірка впаде з "unresolved reference", це очікувано і виправляється в Task 3 (наступна задача якраз переписує `GlassCard.kt`). Якщо потрібно тримати компіляцію зеленою на кожному кроці окремо — виконати Step 3 цього завдання і Step 3 Task 3 в одному коміті замість двох (дозволено, якщо назва коміту описує обидві зміни).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/ui/theme/Color.kt app/src/test/java/com/example/cubemaster/ui/theme/CubeMasterColorsTest.kt
git commit -m "fix: прибрати мертві кольори glassSurfaceLight/Dark і goldMuted, виправити формат redMuted, поглибити OLED-графіт"
```

---

### Task 3: GlassCard — справжній blur через Haze

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/ui/components/GlassCard.kt`

**Interfaces:**
- Consumes: `CubeMasterColors.red`, `.redGlow`, `.graphite` (Task 2); `dev.chrisbanes.haze.{HazeState, HazeStyle, HazeTint, hazeEffect}` (Task 1).
- Produces: `GlassCard(modifier, hazeState: HazeState? = null, cornerRadius: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`. Новий параметр `hazeState` — необов'язковий, тож усі існуючі викови `GlassCard(...)` без цього параметра лишаються коректними (деградують до статичного напівпрозорого фону без blur). `SurfaceCard` видаляється (нуль викликів у коді, перевірено `grep -rn "SurfaceCard("`).

- [ ] **Step 1: Перевірити відсутність викликів `SurfaceCard`, щоб видалення було безпечним**

Run: `grep -rn "SurfaceCard(" --include="*.kt" app/src/main/java`
Expected: жодного результату (окрім самого визначення в `GlassCard.kt`, яке ми видаляємо).

- [ ] **Step 2: Переписати `GlassCard.kt`**

Замінити весь вміст `app/src/main/java/com/example/cubemaster/ui/components/GlassCard.kt` на:

```kotlin
package com.example.cubemaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cubemaster.ui.theme.CubeMasterColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Скляна картка. Якщо переданий [hazeState] (спільний на весь екран, від
 * `rememberHazeState()`), картка справді розмиває вміст позаду себе — той екран
 * повинен мати `Modifier.hazeSource(hazeState)` на своєму фоновому контейнері.
 * Без [hazeState] (наприклад, картка всередині модального діалогу над системним
 * scrim, де немає що розмивати) — деградує до статичного тонованого фону.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == CubeMasterColors.graphite
    val borderColor = if (isDark)
        Color.White.copy(alpha = 0.08f)
    else
        Color.White.copy(alpha = 0.6f)

    val shape = RoundedCornerShape(cornerRadius)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 4.dp else 8.dp,
                shape = shape,
                ambientColor = if (isDark) Color.Black.copy(0.4f) else Color.Black.copy(0.08f),
                spotColor = if (isDark) CubeMasterColors.redGlow else CubeMasterColors.red.copy(0.04f)
            )
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = surfaceColor,
                            tint = HazeTint(surfaceColor.copy(alpha = if (isDark) 0.55f else 0.65f)),
                            blurRadius = 20.dp,
                            noiseFactor = 0.15f
                        )
                    )
                } else {
                    Modifier.background(surfaceColor.copy(alpha = if (isDark) 0.82f else 0.92f))
                }
            )
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}
```

- [ ] **Step 3: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`. Якщо були помилки "unresolved reference: SurfaceCard" — значить лишився виклик, знайти й замінити на `GlassCard(cornerRadius = 12.dp, ...)`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/ui/components/GlassCard.kt
git commit -m "feat: GlassCard використовує Haze для справжнього backdrop blur, видалено SurfaceCard"
```

---

### Task 4: OrnamentalDivider — техно-стилізація

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/ui/components/OrnamentalDivider.kt`

**Interfaces:**
- Consumes: `CubeMasterColors.red`, `.redMuted` (Task 2).
- Produces: `OrnamentalDivider(modifier, color: Color = CubeMasterColors.red, height: Dp = 8.dp)` — сигнатура сумісна (той самий набір параметрів; `color` за замовчуванням тепер повний `red`, бо прозорість малюється градієнтом усередині, а не переданим кольором). `ThinDivider` — без змін.

- [ ] **Step 1: Переконатись, де використовується `OrnamentalDivider` з нестандартними параметрами**

Run: `grep -rn "OrnamentalDivider(" --include="*.kt" app/src/main/java`
Expected: викови в `CommonComponents.kt` (`EmptyState`, без явного `color`) і `SummaryScreen.kt` (без явного `color`) — обидва покладаються на дефолт, тож зміна дефолтного вигляду безпечна і не вимагає правок у місцях виклику.

- [ ] **Step 2: Переписати `OrnamentalDivider` (без зміни `ThinDivider`)**

У `app/src/main/java/com/example/cubemaster/ui/components/OrnamentalDivider.kt` замінити рядки 16-66 (від коментаря `// Геометричний розділювач...` до закриваючої `}` функції `OrnamentalDivider`) на:

```kotlin
// Технічна hairline-лінія з акцентним сегментом по центру — читається як
// UI-індикатор/маркер прогресу, а не як декоративний орнамент.
@Composable
fun OrnamentalDivider(
    modifier: Modifier = Modifier,
    color: Color = CubeMasterColors.red,
    height: Dp = 8.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val midY = size.height / 2f

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    color.copy(alpha = 0f),
                    CubeMasterColors.redMuted,
                    color.copy(alpha = 0f)
                )
            ),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx()
        )

        val segmentWidth = 24.dp.toPx()
        val segmentHeight = 4.dp.toPx()
        val segmentLeft = (w - segmentWidth) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(segmentLeft, midY - segmentHeight / 2f),
            size = androidx.compose.ui.geometry.Size(segmentWidth, segmentHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(segmentHeight / 2f)
        )
    }
}
```

Додати імпорт `androidx.compose.ui.graphics.Brush` до списку імпортів на початку файлу (після `import androidx.compose.ui.graphics.Color`, рядок 9).

- [ ] **Step 3: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/ui/components/OrnamentalDivider.kt
git commit -m "feat: техно-стилізація OrnamentalDivider — hairline з акцентним сегментом замість ромбів"
```

---

### Task 5: Виправити застосування TabularNumberStyle у кошторисі

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/summary/SummaryScreen.kt:104-112`

**Interfaces:**
- Consumes: `com.example.cubemaster.ui.theme.TabularNumberStyle` (без змін у `Type.kt`).

- [ ] **Step 1: Замінити частковий стиль на повний**

У `app/src/main/java/com/example/cubemaster/presentation/summary/SummaryScreen.kt`, рядки 104-111, було:

```kotlin
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${String.format("%.2f", line.totalQty)} ${unitShortLabel(line.unit)}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = com.example.cubemaster.ui.theme.TabularNumberStyle.fontFamily
                    ),
                    color = CubeMasterColors.gold
                )
            }
```

стає:

```kotlin
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${String.format("%.2f", line.totalQty)} ${unitShortLabel(line.unit)}",
                    style = com.example.cubemaster.ui.theme.TabularNumberStyle.copy(
                        fontWeight = MaterialTheme.typography.titleSmall.fontWeight,
                        fontSize = MaterialTheme.typography.titleSmall.fontSize
                    ),
                    color = CubeMasterColors.gold
                )
            }
```

Це застосовує повний `TabularNumberStyle` (з `fontFeatureSettings = "tnum"`) як базу, лишаючи вагу й розмір шрифту `titleSmall`, як було візуально раніше — але тепер з увімкненими моноширинними цифрами.

- [ ] **Step 2: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/summary/SummaryScreen.kt
git commit -m "fix: застосувати повний TabularNumberStyle у зведенні кошторису (tnum ігнорувався)"
```

---

### Task 6: Haze у ProjectsScreen

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/projects/ProjectsScreen.kt`

**Interfaces:**
- Consumes: `GlassCard(hazeState = ...)` (Task 3); `dev.chrisbanes.haze.{rememberHazeState, hazeSource}`.
- Produces: `ProjectCard(project, hazeState: HazeState?, onClick, onSummary, onEdit, onDelete)` — сигнатура приватної функції змінюється (новий параметр після `project`).

- [ ] **Step 1: Додати імпорти**

У `app/src/main/java/com/example/cubemaster/presentation/projects/ProjectsScreen.kt`, після рядка 21 (`import com.example.cubemaster.ui.theme.CubeMasterColors`) додати:

```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Створити `hazeState` і позначити фоновий контейнер**

Рядок 36 (`val state by viewModel.state.collectAsStateWithLifecycle()`), додати одразу після:

```kotlin
    val hazeState = rememberHazeState()
```

Рядок 76, було:

```kotlin
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
```

стає:

```kotlin
        Box(modifier = Modifier.padding(padding).fillMaxSize().hazeSource(hazeState)) {
```

- [ ] **Step 3: Передати `hazeState` у картку**

Рядок 87-93, було:

```kotlin
                    items(state.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onProjectClick(project.id) },
                            onSummary = { onSummaryClick(project.id) },
                            onEdit = { projectToEdit = project },
                            onDelete = { projectToDelete = project }
                        )
                    }
```

стає:

```kotlin
                    items(state.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            hazeState = hazeState,
                            onClick = { onProjectClick(project.id) },
                            onSummary = { onSummaryClick(project.id) },
                            onEdit = { projectToEdit = project },
                            onDelete = { projectToDelete = project }
                        )
                    }
```

Сигнатура `private fun ProjectCard(...)` (рядок 146-152), було:

```kotlin
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onSummary: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
```

стає:

```kotlin
private fun ProjectCard(
    project: Project,
    hazeState: HazeState?,
    onClick: () -> Unit,
    onSummary: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
```

Рядок 153, було:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
```

стає:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState, onClick = onClick) {
```

- [ ] **Step 4: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/projects/ProjectsScreen.kt
git commit -m "feat: увімкнути справжній blur (Haze) на картках проєктів"
```

---

### Task 7: Haze у RoomsScreen

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/rooms/RoomsScreen.kt`

**Interfaces:**
- Consumes: те саме, що Task 6.
- Produces: `RoomCard(item, attachments, hazeState: HazeState?, onClick, onDelete, onAddPhoto, onAddPdf, onAddNote, onDeleteAttachment)`.

- [ ] **Step 1: Додати імпорти**

Після рядка 29 (`import com.example.cubemaster.ui.theme.CubeMasterColors`) додати:

```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Створити `hazeState` і позначити фоновий контейнер**

Рядок 42 (`val state by viewModel.state.collectAsStateWithLifecycle()`), додати одразу після:

```kotlin
    val hazeState = rememberHazeState()
```

Рядок 84, було:

```kotlin
        Box(Modifier.padding(padding).fillMaxSize()) {
```

стає:

```kotlin
        Box(Modifier.padding(padding).fillMaxSize().hazeSource(hazeState)) {
```

- [ ] **Step 3: Передати `hazeState` у картку**

Рядки 98-107, було:

```kotlin
                    items(state.rooms, key = { it.room.id }) { item ->
                        val attachments by viewModel.observeAttachments(item.room.id)
                            .collectAsStateWithLifecycle(initialValue = emptyList())
                        RoomCard(
                            item = item,
                            attachments = attachments,
                            onClick = { onRoomClick(item.room.id) },
                            onDelete = { viewModel.deleteRoom(item.room.id) },
                            onAddPhoto = { uri -> viewModel.addPhoto(item.room.id, uri) },
                            onAddPdf = { uri -> viewModel.addPdf(item.room.id, uri) },
                            onAddNote = { text -> viewModel.addNote(item.room.id, text) },
                            onDeleteAttachment = { viewModel.deleteAttachment(it) }
                        )
                    }
```

стає:

```kotlin
                    items(state.rooms, key = { it.room.id }) { item ->
                        val attachments by viewModel.observeAttachments(item.room.id)
                            .collectAsStateWithLifecycle(initialValue = emptyList())
                        RoomCard(
                            item = item,
                            attachments = attachments,
                            hazeState = hazeState,
                            onClick = { onRoomClick(item.room.id) },
                            onDelete = { viewModel.deleteRoom(item.room.id) },
                            onAddPhoto = { uri -> viewModel.addPhoto(item.room.id, uri) },
                            onAddPdf = { uri -> viewModel.addPdf(item.room.id, uri) },
                            onAddNote = { text -> viewModel.addNote(item.room.id, text) },
                            onDeleteAttachment = { viewModel.deleteAttachment(it) }
                        )
                    }
```

Сигнатура `private fun RoomCard(...)` (рядки 126-134), було:

```kotlin
private fun RoomCard(
    item: RoomUiItem,
    attachments: List<com.cubemaster.core.model.Attachment>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onAddPhoto: (android.net.Uri) -> Unit,
    onAddPdf: (android.net.Uri) -> Unit,
    onAddNote: (String) -> Unit,
    onDeleteAttachment: (com.cubemaster.core.model.Attachment) -> Unit
) {
```

стає:

```kotlin
private fun RoomCard(
    item: RoomUiItem,
    attachments: List<com.cubemaster.core.model.Attachment>,
    hazeState: HazeState?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onAddPhoto: (android.net.Uri) -> Unit,
    onAddPdf: (android.net.Uri) -> Unit,
    onAddNote: (String) -> Unit,
    onDeleteAttachment: (com.cubemaster.core.model.Attachment) -> Unit
) {
```

Рядок 136, було:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
```

стає:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState, onClick = onClick) {
```

- [ ] **Step 4: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/rooms/RoomsScreen.kt
git commit -m "feat: увімкнути справжній blur (Haze) на картках кімнат"
```

---

### Task 8: Haze у GeometryScreen (3 картки)

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/geometry/GeometryScreen.kt`

**Interfaces:**
- Consumes: те саме, що Task 6.
- Produces: `GeometryTab(state, viewModel, hazeState: HazeState?, onWallTap, onOpeningTap)`, `SurfacesTab(state, onLayersClick, viewModel, hazeState: HazeState?)`, `OpeningsTab(state, hazeState: HazeState?, onOpeningAdd, onOpeningEdit, onDelete)`.

- [ ] **Step 1: Додати імпорти**

Після рядка 28 (`import com.example.cubemaster.ui.theme.CubeMasterColors`) додати:

```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Створити `hazeState` і позначити фоновий контейнер**

Рядок 38 (`val state by viewModel.state.collectAsStateWithLifecycle()`), додати одразу після:

```kotlin
    val hazeState = rememberHazeState()
```

Рядки 72-80, було:

```kotlin
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
```

стає:

```kotlin
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .hazeSource(hazeState)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
```

- [ ] **Step 3: Передати `hazeState` у виклики вкладок**

Рядки 90-102, було:

```kotlin
            when (selectedTab) {
                0 -> GeometryTab(
                    state = state,
                    viewModel = viewModel,
                    onWallTap = { wallIndex, offsetMm -> openingDialogRequest = OpeningDialogRequest(wallIndex, offsetMm) },
                    onOpeningTap = onOpeningEdit
                )
                1 -> SurfacesTab(state, onLayersClick, viewModel)
                2 -> OpeningsTab(
                    state = state,
                    onOpeningAdd = { wallIndex -> openingDialogRequest = OpeningDialogRequest(wallIndex, 0) },
                    onOpeningEdit = onOpeningEdit,
                    onDelete = { viewModel.deleteOpening(it) }
                )
            }
```

стає:

```kotlin
            when (selectedTab) {
                0 -> GeometryTab(
                    state = state,
                    viewModel = viewModel,
                    hazeState = hazeState,
                    onWallTap = { wallIndex, offsetMm -> openingDialogRequest = OpeningDialogRequest(wallIndex, offsetMm) },
                    onOpeningTap = onOpeningEdit
                )
                1 -> SurfacesTab(state, onLayersClick, viewModel, hazeState)
                2 -> OpeningsTab(
                    state = state,
                    hazeState = hazeState,
                    onOpeningAdd = { wallIndex -> openingDialogRequest = OpeningDialogRequest(wallIndex, 0) },
                    onOpeningEdit = onOpeningEdit,
                    onDelete = { viewModel.deleteOpening(it) }
                )
            }
```

- [ ] **Step 4: `GeometryTab` — додати параметр і прокинути в картку**

Сигнатура (рядки 160-166), було:

```kotlin
@Composable
private fun GeometryTab(
    state: GeometryUiState,
    viewModel: GeometryViewModel,
    onWallTap: (wallIndex: Int, offsetMm: Int) -> Unit,
    onOpeningTap: (openingId: String) -> Unit
) {
```

стає:

```kotlin
@Composable
private fun GeometryTab(
    state: GeometryUiState,
    viewModel: GeometryViewModel,
    hazeState: HazeState?,
    onWallTap: (wallIndex: Int, offsetMm: Int) -> Unit,
    onOpeningTap: (openingId: String) -> Unit
) {
```

Рядок 251, було:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth()) {
```

стає:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
```

- [ ] **Step 5: `SurfacesTab` — додати параметр і прокинути в картку**

Сигнатура (рядок 297), було:

```kotlin
private fun SurfacesTab(state: GeometryUiState, onLayersClick: (String) -> Unit, viewModel: GeometryViewModel) {
```

стає:

```kotlin
private fun SurfacesTab(
    state: GeometryUiState,
    onLayersClick: (String) -> Unit,
    viewModel: GeometryViewModel,
    hazeState: HazeState?
) {
```

Рядки 299-302, було:

```kotlin
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onLayersClick(surface.id) }
        ) {
```

стає:

```kotlin
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState,
            onClick = { onLayersClick(surface.id) }
        ) {
```

- [ ] **Step 6: `OpeningsTab` — додати параметр і прокинути в картку**

Сигнатура (рядки 350-356), було:

```kotlin
@Composable
private fun OpeningsTab(
    state: GeometryUiState,
    onOpeningAdd: (Int) -> Unit,
    onOpeningEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
```

стає:

```kotlin
@Composable
private fun OpeningsTab(
    state: GeometryUiState,
    hazeState: HazeState?,
    onOpeningAdd: (Int) -> Unit,
    onOpeningEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
```

Рядок 361, було:

```kotlin
        GlassCard(modifier = Modifier.fillMaxWidth()) {
```

стає:

```kotlin
        GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
```

- [ ] **Step 7: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/geometry/GeometryScreen.kt
git commit -m "feat: увімкнути справжній blur (Haze) на картках екрана геометрії кімнати"
```

---

### Task 9: Haze у SummaryScreen + уніфікація картки шапки

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/summary/SummaryScreen.kt`

**Interfaces:**
- Consumes: те саме, що Task 6.
- Produces: `MaterialSummaryRow(line: SummaryMaterialLine, hazeState: HazeState?)`.

- [ ] **Step 1: Додати імпорти**

Після рядка 17 (`import com.example.cubemaster.ui.theme.CubeMasterColors`) додати:

```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Створити `hazeState` і позначити фоновий контейнер**

Рядок 26 (`val state by viewModel.state.collectAsStateWithLifecycle()`), додати одразу після:

```kotlin
    val hazeState = rememberHazeState()
```

Рядок 52, було:

```kotlin
        Column(modifier = Modifier.padding(padding)) {
```

стає:

```kotlin
        Column(modifier = Modifier.padding(padding).hazeSource(hazeState)) {
```

- [ ] **Step 3: Замінити шапку `Surface(surfaceVariant)` на `GlassCard`**

Рядки 53-72, було:

```kotlin
            // Шапка
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Загальна маса матеріалів:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${String.format("%.0f", state.totalMassKg)} кг",
                            style = MaterialTheme.typography.titleLarge,
                            color = CubeMasterColors.gold)
                    }
                    Text("${state.materialLines.size} позицій",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
```

стає:

```kotlin
            // Шапка
            GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp), hazeState = hazeState) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Загальна маса матеріалів:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${String.format("%.0f", state.totalMassKg)} кг",
                            style = MaterialTheme.typography.titleLarge,
                            color = CubeMasterColors.gold)
                    }
                    Text("${state.materialLines.size} позицій",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
```

- [ ] **Step 4: Передати `hazeState` у рядки матеріалів**

Рядок 80, було:

```kotlin
                items(state.materialLines, key = { it.sku }) { line ->
                    MaterialSummaryRow(line)
                }
```

стає:

```kotlin
                items(state.materialLines, key = { it.sku }) { line ->
                    MaterialSummaryRow(line, hazeState)
                }
```

Сигнатура (рядок 88-89), було:

```kotlin
@Composable
private fun MaterialSummaryRow(line: SummaryMaterialLine) {
```

стає:

```kotlin
@Composable
private fun MaterialSummaryRow(line: SummaryMaterialLine, hazeState: HazeState?) {
```

Рядок 90, було:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth()) {
```

стає:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
```

- [ ] **Step 5: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/summary/SummaryScreen.kt
git commit -m "feat: уніфікувати шапку зведення як GlassCard, увімкнути Haze-blur"
```

---

### Task 10: Збірка, встановлення і візуальна верифікація на пристрої

**Files:** немає змін коду — лише перевірка.

**Interfaces:** немає.

- [ ] **Step 1: Повна збірка і unit-тести**

Run: `./gradlew :cubemaster-core:test :app:testDebugUnitTest :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`, усі тести (включно з `CubeMasterColorsTest` з Task 2) зелені.

- [ ] **Step 2: Встановити на пристрій і зробити скріншоти чотирьох екранів у темній темі**

Використати skill `verify` (детальний сценарій там же): зібрати `installDebug`, `pm clear`, запустити застосунок, пройти шляхом Проєкти → відкрити проєкт (Кімнати) → відкрити кімнату (Геометрія/Поверхні/Прорізи) → Зведення. Зробити скріншот кожного екрана.

Перевірити на скріншотах:
- Картки — не плоский колір, видно легке розмиття/тонування (Haze), рамка й тінь на місці.
- Колір `gold` на сумах у Зведенні відрізняється від `red` акценту в топбарі/FAB.
- Суми в Зведенні (кг, позиції) вирівняні по розряду (моноширинні цифри) — порівняти з попереднім скріншотом із задачі верифікації (Task 1-2 цього проєкту, файли `04_project_created.png`, `07_room_created.png` тощо) за відчуттям, чи текст читається природно.
- `OrnamentalDivider` на порожньому екрані проєктів (`EmptyState`) — тонка лінія з акцентним сегментом, без ромбів.

- [ ] **Step 3: Перевірити світлу тему**

Run: `adb shell "cmd uimode night no"` (вимкнути темну тему на пристрої), зробити ще один прохід зі скріншотами тих самих екранів. Повернути тему назад: `adb shell "cmd uimode night yes"`.

- [ ] **Step 4: Очистити тестові дані**

Run: `adb shell pm clear com.example.cubemaster`

- [ ] **Step 5: Якщо все виглядає коректно — фінальний commit (якщо є незакомічені артефакти скріншотів поза репозиторієм — не комітити, вони йдуть у scratchpad)**

Якщо в процесі верифікації код не змінювався — комітити нічого не потрібно, задача суто перевірочна.

---

## Self-Review Notes

- **Спека vs Theme.kt:** `Theme.kt` не потребує жодних правок — усі кольори підключені через імена (`CubeMasterColors.graphite` тощо), тож нові hex-значення з Task 2 підхоплюються автоматично.
- **Спека vs CommonComponents.kt:** єдиний пункт спеки, що стосувався цього файлу (застосування `TabularNumberStyle`), не має відповідних викликів у `CommonComponents.kt` — там немає грошових/числових колонок. Жодних змін файл не потребує.
- **Покриття спеки:** секція 1 (кольори) → Task 2; секція 2 (GlassCard/Haze) → Tasks 1, 3; секція 3 (орнамент) → Task 4; секція 4 (типографіка) → Task 5; секція 5 (уніфікація карток) → Tasks 6-9.
