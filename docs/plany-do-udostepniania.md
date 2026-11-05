# Plany do udostępniania — projekt (D-42, 2026-09-23)

Projekt funkcji z D-42: lista planów z jednym aktywnym, edytor całego planu, plik planu do udostępniania, statystyki
planu z raportem PDF, duże ekrany. Powstał z mapy kodu (pięć obszarów), dwóch niezależnych projektów (dane, ekrany),
sceptyka sprawdzającego je w kodzie i syntezy. Numery linii według stanu `master` z 2026-09-23 (wersja 0.10.0).
Status etapów: `docs/PLAN.md`, sekcja „Plany do udostępniania”.

## Odpowiedź o statystykach

Tak, dziś wszystkie statystyki są wspólne dla całej aplikacji. Historia łączy całą tabelę `sessions` z całą tabelą `set_logs` (`ui/history/HistoryViewModel.kt:55-81`; zapytania bez `WHERE` w `data/db/dao/Daos.kt:86-87` i `:124-126`) i filtruje tylko po okresie (7 dni, 30 dni, wszystko) i po statusie (`domain/stats/SessionStats.kt:52-70`). Wykres rekordu ćwiczenia też bierze serie ze wszystkich sesji (`ui/library/ExerciseDetailViewModel.kt:59`). Z planu korzystają tylko dwie rzeczy:
- seria dni, która bierze rytm odpoczynku z aktywnego planu (`HistoryViewModel.kt:67`),
- ocena cyklu `CycleReview`, liczona w kreatorze na potrzeby progresji (`ui/plan/PlanWizardViewModel.kt:153-167`).

Sesja nie wie, do którego planu należy. `SessionEntity` ma tylko `workoutId` i `workoutName` (`data/db/entity/Entities.kt:46-57`), a `startSession` nie dostaje planu (`data/repo/SessionRepository.kt:31-41`). Przynależność zgaduje się regułą „sesja ukończona, `startedAt >= program.startedAt`, trening z dni planu”, skopiowaną w pięciu miejscach (np. `ui/plan/PlanViewModel.kt:77-81`). Reguła działa tylko dlatego, że treningi planu mają id z prefiksem `programId` (`domain/plan/PlanGenerator.kt:78`), i znika po „Usuń plan” (`data/repo/ProgramRepository.kt:108-112`). Żeby liczyć per plan, trzeba dwóch rzeczy. Sesja zapisuje `programId` w chwili startu (nowa kolumna w `sessions`, Room v4, stare sesje wypełnione wstecz tą samą regułą). Kolejka, statystyki i raport czytają przynależność przez jedną funkcję w domenie zamiast pięciu kopii.

## Projekt

Ścieżki kodu są względne wobec `app/src/main/java/pl/mysttic/trainings/`, testy wobec `app/src/test/java/pl/mysttic/trainings/`.

### Zasady, na których stoi reszta

- **Plan** to wiersz `programs`, jego dni (`program_days`) i jego treningi. **Właściciela treningu wyznacza prefiks id `"$programId-"`**, bez osobnej kolumny. Prefiks jest w kodzie od pierwszego commitu kreatora (`b6bcda1`), a każda droga zapisu zachowuje id:
  - `WorkoutEditing.toWorkout` bierze `existing?.id` (`domain/editor/WorkoutEditing.kt:137`),
  - kopia zapasowa robi upsert po id,
  - trener i „Podbij cele” robią `copy`.

  Kolumna `workouts.programId` z Projektu A odpada: `toWorkout` buduje nowy `Workout(...)` (`:136-144`) i wyzerowałby ją przy każdym zapisie z edytora, a import kopii v1/v2 nadpisałby ją upsertem. Zapis planu sprawdza `require(workoutIds.all { it.startsWith("$programId-") })`. Nowy trening w planie dostaje id `"$programId-w" + UUID8`, więc zwykły licznik nie zderzy się z istniejącym treningiem.
- **Sesja zapisuje `programId` przy starcie** i ta wartość już się nie zmienia. Ustawia ją `PlayerController.load` z `ProgramDao.ownerOf(workout.id)`, więc silnik dalej nie wie o planach (D-16, D-20).
- **Każda kopia planu** (duplikat, import, powtórka, „Duplikuj i zmień”) idzie przez czystą funkcję `PlanCopy` i dostaje nowe id programu i treningów. Dwa plany nigdy nie dzielą treningu ani postępu.
- **Aktywny jest najwyżej jeden plan** (D-37). „Dziś” pokazuje jedną kartę aktywnego planu (D-26).

Niezmienniki, każdy z testem:
- **I1:** trening niewbudowany należy do najwyżej jednego planu, a dzień planu wskazuje tylko własne treningi planu.
- **I2:** wbudowany trening nie stoi w żadnym dniu (D-19). Od seedu v7 w bazie zostają tylko wbudowane z historią (D-43).
- **I3:** aktywny jest zero albo jeden plan.
- **I4:** `sessions.programId` to snapshot z chwili startu.
- **I5:** postęp liczy jedna funkcja, `PlanSessions.progress`.
- **I6:** edycja planu nie zmienia zbioru odhaczonych dni.
- **I7:** import niczego nie nadpisuje.
- **I8:** stare `blocksJson`, checkpoint i kopie v1/v2 dekodują się bez zmian.
- **I9:** `PlanCopy` i eksport są deterministyczne.
- **I10:** bloku `fixed` nie zmienia nic automatycznego: poziom, wariant, korekta trenera, „Nie chcę” ani „Podbij cele”.
- **I11:** plan i trening z sesją `IN_PROGRESS` są tylko do odczytu.

### 1. Room v4: jedna migracja na całą funkcję (etap 1)

