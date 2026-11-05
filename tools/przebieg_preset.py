# -*- coding: utf-8 -*-
"""Przebieg gotowych programów (D-37) na emulatorze: Tabata z katalogu, karta „Dziś”, player z rundami,
podsumowanie stałego protokołu, potem Oddech Wima Hofa: rytm oddechu i krok bez limitu. Zrzuty do build/przebieg-preset/.

Skrypt CZYŚCI DANE aplikacji (`pm clear`) przed startem, więc nie odpalać na telefonie z historią.

    python tools/przebieg_preset.py --serial emulator-5554
    python tools/przebieg_preset.py --serial emulator-5556 --orientacja land --out build/przebieg-preset-land
"""
from __future__ import annotations

import argparse
import os
import re
import sqlite3
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from przebieg import ACTIVITY, PACKAGE, Blad, Urzadzenie, sprawdz_urzadzenie, znajdz_adb  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def zgoda_na_powiadomienia(u: Urzadzenie) -> None:
    """Systemowy dialog zgody zostaje na ekranie po ubiciu apki; jeśli wisi, tapnięcie „Allow” go zamyka."""
    for _ in range(3):
        if u.znajdz("Allow"):
            u.tapnij("Allow")
            time.sleep(0.8)
        else:
            return


def start_app(u: Urzadzenie) -> None:
    u.shell("am", "force-stop", PACKAGE)
    zgoda_na_powiadomienia(u)
    u.shell("am", "start", "-n", f"{PACKAGE}/{ACTIVITY}")
    time.sleep(1.5)
    zgoda_na_powiadomienia(u)
    u.zamknij_dialog_16kb()


def notification_title(u: Urzadzenie) -> str:
    raw = u.shell("dumpsys", "notification", "--noredact", check=False)
    titles = re.findall(r"android\.title=String \(([^)]*)\)", raw)
    return " | ".join(t for t in titles if "runda" in t or "seria" in t or "Przerwa" in t)


