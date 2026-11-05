#!/usr/bin/env python3
"""
Wyciąga z CHANGELOG.md opis jednego wydania: to on trafia do wydania na GitHubie.

    python tools/release_notes.py 0.2.0            # aplikacja, na standardowe wyjście
    python tools/release_notes.py 0.2.0 -o notes.md
    python tools/release_notes.py --baza 11        # baza ćwiczeń: sekcja „## [Baza 11]” (D-47)

Kończy się błędem, gdy sekcji nie ma albo jest pusta: wydanie bez opisu zmian
byłoby dla użytkownika komunikatem „coś się zmieniło, powodzenia”.
"""
import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CHANGELOG = ROOT / "CHANGELOG.md"


def database_header(number: int) -> str:
    """Nagłówek sekcji wydania bazy ćwiczeń; to samo słowo stoi w odsyłaczu na dole pliku."""
    return f"Baza {number}"


def find(header: str):
    """Treść sekcji „## [header]” bez odsyłaczy z dołu pliku; None, gdy takiej sekcji nie ma."""
    text = CHANGELOG.read_text(encoding="utf-8")
    pattern = re.compile(rf"^## \[{re.escape(header)}\][^\n]*\n(.*?)(?=^## |\Z)", re.M | re.S)
    found = pattern.search(text)
    if not found:
        return None
    # Odsyłacze z dołu pliku (```[0.2.0]: https://...```) nie są opisem zmian.
    lines = [line for line in found.group(1).splitlines() if not re.match(r"^\[[^\]]+\]: http", line)]
    return "\n".join(lines).strip()


def section(header: str) -> str:
    body = find(header)
    if body is None:
        sys.exit(f"CHANGELOG.md: brak sekcji [{header}]")
    if not body:
        sys.exit(f"CHANGELOG.md: sekcja [{header}] jest pusta")
    return body


def main() -> None:
    parser = argparse.ArgumentParser(description="Opis wydania z CHANGELOG.md")
    parser.add_argument("version", nargs="?", help="numer wersji aplikacji, np. 0.2.0")
    parser.add_argument("--baza", type=int, metavar="N", help="numer bazy ćwiczeń zamiast wersji aplikacji")
    parser.add_argument("-o", "--output", help="zapisz do pliku zamiast na wyjście")
    args = parser.parse_args()
    if (args.version is None) == (args.baza is None):
        parser.error("podaj numer wersji aplikacji albo --baza N")

    header = database_header(args.baza) if args.baza is not None else args.version.strip().lstrip("v")
    notes = section(header)
    if args.output:
        pathlib.Path(args.output).write_text(notes + "\n", encoding="utf-8", newline="\n")
    else:
        sys.stdout.reconfigure(encoding="utf-8")
        print(notes)


if __name__ == "__main__":
    main()