```sql
-- MIGRATION_3_4
ALTER TABLE programs ADD COLUMN source TEXT NOT NULL DEFAULT 'WIZARD';      -- WIZARD|PRESET|CUSTOM|IMPORTED
ALTER TABLE programs ADD COLUMN description TEXT NOT NULL DEFAULT '';
ALTER TABLE programs ADD COLUMN author TEXT NOT NULL DEFAULT '';
ALTER TABLE programs ADD COLUMN originId TEXT;                               -- duplikat, powtórka, kolejny cykl
ALTER TABLE programs ADD COLUMN shareUid TEXT;                               -- tożsamość planu między telefonami
ALTER TABLE programs ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE programs ADD COLUMN rhythmStart INTEGER NOT NULL DEFAULT 0;     -- kotwica rytmu odpoczynku
UPDATE programs SET source = 'PRESET' WHERE profileJson LIKE '%"presetId":"%';   -- Json kompaktowy, encodeDefaults=true
UPDATE programs SET updatedAt = createdAt, rhythmStart = startedAt;
ALTER TABLE sessions ADD COLUMN programId TEXT;
ALTER TABLE sessions ADD COLUMN plannedSets INTEGER;
CREATE INDEX IF NOT EXISTS index_sessions_programId ON sessions(programId);
UPDATE sessions SET programId = (SELECT p.id FROM programs p
    WHERE substr(sessions.workoutId, 1, length(p.id) + 1) = p.id || '-'
      AND sessions.startedAt >= p.startedAt)
  WHERE workoutId IS NOT NULL;                                               -- dokładnie dzisiejsza reguła
```

- `@ColumnInfo(defaultValue = …)` w encjach musi się zgadzać co do znaku z `DEFAULT`. Kolumny nullable nie mają `DEFAULT`, a indeks nazywa się `index_sessions_programId`. Pilnuje tego `MigrationTestHelper.runMigrationsAndValidate`, a schematy trafiają do assetów testowych (`sourceSets["test"].assets.srcDir("$projectDir/schemas")`).
- `plannedSets` to liczba `Step.Work` w stanie silnika przy `finish`, więc liczy się po ewentualnym `Replan`.
- Kryterium migracji: po niej `PlanProgress` każdego planu jest identyczny jak przed.
- `ProgramDao`:
  - `observeAll()` (`ORDER BY active DESC, updatedAt DESC`), `observeAllDays()`, `observeById(id)`, `byId(id)`,
  - `activate(id)` = `UPDATE programs SET active = CASE WHEN id = :id THEN 1 ELSE 0 END`,
  - `deactivateAll()` bez `WHERE`, `updateMeta(...)`, `setStart(id, startedAt, rhythmStart)`,
  - `ownerOf(workoutId)` = `SELECT id FROM programs WHERE substr(:workoutId,1,length(id)+1) = id || '-' LIMIT 1`,
  - `deleteUnusedOwnedWorkouts(id)` kasuje po prefiksie, nie po dniach, więc sprząta też treningi bez dnia.
- `SessionDao`:
  - `observeForProgram(programId)`,
  - `observeDoneRefs()` (`programId`, `workoutId`, `startedAt` sesji DONE; pod listę planów bez ładowania `set_logs`),
  - `observeSetLogsForProgram(programId)` (JOIN po `sessionId`).
- Wszystkie zapisy wieloetapowe idą w `db.withTransaction {}` (room-ktx 2.8.4 już jest). Dotyczy to `ProgramRepository` (`save`, `delete`, `activate`, `duplicate`, `repeat`, `saveDays`), `PlanShareRepository.commit` i `BackupRepository.apply`. `AppDatabase` trzeba wstrzyknąć do tych repozytoriów w `di/AppModule`.
- `docs/DATABASE.md`: nowe kolumny i wiersz migracji 4. Przy okazji poprawić nieaktualne miejsca:
  - sekcja kopii (cykle są w pliku od v2),
  - BREATHING w enumie,
  - zdanie z linii 223 o pomijaniu brakującego ćwiczenia (planner go nie pomija),
  - `sessions.workoutId` „NULL, gdy skasowano” (nic go nie zeruje).

  Na koniec zielony `tools/check_schema.py`.

### 2. Lista planów i aktywacja

**Domena (czysty Kotlin):**
- `domain/plan/Program.kt`:
  - `enum class PlanSource { WIZARD, PRESET, CUSTOM, IMPORTED }`,
  - nowe pola `source`, `description`, `author`, `originId`, `shareUid`, `updatedAt`, `rhythmStart`,
  - `isPreset = source == PRESET`, `isWizard`, `editableInPlace = source in {WIZARD, CUSTOM}`,
  - `presetId` czytany tylko przy `source == PRESET`,
  - `dayIndexAt`, `isRestDate` i `daysLeft` liczą od `rhythmStart`,
  - usunąć martwe `dayAt` i `nextTrainingDay`.
- `TrainingProfile.neutral(level, days)` dla CUSTOM i IMPORTED: bez wzrostu i wagi, nie przechodzi przez `sanitize` kreatora (tam jest 7..120). Limit dni planu to osobna stała `MAX_PLAN_DAYS = 120`, minimum 1.
- `PlanSessions`:
  - `belongsTo(session, program)`: `programId == program.id`; przy `null` stara reguła (prefiks i `startedAt >= program.startedAt`),
  - `completedWorkoutIds`, `progress`,
  - zastępuje kopie w `PlanViewModel:77`, `TodayViewModel:114`, `PresetsViewModel:185`, `ReminderReceiver:80` i `PlanWizardViewModel:155`.
- `PlanCopy.copy(program, days, workouts, newProgramId, target: PlanSource, now)` zwraca `CopiedPlan(days, workouts, idMap)`:
  - id `"$newProgramId-<n>"` w kolejności pierwszego wystąpienia, treningi bez dnia po `(createdAt, id)`,
  - bloki kopiowane w całości (`fixed`, `openEnded`, `cadenceSec`, `weightKg`, `note`, `label`, `windowSec`),
  - przy `target != PRESET` zeruje w profilu `presetId` i `presetParam`. Inaczej kopia presetu pokazywałaby na „Dziś” jego ostrzeżenie, a „Powtórz program” zbudowałoby oryginał z katalogu (`TodayViewModel.kt:224-242`).
- `PlanRhythm.startFor(program, progress, today, zone)`:
  - plan bez sesji zaczyna dziś,
  - plan wstrzymany dostaje `rhythmStart` = dziś − (numer następnego dnia − 1).
  
  `startedAt` zostaje nietknięty, bo z niego korzysta reguła zapasowa. Daty zrobionych dni pokazujemy z sesji, nie z `startDate.plusDays` (`ui/plan/PlanScreen.kt:137`).
