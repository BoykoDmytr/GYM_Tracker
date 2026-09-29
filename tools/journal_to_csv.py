#!/usr/bin/env python3
"""Converts the hand-made training journal (.xlsx) into the app's CSV import format.

The journal is not a table the app can read directly: every sheet is one workout day, each week is a
block with a "Дата: … Вага тіла: … Самопочуття: …" line, sets are spread over "С1 вага / С1 повт." …
column pairs, and cells use shorthand like "16(2)" (two dumbbells of 16 kg), "*" (machine maximum),
"24+<body weight>" (kettlebell plus body weight). This script turns it into one row per set, keeps every
original entry in a note, and prints what it could not convert instead of guessing.

Exercise names are written exactly as in the journal. Matching them to the app's exercises (e.g.
"Жим штанги лежачи (обережно, без партнера)" → "Жим штанги лежачи") happens on the app's import
screen, where every proposed match is shown before anything is saved.

Usage:
    pip install openpyxl
    python3 tools/journal_to_csv.py journal.xlsx journal.csv [--machine-max KG] [--no-corrections]
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import re
import sys
from dataclasses import dataclass, field

try:
    import openpyxl
except ImportError:  # pragma: no cover
    sys.exit("openpyxl is required: pip install openpyxl")

HEADERS = [
    "Дата", "Час початку", "Тривалість (хв)", "Тренування", "Нотатки тренування", "Вправа", "План",
    "Підхід", "Вага (кг)", "Повторення", "RPE", "Відмова", "Нотатка підходу", "Нотатка вправи", "Вага тіла (кг)",
]

# Sheets with workouts and the name each workout gets in the app.
WORKOUT_SHEETS = {"Понеділок": "Push", "Середа": "Pull", "Пятниця": "Lower", "П'ятниця": "Lower", "Субота": "Upper"}

# Rows whose data belongs to another exercise, as the journal's own note on that row says.
# (sheet, date, exercise as written) -> (exercise the sets were actually done for, explanation)
CORRECTIONS = {
    ("Середа", dt.date(2026, 5, 27), "Підйом EZ-штанги на біцепс"): (
        "Розгинання над головою з гантеллю (трицепс)",
        "у журналі записано в рядку «Підйом EZ-штанги на біцепс»; за нотаткою цього дня замість неї робив трицепс з гантеллю за головою",
    ),
}

NUMBER = re.compile(r"^\s*(\d+(?:[.,]\d+)?)\s*(?:кг)?\s*$")
PER_IMPLEMENT = re.compile(r"^\s*(\d+(?:[.,]\d+)?)\s*\(\s*(\d+)\s*\)\s*(гирі|гирі?|гантелі)?\s*$")
PLUS_BODYWEIGHT = re.compile(r"^\s*(\d+(?:[.,]\d+)?)\s*\+\s*(\d+(?:[.,]\d+)?)\s*$")
MACHINE_MAX = re.compile(r"^\s*\*\s*(?:([+-])\s*(\d+(?:[.,]\d+)?))?\s*$")
REPS_PER_SIDE = re.compile(r"^\s*(\d+)\s*\(\s*(\d+)\s*\)\s*$")


@dataclass
class Report:
    sessions: int = 0
    sets: int = 0
    body_weights: int = 0
    names: dict[str, int] = field(default_factory=dict)
    unconverted: list[str] = field(default_factory=list)
    shorthand: list[str] = field(default_factory=list)
    corrections: list[str] = field(default_factory=list)
    dates: list[dt.date] = field(default_factory=list)


def num(text: str) -> float:
    return float(text.replace(",", "."))


def fmt(value: float) -> str:
    """Decimal comma, no trailing zeros: the app's own export format for Excel (;)."""
    text = f"{value:.3f}".rstrip("0").rstrip(".")
    return text.replace(".", ",")


def parse_date(value) -> dt.date | None:
    if isinstance(value, dt.datetime):
        return value.date()
    if isinstance(value, dt.date):
        return value
    if isinstance(value, str):
        m = re.match(r"^\s*(\d{1,2})\.(\d{1,2})\.(\d{4})\s*$", value)
        if m:
            return dt.date(int(m.group(3)), int(m.group(2)), int(m.group(1)))
    return None


def parse_body_weight(value) -> float | None:
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        m = NUMBER.match(value)
        if m:
            return num(m.group(1))
    return None


def parse_weight(raw, machine_max: float | None) -> tuple[float | None, str | None]:
    """Returns (kg, note). kg is None when the entry cannot be converted without guessing."""
    if raw is None or (isinstance(raw, str) and not raw.strip()):
        return 0.0, None  # nothing written: bodyweight exercise (e.g. hanging leg raises)
    if isinstance(raw, (int, float)):
        return float(raw), None
    text = str(raw).strip()
    if m := NUMBER.match(text):
        return num(m.group(1)), None
    if m := PER_IMPLEMENT.match(text):
        implement = "гирі" if (m.group(3) or "").startswith("гир") else "гантелі"
        return num(m.group(1)), f"{m.group(2)} {implement} по {fmt(num(m.group(1)))} кг"
    if m := PLUS_BODYWEIGHT.match(text):
        return num(m.group(1)), f"на одній нозі: гиря {fmt(num(m.group(1)))} кг + власна вага {fmt(num(m.group(2)))} кг"
    if m := MACHINE_MAX.match(text):
        if machine_max is None:
            return 0.0, f"вага «{text}»: * = максимальна вага тренажера (у кг невідома)"
        delta = num(m.group(2)) if m.group(2) else 0.0
        value = machine_max + delta if m.group(1) == "+" else machine_max - delta
        return value, f"вага «{text}» (максимум тренажера {fmt(machine_max)} кг)"
    return None, None


