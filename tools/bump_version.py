#!/usr/bin/env python3
"""
Podnosi numer wersji w VERSION.md i zamyka sekcję „Niewydane" w CHANGELOG.md.

    python tools/bump_version.py 0.2.0
    python tools/bump_version.py 0.2.0 --date 2026-09-30   # data inna niż dzisiejsza

`app/build.gradle.kts` czyta VERSION.md przy każdym buildzie, więc nie ma tu nic
do synchronizowania: versionName i versionCode wynikają z tego jednego numeru.

Skrypt odmawia pracy, gdy nowy numer nie jest wyższy od obecnego (Android nie
przyjmie aktualizacji z niższym versionCode) i gdy sekcja „Niewydane" jest pusta
— wydanie bez opisu zmian nie ma sensu, bo ten opis trafia wprost na GitHuba.
"""
import argparse
import datetime
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "VERSION.md"
CHANGELOG = ROOT / "CHANGELOG.md"
NUMBER = re.compile(r"^\d+\.\d+\.\d+$")
UNRELEASED = "## [Niewydane]"


def current_version() -> str:
    for line in VERSION_FILE.read_text(encoding="utf-8").splitlines():
        if NUMBER.match(line.strip()):
            return line.strip()
    sys.exit("VERSION.md: nie znalazłem linii z samym numerem wersji")


def as_tuple(version: str) -> tuple:
    return tuple(int(part) for part in version.split("."))


def bump_version_file(new: str, old: str) -> None:
    lines = VERSION_FILE.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if line.strip() == old:
            lines[i] = new
            break
    VERSION_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def bump_changelog(new: str, date: str) -> None:
    text = CHANGELOG.read_text(encoding="utf-8")
    if UNRELEASED not in text:
        sys.exit(f"CHANGELOG.md: brak sekcji „{UNRELEASED}”")
    head, _, rest = text.partition(UNRELEASED)
    body, _, tail = rest.partition("\n## ")
    if not body.strip():
        sys.exit("CHANGELOG.md: sekcja „Niewydane” jest pusta — opisz zmiany przed wydaniem")

    updated = f"{head}{UNRELEASED}\n\n## [{new}] - {date}\n\n{body.strip()}\n\n## {tail}"
    # Odsyłacze na dole pliku: „Niewydane" porównuje się już z nowym tagiem.
    updated = re.sub(
        r"^\[Niewydane\]: .*/compare/v[\d.]+\.\.\.HEAD$",
        f"[Niewydane]: https://github.com/Mysttic/mysttic-trainings/compare/v{new}...HEAD",
        updated,
        count=1,
        flags=re.MULTILINE,
    )
    marker = "[Niewydane]: "
    index = updated.index(marker)
    end_of_line = updated.index("\n", index) + 1
    link = f"[{new}]: https://github.com/Mysttic/mysttic-trainings/releases/tag/v{new}\n"
    updated = updated[:end_of_line] + link + updated[end_of_line:]
    CHANGELOG.write_text(updated, encoding="utf-8", newline="\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Podnosi wersję w VERSION.md i CHANGELOG.md")
    parser.add_argument("version", help="nowy numer, np. 0.2.0")
    parser.add_argument("--date", default=datetime.date.today().isoformat(), help="data wydania (YYYY-MM-DD)")
    args = parser.parse_args()

    new = args.version.strip().lstrip("v")
    if not NUMBER.match(new):
        sys.exit(f"„{new}” to nie jest numer w formacie major.minor.patch")

    old = current_version()
    if as_tuple(new) <= as_tuple(old):
        sys.exit(f"{new} nie jest wyższe niż obecne {old} — Android nie przyjmie takiej aktualizacji")
    if max(as_tuple(new)[1:]) > 99:
        sys.exit("minor i patch muszą być mniejsze niż 100 (versionCode = major*10000 + minor*100 + patch)")

    bump_changelog(new, args.date)
    bump_version_file(new, old)
    print(f"Wersja {old} -> {new}, CHANGELOG zamknięty datą {args.date}.")
    print("Zostaje: commit na develop, PR do master, scalenie. Wydanie zrobi się samo.")


if __name__ == "__main__":
    main()
