#!/usr/bin/env python3
"""
Baza ćwiczeń (D-47): katalog baza/ z exercises.json i images/<id>.jpg, z własnym numerem i własnymi wydaniami.
Gradle podpina baza/ jako assety APK, a wydanie bazy pakuje ten sam katalog do ZIP-a.

  python tools/baza.py                          # edytor bazy w przeglądarce (lokalnie, 127.0.0.1); to samo co `edytor`
  python tools/baza.py edytor --port 8770 --bez-otwierania
  python tools/baza.py sprawdz                  # reguły bazy (te same co SeedIntegrityTest)
  python tools/baza.py sprawdz --wzgledem REF   # plus zgodność wstecz względem REF (np. origin/master albo baza-v10)
  python tools/baza.py podbij                   # numer bazy +1 (exercises.json, VERSION.md, nagłówek w CHANGELOG.md)
  python tools/baza.py podbij --min-app 1.2.0   # do tego najniższa wersja aplikacji, która bazę przyjmie
  python tools/baza.py partia PACZKA.zip        # ilustracje z generatora zdjęć (D-36): do baza/images/ albo do szkiców
  python tools/baza.py paczka KATALOG --wzgledem REF  # ZIP, manifest i opis wydania bazy (workflow baza.yml)

Edytor to kartoteka z tools/przeglad.html z trybem edycji: dodawanie ćwiczenia z ilustracją, edycja pól, wycofanie
z datą, szkice, paczki z generatora i podbicie numeru. Zapisuje prosto do baza/ i tools/szkice/, niczego nie
commituje. Słucha tylko na 127.0.0.1 i przyjmuje zapis wyłącznie z własnej strony.

Szkice (tools/szkice/) to ilustracje i definicje, które jeszcze nie weszły do bazy, więc nie jadą do aplikacji.

Reguły zgodności wstecz (D-47): id raz wydanego ćwiczenia nie znika i nie zmienia `kind`, zmiana treści podnosi
numer, a wartość enuma, której nie zna ostatnie wydanie aplikacji, wymaga `minApp` wyższego od niego. Usunięcie
ćwiczenia to wycofanie: `retiredAt` z datą.

Numer bazy stoi w dwóch miejscach: w baza/exercises.json (czyta go aplikacja) i w VERSION.md pod nagłówkiem
„# Wersja bazy ćwiczeń”, obok numeru aplikacji. `podbij` zmienia oba, `sprawdz` pilnuje, żeby się zgadzały. Opis
wydania bazy to sekcja „## [Baza N]” w CHANGELOG.md: nowy numer bez niej nie przejdzie `sprawdz --wzgledem`,
a `paczka` nie złoży wydania.

`sprawdz` i `podbij` biorą tylko bibliotekę standardową (CI); skalowanie ilustracji innej niż JPEG do 480 px
(`partia`, edytor) potrzebuje Pillow (pip install pillow).
"""
import argparse
import base64
import datetime
import hashlib
import http.server
import io
import json
import os
import re
import struct
import subprocess
import sys
import threading
import urllib.parse
import webbrowser
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BAZA = os.path.join(ROOT, "baza")
PLIK = os.path.join(BAZA, "exercises.json")
OBRAZY = os.path.join(BAZA, "images")
SZKICE = os.path.join(ROOT, "tools", "szkice")
SZKICE_PLIK = os.path.join(SZKICE, "szkice.json")
VERSION_FILE = os.path.join(ROOT, "VERSION.md")
CHANGELOG = os.path.join(ROOT, "CHANGELOG.md")
NAGLOWEK_BAZY = "# Wersja bazy ćwiczeń"  # w VERSION.md; pierwsza niepusta linia pod nim to numer bazy
ENUMS = "app/src/main/java/pl/mysttic/trainings/domain/model/Enums.kt"
PROGRAMY = "app/src/main/java/pl/mysttic/trainings/domain/plan/preset/PresetDefinitions.kt"
# Układ sprzed D-47: z takim REF-em (np. master przed scaleniem) też da się porównać bazę.
STARY_PLIK = "app/src/main/assets/seed/exercises.json"
STARE_OBRAZY = "app/src/main/assets/seed/images"

FORMAT = 1  # format, który zna aplikacja z tego repo (`SeedFile.format`)
ID = re.compile(r"[a-z0-9]+(-[a-z0-9]+)*")
NUMER = re.compile(r"\d+\.\d+\.\d+")
DATA = re.compile(r"\d{4}-\d{2}-\d{2}")
CUSTOM_PREFIX = "custom-"  # zarezerwowany dla ćwiczeń z plików planów (D-42, `PlanFileCodec.CUSTOM_PREFIX`)
ID_MAX = 64
NAZWA_MAX = 80
KROKI_MAX = 10
KROK_MAX = 500
POWTORZENIA = (1, 30)  # widełki `PlanFileCodec.MAX_DEFAULT_REPS`
SEKUNDY = (5, 900)  # `PlanFileCodec.MIN_DEFAULT_SECONDS` i `MAX_DEFAULT_SECONDS`
OBRAZ_MAX_BAJTOW = 200_000
OBRAZ_SZEROKOSC = 480
OBRAZ_WYSOKOSC_MAX = 1200
JAKOSC = 72

# Kolejność kluczy ćwiczenia w exercises.json; `retiredAt` dochodzi na końcu tylko u wycofanych.
KOLEJNOSC = [
    "id", "sourceId", "nameEn", "namePl", "kind", "level", "category", "equipment", "primaryMuscles",
    "secondaryMuscles", "instructionsPl", "images", "easierId", "harderId", "defaultReps", "defaultSeconds", "retiredAt",
]
POLA = set(KOLEJNOSC)
WYMAGANE = {"id", "nameEn", "namePl", "kind", "level", "category", "instructionsPl", "images"}
ENUM_POLE = {"kind": "ExerciseKind", "level": "Difficulty", "category": "ExerciseCategory"}
ENUM_LISTA = {"equipment": "Equipment", "primaryMuscles": "MuscleGroup", "secondaryMuscles": "MuscleGroup"}

BLOKADA = threading.Lock()  # edytor: jeden zapis naraz


# --- pliki i git ---------------------------------------------------------------------------------------------

def wczytaj():
    with open(PLIK, encoding="utf-8") as f:
        return json.load(f)


def zapisz_plik(sciezka, tresc):
    """Zapis przez plik tymczasowy obok: przerwany zapis nie zostawia połowy pliku."""
    tymczasowy = sciezka + ".tmp"
    tryb = "wb" if isinstance(tresc, bytes) else "w"
    opcje = {} if isinstance(tresc, bytes) else {"encoding": "utf-8", "newline": "\n"}
    with open(tymczasowy, tryb, **opcje) as f:
        f.write(tresc)
    os.replace(tymczasowy, sciezka)


def json_tekst(dane):
    """Ten sam układ co przy przeniesieniu (wcięcie 2, polskie znaki wprost), żeby diff pokazywał tylko zmiany."""
    return json.dumps(dane, ensure_ascii=False, indent=2) + "\n"


def zapisz(dane):
    zapisz_plik(PLIK, json_tekst(dane))


