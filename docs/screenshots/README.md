# Zrzuty ekranu

Emulator Pixel_9_Pro (API 37), stan z 2026-09-07 po przejściu na system wizualny „Ogień i Krew” (D-32).
Motyw ciemny, bo tak aplikacja wygląda domyślnie przy wieczornym treningu; jeden zrzut pokazuje motyw jasny.
Plan wygenerowany na 45 minut, poziom początkujący, cel redukcja.

| Plik | Co pokazuje |
|---|---|
| [dzis.png](dzis.png) | Ekran główny: data, zdanie o dzisiejszym treningu, pasek trzech statystyk i jedna karta dnia z przyciskiem Start. Bez listy treningów do wybierania |
| [dzis-jasny.png](dzis-jasny.png) | Ten sam ekran w motywie jasnym — ogień ciemnieje do karminu, żar do złota, żeby trzymać kontrast na papierze |
| [trening.png](trening.png) | Co jest w treningu: chipy poziomu, nagłówek bloku z rundami i przerwą, wiersze ćwiczeń z miniaturą i celem |
| [player.png](player.png) | Player: wielka liczba powtórzeń z jednostką, zdjęcie ćwiczenia, triada „−/Zrobione/+” i drugi rząd akcji |
| [plan.png](plan.png) | Cykl jako kolejka: odhaczone przygaszone, odpoczynek wyszarzony, następny dzień jako jedyna karta z własnym Startem |
| [kreator.png](kreator.png) | Kreator w czterech krokach, tu krok trzeci: dni w tygodniu, długość treningu i cyklu, a w stopce podgląd planu |
| [cwiczenia.png](cwiczenia.png) | Biblioteka (D-38, 2026-09-16): przełącznik kafelki / lista, szukanie, pasek „Filtry” i „Mój sprzęt” z licznikiem, kafelki z ilustracjami |
| [podsumowanie.png](podsumowanie.png) | Podsumowanie po treningu: liczby zamiast pochwał, ocena wysiłku RPE i pole notatki |
| [cykl-zamkniety.png](cykl-zamkniety.png) | Zamknięty cykl: siatka statystyk i pytanie „jak poszedł ten cykl”, które ustawia trudność następnego |
| [historia.png](historia.png) | Historia: statystyki okresu, wykres wolumenu tygodniowego i kalendarz aktywności — zrobione ogniem, resztki krwiste |
| [ustawienia.png](ustawienia.png) | Ustawienia: nagłówki sekcji w żarze, wiersze z opisem i przełącznikiem |
| [health-connect.png](health-connect.png) | Wpis z ukończonego treningu w Health Connect (D-31) |
| [gotowe-programy.png](gotowe-programy.png) | Gotowe programy (D-37, 2026-09-16): katalog, długość i poziom, karta „Dziś” z etapem Tabaty, podgląd cyklu |
| [gotowe-programy-player.png](gotowe-programy-player.png) | Player w stałym protokole: tabata z numerem rundy, oddech w rytmie, zatrzymanie bez limitu, podsumowanie bez „Więcej/Mniej” |
| [motywy.png](motywy.png) | Trzy motywy do wyboru (D-39, 2026-09-19) na ekranie „Dziś”: Ogień i Krew, Papier i Ogień, Stal i Bursztyn. Jedyny obrazek w tym katalogu rysowany kodem, nie zrzut z telefonu — pokazuje kolory w rolach, nie dokładny układ ekranu |
| [biblioteka.png](biblioteka.png) | Biblioteka (D-38, 2026-09-16): kafelki z ilustracjami, rozwinięty panel filtrów (sprzęt, rodzaj, poziom, partie), lista wierszy |

`health-connect.png` pochodzi z aplikacji Health Connect, nie z naszego interfejsu.

Zrzuty odświeżamy ręcznie po zmianach w UI. Przebieg jednego treningu (kreator → player → podsumowanie)
potrafi zebrać `python tools/przebieg.py` na emulatorze — patrz [testing.md](../testing.md);
gotowe programy (katalog → Tabata → player → Wim Hof) zbiera `python tools/przebieg_preset.py`,
a bibliotekę (kafelki, lista, panel filtrów, szukanie) `python tools/przebieg_biblioteka.py`.
Katalog `przebieg/` to taki przebieg z 2026-09-08 w realnym tempie (`--tempo 3`, najniższe ustawienia kreatora)
plus dwa zrzuty steppera; prezentację z tych zrzutów składa `python docs/prezentacja/deck.py`.