- `PlanList.rows(programs, days, doneRefs)` zwraca `PlanListRow(id, name, source, author, status, done, total, lastDoneAt)`. Status (`DRAFT`, `ACTIVE`, `NOT_STARTED`, `PAUSED`, `COMPLETE`) jest liczony, bez kolumny.
- `PlanLock.isReadOnly(programId, workoutIds, inProgress: Session?)` pilnuje I11. Dziś wznowienie z checkpointu łączy stare kroki z bieżącym szablonem (`service/PlayerController.kt:168-203`). Blokadę sprawdzają ViewModele edytorów, `delete`, podmiana trenera i „Podbij cele”.

**Operacje `ProgramRepository`:**
- `save(..., source, activate = true)`.
- `activate(id, now)`:
  - `setStart` według `PlanRhythm`, potem `programDao.activate(id)`, wszystko w transakcji,
  - po transakcji `CoachRepository.replaceExcludedIn(id)`, z pominięciem bloków `fixed` i treningu z sesją w toku (D-35).
- `duplicate(id, now)`: CUSTOM, nieaktywny, `originId = id`, nazwa z dopiskiem „(kopia)”.
- `repeat(id, now)`: nowy wiersz tego samego źródła, `originId = id`, aktywowany. Każdy przebieg to osobny plan z własnym raportem, a podbite cele z szablonów przechodzą dalej. Tak jak dziś działają „Powtórz program” i „Kolejny cykl”.
- `delete(id)`: sesje zostają w Historii z `programId` usuniętego planu. Tekst okna w `PlanScreen.kt:89` zmieniamy na: „Zniknie rozkład dni i nieużyte treningi planu. Historia zostaje, statystyk tego planu już nie będzie.”
- `lastWizardProgram()` filtruje `source == WIZARD`.

**Poprawki spójności, wszystkie w etapie 1 (inaczej duplikat presetu łamie D-37):**
- `Coach.withoutExcluded` pomija bloki `fixed`.
- `SetProgression.suggest` pomija bloki `fixed` i jawnie AMRAP.
- Predykat `Workout.acceptsProgression = !isBuiltIn && !isFixed` w `TodayViewModel:145/204` i `WorkoutDetailViewModel:177`.
- Kreator zeruje `presetId` i `presetParam` profilu bazowego i bierze profil tylko z planu WIZARD (`PlanWizardViewModel:83-86`, `ProgramRepository.startPreset:76`, `CoachRepository:77`).

**UI:**
- Trasy:
  - `PlansRoute`,
  - `PlanRoute(programId: String? = null)` (null = aktywny),
  - `PlanWizardRoute(step, fromProgramId: String? = null)`; kreator liczy `CycleReview` z tego planu.
- `ui/plans/PlansScreen` z sekcjami Aktywny, Wstrzymane i nierozpoczęte, Zakończone (zwinięte, bo trafią tam stare cykle z kreatora). Wiersz `PlanRow` w `ui/common/DesignSystem.kt`: nazwa, źródło („Z kreatora”, „Gotowy program”, „Twój plan”, „Od <autor>”), `SegmentBar`.
- `PlanScreen` z `programId`:
  - tytuł `program.name`,
  - menu `Icons.Filled.MoreVert`: Edytuj, Duplikuj, Duplikuj i zmień, Zacznij od nowa, Zapisz do pliku, Usuń,
  - plan nieaktywny: PrimaryAction „Ustaw jako aktywny” zamiast Start.
- „Dziś”: cichy odsyłacz „Twoje plany (N)” przy braku planu i w `CycleClosed`.
- Okno `PauseActiveDialog` (zależy od pytania 1).

**Kopia zapasowa v3:**
- `BackupProgram` dostaje `source: String? = null`, `description`, `author`, `originId`, `shareUid`, `updatedAt`, `rhythmStart`. `null` w `source` oznacza plik v1/v2 i wtedy źródło liczymy z `profileJson` tą samą regułą co migracja. Bez tego preset z v2 wszedłby jako WIZARD.
- `BackupSession` dostaje `programId` i `plannedSets`.
- `BackupSettings` dostaje `authorName`.
- `BackupFile.VERSION = 3`.
- Po `restorePrograms` wywołać `PlanSessions.backfill`, bo sesje wchodzą przed cyklami (`BackupRepository.kt:103` wobec `:108`).
- Aktywny cykl z pliku przez `activate(id)`.
- Cykl o znanym id pomijamy razem z jego treningami. Dziś `upsertAll` nadpisuje szablony pominiętego cyklu.
- Utwardzenie importu kopii:
  - najpierw `JsonObject`, w którym klucz `app` musi być, a klucza `format` ma nie być (dziś `{}` przechodzi, `BackupModels.kt:20-21`),
  - odczyt ograniczony do 5 MiB,
  - ćwiczenie o id wiersza z seedu (`isCustom = 0`) jest pomijane.

**Testy:**
- `PlanSessionsTest`: równoważność ze starą regułą, reguła zapasowa dla `null`.
- `PlanCopyTest`: determinizm, rozłączne id, `fixed`, `openEnded` i `cadenceSec`, rozłączny postęp oryginału i kopii, wyzerowany `presetId`.
- `PlanRhythmTest`: wznowienie po 20 dniach, `bestStreak` od dnia wznowienia.
- `PlanListTest`, `ProgramTest` (`isPreset` po `source`).
- `CoachTest` i `SetProgressionTest`: `fixed` i AMRAP.
- `MigrationTest` 3→4 (Robolectric + `room-testing`): preset rozpoznany, `sessions.programId` wypełnione, identyczny postęp.
- `ProgramRepositoryTest` (Room in-memory w Robolectricu): I1, I3, `delete` nie rusza innego planu.
- `BackupFormatTest`: v3, preset z v2 ma `PRESET`, a `{}` i plik planu są odrzucone.

### 3. Edytor planu i brakujące pola

- `PlanDraft(programId, name, description, author, adaptive, days: List<DraftDay(key, workoutId?)>, createdWorkoutIds)` jest `@Serializable` i leży w `SavedStateHandle`, więc przeżywa obrót, zmianę okna i śmierć procesu.
- **Nowy plan od zera zakłada wiersz `programs` od razu:** CUSTOM, nieaktywny, zero dni, status DRAFT. Dlatego treningi tworzone w edytorze mają istniejącego właściciela i żadne sprzątanie nie skasuje ich spod szkicu.
- Porzucenie szkicu:
  - nowy plan: `delete`, po potwierdzeniu,
  - zmiany w istniejącym planie: dni wracają, a treningi z `createdWorkoutIds` bez sesji są kasowane.