def git(*args):
    """Wyjście polecenia gita albo None, gdy się nie udało (brak REF-u, płytki klon, brak gita)."""
    try:
        wynik = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, check=False)
    except OSError:
        return None
    return wynik.stdout if wynik.returncode == 0 else None


def blob(dane):
    """Skrót pliku tak, jak liczy go git, żeby porównać obraz z dysku z obrazem z REF-u bez wyciągania go."""
    return hashlib.sha1(b"blob %d\0" % len(dane) + dane).hexdigest()


def wersja_aplikacji():
    with open(VERSION_FILE, encoding="utf-8") as f:
        for linia in f:
            if NUMER.fullmatch(linia.strip()):
                return linia.strip()
    sys.exit("VERSION.md: brak linii z samym numerem wersji")


def numer_bazy_w_version_md():
    """Numer bazy z VERSION.md: pierwsza niepusta linia pod „# Wersja bazy ćwiczeń”; None, gdy go tam nie ma."""
    with open(VERSION_FILE, encoding="utf-8") as f:
        linie = [linia.strip() for linia in f]
    if NAGLOWEK_BAZY not in linie:
        return None
    nastepna = next((linia for linia in linie[linie.index(NAGLOWEK_BAZY) + 1:] if linia), "")
    return int(nastepna) if re.fullmatch(r"[0-9]+", nastepna) else None


def zapisz_numer_bazy(numer):
    """Nowy numer bazy w VERSION.md w miejsce dotychczasowego; reszta pliku bez zmian."""
    with open(VERSION_FILE, encoding="utf-8") as f:
        linie = f.read().splitlines()
    gole = [linia.strip() for linia in linie]
    if NAGLOWEK_BAZY not in gole or numer_bazy_w_version_md() is None:
        raise ValueError(f"VERSION.md: pod nagłówkiem „{NAGLOWEK_BAZY}” brak samego numeru bazy")
    i = next(i for i in range(gole.index(NAGLOWEK_BAZY) + 1, len(gole)) if gole[i])
    linie[i] = str(numer)
    zapisz_plik(VERSION_FILE, "\n".join(linie) + "\n")


def sprawdz_version_md(dane):
    """VERSION.md i baza/exercises.json mówią o tym samym numerze bazy."""
    numer = numer_bazy_w_version_md()
    if numer is None:
        return [f"VERSION.md: brak numeru bazy pod nagłówkiem „{NAGLOWEK_BAZY}”"]
    if numer != dane.get("version"):
        return [f"VERSION.md ma bazę v{numer}, a baza/exercises.json v{dane.get('version')}: "
                "oba zmienia python tools/baza.py podbij"]
    return []


def opis_bazy(numer):
    """Opis wydania bazy: sekcja „## [Baza N]” z CHANGELOG.md bez odsyłaczy; None, gdy jej nie ma."""
    import release_notes  # tools/ jest na ścieżce, bo stąd uruchamia się skrypt

    return release_notes.find(release_notes.database_header(numer))


def dopisz_naglowek_bazy(numer):
    """Pusty nagłówek „## [Baza N]” w CHANGELOG.md nad najnowszą sekcją wydania i odsyłacz do tagu na dole.
    Opis pisze człowiek; istniejącej sekcji nie rusza."""
    import release_notes

    naglowek = release_notes.database_header(numer)
    if release_notes.find(naglowek) is not None:
        return
    with open(CHANGELOG, encoding="utf-8") as f:
        tekst = f.read()
    pierwsza = re.search(r"^## \[(?!Niewydane\])", tekst, re.M)
    if pierwsza is None:
        raise ValueError("CHANGELOG.md: brak sekcji wydań, nie wiem, gdzie dopisać nagłówek bazy")
    tekst = tekst[:pierwsza.start()] + f"## [{naglowek}]\n\n" + tekst[pierwsza.start():]
    odsylacz = f"[{naglowek}]: https://github.com/Mysttic/mysttic-trainings/releases/tag/baza-v{numer}\n"
    niewydane = re.search(r"^\[Niewydane\]: .*\n", tekst, re.M)
    if niewydane:
        tekst = tekst[:niewydane.end()] + odsylacz + tekst[niewydane.end():]
    else:
        tekst = tekst.rstrip("\n") + "\n" + odsylacz
    zapisz_plik(CHANGELOG, tekst)


def krotka(numer):
    return tuple(int(c) for c in numer.split("."))


def enumy(tekst):
    """Stałe enumów z Enums.kt: nazwa enuma → zbiór stałych. Komentarze, napisy i argumenty idą precz."""
    tekst = re.sub(r"/\*.*?\*/", "", tekst, flags=re.S)
    tekst = re.sub(r"//[^\n]*", "", tekst)
    tekst = re.sub(r'"(?:\\.|[^"\\])*"', '""', tekst)
    wynik = {}
    for m in re.finditer(r"enum class (\w+)[^{]*\{", tekst):
        cialo = tekst[m.end():]
        koniec = min(i for i in (cialo.find(";"), cialo.find("}"), len(cialo)) if i >= 0)
        cialo = cialo[:koniec]
        while True:
            krotsze = re.sub(r"\([^()]*\)", "", cialo)
            if krotsze == cialo:
                break
            cialo = krotsze
        wynik[m.group(1)] = {s.strip() for s in cialo.split(",") if re.fullmatch(r"[A-Z][A-Z0-9_]*", s.strip())}
    return wynik


def znane_enumy():
    with open(os.path.join(ROOT, ENUMS), encoding="utf-8") as f:
        return enumy(f.read())


def jpeg_wymiary(dane):
    """Szerokość i wysokość z nagłówka SOF; None, gdy to nie JPEG albo nagłówek jest uszkodzony."""
    if not dane.startswith(b"\xff\xd8"):
        return None
    i = 2
    while i + 4 <= len(dane):
        if dane[i] != 0xFF:
            return None
        znacznik = dane[i + 1]
        if znacznik in (0xD8, 0x01) or 0xD0 <= znacznik <= 0xD7:
            i += 2
            continue
        dlugosc = struct.unpack(">H", dane[i + 2:i + 4])[0]
        if znacznik in (0xC0, 0xC1, 0xC2, 0xC3) and i + 9 <= len(dane):
            wysokosc, szerokosc = struct.unpack(">HH", dane[i + 5:i + 9])
            return szerokosc, wysokosc
        i += 2 + dlugosc
    return None


def dzis():
    return datetime.date.today().isoformat()


# --- reguły --------------------------------------------------------------------------------------------------

