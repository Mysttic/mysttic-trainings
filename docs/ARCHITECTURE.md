# Architektura

Jeden moduł Gradle (`app`), pakiety per warstwa. Zależności idą w jedną stronę: `ui → data → domain`; `domain` nie zna Androida.

```
Compose UI ──collect StateFlow──► ViewModel (Hilt) ──► Repozytoria ──► Room / DataStore / assets
                                       │
                                       └──► domain: WorkoutPlanner, WorkoutEngine, EquipmentMatcher (czysty Kotlin, testy JUnit)
```

## Silnik playera (`domain/engine`)

**Rozwinięcie planu.** `WorkoutPlanner.expand(workout, exercises, difficulty, owned, countdownSec, tuning)` zamienia szablon w płaską listę `Step`:
`Countdown(sec)`, `Work(blockIndex, itemIndex, round, setIndex, setsInItem, exerciseId, exerciseName, targetKind, targetValue, weightKg)`,
`Rest(sec, kind, windowSec?)`; od D-42 `Work` niesie też `note` i `blockLabel`, od D-43 `windowSec`.
Dla bloku `SETS`: seria, przerwa między seriami, …, przerwa między elementami. Dla `CIRCUIT`: elementy rundy z przerwami
między elementami, przerwa między rundami. Między blokami stała przerwa (60 s, skalowana). Przerwy 0 s nie tworzą kroku.

**Skalowanie poziomem** (`DifficultyProfile`): BEGINNER ×0.7 powtórzeń i sekund, ×1.3 przerw; INTERMEDIATE ×1; ADVANCED ×1.3 / ×0.8.
Sekundy i przerwy zaokrąglane do 5 s (min 5), powtórzenia min 1. **Wariant**: BEGINNER bierze `easierId` (jeśli ćwiczenie nie jest
już BEGINNER), ADVANCED bierze `harderId` (jeśli nie jest już ADVANCED); jeden skok, bez rekurencji, i tylko wtedy, gdy posiadany
sprzęt wystarcza na wariant (D-43); inaczej zostaje ćwiczenie z szablonu.

**Maszyna stanów** (`WorkoutEngine.reduce(state, event) -> (state, effects)`), stan:

```
sessionId, difficulty, steps, stepIndex, phase (IDLE|RUNNING|FINISHED|ABORTED),
phaseStartedAt, pausedElapsedMs, pauseStartedAt?, workoutStartedAt, totalPausedMs, lastBeepSecond
```

Czas fazy = `(pauseStartedAt ?: now) − phaseStartedAt − pausedElapsedMs`. Zdarzenia: `Start, Tick, Pause, Resume, Skip, Back,
Done(actual?), AddRest(sec), Abort, Replan(difficulty, steps)`. Efekty: `BeepShort, BeepLong, Vibrate, Speak(text), LogSet(SetResult),
Checkpoint, Finished(status)`.

Reguły:
- Krok czasowy (Countdown, Rest, Work SECONDS/AMRAP) kończy się, gdy `elapsed ≥ dur`. Jeśli tick spóźnił się ≤ 2 s, nowa faza
  startuje **dokładnie na granicy** (brak dryfu); jeśli więcej (telefon spał) — od `now`, bez kaskady przez kolejne fazy.
- Work REPS czeka na `Done(actual)`; `actual == null` → cel. Work SECONDS zakończone przez `Done` loguje przepracowane sekundy.
  AMRAP po czasie loguje 0 (użytkownik dopisuje wynik w szczególe sesji) albo `Done(n)` wcześniej.
- Beep krótki w chwili, gdy zostaje 3, 2, 1 s (raz na sekundę, `lastBeepSecond`); beep długi + wibracja przy każdym przejściu.
- Przy wejściu w przerwę TTS: „Następnie: ćwiczenie, cel”; przy wejściu w pracę: „ćwiczenie, cel”.
- `Replan` przyjmuje plan już scalony przez `WorkoutPlanner.merge(current, stepIndex, replanned)`: wykonane kroki zostają,
  reszta z nowego planu od tej samej pozycji (klucz `blok/element/runda/seria`).