- Sprzątanie przy starcie usuwa tylko treningi `plan-%`, których właściciel nie istnieje, bez sesji i starsze niż 1 dzień.
- `domain/plan/PlanEditing`:
  - `skeleton(weeks, perWeek, rotation)` (przez `DayPattern.Weekly`), `addTrainingDay`, `addRestDay`, `moveDay`, `removeDay`, `duplicateDay`, `setDayWorkout`, `appendWeekCopy`, `toPlanDays()` (gęste `dayIndex` od 1),
  - `lockedKeys(progress)` blokuje **zbiór** `doneDayIndexes`, a nie prefiks. Odhaczenia są zachłanne po `workoutId`, a trening dowolnego dnia da się uruchomić ze szczegółów, więc możliwe jest `done = {1,3}` przy `next = 2`,
  - `validate`: co najmniej jeden dzień treningowy, `workoutId` z prefiksem planu (I1, I2), 1..`MAX_PLAN_DAYS` dni, oraz I6: `resolve` na starych i nowych dniach z tymi samymi sesjami daje ten sam zbiór kluczy (łapie też wstawienie dnia A przed odhaczony dzień A).
- Edytowalność: `canEdit(program) = editableInPlace && !PlanLock`. `WorkoutEditing.canEdit(workout, owner)` sprawdza `WorkoutEditorViewModel.load`; dziś wczytuje każdy trening, także wbudowany (D-19 schodzi do domeny). PRESET i IMPORTED zmienia się przez „Duplikuj i zmień”, czyli CUSTOM z `originId`. Wersja od autora zostaje nietknięta.
- Trasy `PlanEditorRoute(programId: String? = null)` i `WorkoutEditorRoute(workoutId: String? = null, programId: String? = null)`. Trening wbudowany albo z innego planu dodaje się wyłącznie jako kopia pod prefiks planu. Po seedzie v7 (D-43) do skopiowania zostają praktycznie tylko treningi z innych planów.
- Model w `blocksJson`, tylko z wartościami domyślnymi (D-04): `BlockItem.note = ""`, `Block.label = ""`, `Step.Work.note` i `blockLabel` (checkpoint). Superseria dalej jako CIRCUIT, bez nowych enumów.
- Edytor treningu:
  - czas szacowany z `WorkoutPlanner.estimateSeconds`,
  - „Przerwa po bloku” (`restAfterSec`), etykieta bloku, „Duplikuj blok”,
  - okno pozycji zamienione na `ModalBottomSheet`: cel, serie i przerwy, ciężar 0–500 kg co 0,5, wskazówka do 280 znaków, „Zmień ćwiczenie” (serie i przerwy zostają), „Duplikuj pozycję”,
  - `ExercisePickerDialog` z `weight(1f, fill = false)` zamiast `heightIn(max = 420.dp)`,
  - stan okien w `rememberSaveable`,
  - klucze `LazyColumn` stabilne, nie `block-$index`.
- `WorkoutEditing`:
  - `sanitize` dostaje `rounds` 1..30, `restBetweenRoundsSec` i `restAfterSec` 0..600, `cadenceSec` 1..60 i `weightKg` skończone 0..500,
  - `warnings(...)` (nieblokujące, kolor `tertiary`): cel niezgodny z `Exercise.kind` (D-27), brak sprzętu, ćwiczenie spoza bazy, BREATHING w bloku bez `fixed`, przerwa, której player nie zagra.
- Player i szczegóły: wskazówka pod nazwą ćwiczenia, ciężar jako „20 kg” albo „22,5 kg” (dziś „20.0 kg”, `ui/player/PlayerScreen.kt:332`).
- Przełącznik planu „Dopasowuj cele do poziomu i trenera” ustawia `fixed = !adaptive` we wszystkich blokach.
  - Opis przy wyłączonym: „Wartości dokładnie jak wpisane: bez poziomu, trenera i «Podbij cele»”.
  - **Plan własny domyślnie ma przełącznik włączony** (bloki bez `fixed`), więc zachowuje się jak plan z kreatora i nie traci progresji (D-24, D-35). Zmienione w D-49: nowy plan własny startuje z wyłączonym.
  - Osobnej flagi „dosłownie, ale z progresją” nie dodajemy. Każda kombinacja flag to nowa gałąź w `WorkoutPlanner.shownTarget` i w niezmienniku `WorkoutPlannerTest:253-267`.
- Ustawienie `authorName` („Twoje imię na planach i raportach”) w DataStore i w `BackupSettings`.
- Testy:
  - `PlanEditingTest`: szkielet 4×3 A/B, przypadek `done = {1,3}`, kradzież odhaczenia, kolizja id,
  - `WorkoutEditingTest`: nowe pola, `sanitize`, `canEdit`, zachowanie id,
  - `WorkoutPlannerTest`: `note`/`label` w kroku, niezmiennik,
  - dekodowanie starego `blocksJson` i starego checkpointu.

### 4. Plik planu

- Format w `domain/plan/share/PlanFile.kt`. Czysty Kotlin, `@SerialName` na każdym polu na stałe, enumy jako `String`:

```kotlin
@Serializable data class PlanFile(val format: String /* "mysttic-trainings-plan", bez domyślnej */,
  val formatVersion: Int /* 1, bez domyślnej */, val appVersion: String = "", val seedVersion: Int = 0,
  val exportedAt: Long = 0, val plan: SharedPlan)          // treść pod "plan": BackupFile nie złapie kluczy
@Serializable data class SharedPlan(val uid: String, val name: String, val author: String = "",
  val description: String = "", val days: List<SharedDay>, val workouts: List<SharedWorkout>,
  val exercises: List<SharedExercise> = emptyList(),
  val notes: List<SharedNote> = emptyList())          // D-44: notatki do ćwiczeń planu, tylko dla ćwiczeń z pliku
@Serializable data class SharedNote(val exercise: String, val note: String)
@Serializable data class SharedDay(val day: Int, val workout: String? = null)   // klucz "w1"; null = odpoczynek
@Serializable data class SharedWorkout(val key: String, val name: String, val note: String = "", val blocks: List<SharedBlock>)
// SharedBlock/SharedItem: lustro Block/BlockItem (kind, targetKind jako String, te same domyślne, + note/label, + windowSec z D-43)
// SharedExercise(id, namePl, nameEn, kind, level, category, equipment, primaryMuscles, secondaryMuscles,
//                instructionsPl, defaultReps, defaultSeconds) — bez obrazów i wariantów
```