def sprawdz_dane(dane, znane, obraz):
    """Reguły samej bazy. `obraz(id)` zwraca bajty ilustracji albo None. Zwraca listę błędów."""
    bledy = []
    if not isinstance(dane, dict):
        return ["exercises.json: oczekiwany obiekt"]
    if "workouts" in dane:
        bledy.append("exercises.json: baza nie wozi szablonów treningów (sekcja workouts, D-43)")
    if not isinstance(dane.get("version"), int) or dane["version"] < 1:
        bledy.append("version: oczekiwana liczba całkowita od 1")
    if dane.get("format") != FORMAT:
        bledy.append(f"format: {dane.get('format')!r}, a aplikacja z tego repo zna {FORMAT}")
    min_app = dane.get("minApp")
    if not isinstance(min_app, str) or not NUMER.fullmatch(min_app):
        bledy.append(f"minApp: {min_app!r} poza formatem 1.2.3")
    elif krotka(min_app) > krotka(wersja_aplikacji()):
        bledy.append(f"minApp {min_app} wyższe niż VERSION.md ({wersja_aplikacji()}): APK z tego repo by jej nie przyjął")
    cwiczenia = dane.get("exercises")
    if not isinstance(cwiczenia, list) or not cwiczenia:
        return bledy + ["exercises: oczekiwana niepusta lista"]

    po_id = {}
    for n, e in enumerate(cwiczenia):
        gdzie = f"exercises[{n}]"
        if not isinstance(e, dict):
            bledy.append(f"{gdzie}: oczekiwany obiekt")
            continue
        ex_id = e.get("id")
        if isinstance(ex_id, str):
            gdzie = ex_id
        for pole in sorted(WYMAGANE - e.keys()):
            bledy.append(f"{gdzie}: brak pola {pole}")
        for pole in sorted(e.keys() - POLA):
            bledy.append(f"{gdzie}: nieznane pole {pole}")
        if not isinstance(ex_id, str) or not ID.fullmatch(ex_id) or len(ex_id) > ID_MAX:
            bledy.append(f"{gdzie}: id poza formatem kebab-case (do {ID_MAX} znaków)")
            continue
        if ex_id.startswith(CUSTOM_PREFIX):
            bledy.append(f"{ex_id}: prefiks {CUSTOM_PREFIX} jest zarezerwowany dla ćwiczeń z plików planów (D-42)")
        if ex_id in po_id:
            bledy.append(f"{ex_id}: id powtórzone")
        po_id[ex_id] = e

        for pole in ("namePl", "nameEn"):
            wartosc = e.get(pole)
            if not isinstance(wartosc, str) or not wartosc.strip() or len(wartosc) > NAZWA_MAX:
                bledy.append(f"{ex_id}: {pole} pusta albo dłuższa niż {NAZWA_MAX} znaków")
        if "sourceId" in e and e["sourceId"] is not None and not isinstance(e["sourceId"], str):
            bledy.append(f"{ex_id}: sourceId musi być tekstem")
        kroki = e.get("instructionsPl")
        if (not isinstance(kroki, list) or not 1 <= len(kroki) <= KROKI_MAX
                or not all(isinstance(k, str) and k.strip() and len(k) <= KROK_MAX for k in kroki)):
            bledy.append(f"{ex_id}: instructionsPl to od 1 do {KROKI_MAX} niepustych kroków po {KROK_MAX} znaków")

        for pole, enum in ENUM_POLE.items():
            if e.get(pole) not in znane.get(enum, set()):
                bledy.append(f"{ex_id}: {pole} {e.get(pole)!r} spoza {enum}")
        for pole, enum in ENUM_LISTA.items():
            wartosci = e.get(pole, [])
            if not isinstance(wartosci, list):
                bledy.append(f"{ex_id}: {pole} musi być listą")
                continue
            for w in wartosci:
                if w not in znane.get(enum, set()):
                    bledy.append(f"{ex_id}: {pole} {w!r} spoza {enum}")
            if len(set(wartosci)) != len(wartosci):
                bledy.append(f"{ex_id}: {pole} ma powtórzenia")

        for pole, (od, do) in (("defaultReps", POWTORZENIA), ("defaultSeconds", SEKUNDY)):
            wartosc = e.get(pole)
            if wartosc is not None and (not isinstance(wartosc, int) or isinstance(wartosc, bool) or not od <= wartosc <= do):
                bledy.append(f"{ex_id}: {pole} {wartosc!r} poza zakresem {od}..{do}")

        wycofane = e.get("retiredAt")
        if wycofane is not None:
            try:
                if not isinstance(wycofane, str) or not DATA.fullmatch(wycofane):
                    raise ValueError
                if datetime.date.fromisoformat(wycofane) > datetime.date.today():
                    bledy.append(f"{ex_id}: retiredAt {wycofane} z przyszłości")
            except ValueError:
                bledy.append(f"{ex_id}: retiredAt {wycofane!r} to nie data RRRR-MM-DD")

        if e.get("images") != [f"images/{ex_id}.jpg"]:
            bledy.append(f"{ex_id}: images ma być [\"images/{ex_id}.jpg\"]")
        dane_obrazu = obraz(ex_id)
        if dane_obrazu is None:
            bledy.append(f"{ex_id}: brak ilustracji images/{ex_id}.jpg")
        else:
            wymiary = jpeg_wymiary(dane_obrazu)
            if wymiary is None:
                bledy.append(f"{ex_id}: images/{ex_id}.jpg to nie JPEG")
            elif wymiary[0] > OBRAZ_SZEROKOSC or wymiary[1] > OBRAZ_WYSOKOSC_MAX:
                bledy.append(f"{ex_id}: ilustracja {wymiary[0]}x{wymiary[1]}, a szerokość to najwyżej {OBRAZ_SZEROKOSC} px")
            if len(dane_obrazu) > OBRAZ_MAX_BAJTOW:
                bledy.append(f"{ex_id}: ilustracja ma {len(dane_obrazu) // 1000} kB, limit {OBRAZ_MAX_BAJTOW // 1000} kB")

    for ex_id, e in po_id.items():
        for pole in ("easierId", "harderId"):
            cel = e.get(pole)
            if cel is None:
                continue
            if cel == ex_id:
                bledy.append(f"{ex_id}.{pole} wskazuje samo siebie")
            elif cel not in po_id:
                bledy.append(f"{ex_id}.{pole}: brak ćwiczenia {cel}")
            elif po_id[cel].get("kind") != e.get("kind"):
                # Podmiana wariantu przenosi cel: sekundy w miejsce powtórzeń dałyby 30 powtórzeń zamiast 30 s.
                bledy.append(f"{ex_id}.{pole} → {cel}: inny rodzaj celu ({e.get('kind')} i {po_id[cel].get('kind')})")
            elif po_id[cel].get("retiredAt") and not e.get("retiredAt"):
                bledy.append(f"{ex_id}.{pole} → {cel}: wariant jest wycofany")
    return bledy


def sprawdz_katalog():
    """W baza/ są wyłącznie dane: Gradle podpina katalog w całości jako assety, wszystko inne pojechałoby do APK."""
    if not os.path.isdir(BAZA):
        return ["brak katalogu baza/"]
    bledy = []
    zawartosc = set(os.listdir(BAZA))
    if zawartosc != {"exercises.json", "images"}:
        bledy.append(f"baza/: oczekiwane tylko exercises.json i images/, jest {sorted(zawartosc)}")
    if os.path.isdir(OBRAZY):
        for nazwa in sorted(os.listdir(OBRAZY)):
            if not nazwa.endswith(".jpg") or not os.path.isfile(os.path.join(OBRAZY, nazwa)):
                bledy.append(f"baza/images/{nazwa}: w images/ leżą tylko pliki <id>.jpg")
    return bledy


def osierocone(dane):
    ids = {e.get("id") for e in dane.get("exercises", []) if isinstance(e, dict)}
    if not os.path.isdir(OBRAZY):
        return []
    return [f"baza/images/{n}: obraz bez ćwiczenia" for n in sorted(os.listdir(OBRAZY))
            if n.endswith(".jpg") and n[:-4] not in ids]


