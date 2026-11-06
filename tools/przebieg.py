#!/usr/bin/env python3
"""Przebieg jednego treningu na emulatorze: przeklikuje aplikację przez adb i zapisuje zrzuty ekranu.

Wymagania: uruchomiony emulator (albo telefon z debugowaniem USB), zainstalowany debug APK
(`gradlew.bat installDebug`), `adb` na PATH albo w %LOCALAPPDATA%\\Android\\Sdk\\platform-tools.

Skrypt CZYŚCI DANE aplikacji (`pm clear`), żeby zacząć od pustego „Dziś” — nie odpalać na telefonie
z historią, którą chcesz zachować. Przechodzi: kreator (początkujący, 2 dni, 15 min, bez sprzętu),
podgląd cyklu, „Dziś” z planem, szczegóły treningu, player (odliczanie, serie, przerwa), podsumowanie
z oceną RPE, „Dziś” po treningu, podgląd cyklu, Historia. Serie kończy od razu, przerwy pomija — chodzi o ekrany,
nie o realny czas.

    python tools/przebieg.py                       # zrzuty do build/przebieg/
    python tools/przebieg.py --tempo 3             # realne tempo: 3 s na powtórzenie, serie na czas i przerwy do końca (ok. 15 min)
    python tools/przebieg.py --out docs/screenshots/przebieg
    set ANDROID_SERIAL=emulator-5554 & python tools/przebieg.py   # przy kilku urządzeniach

Elementy szuka po tekście w drzewie dostępności (`uiautomator dump`), więc etykiety w skrypcie
muszą zgadzać się z tym, co jest w UI. Gdy czegoś nie znajdzie, zapisuje zrzut i XML drzewa
do katalogu wyjściowego jako `blad-*` i kończy z błędem.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "pl.mysttic.trainings.debug"
ACTIVITY = "pl.mysttic.trainings.MainActivity"

# Które serie i przerwy uwiecznić na zrzutach (numer serii z nagłówka playera). Przy planie, który układa ten skrypt
# (dwa bloki: 5 i 4 ćwiczenia po 2 rundy), 10 to ostatnia seria pierwszego bloku na czas, 18 ostatnia w treningu,
# a przerwy przed 2, 6 i 11 to kolejno przerwa po ćwiczeniu, po rundzie i między blokami.
WORK_SHOT_SETS = {1, 2, 3, 10, 18}
REST_SHOT_SETS = {2, 6, 11}

# Wymiary ekranu Pixel_9_Pro w pikselach, gdy nie ma jeszcze drzewa; potem liczymy je z `wm size` i obrotu
# z `uiautomator dump`, więc skrypty działają też na tablecie i w poziomie (D-42).
EKRAN_WYSOKOSC = 2856
EKRAN_SZEROKOSC = 1280


class Blad(Exception):
    pass


class Urzadzenie:
    def __init__(self, adb: str, serial: str | None, out: str):
        self.adb = adb
        self.serial = serial
        self.out = out
        self.licznik = 0
        self.szerokosc = EKRAN_SZEROKOSC
        self.wysokosc = EKRAN_WYSOKOSC
        self._naturalny: tuple[int, int] | None = None

    # --- adb ---------------------------------------------------------------

    def _cmd(self, *args: str) -> list[str]:
        base = [self.adb]
        if self.serial:
            base += ["-s", self.serial]
        return base + list(args)

    def run(self, *args: str, check: bool = True) -> bytes:
        proc = subprocess.run(self._cmd(*args), capture_output=True)
        if check and proc.returncode != 0:
            raise Blad(f"adb {' '.join(args)}: {proc.stderr.decode('utf-8', 'replace').strip()}")
        return proc.stdout

    def shell(self, *args: str, check: bool = True) -> str:
        return self.run("shell", *args, check=check).decode("utf-8", "replace")

    # --- drzewo dostępności --------------------------------------------------

    def drzewo(self) -> ET.Element:
        """`uiautomator dump` potrafi odmówić, gdy ekran nie jest „bezczynny” — stąd kilka prób i dwie drogi."""
        ostatni = ""
        for _ in range(5):
            raw = self.run("exec-out", "uiautomator", "dump", "/dev/tty", check=False)
            xml = self._wytnij_xml(raw)
            if xml is None:
                self.shell("uiautomator", "dump", "/sdcard/przebieg.xml", check=False)
                raw = self.run("exec-out", "cat", "/sdcard/przebieg.xml", check=False)
                xml = self._wytnij_xml(raw)
            if xml is not None:
                try:
                    root = ET.fromstring(xml)
                    self._zapamietaj_rozmiar(root)
                    return root
                except ET.ParseError as e:
                    ostatni = str(e)
            else:
                ostatni = raw.decode("utf-8", "replace")[-200:]
            time.sleep(0.7)
        raise Blad(f"uiautomator dump nie dał drzewa: {ostatni}")

    def rozmiar_naturalny(self) -> tuple[int, int] | None:
        """Wymiary ekranu w orientacji naturalnej z `wm size` (rozmiar nadpisany wygrywa z fizycznym). Telefon jest
        naturalnie pionowy, Pixel Tablet poziomy. Pytamy raz na przebieg; None, gdy system nie odpowiedział."""
        if self._naturalny is None:
            wymiary = re.findall(r"size: (\d+)x(\d+)", self.shell("wm", "size", check=False))
            self._naturalny = (int(wymiary[-1][0]), int(wymiary[-1][1])) if wymiary else (0, 0)
        return self._naturalny if self._naturalny[0] > 0 else None

    def _zapamietaj_rozmiar(self, root: ET.Element) -> None:
        """Wymiary ekranu po obrocie: `wm size` i obrót z korzenia drzewa. Pierwszy węzeł drzewa nie nadaje się
        na ekran, bo przy otwartym menu dump zawiera tylko okno popupu."""
        naturalny = self.rozmiar_naturalny()
        obrot = root.get("rotation")
        if naturalny and obrot is not None:
            szerokosc, wysokosc = naturalny
            if obrot in ("1", "3"):
                szerokosc, wysokosc = wysokosc, szerokosc
            self.szerokosc, self.wysokosc = szerokosc, wysokosc
            return
        # Zapas bez `wm size`: największy węzeł drzewa, nie pierwszy.
        najwiekszy = 0
        for node in root.iter("node"):
            m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds") or "")
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                if (x2 - x1) * (y2 - y1) > najwiekszy:
                    najwiekszy = (x2 - x1) * (y2 - y1)
                    self.szerokosc, self.wysokosc = x2, y2

    def obszar_przewijania(self, drzewo: ET.Element | None = None) -> tuple[int, int, int, int] | None:
        """Największy przewijany węzeł na ekranie. W poziomie na telefonie stały pasek z przyciskami zajmuje dół,
        więc gest od 3/4 wysokości ekranu trafiałby w pasek, a nie w treść."""
        drzewo = drzewo if drzewo is not None else self.drzewo()
        najlepszy, pole = None, 0
        for node in drzewo.iter("node"):
            if node.get("scrollable") != "true":
                continue
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if not m:
                continue
            x1, y1, x2, y2 = map(int, m.groups())
            if (x2 - x1) * (y2 - y1) > pole:
                najlepszy, pole = (x1, y1, x2, y2), (x2 - x1) * (y2 - y1)
        return najlepszy

    def przewin(self, ms: int = 400, w_gore: bool = False) -> None:
        """Jeden gest w dół wewnątrz przewijanego kontenera (od 3/4 do 1/4 jego wysokości); bez kontenera tak jak
        dawniej, od 3/4 do 1/3 wysokości ekranu, na środku szerokości."""
        obszar = self.obszar_przewijania()
        if obszar is None:
            x = str(self.szerokosc // 2)
            self.shell("input", "swipe", x, str(self.wysokosc * 3 // 4), x, str(self.wysokosc * 35 // 100), str(ms))
            return
        x1, y1, x2, y2 = obszar
        x = str((x1 + x2) // 2)
        dol, gora = str(y1 + (y2 - y1) * 3 // 4), str(y1 + (y2 - y1) // 4)
        if w_gore:
            self.shell("input", "swipe", x, gora, x, dol, str(ms))
        else:
            self.shell("input", "swipe", x, dol, x, gora, str(ms))

    def orientacja(self, poziom: bool) -> None:
        """Blokuje obrót czujnikiem i ustawia pion albo poziom. `user_rotation` liczy się od orientacji naturalnej:
        na telefonie 0 to pion, na Pixel Tablet poziom, więc rotację wybieramy po `wm size`."""
        naturalny = self.rozmiar_naturalny()
        naturalnie_poziomy = naturalny is not None and naturalny[0] > naturalny[1]
        self.shell("settings", "put", "system", "accelerometer_rotation", "0")
        self.shell("settings", "put", "system", "user_rotation", "0" if poziom == naturalnie_poziomy else "1")
        time.sleep(1.5)

    def schowaj_klawiature(self) -> None:
        """ESC nie chowa klawiatury ekranowej. Chowa ją „Wstecz”, ale bez klawiatury na ekranie cofnąłby
        ekran, więc najpierw pytamy system, czy klawiatura jest widoczna."""
        stan = self.shell("dumpsys", "input_method", check=False)
        if re.search(r"\b(mInputShown|mIsInputViewShown)=true\b", stan):
            self.wstecz()

    @staticmethod
    def _wytnij_xml(raw: bytes) -> bytes | None:
        start = raw.find(b"<?xml")
        koniec = raw.rfind(b"</hierarchy>")
        if start < 0 or koniec < 0:
            return None
        return raw[start : koniec + len(b"</hierarchy>")]

    def znajdz(self, wzorzec: str, drzewo: ET.Element | None = None) -> list[tuple[int, int, str]]:
        """Środki węzłów, których `text` albo `content-desc` pasuje w całości do wzorca (bez rozróżniania wielkości liter)."""
        rx = re.compile(wzorzec, re.IGNORECASE)
        drzewo = drzewo if drzewo is not None else self.drzewo()
        wynik = []
        for node in drzewo.iter("node"):
            for attr in ("text", "content-desc"):
                tekst = node.get(attr) or ""
                if tekst and rx.fullmatch(tekst.strip()):
                    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds") or "")
                    if m:
                        x1, y1, x2, y2 = map(int, m.groups())
                        wynik.append(((x1 + x2) // 2, (y1 + y2) // 2, tekst))
                    break
        return wynik

    def czekaj(self, wzorzec: str, timeout: float = 20.0) -> list[tuple[int, int, str]]:
        koniec = time.time() + timeout
        while True:
            znalezione = self.znajdz(wzorzec)
            if znalezione:
                return znalezione
            if time.time() > koniec:
                self.zapisz_blad(wzorzec)
                raise Blad(f"nie znalazłem „{wzorzec}” na ekranie w {timeout:.0f} s")
            time.sleep(0.8)

    def czekaj_az_zniknie(self, wzorzec: str, timeout: float = 20.0) -> None:
        koniec = time.time() + timeout
        while time.time() < koniec and self.znajdz(wzorzec):
            time.sleep(0.8)

    # --- gesty ---------------------------------------------------------------

    def widoczne(self, wzorzec: str, index: int, timeout: float) -> list[tuple[int, int, str]]:
        """Węzły z numerem `index` w widocznej części treści. Najpierw krótko czeka na przejście ekranu, potem przewija
        w dół: w niskim oknie (telefon w poziomie) pola kroku kreatora leżą pod stałym paskiem z przyciskami."""
        koniec = time.time() + min(timeout, 4.0)
        wezly = self.znajdz(wzorzec)
        while len(wezly) <= index and time.time() < koniec:
            time.sleep(0.8)
            wezly = self.znajdz(wzorzec)
        # Najpierw w dół (kolejne pola kroku), potem w górę (pole, które zostało nad ekranem po wcześniejszym przewinięciu).
        for krok in range(10):
            if len(wezly) > index:
                _, y, _ = wezly[index]
                obszar = self.obszar_przewijania()
                if obszar is None and 0 < y < self.wysokosc - 150:
                    return wezly
                # W kontenerze albo pod nim, czyli w stałym pasku z przyciskami: uiautomator nie pokazuje węzłów
                # schowanych poza przewijanym obszarem, więc znaleziony węzeł jest widoczny.
                if obszar is not None and obszar[1] < y < self.wysokosc - 40:
                    return wezly
            self.przewin(w_gore=krok >= 4)
            time.sleep(0.8)
            wezly = self.znajdz(wzorzec)
        wezly = self.czekaj(wzorzec, timeout)
        if len(wezly) <= index:
            self.zapisz_blad(wzorzec)
            raise Blad(f"nie znalazłem „{wzorzec}” nr {index + 1} na ekranie")
        return wezly

    def tapnij(self, wzorzec: str, index: int = 0, timeout: float = 20.0, pauza: float = 0.7) -> None:
        """Gdy węzeł jest poza ekranem (długie podsumowanie, niskie okno), przewija w dół, aż da się w niego trafić."""
        x, y, tekst = self.widoczne(wzorzec, index, timeout)[index]
        print(f"   tap „{tekst}” ({x},{y})")
        self.shell("input", "tap", str(x), str(y))
        time.sleep(pauza)

    def przytrzymaj_pod(self, etykieta: str, wzorzec: str, ms: int) -> None:
        """Przytrzymuje pierwszy węzeł `wzorzec` pod etykietą: po przewinięciu kolejność klawiszy na ekranie się zmienia,
        więc numer klawisza nie wskazuje już tego samego steppera."""
        etykiety = self.widoczne(etykieta, 0, 20.0)
        _, y_etykiety, _ = etykiety[0]
        for _ in range(4):
            pod = [w for w in self.znajdz(wzorzec) if w[1] > y_etykiety]
            if pod:
                x, y, tekst = pod[0]
                print(f"   przytrzymanie „{tekst}” pod „{etykieta}” przez {ms} ms")
                self.shell("input", "swipe", str(x), str(y), str(x), str(y), str(ms))
                time.sleep(0.7)
                return
            self.przewin()
            time.sleep(0.8)
            _, y_etykiety, _ = self.czekaj(etykieta)[0]
        self.zapisz_blad(wzorzec)
        raise Blad(f"nie znalazłem „{wzorzec}” pod „{etykieta}”")

    def przytrzymaj(self, wzorzec: str, index: int, ms: int) -> None:
        x, y, tekst = self.widoczne(wzorzec, index, 20.0)[index]
        print(f"   przytrzymanie „{tekst}” przez {ms} ms")
        self.shell("input", "swipe", str(x), str(y), str(x), str(y), str(ms))
        time.sleep(0.7)

    def wstecz(self) -> None:
        self.shell("input", "keyevent", "4")
        time.sleep(0.7)

    def przewin_do(self, wzorzec: str, kroki: int = 8) -> None:
        """Przewija w dół, aż węzeł pojawi się w drzewie: uiautomator widzi tylko to, co jest na ekranie."""
        for _ in range(kroki):
            if self.znajdz(wzorzec):
                return
            self.przewin()
            time.sleep(0.8)

    def zamknij_dialog_16kb(self, timeout: float = 10.0) -> None:
        """Emulator z obrazem 16 KB pokazuje przy zimnym starcie dialog zgodności nad aplikacją; „OK” go zamyka."""
        koniec = time.time() + timeout
        while time.time() < koniec:
            drzewo = self.drzewo()
            if self.znajdz("Android App Compatibility", drzewo):
                x, y, _ = self.znajdz("OK", drzewo)[0]
                print("   dialog 16 KB: OK")
                self.shell("input", "tap", str(x), str(y))
                time.sleep(0.7)
                return
            if self.znajdz("Ułóż plan", drzewo):
                return
            time.sleep(0.8)

    # --- zrzuty --------------------------------------------------------------

    def zrzut(self, nazwa: str) -> str:
        self.licznik += 1
        plik = os.path.join(self.out, f"{self.licznik:02d}-{nazwa}.png")
        with open(plik, "wb") as f:
            f.write(self.run("exec-out", "screencap", "-p"))
        print(f"-> {plik}")
        return plik

    def zapisz_blad(self, czego: str) -> None:
        try:
            with open(os.path.join(self.out, "blad.png"), "wb") as f:
                f.write(self.run("exec-out", "screencap", "-p"))
            raw = self.run("exec-out", "uiautomator", "dump", "/dev/tty", check=False)
            xml = self._wytnij_xml(raw)
            if xml:
                with open(os.path.join(self.out, "blad-drzewo.xml"), "wb") as f:
                    f.write(xml)
        except Blad:
            pass
        print(f"!! brak „{czego}”; zrzut i drzewo w {self.out}/blad-*", file=sys.stderr)


def znajdz_adb() -> str:
    z_path = shutil.which("adb")
    if z_path:
        return z_path
    kandydaci = []
    for zmienna in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(zmienna):
            kandydaci.append(os.path.join(os.environ[zmienna], "platform-tools"))
    if os.environ.get("LOCALAPPDATA"):
        kandydaci.append(os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk", "platform-tools"))
    kandydaci.append(os.path.expanduser("~/Library/Android/sdk/platform-tools"))
    kandydaci.append(os.path.expanduser("~/Android/Sdk/platform-tools"))
    for katalog in kandydaci:
        for nazwa in ("adb.exe", "adb"):
            sciezka = os.path.join(katalog, nazwa)
            if os.path.isfile(sciezka):
                return sciezka
    raise Blad("nie znalazłem adb — dodaj platform-tools do PATH albo ustaw ANDROID_HOME")


def sprawdz_urzadzenie(u: Urzadzenie) -> None:
    """Przerywa, zanim skrypt cokolwiek zmieni: bez urządzenia albo przy kilku naraz bez --serial i ANDROID_SERIAL."""
    urzadzenia = [l for l in u.run("devices").decode("utf-8", "replace").splitlines()[1:] if l.strip().endswith("device")]
    if not urzadzenia:
        raise Blad("adb nie widzi żadnego urządzenia, uruchom emulator (emulator -avd Pixel_9_Pro)")
    if len(urzadzenia) > 1 and not u.serial:
        raise Blad("kilka urządzeń, podaj --serial albo ANDROID_SERIAL")


def przebieg(u: Urzadzenie, wyczysc: bool, tempo: float = 0.0) -> None:
    print("Start aplikacji")
    if wyczysc:
        u.shell("pm", "clear", PACKAGE)
    # Bez tego Android 13+ pyta o zgodę na powiadomienia zaraz po wejściu do playera.
    u.shell("pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    u.shell("am", "start", "-n", f"{PACKAGE}/{ACTIVITY}")
    u.zamknij_dialog_16kb()
    u.czekaj("Ułóż plan", timeout=40)
    time.sleep(1.0)
    u.zrzut("dzis-bez-planu")

    # Kolejność kroków od D-57: sprzęt, sylwetka i poziom, cel, czas.
    print("Kreator, krok 1: sprzęt")
    u.tapnij("Ułóż plan")
    u.czekaj("Krok 1 z 4")
    u.zrzut("kreator-1-sprzet")

    print("Kreator, krok 2: sylwetka i poziom")
    u.tapnij("Dalej: sylwetka")
    u.czekaj("Krok 2 z 4")
    u.tapnij("Poziom 1")  # DifficultySelector podpisuje pigułki „Poziom 1..3”, nie nazwami poziomów
    # Waga: drugi klawisz „−” na ekranie (pierwszy należy do wzrostu). Przytrzymanie powtarza krok.
    u.przytrzymaj_pod("Waga", "−", ms=1300)
    u.zrzut("kreator-2-sylwetka")

    print("Kreator, krok 3: cel")
    u.tapnij("Dalej: cel")
    u.czekaj("Krok 3 z 4")
    u.tapnij("Kondycja")
    u.zrzut("kreator-3-cel")

    print("Kreator, krok 4: czas")
    u.tapnij("Dalej: czas")
    u.czekaj("Krok 4 z 4")
    u.tapnij("2")
    for _ in range(3):
        u.tapnij("−", pauza=0.3)  # 30 → 15 min
    time.sleep(0.5)
    u.zrzut("kreator-4-czas")
    u.tapnij("Ułóż plan")

    print("Podgląd cyklu po kreatorze, Dziś z planem i szczegóły treningu")
    # Kreator po zapisie przechodzi do podglądu cyklu (PlanRoute z popUpTo kreatora), „Dziś” jest pod „Wstecz”.
    u.czekaj("Start", timeout=60)
    time.sleep(1.0)
    u.zrzut("plan-po-kreatorze")
    u.wstecz()
    # W niskim oknie karta dnia leży pod nagłówkiem: przewijamy do przycisku, zamiast tylko na niego czekać.
    u.widoczne("Co jest w treningu", 0, 20.0)
    time.sleep(1.0)
    u.zrzut("dzis-plan")
    u.tapnij("Co jest w treningu")
    u.czekaj(r"Start · ok\. \d+ min")
    time.sleep(1.0)
    u.zrzut("trening")

    print("Player")
    u.tapnij(r"Start · ok\. \d+ min")
    u.czekaj("Pomiń odliczanie", timeout=40)
    time.sleep(1.0)
    u.zrzut("player-odliczanie")
    # Odliczania nie pomijamy: tapnięcie „Pomiń odliczanie” tuż przed jego końcem trafiało w „Pauzę”,
    # która na ekranie pracy stoi w tym samym miejscu.
    u.czekaj_az_zniknie("Pomiń odliczanie", timeout=30)

    serie = 0
    przerwy = 0
    ostatni = None  # (rodzaj, numer serii) ostatnio obsłużonego kroku, żeby stare drzewo nie dało drugiego tapnięcia
    koniec = time.time() + (60 * 60 if tempo else 15 * 60)
    while time.time() < koniec:
        drzewo = u.drzewo()
        if u.znajdz(r"Cały trening w \d+ min|Trening przerwany.*", drzewo):
            break
        if tempo and u.znajdz(r".*· pauza", drzewo):
            x, y, _ = u.znajdz("Wznów", drzewo)[0]
            u.shell("input", "tap", str(x), str(y))
            time.sleep(0.5)
            continue
        naglowek = u.znajdz(r"\d+:\d+ · seria \d+ z \d+.*", drzewo)
        numer = int(re.search(r"seria (\d+) z", naglowek[0][2]).group(1)) if naglowek else 0
        zrobione = u.znajdz(r"Zrobione \(\d+\)", drzewo)
        na_czas = u.znajdz("Zakończ serię", drzewo)
        przerwa = u.znajdz(r"\+15 s", drzewo)
        if zrobione and ("praca", numer) != ostatni:
            ostatni = ("praca", numer)
            serie += 1
            if numer in WORK_SHOT_SETS:
                time.sleep(1.5)
                u.zrzut(f"player-seria-{numer:02d}")
            if tempo:
                reps = int(re.search(r"\((\d+)\)", zrobione[0][2]).group(1))
                time.sleep(max(0.0, reps * tempo - 2.0))
            x, y, _ = zrobione[0]
            u.shell("input", "tap", str(x), str(y))
        elif na_czas and ("praca", numer) != ostatni:
            ostatni = ("praca", numer)
            serie += 1
            if numer in WORK_SHOT_SETS:
                time.sleep(4.0 if tempo else 2.0)
                u.zrzut(f"player-seria-{numer:02d}-na-czas")
            if not tempo:
                x, y, _ = na_czas[0]
                u.shell("input", "tap", str(x), str(y))
        elif przerwa and ("przerwa", numer) != ostatni:
            ostatni = ("przerwa", numer)
            przerwy += 1
            if numer in REST_SHOT_SETS:
                time.sleep(1.0)
                u.zrzut(f"player-przerwa-przed-{numer:02d}")
            if not tempo:
                x, y, _ = u.znajdz("Pomiń", drzewo)[0]
                u.shell("input", "tap", str(x), str(y))
        time.sleep(1.0 if tempo else 0.6)
    else:
        u.zapisz_blad("koniec treningu")
        raise Blad("trening nie doszedł do podsumowania w wyznaczonym czasie")
    print(f"   serii: {serie}, przerw: {przerwy}")

    print("Podsumowanie")
    time.sleep(1.0)
    u.zrzut("podsumowanie")
    # Pigułki RPE 6..10 są na dole pod opiniami trenera, więc najpierw przewinięcie; ostatni węzeł „7” to ocena, nie statystyka.
    u.przewin_do("Zakończ")
    u.tapnij("7", index=-1)
    u.zrzut("podsumowanie-rpe")
    u.tapnij("Zakończ")

    print("Dziś po treningu, plan, historia")
    u.czekaj("Start", timeout=30)
    time.sleep(1.0)
    u.zrzut("dzis-po-treningu")
    u.tapnij("Zobacz plan")
    u.czekaj(r"Cykl \d+ dni.*")
    time.sleep(1.0)
    u.zrzut("plan")
    u.wstecz()
    u.tapnij("Historia")
    time.sleep(1.5)
    u.zrzut("historia")
    print("Gotowe")


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Przebieg jednego treningu na emulatorze ze zrzutami ekranu.")
    parser.add_argument("--out", default=os.path.join("build", "przebieg"), help="katalog na zrzuty (domyślnie build/przebieg)")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"), help="urządzenie z `adb devices`")
    parser.add_argument("--bez-czyszczenia", action="store_true", help="nie czyść danych aplikacji przed startem")
    parser.add_argument(
        "--orientacja", choices=["port", "land"], default=None,
        help="ustaw orientację przed przebiegiem (tablet i telefon w poziomie, D-42); domyślnie bez zmian",
    )
    parser.add_argument(
        "--tempo", type=float, default=0.0,
        help="sekundy na jedno powtórzenie; z tempem serie na czas i przerwy lecą do końca, więc trening trwa tyle, ile w planie",
    )
    args = parser.parse_args()

    try:
        adb = znajdz_adb()
        os.makedirs(args.out, exist_ok=True)
        u = Urzadzenie(adb, args.serial, args.out)
        sprawdz_urzadzenie(u)
        if PACKAGE not in u.shell("pm", "list", "packages", PACKAGE):
            raise Blad(f"brak {PACKAGE} na urządzeniu — najpierw gradlew.bat installDebug")
        if args.orientacja:
            u.orientacja(args.orientacja == "land")
        przebieg(u, wyczysc=not args.bez_czyszczenia, tempo=args.tempo)
    except Blad as e:
        print(f"Błąd: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
