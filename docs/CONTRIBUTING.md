# Jak pracujemy w tym repo

Projekt jest jednoosobowy, ale proces jest taki, żeby dało się do niego wrócić po miesiącu
i wiedzieć, co robić. Zasady techniczne (co gdzie mieszka, czego nie wolno) są w [CLAUDE.md](../CLAUDE.md).

## Gałęzie

- `master` — gałąź wydaniowa. Trafiają na nią wyłącznie scalone pull requesty. To z niej powstają wydania.
- `develop` — gałąź robocza, domyślna do codziennej pracy. Stąd idą PR-y do `master`.
- Gałęzie tematyczne (`temat/cos`) są opcjonalne i wychodzą z `develop`.

Nazwa `master` zamiast `master` to jedyna różnica wobec bliźniaczego projektu Chatt.

## Cykl zmiany

1. Praca na `develop`, commity po polsku, w trybie rozkazującym, pierwszy wiersz do 72 znaków.
2. Testy lokalnie: `gradlew.bat testDebugUnitTest`. Zmiana w `domain/` bez testu nie wchodzi.
3. Zmiana widoczna dla użytkownika = wpis w sekcji **Niewydane** w [CHANGELOG.md](../CHANGELOG.md).
4. Decyzja projektowa, z której ktoś kiedyś będzie się tłumaczył = wpis w [DECISIONS.md](DECISIONS.md).
5. Gdy zebrało się na wydanie: `python tools/bump_version.py 0.2.0` (podnosi [VERSION.md](../VERSION.md)
   i zamyka sekcję „Niewydane” datą).
6. PR z `develop` do `master`. Workflow **Testy** uruchamia testy jednostkowe i build debugowego APK,
   a w podsumowaniu pisze, czy ten PR wyda wersję i z jakim opisem.
7. Scalenie PR-a. Jeśli w zmianach był `VERSION.md`, workflow **Wydanie** buduje podpisany APK i tworzy
   wydanie na GitHubie z opisem z CHANGELOG-a.

PR bez zmiany `VERSION.md` jest w porządku — po prostu nie wyda wersji. Wydanie bez opisu w CHANGELOG-u
nie powstanie: workflow zatrzyma się z błędem, zanim cokolwiek zbuduje.

## Numer wersji

`major.minor.patch`, [semver](https://semver.org/lang/pl/). Numer mieszka w `VERSION.md` i tylko tam:
`app/build.gradle.kts` czyta go przy każdym buildzie i wylicza z niego `versionCode`
(`major * 10000 + minor * 100 + patch`). `minor` i `patch` muszą zostać poniżej 100.

- `patch` — poprawka, która nic nie zmienia w tym, jak się aplikacji używa.
- `minor` — nowa funkcja albo zauważalna zmiana zachowania.
- `major` — zostawiony na moment, w którym plan albo dane zmienią się nieodwracalnie.

## Testy

`app/src/test` trzyma testy warstwy `domain/` — czysty Kotlin, bez Androida i bez Robolectrica.
Reguły, które da się wyrazić bez telefonu (silnik, planner, generator planu, progresja, statystyki,
przypomnienia, aktualizacje), mają tam swoje testy i to jest miejsce, w którym łapiemy błędy.
Reszta (UI, serwis, Room) jest sprawdzana ręcznie na emulatorze — patrz [testing.md](testing.md).