def obraz_z_dysku(ex_id, katalog=OBRAZY):
    sciezka = os.path.join(katalog, f"{ex_id}.jpg")
    if not os.path.isfile(sciezka):
        return None
    with open(sciezka, "rb") as f:
        return f.read()


def baza_z_refu(ref):
    """(dane, skróty obrazów id → blob) z REF-u, w nowym albo w dawnym układzie; None, gdy REF bazy nie ma."""
    tekst = git("show", f"{ref}:baza/exercises.json")
    if tekst is not None:
        drzewo = git("ls-tree", "-r", ref, "--", "baza/images") or b""
        wzor = re.compile(r"baza/images/([^/]+)\.jpg")
    else:
        tekst = git("show", f"{ref}:{STARY_PLIK}")
        if tekst is None:
            return None
        drzewo = git("ls-tree", "-r", ref, "--", STARE_OBRAZY) or b""
        wzor = re.compile(re.escape(STARE_OBRAZY) + r"/([^/]+)/0\.jpg")
    skroty = {}
    for linia in drzewo.decode("utf-8").splitlines():
        meta, _, sciezka = linia.partition("\t")
        m = wzor.fullmatch(sciezka)
        if m:
            skroty[m.group(1)] = meta.split()[2]
    return json.loads(tekst.decode("utf-8")), skroty


def roznice(stara, nowa, stare_obrazy):
    """Ćwiczenia nowe i zmienione względem starej bazy (treść albo ilustracja)."""
    stare = {e["id"]: e for e in stara["exercises"]}
    dodane, zmienione = [], []
    bez_obrazow = lambda x: {k: v for k, v in x.items() if k != "images"}
    for e in nowa["exercises"]:
        poprzednie = stare.get(e["id"])
        if poprzednie is None:
            dodane.append(e["id"])
            continue
        obraz = obraz_z_dysku(e["id"])
        if bez_obrazow(poprzednie) != bez_obrazow(e) or (obraz is not None and stare_obrazy.get(e["id"]) != blob(obraz)):
            zmienione.append(e["id"])
    return dodane, zmienione


def sprawdz_wzgledem(dane, ref, z_refu=None):
    """Zgodność wstecz z D-47 względem bazy z REF-u. Zwraca (błędy, uwagi)."""
    z_refu = z_refu or baza_z_refu(ref)
    if z_refu is None:
        return [], [f"{ref}: brak bazy do porównania, pomijam reguły zgodności wstecz"]
    stara, stare_obrazy = z_refu
    bledy = []
    stare = {e["id"]: e for e in stara["exercises"]}
    nowe = {e["id"]: e for e in dane["exercises"]}
    for ex_id in sorted(stare.keys() - nowe.keys()):
        bledy.append(f"{ex_id}: ćwiczenie z {ref} zniknęło; usunięcie to wycofanie, id zostaje (historia, plany, programy)")
    for ex_id in sorted(stare.keys() & nowe.keys()):
        if stare[ex_id].get("kind") != nowe[ex_id].get("kind"):
            bledy.append(f"{ex_id}: kind {stare[ex_id].get('kind')} → {nowe[ex_id].get('kind')}; rodzaj celu siedzi w zapisanych blokach")
    dodane, zmienione = roznice(stara, dane, stare_obrazy)
    # Treścią jest wszystko poza samym numerem: ćwiczenia, ilustracje, ale też format i minApp, bo idą do manifestu.
    bez_numeru = lambda baza: {k: v for k, v in baza.items() if k != "version"}
    tresc_inna = bool(dodane or zmienione) or bez_numeru(stara) != bez_numeru(dane)
    if dane["version"] < stara["version"]:
        bledy.append(f"version {dane['version']} niższe niż v{stara['version']} w {ref}")
    elif tresc_inna and dane["version"] == stara["version"]:
        bledy.append(f"treść bazy różni się od {ref}, a numer został v{dane['version']}: python tools/baza.py podbij")
    elif dane["version"] > stara["version"]:
        # Nowy numer względem REF-u to nowe wydanie bazy, a wydanie bez opisu zmian nie wychodzi.
        opis = opis_bazy(dane["version"])
        if opis is None:
            bledy.append(f"CHANGELOG.md: brak sekcji „## [Baza {dane['version']}]” z opisem tego wydania bazy "
                         "(nagłówek dopisuje python tools/baza.py podbij)")
        elif not opis:
            bledy.append(f"CHANGELOG.md: sekcja „## [Baza {dane['version']}]” jest pusta; opisz, co zmienia to wydanie bazy")
    uwagi = [f"względem {ref} (v{stara['version']}): {len(dodane)} nowych, {len(zmienione)} zmienionych"]
    return bledy, uwagi


def sprawdz_ostatnie_wydanie(dane):
    """Wartość enuma, której nie zna ostatnie wydanie aplikacji, wymaga minApp wyższego od niego (D-47)."""
    tagi = (git("tag", "-l", "v*") or b"").decode("utf-8").split()
    wydane = sorted((t[1:] for t in tagi if NUMER.fullmatch(t[1:])), key=krotka)
    if not wydane:
        return [], ["brak tagów v*: pomijam porównanie enumów z ostatnim wydaniem"]
    ostatnie = wydane[-1]
    tekst = git("show", f"v{ostatnie}:{ENUMS}")
    if tekst is None:
        return [], [f"nie odczytałem Enums.kt z v{ostatnie}: pomijam porównanie enumów"]
    stare_enumy = enumy(tekst.decode("utf-8"))
    nowe_wartosci = set()
    for e in dane["exercises"]:
        for pole, enum in ENUM_POLE.items():
            if e.get(pole) not in stare_enumy.get(enum, set()):
                nowe_wartosci.add(f"{enum}.{e.get(pole)} ({e['id']})")
        for pole, enum in ENUM_LISTA.items():
            for w in e.get(pole, []):
                if w not in stare_enumy.get(enum, set()):
                    nowe_wartosci.add(f"{enum}.{w} ({e['id']})")
    if nowe_wartosci and krotka(dane["minApp"]) <= krotka(ostatnie):
        return [f"wartości spoza wydanego v{ostatnie}: {', '.join(sorted(nowe_wartosci))}; "
                f"ustaw minApp wyżej niż {ostatnie} (python tools/baza.py podbij --min-app …)"], []
    return [], []


def stan(dane, ref=None, z_refu=None):
    """Wszystkie reguły naraz: (błędy, uwagi). To samo liczy `sprawdz` i pasek stanu w edytorze."""
    bledy = sprawdz_katalog() + sprawdz_dane(dane, znane_enumy(), obraz_z_dysku) + osierocone(dane) + sprawdz_version_md(dane)
    uwagi = []
    if not bledy:
        b, u = sprawdz_ostatnie_wydanie(dane)
        bledy += b
        uwagi += u
        if ref:
            b, u = sprawdz_wzgledem(dane, ref, z_refu)
            bledy += b
            uwagi += u
    return bledy, uwagi