- Przerwa po pozycji z `windowSec` (EMOM, D-43): planner wystawia estymatę, a `advance` przy wejściu w przerwę podmienia ją
  na okno minus faktyczny czas pracy (bez pauz). Praca dłuższa niż okno pomija przerwę i startuje kolejną rundę. Poziom okna nie rusza.
- `Abort` → `ABORTED` + `Finished(ABANDONED)`; po `FINISHED/ABORTED` silnik ignoruje zdarzenia.

**Checkpoint.** Każde przejście emituje `Checkpoint`; `SessionRepository.saveCheckpoint` zapisuje `EngineState` jako JSON z parą
`(savedAtWall, savedAtElapsed)`. `WorkoutEngine.restore(saved, savedNow, passedMs, now)` przesuwa timestampy do bieżącej domeny zegara
(`passedMs` z wall clock — odporne na reboot) i, jeśli faza minęła o > 30 s, wraca w pauzie. M1: odtwarzanie przy ponownym wejściu
do playera tego samego treningu (`PlayerViewModel.load`). M2: to samo w serwisie po `START_STICKY`.

**Zegar.** `MonotonicClock` = `SystemClock.elapsedRealtime()` (liczy w uśpieniu, odporny na zmianę godziny). Wall clock tylko
w checkpoincie i w datach sesji.

## Dopasowanie sprzętu (`domain/equipment`)

- `Exercise.equipment: Set<Equipment>` — wymagany w całości; pusty zbiór = bez sprzętu. `Equipment` to zamknięta lista domowa
  (hantle, kettlebell, guma, drążek, niski drążek/stół, ławka/krzesło, skakanka, piłka lekarska, piłka gimnastyczna, sztanga).
- `UserSettings.ownedEquipment` — co użytkownik zaznaczył w Ustawieniach.
- Biblioteka filtruje przez `domain/library/LibraryFilter`: „Mój sprzęt” (`Exercise.canDoWith`), „Bez sprzętu” i konkretny sprzęt,
  rodzaj, poziom, partie (główne mięśnie) i szukanie po nazwie PL/EN; wymiary łączy „i”, wartości w wymiarze „lub”.
  `LibraryFacets.of` daje chipy tylko z wartości obecnych w bibliotece, w kolejności enumów.
- `EquipmentMatcher.substitutes(exercise, candidates, owned)`: ta sama główna grupa mięśniowa, wykonalne z posiadanym sprzętem,
  sortowanie: ten sam rodzaj (REPS/TIME) → ta sama kategoria → najbliższy poziom → mniej sprzętu → nazwa.
- `EquipmentMatcher.missingInWorkout` — mapa ćwiczenie → brakujący sprzęt; szczegół treningu pokazuje ostrzeżenie i pierwszy zamiennik.

## Model danych (Room, `data/db`)

| Tabela | Klucz | Uwagi |
|---|---|---|
| `exercises` | `id` (slug) | listy/zbiory jako JSON (Converters), enumy jako nazwy; `isCustom` chroni przed nadpisaniem przez seed |
| `workouts` | `id` | `blocksJson` = `List<Block>` (D-04); `isBuiltIn` = z seedu (od v7 zostają tylko wiersze z historią, D-43) |
| `sessions` | `id` (UUID) | snapshot `workoutName`, `difficulty`; `status` IN_PROGRESS/DONE/ABANDONED; `rpe`, `note`; `programId`, `plannedSets` (D-42) |
| `set_logs` | autoinkrement | jedna wykonana seria: pozycja w planie, cel, wynik, `completedAt` (wall) |
| `player_checkpoints` | `sessionId` | JSON `EngineState` + `savedAtWall` + `savedAtElapsed` |