- W pliku nie ma `TrainingProfile` (wzrost i waga), sesji, `exercise_tuning`, `startedAt` ani `active`. Preset eksportuje się jako migawka etapów z `fixed = true`.
- **Eksport:** `PlanFileMapper.export` jest czysty i deterministyczny: klucze `w<n>`, dni gęste razem z odpoczynkami, migawka każdego użytego ćwiczenia. `shareUid` (UUID) nadajemy przy pierwszym eksporcie i zapisujemy w `programs`.
- **`PlanFileValidator`** (czysty) sprawdza:
  - limity: wejście ≤ 1 MiB, dni 1..120 bez duplikatów, luki wypełniane odpoczynkiem, treningi ≤ 120, bloki ≤ 20 na trening, pozycje ≤ 30 na blok, `workCount` ≤ 500 na trening, ćwiczenia ≤ 300,
  - spójność odwołań: dzień z nieznanym albo zdublowanym kluczem treningu odrzuca plik; trening bez dnia daje ostrzeżenie i jest pomijany,
  - `uid` jako `[a-z0-9-]{1,64}`,
  - wartości przez rozszerzone `WorkoutEditing.sanitize`,
  - teksty: trim, bez znaków sterujących i bidi, nazwa ≤ 80, opis ≤ 2000, autor ≤ 60, wskazówka ≤ 280,
  - id ćwiczeń `[a-z0-9]+(-[a-z0-9]+)*` ≤ 64,
  - nieznany enum w `kind`, `targetKind`, `category` albo `equipment` usuwa pozycję z ostrzeżeniem. Ćwiczenie z nieznanym sprzętem nie może stać się „bez sprzętu”, bo sprzęt to zbiór wymagany w całości.
- **`ExerciseResolver`** (czysty):
  - id zaczynające się od `custom-` dostaje zawsze `custom-<8 znaków SHA-256 kanonicznej migawki>`. Ta sama treść daje to samo id, nie ma kolizji z seedem ani z innym autorem, a pozycje są przemapowane,
  - pozostałe id (rodzina seedu): jest lokalnie, więc bierzemy lokalne; brak lokalnie, więc wiersz `isCustom = 1` z `images = []`, który po aktualizacji seedu przejmie `SeedImporter` (test),
  - baza ćwiczeń nie przyjmuje id z prefiksem `custom-` (`tools/baza.py sprawdz`, `SeedIntegrityTest`; do v9 pilnował tego `tools/build_seed.py`).
- **Podgląd** `PlanImportPreview`: nazwa, autor, liczby, ostrzeżenia, braki sprzętu, wykluczenia trenera, wyższy `seedVersion` („zaktualizuj, żeby dostać ilustracje”), „Masz już ten plan”, gdy `shareUid` już jest.
- **`commit`** w jednej transakcji: ćwiczenia, `PlanCopy` pod nowe id (`source = IMPORTED`), dni, profil `neutral`. Bez automatycznej aktywacji; domyślne `fixed` zależy od pytania 2.
- **System:**
  - udostępnianie przez `ACTION_SEND` (`application/json`, `EXTRA_STREAM`, `ClipData`, `FLAG_GRANT_READ_URI_PERMISSION`, `createChooser`) i zapis przez SAF `CreateDocument`; plik `<slug-ascii>.plan.json`,
  - istniejący `FileProvider` (authority `.updates` bez zmian) dostaje w `res/xml/update_paths.xml` wpisy `<cache-path name="shared" path="shared/"/>` i `<cache-path name="reports" path="reports/"/>`,
  - pliki w `shared/`, `reports/` i `incoming/` starsze niż 1 dzień są sprzątane przy starcie i przed każdym eksportem.
- **Manifest `MainActivity`:**
  - filtr `SEND` tylko z `mimeType`, **bez `scheme`**. `IntentFilter.matchData` przy zadeklarowanym schemacie i intencji bez URI daje `NO_MATCH_DATA`,
  - filtr `VIEW` ze `scheme content` i `application/json`,
  - `text/plain` i `application/octet-stream` tylko wtedy, gdy test na telefonie pokaże, że Messenger albo Gmail ich używają. Zapas: „Wczytaj plik” (SAF).
- **Odbiór:** `handleIntent` przyjmuje tylko `content://` z authority innym niż własne. `PlanShareRepository.stage(uri)` czyta od razu, w zakresie aplikacji (singleton), a nie w `lifecycleScope`, i odkłada plik do `cacheDir/incoming/<uuid>.json`. Klucz trafia do `StateFlow` w singletonie, więc przeżywa obrót (`MainActivity.kt:48`). `TrainingsRoot` nawiguje do `PlanImportRoute(stagedKey)` z `launchSingleTop`; player biegnie dalej w serwisie (D-16).
- Plik rozpoznany jako kopia zapasowa daje komunikat „To kopia zapasowa, wczytaj ją w Ustawieniach”.
- Testy:
  - `PlanFileFormatTest`: round-trip, plik wzorcowy w `app/src/test/resources` zamraża nazwy pól,
  - `PlanFileValidatorTest`: wrogie `rounds = 10^6`, NaN, duplikaty, klucze, bidi, nieznany sprzęt,
  - `ExerciseResolverTest`, `PlanFileMapperTest` (eksport, import, eksport daje ten sam `plan`),
  - `IncomingUriTest`.

### 5. Statystyki planu i PDF

- `domain/stats/PlanStatsCalculator.compute(program, workoutNames, sessions, logs, zone, today)` zwraca `PlanStats`: frekwencja x z y, `days: List<PlanDayResult(dayIndex, workoutName, sessionId?, doneAt?, durationSec?, rpe?, loggedSets, plannedSets?)>` z nowego `PlanProgress.resolveDetailed`, `exercises` (serie, trafienie celu, pierwszy, ostatni i najlepszy wynik, maksymalny ciężar „z planu”), tygodnie planu z kolejki (`(dayIndex-1)/7+1`, nie z kalendarza, D-21), RPE, notatki, przerwane sesje osobno.
- Reguły obliczeń:
  - tylko sesje DONE,
  - deduplikacja logów po `(sessionId, blockIndex, itemIndex, round, setIndex)`, zostaje ostatni wpis (skutek „Wstecz”),
  - AMRAP i pozycje `openEnded` (według szablonu przy zgodnym `exerciseId`) poza „% celu”,
  - `plannedSets` z sesji, a przy `null` szacunek z `workCount`, oznaczony w raporcie.