def programy_ids(ids):
    """Ćwiczenia, które wskazują gotowe programy: napisy z PresetDefinitions.kt równe id z bazy. Ze zbiorem
    nadmiarowym jest bezpieczniej: wycofania programu i tak pilnuje dopiero PresetSeedConsistencyTest."""
    try:
        with open(os.path.join(ROOT, PROGRAMY), encoding="utf-8") as f:
            napisy = set(re.findall(r'"([a-z0-9]+(?:-[a-z0-9]+)*)"', f.read()))
    except OSError:
        return set()
    return napisy & set(ids)


def ref_domyslny():
    """Bazę porównujemy z `origin/master`: stamtąd wychodzą wydania bazy i aplikacji. Bez niego z HEAD."""
    return "origin/master" if git("rev-parse", "--verify", "-q", "origin/master") is not None else "HEAD"


# --- ilustracje i szkice -------------------------------------------------------------------------------------

def dopasuj_obraz(dane):
    """Ilustracja w formacie bazy: JPEG do 480 px szerokości. Plik z generatora (480 px, q72) przechodzi bez zmian,
    resztę skaluje i koduje Pillow."""
    wymiary = jpeg_wymiary(dane)
    if wymiary is not None and wymiary[0] <= OBRAZ_SZEROKOSC:
        return dane
    try:
        from PIL import Image  # pip install pillow
    except ImportError:
        raise ValueError("ilustracja nie jest JPEG-iem do 480 px szerokości; do przeskalowania potrzebny jest Pillow (pip install pillow)")
    try:
        obraz = Image.open(io.BytesIO(dane))
        obraz.load()
    except Exception:
        raise ValueError("plik nie jest obrazem")
    obraz = obraz.convert("RGB")
    if obraz.width > OBRAZ_SZEROKOSC:
        wysokosc = round(obraz.height * OBRAZ_SZEROKOSC / obraz.width)
        obraz = obraz.resize((OBRAZ_SZEROKOSC, wysokosc), Image.LANCZOS)
    wynik = io.BytesIO()
    obraz.save(wynik, "JPEG", quality=JAKOSC, optimize=True, progressive=True)
    return wynik.getvalue()


def wczytaj_szkice():
    """Definicje czekające na ilustrację: lista rekordów w układzie ćwiczenia, bez `images`."""
    if not os.path.isfile(SZKICE_PLIK):
        return []
    with open(SZKICE_PLIK, encoding="utf-8") as f:
        return json.load(f).get("exercises", [])


def zapisz_szkice(lista):
    os.makedirs(SZKICE, exist_ok=True)
    if not lista:
        if os.path.isfile(SZKICE_PLIK):
            os.remove(SZKICE_PLIK)
        return
    zapisz_plik(SZKICE_PLIK, json_tekst({"exercises": lista}))


def usun_szkic(ex_id):
    zapisz_szkice([s for s in wczytaj_szkice() if s.get("id") != ex_id])
    obraz = os.path.join(SZKICE, f"{ex_id}.jpg")
    if os.path.isfile(obraz):
        os.remove(obraz)
    if os.path.isdir(SZKICE) and not os.listdir(SZKICE):
        os.rmdir(SZKICE)


def wczytaj_partie(zawartosc):
    """Paczka z generatora zdjęć: ilustracje ćwiczeń z bazy podmienia, resztę odkłada do szkiców.
    Zwraca (podmienione, do_szkicow, pominiete)."""
    znane = {e["id"] for e in wczytaj()["exercises"]}
    podmienione, do_szkicow, pominiete = [], [], []
    with zipfile.ZipFile(io.BytesIO(zawartosc)) as z:
        try:
            manifest = json.loads(z.read("manifest.json").decode("utf-8"))
        except KeyError:
            raise ValueError("w paczce nie ma manifest.json, to nie jest paczka z generatora zdjęć")
        if manifest.get("format") != "mysttic-trainings-zdjecia":
            raise ValueError("to nie jest paczka z generatora zdjęć")
        for ex_id in manifest.get("id", []):
            if not isinstance(ex_id, str) or not ID.fullmatch(ex_id) or ex_id.startswith(CUSTOM_PREFIX):
                pominiete.append(str(ex_id))
                continue
            dane = dopasuj_obraz(z.read(f"{ex_id}.jpg"))
            if ex_id in znane:
                if obraz_z_dysku(ex_id) == dane:
                    continue  # ta sama ilustracja drugi raz: treść bazy się nie zmienia
                zapisz_plik(os.path.join(OBRAZY, f"{ex_id}.jpg"), dane)
                podmienione.append(ex_id)
            else:
                os.makedirs(SZKICE, exist_ok=True)
                zapisz_plik(os.path.join(SZKICE, f"{ex_id}.jpg"), dane)
                do_szkicow.append(ex_id)
    return podmienione, do_szkicow, pominiete


def normalizuj(rekord):
    """Rekord z formularza w układzie exercises.json: kolejność kluczy, typy, puste pola jako null."""
    if not isinstance(rekord, dict):
        raise ValueError("oczekiwany obiekt ćwiczenia")
    tekst = lambda k: (rekord.get(k) or "").strip() if isinstance(rekord.get(k), str) else rekord.get(k)
    lista = lambda k: [x for x in (rekord.get(k) or []) if isinstance(x, str) and x.strip()]
    ex_id = tekst("id") or ""
    wynik = {
        "id": ex_id,
        "sourceId": tekst("sourceId") or None,
        "nameEn": tekst("nameEn") or "",
        "namePl": tekst("namePl") or "",
        "kind": rekord.get("kind"),
        "level": rekord.get("level"),
        "category": rekord.get("category"),
        "equipment": lista("equipment"),
        "primaryMuscles": lista("primaryMuscles"),
        "secondaryMuscles": lista("secondaryMuscles"),
        "instructionsPl": [k.strip() for k in lista("instructionsPl")],
        "images": [f"images/{ex_id}.jpg"],
        "easierId": tekst("easierId") or None,
        "harderId": tekst("harderId") or None,
        "defaultReps": rekord.get("defaultReps"),
        "defaultSeconds": rekord.get("defaultSeconds"),
    }
    for pole in ("defaultReps", "defaultSeconds"):
        try:
            wynik[pole] = int(wynik[pole])
        except (TypeError, ValueError):
            pass  # walidator powie, co jest nie tak
    if rekord.get("retiredAt"):
        wynik["retiredAt"] = rekord["retiredAt"]
    return wynik


def dekoduj_obraz(tekst):
    """Obraz z formularza: data URL albo czyste base64; None, gdy nie przyszedł."""
    if not tekst:
        return None
    if tekst.startswith("data:"):
        tekst = tekst.split(",", 1)[1] if "," in tekst else ""
    try:
        return base64.b64decode(tekst, validate=True)
    except ValueError:
        raise ValueError("nie odczytałem przesłanego obrazu")


# --- polecenia -----------------------------------------------------------------------------------------------

def cmd_sprawdz(args):
    dane = wczytaj()
    bledy, uwagi = stan(dane, args.wzgledem)
    for u in uwagi:
        print(f"Uwaga: {u}")
    for b in bledy:
        print(f"BŁĄD: {b}")
    if bledy:
        sys.exit(f"Baza nie przeszła: {len(bledy)} błędów")
    print(f"OK: baza v{dane['version']}, format {dane['format']}, minApp {dane['minApp']}, {len(dane['exercises'])} ćwiczeń")


