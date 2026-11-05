#!/usr/bin/env python3
"""
Składa przeglądową kartotekę ćwiczeń z seedu: jedna strona HTML z wszystkimi zdjęciami, opisami,
kategoriami i celami — do przeglądania w przeglądarce, nie w aplikacji.

Wejście:  baza/exercises.json + baza/images/<id>.jpg (D-47)
          etykiety polskie z domain/model/Enums.kt (żeby nie rozjeżdżały się z aplikacją)
Wyjście:  build/przeglad/index.html

Użycie:
  python tools/przeglad.py            # strona odwołuje się do zdjęć w baza/ (lekka, po zmianie bazy wystarczy odświeżyć)
                                      # i od razu otwiera się w domyślnej przeglądarce (--bez-otwierania, żeby nie)
  python tools/przeglad.py --osadz    # zdjęcia wklejone w plik (jeden samodzielny HTML, ~5 MB, do wysłania albo na telefon)
  python tools/przeglad.py --osadz --artefakt  # sama treść strony, bez <html>, do opublikowania jako artefakt

Od seedu v5 każde ćwiczenie ma jedną ilustrację generowaną (D-36), więc źródłem obrazu jest zawsze „ilustracja”;
filtr źródła na stronie został po czasach zdjęć z free-exercise-db i wger.
Podgląd „jak w aplikacji” powtarza filtr zdjęć z ui/common/Components.kt (szarość → sepia → nasycenie → jasność).

Wymaga: Python 3.9+ (bez zależności).
"""
import argparse
import base64
import datetime
import json
import os
import re
import sys
import webbrowser
from pathlib import Path

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BAZA = os.path.join(ROOT, "baza")
SEED = os.path.join(BAZA, "exercises.json")
ENUMS = os.path.join(ROOT, "app", "src", "main", "java", "pl", "mysttic", "trainings", "domain", "model", "Enums.kt")
OUT_DIR = os.path.join(ROOT, "build", "przeglad")

# Enumy bez etykiet w Kotlinie — nazwy tylko na tę stronę.
KIND_PL = {"REPS": "Powtórzenia", "TIME": "Czas"}
SOURCE_PL = {
    "ilustracja": "Ilustracja",
    "free-exercise-db": "free-exercise-db",
    "wger": "wger",
}


def enum_labels(text, name):
    """Etykiety polskie z Enums.kt: NAZWA("Etykieta") albo NAZWA(1, "Etykieta")."""
    m = re.search(r"enum class %s\b[^{]*\{(.*?)\n\}" % name, text, re.S)
    if not m:
        sys.exit(f"Nie znalazłem enuma {name} w {ENUMS}")
    out = {}
    for entry in re.finditer(r"^\s*([A-Z][A-Z_0-9]*)\s*\(([^)]*)\)", m.group(1), re.M):
        labels = re.findall(r'"([^"]*)"', entry.group(2))
        if labels:
            out[entry.group(1)] = labels[-1]
    return out


def data_uri(path):
    with open(path, "rb") as f:
        return "data:image/jpeg;base64," + base64.b64encode(f.read()).decode("ascii")


def build(embed, seed=None, obraz_url=None, surowe=False):
    """Dane strony. Edytor (tools/baza.py) podaje bazę, adres obrazu `obraz_url(ścieżka względna, plik)`
    i prosi o rekordy w układzie exercises.json (`surowe`), które formularz edytuje."""
    if seed is None:
        seed = json.load(open(SEED, encoding="utf-8"))
    enums = open(ENUMS, encoding="utf-8").read()
    cat_pl = enum_labels(enums, "ExerciseCategory")
    lvl_pl = enum_labels(enums, "Difficulty")
    eq_pl = enum_labels(enums, "Equipment")
    mus_pl = enum_labels(enums, "MuscleGroup")

    exercises = []
    for e in seed["exercises"]:
        images = []
        for rel in e["images"]:
            path = os.path.join(BAZA, rel.replace("/", os.sep))
            if not os.path.exists(path):
                print(f"Uwaga: {e['id']} wskazuje na brakujące zdjęcie {rel}")
                continue
            if obraz_url:
                images.append(obraz_url(rel, path))
            else:
                images.append(data_uri(path) if embed else "../../baza/" + rel)
        item = dict(
            id=e["id"],
            pl=e["namePl"],
            en=e["nameEn"],
            kind=e["kind"],
            level=e["level"],
            category=e["category"],
            equipment=e.get("equipment", []),
            primary=e.get("primaryMuscles", []),
            secondary=e.get("secondaryMuscles", []),
            instr=e.get("instructionsPl", []),
            images=images,
            easier=e.get("easierId"),
            harder=e.get("harderId"),
            reps=e.get("defaultReps"),
            seconds=e.get("defaultSeconds"),
            source="ilustracja",
            sourceId=e.get("sourceId"),
            author=None,
            retired=e.get("retiredAt"),
        )
        if surowe:
            item["raw"] = e
        exercises.append(item)

    # Od seedu v7 plik nie wozi już szablonów treningów (kolejkę układa kreator albo gotowy program).
    return dict(
        version=seed["version"],
        built=datetime.date.today().isoformat(),
        exercises=exercises,
        labels=dict(category=cat_pl, level=lvl_pl, equipment=eq_pl, muscle=mus_pl, kind=KIND_PL, source=SOURCE_PL),
    )


def document(body):
    """Szkielet strony do otwarcia z dysku. Bez `meta charset` przeglądarka zjada polskie znaki z file://."""
    title = re.search(r"<title>(.*?)</title>", body).group(1)
    return (
        "<!doctype html>\n<html lang=\"pl\">\n<head>\n"
        "<meta charset=\"utf-8\">\n"
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
        f"<title>{title}</title>\n</head>\n<body>\n{body}\n</body>\n</html>\n"
    )


def strona(data, artefakt=False, tytul=None):
    """HTML strony z danymi wklejonymi w szablon. `</` w danych nie może zamknąć skryptu, stąd `<\\/`."""
    template = open(os.path.join(ROOT, "tools", "przeglad.html"), encoding="utf-8").read()
    if tytul:
        template = re.sub(r"<title>.*?</title>", f"<title>{tytul}</title>", template, count=1)
    body = template.replace("/*__DANE__*/null", json.dumps(data, ensure_ascii=False).replace("</", "<\\/"))
    return body if artefakt else document(body)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--osadz", action="store_true", help="wklej zdjęcia do pliku (samodzielny HTML)")
    ap.add_argument("--wyjscie", metavar="PLIK", help="ścieżka wyjściowa (domyślnie build/przeglad/index.html)")
    ap.add_argument("--artefakt", action="store_true", help="bez szkieletu <html> — treść strony pod publikację")
    ap.add_argument("--bez-otwierania", action="store_true", help="nie otwieraj strony w przeglądarce po zapisie")
    args = ap.parse_args()

    data = build(embed=args.osadz)
    html = strona(data, artefakt=args.artefakt)

    out = args.wyjscie or os.path.join(OUT_DIR, "index.html")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(html)
    n_img = sum(len(e["images"]) for e in data["exercises"])
    size = os.path.getsize(out) / 1_000_000
    print(f"OK: {len(data['exercises'])} ćwiczeń, {n_img} zdjęć, seed v{data['version']} -> {out} ({size:.1f} MB)")
    # Artefakt to sama treść bez <html>, w przeglądarce nie ma czego oglądać.
    if not args.bez_otwierania and not args.artefakt:
        webbrowser.open(Path(out).resolve().as_uri())


if __name__ == "__main__":
    main()