- **`CycleReview` (D-22) zostaje przy starej formule:** surowe logi i `workCount` bieżącego szablonu. Przenosi się tylko z ViewModelu do `PlanProgression.review` z testem blokującym wynik. Nowa kompletność w `PlanStats` jest osobną miarą. Inaczej deduplikacja i `plannedSets` mogłyby przestawić próg 0,8 i mnożnik `targetScale`.
- `SetLog.hitTarget` przestaje porównywać sekundy AMRAP z powtórzeniami. `HistoryScreen` pokazuje „N powt. w X s”, a `SetProgression` pomija AMRAP od etapu 1.
- `ProgressStats.weeklyVolume` i `heatmap` dostają zakres od–do.
- `PlayerController.changeDifficulty` aktualizuje `sessions.difficulty` (dług z PLAN.md), inaczej raport pokaże zły poziom.
- `domain/report`:
  - `PlanReport(title, subtitle, generatedAt, sections)` z sekcjami `KeyValues | Table | Chart | Notes`,
  - `PlanReportBuilder` (polska odmiana z `Announcer.plural`, daty `pl-PL`, bez → i Δ, których Sora nie ma),
  - `ReportPaginator.paginate(blocks, measure, pageHeight)` z wstrzykniętym pomiarem i powtórzonym nagłówkiem tabeli.
- `ui/common/ChartPainters.kt`:
  - `DrawScope.drawVolumeBars`, `drawRecordLine` i `drawHeatmap` (kalendarz przepisany ze Spacerów na prostokąty), `ChartColors.from(ColorScheme)`, grubości w dp,
  - ekran rysuje przez `Canvas {}`, PDF przez `CanvasDrawScope().draw(Density(1f), …, Canvas(page.canvas), size)` (D-23).
- `data/report/PdfReportRenderer`:
  - `PdfDocument`, A4 595×842 pt, marginesy 40 pt,
  - `TextPaint` z Sorą i `fontVariationSettings` (`'wght' 400/600`), `StaticLayout`,
  - kolory `schemeFor(AppTheme.PAPER)`,
  - dwa przebiegi (paginacja, potem stopka „strona X z Y”), `close()` w `finally`.
- Podgląd w aplikacji przez platformowy `PdfRenderer`, bez nowej zależności.
- `ReportRepository`: zapis przez SAF `CreateDocument("application/pdf")` i udostępnianie z `cacheDir/reports`.
- UI: `PlanStatsRoute(programId)` z `PlanScreen` i listy planów (nie z „Dziś”, D-26), przyciski „Udostępnij raport” (`Icons.Filled.Share`) i „Zapisz PDF”.
- Notatki sesji w raporcie za przełącznikiem. Wpis w `docs/privacy-policy.md`.
- Testy:
  - `PlanStatsTest` (deduplikacja, AMRAP, `plannedSets = null`, tygodnie z kolejki),
  - `PlanProgressionTest` (`CycleReview` bez zmian, także z duplikatami logów),
  - `PlanReportBuilderTest`, `ReportPaginatorTest`, rozszerzone `ProgressStatsTest` i `SessionStatsTest`,
  - `ChartPaintersScreenshotTest` (Robolectric NATIVE),
  - renderer sprawdzany na emulatorze, bo natywny `PdfDocument` w Robolectricu jest niepotwierdzony.

### 6. Tablety i duże ekrany

- `ui/adaptive/AppLayout(widthDp, heightDp)` z `BoxWithConstraints` na korzeniu `TrainingsRoot`, bez `material3-adaptive`: nie wiadomo, czy BOM 2026.08.00 ją zawiera, a `NavigationSuiteScaffold` wymaga ikon, czego D-32 zabrania. Pola:
  - `navRail = w >= 600 || h < 480`,
  - `twoPane = w >= 840 && h >= 480` (warunek na wysokość chroni telefon w poziomie),
  - `playerSideBySide = w > h && w >= 600`,
  - `tileColumns` 2, 3 albo 4.

  Udostępnione przez `LocalAppLayout`. Test `AppLayoutTest` dla 411×891, 891×411, 700×1000, 800×1280 i 1280×800.
- `NavRail` o tym samym języku co dolny pasek (etykiety, wskaźnik 34×4, bez ikon) i `VerticalHairline`.
- `Dimens.contentMaxWidth = 640.dp` i `listPaneWidth = 400.dp`. `Modifier.readableWidth()` i `readableContentPadding()` dla `LazyColumn`. „Dziś” zostaje jedną wycentrowaną kartą.
- `ui/common/TwoPane(list, detail)`:
  - od etapu 2 na liście planów (lista | szczegół planu przez wspólne `PlanDetailSource.observe(id)`, bez assisted injection),
  - potem w bibliotece i Historii.
  
  Edytor treningu zostaje osobną trasą także na tablecie, dopóki `WorkoutEditorViewModel` bierze id z `toRoute` (`:57`). Assisted injection wchodzi dopiero w etapie 6. Wybór panelu trzymamy w `rememberSaveable`, a przy przejściu z szerokiego na wąskie ekran nawiguje do trasy szczegółu.
- Istniejące ekrany:
  - player w poziomie obok siebie (bezstanowe `PlayerContent`; autoskalowanie liczby do 72 sp tylko w ograniczonym pudełku, zgodnie z D-32),
  - nagłówek kreatora i programów w przewijanej części,
  - `imePadding` przy notatce, obraz ćwiczenia z `heightIn(max = 360.dp)`, siatka biblioteki z `tileColumns`,
  - notatka i RPE w szczegółach sesji w `rememberSaveable`.
