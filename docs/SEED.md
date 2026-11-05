# Baza ćwiczeń

Od D-47 baza ćwiczeń ma własny katalog, własny numer i własne wydania, więc zmiana ćwiczenia nie wymaga nowej
wersji aplikacji. Jedynym źródłem prawdy jest katalog `baza/` w korzeniu repozytorium:

```
baza/exercises.json      numer, format, minApp i lista ćwiczeń
baza/images/<id>.jpg     jedna ilustracja na ćwiczenie (480 px szerokości, JPEG q72, od 4 do 30 kB)
```

Gradle podpina `baza/` jako assety APK (`app/build.gradle.kts`), więc aplikacja widzi `exercises.json`
i `images/<id>.jpg` w korzeniu assetów. W `baza/` leżą wyłącznie te dane, bo wszystko inne pojechałoby do aplikacji
(pilnuje tego `SeedIntegrityTest`). Szkice, czyli ilustracje i definicje, które jeszcze nie weszły do bazy, czekają
w `tools/szkice/`.

Stan: baza v10, 283 ćwiczenia (w tym 20 na ergometry, D-44), każde z ilustracją generowaną (D-36). Szablonów treningów
baza nie wozi od v7 (D-43): kolejkę układa kreator, gotowy program albo plan własny (D-42). Cztery ćwiczenia oddechowe
(kategoria `BREATHING`, bez partii mięśniowych) żyją tylko w gotowych programach (D-37): kreator, zamienniki
i wyszukiwarka edytora treningu ich nie biorą.

## Format

`exercises.json` to obiekt z czterema polami:

| pole | znaczenie |
|---|---|
| `version` | numer bazy; rośnie z każdą zmianą treści i ciągnie dalej dawny `SEED_VERSION` (v9 był ostatnim seedem w assetach); ten sam numer stoi w `VERSION.md` pod „# Wersja bazy ćwiczeń” |
| `format` | znaczenie pól, dziś 1; pole dodane z wartością domyślną formatu nie zmienia, zmiana znaczenia istniejącego pola tak |
| `minApp` | najniższa wersja aplikacji, która przyjmie tę bazę; nie wyżej niż numer z `VERSION.md`, bo baza jedzie w tym APK |
| `exercises` | lista ćwiczeń |

Pola ćwiczenia:

| pole | znaczenie |
|---|---|
| `id` | kebab-case, do 64 znaków, stałe (trafia do historii); prefiks `custom-` jest zarezerwowany dla plików planów (D-42) |
| `sourceId` | id w free-exercise-db, gdy ćwiczenie stamtąd pochodzi |
| `nameEn`, `namePl` | nazwy, do 80 znaków |
| `kind` | `REPS` albo `TIME`: z niego bierze się cel w planie (D-27) |
| `level`, `category` | `Difficulty` i `ExerciseCategory` |
| `equipment` | sprzęt wymagany w całości, pusta lista = bez sprzętu (D-07) |
| `primaryMuscles`, `secondaryMuscles` | `MuscleGroup` |
| `instructionsPl` | od 1 do 10 kroków po polsku, każdy do 500 znaków |
| `images` | zawsze `["images/<id>.jpg"]` |
| `easierId`, `harderId` | warianty o poziom niżej i wyżej, tego samego rodzaju celu, bo podmiana wariantu przenosi cel |
| `defaultReps`, `defaultSeconds` | cel domyślny: od 1 do 30 powtórzeń, od 5 do 900 s (widełki pliku planu, `PlanFileCodec`) |
| `retiredAt` | data wycofania `RRRR-MM-DD`, tylko u wycofanych: ćwiczenie zostaje dla historii i planów, ale znika z biblioteki, kreatora i zamienników; nie wskazuje go żaden wariant ani gotowy program |

Wartości enumów pochodzą z `domain/model/Enums.kt`. Nieznanego pola walidator nie przepuści, żeby literówka nie
przeszła po cichu (aplikacja nieznane pola pomija).

## Reguły zgodności (D-47)

Bazę zobaczy każda zainstalowana wersja aplikacji, która umie ją pobrać, więc:

- zmiana treści (ćwiczenia, ilustracje, `format`, `minApp`) to wyższy `version`, inaczej telefon z tym numerem
  jej nie wczyta;
- id raz wydanego ćwiczenia nie znika i nie zmienia `kind`: historia, notatki, trener, plany i programy wskazują
  po id, a rodzaj celu siedzi w zapisanych blokach;
- wartość enuma, której nie ma w `Enums.kt` ostatniego wydanego tagu `v<x>`, wymaga `minApp` wyższego niż `x`.

Reguły danych sprawdza `python tools/baza.py sprawdz`, a z `--wzgledem <REF>` także zgodność wstecz. W CI pilnują
bazy `SeedIntegrityTest` i `PresetSeedConsistencyTest` (gotowe programy wskazują ćwiczenia po id i rodzaju celu).

## Edytor bazy

