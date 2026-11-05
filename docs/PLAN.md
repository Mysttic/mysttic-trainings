# Plan projektu

## Po co ten dokument

Cel: własna aplikacja Android do codziennych treningów w domu. Sama układa cykl z danych właściciela, prowadzi przez
trening (odliczanie, powtórzenia, sygnały) i zapamiętuje postęp. Bez reklam, subskrypcji, konta i sieci.

Ten plik trzyma **to, co jeszcze nie zrobione**, plus krótkie archiwum. Reszta mieszka gdzie indziej:

| pytanie | plik |
|---|---|
| co poszło w którym wydaniu | `CHANGELOG.md`, `git tag` |
| dlaczego tak, a nie inaczej | `docs/DECISIONS.md` |
| jak to zbudować i sprawdzić | `docs/building.md`, `docs/testing.md`, `CLAUDE.md` |
| co jest w bazie | `docs/DATABASE.md` |
| skąd się bierze biblioteka ćwiczeń | `docs/SEED.md` |
| jak działają plany do udostępniania | `docs/plany-do-udostepniania.md` |
| zapis przebiegów i weryfikacji do 2026-09-23 | `git show f45533e:docs/PLAN.md` |

## Stan na 0.9.1 (2026-09-23)

- Biblioteka: seed v9, 283 ćwiczenia (w tym 20 na ergometry), każde z jedną ilustracją generowaną, zero zdjęć z sieci.
  Seed nie wozi już szablonów treningów. Żadna definicja nie czeka na ilustrację.
- Kod: 381 testów jednostkowych w 44 klasach, Room v5, kopia zapasowa v4, jedenaście gotowych programów, kreator planu,
  trener, edytor treningu, trzy motywy (D-39), a od D-42 lista planów, edytor planu, plik planu i raport PDF.
- Wydania: dwanaście tagów (`v0.1.0` … `v0.9.1`), aktualizacja z poziomu aplikacji przez stronę
  <https://mysttic.github.io/Mysttic/mysttic-trainings/> (D-33). Wersja 0.10.0 (motywy) jest scalona do `master`,
  ale tagu jeszcze nie ma: wydanie czeka na minuty Actions (patrz „Wydania i CI”).