Statystyki (wolumen, rekordy, streak) liczone zapytaniami na `set_logs`/`sessions`, bez tabel pochodnych.

## Baza ćwiczeń (`data/seed`, `baza/`, `tools/baza.py`)

`baza/exercises.json` (`version`, `format`, `minApp`, `exercises[]`; od v7 bez `workouts[]`), w APK asset `exercises.json`,
bo Gradle podpina `baza/` jako katalog assetów (D-47) → `SeedImporter.ensureSeeded()` przy starcie aplikacji:
importuje, gdy `version` > zapisanej w DataStore lub baza jest pusta; `upsert` wbudowanych, własne ćwiczenia nietknięte. Przy podbiciu
kasuje wbudowane treningi bez historii i resztki skasowanych planów (D-43). Szczegóły: `SEED.md`.

Bazę nowszą niż w APK aplikacja pobiera z wydania bazy (D-47): `UpdateRepository.check()` jednym zapytaniem znajduje
wydanie aplikacji i bazy, `ExerciseDatabaseRepository` bierze manifest, ZIP (rozmiar i SHA-256), rozpakowuje go z limitami
(`DatabaseArchive`) do `files/baza/<numer>/`, sprawdza (`ExerciseDatabaseCheck`: reguły bazy, zgodność z tym, co jest
w telefonie, i z gotowymi programami przez `PresetCatalog.libraryProblems`) i oddaje `SeedImporter.importDownloaded`.
Przy starcie wygrywa wyższy numer, wbudowany albo pobrany; obrazy pobranej bazy mają ścieżki `baza/<numer>/...`,
a `ui/common/exerciseImageModel` zamienia je na plik zamiast assetu.

## Ustawienia (`data/settings`)

DataStore Preferences: `difficulty`, `ownedEquipment`, `countdownSec`, `soundEnabled`, `ttsEnabled`, `vibrationEnabled`,
`keepScreenOn`, `seedVersion`, `volumeKeyDone`, `reminderEnabled`/`reminderHour`/`reminderMinute`, `healthConnectEnabled`,
`updateChecks`/`lastUpdateCheck`, `libraryTiles` (kafelki albo lista w bibliotece), `themeId` i `backgrounds`
(motyw i tło każdego motywu, D-39, D-50), `photoPeople` (osoby na zdjęciu dnia, D-51). Zdjęcie dnia wybiera
`data/background/BackgroundPhotoRepository`: zdjęcia z `tla.json` wczytanej bazy i treningi dnia ze wszystkich planów
w użyciu, regułą z `domain/background/PhotoPicker` (trzy najliczniejsze tematy, losowanie z wagą, stałe przez dzień). Zmiana poziomu z dowolnego ekranu
(player, Ustawienia) zapisuje to samo pole.

## UI (`ui/`)

Navigation Compose z trasami `@Serializable` (`ui/navigation/Routes.kt`); dolny pasek na 4 ekranach głównych
(Dziś, Ćwiczenia, Historia, Ustawienia). Ekrany: `today` (jedna karta z następnym treningiem z kolejki, D-26),
`plan` (kreator profilu i podgląd cyklu), `library` (kafelki albo lista w jednej `LazyVerticalGrid`, rozwijany panel filtrów jako pierwszy element siatki) + `ExerciseDetail` (zdjęcia, instrukcja, warianty, zamienniki, wykres rekordów),
`workout/WorkoutDetail` (cele po skalowaniu, propozycja progresji, wejście do edytora), `editor`, `player` (cienki widok na
`PlayerController`), `history` (filtry, statystyki, wykresy) + szczegół sesji z RPE i notatką, `settings`.
Wykresy rysuje `ui/common/Charts.kt` na `Canvas` (D-23), dane liczy `domain/stats/ProgressStats`.

## Sygnały (`service/Cues`)