- Zrzuty: `LayoutScreenshotTest` z qualifiers `w411dp-h891dp-port`, `w891dp-h411dp-land`, `w700dp-h1000dp-port` i `w1280dp-h800dp-land`. Warunek: bezstanowe `*Content` z fiksturami. Wzorce nagrywa `-Proborazzi.record=true`, a CI tylko rysuje (D-40).
- Emulator: AVD `Pixel_Tablet_API_37` (`avdmanager create avd -d pixel_tablet`, ten sam obraz co `Pixel_9_Pro`: API 37, x86_64, 16 KB, port 5556). Orientację ustawia `settings put system accelerometer_rotation 0` i `user_rotation 0|1`; split-screen z Przeglądu. Skrypty `tools/przebieg*.py`:
  - wymiary z korzenia `uiautomator dump` zamiast `EKRAN_WYSOKOSC = 2856`,
  - `przewin()` od 0,75H do 0,35H,
  - flaga `--orientacja port|land`,
  - `przebieg_preset.py` w `main()`,
  - `testTagsAsResourceId` tam, gdzie dziś wybór idzie po kolejności węzłów,
  - nowy `tools/przebieg_plany.py --etap N`.

### Zarzuty krytyki i co z nimi zrobiono

| Zarzut | Rozwiązanie |
|---|---|
| `toWorkout` zeruje `workouts.programId` (blokujące) | kolumny nie ma, właściciela wyznacza prefiks id, który przeżywa każdą drogę zapisu; `PlanSessions` ma regułę zapasową dla `null` |
| filtr `SEND` ze `scheme` nie łapie niczego | `SEND` tylko z `mimeType`; `text/plain` i `octet-stream` dopiero po teście na telefonie |
| kopia v1/v2 wchodzi jako WIZARD | `BackupProgram.source` nullable, `null` liczone z `profileJson`, test |
| `CycleReview` przestanie być identyczne | stara formuła zostaje, nowa kompletność jest osobną miarą |
| blokada prefiksu dni zamiast zbioru | blokujemy `doneDayIndexes` i walidujemy I6, test `done = {1,3}` |
| sprzątanie kasuje treningi szkicu | wiersz planu powstaje od razu, sprząta się tylko treningi bez właściciela i starsze niż 1 dzień |
| profil CUSTOM/IMPORTED i bramki kreatora | `TrainingProfile.neutral` i `isWizard` we wszystkich odczytach profilu |
| ochrona `fixed` dopiero w E2 | poprawki `Coach`, `SetProgression` i `acceptsProgression` w etapie 1 |
| kopia presetu niesie `presetId` | `PlanCopy` zeruje, `presetId` czytany tylko przy PRESET |
| edycja przy sesji w toku | I11: `PlanLock` w domenie i ViewModelach |
| walidator bez spójności odwołań | klucze, duplikaty, treningi bez dni, format `uid` |
| `fixed` domyślnie zabija progresję planu własnego | plan własny domyślnie adaptacyjny, import to pytanie 2; osobnej flagi nie dodajemy (powód w sekcji 3); od D-49 nowy plan własny startuje dosłownie, a progresję włącza przełącznik |

Drobne zarzuty też są uwzględnione:
- id `-w<UUID8>`,
- `MAX_PLAN_DAYS`,
- AMRAP w `SetProgression`,
- przestrzeń `custom-<hash>`,
- sprawdzanie PDF biblioteką standardową Pythona,
- `authorName` w kopii zapasowej,
- edytor jako trasa do etapu 6,
- `rhythmStart` zamiast przepisywania `startedAt`,
- linia o katalogu w `CLAUDE.md`,
- walidacja URI i odbiór w zakresie aplikacji,
- determinizm bez `LIMIT 1` bez sortowania.

Brak porównania „Δ” w pierwszej sesji nowego przebiegu zostaje jako opisane ograniczenie.

## Etapy

1. **Fundament danych (Room v4), bez nowych ekranów.**
   - Zakres:
     - migracja 3→4, encje i DAO, `PlanSource` i pola `Program`,
     - `sessions.programId` i `plannedSets` z `PlayerController`,
     - `PlanSessions` zamiast pięciu kopii, `PlanLock`,
     - transakcje w `ProgramRepository` i `BackupRepository`, `deleteUnusedOwnedWorkouts`,
     - `lastWizardProgram` po źródle, czysty profil w kreatorze,
     - I10 (`Coach`, `SetProgression`, `acceptsProgression`), AMRAP w `SetProgression`,
     - kopia v3 z backfillem i utwardzeniem importu,
     - tekst „Usunąć plan?”, `docs/DATABASE.md`.
   - Zrobione, gdy:
     - `MigrationTest` 3→4 pokazuje identyczny postęp przed i po,
     - `PlanSessionsTest`, `ProgramRepositoryTest` i `BackupFormatTest` v3 są zielone, `check_schema.py` zielony,
     - aktualizacja w miejscu z 0.10.0 z historią na emulatorze (nie po `pm clear`) daje ten sam stan „Dziś”, Historii i serii dni,
     - `przebieg.py` i `przebieg_preset.py` przechodzą bez zmian.
   - Ryzyka: `MigrationTestHelper` pod Robolectriciem (zapas: aktualizacja na emulatorze), zgodność `defaultValue` z SQL, założenie kompaktowego JSON-u w `LIKE`.

2. **Lista planów, aktywacja, duplikat, powtórka i szkielet dużych ekranów.**
   - Zakres:
     - `PlansRoute`, `PlanRoute(programId)`, `PlanWizardRoute(fromProgramId)`, menu planu,
     - `activate`, `duplicate`, `repeat`, `PlanRhythm`, `PlanList`, podmiana wykluczeń przy aktywacji,
     - okno przełączenia (według pytania 1),
     - `AppLayout`, `NavRail`, `readableWidth`, `TwoPane` dla listy planów,
     - skrypty przebiegu z dynamicznym ekranem i `--orientacja`, AVD tabletu w `docs/testing.md`,
     - D-42.
   - Zrobione, gdy:
     - przełączenie A → B → A zachowuje postęp obu (test repozytorium i `przebieg_plany.py --etap 2`),
     - kopia ma rozłączny postęp, a usunięcie kopii nie rusza oryginału,
     - „Dziś” pokazuje tylko aktywny plan,
     - lista i szczegół obejrzane na `Pixel_Tablet_API_37` w obu orientacjach.
   - Ryzyka: seria dni po wznowieniu (nowa semantyka `rhythmStart`), zapchana sekcja „Zakończone” starymi cyklami, przejście z szerokiego na wąskie z wybranym szczegółem.

