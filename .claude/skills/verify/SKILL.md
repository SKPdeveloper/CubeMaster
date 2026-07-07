---
name: verify
description: Зібрати, встановити й перевірити CubeMaster на реальному Android-пристрої/емуляторі через adb — надійно, без вгадування піксельних координат.
---

# Перевірка CubeMaster на пристрої

Цей файл — напрацьований сценарій ручного тестування застосунку через adb.
Створений після сесії, де вгадування координат для `adb shell input tap`
постійно ламалося через появу клавіатури й зсув layout. Головне правило:
**ніколи не вгадуй координати наосліп — знаходь елемент за текстом.**

## 0. Швидкий чек-лист (typical loop)

```bash
./gradlew :cubemaster-core:test :app:compileDebugKotlin --console=plain   # швидка перевірка коду
./gradlew :app:installDebug --console=plain                              # тільки коли компіляція зелена
adb shell pm clear com.example.cubemaster                                 # чистий стан перед сценарієм
adb shell am start -n com.example.cubemaster/.MainActivity
sleep 4   # анонімний вхід + перше створення Room DB — не поспішай зі скріншотом/force-stop раніше
```

Обидва gradle-виклики (`test` + `compileDebugKotlin`) варто пускати одним
викликом у фоні (`run_in_background: true`) — компіляція `:app` однаково
чекає модуль `:cubemaster-core`, тож паралельний запуск нічого не виграє,
а два окремі виклики просто дублюють Gradle-конфігурацію.

## 1. Головний інструмент: `tap_by_text.py`

`W:\CubeMaster\.claude\skills\verify\tap_by_text.py` — тапає по елементу
UI, знайденому через `uiautomator dump`, за його `text`, `content-desc` або
`resource-id`. Це усуває 90% проблем ручного тестування цієї сесії
(тап "в порожнечу" через появу клавіатури, зсув після скролу тощо).

```bash
python "W:/CubeMaster/.claude/skills/verify/tap_by_text.py" "Створити"                # точний збіг
python "W:/CubeMaster/.claude/skills/verify/tap_by_text.py" "Кімнат" --contains        # частковий збіг
python "W:/CubeMaster/.claude/skills/verify/tap_by_text.py" "Add" --contains --index 1 # другий збіг (0 — перший)
python "W:/CubeMaster/.claude/skills/verify/tap_by_text.py" x --list                   # усі text/content-desc з bounds на екрані (для розвідки)
python "W:/CubeMaster/.claude/skills/verify/tap_by_text.py" x --dump-only              # сирий XML, якщо --list замало
```

Термінал показує кирилицю побитою (`???`) через кодування консолі — це
суто косметика самого виводу команди, на пошук/тап це не впливає (перевірено:
скрипт коректно знаходить і тапає українські підписи).

**Обмеження:** добре працює для кнопок/іконок/чекбоксів/лейблів (усе, що має
`text` або `content-desc`). Для порожніх текстових полів (`OutlinedTextField`
без введеного значення) accessibility-дерево не завжди експонує підказку —
для них надійніше або (а) спершу ввести якийсь текст типовим тапом за
найближчим видимим лейблом-сусідом, або (б) використати `--list`, знайти
bounds потрібного поля вручну і тапнути по центру один раз.

## 2. Стандартний сценарій: створити проєкт → кімнату → відкрити розділ

```bash
T="W:/CubeMaster/.claude/skills/verify/tap_by_text.py"

# Проєкт
python "$T" "Новий проєкт" --contains          # FAB "+" на списку проєктів (content-desc)
sleep 1
python "$T" "Назва об'єкта" --contains         # фокус на полі (якщо не спрацює — tap за bounds з --list)
adb shell input text "TestProject"
python "$T" "Створити"

# Кімната
python "$T" "TestProject" --contains           # відкрити щойно створений проєкт
sleep 1
python "$T" "Додати кімнату" --contains        # FAB кімнат
sleep 1
python "$T" "Назва (наприклад" --contains
adb shell input text "Room1"
python "$T" "Додати"

# Кімната створюється з дефолтним прямокутником 4000×3000 — одразу відкривай Room1
python "$T" "Room1" --contains
```

Далі всередині кімнати доступні вкладки/кнопки (перевірені content-desc/text
цієї сесії):

