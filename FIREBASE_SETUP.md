# Налаштування Firebase — покрокова інструкція

Цей документ пояснює, як довести хмарну частину CubeMaster (Firestore, Storage, Cloud Functions) до робочого стану. Розрахований на людину, яка ніколи раніше не працювала з Firebase CLI — кожна команда пояснена окремо, нічого не пропущено.

## Чому це взагалі потрібно

Під час тестування виявилось дві проблеми:

1. **Завантаження фото у вкладеннях не працює** — застосунок показує помилку `Object does not exist at location.`
2. **Генерація PDF/XLSX кошторису не відповідає** — кнопка натискається, але нічого не відбувається.

Причина обох проблем одна: у репозиторії **немає файлів `firebase.json` і `.firebaserc`**. Це означає, що інструмент командного рядка Firebase (Firebase CLI) у цьому проєкті ще жодного разу не запускали. Без нього неможливо:
- задеплоїти правила безпеки для Cloud Storage (тому фото "нема куди" завантажувати — сховище існує, але не налаштоване приймати файли від застосунку);
- задеплоїти сам код Cloud Functions (`functions/src/*.ts`) — він лежить у репозиторії, але на серверах Firebase його ще ніколи не було, тому дзвінок з застосунку просто ні до чого не доходить.

Firestore (список проєктів/кімнат) при цьому працює, бо для найпростіших сценаріїв Firebase Console сам створює тимчасові правила при створенні бази даних — а от Storage і Functions без ручного деплою не запрацюють ніколи.

## Крок 0. Що вже є, а що потрібно встановити

| Компонент | Статус на цьому комп'ютері |
|---|---|
| Node.js | Встановлено (v24) — можна перевірити командою `node --version` |
| Firebase CLI | Встановлено (v15.22.4) — можна перевірити командою `firebase --version` |
| Вхід в акаунт Google (`firebase login`) | **Не виконано** |
| `firebase.json` / `.firebaserc` у репозиторії | **Відсутні** — це і є корінь проблеми |

Якщо на іншому комп'ютері `firebase --version` видає помилку "команда не знайдена" — спочатку встановіть Node.js з [nodejs.org](https://nodejs.org) (LTS-версія), а потім виконайте `npm install -g firebase-tools`.

## Крок 1. Увійти в акаунт Firebase

Це єдиний крок, який неможливо виконати автоматично — він відкриває браузер для входу в Google-акаунт, до якого прив'язаний Firebase-проєкт `cubemaster-9d566`.

```bash
firebase login
```

**Що станеться:** відкриється браузер, попросить обрати Google-акаунт і підтвердити доступ. Після підтвердження термінал напише `Success! Logged in as ...`.

**Якщо браузер не відкривається автоматично** (наприклад, ви працюєте через SSH або в контейнері) — використайте:
```bash
firebase login --no-localhost
```
Тоді CLI видасть посилання, яке треба відкрити вручну на будь-якому пристрої з браузером, і попросить вставити код підтвердження назад у термінал.

## Крок 2. Підключити репозиторій до існуючого Firebase-проєкту

Виконується один раз, у корені репозиторію (`W:\CubeMaster`):

```bash
firebase init
```

CLI задасть кілька питань — ось що відповідати:

