# Premium Glassmorphism 2.0 — Remaining Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Поширити вже затверджений і реалізований паттерн Haze-blur (`docs/superpowers/plans/2026-07-09-premium-glassmorphism-2.md`, злитий в master) на решту 7 екранів застосунку (з 8 обстежених — `ObjectPlanScreen` не має жодної `GlassCard`, тож не потребує змін), і уніфікувати мову картки там, де досі лишився `Surface(color = ...surfaceVariant...)`.

**Architecture:** Той самий встановлений паттерн: `val hazeState = rememberHazeState()` на екрані, `Modifier.hazeSource(hazeState)` на фоновому контейнері, `hazeState = hazeState` у кожен виклик `GlassCard`. Ніяких нових архітектурних рішень — це механічне застосування вже затвердженого дизайну (design review не потрібен, дизайн-рішення вже ухвалені й перевірені на 4 екранах).

**Tech Stack:** Kotlin, Jetpack Compose, Haze 1.7.2 (вже підключено в Task 1 попереднього плану).

## Global Constraints

- `GlassCard(hazeState: HazeState? = null, ...)` — параметр вже існує (не змінюється), задачі лише додають `hazeState = hazeState` у виклики.
- `Surface(color = MaterialTheme.colorScheme.surfaceVariant)` та `Surface(color = ...primaryContainer...)` — конвертуються в `GlassCard`, якщо це картка з даними/підсумком (за прецедентом шапки `SummaryScreen`). `InfoCard`/`WarningCard` — НЕ конвертувати, вони навмисно суцільні (сигнал попередження/інформації).
- Топбар, `AlertDialog`, `ExposedDropdownMenu` — не торкатись, лишаються суцільними.
- `./gradlew :app:compileDebugKotlin --console=plain` має проходити після кожної задачі.
- `ObjectPlanScreen.kt` — поза обсягом цього плану (немає жодної `GlassCard`, застосування паттерна не дає видимого ефекту).

---

### Task 1: Haze у ProfileScreen

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: `GlassCard(hazeState = ...)`, `dev.chrisbanes.haze.{rememberHazeState, hazeSource, HazeState}`.

- [ ] **Step 1: Додати імпорти**

Після рядка 19 (`import com.example.cubemaster.ui.theme.CubeMasterColors`) додати:

```kotlin
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Створити `hazeState` і позначити фоновий контейнер**

Рядок 26 (`val state by viewModel.state.collectAsStateWithLifecycle()`), додати одразу після:

```kotlin
    val hazeState = rememberHazeState()