M1: `ToneGenerator` (beep), `Vibrator`, `TextToSpeech` pl-PL. M2: te same wywołania z foreground service; `SoundPool` z własnymi
próbkami i AudioFocus z duckingiem.

Powiadomienie playera wystawia `WorkoutService`. Zgodę na powiadomienia serwis sprawdza co 2 s, a `NotificationGate`
(zbocze, czysty Kotlin) mówi, kiedy wystawić powiadomienie ponownie przez `startForeground`: zgoda nadana w trakcie
treningu działa od razu, bez czekania na kolejny trening (D-43).

## Kreator planu (`domain/plan`)

`TrainingProfile` (wzrost, waga, cel, poziom, dni w tygodniu, minuty sesji, długość cyklu, sprzęt, `targetScale`) →
`PlanGenerator.generate` → `GeneratedPlan` (treningi A/B/C… + rozkład dni). Generator jest deterministyczny: sprzęt to twardy filtr,
BMI ≥ 30 wyklucza plyometrię (także wariant, który wszedłby po podmianie poziomu), rozciąganie wchodzi tylko przy celu „mobilność”
(i tam idzie pierwsze), a rola (nogi, push, pull, tułów, kardio) nie powtarza partii, dopóki są inne do wzięcia; rola bez kandydata
jest pomijana. Kandydata ocenia się po wariancie z `WorkoutPlanner.resolveVariant` dla poziomu profilu i posiadanego sprzętu:
bez dubli, a wariant na sprzęt, którego nie ma, w ogóle nie wchodzi (zostaje ćwiczenie bazowe, D-43).
Rodzaj celu bierze się z `Exercise.kind` (D-27), czas pracy z `Exercise.defaultSeconds`. Rundy i serie siedzą
w `repeatsRange(level, goal)` (D-34), a `fitToTime` dobiera liczbę ćwiczeń (`MIN_ITEMS..MAX_ITEMS`, bloki po `BLOCK_ITEMS`)
i przerwy tak, żeby trafić w zadeklarowane minuty z dokładnością `TIME_TOLERANCE_SEC` (60 s); długość sesji ogranicza
`TrainingProfile.minutesRange(level)`.

`Program` + `PlanDay` w Room (`programs`, `program_days`); `PlanProgress.resolve(days, doneWorkoutIds)` rozlicza plan **kolejką**
(D-21): N ukończonych sesji treningu X odhacza pierwsze N jego wystąpień. `PlanProgression` liczy z frekwencji i kompletu serii,
czy kolejny cykl ma być trudniejszy, a `SetProgression` proponuje podbicie celu pojedynczego ćwiczenia (D-24), z pominięciem
bloków `fixed` i pozycji AMRAP (D-42). Progresja liczy się od celu pokazanego, czyli przez `WorkoutPlanner.shownExercise`/`shownTarget`
(poziom, sprzęt, korekta trenera), więc karta mówi tę samą liczbę, którą widać w wierszu ćwiczenia (D-43). Kasowanie planu zabiera
jego treningi bez historii (`ProgramDao.deleteUnusedWorkouts` i `deleteUnusedOwnedWorkouts`, 0.9.1 i D-42).

Gotowe programy (`domain/plan/preset`, D-37): `PresetCatalog.build(preset, poziom, długość, parametr, programId, now)` daje ten
sam `GeneratedPlan`, co kreator, tylko z jednym szablonem na etap progresji (`"$programId-s<n>"`, bloki `fixed`) i dniem cyklu
wskazującym etap; `Stages` liczy narastanie i przydział sesji do etapów, `PresetDefinitions` trzyma tabele jedenastu programów
jako funkcje poziomu i liczby sesji. `WorkoutPlanner` pomija dla `fixed` skalowanie, wariant i trenera (`shownExercise`,
`shownTarget`, `restSec` wspólne z ekranem szczegółów), a silnik ma krok bez limitu (`openEnded`, cap 10 × cel), rytm
(`cadenceSec` → `EngineEffect.Cadence`) i licznik AMRAP (`EngineEvent.Count`). Cykl z presetu poznaje się po
`TrainingProfile.presetId`; `PlanWizardViewModel`, `SetProgression` i `CoachRepository` omijają go.