- CI: `testy.yml` (jeden bieg na PR z `develop`, przy „Ready for review” albo ręcznie; D-41, PR #19) i `wydanie.yml`
  (podpisany APK po scaleniu z podbitym `VERSION.md`).
- Weryfikacja: emulator Pixel_9_Pro (API 37), dla dużych ekranów AVD Pixel_Tablet_API_37 (D-42), plus skrypty przebiegu
  `tools/przebieg.py`, `tools/przebieg_preset.py`, `tools/przebieg_biblioteka.py`. **Telefon nie jest bramką wydania**
  (decyzja właściciela z 2026-09-18): to, co przechodzi na emulatorze, uznajemy za sprawdzone.

## Wymagania właściciela

- **Aplikacja układa plan sama**: użytkownik podaje wzrost, wagę, cel, poziom, sprzęt i ile ma czasu, a dostaje gotowy
  cykl do uruchomienia. Po zamknięciu cyklu generuje kolejny z zaktualizowanymi danymi.
- Ćwiczenia z podziałem „bez sprzętu” / „ze sprzętem”; użytkownik zaznacza, co ma, a dla ćwiczeń wymagających
  brakującego sprzętu aplikacja proponuje zamienniki.
- Kilka poziomów trudności, zmiana możliwa **w każdym momencie**, także w trakcie treningu.
- Jeden trening na ekranie: „Dziś” pokazuje wyłącznie następną pozycję z kolejki (D-26).
- Naraz jeden aktywny plan; pozostałe czekają na liście z postępem, a przełączyć wolno w każdej chwili (D-42, zmienia
  literę D-37).
- Każdy może ułożyć plan, zmienić go i wysłać innym; plan od kogoś wchodzi dosłownie, tak jak go ułożył autor (D-42).
- Statystyki i raport PDF osobno dla każdego planu (D-42).
- Dobra obsługa tabletów i ekranu w poziomie. Tylko Android, bez sieci i konta, sklep odłożony (D-42).
- Package `pl.mysttic.trainings`, repozytorium prywatne, bez publikacji w sklepie.

## Do zrobienia

### Gotowe programy po pierwszym użyciu (issue #21, #22, D-46)

- [x] Kod i testy (2026-09-29): zamienniki bez sprzętu w „Siedmiu minutach” i blokada „Deski i drążka” bez drążka,
      trudność programów na liście (od łagodnych), pominięte ćwiczenia w podsumowaniu z opiniami, ocena wysiłku
      od 6 do 10 z podpisami w podsumowaniu i Historii, deska boczna z przerwą i podpisami stron. 390 testów zielonych.
      Na emulatorze Pixel_9_Pro: lista z trudnością i wyszarzonym drążkiem, „Siedem minut” bez ławki (wykrok skrzyżny,
      pompki wąskie), przerwa 5 s i „Druga strona”, siedem pominiętych ćwiczeń w podsumowaniu, skala w Historii.
- [ ] Zamknąć issue #21 i #22 po wydaniu 1.0.0.

### Seed i biblioteka

- [x] **Issue #20 (D-44): 60 ilustracji nowych ćwiczeń** z `generator-zdjec-nowe.html` (2026-09-25): 20 na ergometry
      i 40 ze sprzętem weszło w seedzie v8. Na emulatorze: import v8 (254 ćwiczenia), biblioteka z ergometrami
      i ilustracjami, notatka planu przeżyła re-import seedu.
- [x] Issue #20 (D-44) w kodzie: ergometry jako sprzęt poza kreatorem i poza zamiennikami zwykłych ćwiczeń, cel na czas
      do godziny z krokiem 5 s / 15 s / minuta, notatki do ćwiczeń w planie (Room v5, szczegóły treningu, player, kopia
      i powtórka planu, plik planu, kopia zapasowa v4). Sprawdzone na emulatorze Pixel_9_Pro: migracja 4 → 5 na danych,
      notatka wpisana w szczegółach treningu widoczna w playerze.
- [ ] Na emulatorze po ilustracjach: trening na wioślarzu 20 min ułożony w edytorze (czas „20:00” w playerze, krok
      minutowy w edytorze), ergometr w filtrach biblioteki, kreator bez ergometrów przy zaznaczonym rowerze.
- [x] **29 ilustracji z uzupełnienia** (2026-09-25, seed v9). Dwie przyszły drugi raz, bo generator narysował zły ruch
      (odwodzenie nogi wyglądało jak wymach do tyłu, pompki pike z kolan jak rozciąganie). Przegląd całej biblioteki
      (283 ćwiczenia, skrót obrazu i podobieństwo opisów): bez dubli, najbliższe pary to warianty i ten sam ruch z innym sprzętem.
      Lista i powody:
      - ciągnięcie i kardio dla zaawansowanego bez sprzętu (`towel-row-door-single-arm`, `burpee-tuck-jump`): dziś obie
        role nie mają na tym poziomie ani jednego kandydata, więc plan cicho podstawia ćwiczenia dla początkującego;
      - plyometria (`pogo-hops`, `skater-jumps`): przy celu „redukcja” generator ją premiuje, a w seedzie są tylko trzy
        takie ćwiczenia i żadnego dla początkującego;
      - push dla początkującego bez sprzętu (`pike-pushups-knees`, `kneeling-triceps-extension-floor`): cztery kandydatury,
        z czego trzy to ta sama pompka;
      - zawias biodrowy na poziomie zaawansowanym (`sliding-leg-curl-single-leg`);
      - rozciąganie pleców (`lat-stretch-kneeling`, `thread-the-needle`): plan mobilności nie miał ani jednego ćwiczenia
        w roli „pull”;
      - zamienniki wąskich partii (`side-lying-leg-raise`, `fire-hydrant`, `self-resisted-biceps-curl`, `prone-lat-pulldown`,
        `fingertip-plank`, `dumbbell-shrug`, `band-shrug`): dziś „Nie chcę” na takim ćwiczeniu kasuje pozycję z treningu,
        bo `EquipmentMatcher.substitutes` nie ma czym podmienić;
      - sprzęt z dawnej listy braków (`dips-between-chairs`, `toes-to-bar`, `kettlebell-clean`, `kettlebell-snatch`);
      - sztanga (`barbell-*`, dziewięć pozycji): `Equipment.BARBELL` był do zaznaczenia w ustawieniach i w kreatorze,
        a w bibliotece nie miał ani jednego ćwiczenia.
- [ ] Po wczytaniu ilustracji sprawdzić na emulatorze plan dla zaawansowanego bez sprzętu i dla profilu ze sztangą.
- [ ] Przegląd partii mięśniowych i celów przy pozycjach dopisywanych ręcznie do `ILLUSTRATED` (dobierane z kart
      generatora, nie z datasetu).

### Plany do udostępniania (D-42)

Decyzja i pełny projekt: D-42 i `docs/plany-do-udostepniania.md`. Kod sześciu etapów jest na `develop` (scalony
w 72de1c7), a po scaleniu z długiem technicznym D-43 build i testy jednostkowe przechodzą (2026-09-23).
`[~]` znaczy: napisane, niesprawdzone na emulatorze.

- [~] Etap 1, dane: Room v4 (`programs.source/description/author/originId/shareUid/updatedAt/rhythmStart`,
      `sessions.programId/plannedSets` z wypełnieniem wstecz dzisiejszą regułą), `PlanSessions` zamiast pięciu kopii reguły,
      `PlanLock` (plan z sesją w toku tylko do odczytu), transakcje w `ProgramRepository` i `BackupRepository`,
      `lastWizardProgram` po źródle, `fixed` chroniony przed „Nie chcę” i „Podbij cele”, AMRAP poza `SetProgression`,
      kopia v3 (źródło cyklu z v1/v2 liczone z `profileJson`, import odrzuca `{}` i plik planu), tekst „Usunąć plan?”,
      `docs/DATABASE.md`. `MigrationTest` 3 → 4 i aktualizacja w miejscu: osobny punkt niżej.
- [~] Etap 2, lista planów: `PlansRoute`, `PlanRoute(programId)`, menu planu (duplikuj, zacznij od nowa, usuń),
      aktywacja w transakcji z kotwicą rytmu, podmiana wykluczeń trenera przy aktywacji, odsyłacz „Twoje plany” na „Dziś”;
      szkielet dużych ekranów (`AppLayout`, szyna bez ikon, `readableWidth`); skrypty przebiegu z wymiarami ekranu
      z `uiautomator dump` i `--orientacja`.
- [~] Etap 3, edytor planu: `PlanEditing` (dni, odpoczynki, przypisania, szkielet tygodni, blokada zrobionych dni jako zbioru),
      „Ułóż własny plan” (wiersz szkicu od razu), `canEdit` w domenie (D-19), w edytorze treningu ciężar, przerwa po bloku,
      etykieta bloku, wskazówka, podmiana ćwiczenia, ostrzeżenia, czas szacowany; przełącznik „Dopasowuj cele do poziomu
      i trenera” (plan własny domyślnie włączony); „Twoje imię” w Ustawieniach i w kopii.
- [~] Etap 4, plik planu: format `mysttic-trainings-plan` v1 (`.plan.json`, od D-43 z `windowSec`), walidator z limitami
      i spójnością odwołań, ćwiczenia własne jako `custom-<skrót treści>`, podgląd importu, zapis w transakcji bez
      automatycznej aktywacji, udostępnianie i SAF, filtry `VIEW`/`SEND`, sprzątanie `cacheDir`.
- [~] Etap 5, statystyki i raport: `PlanStatsRoute`, tygodnie z kolejki, deduplikacja serii po „Wstecz”, `CycleReview`
      bez zmian (D-22), wykres tygodni na ekranie i słupki w PDF, `PdfDocument` A4 z Sorą, podgląd przez `PdfRenderer`,
      „Zapisz PDF” i „Udostępnij raport”, polityka prywatności.
- [~] Etap 6, duże ekrany: player i podsumowanie w poziomie, kreator i programy w niskim oknie, edytor treningu
      w prawym panelu, `docs/guide`.
- [x] Lista z `docs/testing.md` („Plany do udostępniania i duże ekrany”) na Pixel_9_Pro (pion i poziom) i na AVD
      `Pixel_Tablet_API_37` w obu orientacjach (2026-09-23..25, develop 407b330). Siedem błędów major poprawionych
      i sprawdzonych na emulatorze: przesunięcie zrobionego dnia w edytorze planu, kopia zapasowa nadpisująca treningi
      planów, które już są, brak wejścia w „Twoje plany” bez planu, czytelna szerokość (`readableWidth` nie zawężała),
      obrót gubiący arkusz pozycji i wybór ćwiczenia, szczegół ćwiczenia na całą szerokość tabletu oraz plik planu
      z surowym szablonem zamiast tego, co widzi autor. Raport z testów i zrzuty leżą lokalnie (`build/testy-d42`,
      `build/galeria-tablet`, poza repozytorium).
- [x] Ok. 60 znalezisk minor i cosmetic z tych samych testów poprawione (2026-09-25): import i wysyłka pliku planu
      (drugi plik z intencji, błędy odbioru, własna aplikacja w oknie udostępniania, pusty plik po błędzie zapisu),
      kopie planu (shareUid, autor, treningi bez dnia), edytor planu i kreator (nadwyżkowe sesje, uprzedzenie
      o wstrzymaniu planu, niskie okno), szczegóły i edytor treningu (ciężar i wskazówka, potwierdzenie przy treningu
      w toku, krok ciężaru), player w poziomie (przewinięcie między krokami, ucięta liczba, karta „Dalej”), czytelna
      szerokość, odmiany liczb, raport PDF z podglądem w aplikacji, skrypty przebiegu. Sprawdzone na Pixel_9_Pro
      w poziomie (`tools/przebieg.py --orientacja land` przechodzi do końca).
- [x] Resztki z testów D-42 (2026-09-25): klawiatura nie przesuwa już całego okna (`adjustResize` w manifeście
      i `imePadding` na treści w `TrainingsRoot`, L-7, O-6), „Porzuć” w edytorze planu usuwa treningi dodane w tej
      edycji (C3-5), podmiana „Nie chcę” dogania trening, który był w toku, po jego końcu (C1-5).
      Świadomie bez zmian: kafelki biblioteki o różnej wysokości ilustracji (O-5, D-38: ilustracja w całości),
      szczegóły treningu w niskim oknie (nagłówek przewija się razem z listą, stały jest tylko pasek ze Startem),
      wiersze edytora planu na komponentach z `DesignSystem` (C3-17, sam wygląd, po wydaniu).
- [ ] Po wydaniu 0.11.0: lista ze szczegółem obok siebie na tablecie (`TwoPane`, D-42 poza pierwszą rundą).
- [x] `tools/przebieg.py --orientacja` liczy rotację od położenia naturalnego (tablet leży poziomo), przewija wewnątrz
      treści w górę i w dół i szuka stepperów po etykiecie, więc przebieg w poziomie na telefonie przechodzi do końca.
- [x] `MigrationTest` (Robolectric, bez `room-testing`): baza budowana z `1.json`, `3.json` i `4.json` tak jak Room
      tamtej wersji, z danymi jak z 0.9.x (gotowy program, sesje przed i po starcie planu, serie, trener), otwierana
      bieżącym `AppDatabase` z całym łańcuchem migracji do v5. Room sam sprawdza schemat po migracji; test łapie zepsuty
      SQL migracji (sprawdzone celowym błędem w 4 → 5). Na emulatorze aktualizacja w miejscu 0.8.0 (v3) → v4
      i v4 → v5 na danych.
- [x] `app/schemas/.../4.json` ma prawdziwy `identityHash` (`da8d54b4…`, odczytany z bazy v4 z emulatora); tabele,
      kolumny i indeksy pliku zgadzają się z tą bazą.
- [ ] Na emulatorze przy okazji: eksport kopii na 0.9.1, import po czystej instalacji 0.11.0 i powrót planu na „Dziś”.
- [ ] Poza zakresem pierwszej rundy: `tools/przebieg_plany.py`, lista ze szczegółem obok siebie (dwa panele), zrzuty
      `LayoutScreenshotTest`. Na razie tablet dostaje szynę, czytelną szerokość i player w poziomie. Opis etapów
      w `develop` (`git show f45533e:docs/PLAN.md`) wymieniał jeszcze `TwoPane`, `PlanWizardRoute(fromProgramId)`
      i wspólne paintery wykresów dla ekranu i PDF; w kodzie ich nie ma (PDF rysuje słupki sam), więc też czekają.
- [ ] Wysyłka i otwarcie pliku planu przez Messenger, Gmail i Pliki Google (telefon albo emulator z tymi aplikacjami):
      zapisać, z jakim MIME przychodzi.

### Po scaleniu długu (D-43)

- [x] Test Robolectric z Room w pamięci (`data/db/WorkoutCleanupTest`): `ProgramRepository.delete` (rotacja, etapy bez dnia,
      same odpoczynki, cudze plany i prefiksy, sesja zakończona i w toku, drugie wywołanie) oraz `deleteOwnerlessPlanWorkouts`
      i `deleteUnusedBuiltIn` z pustym `keep`, które kasują dane przy pierwszym starcie po aktualizacji. Scenariusze
      przeniesione z dawnego `PlanCleanupTest`.
- [ ] Okno EMOM w edytorze treningu i w szczegółach treningu (np. etykieta „co 60 s”). Scalenie objęło domenę, plik planu
      i sito edytora, interfejs okna jeszcze nie pokazuje.
- [ ] Do decyzji: czy „Podbij cele” ma respektować blokadę planu z sesją w toku (`PlanLock`, D-42). Dziś nie blokuje.

### Wydania i CI

- [ ] **Wydanie 1.0.0 (D-45).** Pierwsze stabilne, niesie wszystko od 0.9.1, także motywy z niewydanej 0.10.0.
      2026-10-01 (minuty Actions wróciły): data w nagłówku `[1.0.0]` ustawiona, szkic PR z `develop` do `master`,
      „Ready for review” (testy w CI). Zostaje scalenie przez właściciela; `wydanie.yml` ruszy sam, bo scalenie
      zmienia `VERSION.md` z 0.10.0 na 1.0.0. Potem zamknąć issue #20, #21 i #22.
- [x] **Wydanie 0.10.0 nie powstanie**: zastąpione przez 1.0.0 (D-45), które niesie motywy. Nie uruchamiać ręcznie
      *Wydanie* na `master` przed scaleniem 1.0.0, bo wydałoby 0.10.0. Historia poniżej. Po scaleniu PR #17 `wydanie.yml` padał w dwie sekundy, bez ani jednego
      kroku i z pustymi logami; to samo robiło uruchomienie ręczne. Powód stał w adnotacjach check runu: „The job was
      not started because recent account payments have failed or your spending limit needs to be increased.”
      Właściciel potwierdził: skończyły się minuty Actions (repo jest prywatne, każda minuta jest płatna). Tag `v0.10.0`
      nie powstał, na stronie stoi 0.9.1. Kiedy minuty wrócą (odnowienie albo podniesienie limitu wydatków w GitHub →
      Settings → Billing and licensing): *Actions → Wydanie → Run workflow* na `master`. Bramka na istniejącym tagu chroni
      przed podwójnym wydaniem, więc powtórne uruchomienie jest bezpieczne.
- [ ] `wydanie.yml` nie uruchamia testów i nie zachowuje `mapping.txt` z R8.

### Z audytu 2026-09-19

Otwarte punkty z przeglądu całej aplikacji (siedem równoległych przeglądów plus sceptyk). Numery linii są z dnia audytu.

- [ ] **Szczegóły treningu pokazują przerwy, których player nie zagra.** `WorkoutDetailViewModel.kt:134-138` rysuje „przerwa N s”
      przy każdej pozycji i przy `sets == 1`, a `WorkoutPlanner` emituje ją tylko `if (itemIndex < block.items.lastIndex)` (:69)
      i `if (set < sets)` (:58). Dotyczy każdego planu z kreatora poza celem „siła” i wszystkich gotowych programów.
      Brakujący niezmiennik: test porównujący listę przerw ze szczegółów z krokami `Step.Rest` z `expand`.
- [ ] **Martwy kod.** `EquipmentMatcher.missingInWorkout`, `Program.dayAt`, `Program.nextTrainingDay`, `Announcer.formatClock`,
      `PresetCatalog.stageOf`: zero wywołań produkcyjnych, przy życiu trzymają je własne testy. Zegar formatują trzy
      implementacje naraz (`Announcer`, `PlayerScreen.clockValue`, `Components.formatDuration`).
- [ ] „Nowy trening” w edytorze: od D-42 wchodzi się tam z edytora planu (`WorkoutEditorRoute(null, programId)`,
      `TrainingsRoot.kt:320`), więc gałąź `isNew` żyje. Sprawdzić na emulatorze razem z edytorem planu i skreślić.
- [ ] Klawisz głośności: `MainActivity.kt:55` konsumuje zdarzenie przy węższym warunku niż `:62`, więc poza ekranem playera
      „down” idzie do systemu, a „up” ginie.
- [x] Test migracji Room jest (`MigrationTest`, patrz „Plany do udostępniania”); baza stoi na wersji 5.
- [ ] Liczebniki odmienione na sztywno w dopełniaczu: „22 sekund”, „2 serii”, „3 pompek”, „5 oddech z 30”
      (`PlayerScreen.kt:248`, `:295`, `HistoryScreen.kt:160`, `WorkoutDetailScreen.kt:133`, `WorkoutEditorScreen.kt:162`,
      `PresetsScreen.kt:213`): sześć prywatnych kopii reguły, a `Announcer.plural` robi to poprawnie i mówi dobrze w TTS.
- [ ] Seria i rekord dni kasują się po przekroczeniu długości cyklu i po założeniu nowego (`Program.kt:54`): od 31. dnia
      rozciągniętego cyklu żaden dzień nie jest odpoczynkiem, więc pierwsza przerwa zeruje serię.
- [ ] „Wznów” nic nie daje po bezpieczniku kroku bez limitu (`WorkoutEngine.kt:215`): w Wim Hofie po 10 × cel pauza wraca
      przy pierwszym ticku; wyjście tylko przez „Zakończ” albo „Przerwij”.
- [ ] „Zrobione”, „Pomiń” i „Wstecz” użyte w pauzie doliczają czas pauzy do zegara treningu (`WorkoutEngine.kt:319`).
- [ ] Kafelki biblioteki dekodują ilustracje do `ARGB_8888` bez konfiguracji Coila (`Components.kt:136`): 194 obrazy
      to ok. 100 MB bitmap z 2 MB JPEG-ów; brakuje `RGB_565`, limitu pamięci podręcznej i `crossfade`.
- [ ] „Dziś” czyta całą bibliotekę dwa razy przy każdej emisji i dekoduje bloki wszystkich treningów (`TodayViewModel.kt:125`).
- [ ] Przyciski „Łatwiej/Trudniej” ucinają nazwę wariantu już przy domyślnej czcionce (`ExerciseDetailScreen.kt:145`).
- [ ] Dostępność jednym przejściem po `ui/common`: `FilterPill`, `ChoiceCard`, `DifficultySelector` i zakładki nie mówią
      czytnikowi, że są zaznaczone; wiersz z przełącznikiem to dwa przystanki; `ViewToggleCell` ma 46 × 34 dp;
      wykresy nie mają węzła semantycznego; kolejności w bloku nie da się zmienić bez gestu przeciągania.
- [ ] Skala czcionki 2,0: przyciski playera urywają się bez wielokropka (`PlayerScreen.kt:489`), podpisy dni w kalendarzu
      są przycięte w sztywnym pudełku (`Charts.kt:175`).
- [ ] Historia przelicza wszystko przy każdym tapnięciu chipa, na całej tabeli `set_logs` bez `WHERE` i bez indeksu
      na `completedAt` (`HistoryViewModel.kt:55`, `Daos.kt:122`, `SessionStats.kt:95`).
- [ ] Drobne porządki: seed parsuje 150 kB JSON-a przed sprawdzeniem wersji (`SeedImporter.kt:72`), `Converters` szuka
      enumów liniowo, `PlayerSnapshot` przechodzi listę kroków cztery razy na sekundę, `ReminderReceiver` czyta sesje dwa razy.
- [ ] `SessionRepository.deleteSession` nie jest transakcją (`ProgramRepository` ma `db.withTransaction` od D-42);
      `toggleEquipment` czyta i zapisuje w dwóch krokach (lost update przy szybkim klikaniu).
- [ ] DataStore bez `ReplaceFileCorruptionHandler`; wejścia do playera bez `launchSingleTop`; licznik „w tym tygodniu”
      liczy granicę tygodnia raz, przy tworzeniu ViewModelu; alarm przypomnienia nie wraca po aktualizacji w miejscu
      ani po zmianie strefy czasowej; powiadomienie „Czas na trening” nie znika po odbytym treningu.
- [ ] Dwa opisy obiecują więcej, niż kod robi: „raz dziennie” przy sprawdzaniu wydań (dzieje się przy wejściu w Ustawienia)
      i odwracalnie wyglądający przełącznik „Nie proponuj w planach” (podmian w bieżącym cyklu nie da się cofnąć).

### Decyzje produktowe

- [ ] Kształt treningu: `fitToTime` przy remisie wybiera najmniej rund i najwięcej ćwiczeń (9 × 2 rundy zamiast 5 × 3
      z dłuższymi przerwami). Do oceny w realnym treningu, czy to dobry domyślny kształt.
- [ ] Wear OS: opcja z M6, nigdy nie zaczęta.
- [ ] Licencja repozytorium: żywa tylko warunkowo, bo D-33 trzyma kod prywatnym, a publicznie jedzie sam plik APK.

### Weryfikacja na emulatorze

- [ ] Gotowy program: trzy sesje pod rząd ze zmianą etapu na karcie „Dziś”, klawisz głośności na kroku bez limitu.
- [ ] Biblioteka w motywie „Papier i Ogień” (D-39): kafelki i panel filtrów sprawdzone tylko w ciemnym.
- [ ] Motywy (D-39): „Papier i Ogień” i „Stal i Bursztyn” na wszystkich ekranach głównych, pierwsza klatka po starcie
      przy motywie niezgodnym z trybem systemu (tło okna bierze się wtedy jeszcze z `res/values(-night)/colors.xml`).
- [ ] EMOM po zmianie z D-43: runda ma trwać minutę niezależnie od tempa serii, a seria dłuższa niż okno ma od razu
      przechodzić w kolejną rundę.
- [ ] Zgoda na powiadomienia nadana w trakcie treningu (`adb shell pm grant … POST_NOTIFICATIONS`): powiadomienie ma
      wrócić w ciągu dwóch sekund, bez czekania na zmianę kroku.
- [ ] Poziom w Historii (D-43): trening przerwany po zmianie poziomu w trakcie i sesja gotowego programu po zmianie
      poziomu w Ustawieniach.
- [ ] Pierwsze uruchomienie po aktualizacji z danymi z 0.10.0 (seed v6 → v7, D-43): wbudowane treningi bez historii
      i resztki po starych „Usuń plan” znikają, a treningi z historią i treningi istniejących planów zostają.
- [ ] Telefon, opcjonalnie (nie bramka wydania): sygnał rytmu przy cudzej muzyce, wibracja jako rytm przy wyłączonym
      dźwięku i zatrzymanie oddechu przy zgaszonym ekranie (D-37); biblioteka (D-38) i oba nowe motywy (D-39)
      na prawdziwym ekranie; opinie trenera i korekta celu (D-35); dwie poprawki z pierwszego testu, które nie wróciły
      na telefon (wibracja z `USAGE_ALARM` przy wyłączonym sprzężeniu dotykowym, kanał `player_v2` na ekranie blokady);
      trening dłuższy niż kilka minut przy zgaszonym ekranie; ikona aplikacji na launcherze; nowy cykl na najniższych
      ustawieniach (patrz „Kształt treningu”).

## Zapis obserwacji i decyzji

Nie zadania, tylko rzeczy, o których warto pamiętać.

- **Zgubiona seria z 2026-09-06**: w jednym przebiegu na emulatorze trening zapisał serie deski 1 i 3, a serii 2
  zabrakło w logu. Powtórka przeszła czysto, kod nie ma ścieżki gubiącej krok pracy poza `Skip`/`Back`/`Replan`,
  a niezmiennik jest przypięty testem w `WorkoutEngineTest`. Jeśli powtórzy się na telefonie: logować każde
  `EngineEvent` z timestampem i porównać z `set_logs`.
- **Role `*Fixed*` w motywie** zostają na wartościach domyślnych Material 3: żaden komponent w tej aplikacji ich nie czyta.
- **Dialog zgodności 16 KB** przy starcie na emulatorze x86_64 (DataStore, `androidx.graphics.path`) nie jest po naszej
  stronie; skrypty przebiegu go zamykają.
- **Agresywne oszczędzanie baterii**: Pixel go nie ma, więc tej pozycji nie da się zamknąć bez innego telefonu.
- **Migracja Room na długiej, prawdziwej historii**: patrz „Plany do udostępniania”, v4 to właśnie ta zmiana schematu.
- **Bieg Actions pada bez logów**: powód odmowy stoi w `GET /repos/{owner}/{repo}/check-runs/{job_id}/annotations`,
  nie w logach biegu.
- **Obalone przez sceptyka w audycie 2026-09-19** (nie ruszamy): nieodczytywalny checkpoint playera, pętla edytora
  po duplikacji wbudowanego treningu, liczenie frazy szukania per ćwiczenie, dwa formaty czasu treningu i reguła
  `-keepnames` rzekomo gubiąca `Goal` w R8.
- **Dowody przebiegów** mają lądować w `docs/screenshots`, nie w gitignorowanym `build/`. Kilka odsyłaczy z dawnych
  wersji tego planu wskazywało na katalogi, których już nie ma, i na `przeglad-zdjec-wynik.json`, który przepadł razem
  z `build/`.
- **Cykl rozliczamy kolejką, nie kalendarzem** (D-21), a „Dziś” pokazuje jedną pozycję (D-26). To dwa najczęściej
  podważane założenia; obie decyzje mają swoje wpisy w `docs/DECISIONS.md`.
- **wger wypadł z seedu w v5** (D-28, D-36), więc reguła „omijaj ćwiczenia bez zdjęcia” w `PlanGenerator` i sięganie po
  ostatni kadr w `Exercise.images` są dziś martwe: każde ćwiczenie ma dokładnie jeden obraz.
- **Zdjęte z zakresu**: własne ćwiczenia z formularzem i zdjęciem (2026-09-06, aplikacja ma układać plan, nie być
  edytorem bazy), widget „Dziś” (brak Glance w zestawie wersji z JetNews), „plan tygodnia” (dostarczony jako cykl w M4).

## Archiwum: co dostarczono

| kamień / temat | zakres | wydanie | decyzje |
|---|---|---|---|
| M1 Fundament | model domenowy, silnik playera, planner, Room, seed, ekrany, CI | 0.1.0 | D-04, D-10, D-14 |
| M2 Player w tle | `PlayerController` + foreground service, powiadomienie z akcjami, checkpoint | 0.1.0 | D-16 |
| M3 Dziennik i dane | historia z filtrami, statystyki okresu, delty, kopia zapasowa JSON przez SAF | 0.1.0 | D-18 |
| M4 Kreator i edytor | kreator planu, cykl jako kolejka, seria dni, edytor treningu, progresja między cyklami | 0.1.0 | D-19, D-20, D-21, D-22 |
| M5 Postęp i progresja | wykresy na Canvasie, progresja celów, kadr docelowy ćwiczenia | 0.1.0 | D-23, D-24, D-25 |
| M6 Natywne dodatki | przypomnienie, klawisz głośności jako „Zrobione”, zapis do Health Connect | 0.1.0 | D-29, D-30, D-31 |
| M7 Wydania | `VERSION.md`, dwa workflowy, aktualizacja z aplikacji przez stronę Mysttic | 0.1.0 … 0.3.2 | D-33 |
| Uproszczenie | jeden trening na ekranie, cele z rodzaju ćwiczenia, trafianie w czas sesji | 0.1.0 | D-26, D-27, D-28 |
| Ogień i Krew | pełny system wizualny, krój Sora, przepisane ekrany, kreator w czterech krokach | 0.2.0 | D-17, D-32 |
| Test na telefonie | wibracja jako alarm zamiast haptyki dotyku, kanał `player_v2` widoczny na blokadzie | 0.3.2 | |
| Objętość z poziomu | generator przepisany: rundy z widełek poziomu, czas z liczby ćwiczeń; stepper z przeciąganiem | 0.4.0 | D-34 |
| Trener | opinie w podsumowaniu, korekta celu, wykluczenia z zamiennikiem, Room v3 | 0.5.0 | D-35 |
| Ilustracje ćwiczeń | trzy partie generowane: 45, potem 62 nowe pozycje, na końcu wymiana 77 zdjęć z sieci | 0.5.0 … 0.7.0 | D-36 |
| Gotowe programy | katalog jedenastu protokołów, etapy w kolejce, krok bez limitu, rytm oddechu, seed v6 | 0.8.0 | D-37 |
| Biblioteka | kafelki z ilustracjami, rozwijany panel filtrów, wymiary „i”, wartości „lub” | 0.9.0 | D-38 |
| Poprawki po audycie | kopia przenosi plan i komplet ustawień, przypomnienie przez `EntryPointAccessors`, blokada kasowania treningu z kolejki, kopia systemowa bez WAL, RPE, sprzątanie po „Usuń plan”, „Podbij cele” bez wariantu na brakujący sprzęt, Historia przewijana w całości | 0.9.1 | |
| Motywy | trzy motywy w Ustawieniach, kontrakt w `ThemeContractTest`, zrzuty z Robolectrica | 0.10.0 (tag czeka) | D-39, D-40 |
| CI | jeden bieg na PR, jeden job na workflow | | D-41 |
| Plany do udostępniania | lista planów, edytor planu, plik planu, statystyki i PDF, duże ekrany, Room v4, kopia v3 | niewydane | D-42 |
| Dług techniczny | progresja od celu pokazanego, sprzęt w wariantach, okno EMOM, poziom w historii przy przerwaniu i w programie, powiadomienie po zgodzie, sprzątanie resztek przy imporcie, seed v7 | niewydane | D-43 |