```

Рядки 41-48, було:

```kotlin
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
```

- [ ] **Step 3: Передати `hazeState` у картку**

Рядок 50, було:

```kotlin
            GlassCard(modifier = Modifier.fillMaxWidth()) {
```

стає:

```kotlin
            GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
```

- [ ] **Step 4: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/profile/ProfileScreen.kt
git commit -m "feat: увімкнути справжній blur (Haze) на екрані профілю"
```

---

### Task 2: Haze у ProjectDocumentsScreen

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/documents/ProjectDocumentsScreen.kt`

**Interfaces:**
- Consumes: те саме, що Task 1.

- [ ] **Step 1: Додати імпорти**

Після рядка 17 (`import com.example.cubemaster.ui.components.InfoCard`) додати:

```kotlin
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Створити `hazeState` і позначити фоновий контейнер**

Рядок 24 (`val documents by viewModel.documents.collectAsStateWithLifecycle(initialValue = emptyList())`), додати одразу після:

```kotlin
    val hazeState = rememberHazeState()
```

Рядки 39-42, було:

```kotlin
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
```

стає:

```kotlin
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().hazeSource(hazeState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
```

- [ ] **Step 3: Передати `hazeState` у картку**

Рядок 44, було:

```kotlin
            GlassCard(modifier = Modifier.fillMaxWidth()) {
```

стає:

```kotlin
            GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
```

- [ ] **Step 4: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/documents/ProjectDocumentsScreen.kt
git commit -m "feat: увімкнути справжній blur (Haze) на екрані документів проєкту"
```

---

### Task 3: Haze у CatalogScreen

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/catalog/CatalogScreen.kt`

**Interfaces:**
- Consumes: те саме, що Task 1.
- Produces: `MaterialEntryCard(entry, isExpanded, onToggle, latestPrice, onSetPrice, hazeState: HazeState?)`.

- [ ] **Step 1: Додати імпорти**

Після рядка 18 (`import com.example.cubemaster.ui.theme.CubeMasterColors`) додати:

```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Створити `hazeState` і позначити фоновий контейнер**

Рядок 25 (`val state by viewModel.state.collectAsStateWithLifecycle()`), додати одразу після:

```kotlin
    val hazeState = rememberHazeState()
```

Рядок 42, було:

```kotlin
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
```

стає:

```kotlin
        Column(modifier = Modifier.padding(padding).fillMaxSize().hazeSource(hazeState).imePadding()) {
```

- [ ] **Step 3: Передати `hazeState` у картку**

Рядки 68-77, було:

```kotlin
                    items(state.entries, key = { it.sku }) { entry ->
                        MaterialEntryCard(
                            entry = entry,
                            isExpanded = expandedItem == entry.sku,
                            onToggle = {
                                expandedItem = if (expandedItem == entry.sku) null else entry.sku
                            },
                            latestPrice = state.prices[entry.sku],
                            onSetPrice = { price -> viewModel.setManualPrice(entry.sku, price) }
                        )
                    }
```

стає:

```kotlin
                    items(state.entries, key = { it.sku }) { entry ->
                        MaterialEntryCard(
                            entry = entry,
                            isExpanded = expandedItem == entry.sku,
                            onToggle = {
                                expandedItem = if (expandedItem == entry.sku) null else entry.sku
                            },
                            latestPrice = state.prices[entry.sku],
                            onSetPrice = { price -> viewModel.setManualPrice(entry.sku, price) },
                            hazeState = hazeState
                        )
                    }
```

Сигнатура `private fun MaterialEntryCard(...)` (рядки 104-110), було:

```kotlin
private fun MaterialEntryCard(
    entry: MaterialCatalogEntry,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    latestPrice: Double?,
    onSetPrice: (Double) -> Unit
) {
```

стає:

```kotlin
private fun MaterialEntryCard(
    entry: MaterialCatalogEntry,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    latestPrice: Double?,
    onSetPrice: (Double) -> Unit,
    hazeState: HazeState?
) {
```

Рядок 115, було:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onToggle) {
```

стає:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState, onClick = onToggle) {
```

- [ ] **Step 4: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/catalog/CatalogScreen.kt
git commit -m "feat: увімкнути справжній blur (Haze) на картках каталогу матеріалів"
```

---

### Task 4: Haze у HelpScreen

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/help/HelpScreen.kt`

**Interfaces:**
- Consumes: те саме, що Task 1.
- Produces: `HelpSectionCard(section: HelpSection, hazeState: HazeState?)`.

- [ ] **Step 1: Додати імпорти**

На початку файлу, у блоці імпортів (перед першим `@Composable`), додати:

```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

Якщо у файлі вже є `import com.example.cubemaster.ui.components.*` — додати рядки одразу після нього; інакше — після останнього `import androidx...` рядка перед оголошенням `helpSections`/першого `@Composable`.

- [ ] **Step 2: Створити `hazeState` і позначити `LazyColumn` як фоновий контейнер**

Рядки 194-201, було:

```kotlin
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { CubeMasterTopBar(title = "Довідка", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
```

стає:

```kotlin
fun HelpScreen(onBack: () -> Unit) {
    val hazeState = rememberHazeState()
    Scaffold(
        topBar = { CubeMasterTopBar(title = "Довідка", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().hazeSource(hazeState),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
```

- [ ] **Step 3: Передати `hazeState` у картку**

Рядки 211-213, було:

```kotlin
            items(helpSections) { section ->
                HelpSectionCard(section)
            }
```

стає:

```kotlin
            items(helpSections) { section ->
                HelpSectionCard(section, hazeState)
            }
```

Сигнатура `private fun HelpSectionCard(...)` (рядок 219), було:

```kotlin
private fun HelpSectionCard(section: HelpSection) {
```

стає:

```kotlin
private fun HelpSectionCard(section: HelpSection, hazeState: HazeState?) {
```

Рядки 222-225, було:

```kotlin
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
```

стає:

```kotlin
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        onClick = { expanded = !expanded }
    ) {
```

- [ ] **Step 4: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/help/HelpScreen.kt
git commit -m "feat: увімкнути справжній blur (Haze) на картках довідки"
```

---

### Task 5: Haze у DemolitionScreen (2 картки)

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/demolition/DemolitionScreen.kt`

**Interfaces:**
- Consumes: те саме, що Task 1.
- Produces: `DemolitionTaskRow(task: DemolitionTask, hazeState: HazeState?, onDelete: () -> Unit)`.

- [ ] **Step 1: Додати імпорти**

Після рядка 22 (`import com.example.cubemaster.ui.theme.CubeMasterColors`) додати:

```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Створити `hazeState` і позначити фоновий контейнер**

Рядок 30 (`val state by viewModel.state.collectAsStateWithLifecycle()`), додати одразу після:

```kotlin
    val hazeState = rememberHazeState()
```

Рядки 50-58, було:

```kotlin
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
```

- [ ] **Step 3: Передати `hazeState` в інлайн-картку "Зведення демонтажу"**

Рядок 86, було:

```kotlin
            GlassCard(modifier = Modifier.fillMaxWidth()) {
```

стає:

```kotlin
            GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
```

- [ ] **Step 4: Передати `hazeState` у `DemolitionTaskRow`**

Рядки 101-103, було:

```kotlin
                state.tasks.forEach { task ->
                    DemolitionTaskRow(task = task, onDelete = { viewModel.deleteTask(task.id) })
                }
```

стає:

```kotlin
                state.tasks.forEach { task ->
                    DemolitionTaskRow(task = task, hazeState = hazeState, onDelete = { viewModel.deleteTask(task.id) })
                }
```

Сигнатура `private fun DemolitionTaskRow(...)` (рядок 451), було:

```kotlin
private fun DemolitionTaskRow(task: DemolitionTask, onDelete: () -> Unit) {
```

стає:

```kotlin
private fun DemolitionTaskRow(task: DemolitionTask, hazeState: HazeState?, onDelete: () -> Unit) {
```

Рядок 452, було:

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
git add app/src/main/java/com/example/cubemaster/presentation/demolition/DemolitionScreen.kt
git commit -m "feat: увімкнути справжній blur (Haze) на екрані демонтажу"
```

---

### Task 6: Haze у LayersScreen + уніфікація двох карток-шапок

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/layers/LayersScreen.kt`

**Interfaces:**
- Consumes: те саме, що Task 1.
- Produces: `LayerCard(item, onMoveUp, onMoveDown, onDelete, onEditThickness, onTogglePorous, onToggleDiagonal, hazeState: HazeState?)`, `SummaryFooter(state: LayersUiState, hazeState: HazeState?)`.

- [ ] **Step 1: Додати імпорти**

У блоці імпортів (після останнього `import com.example.cubemaster...` рядка) додати:

```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Створити `hazeState` і позначити фоновий контейнер**

Одразу після рядка, де читається `val state by viewModel.state.collectAsStateWithLifecycle()` (на початку композиції екрана), додати:

```kotlin
    val hazeState = rememberHazeState()
```

Рядок 65, було:

```kotlin
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
```

стає:

```kotlin
        Column(modifier = Modifier.padding(padding).fillMaxSize().hazeSource(hazeState)) {
```

- [ ] **Step 3: Конвертувати заголовок "Площа" з `Surface` у `GlassCard`**

Рядки 68-83, було:

```kotlin
            // Заголовок поверхні
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Площа: ${String.format("%.2f", state.surfaceAreaM2)} м²",
                        style = MaterialTheme.typography.titleSmall)
                    if (state.surface != null) {
                        LayerStackIndicator(
                            layers = state.surface!!.layers,
                            modifier = Modifier.width(120.dp).height(10.dp)
                        )
                    }
                }
            }
```

стає:

```kotlin
            // Заголовок поверхні
            GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Площа: ${String.format("%.2f", state.surfaceAreaM2)} м²",
                        style = MaterialTheme.typography.titleSmall)
                    if (state.surface != null) {
                        LayerStackIndicator(
                            layers = state.surface!!.layers,
                            modifier = Modifier.width(120.dp).height(10.dp)
                        )
                    }
                }
            }
```

- [ ] **Step 4: Передати `hazeState` у `LayerCard`**

Рядки 105-114, було:

```kotlin
                    items(state.calculatedLayers, key = { it.layer.id }) { item ->
                        LayerCard(
                            item = item,
                            onMoveUp = { viewModel.moveLayerUp(item.layer.id) },
                            onMoveDown = { viewModel.moveLayerDown(item.layer.id) },
                            onDelete = { viewModel.removeLayer(item.layer.id) },
                            onEditThickness = { layerToEditThickness = item.layer.id to (item.layer.thicknessMm?.toString() ?: "") },
                            onTogglePorous = { viewModel.setLayerPorous(item.layer.id, it) },
                            onToggleDiagonal = { viewModel.setLayerDiagonal(item.layer.id, it) }
                        )
                    }
```

стає:

```kotlin
                    items(state.calculatedLayers, key = { it.layer.id }) { item ->
                        LayerCard(
                            item = item,
                            onMoveUp = { viewModel.moveLayerUp(item.layer.id) },
                            onMoveDown = { viewModel.moveLayerDown(item.layer.id) },
                            onDelete = { viewModel.removeLayer(item.layer.id) },
                            onEditThickness = { layerToEditThickness = item.layer.id to (item.layer.thicknessMm?.toString() ?: "") },
                            onTogglePorous = { viewModel.setLayerPorous(item.layer.id, it) },
                            onToggleDiagonal = { viewModel.setLayerDiagonal(item.layer.id, it) },
                            hazeState = hazeState
                        )
                    }
```

Сигнатура `private fun LayerCard(...)` (рядки 178-186), було:

```kotlin
private fun LayerCard(
    item: LayerCalculatedItem,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onEditThickness: () -> Unit,
    onTogglePorous: (Boolean) -> Unit,
    onToggleDiagonal: (Boolean) -> Unit
) {
```

стає:

```kotlin
private fun LayerCard(
    item: LayerCalculatedItem,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onEditThickness: () -> Unit,
    onTogglePorous: (Boolean) -> Unit,
    onToggleDiagonal: (Boolean) -> Unit,
    hazeState: HazeState?
) {
```

Рядок 187, було:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth()) {
```

стає:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
```

- [ ] **Step 5: Конвертувати `SummaryFooter` з `Surface` у `GlassCard` і передати `hazeState`**

Рядок 121, було:

```kotlin
                SummaryFooter(state)
```

стає:

```kotlin
                SummaryFooter(state, hazeState)
```

Сигнатура і тіло `private fun SummaryFooter(...)` (рядки 264-271), було:

```kotlin
private fun SummaryFooter(state: LayersUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        val totalKg = state.calculatedLayers.sumOf { it.result.mixMassKg }
        Column(modifier = Modifier.padding(16.dp)) {
```

стає:

```kotlin
private fun SummaryFooter(state: LayersUiState, hazeState: HazeState?) {
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
        val totalKg = state.calculatedLayers.sumOf { it.result.mixMassKg }
        Column(modifier = Modifier.padding(16.dp)) {
```

(Закриваюча дужка `Surface { ... }` наприкінці функції лишається на місці — синтаксично вона тепер закриває `GlassCard { ... }`, зміна лише в оголошенні, не в структурі блоку.)

- [ ] **Step 6: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/layers/LayersScreen.kt
git commit -m "feat: увімкнути Haze-blur і уніфікувати картки-шапки на екрані шарів"
```

---

### Task 7: Haze у EstimateScreen + уніфікація двох карток-шапок

**Files:**
- Modify: `app/src/main/java/com/example/cubemaster/presentation/estimate/EstimateScreen.kt`

**Interfaces:**
- Consumes: те саме, що Task 1.
- Produces: `EstimateLineRow(line, markup, onToggleMarkup, onDelete, hazeState: HazeState?)`.

- [ ] **Step 1: Додати імпорти**

Після рядка 26 (`import com.example.cubemaster.ui.theme.CubeMasterColors`) додати:

```kotlin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Створити `hazeState` і позначити фоновий контейнер**

Рядок 34 (`val state by viewModel.state.collectAsStateWithLifecycle()`), додати одразу після:

```kotlin
    val hazeState = rememberHazeState()
```

Рядок 75, було:

```kotlin
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
```

стає:

```kotlin
        Column(modifier = Modifier.padding(padding).fillMaxSize().hazeSource(hazeState).imePadding()) {
```

- [ ] **Step 3: Конвертувати блок "Прорабська націнка" з `Surface` у `GlassCard`**

Рядки 77-95, було:

```kotlin
            // Поле націнки
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Прорабська націнка:", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberInputField(
                            value = state.markupPercent.toInt().toString(),
                            onValueChange = { v -> v.replace(",", ".").toDoubleOrNull()?.let { viewModel.updateMarkup(it) } },
                            label = "",
                            unit = "%",
                            modifier = Modifier.width(80.dp)
                        )
                    }
                }
            }
```

стає:

```kotlin
            // Поле націнки
            GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Прорабська націнка:", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberInputField(
                            value = state.markupPercent.toInt().toString(),
                            onValueChange = { v -> v.replace(",", ".").toDoubleOrNull()?.let { viewModel.updateMarkup(it) } },
                            label = "",
                            unit = "%",
                            modifier = Modifier.width(80.dp)
                        )
                    }
                }
            }
```

- [ ] **Step 4: Конвертувати блок "Загальна сума" з `Surface` у `GlassCard`**

Рядки 97-110, було:

```kotlin
            // Підсумок
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Загальна сума:", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${state.grandTotal.toUaStringRound()} грн",
                        style = MaterialTheme.typography.titleLarge,
                        color = CubeMasterColors.gold
                    )
                }
            }
```

стає:

```kotlin
            // Підсумок
            GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Загальна сума:", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${state.grandTotal.toUaStringRound()} грн",
                        style = MaterialTheme.typography.titleLarge,
                        color = CubeMasterColors.gold
                    )
                }
            }
```

- [ ] **Step 5: Передати `hazeState` у `EstimateLineRow` (обидва виклики — Матеріали і Роботи)**

Рядки 128-135, було:

```kotlin
                        items(materials, key = { it.id }) { line ->
                            EstimateLineRow(
                                line = line,
                                markup = state.markupPercent,
                                onToggleMarkup = { viewModel.toggleMarkup(line.id) },
                                onDelete = { viewModel.removeLine(line.id) }
                            )
                        }
```

стає:

```kotlin
                        items(materials, key = { it.id }) { line ->
                            EstimateLineRow(
                                line = line,
                                markup = state.markupPercent,
                                onToggleMarkup = { viewModel.toggleMarkup(line.id) },
                                onDelete = { viewModel.removeLine(line.id) },
                                hazeState = hazeState
                            )
                        }
```

Рядки 140-147, було:

```kotlin
                        items(labor, key = { it.id }) { line ->
                            EstimateLineRow(
                                line = line,
                                markup = state.markupPercent,
                                onToggleMarkup = { viewModel.toggleMarkup(line.id) },
                                onDelete = { viewModel.removeLine(line.id) }
                            )
                        }
```

стає:

```kotlin
                        items(labor, key = { it.id }) { line ->
                            EstimateLineRow(
                                line = line,
                                markup = state.markupPercent,
                                onToggleMarkup = { viewModel.toggleMarkup(line.id) },
                                onDelete = { viewModel.removeLine(line.id) },
                                hazeState = hazeState
                            )
                        }
```

Сигнатура `private fun EstimateLineRow(...)` (рядки 193-198), було:

```kotlin
private fun EstimateLineRow(
    line: EstimateLineUi,
    markup: Double,
    onToggleMarkup: () -> Unit,
    onDelete: () -> Unit
) {
```

стає:

```kotlin
private fun EstimateLineRow(
    line: EstimateLineUi,
    markup: Double,
    onToggleMarkup: () -> Unit,
    onDelete: () -> Unit,
    hazeState: HazeState?
) {
```

Рядок 202, було:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth()) {
```

стає:

```kotlin
    GlassCard(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
```

- [ ] **Step 6: Перевірити компіляцію**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/cubemaster/presentation/estimate/EstimateScreen.kt
git commit -m "feat: увімкнути Haze-blur і уніфікувати картки-шапки на екрані кошторису"
```

---

### Task 8: Збірка, встановлення і візуальна верифікація на пристрої

**Files:** немає змін коду — лише перевірка.

- [ ] **Step 1: Повна збірка і unit-тести**

Run: `./gradlew :cubemaster-core:test :app:testDebugUnitTest :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Встановити на пристрій і пройти всі 7 змінених екранів**

Skill `verify`: `installDebug`, `pm clear`, запустити застосунок. Пройти: Каталог матеріалів (розгорнути картку), Профіль, Документи проєкту, Довідка (розгорнути секцію), Демонтаж (додати одне завдання), Шари (відкрити поверхню кімнати), Кошторис (відкрити естимейт проєкту). Скріншот кожного.

Перевірити: картки не виглядають як плоский суцільний колір колишніх `Surface(surfaceVariant)` місць (Демонтаж-зведення вже було GlassCard — звірити, що Шари/Кошторис тепер виглядають так само), немає регресій у функціоналі (розгортання акордеонів, зміна націнки, видалення рядків).

- [ ] **Step 3: Перевірити світлу тему**

`adb shell "cmd uimode night no"`, повторити скріншоти ключових екранів (Кошторис, Шари — де відбулась конвертація Surface→GlassCard). Повернути тему: `adb shell "cmd uimode night yes"` (чи `no`, залежно від того, яка була дефолтною до перевірки).

- [ ] **Step 4: Очистити тестові дані**

`adb shell pm clear com.example.cubemaster`

## Self-Review Notes

- **`ObjectPlanScreen.kt`** — свідомо поза обсягом: немає жодної `GlassCard`, весь екран — canvas-редактор плану без карток даних. Застосування `hazeSource` без жодної картки, що його споживає, не дає видимого ефекту.
- **`InfoCard`/`WarningCard`** — жодного разу не конвертуються в `GlassCard` в жодній задачі: це навмисно суцільні попереджувальні/інформаційні блоки (той самий принцип, що і в попередньому плані для areaWarning/openingWarning).
- **`ExposedDropdownMenu` (LayersScreen, EstimateScreen)** — не картки, не в обсязі.
- Покриття: 7 з 8 обстежених екранів отримують `hazeState`; 4 конвертації `Surface`→`GlassCard` (LayersScreen ×2, EstimateScreen ×2).
