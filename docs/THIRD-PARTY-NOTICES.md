# Materiały i biblioteki zewnętrzne

Kod projektu jest na licencji MIT ([LICENSE](../LICENSE)), a baza ćwiczeń w `baza/` na
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.pl), autor: Mysttic. Poniżej to, co pochodzi
z zewnątrz i ma własne licencje.

## Ćwiczenia i zdjęcia

| Źródło | Co stamtąd jest | Licencja |
|---|---|---|
| [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (yuhonas) | poziom, partie mięśniowe i kategoria większości ćwiczeń w `baza/` | [Unlicense](https://unlicense.org/) (domena publiczna) |

Wszystkie obrazy w `baza/images/` są od seedu v5 własnymi ilustracjami, generowanymi na potrzeby
tego projektu (D-36), i nie wymagają atrybucji. Wcześniejsze wydania miały zdjęcia z free-exercise-db,
a ośmiu ćwiczeń z [wger](https://wger.de) na [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.pl)
(autorzy: Settebello, erikocobra, AlucardEvil40, społeczność wger); żadne z nich nie jedzie już w aplikacji.
Widać je jeszcze na zrzutach ekranu w `docs/screenshots/przebieg/` z września 2026 (na przykład bird dog
i bieg w miejscu): te rysunki z wger są tam na CC BY-SA 4.0 z atrybucją jak wyżej.

Polskie nazwy i instrukcje ćwiczeń są napisane na potrzeby tego projektu.

## Krój pisma

| Źródło | Co stamtąd jest | Licencja |
|---|---|---|
| [Sora](https://fonts.google.com/specimen/Sora) (Sora Project Authors) | `app/src/main/res/font/sora.ttf`, wariant zmienny osadzony w aplikacji | [SIL Open Font License 1.1](https://openfontlicense.org/) |

## Biblioteki

Wersje pochodzą z `gradle/libs.versions.toml` i są zgodne z zestawem z próbki
[JetNews](https://github.com/android/compose-samples).

| Biblioteka | Licencja |
|---|---|
| AndroidX (Core, Activity, Lifecycle, Navigation, Compose, Room, DataStore, Health Connect) | Apache 2.0 |
| Jetpack Compose Material 3 | Apache 2.0 |
| Dagger Hilt | Apache 2.0 |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache 2.0 |
| Coil | Apache 2.0 |
| JUnit 4 | Eclipse Public License 1.0 |

## Dźwięki

Próbki sygnałów w `res/raw` są generowane skryptem `tools/build_beeps.py` (biblioteka standardowa
Pythona), więc nie pochodzą z żadnego zewnętrznego zbioru.
