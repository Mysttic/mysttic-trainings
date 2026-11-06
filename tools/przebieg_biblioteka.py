# -*- coding: utf-8 -*-
"""Przebieg biblioteki ćwiczeń na emulatorze: kafelki (domyślne) i lista, panel filtrów, chipy „lub” w sekcji i „i” między
sekcjami, licznik i „Wyczyść filtry” nad listą, „Tylko mój sprzęt” i widok w panelu (D-52), szukanie. Zrzuty do build/przebieg-biblioteka/.

Instaluje app/build/outputs/apk/debug/app-debug.apk i nie czyści danych apki; z pustym sprzętem w Ustawieniach
„Tylko mój sprzęt” + „Hantle” daje 0 wyników, co skrypt uwiecznia. Przed startem usuwa stare zrzuty z katalogu wyjściowego.

    python tools/przebieg_biblioteka.py --serial emulator-5554
    python tools/przebieg_biblioteka.py --serial emulator-5556 --orientacja port --out build/przebieg-biblioteka-port
"""
from __future__ import annotations

import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from przebieg import ACTIVITY, PACKAGE, Blad, Urzadzenie, sprawdz_urzadzenie, znajdz_adb  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(REPO, "app", "build", "outputs", "apk", "debug", "app-debug.apk")


def przebieg(u: Urzadzenie) -> None:
    print("instalacja", u.run("install", "-r", APK).decode("utf-8", "replace").strip())
    u.shell("am", "force-stop", PACKAGE)
    u.shell("am", "start", "-n", f"{PACKAGE}/{ACTIVITY}")
    time.sleep(3)
    u.zamknij_dialog_16kb()
    u.tapnij("Ćwiczenia", pauza=2.5)
    u.zrzut("kafelki")
    u.przewin(500)
    time.sleep(1.5)
    u.zrzut("kafelki-dalej")

    # Od D-52 nad listą jest tylko szukanie z przyciskiem filtrów („Filtry” / „Filtry · n”);
    # widok, „Tylko mój sprzęt” i chipy są w panelu, a „Wyczyść filtry” nad listą, gdy coś filtruje.
    u.tapnij("Filtry", pauza=1.2)
    u.zrzut("panel")
    u.przewin_do("Lista")
    u.tapnij("Lista", pauza=1.0)
    u.tapnij("Filtry", pauza=1.5)
    u.zrzut("lista")
    u.tapnij("Filtry", pauza=1.2)
    u.przewin_do("Kafelki")
    u.tapnij("Kafelki", pauza=1.0)
    u.tapnij("Filtry", pauza=1.2)

    u.tapnij("Filtry", pauza=1.2)
    u.tapnij("Kardio")
    u.tapnij("Bez sprzętu")
    u.zrzut("panel-kardio-bez-sprzetu")
    u.tapnij("Filtry.*", pauza=1.5)
    u.zrzut("kafelki-kardio")

    u.tapnij("Filtry.*", pauza=1.2)
    u.tapnij("Tylko mój sprzęt")
    u.tapnij("Filtry.*", pauza=1.5)
    u.zrzut("moj-sprzet")
    u.tapnij("Wyczyść filtry", pauza=1.2)
    u.tapnij("Filtry", pauza=1.2)
    u.tapnij("Kardio")
    u.tapnij("Bez sprzętu")
    u.tapnij("Hantle")
    u.przewin_do("Brzuch")
    u.tapnij("Brzuch")
    u.zrzut("panel-dol")
    u.tapnij("Wyczyść filtry")
    u.tapnij("Filtry", pauza=1.2)
    u.tapnij("Filtry", pauza=1.2)
    u.tapnij("Ławka")
    u.tapnij("Filtry.*", pauza=1.5)
    u.zrzut("kafelki-lawka-brak-sprzetu")
    u.tapnij("Wyczyść filtry", pauza=1.2)

    u.tapnij("Szukaj ćwiczenia")
    u.shell("input", "text", "plank")
    time.sleep(1.2)
    # Klawiatura zasłaniała połowę wyników: chowamy ją, pole zostaje wypełnione.
    u.schowaj_klawiature()
    time.sleep(1.0)
    u.zrzut("szukanie")
    print("gotowe:", u.out)


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Przebieg biblioteki ćwiczeń ze zrzutami. Instaluje debug APK, nie czyści danych apki.")
    parser.add_argument("--out", default=os.path.join(REPO, "build", "przebieg-biblioteka"), help="katalog na zrzuty (domyślnie build/przebieg-biblioteka)")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"), help="urządzenie z `adb devices`")
    parser.add_argument(
        "--orientacja", choices=["port", "land"], default=None,
        help="ustaw orientację przed przebiegiem (tablet i telefon w poziomie, D-42); domyślnie bez zmian",
    )
    args = parser.parse_args()

    try:
        if not os.path.isfile(APK):
            raise Blad(f"brak {APK}, najpierw gradlew.bat assembleDebug")
        u = Urzadzenie(znajdz_adb(), args.serial, args.out)
        sprawdz_urzadzenie(u)
        os.makedirs(args.out, exist_ok=True)
        for nazwa in os.listdir(args.out):
            sciezka = os.path.join(args.out, nazwa)
            if os.path.isfile(sciezka) and nazwa.endswith((".png", ".xml")):
                os.remove(sciezka)
        if args.orientacja:
            u.orientacja(args.orientacja == "land")
        przebieg(u)
    except Blad as e:
        print("BŁĄD:", e, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