`python tools/baza.py` (albo `python tools/baza.py edytor --port 8770 --bez-otwierania`) stawia lokalny serwer na
`127.0.0.1` i otwiera w przeglądarce kartotekę z trybem edycji:

- **Dodaj ćwiczenie**: formularz z ilustracją (przeciągnij albo wybierz plik; JPEG z generatora do 480 px wchodzi bez
  zmian, inny obraz przeskaluje Pillow), nazwami, rodzajem i celem domyślnym, poziomem,
  kategorią, sprzętem, mięśniami (pierwszy główny to partia główna), krokami i wariantami.
- **Id** układa się samo z nazwy (angielskiej, a gdy pusta, polskiej), więc pole jest schowane: pod nazwą stoi
  „Id: … · zmień”, a „zmień” pokazuje je przed pierwszym zapisem. Odsłania się też samo, gdy zapis odrzuci id
  (powtórzone albo w złym formacie). Po id aplikacja łączy historię, plany i warianty, więc po zapisie jest stałe.
- **Edytuj** w panelu ćwiczenia: id pokazane jako stałe, a rodzaj celu ćwiczenia, które jest już w `origin/master`,
  zablokowany.
- **Wycofaj / Przywróć**: wycofanie wpisuje dzisiejszą datę w `retiredAt`. Edytor nie wycofa ćwiczenia, które jest
  wariantem innego albo którego używa gotowy program (`PresetDefinitions.kt`).
- **Szkice**: ćwiczenie bez ilustracji („Zapisz jako szkic”) albo ilustracja bez definicji (z paczki generatora) czeka
  w `tools/szkice/`; „Uzupełnij” kończy szkic i przenosi go do bazy. „Wczytaj paczkę z generatora (ZIP)” robi to samo
  co `partia`.
- **Podbij numer** i pasek stanu z tymi samymi regułami co `sprawdz --wzgledem origin/master`.

Każdy zapis przechodzi reguły całej bazy, a odrzucony pokazuje powody w formularzu. Serwer przyjmuje zapis tylko
z własnej strony (nagłówek `X-Baza`, adres lokalny) i niczego nie commituje.

## Jak zmienić bazę

1. Zmień bazę w edytorze albo wprost w `baza/exercises.json` i `baza/images/`. Nową ilustrację z generatora zdjęć
   (paczka ZIP z `manifest.json`, „Pobierz tylko nowe”) wczytuje edytor albo `python tools/baza.py partia <zip>`:
   obraz ćwiczenia z bazy podmienia, obraz ćwiczenia, którego w bazie nie ma, odkłada do `tools/szkice/`. Ćwiczenie
   wchodzi do bazy dopiero z definicją i ilustracją naraz, tak jak przed D-47 definicja w `ILLUSTRATED` czekała
   na obraz (D-36).
2. Podbij numer (edytor albo `python tools/baza.py podbij`, z `--min-app X.Y.Z`, gdy baza używa czegoś, czego starsza
   aplikacja nie zna). Podbicie zmienia numer w `baza/exercises.json` i w `VERSION.md` i dopisuje w `CHANGELOG.md`
   pusty nagłówek `## [Baza N]`. Numer liczy się względem tego, co już wyszło z `master`: kolejne zmiany przed
   wydaniem zostają przy tym samym numerze, więc drugie podbicie przed wydaniem `podbij` odrzuca.
3. Opisz wydanie w sekcji `## [Baza N]` w `CHANGELOG.md`: co przybyło, co się zmieniło, co wycofane. Ten opis trafia
   do wydania bazy na stronie; bez niego `sprawdz --wzgledem` i `paczka` się zatrzymują. Sekcję ma każde wydanie bazy,
   także to, które wychodzi razem z aplikacją (wtedy wystarczy krótko).
4. `python tools/baza.py sprawdz --wzgledem origin/master`.
5. Aplikacja przy starcie wczytuje bazę z wyższym numerem (`SeedImporter`): `upsert` ćwiczeń wbudowanych, a własne
   ćwiczenia użytkownika (`isCustom = true`) bez zmian. Przy okazji importu znikają wiersze, do których nie prowadzi już
   żaden ekran: wbudowane treningi z dawnych seedów i treningi planów osierocone przez „Usuń plan” w wydaniach sprzed
   0.9.1 (D-43). Treningów, które mają historię, import nie rusza.

Skalowanie obrazu innego niż JPEG do 480 px (edytor, `partia`) potrzebuje Pillow (`pip install pillow`); reszta
narzędzia, także `sprawdz` w CI, bierze tylko bibliotekę standardową.

## Wydanie bazy

Zmiana w `baza/` idzie przez `develop` i PR do `master` jak kod; przy PR-ze `testy.yml` sprawdza ją względem
`master`. Po scaleniu workflow **Baza** (`.github/workflows/baza.yml`) wydaje ją sam: paczka z
`python tools/baza.py paczka` (ZIP i manifest), wydanie `mysttic-trainings-baza-v<numer>` w `Mysttic/Mysttic`
z opisem z sekcji `[Baza <numer>]` w `CHANGELOG.md` i tag `baza-v<numer>`. Aplikacja nie musi wychodzić razem
z bazą, a strona projektu pokazuje numer i datę najnowszego wydania bazy pod numerem aplikacji. Szczegóły i umowa nazw: `docs/RELEASE.md`.

