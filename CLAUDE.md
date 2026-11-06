# mysttic-trainings — instrukcje dla Claude Code

Osobista aplikacja Android do treningów w domu, z publicznym kodem (D-48). Sercem jest kreator planu (D-20): użytkownik podaje wzrost, wagę, cel, sprzęt
i ile czasu ma, a dostaje gotowy cykl treningowy do uruchomienia; po zakończeniu generuje kolejny z zaktualizowanymi danymi.
Do tego prowadzenie przez trening (odliczanie, powtórzenia, sygnały), poziom trudności zmienny w każdej chwili, lokalna historia.
Bez reklam, bez konta, bez sieci. Jeden użytkownik (właściciel repo), jeden telefon. Package: `pl.mysttic.trainings`.

## Stan projektu (2026-11-05, wydane 1.1.0 i baza v10)

Aplikacja robi wszystko, co miała robić: kreator planu (D-20) albo gotowy program (D-37) układa cykl, player prowadzi
przez trening w foreground service (D-16), historia liczy statystyki i wykresy, trener koryguje cele (D-35), biblioteka
ma kafelki i panel filtrów (D-38). Wygląd to jeden z czterech motywów do wyboru, domyślnie „Ogień i Krew” (D-32, D-39, D-56).
Wydania jadą na publiczną stronę `Mysttic/Mysttic` i stamtąd aplikacja sama się aktualizuje (D-33). Ostatnie wydanie
to 1.1.0 z bazą ćwiczeń v10 (2026-10-28): baza ćwiczeń rozwijana i wydawana niezależnie od aplikacji (D-47).
Od 2026-11-05 to repozytorium jest publiczne i zaczyna od stanu 1.1.0 jednym commitem z tagami `v1.1.0` i `baza-v10`
(D-48): kod na MIT, baza ćwiczeń na CC BY 4.0. Historia sprzed 1.1.0, dawne pull requesty i zamknięte issue
nie przeszły do tego repozytorium; przeszły wydanie 1.1.0 z APK i otwarte issue (#1 do #3).

Liczby: baza ćwiczeń v11 w `baza/` (283 ćwiczenia, każde z jedną ilustracją generowaną, bez szablonów treningów,
i 36 zdjęć tła, D-51), 509 testów jednostkowych w 55 klasach, Room v6, kopia zapasowa v5, jedenaście gotowych programów.

Weryfikacja stoi na emulatorze Pixel_9_Pro (API 37), dla dużych ekranów na AVD Pixel_Tablet_API_37 (D-42), i na skryptach
`tools/przebieg*.py`. Decyzja właściciela z 2026-09-18: **telefon nie jest bramką wydania**, emulator wystarcza.

W 1.0.0 weszły: plany do udostępniania (D-42: lista planów, edytor planu, plik planu, statystyki planu z PDF, duże ekrany),
sprawdzone na emulatorze według `docs/testing.md`; dług techniczny z D-43,
czyli progresja liczona od celu pokazanego, sprzęt sprawdzany przy podmianie wariantu, okno EMOM w modelu, poziom w historii
także przy przerwaniu i w gotowym programie, powiadomienie wracające po nadaniu zgody, sprzątanie resztek po starszych
„Usuń plan” oraz seed v7 bez wbudowanych treningów. Issue #20 (D-44): ergometry, cele na czas do godziny i notatki
do ćwiczeń w planie; 60 ćwiczeń z issue #20 (20 na ergometry, 40 ze sprzętem) weszło z ilustracjami w seedzie v8,
a 29 uzupełniających (sztanga, zamienniki wąskich partii, plyometria, warianty bez sprzętu) w seedzie v9. Issue #21
i #22 (D-46): sprzęt w gotowych programach (zamienniki albo blokada), trudność programów na liście, pominięte ćwiczenia
w podsumowaniu, skala wysiłku z podpisami i deska boczna z przerwą na zmianę strony.

W 1.1.0 (D-47): baza ćwiczeń przeniesiona z assetów i `tools/build_seed.py` do `baza/` jako v10, z narzędziem
i edytorem `tools/baza.py` (dodawanie z ilustracją, edycja, wycofanie z datą `retiredAt`, szkice). Aplikacja rozumie
`retiredAt` (Room v6): wycofane znika z biblioteki, kreatora, zamienników i wyszukiwarki edytora, a zostaje w historii
i planach z datą w szczegółach ćwiczenia. Workflow `baza.yml` wydaje bazę po scaleniu zmiany w `baza/` do `master`,
a `testy.yml` sprawdza ją przy PR-ze. Aplikacja pobiera nowszą bazę z Ustawień (manifest, ZIP z SHA-256,
`DatabaseArchive`, `ExerciseDatabaseCheck`, import do `files/baza/<n>/`), a build debug wczytuje ją też z pliku;
pobieranie przez sieć sprawdzone na emulatorze na wydaniu v10.

W 1.2.0 (niewydane, D-49, issue #1): nowy plan własny startuje dosłownie (przełącznik „Dopasowuj cele…” wyłączony),
„×2” przy treningu planu robi jego kopię w tym samym planie (`PlanEditing.copyName`, `PlanEditing.copyBlocks`),
a Wstecz z edycji zapisanego planu pyta o zmiany także po dodaniu treningu. Do tego tło (issue #6): pod ekranami
zawsze leży zdjęcie dnia (D-51, D-55), rozmyte zdjęcie z bazy ćwiczeń 11 (`baza/tla.json`, `baza/tla/`) losowane raz
dziennie z trzech tematów, których najwięcej w dzisiejszych treningach wszystkich planów, z osobami płci z „O mnie”;
nic w aplikacji nie nazywa partii dnia, a karty są szklane (`cardColor()`). Na zdjęciu leży do wyboru jeden z trzech
gradientów motywu (D-50) z kryciem 0,4 albo nic („Samo zdjęcie”, domyślne); wybór osobno dla każdego motywu
(`ui/theme/Backgrounds.kt`), kopia zapasowa v5 go niesie; baza 11 wymaga aplikacji 1.2.0. Zdjęcia to własny komplet
właściciela z generatora (issue #8): prompty i instrukcja dla agenta generującego leżą w `tools/tla_prompty.json`, a karty
z nimi pokazuje sekcja „Zdjęcia tła” edytora bazy. Czwarty motyw „Brzoza i Mech”
(D-56) jest jasny, leśny. „O mnie” (D-57) zbiera imię, płeć, wzrost i wagę, a kreator zaczyna od sprzętu. Plik planu
(D-58) jest podpisany kluczem telefonu z Android Keystore i imieniem z „O mnie”, na życzenie zamknięty hasłem.
Także w 1.2.0 nowy interfejs z issue #5 (D-52): projekt w designerze (canvas „mysttic-trainings: nowy interfejs”),
nawigacja z własnymi ikonami, „Dziś” z mniejszą ilością informacji na start, biblioteka z filtrami w polu szukania
i zwijanymi szczegółami, Ustawienia w kategoriach (od D-57 siedmiu: „O mnie” pierwsze, Wygląd zbiera motyw i tło), prostszy
player (poziom i przerwanie w menu ⋮), lista i szczegół obok siebie na tablecie — oraz reszta issue #1 (D-53): zakładka
Plany w głównej nawigacji z „Nowy plan”. Resztę issue #5 domyka D-54: Historia (na tablecie lista i szczegół sesji),
podgląd planu tygodniami, kreator ze streszczeniem i edytory planu i treningu w tym samym języku, z menu ⋮ na rzadsze
akcje i bez glifów Materiala w `ui/`. Układ sprawdzają zrzuty `LayoutScreenshotTest`, a przejście na emulatorze
(`docs/testing.md`, „Nowy interfejs”) i zrzuty w `docs/screenshots` są po D-54 (2026-11-06).

Zmiany idą do `CHANGELOG.md` pod wersją, do której wejdą; `docs/PLAN.md` nie prowadzimy (decyzja właściciela z 2026-10-28).

## Komendy

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr   # brak JDK na PATH; w bash: export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
gradlew.bat assembleDebug          # debug APK: app/build/outputs/apk/debug/app-debug.apk
gradlew.bat installDebug           # instalacja na podłączonym telefonie (adb); przy kilku urządzeniach: set ANDROID_SERIAL=<serial>
gradlew.bat testDebugUnitTest      # testy jednostkowe (domain, serwis, motyw, kopia, seed, sprzątanie w Room) — obowiązkowe po zmianach w domain/
gradlew.bat testDebugUnitTest -Proborazzi.record=true  # zapis wzorców zrzutów palet (D-40) i układu ekranów (uklad/, D-52) do app/src/test/screenshots
gradlew.bat assembleRelease        # release (niepodpisany bez keystore — patrz docs/RELEASE.md)
python tools/baza.py               # edytor bazy ćwiczeń w przeglądarce (127.0.0.1:8770): dodawanie z ilustracją, edycja, wycofanie, szkice, zdjęcia tła
python tools/baza.py sprawdz --wzgledem origin/master  # reguły bazy ćwiczeń i zgodność wstecz (D-47), obowiązkowo po zmianie w baza/
python tools/baza.py podbij        # numer bazy +1 w exercises.json i VERSION.md, nagłówek [Baza N] w CHANGELOG.md (--min-app X.Y.Z, gdy baza wymaga nowszej aplikacji)
python tools/baza.py partia <zip>  # ilustracje z generatora zdjęć (D-36): do baza/images/ albo do tools/szkice/
python tools/baza.py tlo <zdjęcie> --temat CORE --plec FEMALE  # zdjęcie tła (D-51): kadr, szarość, rozmycie, do baza/tla/
python tools/baza.py paczka build/wydanie-bazy --wzgledem baza-v10  # ZIP, manifest i opis wydania bazy (to samo robi baza.yml)
python tools/przeglad.py           # kartoteka ćwiczeń: build/przeglad/index.html, od razu w przeglądarce (--osadz = jeden plik ze zdjęciami)
python tools/build_beeps.py        # regeneracja próbek beep do res/raw (biblioteka standardowa)
python tools/check_schema.py       # zgodność docs/DATABASE.md z wyeksportowanym schematem Room — obowiązkowo po zmianie encji
python tools/check_contrast.py     # kontrast par wszystkich schematów z ui/theme/Theme.kt — obowiązkowo po zmianie motywu
python tools/bump_version.py X.Y.Z # podniesienie numeru w VERSION.md razem z nagłówkiem w CHANGELOG.md
python tools/release_notes.py X.Y.Z  # opis wydania z CHANGELOG.md dla workflow (część umowy wydaniowej); --baza N dla wydania bazy
python tools/przebieg.py           # przeklikuje jeden trening na emulatorze przez adb i zapisuje zrzuty do build/przebieg/ (czyści dane apki)
python tools/przebieg_preset.py    # gotowe programy na emulatorze: Tabata z katalogu, player z rundami, Wim Hof (rytm, krok bez limitu); zrzuty w build/przebieg-preset/
python tools/przebieg_biblioteka.py # biblioteka na emulatorze: kafelki i lista, panel filtrów, chipy, licznik, czyszczenie, szukanie; zrzuty w build/przebieg-biblioteka/
```

Wymagania: JDK 17+ (lokalnie JBR 21 z Android Studio, D-14), Android SDK z platformą 37 (AGP dociąga automatycznie), Python 3 (+ Pillow do `tools/baza.py partia` i `tlo`).

## Struktura

```
app/src/main/java/pl/mysttic/trainings/
  domain/model/      Exercise, Workout(Block, BlockItem), Session, SetLog, enumy — czysty Kotlin
  domain/engine/     WorkoutPlanner (szablon → kroki), WorkoutEngine (maszyna stanów), DifficultyProfile, Announcer (PL)
  domain/equipment/  EquipmentMatcher — braki sprzętu w treningu i zamienniki (filtrowanie biblioteki przeszło do domain/library, D-38)
  domain/stats/      SessionStats, ProgressStats, PlanStats — statystyki okresu, filtry, delty, dane pod wykresy, przebieg jednego planu (czysty Kotlin)
  domain/editor/     WorkoutEditing — operacje edytora na blokach, walidacja, zakresy (czysty Kotlin)
  domain/library/    LibraryFilter, LibraryFacets — filtr biblioteki: wymiary „i”, wartości „lub”, chipy tylko z wartości obecnych w seedzie (czysty Kotlin)
  domain/plan/       TrainingProfile, PlanGenerator, Program, PlanProgress, PlanProgression, SetProgression — kreator cyklu, kolejka, progresja, „Podbij cele” (czysty Kotlin, D-20..D-24)
  domain/plan/preset/ PresetId, PresetDefinition, Stages, PresetCatalog, PresetDefinitions — gotowe programy: tabele etapów i budowa cyklu (czysty Kotlin, D-37)
                     PlanSessions (sesja → plan), PlanCopy, PlanEditing, PlanList, PlanRhythm — lista planów i edytor planu (D-42)
  domain/plan/share/ PlanFile, PlanFileCodec — plik planu do udostępniania: treść, eksport, wrogi odczyt, ćwiczenia z pliku (D-42);
                     PlanSeal — koperta formatu 2: podpis ECDSA, hasło AES-GCM, odcisk klucza (D-58)
  domain/report/     PlanReport, PlanReportBuilder, ReportPaginator — raport z przebiegu planu jako linie, podział na strony (D-42)
  domain/coach/      ExerciseFeedback, ExerciseTuning, Coach — trener: opinie z podsumowania, korekta celu, wykluczenia (czysty Kotlin, D-35)
  domain/reminder/   ReminderSchedule — kiedy i o czym przypomnieć (czysty Kotlin, D-29)
  domain/health/     HealthExport — co i kiedy zapisujemy w Health Connect (czysty Kotlin, D-31)
  domain/background/ PhotoTopic, PhotoPeople, PhotoPicker — zdjęcie dnia: temat z ćwiczeń treningu, osoby, losowanie stałe przez dzień (D-51)
  domain/update/     UpdateCheck, ReleaseParser: porównanie wersji i odpowiedź GitHuba (czysty Kotlin);
                     DatabaseUpdate (wydanie bazy, manifest, decyzja) i ExerciseDatabaseCheck (wroga kontrola pobranej bazy, D-47)
  data/db/           Room: encje, DAO, konwertery, AppDatabase (schemat → app/schemas/)
  data/seed/         SeedImporter: asset exercises.json (z baza/) → Room (wersjonowany; przy podbiciu kasuje wbudowane treningi
                     bez historii i resztki skasowanych planów, D-43)
  data/backup/       BackupRepository + DTO — eksport i import JSON przez SAF (D-18)
  data/repo/ProgramRepository  cykle treningowe: zapis wygenerowanego planu, aktywny cykl (D-20)
  data/repo/CoachRepository    pamięć trenera: opinie, wykluczenie z podmianą w aktywnym cyklu, zerowanie (D-35)
  data/share/PlanShareRepository  plik planu: eksport do okna udostępniania i SAF, odbiór z intencji, podgląd, import (D-42)
  data/share/PlanSigningKey  klucz podpisu planów w Android Keystore, nie wychodzi z telefonu (D-58)
  data/settings/     DataStore: poziom, sprzęt, odliczanie, sygnały, motyw i tło każdego motywu (`BackgroundChoices`, D-50), „O mnie”: imię, płeć, wzrost, waga (D-57)
  data/background/   BackgroundPhotoRepository: zdjęcia tła z `tla.json` wczytanej bazy i zdjęcie dnia pod następny trening (D-51)
  data/repo/         repozytoria + mapowanie encja ↔ domena
  service/           PlayerController (stan silnika w procesie, D-16), WorkoutService (FGS: tick, wake lock, powiadomienie),
                     PlayerNotifications + NotificationContent (treść powiadomienia, czysty Kotlin), Cues (SoundPool/wibracja/TTS + audio focus), MonotonicClock
                     ReminderScheduler + ReminderReceiver + ReminderNotifications (codzienne przypomnienie, D-29)
                     HealthConnectExporter (opcjonalny zapis sesji, D-31)
                     NotificationGate (zbocze zgody na powiadomienia w trakcie treningu, czysty Kotlin, D-43)
  data/update/       UpdateRepository: pytanie o wydania (aplikacja i baza jednym zapytaniem), pobranie APK, systemowy instalator;
                     ExerciseDatabaseRepository (manifest, ZIP z SHA-256, kontrola, import bazy) i DatabaseArchive (rozpakowanie z limitami)
  di/                AppModule — jedyne miejsce z wiązaniami Hilta
  ui/                Compose + Hilt ViewModels: today, plan (kreator i podgląd cyklu), plans (zakładka Plany: lista i „Nowy plan”, D-53), planeditor, share (wysyłka i import planu),
                     stats (statystyki planu, PdfReportRenderer), presets (wybór gotowego programu), library, workout, editor, player (cienki widok
                     na PlayerController), history, settings (kategorie, SettingsSummary); navigation (trasy typowane, pasek i szyna z ikonami);
                     adaptive (AppLayout, szyna, czytelna szerokość, D-42; TwoPane dla Ćwiczeń i Ustawień, D-52, i Historii, D-54)
  ui/common/         DesignSystem.kt (karty, przyciski, steppery), Components.kt (ExerciseTile, ExerciseImage, EmptyState…), Charts.kt (wykresy na Canvas),
                     Structure.kt (zwijany wiersz, kategorie, pasek powrotu, znaczniki, D-52; menu ⋮ i cichy odsyłacz, D-54),
                     AppIcons.kt (własne ikony kreskowe, D-52)
  ui/theme/          Color.kt (motyw domyślny), Themes.kt (trzy pozostałe + AppTheme), Theme.kt (cztery schematy po 35 ról), Type.kt (Sora), Shape.kt,
                     Backgrounds.kt (warianty tła każdego motywu: `AppBackground`, `appBackground`, D-50)
app/src/test/        testy JUnit4: domain, service, data (kopia, seed, sprzątanie treningów na Room w pamięci), ui (motyw, zrzuty D-40, układ, saver opinii)
baza/                baza ćwiczeń (D-47): exercises.json + images/<id>.jpg, jedyne źródło prawdy; Gradle podpina katalog jako assety APK,
                     więc leżą w nim wyłącznie dane; od bazy 11 też zdjęcia tła: tla.json + tla/<id>.jpg (D-51)
tools/baza.py        edytor bazy (lokalny serwer + tools/przeglad.html w trybie edycji), sprawdzanie, numer, paczki z generatora zdjęć (D-36)
tools/szkice/        ilustracje i definicje, które jeszcze nie weszły do bazy (nie jadą do aplikacji)
tools/przeglad.py    kartoteka ćwiczeń z bazy jako strona HTML (przegląd ilustracji i opisów poza aplikacją)
docs/                PLAN, ARCHITECTURE, DATABASE (tabele, kolumny, klucze), DECISIONS, SEED, RELEASE, design-brief, site-page,
                     guide (użytkownik), poradnik-trenera (plan dla klienta krok po kroku), building, testing, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, privacy-policy,
                     THIRD-PARTY-NOTICES, screenshots, site, prezentacja
```

## Zasady

- `domain/` nie importuje Androida. Silnik to czysta funkcja `WorkoutEngine.reduce(state, event) -> (state, effects)`;
  zdarzenia niosą własne `now` (ms, zegar monotoniczny). Przejścia liczone z timestampów, tick tylko sprawdza, czy faza minęła.
- Każda zmiana w `domain/` (engine, equipment, stats, editor, library, plan, plan/preset, coach, reminder, health, update,
  background) = aktualizacja albo dopisanie testu w `app/src/test`. Dziś jest ich 509 w 55 klasach.
- `PlanGenerator` musi zostać deterministyczny (bez losowania): te same dane wejściowe = ten sam plan, inaczej testy tracą sens.
- Plan rozliczamy kolejką, nie datami (D-21): dzień jest odhaczony przez ukończoną sesję tego treningu, niezależnie kiedy.
- Plany (D-42): aktywny jest najwyżej jeden, pozostałe czekają z postępem; przełączyć wolno w każdej chwili. Trening należy
  do jednego planu — właściciela wyznacza prefiks id `"$programId-"`, a każda kopia planu (duplikat, powtórka, import) idzie
  przez `PlanCopy` z nowymi id. Sesja zapisuje `programId` przy starcie; przynależność sesji do planu liczy tylko `PlanSessions`
  (nie kopiować reguły). Zrobione dni planu nie zmieniają się przy edycji i zostają na swoich numerach
  (`PlanEditing.validate`). Plan od kogoś wchodzi dosłownie (bloki `fixed`), więc eksport liczy bloki adaptacyjne
  przez `WorkoutPlanner.shownBlocks` (poziom i sprzęt autora, bez trenera), a nie wysyła surowego szablonu. Plik planu (`domain/plan/share`) to osobny format ze stałymi `@SerialName`: zmiana pola =
  nowe pole z domyślną wartością, a pliki wzorcowe w `app/src/test/resources` muszą się dalej wczytywać.
  Od D-58 plik to koperta formatu 2 (`PlanSeal`): treść podpisana kluczem telefonu (`PlanSigningKey`, ECDSA P-256),
  na życzenie zamknięta hasłem (AES-256-GCM, PBKDF2-HMAC-SHA256); podpis niezgodny z treścią blokuje import, plik
  formatu 1 wchodzi jako niepodpisany. Eksport wymaga imienia z „O mnie” (`signer`), a autor planu od kogoś zostaje
  w pliku, także w kopii planu (`ProgramRepository.copyOf`). Wzorce formatu 2 (`plan-podpisany`, `plan-z-haslem`
  z hasłem `lesna-polana`) są umową jak `plan-wzorcowy`. Hasło nigdy nie trafia do `SavedStateHandle` ani na dysk.
- Jeden trening na ekranie (D-26): „Dziś” pokazuje wyłącznie następną pozycję z kolejki. Nie dokładać list treningów do wyboru
  ani ustawień poza kreatorem; wybór parametrów dzieje się przy zakładaniu cyklu. Lista planów to zakładka Plany w głównej
  nawigacji (D-53), z „Nowy plan” (kreator, gotowy program, własny plan, plik); „Dziś” odsyła do niej tylko przed pierwszym
  planem. Statystyki na „Dziś” pokazujemy dopiero z historią (D-52). Nowy plan własny startuje dosłowny (`literal = true`, D-49).
- Cel ćwiczenia bierze się z `Exercise.kind` (D-27): powtórzenia dla ćwiczeń na powtórzenia, sekundy tylko dla izometrii i cardio.
  `PlanGenerator` musi trafiać w deklarowany czas sesji z dokładnością `TIME_TOLERANCE_SEC` (60 s) — testy to sprawdzają.
- Objętość planu trzyma się poziomu (D-34): rundy i serie tylko z `PlanGenerator.repeatsRange`, czas wypełniają kolejne ćwiczenia
  (do `MAX_ITEMS`, w blokach po `BLOCK_ITEMS`) i przerwy. Nigdy nie dokładać rund ponad widełki, żeby trafić w czas — za mała
  biblioteka ma dać krótszy trening z notatką. Długość sesji ogranicza `TrainingProfile.minutesRange(level)`; kreator i `sanitize`
  muszą jej używać zamiast `MINUTES_RANGE`.
- Gotowe programy (D-37): `PresetCatalog.build` jest deterministyczny jak `PlanGenerator`; etap = osobny `Workout` z blokami
  `fixed`, których `WorkoutPlanner` nie skaluje poziomem, nie podmienia wariantem i nie koryguje trenerem (wspólne
  `shownExercise`/`shownTarget`/`restSec` dla playera i szczegółów). Naraz jeden aktywny program; od D-42 katalog jest dostępny
  zawsze, a start nowego programu wstrzymuje bieżący plan z postępem. Nowy program = obiekt w `PresetDefinitions.kt` + test w `PresetCatalogTest`, a każde jego
  ćwiczenie musi istnieć w bazie ćwiczeń (`PresetSeedConsistencyTest`). Ćwiczenia z kategorią `ExerciseCategory.BREATHING`
  (kategoria, nie `ExerciseKind`) nie wchodzą do kreatora, zamienników ani wyszukiwarki edytora treningu.
- Sprzęt w gotowym programie (D-46) sprawdza się przy budowie cyklu, nie w playerze: `PresetCatalog.build` dostaje
  bibliotekę i posiadany sprzęt z ustawień i podmienia tylko ćwiczenia, których nie da się zrobić, na zamiennik
  z `PresetDefinition.substitutes(level)`. Sprzęt bez zamiennika (`PresetCatalog.unmetEquipment`) blokuje program.
  Nowy program ze sprzętem = zamiennik albo świadoma blokada. Każdy program ma `intensity` (bez domyślnej), a
  `PresetCatalog.all` stoi od łagodnych do wymagających; ta trudność to nie poziom 1/2/3 ze startu.
- Trener (D-35): opinia z podsumowania zmienia `ExerciseTuning`, a korekta wchodzi w `WorkoutPlanner` po skalowaniu poziomem;
  kluczem jest ćwiczenie pokazane na ekranie, nie pozycja szablonu. Korekta nigdy nie przepisuje szablonów treningów.
  „Nie chcę” podmienia pozycje aktywnego cyklu przez `Coach.withoutExcluded` i wchodzi do `PlanGenerator.generate(excluded)`.
  Zmiana reguł trenera = test w `app/src/test/.../domain/coach`.
- Cel pokazany to jedno źródło liczb (D-43): `WorkoutPlanner.shownExercise`/`shownTarget` liczą wariant, skalowanie poziomem
  i korektę trenera, a wszystko, co pokazuje albo porównuje cel (player, szczegóły treningu, `SetProgression`), idzie przez nie.
  Nie licz celu drugi raz na boku.
- Wariant poziomu wolno pokazać tylko z jego sprzętem (D-43): `resolveVariant` dostaje posiadany zestaw i przy braku zostaje
  przy ćwiczeniu z szablonu. Nowe wywołanie `expand`/`shownExercise`/`shownTarget` musi dostać prawdziwy `ownedEquipment`
  z ustawień, nie `emptySet()` dla wygody.
- Okno EMOM (`BlockItem.windowSec`, D-43) liczy silnik, nie planner: planner wystawia estymatę przerwy, `WorkoutEngine.advance`
  podmienia ją na okno minus faktyczny czas pracy. Poziom trudności okna nie rusza. Plik planu (D-42) niesie je jako
  `windowSec` z domyślnym null, a `WorkoutEditing.sanitize` przycina je do `MAX_WINDOW_SEC` (600 s).
- Kasowanie planu (`ProgramRepository.delete`, w transakcji) zabiera jego treningi: z dni planu i z prefiksem `"$programId-"`,
  poza tymi, które ma jakakolwiek sesja (Historia sięga po nie przy Δ). Resztki po wydaniach sprzed 0.9.1 (trening `plan-…`
  bez istniejącego planu-właściciela, bez dnia planu i bez sesji, starszy niż doba) sprząta `SeedImporter` przy podbiciu
  seedu (`WorkoutDao.deleteOwnerlessPlanWorkouts`, D-43).
- Ergometry (D-44): `Equipment` z flagą `ergometer` (wioślarz, rower stacjonarny, orbitrek, bieżnia). Ich ćwiczenia
  (`Exercise.usesErgometer`) są w bibliotece i edytorach, ale nigdy w `PlanGenerator` ani jako zamiennik zwykłego
  ćwiczenia w `EquipmentMatcher.substitutes`; kreator nie pokazuje ergometrów na liście sprzętu. Cel na czas sięga
  `WorkoutEditing.MAX_TARGET_SECONDS` (godzina), a krok w edytorze liczą `nextTarget`/`previousTarget`.
- Notatki do ćwiczeń w planie (D-44): tabela `plan_exercise_notes`, klucz plan i ćwiczenie pokazane na ekranie.
  Zapis tylko przez `ProgramRepository.setNote` (tekst przez `PlanNotes.clean`); kopia i powtórka planu, plik planu
  (`SharedPlan.notes`) i kopia zapasowa (od v4) je niosą, `ProgramRepository.delete` je kasuje. Silnik i szablony
  treningów o notatkach nie wiedzą: player czyta je w `PlayerViewModel`, nie w `PlayerController`.
- Seed nie wozi szablonów treningów (od v7, D-43): kolejkę układa kreator, gotowy program albo plan własny (D-42).
  Wbudowane treningi z historią zostają w bazie i dalej są tylko do kopiowania (D-19 w domenie, `WorkoutEditing.canEdit`).
- Stan playera żyje tylko w `service/PlayerController` (D-16): UI nie trzyma stanu silnika i nie tyka. Nowy efekt silnika = obsługa
  w `PlayerController.handle`; zmiana treści powiadomienia = `NotificationContent` + test w `app/src/test/.../service`.
- Poziom trudności i posiadany sprzęt to globalne ustawienia (DataStore). Szablony treningów są pisane dla INTERMEDIATE;
  skalowanie i podmiana wariantów dzieje się w `WorkoutPlanner` (`DifficultyProfile`), nigdy w UI.
- Sprzęt: `Exercise.equipment` = zbiór WYMAGANY w całości; pusty = bez sprzętu. Nie dodawać „alternatywnego” sprzętu do modelu —
  zamiennik to osobne ćwiczenie (`EquipmentMatcher.substitutes`).
- Room: zmiana encji = `version + 1` w `AppDatabase` + `Migration` w `MIGRATIONS` + opis w `docs/DATABASE.md`
  (pilnuje tego `python tools/check_schema.py`). Bez `fallbackToDestructiveMigration`
  (historia treningów jest danymi użytkownika). Od 1.0.0 (D-45) bez wyjątków: każda zmiana schematu ma migrację i test
  w `MigrationTest`, nigdy „odinstaluj i zainstaluj”.
- Bloki treningu są JSON-em w `workouts.blocksJson` (D-04) — zmiana pól `Block`/`BlockItem` musi mieć wartości domyślne (kompatybilność wstecz).
- `EngineState` jest serializowany do checkpointu; zmiana pól z domyślnymi wartościami jest bezpieczna, usunięcie pola — wyczyść tabelę `player_checkpoints` w migracji.
- UI: Material 3, Compose, `hiltViewModel()`, `collectAsStateWithLifecycle()`, trasy `@Serializable` (`ui/navigation/Routes.kt`).
- Wykresy rysujemy na `Canvas` w `ui/common/Charts.kt` (D-23), dane liczy `domain/stats/ProgressStats`; bez bibliotek wykresowych.
- Wygląd: system „Ogień i Krew” (D-32), od D-39 jeden z motywów do wyboru w Ustawieniach (ten jest domyślny; od D-56
  cztery). Motyw to jeden skończony wygląd, nie para jasny/ciemny: aplikacja nie idzie za trybem systemu, bo „Papier
  i Ogień” i „Brzoza i Mech” są jasne zawsze. Kolory tylko z `MaterialTheme.colorScheme`, nigdy `Color(0x…)` w ekranie; wartości domyślnego
  motywu stoją w `ui/theme/Color.kt`, pozostałych w `Themes.kt`, a cztery schematy (po 35 ról) w `Theme.kt` (D-17).
  Nowy motyw = wartości, schemat, pozycja w `AppTheme` i zielony `python tools/check_contrast.py` (chodzi po wszystkich
  schematach). Zmiana odcienia = ten sam skrypt. Te same progi pilnuje `ThemeContractTest` w `app/src/test/.../ui/theme`,
  więc CI zatrzyma motyw, o którego skrypcie ktoś zapomniał; oba są uruchamiane w `testy.yml`. Wygląd komponentów
  łapią zrzuty (D-40, `ThemeScreenshotTest`) — po zmianie motywu przelicz wzorce (`-Proborazzi.record=true`)
  i obejrzyj diff. Nowa para tekst/tło w komponencie = wpis w `check_contrast.py` i `ThemeContractTest` naraz.
  Trzy reguły, których trzyma się każdy ekran: wypełnienie `primary` znaczy akcję, `tertiary` niesie informację i nigdy nie trafia
  na przycisk, zrobione rysujemy ogniem, a resztki kolorem `outlineVariant`. Elewacje zerowe — warstwy rozdziela stopień
  `surfaceContainer` i linia włosowa. Bez `dynamicColor`.
  Tło ekranów (D-50, D-55): zawsze zdjęcie dnia, a na nim „Samo zdjęcie” (pierwszy wariant motywu, domyślny) albo
  gradient motywu z kryciem `GRADIENT_OVER_PHOTO` z `ui/theme/Backgrounds.kt` (`AppBackground`, warianty osobno dla
  każdego motywu, jedno pasmo `PhotoBand` na motyw). Tła jednolitego nie ma. Maluje je tylko korzeń nawigacji (dół
  pasma, zdjęcie, gradient), a wszystko nad nim jest przezroczyste: ekran z własnym Scaffoldem bierze `ScreenScaffold`
  (pasek tytułu `screenTopBarColors()`), nigdy `containerColor = surface`, i nie kładzie pod treścią wypełnienia
  `surface`. Kontrast liczy siatka: każdy punkt pasma z każdym punktem gradientu; nowy wariant = kolory, wpis
  w `AppBackground` i zielony `check_contrast.py`, który próbkuje tak samo jak `ThemeContractTest`.
  Zdjęcie dnia (D-51): filtr kładzie każdy piksel zdjęcia na paśmie `PhotoBand` (dwa kolory motywu), a karty leżące
  na tle ekranu są nad nim szkłem: wypełnienie karty bierze się z `cardColor()`, nigdy wprost z `surfaceContainer`.
  Paski, menu, okna i miniatury zostają pełne. Pasmo i alfa szkła trzymają w każdym punkcie pary na tle i na karcie
  (test i skrypt liczą w 8 bitach na kanał); zmiana któregokolwiek = zielony `check_contrast.py`. Skład dnia i temat
  zdjęcia liczy tylko `PhotoPicker` (treningi dnia ze wszystkich planów w użyciu, trzy najliczniejsze tematy, losowanie
  z wagą stałe przez dzień); żaden napis nie mówi, jaką partię dziś ćwiczysz, także nagłówek „Dziś” (D-55). Osoby
  na zdjęciu wynikają z płci w „O mnie” (`PhotoPeople.of`, D-57). Zdjęcia dodaje `tools/baza.py tlo`
  albo sekcja „Zdjęcia tła” edytora (każdy temat dla obu płci, minApp jak przy nowym enumie).
- Wspólne komponenty w `ui/common/`: `DesignSystem.kt` (WorkoutCard, ExerciseRow, StatRow, PrimaryAction, ProgressionCard,
  FilterPill, SettingRow, ValueStepper, ScreenScaffold…), `Components.kt` (ExerciseTile, ExerciseImage, ExerciseIllustration,
  ExerciseThumbnail, ExercisePreviewStrip, EmptyState, formatDuration…) i `Structure.kt` (ScreenTitle, BackTopBar, DisclosureRow,
  CategoryRow, TagChip, IconLabelButton, SegmentedText, OverflowMenu, QuietLink). Nowy ekran składa się z nich, zamiast
  rysować własne karty i przyciski; ikony tylko z `AppIcons` (od D-54 żaden ekran nie bierze glifów Materiala).
  Szczegół, który nie jest potrzebny co dzień, chowamy w `DisclosureRow` albo w kategorii, a nie usuwamy (D-52).
- Ekrany główne (D-52) mają bezstanowe `*Content` i klasę `*Actions`, a `LayoutScreenshotTest` rysuje je z fiksturami
  na telefonie i tablecie: nowy stan ekranu głównego = fikstura w tym teście. Na tablecie w poziomie (`AppLayout.twoPane`)
  Ćwiczenia i Ustawienia idą przez `ui/adaptive/TwoPane`; panel szczegółu ćwiczenia to ten sam `ExerciseDetailViewModel`
  z wyborem przez `select`. Poziom trudności i „Przerwij trening” w playerze mieszkają w menu ⋮ (arkusz), nie nad liczbą.
- Typografia: Sora z `res/font/sora.ttf` (wariant zmienny, wagi z osi `wght`, wymaga API 26). Skala i style w `ui/theme/Type.kt`;
  liczby playera (208/176/132 sp) są policzone pod czytanie z półtora metra — nie zmniejszać.
  Ikony tylko własne kreskowe z `ui/common/AppIcons.kt` (D-52, D-54: ścieżki SVG w siatce 24, kreska 1,8; nowa ikona =
  wpis w `AppIcons.all`, pilnuje go `AppIconsTest`). Zależność `material-icons-core` została w Gradle, ale żaden ekran
  z niej nie korzysta; bez `material-icons-extended`. Teksty UI po polsku inline (D-10).
- Baza ćwiczeń (D-47): edytujesz w edytorze (`python tools/baza.py`) albo wprost `baza/exercises.json` i `baza/images/<id>.jpg`, podbijasz numer
  (`python tools/baza.py podbij`: `baza/exercises.json`, `VERSION.md` pod „# Wersja bazy ćwiczeń” i pusty nagłówek
  `## [Baza N]` w `CHANGELOG.md`), opisujesz wydanie w tej sekcji i sprawdzasz
  (`python tools/baza.py sprawdz --wzgledem origin/master`: także zgodność numeru w obu plikach i opis nowego numeru); aplikacja
  re-importuje przy starcie, własne ćwiczenia użytkownika zostają. Ćwiczenie wchodzi do bazy tylko z definicją i ilustracją
  naraz, szkice czekają w `tools/szkice/`. Zgodność wstecz: id raz wydanego ćwiczenia nie znika i nie zmienia `kind`,
  zmiana treści podnosi `version`, a wartość enuma nieznana ostatniemu wydaniu aplikacji wymaga `minApp`. W `baza/` leżą
  tylko dane, bo cały katalog jedzie do APK. Integralności pilnuje `SeedIntegrityTest`, programów `PresetSeedConsistencyTest`.
  Prefiks `custom-` jest zarezerwowany dla ćwiczeń z plików planów (D-42), `tools/baza.py` go odrzuca. Opis: `docs/SEED.md`.
- AGP 9 ma wbudowany Kotlin: nie dodawać pluginu `org.jetbrains.kotlin.android`. Wersje w `gradle/libs.versions.toml` pochodzą z JetNews (android/compose-samples) — podnosić razem.
- Pracujemy na `develop`, wydajemy przez PR do `master`. **PR do `master` scala właściciel, nigdy Claude** — Claude go
  otwiera, doprowadza CI do zielonego i na tym kończy. PR zakładamy **jako szkic**: testy ruszają raz, przy „Ready for
  review” (D-41; minuty Actions od D-48 są darmowe, ale zasada zostaje, dopóki właściciel jej nie zmieni). Po poprawce na czerwonym biegu odpalamy je ręcznie
  (*Actions → Testy → Run workflow* na gałęzi PR-a) — samo pchnięcie nic nie uruchamia. Numer wersji stoi w `VERSION.md` (Gradle czyta go przy buildzie);
  wydanie aplikacji powstaje tylko wtedy, gdy scalenie do `master` podniosło w tym pliku numer aplikacji (numer bazy
  ćwiczeń w tym samym pliku wydaje osobno `baza.yml`). Podnoszenie: `python tools/bump_version.py X.Y.Z`.
- Wydanie jedzie też na publiczną stronę `Mysttic/Mysttic` jako `mysttic-trainings-v<numer>` z plikiem
  `mysttic-trainings-<numer>-android.apk` (D-33); ta sama nazwa co katalog strony i `tagPrefix` w jej `index.html`.
  Tag z prefiksem, nazwa pliku i opis (numer + adres strony) to umowa, którą czytają trzy programy naraz: workflow, skrypt strony
  i `UpdateCheck` w aplikacji. Zmiana któregokolwiek = zmiana we wszystkich trzech. Opis strony: `docs/site-page.md`.
- Baza ćwiczeń ma własne wydania (D-47): po scaleniu zmiany w `baza/` do `master` workflow `baza.yml` składa
  `mysttic-trainings-baza-<numer>.zip` i manifest `mysttic-trainings-baza-<numer>.json` (`tools/baza.py paczka`),
  publikuje je jako `mysttic-trainings-baza-v<numer>` w `Mysttic/Mysttic` bez „Latest” z opisem z sekcji
  `## [Baza <numer>]` w `CHANGELOG.md` i zakłada tag `baza-v<numer>`. Strona projektu pokazuje numer i datę tego
  wydania pod numerem aplikacji. Numer już wydany z inną treścią = czerwony bieg. Tag i nazwy plików to umowa `baza.yml` z pobieraniem bazy
  w aplikacji: zmiana jednego = zmiana w obu. Prefiks nie może zaczynać się od `mysttic-trainings-v` (umowa D-33).
- Każda zmiana w projekcie = wpis w `CHANGELOG.md` w sekcji wersji, do której wejdzie (decyzja właściciela z 2026-10-28).
  Numer w `VERSION.md` podnosimy od razu po decyzji o wersji, a datę w nagłówku sekcji wpisujemy przy wydaniu. Ten opis
  trafia do wydania na GitHubie; pusta sekcja zatrzymuje wydanie (`release_notes.py`). Zmiana bazy ćwiczeń idzie
  do sekcji `## [Baza N]` jej wydania, które ma każdy nowy numer bazy, także wydany razem z aplikacją (`release_notes.py --baza N`).
  `docs/PLAN.md` nie prowadzimy.
- Commity po polsku lub angielsku, krótkie, w trybie rozkazującym. Po każdej decyzji wpis w `docs/DECISIONS.md`.

## Wynik pierwszego buildu

Wszystkie wersje z `libs.versions.toml` rozwiązują się, kod skompilował się bez zmian. Jedyne poprawki: `hiltViewModel` importować
z `androidx.hilt.lifecycle.viewmodel.compose` (D-15), `Locale.forLanguageTag` zamiast konstruktora. Coil 2 czyta assets przez
`file:///android_asset/...`, Room mapuje `Set<Equipment>`/`List<MuscleGroup>` przez `Converters` bez zastrzeżeń KSP.