## Trener (`domain/coach`, `data/repo/CoachRepository`)

Opinia o ćwiczeniu z podsumowania treningu (`ExerciseFeedback`: bez zmian, więcej, mniej, nie chcę) trafia do
`ExerciseTuning`: korekta powtórzeń i sekund w stałych krokach oraz wykluczenie. `Coach` jest czystym Kotlinem:
`apply` liczy nowy stan, `withoutExcluded` przepisuje bloki treningu bez wykluczonych ćwiczeń (zamiennik z
`EquipmentMatcher.substitutes` albo usunięcie pozycji). `WorkoutPlanner.expand` dostaje mapę korekt i nakłada je po
skalowaniu poziomem, kluczem po ćwiczeniu pokazanym na ekranie. `CoachRepository` trzyma pamięć w tabeli
`exercise_tuning` (neutralny stan nie ma wiersza), po „nie chcę” przepisuje treningi aktywnego cyklu i daje
kreatorowi listę wykluczeń dla `PlanGenerator` (D-35).

## Powiadomienia poza treningiem (`domain/reminder`, `service/Reminder*`)

`ReminderSchedule` (czysty Kotlin) mówi, kiedy jest najbliższy termin i czy w ogóle przypominać („dziś nie było treningu, a kolejka
ma co podać”). `ReminderScheduler` ustawia jednorazowy alarm dokładny, `ReminderReceiver` odbiera go i `BOOT_COMPLETED`,
po każdym odpaleniu ustawiając następny (D-29).

## Aktualizacje (`domain/update`, `data/update`)

`UpdateCheck` trzyma umowę ze stroną (identyfikator projektu `mysttic-trainings`, prefiks taga, nazwa pliku, adres strony)
i porównuje numery wersji, odporne na „-debug”. `ReleaseParser` zamienia **listę** wydań publicznego `Mysttic/Mysttic`
na `UpdateResult`: odrzuca szkice i wydania kandydujące, bierze tagi z prefiksem i gołym `X.Y.Z` po nim, a z nich najwyższą
wersję, nie pierwszą z listy. Leżą tam wydania wszystkich projektów Mysttic, więc bez prefiksu aplikacja wzięłaby cudze
za swoje (D-33), a lista idzie za datą commita, na który wskazuje tag, nie za kolejnością wydań (umowa w `RELEASES.md`
strony). Plik wydania, który jeszcze się wgrywa (`state` inny niż `uploaded`), znaczy „jeszcze nic”. Wydania bazy ćwiczeń
czyta z tej samej odpowiedzi `DatabaseUpdate` według tych samych reguł (D-47). Lista bez naszego wydania znaczy
„nic nowego”, nie błąd: trzy wyniki (`NEWER`, `UP_TO_DATE`, `FAILED`) są rozdzielone, bo „nie ma nowszej” i „nie udało się
zapytać” wysyłają człowieka w zupełnie inne miejsca.

`UpdateRepository` odpytuje API bez tokenu, pobiera APK do `files/updates/` przez plik `.part` ze sprawdzeniem rozmiaru
i oddaje go systemowemu instalatorowi przez `FileProvider`. Numer zainstalowanej wersji pochodzi z `BuildConfig.VERSION_NAME`,
a ten z `VERSION.md` czytanego przez Gradle.

## Health Connect (`domain/health`, `service/HealthConnectExporter`)

`HealthExport` decyduje, co się kwalifikuje (ukończone i dłuższe niż minuta) i jak opisać wysiłek; eksporter zapisuje
`ExerciseSessionRecord` po zakończeniu sesji, jednostronnie i tylko za zgodą użytkownika (D-31).