def parse_reps(raw) -> tuple[int | None, str | None]:
    if raw is None or (isinstance(raw, str) and not raw.strip()):
        return None, None
    if isinstance(raw, (int, float)) and float(raw).is_integer() and raw >= 1:
        return int(raw), None
    text = str(raw).strip()
    if text.isdigit() and int(text) >= 1:
        return int(text), None
    if m := REPS_PER_SIDE.match(text):
        return int(m.group(1)), f"повторення «{text}»"
    return None, None


def convert(path: str, out_path: str, machine_max: float | None, corrections: bool) -> Report:
    wb = openpyxl.load_workbook(path, data_only=True)
    report = Report()
    rows: list[list[str]] = [HEADERS]
    for ws in wb.worksheets:
        workout = WORKOUT_SHEETS.get(ws.title)
        if workout is None:
            continue
        date = body = None
        feeling = ""
        session_has_sets = False
        body_written = False
        for row in ws.iter_rows(min_row=1, max_row=ws.max_row):
            cells = [c.value for c in row] + [None] * 12
            first = cells[0]
            if isinstance(first, str) and first.strip().upper().startswith("ТИЖДЕНЬ"):
                if session_has_sets:
                    report.sessions += 1
                date = parse_date(cells[4])
                body = parse_body_weight(cells[7])
                feeling = str(cells[10] or "").strip()
                session_has_sets = False
                body_written = False
                if cells[4] not in (None, "") and date is None:
                    report.unconverted.append(f"{ws.title} {first}: дата «{cells[4]}» не розпізнана — блок пропущено")
                continue
            if not isinstance(first, str) or not first.strip() or first.strip() == "Вправа" or date is None:
                continue
            name = " ".join(first.split())
            plan = str(cells[1] or "").strip()
            rir = str(cells[2] or "").strip()
            row_note = " ".join(str(cells[11] or "").split())
            exercise_note = "; ".join(x for x in [row_note, f"RIR за планом: {rir}" if rir else ""] if x)
            key = (ws.title, date, name)
            if corrections and key in CORRECTIONS:
                name, why = CORRECTIONS[key]
                exercise_note = "; ".join(x for x in [exercise_note, why] if x)
                report.corrections.append(f"{ws.title} {date:%d.%m.%Y}: «{key[2]}» → «{name}» ({why})")
            number = 0
            for s in range(4):
                w_raw, r_raw = cells[3 + 2 * s], cells[4 + 2 * s]
                if (w_raw in (None, "")) and (r_raw in (None, "")):
                    continue
                weight, weight_note = parse_weight(w_raw, machine_max)
                reps, reps_note = parse_reps(r_raw)
                where = f"{ws.title} {date:%d.%m.%Y} «{name}» С{s + 1}"
                if weight is None or reps is None:
                    report.unconverted.append(f"{where}: вага «{w_raw}», повт. «{r_raw}» — не вдалося перетворити, пропущено")
                    continue
                if weight_note or reps_note:
                    report.shorthand.append(f"{where}: «{fmt(w_raw) if isinstance(w_raw, float) else w_raw}» × «{fmt(r_raw) if isinstance(r_raw, float) else r_raw}» → {fmt(weight)} кг × {reps}")
                number += 1
                note = "; ".join(x for x in [weight_note, reps_note] if x)
                rows.append([
                    date.isoformat(), "", "", workout, feeling, name, re.sub(r"\s*×\s*", "×", plan), str(number),
                    fmt(weight), str(reps), "", "", note, exercise_note,
                    fmt(body) if body is not None and not body_written else "",
                ])
                if body is not None and not body_written:
                    body_written = True
                    report.body_weights += 1
                session_has_sets = True
                report.sets += 1
                report.names[name] = report.names.get(name, 0) + 1
                if date not in report.dates:
                    report.dates.append(date)
        if session_has_sets:
            report.sessions += 1
    with open(out_path, "w", encoding="utf-8-sig", newline="") as f:
        csv.writer(f, delimiter=";", lineterminator="\r\n").writerows(rows)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("xlsx")
    parser.add_argument("csv")
    parser.add_argument("--machine-max", type=float, help="maximum weight of the HouseFit machine in kg, for '*' entries")
    parser.add_argument("--no-corrections", action="store_true", help="keep rows under the exercise they are written in")
    args = parser.parse_args()
    report = convert(args.xlsx, args.csv, args.machine_max, not args.no_corrections)

    print(f"Тренувань: {report.sessions}, підходів: {report.sets}, записів ваги тіла: {report.body_weights}")
    if report.dates:
        print(f"Період: {min(report.dates):%d.%m.%Y} – {max(report.dates):%d.%m.%Y}")
    print("\nВправи (як у журналі) — підходів:")
    for name, count in sorted(report.names.items()):
        print(f"  {count:3d}  {name}")
    for title, items in (("Виправлення за нотатками журналу", report.corrections),
                         ("Скорочені записи (оригінал збережено в нотатці підходу)", report.shorthand),
                         ("НЕ перетворено", report.unconverted)):
        if items:
            print(f"\n{title} ({len(items)}):")
            for item in items:
                print("  - " + item)


if __name__ == "__main__":
    main()