def podbij(min_app=None):
    """Numer bazy +1 w baza/exercises.json i VERSION.md, z pustym nagłówkiem „## [Baza N]” w CHANGELOG.md.
    Numer, który jeszcze nie wyszedł (wyższy niż w `origin/master`), zostaje: kolejne zmiany wchodzą do niego
    bez podbijania, a `min_app` zmienia wtedy samo minApp."""
    dane = wczytaj()
    stary = dane["version"]
    ref = ref_domyslny()
    z_refu = baza_z_refu(ref)
    czeka = z_refu is not None and stary > z_refu[0]["version"]
    if czeka and not min_app:
        raise ValueError(f"v{stary} jeszcze nie wyszła ({ref} ma v{z_refu[0]['version']}): kolejne zmiany wejdą do niej "
                         "bez podbijania")
    if numer_bazy_w_version_md() is None:
        raise ValueError(f"VERSION.md: pod nagłówkiem „{NAGLOWEK_BAZY}” brak samego numeru bazy")
    if min_app:
        if not NUMER.fullmatch(min_app):
            raise ValueError(f"minApp {min_app}: oczekiwany numer 1.2.3")
        if krotka(min_app) > krotka(wersja_aplikacji()):
            raise ValueError(f"minApp {min_app} wyższe niż VERSION.md ({wersja_aplikacji()}): najpierw podnieś wersję aplikacji")
        dane["minApp"] = min_app
    dane["version"] = stary if czeka else stary + 1
    zapisz(dane)
    zapisz_numer_bazy(dane["version"])
    dopisz_naglowek_bazy(dane["version"])
    return stary, dane


def cmd_podbij(args):
    try:
        stary, dane = podbij(args.min_app)
    except ValueError as e:
        sys.exit(str(e))
    print(f"Baza v{stary} -> v{dane['version']}, minApp {dane['minApp']} (baza/exercises.json i VERSION.md)")
    if not opis_bazy(dane["version"]):
        print(f"Zostaje opis wydania: sekcja „## [Baza {dane['version']}]” w CHANGELOG.md")


def cmd_partia(args):
    with open(args.paczka, "rb") as f:
        zawartosc = f.read()
    try:
        podmienione, do_szkicow, pominiete = wczytaj_partie(zawartosc)
    except ValueError as e:
        sys.exit(f"{args.paczka}: {e}")
    print(f"Paczka {os.path.basename(args.paczka)}: {len(podmienione)} nowych ilustracji w bazie, {len(do_szkicow)} w szkicach")
    if pominiete:
        print("Pominięte (id poza formatem bazy): " + ", ".join(pominiete))
    if podmienione:
        print("Zmieniła się treść bazy, więc przed wydaniem: python tools/baza.py podbij")
    if do_szkicow:
        print("Czekają na definicję w tools/szkice/ (bez niej nie wejdą do bazy): " + ", ".join(do_szkicow))


# --- wydanie bazy --------------------------------------------------------------------------------------------

PROJEKT = "mysttic-trainings"
STRONA = "https://mysttic.github.io/Mysttic/mysttic-trainings/"


def nazwa_pliku(numer, rozszerzenie):
    """Umowa wydania bazy (D-47): czyta ją aplikacja, więc zmiana tutaj = zmiana w aplikacji."""
    return f"{PROJEKT}-baza-{numer}.{rozszerzenie}"


def odmiana(n, jeden, kilka, wiele):
    if n == 1:
        return jeden
    if n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14):
        return kilka
    return wiele


