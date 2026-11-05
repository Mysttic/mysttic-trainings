# Wersja aplikacji

1.2.0

# Wersja bazy ćwiczeń

11

---

Tu mieszkają dwa numery: aplikacji i bazy ćwiczeń, która od 1.1.0 ma własne
wydania (D-47).

## Aplikacja

Podniesienie numeru aplikacji i scalenie do `master` to jedyny sposób, w jaki
powstaje nowe wydanie aplikacji: [`.github/workflows/wydanie.yml`](.github/workflows/wydanie.yml)
rusza przy każdej zmianie tego pliku, ale wydaje tylko numer, którego tagu
jeszcze nie ma. Podbicie samej bazy niczego więc w nim nie wydaje.

Numer trzyma się `major.minor.patch`. Workflow czyta pierwszą linię, która jest
samym numerem, więc reszta pliku może być czymkolwiek.

`app/build.gradle.kts` czyta ten plik przy każdym buildzie, więc `versionName`
i `versionCode` nie mają jak się rozjechać z tym, co tu stoi. `versionCode`
powstaje z numeru: `major * 10000 + minor * 100 + patch` (0.1.0 → 100), bo
Android nie przyjmie aktualizacji z numerem nie wyższym niż poprzedni.

Podniesienie numeru razem z nagłówkiem w CHANGELOG.md:

```
python tools/bump_version.py 0.2.0
```

## Baza ćwiczeń

Numer bazy to sama liczba, pierwsza niepusta linia pod nagłówkiem „Wersja bazy
ćwiczeń”. Ten sam numer stoi w `baza/exercises.json` jako `version`, bo stamtąd
czyta go aplikacja; zgodność obu pilnują `python tools/baza.py sprawdz`
i `SeedIntegrityTest`. Każda zmiana treści bazy podnosi numer, a wydanie bazy
robi [`.github/workflows/baza.yml`](.github/workflows/baza.yml) po scaleniu
zmiany w `baza/` do `master`. Jego opis to sekcja `## [Baza N]` w CHANGELOG.md.

Podniesienie numeru w obu plikach razem z nagłówkiem tej sekcji:

```
python tools/baza.py podbij
```