| Що | Як знайти |
|---|---|
| Вкладки Геометрія/Поверхні/Прорізи | `--contains "Геометрія"`, `"Поверхні"`, `"Прорізи"` |
| Кнопка Демонтаж | `--contains "Демонтаж"` |
| Кнопка "Назад" (будь-який екран) | `"Назад"` — усі топбари використовують один і той самий `contentDescription` |
| Каталог/Профіль/Довідка (список проєктів) | `--contains "Каталог"`, `"Профіль"`, `"Довідка"` |
| План об'єкта/Зведення/Кошторис/Документи (список кімнат) | `--contains "План об'єкта"`, `"Зведення"`, `"Кошторис"`, `"Документи"` |

## 3. Скріншоти

```bash
adb exec-out screencap -p > "<SCRATCHPAD>/shots/NN_опис.png"
```

Завжди `adb exec-out`, ніколи `adb shell screencap ... && adb pull` чи
`adb shell > file` — на Windows/Git Bash звичайний `shell`-редирект ламає
бінарні дані (переклад LF→CRLF), файл виявляється нечитним. Те саме
стосується витягування будь-яких бінарних файлів з пристрою (бази даних
нижче) — тільки `exec-out`.

## 4. Пряме редагування локальної бази (швидше за UI для масових/специфічних даних)

Для сценаріїв, де через UI занадто повільно чи неможливо підготувати стан
(наприклад: перевірка міграції схеми, певна ціна в каталозі, багато кімнат) —
пиши напряму в SQLite-файл застосунку. Room працює в WAL-режимі, тож потрібно
витягувати одразу три файли, інакше побачиш стару/порожню БД:

```bash
MSYS_NO_PATHCONV=1 adb exec-out run-as com.example.cubemaster \
    cat /data/data/com.example.cubemaster/databases/cubemaster.db > local.db
MSYS_NO_PATHCONV=1 adb exec-out run-as com.example.cubemaster \
    cat /data/data/com.example.cubemaster/databases/cubemaster.db-wal > local.db-wal
MSYS_NO_PATHCONV=1 adb exec-out run-as com.example.cubemaster \
    cat /data/data/com.example.cubemaster/databases/cubemaster.db-shm > local.db-shm

sqlite3 local.db "SELECT * FROM rooms;"          # читати
sqlite3 local.db "INSERT INTO ...; PRAGMA wal_checkpoint(TRUNCATE);"  # писати + злити WAL в основний файл

adb shell am force-stop com.example.cubemaster
MSYS_NO_PATHCONV=1 adb push local.db /data/local/tmp/cubemaster.db
MSYS_NO_PATHCONV=1 adb shell run-as com.example.cubemaster rm -f \
    /data/data/com.example.cubemaster/databases/cubemaster.db \
    /data/data/com.example.cubemaster/databases/cubemaster.db-wal \
    /data/data/com.example.cubemaster/databases/cubemaster.db-shm
MSYS_NO_PATHCONV=1 adb shell run-as com.example.cubemaster cp \
    /data/local/tmp/cubemaster.db /data/data/com.example.cubemaster/databases/cubemaster.db
adb shell am start -n com.example.cubemaster/.MainActivity
```

`MSYS_NO_PATHCONV=1` обов'язковий на Git Bash/Windows — без нього шляхи
на кшталт `/data/local/tmp/...` конвертуються в `C:/Program Files/Git/data/...`
і команда провалюється з незрозумілою помилкою.

Після ручних експериментів із БД завжди `adb shell pm clear
com.example.cubemaster`, щоб не лишити тестові дані в наступній сесії.

## 5. Типові пастки цієї сесії (щоб не наступати знову)

- Після `am start` перед першим `force-stop`/pull бази — почекай 3-5
  секунд: анонімний вхід і перше створення Room DB відбуваються
  асинхронно, надто швидкий `pm clear`/`force-stop` застає файл БД ще не
  створеним.
- Клавіатура на екрані зсуває всі елементи нижче — якщо робиш кілька
  `input tap` поспіль без скріншота між ними, другий тап майже напевно
  влучить не туди. `tap_by_text.py` це вирішує (перечитує layout щоразу).
- Один `git commit`/`bash`-виклик з кириличним текстом іноді виводить
  побиту кодову сторінку в консолі — це не помилка виконання, лише
  відображення; перевіряй результат (`git log`, `git status`), а не сам
  зіпсований вивід команди.
