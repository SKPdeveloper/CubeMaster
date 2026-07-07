#!/usr/bin/env python3
"""Знайти елемент UI на підключеному Android-пристрої за видимим текстом,
content-description чи resource-id і тапнути по його центру.

Навіщо: ручне вгадування піксельних координат для adb `input tap` ламається
щоразу, коли з'являється клавіатура чи трохи зсувається layout (це сталось
багато разів під час ручного тестування CubeMaster). uiautomator dump дає
точні межі елемента незалежно від того, що саме зараз на екрані.

Використання:
    python tap_by_text.py "Створити"                  # точний збіг тексту
    python tap_by_text.py "Кімнат" --contains          # частковий збіг
    python tap_by_text.py "Add" --contains --index 1   # другий збіг (0 — перший)
    python tap_by_text.py "Площа" --dump-only          # тільки показати XML (діагностика)
    python tap_by_text.py x --list                     # список усіх text/content-desc на екрані (перший аргумент ігнорується)

Вимагає: adb у PATH, підключений пристрій/емулятор (adb devices).
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


def dump_ui() -> str:
    result = subprocess.run(
        ["adb", "exec-out", "uiautomator", "dump", "/dev/tty"],
        capture_output=True,
    )
    xml_text = result.stdout.decode("utf-8", errors="ignore")
    # uiautomator інколи дописує в той самий потік службовий рядок
    # "UI hierarchy dumped to: ..." після XML — відрізаємо все після останнього ">".
    end = xml_text.rfind(">")
    if end == -1:
        raise RuntimeError(f"Не вдалось отримати UI dump. Сирий вивід: {xml_text[:300]}")
    return xml_text[: end + 1]


def find_all_bounds(xml_text: str, text: str, contains: bool) -> list[str]:
    root = ET.fromstring(xml_text)
    matches = []
    for node in root.iter("node"):
        candidates = (node.get("text", ""), node.get("content-desc", ""), node.get("resource-id", ""))
        hit = any(text in c for c in candidates) if contains else any(text == c for c in candidates)
        if hit and node.get("bounds"):
            matches.append(node.get("bounds"))
    return matches


def parse_center(bounds_str: str) -> tuple[int, int]:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
    if not m:
        raise ValueError(f"Не вдалось розпарсити bounds: {bounds_str}")
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def main() -> None:
    args = sys.argv[1:]
    if not args or args[0].startswith("--"):
        print(__doc__)
        sys.exit(1)

    text = args[0]
    contains = "--contains" in args
    dump_only = "--dump-only" in args
    list_mode = "--list" in args
    index = 0
    if "--index" in args:
        index = int(args[args.index("--index") + 1])

    xml_text = dump_ui()
    if dump_only:
        print(xml_text)
        return

    if list_mode:
        root = ET.fromstring(xml_text)
        for node in root.iter("node"):
            t, desc, rid = node.get("text", ""), node.get("content-desc", ""), node.get("resource-id", "")
            if t or desc:
                clickable = "clickable" if node.get("clickable") == "true" else ""
                print(f"{node.get('bounds')}\ttext={t!r}\tdesc={desc!r}\t{clickable}")
        return

    matches = find_all_bounds(xml_text, text, contains)
    if not matches:
        print(f"НЕ ЗНАЙДЕНО: '{text}' (contains={contains}). Спробуйте --dump-only, щоб побачити реальний UI.")
        sys.exit(2)
    if index >= len(matches):
        print(f"Знайдено лише {len(matches)} збігів для '{text}', запитано index={index}.")
        sys.exit(3)

    x, y = parse_center(matches[index])
    print(f"Тап '{text}' [{index}/{len(matches)}] -> ({x}, {y})")
    subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])


if __name__ == "__main__":
    main()