def przebieg(u: Urzadzenie, out: str) -> None:
    print("== Tabata z katalogu")
    u.shell("pm", "clear", PACKAGE, check=False)
    u.shell("pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    start_app(u)
    u.czekaj("Ułóż plan", timeout=60)
    time.sleep(2.5)  # import seedu po pm clear
    u.zamknij_dialog_16kb(timeout=5)
    u.czekaj("Gotowy program")
    u.zrzut("dzis-bez-planu")
    u.tapnij("Gotowy program")
    u.czekaj("Który program")
    u.zrzut("programy-lista")
    # Lista idzie od łagodnych do wymagających (D-46), Tabata stoi pod koniec.
    u.przewin_do("Tabata")
    u.tapnij("Tabata")
    u.tapnij("Dalej")
    u.czekaj("Ile i jak mocno")
    u.tapnij("14 dni")
    time.sleep(0.5)
    print("   stopka:", [t for _, _, t in u.znajdz(r"Wychodzi .*")])
    u.zrzut("programy-ustawienia")
    u.tapnij("Zacznij program")
    u.czekaj(r"(?s).*Cykl 14 dni.*", timeout=30)
    u.zrzut("plan-tabata")
    u.wstecz()
    u.czekaj("Start")
    karta = u.znajdz(r"Tabata · etap \d+ z \d+")
    print("   karta:", [t for _, _, t in karta])
    print("   etap:", [t for _, _, t in u.znajdz(r"Etap 1 z .*")])
    u.zrzut("dzis-tabata")

    u.tapnij("Start")
    zgoda_na_powiadomienia(u)
    u.czekaj("Zaczynamy")
    time.sleep(6.0)  # odliczanie 5 s
    # rozgrzewka: trzy pozycje na czas, potem 15 s przerwy; pomijamy, żeby dojść do bloku tabaty
    for _ in range(4):
        u.tapnij("Pomiń", index=-1)
        time.sleep(0.6)
    u.czekaj(r"runda 1 z 2.*")
    print("   stały protokół:", bool(u.znajdz(r"Stały protokół.*")))
    print("   powiadomienie:", notification_title(u))
    u.zrzut("player-tabata-runda")
    time.sleep(21.0)  # koniec okna 20 s: przerwa 10 s i zapowiedź
    u.zrzut("player-tabata-przerwa")
    u.tapnij("Przerwij trening")
    u.tapnij("Przerwij i zapisz")
    u.czekaj("Przerwane")
    pigulki = [t for _, _, t in u.znajdz(r"Bez zmian|Więcej|Mniej|Nie chcę")]
    print("   pigułki podsumowania:", sorted(set(pigulki)))
    u.zrzut("podsumowanie-staly-protokol")
    u.tapnij("Zakończ")
    u.czekaj("Start")

    print("== Usunięcie planu i Oddech Wima Hofa")
    u.tapnij("Zobacz plan")
    u.czekaj(r"(?s).*Cykl 14 dni.*")
    # Od D-42 „Usuń plan” jest w menu planu (⋮ „Więcej”), a nie w stopce.
    u.tapnij("Więcej")
    u.czekaj("Usuń plan")
    u.zrzut("plan-menu")
    u.tapnij("Usuń plan")
    u.czekaj(r"Usunąć plan\?")
    u.tapnij("Usuń plan", index=-1)
    u.czekaj("Gotowy program")
    u.tapnij("Gotowy program")
    u.czekaj("Który program")
    u.przewin_do("Oddech Wima Hofa")
    u.tapnij("Oddech Wima Hofa")
    u.tapnij("Dalej")
    u.czekaj("Ile i jak mocno")
    print("   ostrzeżenie:", bool(u.znajdz(r"Tylko na siedząco.*")))
    u.zrzut("programy-wim-hof")
    u.tapnij("Zacznij program")
    u.czekaj(r"(?s).*Cykl 21 dni.*", timeout=30)
    u.wstecz()
    u.czekaj("Start")
    print("   karta:", [t for _, _, t in u.znajdz(r"Oddech Wima Hofa · etap \d+ z \d+")])
    u.zrzut("dzis-wim-hof")
    u.tapnij("Start")
    u.czekaj("Zaczynamy")
    time.sleep(9.5)  # odliczanie 5 s + kilka sekund oddechu
    print("   oddech:", [t for _, _, t in u.znajdz(r"oddech z \d+|jeszcze .*|runda 1 z 3.*")])
    u.zrzut("player-oddech-rytm")
    u.tapnij("Pomiń", index=-1)
    u.czekaj("jak najdłużej")
    time.sleep(4.0)
    print("   zatrzymanie:", [t for _, _, t in u.znajdz(r"jak najdłużej|Zakończ")])
    u.zrzut("player-zatrzymanie")
    u.tapnij("Zakończ")
    u.czekaj("Wdech i zatrzymanie", timeout=10)
    u.zrzut("player-wdech")
    u.tapnij("Przerwij trening")
    u.tapnij("Przerwij i zapisz")
    u.czekaj("Przerwane")
    u.tapnij("Zakończ")
    u.czekaj("Start")

    print("== Baza: sesje i serie")
    db = u.run("exec-out", "run-as", PACKAGE, "cat", "databases/trainings.db")
    wal = u.run("exec-out", "run-as", PACKAGE, "cat", "databases/trainings.db-wal", check=False)
    with open(os.path.join(out, "trainings.db"), "wb") as f:
        f.write(db)
    if wal:
        with open(os.path.join(out, "trainings.db-wal"), "wb") as f:
            f.write(wal)
    con = sqlite3.connect(os.path.join(out, "trainings.db"))
    try:
        for row in con.execute("SELECT name, profileJson FROM programs ORDER BY createdAt"):
            preset = re.search(r'"presetId":"?([A-Z_]+)"?', row[1] or "")
            print("   program:", row[0], "| presetId:", preset.group(1) if preset else None)
        for row in con.execute("SELECT exerciseName, targetKind, targetValue, actualValue FROM set_logs ORDER BY completedAt"):
            print("   seria:", row)
    finally:
        con.close()
    print("Gotowe")


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Przebieg gotowych programów (Tabata, Oddech Wima Hofa) ze zrzutami. Czyści dane apki.")
    parser.add_argument("--out", default=os.path.join(REPO, "build", "przebieg-preset"), help="katalog na zrzuty (domyślnie build/przebieg-preset)")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"), help="urządzenie z `adb devices`")
    parser.add_argument(
        "--orientacja", choices=["port", "land"], default=None,
        help="ustaw orientację przed przebiegiem (tablet i telefon w poziomie, D-42); domyślnie bez zmian",
    )
    args = parser.parse_args()

    try:
        u = Urzadzenie(znajdz_adb(), args.serial, args.out)
        sprawdz_urzadzenie(u)
        if PACKAGE not in u.shell("pm", "list", "packages", PACKAGE):
            raise Blad(f"brak {PACKAGE} na urządzeniu, najpierw gradlew.bat installDebug")
        os.makedirs(args.out, exist_ok=True)
        if args.orientacja:
            u.orientacja(args.orientacja == "land")
        przebieg(u, args.out)
    except Blad as e:
        print(f"Błąd: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