1. **"Which Firebase features do you want to set up?"** — стрілками вгору/вниз і пробілом обрати три пункти: `Firestore`, `Functions`, `Storage`. Потім Enter.
2. **"Please select an option"** (для проєкту) — обрати `Use an existing project`, потім у списку знайти **`cubemaster-9d566`** (це саме той проєкт, до якого вже прив'язаний `google-services.json` у застосунку).
3. **Firestore: What file should be used for Firestore Rules?** — залишити за замовчуванням `firestore.rules` (файл вже існує в репозиторії, CLI запитає "перезаписати?" — відповісти **No**, щоб не втратити наявні правила).
4. **Firestore: What file should be used for Firestore indexes?** — залишити за замовчуванням `firestore.indexes.json`.
5. **Functions: What language?** — обрати `TypeScript` (код у `functions/src/` вже написаний на TypeScript).
6. **Functions: Do you want to use ESLint?** — на ваш розсуд, не критично.
7. **Functions: Overwrite existing files?** (package.json, tsconfig.json, index.ts) — відповісти **No** на кожен, у `functions/` вже є робочий код, перезаписувати не треба.
8. **Storage: What file should be used for Storage Rules?** — залишити за замовчуванням `storage.rules`.

Після цього кроку в корені репозиторію з'являться два нові файли: `firebase.json` і `.firebaserc`. Саме їх бракувало.

## Крок 3. Правила безпеки для Cloud Storage

Крок 2 створить файл `storage.rules` із правилами-заглушками (типово — повна заборона). Замініть його вміст на:

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{userId}/{allPaths=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

**Що це означає:** будь-який файл під шляхом `users/<uid>/...` може читати й записувати тільки той користувач, чий `uid` збігається з частиною шляху. Це той самий принцип, що вже використовується у `firestore.rules` — кожен бачить лише свої дані.

## Крок 4. Тарифний план — важливий момент

Firebase Storage і Cloud Functions **не працюють на безкоштовному плані Spark** для нових проєктів — Google вимагає підключити план **Blaze** (оплата за фактичне використання).

| | Spark (безкоштовний) | Blaze (pay-as-you-go) |
|---|---|---|
| Firestore | Так, з лімітами | Так |
| Cloud Storage | **Ні** (для нових проєктів) | Так |
| Cloud Functions з доступом до інтернету (потрібно для Puppeteer у `generatePdf.ts`) | Ні | Так |
| Оплата | 0 грн, завжди | Тільки за перевищення безкоштовної квоти — для одного прораба з невеликою кількістю кошторисів на місяць це, як правило, **0 грн або кілька центів** |

Підключити: Firebase Console → оберіть проєкт `cubemaster-9d566` → внизу лівого меню "Upgrade" → обрати Blaze → прив'язати банківську картку. Це відкриває сторінка `console.firebase.google.com/project/cubemaster-9d566/usage/details`.

Без цього кроку `firebase deploy` для Storage і Functions видасть помилку з посиланням на сторінку підключення білінгу — це очікувано, не баг.

## Крок 5. Задеплоїти все

```bash
firebase deploy --only firestore:rules,storage,functions
```

**Що робить кожна частина:**
- `firestore:rules` — оновлює правила Firestore (у вас вони вже правильні, але варто задеплоїти про всяк випадок, якщо в консолі досі лежать тимчасові).
- `storage` — завантажує `storage.rules` з кроку 3. Після цього фото у вкладеннях почнуть зберігатися.
- `functions` — компілює TypeScript (`npm run build` виконається автоматично) і завантажує три функції: `generateEstimatePdf`, `generateEstimateXlsx`, `refreshExternalPrices`. Перший деплой триває довше (2–5 хвилин), бо збирається окреме середовище Node.js на сервері.

**Можливі помилки:**
- `Error: Your project must be on the Blaze (pay-as-you-go) plan` — не виконано Крок 4.
- `Error: functions predeploy error: Command terminated with non-zero exit code` — зазвичай означає помилку компіляції TypeScript; виконайте `cd functions && npm install && npm run build` окремо і подивіться повний текст помилки.

## Крок 6. Перевірка

1. Firebase Console → Storage — має з'явитись бакет `cubemaster-9d566.firebasestorage.app` з правилами з кроку 3.
2. Firebase Console → Functions — мають з'явитись три функції зі статусом "Active" (зелений).
3. У застосунку: додати фото у вкладення кімнати — тепер має завантажитись без помилки `Object does not exist at location`.
4. У застосунку: кошторис → кнопка PDF — має відкритись згенерований файл (перший виклик після деплою може тривати 10–30 секунд через "холодний старт" функції, це нормально).

## Окремо: реєстрація нового package name для Google Play

Це не пов'язано з проблемами вище, але теж потребує ручних дій у Firebase Console (не з коду):

1. Firebase Console → Project settings (шестерня зверху ліворуч) → вкладка "General" → розділ "Your apps".
2. "Add app" → Android.
3. Package name: **`com.skpfreelance.cubemaster`** (узгоджений новий ідентифікатор замість `com.example.cubemaster`).
4. Завантажити новий `google-services.json` і замінити ним поточний файл `app/google-services.json`.
5. Після цього — сигнал мені, що файл оновлено, і я перейменую `applicationId`/пакети в коді одним комітом.