3. **Edytor planu i brakujące pola.**
   - Zakres:
     - `PlanDraft`, `PlanEditing`, `PlanEditorRoute`, „Ułóż własny plan”, szkic jako wiersz DRAFT,
     - `WorkoutEditorRoute(workoutId?, programId?)`, `canEdit` w domenie,
     - `note`, `label`, ciężar, przerwa po bloku, podmiana i duplikowanie, arkusz pozycji, ostrzeżenia, format kg,
     - przełącznik „Dopasowuj cele…”, `authorName`.
   - Zrobione, gdy:
     - `PlanEditingTest` (zbiór zrobionych dni, kradzież odhaczenia, kolizje id), `WorkoutEditingTest` i dekodowanie starego `blocksJson` i checkpointu są zielone,
     - plan 4×3 z A/B da się ułożyć na `Pixel_9_Pro` w 10 minut,
     - przebieg „ułóż, aktywuj, zrób dzień 1, spróbuj przesunąć dzień 1” działa,
     - `adb shell am kill` w trakcie edycji niczego nie gubi.
   - Ryzyka: arkusz pozycji na 360 dp, rozjazd zapisu „trening od razu, plan na końcu” (komunikat przy porzuceniu).

4. **Plik planu.**
   - Zakres: format, `PlanFileMapper`, `PlanFileValidator`, `ExerciseResolver`, `PlanImportRoute` z podglądem, `commit` w transakcji, udostępnianie i SAF, filtry intencji, `stage`, sprzątanie cache, reguła `custom-` w `build_seed.py`.
   - Zrobione, gdy:
     - testy formatu, walidatora, resolvera i URI są zielone,
     - eksport i import między dwoma emulatorami daje tę samą treść pod nowymi id, a ponowny import pokazuje „Masz już ten plan”,
     - plik planu podany do „Importuj kopię” daje czytelny błąd,
     - **na telefonie właściciela sprawdzone wysyłanie i otwieranie przez Messenger, Gmail i Pliki Google, z zapisem, które MIME były potrzebne.**
   - Ryzyka: MIME w komunikatorach, uprawnienie `content://` wygasające po chwili, kontrakt `@SerialName` na lata.

5. **Statystyki planu i raport PDF.**
   - Zakres: `resolveDetailed`, `PlanStatsCalculator`, `PlanProgression.review`, zakresowe `ProgressStats`, poprawka AMRAP w Historii, `sessions.difficulty` po zmianie poziomu, `ChartPainters`, `domain/report`, `PdfReportRenderer`, podgląd przez `PdfRenderer`, `PlanStatsRoute`, polityka prywatności.
   - Zrobione, gdy:
     - testy JUnit są zielone, `CycleReview` bez zmian,
     - `przebieg_plany.py --etap 5` robi 2 sesje i PDF, `run-as cat` zapisuje go do `build/`, a skrypt w Pythonie (sama biblioteka standardowa) sprawdza nagłówek `%PDF-` i liczbę stron,
     - zrzut podglądu pokazuje polskie znaki i wagę 600.
   - Ryzyka: `PdfDocument` może zgubić oś `wght` (zapas: statyczne pliki Sora OFL tylko do PDF), stare sesje bez `plannedSets` (szacunek oznaczony w raporcie).

6. **Pełny przegląd dużych ekranów.**
   - Zakres:
     - player i podsumowanie w poziomie, kreator i programy w niskim oknie,
     - biblioteka i Historia jako lista ze szczegółem,
     - assisted injection dla ViewModeli szczegółów i edytor treningu w prawym panelu,
     - bezstanowe `*Content`, `LayoutScreenshotTest` z wzorcami w repo,
     - lista kontrolna tabletu, `docs/guide`, `docs/screenshots`, `docs/site-page.md`.
   - Zrobione, gdy:
     - `przebieg`, `przebieg_preset`, `przebieg_biblioteka` i `przebieg_plany` przechodzą na `Pixel_Tablet_API_37` w obu orientacjach i na `Pixel_9_Pro` w poziomie,
     - obrót i split-screen w trakcie treningu i przy otwartym arkuszu pozycji niczego nie gubią,
     - „Dziś” na tablecie to jedna karta.
   - Ryzyka: odtwarzanie ViewModeli po śmierci procesu po przejściu na assisted injection, różnice wzorców Windows i Linux, wycięcie aparatu w poziomie przy `enableEdgeToEdge`.

## Otwarte pytania

Rozstrzygnięte przez właściciela 2026-09-23: 1 — A (przełączanie w każdej chwili, aktywny jeden), 2 — A (dosłownie), 3 — A (ciężar zaplanowany).

1. **Przełączanie aktywnego planu przy niedomkniętej kolejce: (A) wolno, po potwierdzeniu, a bieżący plan zostaje wstrzymany z postępem, czy (B) nowy plan można aktywować dopiero po domknięciu bieżącego albo po jawnym „Przerwij plan”?** Rekomenduję A. Lista planów „z której wybiera się aktywny” traci sens, jeśli wybór jest zablokowany na tygodnie, a postęp i tak się nie gubi, bo liczy go `sessions.programId`. A zmienia literę D-37 i linię o katalogu w `CLAUDE.md`.
2. **Plan od kogoś: (A) domyślnie dosłownie (bloki `fixed`: bez skalowania poziomem, trenera i „Podbij cele”), z przełącznikiem „Dopasuj do mojego poziomu” w podglądzie importu, czy (B) domyślnie dopasowany do poziomu odbiorcy (początkujący widzi około 70% powtórzeń i łatwiejsze warianty, ciężar bez zmian)?** Rekomenduję A. Autor pisze wartości dla konkretnej osoby, a przy B raport wraca do niego z innymi liczbami, niż wpisał.
3. **Ciężar w raporcie: (A) pierwsza wersja raportu z ciężarem zaplanowanym i RPE na całą sesję, bez zmian w playerze, czy (B) najpierw wpisywanie faktycznego ciężaru przy serii (nowe zdarzenie silnika, kolumna w `set_logs`, migracja v5, o etap dłużej)?** Rekomenduję A teraz, a B jako pierwszy etap po tej funkcji, jeśli autorzy planów z ciężarami okażą się głównymi odbiorcami raportu.