def zip_bazy(sciezka):
    """ZIP z exercises.json i images/ w stałej kolejności i ze stałą datą, żeby ta sama treść dała ten sam plik."""
    def dodaj(z, nazwa, tresc, kompresja):
        info = zipfile.ZipInfo(nazwa, date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = kompresja
        info.external_attr = 0o644 << 16
        z.writestr(info, tresc)

    with zipfile.ZipFile(sciezka, "w") as z:
        with open(PLIK, "rb") as f:
            dodaj(z, "exercises.json", f.read(), zipfile.ZIP_DEFLATED)
        for nazwa in sorted(os.listdir(OBRAZY)):
            # JPEG jest już skompresowany: STORED oszczędza czas i nie zmienia rozmiaru.
            with open(os.path.join(OBRAZY, nazwa), "rb") as f:
                dodaj(z, f"images/{nazwa}", f.read(), zipfile.ZIP_STORED)


def paczka(katalog, ref=None):
    """Pliki wydania bazy: ZIP, manifest (czyta go aplikacja przed pobraniem ZIP-a) i opis wydania.
    Liczby nowych, zmienionych i wycofanych są względem REF-u: poprzedniego wydania bazy albo aplikacji."""
    dane = wczytaj()
    bledy, _ = stan(dane)
    if bledy:
        raise ValueError("baza nie przechodzi reguł: " + "; ".join(bledy[:5]))
    numer = dane["version"]
    opis_zmian = opis_bazy(numer)
    if not opis_zmian:
        raise ValueError(f"CHANGELOG.md: brak opisu wydania bazy (sekcja „## [Baza {numer}]”)")
    os.makedirs(katalog, exist_ok=True)
    sciezka_zip = os.path.join(katalog, nazwa_pliku(numer, "zip"))
    zip_bazy(sciezka_zip)
    with open(sciezka_zip, "rb") as f:
        zawartosc = f.read()

    dodane = zmienione = wycofane = None
    z_refu = baza_z_refu(ref) if ref else None
    if z_refu:
        dodane, zmienione = roznice(z_refu[0], dane, z_refu[1])
        stare = {e["id"]: e for e in z_refu[0]["exercises"]}
        wycofane = [e["id"] for e in dane["exercises"] if e.get("retiredAt") and not stare.get(e["id"], {}).get("retiredAt")]
        # Wycofanie to też zmiana pola, ale w opisie liczy się osobno.
        zmienione = [i for i in zmienione if i not in wycofane]
    manifest = {
        "format": dane["format"],
        "version": numer,
        "minApp": dane["minApp"],
        "exercises": len(dane["exercises"]),
        "added": len(dodane) if dodane is not None else None,
        "changed": len(zmienione) if zmienione is not None else None,
        "retired": len(wycofane) if wycofane is not None else None,
        "file": os.path.basename(sciezka_zip),
        "size": len(zawartosc),
        "sha256": hashlib.sha256(zawartosc).hexdigest(),
    }
    zapisz_plik(os.path.join(katalog, nazwa_pliku(numer, "json")), json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")

    if dodane is None:
        zmiany = f"{len(dane['exercises'])} {odmiana(len(dane['exercises']), 'ćwiczenie', 'ćwiczenia', 'ćwiczeń')}."
    else:
        czesci = [
            f"{len(dodane)} {odmiana(len(dodane), 'nowe ćwiczenie', 'nowe ćwiczenia', 'nowych ćwiczeń')}",
            f"{len(zmienione)} {odmiana(len(zmienione), 'zmienione', 'zmienione', 'zmienionych')}",
            f"{len(wycofane)} {odmiana(len(wycofane), 'wycofane', 'wycofane', 'wycofanych')}",
        ]
        zmiany = ", ".join(czesci) + f" (razem {len(dane['exercises'])})."
    opis = (f"Baza ćwiczeń {numer}\n\n{opis_zmian}\n\n{zmiany}\n"
            f"Wymaga aplikacji {dane['minApp']} albo nowszej.\n\n{STRONA}\n")
    zapisz_plik(os.path.join(katalog, "opis.md"), opis)
    return manifest, opis


def cmd_paczka(args):
    try:
        manifest, opis = paczka(args.katalog, args.wzgledem)
    except ValueError as e:
        sys.exit(str(e))
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    print(opis)


# --- edytor --------------------------------------------------------------------------------------------------

def dane_strony():
    """Dane kartoteki (tools/przeglad.py) z trybem edycji: stan reguł, ćwiczenia z REF-u, programy, szkice."""
    import przeglad  # tools/ jest na ścieżce, bo stąd uruchamia się skrypt

    dane = wczytaj()
    ref = ref_domyslny()
    z_refu = baza_z_refu(ref)
    bledy, uwagi = stan(dane, ref, z_refu)

    def adres(wzgledna, sciezka):
        with open(sciezka, "rb") as f:
            return f"/{wzgledna}?v={blob(f.read())[:10]}"

    strona = przeglad.build(embed=False, seed=dane, obraz_url=adres, surowe=True)
    ids = {e["id"] for e in dane["exercises"]}
    definicje = {s["id"]: s for s in wczytaj_szkice() if isinstance(s, dict) and s.get("id")}
    obrazy = {n[:-4] for n in os.listdir(SZKICE) if n.endswith(".jpg")} if os.path.isdir(SZKICE) else set()
    szkice = []
    for ex_id in sorted(definicje.keys() | obrazy):
        sciezka = os.path.join(SZKICE, f"{ex_id}.jpg")
        szkice.append(dict(
            id=ex_id,
            cwiczenie=definicje.get(ex_id),
            obraz=adres(f"szkice/{ex_id}.jpg", sciezka) if ex_id in obrazy else None,
            wBazie=ex_id in ids,
        ))
    try:
        import PIL  # noqa: F401
        pillow = True
    except ImportError:
        pillow = False
    strona["edit"] = dict(
        ref=ref,
        bledy=bledy,
        uwagi=uwagi,
        wersja=dane["version"],
        wydana=z_refu[0]["version"] if z_refu else None,
        format=dane["format"],
        minApp=dane["minApp"],
        aplikacja=wersja_aplikacji(),
        wydane={e["id"]: e.get("kind") for e in z_refu[0]["exercises"]} if z_refu else {},
        programy=sorted(programy_ids(ids)),
        szkice=szkice,
        pillow=pillow,
        dzis=dzis(),
    )
    return strona


class Odmowa(Exception):
    """Zapis odrzucony z listą powodów dla formularza."""

    def __init__(self, *bledy):
        super().__init__("; ".join(bledy))
        self.bledy = list(bledy)


def zapisz_cwiczenie(cialo):
    """Nowe albo poprawione ćwiczenie z formularza. Zapisuje dopiero, gdy cała baza przechodzi reguły."""
    e = normalizuj(cialo.get("cwiczenie"))
    oryginal = cialo.get("oryginal")
    z_szkicu = cialo.get("zSzkicu")
    nowy_obraz = dekoduj_obraz(cialo.get("obraz"))
    if nowy_obraz is not None:
        nowy_obraz = dopasuj_obraz(nowy_obraz)
    elif z_szkicu:
        nowy_obraz = obraz_z_dysku(z_szkicu, SZKICE)

    dane = wczytaj()
    pozycje = {x["id"]: i for i, x in enumerate(dane["exercises"])}
    if oryginal:
        if oryginal not in pozycje:
            raise Odmowa(f"nie ma ćwiczenia {oryginal}")
        if e["id"] != oryginal:
            raise Odmowa("id istniejącego ćwiczenia się nie zmienia (historia i plany wskazują po id)")
        poprzednie = dane["exercises"][pozycje[oryginal]]
        if poprzednie.get("retiredAt"):
            e["retiredAt"] = poprzednie["retiredAt"]  # wycofanie i przywrócenie mają osobny przycisk
        else:
            e.pop("retiredAt", None)
        dane["exercises"][pozycje[oryginal]] = e
    else:
        e.pop("retiredAt", None)
        if e["id"] in pozycje:
            raise Odmowa(f"ćwiczenie {e['id']} już jest w bazie")
        if nowy_obraz is None:
            raise Odmowa("nowe ćwiczenie wchodzi do bazy tylko z ilustracją; bez niej zapisz szkic")
        dane["exercises"].append(e)

    obraz = lambda i: nowy_obraz if i == e["id"] and nowy_obraz is not None else obraz_z_dysku(i)
    bledy = sprawdz_dane(dane, znane_enumy(), obraz)
    ref = ref_domyslny()
    z_refu = baza_z_refu(ref)
    if z_refu:
        poprzednie = {x["id"]: x for x in z_refu[0]["exercises"]}.get(e["id"])
        if poprzednie and poprzednie.get("kind") != e["kind"]:
            bledy.append(f"{e['id']}: rodzaj celu wydanego ćwiczenia się nie zmienia (jest w {ref} jako {poprzednie.get('kind')})")
    if bledy:
        raise Odmowa(*bledy)

    if nowy_obraz is not None:
        zapisz_plik(os.path.join(OBRAZY, f"{e['id']}.jpg"), nowy_obraz)
    zapisz(dane)
    if z_szkicu:
        usun_szkic(z_szkicu)
    return {"id": e["id"]}


def zapisz_szkic(cialo):
    """Definicja bez ilustracji (albo z ilustracją, której nie chcemy jeszcze w bazie) do tools/szkice/."""
    e = normalizuj(cialo.get("cwiczenie"))
    e.pop("images", None)
    e.pop("retiredAt", None)
    z_szkicu = cialo.get("zSzkicu")
    if not ID.fullmatch(e["id"]) or len(e["id"]) > ID_MAX or e["id"].startswith(CUSTOM_PREFIX):
        raise Odmowa("szkic też potrzebuje id w formacie kebab-case (bez prefiksu custom-)")
    if e["id"] in {x["id"] for x in wczytaj()["exercises"]}:
        raise Odmowa(f"ćwiczenie {e['id']} już jest w bazie")
    obraz = dekoduj_obraz(cialo.get("obraz"))
    if obraz is not None:
        obraz = dopasuj_obraz(obraz)
    if z_szkicu and z_szkicu != e["id"]:
        stary_obraz = obraz_z_dysku(z_szkicu, SZKICE)
        usun_szkic(z_szkicu)
        if obraz is None:
            obraz = stary_obraz
    szkice = [s for s in wczytaj_szkice() if s.get("id") != e["id"]] + [e]
    zapisz_szkice(szkice)
    if obraz is not None:
        os.makedirs(SZKICE, exist_ok=True)
        zapisz_plik(os.path.join(SZKICE, f"{e['id']}.jpg"), obraz)
    return {"id": e["id"]}


def wycofaj(cialo):
    """Wycofanie z dzisiejszą datą albo przywrócenie. Wycofanego nie wskazuje wariant ani gotowy program."""
    ex_id = cialo.get("id")
    dane = wczytaj()
    po_id = {e["id"]: e for e in dane["exercises"]}
    if ex_id not in po_id:
        raise Odmowa(f"nie ma ćwiczenia {ex_id}")
    if cialo.get("wycofane"):
        wskazuja = sorted(e["id"] for e in dane["exercises"]
                          if not e.get("retiredAt") and ex_id in (e.get("easierId"), e.get("harderId")))
        if wskazuja:
            raise Odmowa(f"jest wariantem ćwiczeń {', '.join(wskazuja)}: najpierw zmień tam łatwiejszy albo trudniejszy wariant")
        if ex_id in programy_ids({ex_id}):
            raise Odmowa("używa go gotowy program (PresetDefinitions.kt): wycofanie wymaga zmiany programu i nowej wersji aplikacji")
        po_id[ex_id]["retiredAt"] = dzis()
    else:
        po_id[ex_id].pop("retiredAt", None)
    bledy = sprawdz_dane(dane, znane_enumy(), obraz_z_dysku)
    if bledy:
        raise Odmowa(*bledy)
    zapisz(dane)
    return {"id": ex_id, "retiredAt": po_id[ex_id].get("retiredAt")}


class Edytor(http.server.BaseHTTPRequestHandler):
    server_version = "baza-edytor"
    LIMIT = 30_000_000  # paczka z generatora ma kilka MB, obraz z formularza dużo mniej

    def log_message(self, format, *args):  # noqa: A002 (sygnatura z BaseHTTPRequestHandler)
        pass

    def _host_ok(self):
        # Ochrona przed DNS rebinding: odpowiadamy tylko na adres lokalny.
        host = (self.headers.get("Host") or "").rsplit(":", 1)[0]
        return host in ("127.0.0.1", "localhost")

    def _wyslij(self, kod, typ, tresc, cache=False):
        self.send_response(kod)
        self.send_header("Content-Type", typ)
        self.send_header("Content-Length", str(len(tresc)))
        self.send_header("Cache-Control", "max-age=31536000, immutable" if cache else "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(tresc)

    def _json(self, kod, dane):
        self._wyslij(kod, "application/json; charset=utf-8", json.dumps(dane, ensure_ascii=False).encode("utf-8"))

    def do_GET(self):
        if not self._host_ok():
            return self._json(403, {"bledy": ["tylko adres lokalny"]})
        sciezka = urllib.parse.urlsplit(self.path).path
        if sciezka in ("/", "/index.html"):
            import przeglad
            html = przeglad.strona(dane_strony(), tytul="Baza ćwiczeń Mysttic")
            return self._wyslij(200, "text/html; charset=utf-8", html.encode("utf-8"))
        if sciezka == "/api/dane":
            return self._json(200, dane_strony())
        m = re.fullmatch(r"/(images|szkice)/([a-z0-9]+(?:-[a-z0-9]+)*)\.jpg", sciezka)
        if m:
            dane = obraz_z_dysku(m.group(2), OBRAZY if m.group(1) == "images" else SZKICE)
            if dane is not None:
                return self._wyslij(200, "image/jpeg", dane, cache=True)
        self._json(404, {"bledy": ["nie ma takiej strony"]})

    def do_POST(self):
        # Zapis tylko z własnej strony: inna karta przeglądarki nie wyśle nagłówka X-Baza bez zgody serwera (CORS).
        pochodzenie = self.headers.get("Origin")
        if (not self._host_ok() or self.headers.get("X-Baza") != "1"
                or (pochodzenie and urllib.parse.urlsplit(pochodzenie).hostname not in ("127.0.0.1", "localhost"))):
            return self._json(403, {"bledy": ["zapis tylko z edytora bazy"]})
        dlugosc = int(self.headers.get("Content-Length") or 0)
        if dlugosc > self.LIMIT:
            return self._json(413, {"bledy": ["za duże żądanie"]})
        try:
            cialo = json.loads(self.rfile.read(dlugosc).decode("utf-8") or "{}")
        except (UnicodeDecodeError, json.JSONDecodeError):
            return self._json(400, {"bledy": ["żądanie to nie JSON"]})
        trasy = {
            "/api/zapisz": zapisz_cwiczenie,
            "/api/szkic": zapisz_szkic,
            "/api/szkic/usun": lambda c: (usun_szkic(str(c.get("id"))), {"id": c.get("id")})[1],
            "/api/wycofaj": wycofaj,
            "/api/podbij": lambda c: {"version": podbij((c.get("minApp") or "").strip() or None)[1]["version"]},
            "/api/partia": lambda c: dict(zip(("podmienione", "doSzkicow", "pominiete"),
                                              wczytaj_partie(dekoduj_obraz(c.get("zip")) or b""))),
        }
        trasa = trasy.get(urllib.parse.urlsplit(self.path).path)
        if trasa is None:
            return self._json(404, {"bledy": ["nie ma takiej operacji"]})
        with BLOKADA:
            try:
                wynik = trasa(cialo)
            except Odmowa as e:
                return self._json(400, {"bledy": e.bledy})
            except (ValueError, zipfile.BadZipFile) as e:
                return self._json(400, {"bledy": [str(e)]})
        self._json(200, dict(ok=True, **wynik))


def cmd_edytor(args):
    serwer = http.server.ThreadingHTTPServer(("127.0.0.1", args.port), Edytor)
    adres = f"http://127.0.0.1:{serwer.server_port}/"
    print(f"Edytor bazy: {adres} (Ctrl+C kończy). Zmiany lądują w baza/ i tools/szkice/, commit robisz sam.")
    if not args.bez_otwierania:
        webbrowser.open(adres)
    try:
        serwer.serve_forever()
    except KeyboardInterrupt:
        print("Koniec.")


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description="Baza ćwiczeń (D-47)")
    sub = ap.add_subparsers(dest="polecenie")
    p = sub.add_parser("edytor", help="edytor bazy w przeglądarce (domyślne polecenie)")
    p.add_argument("--port", type=int, default=8770)
    p.add_argument("--bez-otwierania", action="store_true", help="nie otwieraj przeglądarki")
    p.set_defaults(cmd=cmd_edytor)
    p = sub.add_parser("sprawdz", help="reguły bazy, opcjonalnie zgodność wstecz względem REF")
    p.add_argument("--wzgledem", metavar="REF", help="commit, gałąź albo tag z bazą do porównania")
    p.set_defaults(cmd=cmd_sprawdz)
    p = sub.add_parser("podbij", help="numer bazy +1")
    p.add_argument("--min-app", metavar="X.Y.Z", help="najniższa wersja aplikacji, która przyjmie bazę")
    p.set_defaults(cmd=cmd_podbij)
    p = sub.add_parser("partia", help="paczka ZIP z generatora zdjęć")
    p.add_argument("paczka", metavar="ZIP")
    p.set_defaults(cmd=cmd_partia)
    p = sub.add_parser("paczka", help="pliki wydania bazy: ZIP, manifest i opis")
    p.add_argument("katalog", metavar="KATALOG")
    p.add_argument("--wzgledem", metavar="REF", help="poprzednie wydanie bazy albo aplikacji, do liczby zmian")
    p.set_defaults(cmd=cmd_paczka)
    args = ap.parse_args()
    if args.polecenie is None:
        args = ap.parse_args(["edytor"])
    args.cmd(args)


if __name__ == "__main__":
    main()
