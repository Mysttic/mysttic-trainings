# Zrzuty ekranu

Emulator Pixel_9_Pro (API 37), stan z 2026-11-06: nowy interfejs (D-52, D-53, D-54) w motywie „Ogień i Krew”
ze zdjęciem dnia pod ekranami (D-55, własne zdjęcia bazy 11 z issue #8) i szklanymi kartami, jeden zrzut w motywie
jasnym. Plan z kreatora: początkujący, 2 dni w tygodniu, 15 minut, bez sprzętu
(`tools/przebieg.py`). Gotowe programy z `tools/przebieg_preset.py`, biblioteka z `tools/przebieg_biblioteka.py`.
Zamknięty cykl pochodzi z AVD tabletu w pionie, bo tam był cykl z kreatora doprowadzony do końca.
Układ tych samych ekranów z fiksturami, na telefonie i tablecie, rysuje też `LayoutScreenshotTest`
do [`app/src/test/screenshots/uklad`](../../app/src/test/screenshots/uklad).

| Plik | Co pokazuje |
|---|---|
| [dzis.png](dzis.png) | Ekran główny: data, nagłówek „Trening na dziś” bez partii dnia (D-55) i jedna karta dnia z miniaturami ćwiczeń, paskiem tygodnia, Startem i dwoma cichymi odsyłaczami. Bez listy treningów do wybierania |
| [dzis-jasny.png](dzis-jasny.png) | Ten sam ekran w motywie jasnym „Papier i Ogień”: ogień ciemnieje do karminu, żar do złota, żeby trzymać kontrast na papierze |
| [trening.png](trening.png) | Co jest w treningu: chipy poziomu, nagłówek bloku z rundami i przerwą, wiersze ćwiczeń z miniaturą i celem |
| [player.png](player.png) | Player: pozycja w żarze, nazwa ćwiczenia, wielka liczba powtórzeń, ilustracja, triada „−/Zrobione/+” i ciche Wstecz, Pauza, Pomiń; poziom i przerwanie w menu ⋮ |
| [plan.png](plan.png) | Cykl jako kolejka: postęp, karta następnego dnia ze Startem i rozkład tygodniami; rozwinięty tydzień z następnym treningiem, kolejne odpoczynki jednym wierszem (D-54) |
| [kreator.png](kreator.png) | Kreator w czterech krokach od sprzętu (D-57), tu ostatni: dni w tygodniu, długość treningu i cyklu, a w stopce podgląd planu; × zamyka kreator z każdego kroku |
| [cwiczenia.png](cwiczenia.png) | Biblioteka: tytuł, pole szukania z lejkiem filtrów i kafelki z ilustracjami (D-52) |
| [podsumowanie.png](podsumowanie.png) | Podsumowanie po treningu: liczby zamiast pochwał, opinie do ćwiczeń, ocena wysiłku RPE i pole notatki |
| [cykl-zamkniety.png](cykl-zamkniety.png) | Zamknięty cykl (tablet w pionie): siatka statystyk i pytanie „jak poszedł ten cykl”, które ustawia trudność następnego |
| [historia.png](historia.png) | Historia: okres i jego statystyki, rozwinięty „Postęp” z wolumenem tygodniowym i kalendarzem aktywności, segmenty stanu nad listą (D-54) |
| [ustawienia.png](ustawienia.png) | Ustawienia: siedem kategorii z ikoną i streszczeniem stanu, na górze „O mnie” (D-52, D-57) |
| [health-connect.png](health-connect.png) | Wpis z ukończonego treningu w Health Connect (D-31) |
| [gotowe-programy.png](gotowe-programy.png) | Gotowe programy (D-37): katalog, ustawienia programu, karta „Dziś” z etapem Tabaty, podgląd cyklu |
| [gotowe-programy-player.png](gotowe-programy-player.png) | Player w stałym protokole: Tabata z numerem rundy, oddech w rytmie, zatrzymanie bez limitu, podsumowanie bez „Więcej/Mniej” |
| [motywy.png](motywy.png) | Trzy motywy do wyboru (D-39, 2026-09-19) na ekranie „Dziś”: Ogień i Krew, Papier i Ogień, Stal i Bursztyn. Jedyny obrazek w tym katalogu rysowany kodem, nie zrzut z telefonu — pokazuje kolory w rolach, nie dokładny układ ekranu |
| [biblioteka.png](biblioteka.png) | Biblioteka (D-52): kafelki, rozwinięty panel filtrów („Tylko mój sprzęt”, widok, sprzęt, rodzaj, poziom, partie), lista wierszy |

`health-connect.png` pochodzi z aplikacji Health Connect, nie z naszego interfejsu.

Zrzuty odświeżamy ręcznie po zmianach w UI. Przebieg jednego treningu (kreator → player → podsumowanie)
potrafi zebrać `python tools/przebieg.py` na emulatorze — patrz [testing.md](../testing.md);
gotowe programy (katalog → Tabata → player → Wim Hof) zbiera `python tools/przebieg_preset.py`,
a bibliotekę (kafelki, lista, panel filtrów, szukanie) `python tools/przebieg_biblioteka.py`.
Katalog `przebieg/` to taki przebieg z 2026-09-08 w realnym tempie (`--tempo 3`, najniższe ustawienia kreatora)
plus dwa zrzuty steppera; prezentację z tych zrzutów składa `python docs/prezentacja/deck.py`.
