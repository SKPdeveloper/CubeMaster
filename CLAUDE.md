# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Команди розробки

```bash
# Збірка debug-версії
./gradlew assembleDebug

# Unit-тести cubemaster-core (геометрія, розрахунки)
./gradlew :cubemaster-core:test

# Unit-тести всіх модулів
./gradlew test

# Окремий тест
./gradlew :cubemaster-core:test --tests "com.cubemaster.core.GeometryEngineTest"

# Інструментальні тести (потрібен пристрій)
./gradlew connectedAndroidTest

# Cloud Functions
cd functions && npm run build
cd functions && npm run deploy
```

## Архітектура

```
:cubemaster-core      — чистий Kotlin-модуль (БЕЗ Android-залежностей)
                        geometry/, calculation/, catalog/, model/
:app                  — Android-застосунок
  data/local/         — Room DAO + Entity + AppDatabase
  data/remote/        — Firebase (AuthRepository, FirestoreRepository, StorageRepository)
  data/sync/          — SyncWorker (WorkManager офлайн→онлайн)
  domain/repository/  — Domain repositories (ProjectRepository, RoomRepository, ...)
  di/                 — Hilt (AppModule, DatabaseModule, FirebaseModule)
  ui/theme/           — CubeMasterTheme, CubeMasterColors, ManropeFontFamily
  ui/components/      — GlassCard, OrnamentalDivider, LayerStackIndicator, CommonComponents
  ui/navigation/      — AppNavigation + Screen sealed class
  presentation/       — ViewModel + Screen для кожного розділу
  messaging/          — CubeMasterFcmService
functions/            — Cloud Functions (TypeScript/Node.js)
  src/generatePdf.ts  — Puppeteer → PDF → Cloud Storage
  src/generateXlsx.ts — ExcelJS → XLSX → Cloud Storage
  src/refreshPrices.ts— Парсинг зовнішніх цін (rate-limited)
firestore.rules       — Правила безпеки Firestore
```

## Налаштування Firebase (необхідно перед збіркою)

1. Створити Firebase-проєкт на console.firebase.google.com
2. Додати Android-застосунок з package `com.example.cubemaster`
3. Завантажити `google-services.json` → `app/google-services.json`
4. Увімкнути: Authentication, Firestore, Storage, Functions, Remote Config, FCM, Crashlytics

## Шрифти (необхідно додати вручну)

Покласти Manrope-файли у `app/src/main/res/font/`:
- `manrope_regular.ttf`
- `manrope_medium.ttf`
- `manrope_semibold.ttf`
- `manrope_bold.ttf`
- `manrope_extrabold.ttf`

Завантажити з fonts.google.com/specimen/Manrope

## Ключові правила домену

- Всі норми витрати матеріалів прив'язані до ДБН/ДСТУ — значення в `MaterialDefaults.kt`
- Ґрунтовка додається **автоматично** при переходах між шарами (функція `requiresPrimer`)
- Гідроізоляція у ванній/балконі → **блокуючий діалог** (не мовчазний дозвіл)
- Демонтаж залізобетону → обов'язкове попередження про ДБН В.1.2-14:2018
- Нев'язка контуру > 10 см → збереження заблоковано; 2–10 см → автокорекція

## Мова і локалізація

- Інтерфейс, помилки, підказки — **повністю українською**
- Коментарі в коді — **українською**
- Ідентифікатори (класи, змінні, поля Firestore) — **англійською**
- Locale: `java.util.Locale("uk", "UA")`; десятковий роздільник у UI — кома

## Firestore-схема

```
/users/{uid}/projects/{projectId}
/users/{uid}/projects/{projectId}/rooms/{roomId}
/users/{uid}/projects/{projectId}/rooms/{roomId}/surfaces/{surfaceId}   ← layers як JSON-масив
/users/{uid}/projects/{projectId}/rooms/{roomId}/openings/{openingId}
/users/{uid}/projects/{projectId}/rooms/{roomId}/demolitionTasks/{taskId}
/users/{uid}/projects/{projectId}/estimates/{estimateId}
/materialCatalog/{sku}     ← shared read-only
/priceEntries/{entryId}    ← shared read-only (manual → users/{uid}/manualPrices/)
```

## Дизайн-система

- Material 3 — тільки як інфраструктура (theming, accessibility), не візуально
- Глазморфізм: `GlassCard` з `glassSurfaceLight/Dark` + `Modifier.blur` на Android 12+
- Орнамент (`OrnamentalDivider`): зигзаг-ромби — ніколи у зонах числових даних
- Темна тема — пріоритетна (OLED, робота на об'єкті)
- Tabular figures: клас `TabularNumberStyle` для числових колонок кошторису

## Структура Firestore Security Rules

Файл `firestore.rules` у корені репозиторію. Клієнт читає/пише тільки власні дані (uid == userId). `materialCatalog` і `priceEntries` — write заборонено з клієнта (тільки Cloud Functions).