## Jak przejrzeć bazę

Edytor pokazuje bazę na żywo. Statyczną kartotekę bez edycji, do wysłania albo obejrzenia na telefonie,
`python tools/przeglad.py` składa z `baza/` w jedną stronę `build/przeglad/index.html` i otwiera ją w domyślnej
przeglądarce (`--bez-otwierania` pomija ten krok): kontaktówka wszystkich ćwiczeń, filtry (kategoria, poziom, cel,
sprzęt, partia), a po kliknięciu panel z ilustracją, opisem, mięśniami, celem domyślnym i wariantami. Etykiety polskie
strona czyta z `domain/model/Enums.kt`, więc nie rozjeżdżają się z aplikacją, a przełącznik „Zdjęcia jak w aplikacji”
nakłada ten sam filtr barwny co `ui/common/Components.kt` (szarość, sepia, nasycenie, jasność), do oceny, jak ilustracja
wygląda na ekranie, a nie w pliku.

Strona odwołuje się do ilustracji w `baza/images/`, więc po zmianie bazy wystarczy ją odświeżyć. `--osadz` wkleja
ilustracje do pliku (jeden samodzielny HTML ok. 5 MB, do wysłania albo obejrzenia na telefonie), `--artefakt` zdejmuje
szkielet `<html>`.

## Skąd są dane

Podstawą był [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (yuhonas, Unlicense, domena publiczna):
ćwiczenia z `sourceId` mają stamtąd poziom, partie mięśniowe i kategorię. Do v9 dociągał je przy każdym uruchomieniu
skrypt `tools/build_seed.py`; przy przeniesieniu do `baza/` (D-47) wartości zostały zamrożone w `exercises.json`,
więc zmiany w datasecie już do bazy nie spływają. Polskie nazwy i instrukcje są pisane na potrzeby projektu.

Mapowania z datasetu, gdyby kolejne ćwiczenie miało stamtąd przyjść:

| dataset | aplikacja |
|---|---|
| `level` beginner / intermediate / expert | `Difficulty` BEGINNER / INTERMEDIATE / ADVANCED |
| `category` strength / plyometrics / cardio / stretching (inne → strength) | `ExerciseCategory` |
| `primaryMuscles`, `secondaryMuscles` (17 nazw; middle back → UPPER_BACK) | `MuscleGroup` |
| `equipment` (body only, dumbbell, …) | ustawiane ręcznie per ćwiczenie |
| `force: static` | zwykle `kind = TIME` |

Ilustracje generuje właściciel w Notion AI (płaska grafika, niebieska koszulka, białe tło, strzałki kierunku ruchu)
i przyjmuje w `generator-zdjec*.html` po kontroli z opisem ćwiczenia (D-36). Nie wymagają atrybucji. Od v5 w bazie
nie ma żadnego zdjęcia z sieci, a od D-47 ilustracje leżą w repo raz, w `baza/images/` (wcześniej także
w `tools/zdjecia/`). Partie:

| partia | seed | strona | co weszło |
|---|---|---|---|
| pierwsza (2026-09-09) | v3 | `generator-zdjec.html` | 45 nowych ćwiczeń |
| druga (2026-09-10) | v4 | ten sam generator domknięty do końca + `generator-zdjec-niepewne.html` | 66 obrazów: 62 nowe ćwiczenia i cztery, które nie miały żadnego zdjęcia |
| trzecia (2026-09-13) | v5 | `generator-zdjec-wymiana.html` | 77 ilustracji w miejsce zdjęć z sieci, czyli cała reszta biblioteki |
| czwarta (2026-09-15) | v6 | `generator-zdjec-programy.html` | 6 ćwiczeń gotowych programów: oddech Wima Hofa (4), kobra, skręt w leżeniu |
| szósta (2026-09-25, D-44) | v8 | `generator-zdjec-nowe.html` | 60 ćwiczeń z issue #20: 20 na ergometry, 40 ze sprzętem |
| siódma (2026-09-25) | v9 | `generator-zdjec-uzupelnienie.html` | 29 ćwiczeń: dziury kreatora, wąskie partie, sztanga |

## Wybór ćwiczeń

Kryteria: wykonalne w domu (masa ciała, hantle, kettlebell, guma, drążek, krzesło albo ławka, skakanka, sztanga,
ergometry), ilustracja pokazuje tę samą wersję ruchu co polska instrukcja. Pominięte świadomie: ćwiczenia na maszynach
siłowych, z partnerem i z ławką skośną.

Nie zmieniaj `id` istniejącego ćwiczenia: `set_logs.exerciseId` w historii by osierociało. Ćwiczenia z bazy nie da się
usunąć z tego samego powodu.
