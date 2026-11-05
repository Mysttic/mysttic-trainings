#!/usr/bin/env python3
"""
Pilnuje, żeby docs/DATABASE.md nie rozjechało się z wyeksportowanym schematem Room.

Porównuje najnowszy plik z app/schemas/pl.mysttic.trainings.data.db.AppDatabase/ z dokumentem:
numer wersji, komplet tabel, komplet kolumn w sekcji każdej tabeli i kolumny opisane w dokumencie,
których w schemacie już nie ma.

Użycie:
  python tools/check_schema.py        # 0 = zgodne, 1 = do poprawy (z listą różnic)

Uruchamiaj po każdej zmianie encji — razem z podbiciem `version` i migracją (docs/DECISIONS.md, D-05).
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEMAS = os.path.join(ROOT, "app", "schemas", "pl.mysttic.trainings.data.db.AppDatabase")
DOC = os.path.join(ROOT, "docs", "DATABASE.md")


def newest_schema():
    versions = [int(f[:-5]) for f in os.listdir(SCHEMAS) if f.endswith(".json")]
    version = max(versions)
    with open(os.path.join(SCHEMAS, f"{version}.json"), encoding="utf-8") as f:
        return version, json.load(f)["database"]["entities"]


def sections(text):
    """Sekcje `### \\`nazwa_tabeli\\`` z dokumentu: nazwa -> treść do następnego nagłówka."""
    out = {}
    parts = re.split(r"\n(?=#{2,3} )", text)
    for part in parts:
        m = re.match(r"#{2,3} `([a-z_]+)`", part)
        if m:
            out[m.group(1)] = part
    return out


def documented_columns(section):
    """Nazwy w pierwszej komórce wiersza tabeli: `| \\`a\\`, \\`b\\` | ...` -> {a, b}."""
    names = set()
    for line in section.splitlines():
        m = re.match(r"\|\s*((?:`[A-Za-z_]+`(?:,\s*)?)+)\s*\|", line)
        if m:
            names.update(re.findall(r"`([A-Za-z_]+)`", m.group(1)))
    return names


def main():
    version, entities = newest_schema()
    doc = open(DOC, encoding="utf-8").read()
    found = sections(doc)
    problems = []

    if f"**wersja {version}**" not in doc:
        problems.append(f"docs/DATABASE.md nie mówi o wersji {version} (szukam '**wersja {version}**')")

    for entity in entities:
        table = entity["tableName"]
        section = found.get(table)
        if section is None:
            problems.append(f"brak sekcji `### \\`{table}\\`` w dokumencie")
            continue
        columns = {f["columnName"] for f in entity["fields"]}
        described = documented_columns(section)
        for missing in sorted(columns - described):
            problems.append(f"{table}: kolumna `{missing}` nie jest opisana")
        for extra in sorted(described - columns):
            problems.append(f"{table}: dokument opisuje `{extra}`, a schemat jej nie ma")

    for table in sorted(set(found) - {e["tableName"] for e in entities}):
        problems.append(f"dokument ma sekcję `{table}`, a schematu takiej tabeli nie ma")

    if problems:
        print(f"Rozjazd docs/DATABASE.md ze schematem v{version}:")
        for p in problems:
            print(" -", p)
        sys.exit(1)
    tables = len(entities)
    columns = sum(len(e["fields"]) for e in entities)
    print(f"OK: docs/DATABASE.md zgodne ze schematem v{version} ({tables} tabel, {columns} kolumn)")


if __name__ == "__main__":
    main()
