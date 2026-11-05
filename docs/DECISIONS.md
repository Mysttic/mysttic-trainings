# Decyzje (ADR w skrócie)

Format: kontekst → decyzja → konsekwencje. Dopisuj na końcu, nie edytuj historii.

## D-01 Natywny Android zamiast PWA (2026-09-05)

Kontekst: PWA (Vercel) byłaby szybsza do zbudowania, ale przy zgaszonym ekranie nie da sygnału końca przerwy.
Decyzja: Kotlin + Jetpack Compose, APK instalowany ręcznie. Konsekwencje: foreground service (M2), Android Studio/Gradle, tylko Android.

## D-02 Wersje narzędzi z JetNews

Kontekst: brak możliwości zbudowania projektu w środowisku, w którym powstawał fundament. Decyzja: `libs.versions.toml` kopiuje
zestaw z oficjalnego JetNews (android/compose-samples, wrzesień 2026): AGP 9.3.2, Gradle 9.5.0, Kotlin 2.4.10, KSP 2.3.11,
Compose BOM 2026.08.00, Hilt 2.60.1, Room 2.8.4, Navigation 2.9.8. Konsekwencje: AGP 9 = wbudowany Kotlin, bez pluginu
`org.jetbrains.kotlin.android`; podnosić wersje razem, wzorując się na JetNews/NIA.

## D-03 Jeden moduł Gradle

Kontekst: jedna osoba, jedna apka. Decyzja: `app` z pakietami per warstwa; `domain` bez zależności od Androida (testy JVM bez Robolectric).
Konsekwencje: podział na moduły dopiero, gdy czas builda zacznie boleć.

## D-04 Bloki treningu jako JSON w `workouts.blocksJson`

Kontekst: propozycja zakładała tabele `Block`/`BlockItem`. Decyzja: `List<Block>` serializowane kotlinx.serialization w jednej kolumnie.
Konsekwencje: brak zapytań „w których treningach jest ćwiczenie X” po stronie SQL (robione w Kotlinie), za to edytor (M4) i seed są proste;
nowe pola `Block`/`BlockItem` muszą mieć wartości domyślne.

## D-05 Migracje Room bez trybu destrukcyjnego

Decyzja: `exportSchema = true` do `app/schemas/`, każda zmiana = `version + 1` + `Migration`. Historia treningów to dane użytkownika.
Wyjątek: faza 0.x bez realnej historii — dopuszczalna reinstalacja, odnotowana w commicie. Od 1.0.0 (D-45) wyjątku nie ma.

## D-06 Poziom trudności = globalne ustawienie + profil mnożników + jeden skok wariantu

Kontekst: wymaganie „kilka poziomów, zmiana w każdym momencie”. Decyzja: szablony pisane dla INTERMEDIATE; `DifficultyProfile`
skaluje powtórzenia/sekundy/przerwy; BEGINNER/ADVANCED podmieniają ćwiczenie na `easierId`/`harderId` (jeden skok). Zmiana
w trakcie treningu = `WorkoutPlanner.merge` + `EngineEvent.Replan`. Konsekwencje: jedno miejsce strojenia (`DifficultyProfile`),
brak trzech wersji każdego treningu; per-ćwiczeniowe nadpisania mnożników — dopiero gdy praktyka pokaże potrzebę.

## D-07 Model sprzętu: wymagany zbiór + posiadany zbiór + zamienniki

Decyzja: `Exercise.equipment` (wszystkie elementy wymagane), `ownedEquipment` w ustawieniach, filtry ALL/NO_EQUIPMENT/MY_EQUIPMENT,
`EquipmentMatcher.substitutes` po głównej grupie mięśniowej. Świadomie bez „sprzętu alternatywnego” w jednym ćwiczeniu (hantel LUB
kettlebell): to dwa ćwiczenia w bazie. Konsekwencje: prostota modelu, czasem duplikat nazwy z innym sprzętem.

## D-08 Silnik jako czysta funkcja + efekty

Decyzja: `reduce(state, event) -> (state, effects)`; zdarzenia niosą `now`; efekty (dźwięk, zapis, checkpoint) wykonuje warstwa wyżej.
Konsekwencje: deterministyczne testy bez zegara i Androida; M2 przenosi silnik do serwisu bez zmian w `domain`.

## D-09 M1: silnik w `PlayerViewModel`, M2: foreground service

Kontekst: fundament bez możliwości uruchomienia na urządzeniu. Decyzja: najpierw działający player w procesie (tick 250 ms w ViewModelu),
serwis z powiadomieniem w M2. Konsekwencje: w M1 trening przy zgaszonym ekranie nie gra sygnałów (silnik nadrabia po powrocie
dzięki timestampom); `keepScreenOn` domyślnie włączone.

## D-10 Teksty UI po polsku inline

Decyzja: bez `strings.xml` (poza `app_name`) — aplikacja jednojęzyczna dla jednej osoby. Konsekwencje: szybsze iteracje;
jeśli kiedyś i18n, ekstrakcja do zasobów jest mechaniczna.

## D-11 Seed z free-exercise-db przez skrypt w repo

Decyzja: `tools/build_seed.py` = lista kurowana + tłumaczenia PL + własne ćwiczenia; zdjęcia 480 px JPEG w `assets`. Licencja
Unlicense (domena publiczna). Konsekwencje: ~3 MB assetów; wątpliwości co do pochodzenia części zdjęć w upstream są bez znaczenia
dla użytku własnego — przy ewentualnej publikacji zdjęcia do podmiany.

## D-12 Dystrybucja: prywatne repo, sideload, Obtainium

Decyzja: GitHub Actions buduje podpisany APK na tag `v*` do Releases; telefon aktualizuje przez Obtainium (repo prywatne → token
w Obtainium). Keystore poza repo, kopia w menedżerze haseł. Konsekwencje: brak Play, brak recenzji typów foreground service.

## D-13 Ikony tylko z `material-icons-core`

Decyzja: bez `material-icons-extended` (kilkadziesiąt MB w buildzie, artefakt zamrożony). Ekrany używają Home, List, DateRange,
Settings, PlayArrow, ArrowBack. Konsekwencje: brakujące ikony rysować jako `VectorDrawable` w `res/drawable`.

## D-14 Środowisko builda: JDK z Android Studio, weryfikacja M1 na emulatorze (2026-09-05)

Kontekst: na maszynie deweloperskiej nie ma `java` na PATH ani osobnego JDK 17; jest JBR 21 w Android Studio. Przy pierwszym
uruchomieniu telefon nie był podłączony, był AVD Pixel_9_Pro (API 37, x86_64, obraz z 16 KB page size).
Decyzja: lokalnie `JAVA_HOME` = `C:\Program Files\Android\Android Studio\jbr`, CI zostaje na Temurin 17 (AGP 9.3.2 działa z oboma).
Przebieg M1 zweryfikowany na emulatorze; poprawki layoutu (chipy, przyciski playera, insety) zrobione po zobaczeniu ekranów.
Konsekwencje: punkt „instalacja na telefonie” w PLAN.md ma status [~] do czasu testu na urządzeniu. Dialog „Android App Compatibility”
(16 KB) dotyczy x86_64 bibliotek DataStore 1.2.0 i androidx.graphics.path z zależności, nie naszego kodu; w 0.x ignorowany,
na arm64 do sprawdzenia przy podnoszeniu wersji AndroidX.

## D-15 `hiltViewModel` z pakietu `androidx.hilt.lifecycle.viewmodel.compose`

Kontekst: `androidx.hilt:hilt-navigation-compose:1.4.0` oznacza `hiltViewModel()` jako deprecated; implementacja przeniesiona do
`androidx.hilt:hilt-lifecycle-viewmodel-compose`. Decyzja: jawna zależność na `hilt-lifecycle-viewmodel-compose` (ta sama wersja
`hiltExt`) i import z nowego pakietu; `hilt-navigation-compose` zostaje. Konsekwencje: build bez deprecacji; przy podnoszeniu `hiltExt`
obie biblioteki razem.

## D-31 Health Connect: zapis jednostronny, opcjonalny, po ukończonym treningu (2026-09-06)

Kontekst: M6 przewidywał `ExerciseSessionRecord` po każdej sesji. Decyzja: `androidx.health.connect:connect-client:1.1.0`
(ostatnia stabilna; 1.2.0 jest w alfie, biblioteki nie ma w zestawie JetNews), zapis **jednostronny** — nic stamtąd nie
czytamy, historia w Room zostaje źródłem prawdy. Funkcja jest wyłączona domyślnie: przełącznik w Ustawieniach pojawia się
tylko wtedy, gdy `HealthConnectClient.getSdkStatus` mówi, że dostawca jest, a zgodę na zapis bierze kontrakt
`PermissionController`. Zapis idzie po ukończeniu sesji z `PlayerController.finish`, best effort w `runCatching` —
dziennik zdrowia nie ma prawa wywrócić podsumowania. Co trafia do zapisu, decyduje `domain/health/HealthExport`
(czysty Kotlin, 3 testy): tylko `DONE` dłuższe niż minuta, obwód jako trening interwałowy, serie proste jako kalistenika,
pusta nazwa zastąpiona „Trening w domu”. Konsekwencje: manifest deklaruje `android.permission.health.WRITE_EXERCISE`
i filtr `VIEW_PERMISSION_USAGE` (Health Connect linkuje do niego z okna zgody); rekordy mają `Metadata.activelyRecorded`,
bo sesję mierzy stoper aplikacji. Sprawdzone na emulatorze: wpis „High intensity interval training · Dół A” w Health Connect.

## D-30 Przycisk głośności potwierdza serię tylko wtedy, gdy trening na to czeka (2026-09-06)

Kontekst: po przejściu planów na powtórzenia (D-27) każda seria kończy się tapnięciem „Zrobione”, a telefon w trakcie
ćwiczenia leży na podłodze. Decyzja: `KEYCODE_VOLUME_UP/DOWN` w `MainActivity` woła `PlayerController.doneFromVolumeKey()`,
które zużywa klawisz wyłącznie wtedy, gdy ekran playera jest na wierzchu, a silnik stoi na serii na powtórzenia
(`EngineState.awaitingRepsDone`, czysty Kotlin, 3 testy). W odliczaniu, przerwie, pauzie i poza playerem klawisz wraca
do systemu, żeby dało się ustawić głośność sygnałów. Przełącznik w Ustawieniach (domyślnie włączony) wyłącza skrót.
Konsekwencje: `MainActivity` trzyma kopię ustawienia (obsługa klawisza nie może czekać na DataStore), a `PlayerScreen`
zgłasza kontrolerowi swoją widoczność przez `DisposableEffect`. Pierwsza wersja warunku była błędna
(`(step as? Work)?.durationSec == null` jest prawdą także dla przerwy) — złapał to test, nie emulator.

## D-29 Przypomnienie: jednorazowy alarm dokładny, decyzja w domenie (2026-09-06)

Kontekst: M6 zakładał przypomnienia na `AlarmManager` albo `WorkManager`. Decyzja: `AlarmManager` bez nowej zależności,
alarm **jednorazowy** przestawiany po każdym odpaleniu (`setRepeating` nie odpala w trybie czuwania, a `setAndAllowWhileIdle`
dostaje godzinne okno — „przypomnij o 18:00” z odpaleniem o 18:47 mija się z celem). Prosimy o alarm dokładny
(`USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM` do API 32) i schodzimy na wariant niedokładny, gdy uprawnienia zabraknie.
Co i kiedy pokazać, liczy `domain/reminder/ReminderSchedule` (czysty Kotlin, 7 testów): najbliższy termin, warunek
„dziś nie było treningu i kolejka ma co podać”, treść. `ReminderReceiver` (`@AndroidEntryPoint`, `goAsync`) odbiera alarm
i `BOOT_COMPLETED`, po każdym odpaleniu ustawia następny; `exported="false"` wystarcza, bo broadcast systemowy i tak dochodzi
(sprawdzone rebootem emulatora — alarm wrócił bez uruchamiania aplikacji). Konsekwencje: przypomnienie nie zna dni planu,
tylko kolejkę (D-21), więc po treningu tego samego dnia milczy; osobny kanał powiadomień „reminder”, bo to jedyne, które
ma prawo zadzwonić.

## D-28 Drugie źródło zdjęć: wger na CC-BY-SA (2026-09-06)

Kontekst: dwanaście ćwiczeń w seedzie nie miało zdjęcia, bo free-exercise-db (dataset z bodybuilding.com) nie zna domowej
klasyki: burpee, bird doga, hollow holdu, pajacyków, pompek na kolanach, wall sita. Cztery z nich trafiły do wygenerowanego
planu i w playerze świeciły szarym „brak zdjęcia”. Decyzja: dokładamy drugie źródło, [wger.de](https://wger.de) (CC-BY-SA 4.0),
z adresami zdjęć wpisanymi wprost w `CUSTOM` w `tools/build_seed.py` — build nie zależy od ich API. Osiem ćwiczeń dostało
ilustracje (styl rysunkowy, inny niż studyjne zdjęcia datasetu — poprawność ruchu przed spójnością stylu), atrybucja jest
w `docs/SEED.md` i na ekranie Ustawień. Dla czterech, których nie ma w żadnym źródle (`burpees`, `wall-sit`, `knee-pushups`,
`kettlebell-halo`), `PlanGenerator` rotuje warianty tylko po ćwiczeniach ze zdjęciem i sięga po pozostałe dopiero wtedy, gdy
roli nie ma czym wypełnić. Konsekwencje: przeskalowane pliki z wger są CC-BY-SA 4.0, więc przy publikacji aplikacji trzeba
zostawić atrybucję; ćwiczenia bez zdjęć zostają w bibliotece i w edytorze, po prostu nie wchodzą do planu bez potrzeby.

Wycofane w seedzie v5 (2026-09-13): wszystkie zdjęcia z sieci ustąpiły ilustracjom (D-36), więc żaden plik z wger nie jedzie
już w aplikacji, a atrybucja zeszła z ekranu Ustawień. Adresy w `CUSTOM` zostają jako zapas.

## D-27 Cele w powtórzeniach wszędzie, gdzie pasują (2026-09-06)

Kontekst: generator dawał powtórzenia tylko przy celu „siła”, więc plan na redukcję składał się z samych odliczanek („40 sekund
przysiadów”). Użytkownik zgłosił, że w ogóle nie widzi ćwiczeń na powtórzenia. Decyzja: o rodzaju celu decyduje ćwiczenie, nie cel
planu — `ExerciseKind.REPS` dostaje powtórzenia (także w obwodzie), a odliczanie czasu zostaje dla izometrii i cardio (deska,
wspinaczka, skakanka). Konsekwencje: w obwodzie po każdej pozycji na powtórzenia trzeba tapnąć „Zrobione”, zamiast czekać na zero;
estymata czasu liczy powtórzenie po 3 s, więc plan trafia w zadeklarowane minuty, ale realny czas zależy od tempa ćwiczącego.

## D-26 Jeden trening na ekranie, wybór tylko w kreatorze (2026-09-06)

Kontekst: „Dziś” pokazywało kartę planu, chipy poziomu trudności i listę wszystkich treningów (wbudowane gotowce, kopie, wszystkie
treningi z cyklu). Użytkownik: „wystarczy, że użytkownik będzie widział tylko jeden swój trening, wybór treningu ma być tylko na
samym początku”. Decyzja: „Dziś” to jedna karta z następną pozycją z kolejki (nazwa, liczba ćwiczeń, czas, Start), plus skróty
„Co jest w treningu” i „Zobacz plan”; bez planu jest jedno wezwanie „Ułóż plan”. Znikają lista treningów, przycisk „Nowy” i chipy
poziomu (poziom zostaje w Ustawieniach i w playerze, gdzie zmienia się w trakcie serii). Konsekwencje: wbudowane treningi zostają
w bazie jako dane, ale nie mają wejścia w UI; `TodayUiState` schudło do trzech pól, a `PlanCard` niesie czas i liczbę ćwiczeń,
żeby karta mogła zastąpić listę. Ekran szczegółów treningu (z edytorem i propozycją progresji) jest dostępny z tej jednej karty.

## D-25 Pojedyncze zdjęcie ćwiczenia bierzemy z końca listy (2026-09-06)

Kontekst: seed z free-exercise-db daje dwie klatki w kolejności „pozycja startowa, pozycja docelowa”. Player i miniatury pokazywały
klatkę pierwszą, więc przy izometrii zdjęcie kłóciło się z opisem: „Deska (plank)” miała fotografię klęku, bo tak wygląda wejście
do pozycji. Decyzja: tam, gdzie jest miejsce tylko na jedno zdjęcie (player, miniatura na liście), bierzemy `images.lastOrNull()`;
ekran ćwiczenia dalej pokazuje obie klatki obok siebie. Konwencja opisana w KDoc `Exercise.images`. Konsekwencje: przy ćwiczeniach
na powtórzenia widać pozycję końcową (dół pompki, dół przysiadu), co jest równie czytelne jak start; gdyby kiedyś seed dostał więcej
niż dwie klatki, ta reguła wymaga rewizji.

## D-24 Progresja celów liczona na wartościach z ekranu, nie z szablonu (2026-09-06)

Kontekst: `domain/plan/SetProgression` podbija cel ćwiczenia, gdy w poprzednim wykonaniu wszystkie serie wyszły na cel (+1 powtórzenie
albo +5 s, po dobiciu limitu 20 powt. / 90 s propozycja `harderId`). Szablon trzyma wartości dla INTERMEDIATE, a log serii zapisuje cel
już przeskalowany poziomem, więc porównanie „log kontra szablon” działałoby wyłącznie na średniozaawansowanym, a u początkującego
`+5 s` potrafi zniknąć w zaokrągleniu do pięciu sekund (40 i 45 s to po skalowaniu ta sama trzydziestka). Decyzja: reguła liczy się
na wartościach widocznych na ekranie (`DifficultyProfile`), a `rawTo` przelicza wynik z powrotem na szablon, wybierając najmniejszą
wartość, która realnie podnosi to, co użytkownik zobaczy (sekundy zostają wielokrotnościami piątki). Propozycja wymaga kompletu serii
i zgodności logów z szablonem, więc nie powtarza się po zastosowaniu ani nie łapie się na trening zrobiony na innym poziomie.
Konsekwencje: propozycja pokazuje się w szczegółach treningu i skrótem na karcie planu („Podbij cele”), zawsze z jednym tapem
„Zastosuj”; treningów wbudowanych nie ruszamy (D-19), bo nadpisze je seed.

## D-23 Wykresy na Compose Canvas zamiast biblioteki (2026-09-06)

Kontekst: plan zakładał Vico do trzech wykresów (rekord ćwiczenia w czasie, wolumen tygodniowy, kalendarz aktywności).
Decyzja: rysujemy wprost na `Canvas` w `ui/common/Charts.kt`. Trzy proste obrazki bez interakcji, zoomu i osi nie są warte
zależności, którą trzeba trzymać w zgodzie z Compose BOM przy każdym podniesieniu wersji (D-02), a przy okazji dostajemy
kolory prosto z `MaterialTheme.colorScheme` bez mapowania na API biblioteki.
Dane liczy `domain/stats/ProgressStats` (czysty Kotlin, testowany), więc w warstwie UI zostaje samo rysowanie:
słupki wolumenu z wyróżnionym bieżącym tygodniem, linia najlepszej serii i siatka kalendarza w układzie tydzień na kolumnę.
Wolumen łączy powtórzenia i sekundy pracy w jednej mierze (sekundy dzielone przez 3), żeby trening siłowy i obwodowy
dało się porównać jednym słupkiem — to przybliżenie, nie fizjologia.
Konsekwencje: przy wykresach z interakcją (zoom, tooltipy, wiele serii) decyzję trzeba będzie przemyśleć ponownie.

## D-22 Progresja między cyklami z frekwencji, nie z rekordów (2026-09-06)

Kontekst: bez progresji trzeci cykl byłby tak samo trudny jak pierwszy. Aplikacja nie wie jednak, ile powtórzeń użytkownik
„mógł” zrobić: cele czasowe zapisują się automatycznie, a pominięte serie nie tworzą wpisu.
Decyzja: progresja opiera się na tym, co da się zmierzyć uczciwie — frekwencji (ile z zaplanowanych treningów zrobiono)
i kompletności (ile serii zapisano w treningach, które zrobiono). Próg 80% obu daje „Trudniej”, poniżej 50% frekwencji
„Łatwiej”, pomiędzy „Tak samo”. Użytkownik może propozycję nadpisać w kreatorze.
Efektem jest `TrainingProfile.targetScale` — mnożnik celów kumulujący się między cyklami, przycięty do 0,7–1,6.
Mianownikiem kompletności są tylko treningi ukończone: pominięta sesja już obniża frekwencję, liczenie jej serii drugi raz
byłoby podwójną karą. Mnożnik nie wydłuża treningu, bo `PlanGenerator.tuneToTime` trzyma zadeklarowane minuty —
podbicie celów oznacza gęstszą pracę, ewentualnie o rundę mniej.
Konsekwencje: progresja jest odporna na to, że ktoś przeklikał trening (zero zapisanych serii nie podbije celów),
ale nie zauważy, że ktoś zrobił 15 powtórzeń zamiast 10 — do tego trzeba by logować rekordy per ćwiczenie (M5).

## D-21 Plan rozliczany jako kolejka, seria dni jako motywacja (2026-09-06)

Kontekst: pierwsza wersja planu przypisywała dni do dat (dzień 1 = data wygenerowania). Pominięcie treningu zostawiało dziurę,
której nie dało się nadrobić: „Dziś” pokazywało już kolejną pozycję, a zaległy dzień zostawał nieodhaczony na zawsze.
Decyzja: plan to kolejka. `PlanProgress.resolve` dopasowuje ukończone sesje do dni po identyfikatorze treningu i po kolei
(N sesji „Góra A” odhacza N pierwszych wystąpień „Góry A”), a „następny” to pierwszy nieodhaczony trening. Data sesji nie ma
znaczenia, więc wczorajszy trening można zrobić dzisiaj i nic nie przepada. Postęp pokazujemy jako „7 z 18 treningów”,
nie „dzień 12 z 30”; dni odpoczynku zostają w podglądzie jako rytm, ale nie liczą się do postępu.
Osobno liczymy serię dni (`SessionStats.streak`): ile dni kalendarzowych z rzędu poszło zgodnie z planem. Dzień liczy się,
gdy był trening albo gdy plan przewidywał odpoczynek — zaplanowana przerwa serii nie kasuje (decyzja użytkownika, 2026-09-06),
bo aplikacja sama każe odpoczywać i karanie za to byłoby niespójne. Przerywa dopiero dzień, w którym plan czekał na trening,
a treningu nie było; brak treningu dzisiaj jeszcze nie kasuje, bo dzień trwa. Rytm tygodnia z `PlanGenerator` zostaje więc
w użyciu (`Program.isRestDate`) mimo rozliczania kolejką: kolejka mówi CO robić, rytm KIEDY plan tego oczekiwał.
Bez aktywnego planu predykat zwraca false i wychodzi zwykła seria dni z treningiem. Rekord liczony tą samą regułą.
Konsekwencje: cykl „30-dniowy” to w praktyce 18 treningów do zrobienia w swoim tempie, a nie zobowiązanie kalendarzowe.

## D-20 Kreator planu: cykl generowany z profilu, kolejny cykl z poprawionych danych (2026-09-06)

Kontekst: aplikacja miała być prosta w obsłudze. Wybieranie treningu z listy i budowanie własnych w edytorze to za dużo pracy
dla użytkownika, który chce po prostu ćwiczyć. Decyzja (2026-09-06): sercem aplikacji jest kreator — z profilu (wzrost, waga, cel,
poziom, dni w tygodniu, długość treningu, długość cyklu, sprzęt) powstaje gotowy cykl treningowy z rozkładem dni.
`domain/plan/PlanGenerator` jest czysty i deterministyczny: te same dane dają ten sam plan, więc da się go przetestować.
Reguły doboru: sprzęt to twardy filtr, BMI ≥ 30 wyklucza plyometrię, rozciąganie jest ćwiczeniem głównym tylko przy celu
„mobilność”, w jednym treningu unikamy dwóch ćwiczeń na tę samą partię, a liczba ćwiczeń i rund wynika z deklarowanych minut
(korygowana estymatą `WorkoutPlanner`). Cykl ma dowolną długość z listy (14–60 dni); po jego końcu „Wygeneruj kolejny cykl”
otwiera formularz wypełniony poprzednimi wartościami do poprawienia.
Wygenerowane treningi to zwykłe treningi użytkownika, więc działa na nich edytor (D-19), player i kopia zapasowa (D-18).
Postęp cyklu liczymy z historii sesji (ukończona sesja tego treningu tego dnia = dzień odhaczony), więc player nie wie nic o planach.
Konsekwencje: Room v2 (`programs`, `program_days`) z migracją; własne ćwiczenia ze zdjęciem wypadły z zakresu M4.

## D-19 Edytor: wbudowane treningi tylko do kopiowania, kolejność strzałkami (2026-09-05)

Kontekst: seed nadpisuje wbudowane treningi przy każdym podbiciu `SEED_VERSION`, więc edycja w miejscu i tak by zniknęła.
Decyzja: `WorkoutEditorRoute(workoutId = null)` tworzy nowy trening, z `workoutId` edytuje istniejący, ale tylko własny;
wbudowany otwiera się w edytorze dopiero po „Duplikuj do edycji" (`WorkoutRepository.duplicate`, nazwa z sufiksem „(kopia)”).
Cała logika edycji siedzi w `domain/editor/WorkoutEditing.kt` jako czyste funkcje na `List<Block>`, więc kolejność, zakresy
i walidacja są testowane bez Androida, a ViewModel tylko je składa.
Kolejność ćwiczeń w bloku zmienia się przeciąganiem za uchwyt (2026-09-06): elementy bloku są w zwykłej `Column`,
więc wystarczyło `detectDragGestures` na uchwycie i zamiana miejscami „na żywo” po przekroczeniu wysokości wiersza —
bez liczenia pozycji docelowej przy upuszczeniu. Bloki zostają na strzałkach, bo są elementami `LazyColumn`,
gdzie przeciąganie wymagałoby własnej obsługi przewijania i pomiaru pozycji.
Konsekwencje: nie da się popsuć wbudowanego treningu, a kopia jest zwykłym treningiem użytkownika (trafia też do kopii zapasowej, D-18).

## D-18 Kopia zapasowa: jeden plik JSON przez SAF, import scalający (2026-09-05)

Kontekst: aplikacja nie ma konta ani sieci (D-01), więc jedyną drogą przeniesienia historii na nowy telefon jest plik.
Decyzja: `data/backup/` z własnymi DTO (`BackupFile`), zapis i odczyt przez Storage Access Framework, bez uprawnień do dysku.
Do pliku trafia historia, ćwiczenia i treningi użytkownika oraz ustawienia; wbudowane ćwiczenia i treningi pomijamy, bo odtwarza je seed.
`seedVersion` zostaje poza plikiem — to lokalna sprawa instalacji. Import scala zamiast nadpisywać: sesja o znanym id jest pomijana
razem z jej seriami (klucz `SetLogEntity.id` nadaje baza, więc nic się nie zderza), ćwiczenia i treningi idą przez `upsert`,
ustawienia są odtwarzane. Konsekwencje: import tego samego pliku dwa razy nie duplikuje historii; nie da się przez import
zmienić już zapisanej sesji (do tego jest usuwanie w Historii). DTO są osobne od encji Room i modeli domenowych, więc zmiana bazy
nie psuje starych plików; nowe pola muszą mieć wartości domyślne.

Uzupełnienie (2026-09-19, plik w wersji 2): do kopii wchodzą też cykle treningowe (`programs`, `program_days`)
i komplet ustawień. Bez tego kopia wiozła treningi wygenerowane dla cyklu, ale nie sam cykl — po imporcie na nowym
telefonie „Dziś” było puste, a treningi zostawały bez wejścia (D-26), i ginęła połowa ustawień dodanych po M6
(przypomnienie, klawisz głośności, Health Connect, sprawdzanie wydań, widok biblioteki). Cykl o znanym id jest
pomijany jak sesja; aktywny cykl z pliku staje się aktywnym cyklem aplikacji, bo kopię wczytuje się po to, żeby
wrócić do swojego planu. Poza plikiem zostają `seedVersion` i `lastUpdateCheck` — to lokalna sprawa instalacji.
Po odtworzeniu ustawień alarm przypomnienia jest przestawiany (`ReminderScheduler.sync`), inaczej włączone
przypomnienie nie miałoby alarmu.

## D-17 Pełny schemat kolorów Material 3, kontrast sprawdzany skryptem (2026-09-05)

Kontekst: `Theme.kt` definiował 13 ról z 35. Reszta spadała na paletę bazową M3, więc zaznaczone chipy, karty, dialogi i pasek
nawigacji były fioletowe mimo markowego teal. Najbardziej raziło to w ciemnym motywie.
Decyzja: wszystkie role podane jawnie, w obu motywach, wokół marki (teal #0E7C7B, bursztyn #B7791F). Paleta powstała jako trzy
niezależne propozycje ocenione przez dwóch sędziów liczących kontrast; wygrała ta, która trzyma markowe hexy i nie łamie progów.
Do niej przeszczepiono ciemne warianty poprawiające czytelność planów (surfaceVariant, surfaceContainerHighest, secondaryContainer)
oraz spójną zieloną rodzinę tertiary, zarezerwowaną na przyszły stan „cel osiągnięty”. Progi: 4,5:1 dla tekstu, 3:1 dla elementów
interfejsu, plus miękka separacja warstw (karta i pasek wobec tła, pigułka zakładki).
Konsekwencje: `python tools/check_contrast.py` czyta `Theme.kt` i zwraca kod 1 przy złamanym progu, więc zmiana koloru jest
weryfikowalna liczbą, nie oceniana na oko. Dwie pułapki odnotowane przy okazji: w Material 3 1.4 `OutlinedButton` ma treść
`onSurfaceVariant` (nie `primary`), a `contentColorFor` dopasowuje po wartości koloru, więc `background` i `surface` nie mogą
mieć tego samego hexa. Dynamiczny kolor pozostaje wyłączony.

## D-16 M2: silnik w `PlayerController`, serwis tylko tyka i pokazuje powiadomienie (2026-09-05)

Kontekst: D-09 zapowiadał przeniesienie silnika z `PlayerViewModel` do foreground service. Decyzja: stan silnika trzyma singleton
`service/PlayerController` (`StateFlow<EngineState?>`, efekty przetwarzane sekwencyjnie, `start()` pod mutexem), a `WorkoutService`
(FGS typu `mediaPlayback`, bo gra sygnały i TTS) tylko tyka co 250 ms, trzyma `PARTIAL_WAKE_LOCK` w trakcie pracy i buduje
powiadomienie z `NotificationContent` (czysty Kotlin, testowany) z akcjami przez `PendingIntent.getForegroundService`. UI (`PlayerViewModel`,
karta na „Dziś”) tylko obserwuje kontroler. Wstecz z playera nie przerywa treningu, przerwanie tylko przyciskiem z dialogiem.
`START_STICKY` + `restoreLatest()` z checkpointu; faza przeterminowana o >30 s (`RESTORE_OVERDUE_PAUSE_MS`) wraca w pauzie z kartą
„Kontynuować od tego miejsca?”. Sygnały: SoundPool z próbkami z `tools/build_beeps.py` (`res/raw`), audio focus `TRANSIENT_MAY_DUCK`
zwalniany po utterance/beepie. Konsekwencje: jeden proces = jeden trening (start innego zamyka poprzedni jako ABANDONED bez podsumowania);
powiadomienie celowo bez „Przerwij”; `PlayerViewModel` bez logiki, więc M3+ nie dotyka silnika; test na prawdziwym telefonie
z oszczędzaniem baterii nadal przed nami (PLAN M2).

## D-32 Nowy system wizualny „Ogień i Krew” zamiast domyślnego Material 3 (2026-09-07)

Kontekst: aplikacja działała, ale wyglądała jak formularz techniczny — domyślny Material 3, systemowy krój, każda sekcja
w tej samej szarej karcie. Poszedł brief do projektanta (`docs/design-brief.md`) z jednym zastrzeżeniem: decyzje produktowe
(jeden trening na ekranie, kolejka zamiast kalendarza, wybór tylko w kreatorze) są nienaruszalne. Wrócił komplet:
system wizualny, ekrany w obu motywach i gotowe tokeny.

Decyzja: wdrażamy „Ogień i Krew”. Trzy reguły, z których wynika cała reszta:
1. **Krew to podłoże.** Powierzchnie są wypaloną krwią (`#120809` w ciemnym, `#FDF5F1` w jasnym), nie neutralnym grafitem.
   Czerwień jest wszędzie i nigdy nie jest tekstem.
2. **Ogień to akcja.** Wypełnienie kolorem `primary` znaczy „kliknij” albo „zrobione”. Nic dekoracyjnego go nie dostaje,
   a tekst na ogniu jest zawsze ciemny (`onPrimary`), nigdy biały.
3. **Żar to informacja.** `tertiary` niesie nagłówki sekcji, serię dni i podpowiedzi progresji. Nigdy nie trafia na przycisk.

Konsekwencje techniczne: krój Sora (Google Fonts, OFL) jako jeden plik wariantu zmiennego w `res/font/sora.ttf`, wagi z osi
`wght` (wymaga API 26, czyli dokładnie naszego `minSdk`). Trzy własne style liczb playera (208 / 176 / 132 sp, waga 300,
cyfry tabularne) z przyciętą interlinią — policzone pod czytanie z półtora metra, nie zmniejszać. Elewacje wszędzie zerowe:
warstwy rozdziela stopień `surfaceContainer` i linia włosowa 1 dp. Komponenty wielokrotnego użytku wylądowały
w `ui/common/DesignSystem.kt`, zdjęcia ćwiczeń dostały filtr koloru ujednolicający niejednolitą bibliotekę.
Dolna nawigacja jest bez ikon: sama etykieta i wskaźnik 34 x 4 dp.

Dwie zmiany przepływu, które przyszły razem z projektem. Ocena wysiłku (RPE) i notatka przeniosły się z ekranu sesji
w Historii do podsumowania zaraz po treningu — pyta się o to wtedy, gdy człowiek jeszcze pamięta, jak było.
Pytanie „jak poszedł ten cykl” przeniosło się z kreatora na ekran „Dziś · cykl zamknięty”: odpowiedź (za łatwo / w porządku /
za ciężko) leci do kreatora trasą `PlanWizardRoute(step)` jako wybór wstępny, który dalej można zmienić.
Pytamy o odczucie, a nie o polecenie dla generatora, i mapujemy je na `ProgressionStep` dopiero w UI.

Liczby playera dostały autoskalowanie (`TextAutoSize.StepBased`, dół 72 sp): rozmiar z projektu jest górną granicą,
a przy powiększonej czcionce systemowej albo dwóch cyfrach tekst schodzi w dół zamiast dać się przyciąć.
Sprawdzone na emulatorze przy skali czcionki 1,0, 1,3 i 2,0.

`dynamicColor` zostaje wyłączony — paleta jest tożsamością produktu, nie tapetą użytkownika (to samo ustalenie co w D-17).
Zmieniła się też lista par w `tools/check_contrast.py`: zniknęła pigułka aktywnej zakładki (nie ma już pigułki), doszły pary
z żarem. 35 ról w obu motywach przechodzi progi. Handoff nie objął edytora treningu — ten ekran dostaje tylko nowy motyw.

## D-33 Wydania na stronie Mysttic, aktualizacja z publicznego repozytorium (2026-09-07)

Kontekst: aktualizator w aplikacji pytał `Mysttic/mysttic-trainings`, czyli repozytorium kodu. Jest
prywatne, więc API odpowiadało 404 każdej zainstalowanej kopii i „Sprawdź teraz” zawsze kończyło się
komunikatem o nieudanym sprawdzeniu. Upublicznienie repozytorium kodu wymagałoby wyboru licencji
i przeglądu historii; Chatt rozwiązał to samo inaczej i działa tak od 0.6.1.

Decyzja: kod zostaje prywatny, a na publiczne `Mysttic/Mysttic` (repozytorium strony
<https://mysttic.github.io/Mysttic/>) jedzie sam plik. Workflow wydania kopiuje APK
zaraz po opublikowaniu wydania u nas, jako `mysttic-trainings-v<numer>`, i kasuje starsze wydania tego
projektu razem z tagami. Na stronie stoi jedna wersja, pełna historia zostaje w repozytorium kodu.
Od 2026-09-21 kopiowanie to końcowe kroki joba `wydanie`, a nie osobny job `strona`: APK leży wtedy
jeszcze na dysku runnera, więc odpada trzeci runner i pobieranie pliku z sieci (oszczędność minut
Actions, umowa o tagu, nazwie pliku i opisie bez zmian).

Umowa, której trzymają się trzy programy naraz (workflow, skrypt strony, aktualizator w aplikacji):
tag z prefiksem projektu `mysttic-trainings-v`, nazwa pliku `mysttic-trainings-<numer>-android.apk`, opis wydania
złożony z numeru i adresu strony. Prefiks jest konieczny, bo w `Mysttic/Mysttic` leżą wydania
wszystkich projektów: bez niego aplikacja wzięłaby wydanie Chatta za swoje.

W aplikacji: pytanie idzie na `GET /repos/Mysttic/Mysttic/releases?per_page=30`, nie na
`releases/latest` (ten odpowiada za całe repozytorium). `ReleaseParser` odrzuca szkice i wydania
kandydujące, filtruje po prefiksie i bierze pierwsze z listy; lista bez naszego wydania znaczy
„nic nowego”, nie błąd. Pobieranie idzie przez plik `.part` i sprawdza rozmiar wobec tego, co
deklaruje wydanie, żeby połówka pliku nie trafiła do instalatora. Wydanie bez pliku dla Androida
i tak niesie numer i adres strony, więc karta w Ustawieniach mówi to wprost i proponuje stronę.

Konsekwencja jednorazowa: kopie zainstalowane wcześniej pytają adres, z którym zostały zbudowane,
więc wydania po zmianie adresu nie zobaczą. 0.3.0 trzeba na nie założyć ręcznie. Stąd kolejność przy
każdej przyszłej zmianie schematu: najpierw wersja, która czyta nowy schemat, potem schemat.

## D-34 Objętość z poziomu, czas z liczby ćwiczeń (2026-09-08)

Kontekst: test na telefonie przy najniższych ustawieniach (początkujący, 15 min, bez sprzętu) dał trening z dwóch ćwiczeń
po sześć–siedem rund (48 pompek, 77 mostków), a dłuższe sesje do ośmiu rund i ponad sto powtórzeń jednego ćwiczenia.
Generator wypełniał zadeklarowany czas rundami i seriami (`MAX_ROUNDS = 8`, `MAX_SETS = 6`), a liczbę ćwiczeń brał
z prostego dzielnika minut. Bez sprzętu rola „pull” nie miała kandydata, więc trening tracił jedną z trzech pozycji,
mnożnik poziomu 0,7 był neutralizowany dodatkowymi rundami, a czas pracy pozycji na czas brał się z celu planu (30–45 s),
nie z ćwiczenia, więc hollow body dostawał 45 s. U zaawansowanego mostek i mostek na jednej nodze zlewały się po podmianie
wariantu w jedno ćwiczenie na 16 rund.

Decyzja: rundy i serie trzymają się widełek poziomu (`PlanGenerator.repeatsRange`: początkujący 2–3, średniozaawansowany
3–4, zaawansowany 3–5; mobilność 2–4), a czas wypełniają kolejne ćwiczenia z rotacji ról (do 16, w blokach po najwyżej 6)
i przerwy w stałych widełkach. Rola bez kandydata jest pomijana, nie zostawia dziury. Czas pracy bierze się
z `Exercise.defaultSeconds` (+10 s kardio przy redukcji, +15 s rozciąganie przy mobilności), strojony o ±10 s. Kandydata
ocenia się po wariancie, który player pokaże na tym poziomie (`WorkoutPlanner.resolveVariant`): dwie pozycje nie mogą się
zlać w jedno ćwiczenie, wariant na sprzęt, którego nie ma, wyklucza kandydata, a początkujący nie dostaje ćwiczeń o dwa
poziomy za trudnych. Przy celu „mobilność” rozciąganie wygrywa z dopasowaniem poziomu. Przegląd (rundy × liczba ćwiczeń
× przerwy) jest wyczerpujący i w stałej kolejności, więc generator zostaje deterministyczny; przy równym trafieniu
wygrywa mniejsza objętość.

Konsekwencje: długość sesji ma górną granicę z poziomu (`TrainingProfile.minutesRange`: 45/60/75 min), bo dłuższej nie
da się wypełnić bez łamania widełek; kreator nie pozwala jej przekroczyć, a zmiana poziomu na niższy przycina wybrane
minuty. Gdy biblioteka nie ma dość ćwiczeń na dany sprzęt, trening wychodzi krótszy i mówi to w notatce, zamiast dokładać
rund. Treningi dłuższe niż sześć ćwiczeń mają kilka bloków z minutą przerwy między nimi. Sprawdzone na całej siatce
ustawień z prawdziwym seedem (poziom × cel × minuty × trzy zestawy sprzętu, 3 i 4 dni w tygodniu): zero chybień czasu,
najwięcej powtórzeń jednego ćwiczenia w treningu to 130 (zaawansowany, 5 × 26 skrętów tułowia). Biblioteka bez sprzętu
nie ma żadnego ćwiczenia na plecy (pull), więc „Góra” bez drążka to warianty pompek i tułów — to ograniczenie seedu,
nie generatora.

## D-35 Trener: opinia o ćwiczeniu przesuwa cel w kolejnych seriach (2026-09-08)

Kontekst: użytkownik chce po treningu powiedzieć o każdym ćwiczeniu „mogę więcej”, „potrzebuję mniej” albo
„w ogóle nie chcę”, a aplikacja ma to uwzględnić w kolejnych treningach: 6 powtórzeń w tym tygodniu ma zejść
do 4, a 10 za parę tygodni do 8. Bez AI, bez sieci.

Decyzja: trener to reguły w `domain/coach` i pamięć per ćwiczenie (`ExerciseTuning`: korekta powtórzeń, korekta
sekund, wykluczenie), trzymana na stałe w tabeli `exercise_tuning` (Room v3), dopóki użytkownik nie powie inaczej.
Opinie zbiera podsumowanie treningu, zapis idzie przy „Zakończ”, domyślna opcja nic nie zmienia. „Więcej” i „Mniej”
to stały krok (2 powtórzenia, 5 s) sumowany z kolejnych opinii, w widełkach ±20 powtórzeń i ±60 s. Korekta jest
nakładana w `WorkoutPlanner` po skalowaniu poziomem, więc użytkownik widzi dokładnie 6 → 4 i 10 → 8 bez błędów
zaokrąglenia, a kluczem jest ćwiczenie pokazane na ekranie (wariant poziomu, nie pozycja bazowa szablonu). Działa
względnie, więc obejmie też progresję w cyklu, gdy taka powstanie. Cel nie schodzi poniżej 1 powtórzenia i 5 s.

„Nie chcę” od razu przepisuje treningi aktywnego cyklu: pozycja dostaje zamiennik z `EquipmentMatcher.substitutes`
(ta sama główna partia, posiadany sprzęt, bez ćwiczeń już obecnych w treningu), przerwy i serie pozycji zostają, cel
bierze z domyślnych wartości zamiennika; bez zamiennika pozycja znika, pusty blok też. `PlanGenerator` dostaje listę
wykluczeń i nie proponuje ich ani jako pozycji, ani jako wariantu poziomu. Cofnięcie: przełącznik w szczegółach
ćwiczenia (działa na kolejne plany; do bieżącego cyklu ćwiczenie nie wraca samo).

Odrzucone: pytanie po każdej serii w playerze (przeszkadza w treningu), korekta procentowa (użytkownik myśli
w powtórzeniach), przepisywanie szablonów treningów w miejscu (poziom początkujący skaluje przez 0,7, więc −2 na
ekranie nie da się zapisać w szablonie bez błędu zaokrąglenia), pamięć tylko na jeden cykl (co miesiąc od zera).

Konsekwencje: estymata czasu w generatorze nie zna korekt (±2 powtórzenia to ±6 s na serię, poniżej tolerancji);
edytor pokazuje wartości szablonu bez korekty, a szczegóły treningu i player wartości po korekcie z podpisem „trener”;
historia trzyma cele z dnia treningu. Kopia zapasowa ma pole `tuning`; starsze pliki bez niego wczytują się bez zmian.

## D-36 Ilustracje generowane jako trzecie źródło seedu (2026-09-09)

Kontekst: przegląd zdjęć z 2026-09-08 wskazał 148 ćwiczeń do biblioteki, z czego dla 111 nie ma zdjęcia w free-exercise-db
ani wger, a mieszanka zdjęć z siłowni i rysunków odstaje stylem. Właściciel generuje ilustracje w Notion AI przez stronę
`generator-zdjec.html` (stan w IndexedDB Brave) i przyjmuje je po kontroli z krokami ćwiczenia.

Decyzja: ilustracja to jeden obraz na ćwiczenie, źródłem prawdy w repo jest `tools/zdjecia/<id>.jpg` w formacie, który
wypluwa generator (480 px, JPEG q72). `build_seed.py` daje ćwiczeniu z ilustracją `images = [0.jpg]` niezależnie od tego,
czy ma zdjęcia w sieci (ilustracja wygrywa), a paczkę ZIP z generatora wczytuje `--partia`. Definicja ćwiczenia (nazwy,
kroki, poziom, sprzęt) pochodzi z karty generatora, partie mięśniowe i cele dobiera się ręcznie w liście `ILLUSTRATED`;
bez definicji obraz nie wchodzi do aplikacji, skrypt to wypisuje. Warianty `easier`/`harder` tylko w obrębie tego samego
rodzaju (powtórzenia albo czas) i sprzętu, bo player podmienia wariant, ale zostawia cel z szablonu.

Odrzucone: czytanie stanu generatora prosto z profilu przeglądarki w skrypcie (raz zrobione ręcznie przez kopię bazy
IndexedDB i headless Chrome, ale to nie jest droga na co dzień: eksport ZIP ze strony oznacza rekordy jako wysłane).

Konsekwencje: ekran ćwiczenia pokazuje jedną ilustrację bez podpisów „Start” i „Koniec”; seed v3 miał 126 ćwiczeń, 45
z ilustracją, 77 ze zdjęciami z sieci, 4 bez obrazu. Kolejne partie: `python tools/build_seed.py --partia <zip>` plus
wpisy w `ILLUSTRATED` dla nowych ćwiczeń.

Druga partia (2026-09-10, seed v4) domknęła listę: 66 nowych obrazów, w tym 28 z drugiego generatora
`generator-zdjec-niepewne.html` — tam, gdzie przegląd znalazł w sieci tylko częściowo pasujące zdjęcie, poszła ilustracja
zamiast niego. Seed v4 ma 188 ćwiczeń, 111 z ilustracją, 77 ze zdjęciami z sieci, żadnego bez obrazu.

Trzecia partia (2026-09-13, seed v5) wymieniła te ostatnie 77 przez `generator-zdjec-wymiana.html`, gdzie przy każdej karcie
stały obecne kadry ze źródłem i licencją. Cała biblioteka jest w jednym stylu, `assets/seed/images/` ma jeden plik na ćwiczenie
(2,4 MB zamiast 4,2 MB), a zdjęcia z sieci i ich atrybucja wyszły z aplikacji. Cena: jedna ilustracja musi pokazać ruch, który
wcześniej opowiadały dwa kadry „Start” i „Koniec” — stąd strzałki w promptach. Zysk widać w playerze, który brał ostatni obraz,
czyli pozycję końcową; teraz pokazuje cały ruch.

## D-37 Gotowe programy: druga droga do cyklu, stały protokół, etapy w kolejce (2026-09-15)

Kontekst: właściciel chce prowadzenia po gotowych protokołach (tabata, brzuch codziennie z rosnącą objętością, 100 pompek,
oddech Wima Hofa) z wyborem długości planu i poziomu, przy zasadzie: naraz jeden program, kolejny rodzaj do wyboru dopiero po
zamknięciu cyklu. `PlanGenerator` tego nie ułoży (D-34 trzyma rundy w widełkach 2 do 5), a poziom (D-06) i trener (D-35)
przepisałyby 20/10 tabaty na 15/15. Analiza (workflow 35 agentów: czytelnicy, trzy projekty, sędziowie, refutacje) leży
w `build/analiza/gotowe-programy.md`.

Decyzja: gotowy program to deterministyczna funkcja `PresetCatalog.build(preset, poziom, długość, parametr, programId, now)`
w `domain/plan/preset`, zapisywana przez `ProgramRepository.save` jak plan z kreatora. Każdy etap progresji to osobny `Workout`
(`"$programId-s<n>"`), dzień cyklu wskazuje etap, kolejka (D-21) odhacza je po kolei; „to samo codziennie, co jakiś czas więcej”
to etap jako funkcja numeru sesji (`Stages.stageIndex`). Poziom i długość wybiera się przy starcie i wchodzą wyłącznie w tabele
etapów: bloki mają `Block.fixed = true`, więc `WorkoutPlanner` nie skaluje ich poziomem, nie podmienia wariantu i nie nakłada
korekty trenera; selektor poziomu w playerze i w szczegółach znika, „Podbij cele”, edycja etapu i podmiana „Nie chcę” w aktywnym
cyklu są dla presetu wyłączone (wykluczenie zapisuje się na przyszłe plany z kreatora). Cykl z presetu nosi `presetId`,
`presetParam`, `level` i `cycleDays` w `profileJson`; katalog żyje w Kotlinie, nie w seedzie ani w bazie. Wejście: „Gotowy
program” pod „Ułóż plan” na „Dziś” bez planu, na ekranie zamkniętego cyklu i w stopce planu po jego domknięciu; w trakcie cyklu
żadnego przełączania (także „Wygeneruj kolejny cykl” czeka na koniec). Ćwiczenia oddechowe (`ExerciseCategory.BREATHING`, puste
partie) nigdy nie wchodzą do planu z kreatora ani do zamienników. Silnik dostał krok bez limitu (`openEnded`: czas w górę, koniec
tapnięciem albo klawiszem głośności, bezpiecznik 10 × cel), rytm (`cadenceSec`, efekt `Cadence` bez audio focus), numer rundy
w mowie i powiadomieniu, a AMRAP zapisuje licznik z ekranu także po upływie czasu.

Odrzucone: sekcja `programs` w seedzie (D-19 i „Nie chcę” edytowałyby wbudowane w miejscu), szablony w tabeli, krok 0 kreatora,
drugi aktywny slot programu (oddech rano plus trening po południu), licznik „dzień X z N” (D-21), nowy `TargetKind.HOLD`.

Konsekwencje: zero migracji Room, nowe pola z wartościami domyślnymi w `blocksJson` (`fixed`, `restAfterSec`, `openEnded`,
`cadenceSec`), `profileJson` (`presetId`, `presetParam`) i checkpoincie (`lastCueIndex`, `pendingCount`). Mowa „runda 2 z 8”
i tytuł powiadomienia zmieniają się dla każdego obwodu z rundami, także z kreatora. Program codzienny zeruje serię dni po jednej
luce (cecha wyzwania). Treningi etapów mnożą wiersze `workouts` i po „Usuń plan” zostają sierotami, jak treningi z kreatora.
Litera D-26 zmienia się w dwóch miejscach: drugorzędny odsyłacz „Gotowy program” pod jedynym wezwaniem i brak selektora poziomu
przy stałym protokole; duch (wybór tylko przy zakładaniu cyklu, jeden trening na „Dziś”) zostaje. Seed v6: cztery ćwiczenia
oddechowe, kobra i skręt w leżeniu z ilustracjami z `generator-zdjec-programy.html` (czwarta partia, 194 ćwiczenia).

## D-41 CI odpalane raz na pull request, jeden job na workflow (2026-09-19)

**Zastąpiona** (2026-09-23): na `master` weszła późniejsza optymalizacja CI z PR #19 (2026-09-21) i to jej
workflow obowiązują także na `develop`; opis niżej zostaje jako historia.

Kontekst: 19 września limit minut Actions na koncie wyczerpał się w trakcie wydawania 0.10.0. GitHub przestał startować
jakikolwiek job — bez logów, bez kroków, z komunikatem widocznym tylko w adnotacjach check runu („The job was not started
because recent account payments have failed or your spending limit needs to be increased”) — więc stanęły naraz testy
i wydanie. Repozytorium jest prywatne (D-12), czyli każda minuta jest płatna, a ustawienie było hojne: `testy.yml` ruszał
przy każdym pchnięciu do otwartego PR-a (`synchronize`) i w dwóch jobach, z czego drugi czytał jeden plik.

Decyzja: jeden bieg na pull request i jeden job na workflow.

- `testy.yml` reaguje na `opened`, `reopened` i `ready_for_review`, a job pomija szkice. PR zakładamy jako szkic, pracujemy
  w nim do skutku, a „Ready for review” daje jeden bieg na tym, co naprawdę idzie do `master`. Poprawka po czerwonym
  biegu = ręczne *Run workflow* na gałęzi PR-a.
- Kontrola numeru wersji i opisu wydania przestaje być osobnym jobem: GitHub liczy każdy zaczęty job jako pełną minutę,
  a ta robota trwa sekundy. Idzie razem z `check_schema.py` i `check_contrast.py` przed Gradle'em — sekundy przed minutami.
- Zmiana wyłącznie w `docs/` albo w plikach `.md` w korzeniu pomija testy i build; kontrola wersji zostaje.
- `testDebugUnitTest` i `assembleDebug` w jednym wywołaniu Gradle'a, bo dzielą kompilację.
- Raport testów nie jedzie artefaktem (upload i tak przewracał się o wspólny limit magazynu konta) — nazwy testów,
  które padły, wypisuje krok `Co padło` w podsumowaniu biegu.
- `wydanie.yml`: bramka „tag już istnieje” jest pierwszym krokiem joba budującego, nie osobnym jobem.
- Sprzątaczka magazynu (`actions-storage.yml`) chodzi raz w tygodniu zamiast codziennie — zwalnia to samo miejsce
  za siódmą część minut.

Odrzucone: publiczne repozytorium (minuty byłyby darmowe, ale kod ma zostać prywatny — D-12); `paths-ignore` na poziomie
workflow (bieg pominięty wygląda przy PR-ze jak niewykonany, a kontrola wersji miałaby nie ruszać wtedy, gdy jest
najbardziej potrzebna — przy PR-ze zmieniającym sam `VERSION.md`); zostawienie `synchronize` z `cancel-in-progress`
(nie pomaga, gdy pchnięcia są rozłożone w czasie, a tak właśnie wygląda praca z poprawkami); kolejka scalania
(merge queue — konfiguracja plus osobny bieg za każdy wpis); własny runner.

Konsekwencje: wynik przy PR-ze może być starszy niż głowa gałęzi, więc każdy bieg zaczyna podsumowanie od linii
„Sprawdzony commit” — to ona, a nie sama zielona fajka, mówi, co zostało sprawdzone. Szkic nie ma CI, więc jedyny bieg
wypada tuż przed scaleniem. Przy dwóch jobach zamiast pięciu (dwa w testach, trzy w wydaniu) mniej widać, na którym
etapie coś padło — zostaje podsumowanie i nazwy kroków.

## D-42 Plany użytkownika: lista, plik do udostępniania, statystyki planu z PDF, duże ekrany (2026-09-23)

Kontekst: właściciel (2026-09-23): nie ma osobnego trybu trenera — każdy użytkownik może ułożyć plan albo zmienić istniejący
i wysłać go innym; aplikacja trzyma listę planów (własne, zaimportowane, wygenerowane), z której wybiera się aktywny;
statystyki są dziś wspólne dla całej aplikacji, a potrzebne są dla konkretnego planu, z raportem w PDF (np. dla osoby,
która plan ułożyła); zostajemy przy Androidzie, bo wiele osób ma tylko telefon, ale z dobrą obsługą tabletów i dużych
ekranów, sprawdzaną na emulatorze tabletu; dystrybucja przez sklep odłożona, na razie wersja lokalna jak dotąd (D-12, D-33).
Stan kodu: cykle poza aktywnym nie mają żadnego wejścia, sesja nie zna planu (przynależność zgaduje reguła „trening z dni
planu i `startedAt >= program.startedAt`” skopiowana w pięciu miejscach), edytor obsługuje jeden trening (D-19),
jedynym plikiem jest kopia zapasowa (D-18).

Decyzja: jedna rola; autor to pole planu (`programs.author`), w interfejsie „autor planu”, bo „trener” to już D-35.
Plan to wiersz `programs` ze źródłem (`PlanSource`: WIZARD, PRESET, CUSTOM, IMPORTED), opisem, autorem, `originId`
i `shareUid`, jego dni i jego treningi. Trening należy do jednego planu, a właściciela wyznacza prefiks id
`"$programId-"` — ta sama konwencja, której kreator i gotowe programy używają od początku; duplikat, import, powtórka
i „Duplikuj i zmień” zawsze przenumerowują id (`PlanCopy`), więc dwa plany nigdy nie dzielą treningu ani postępu.
Sesja zapisuje `programId` przy starcie (ustawia go `PlayerController` z właściciela treningu, silnik dalej nie wie
o planach); kolejkę (D-21), statystyki i raport liczy jedna funkcja `PlanSessions`. Naraz jeden aktywny plan (D-37);
lista planów jest osobnym ekranem, „Dziś” zostaje jedną kartą (D-26), a aktywacja z listy jest zakładaniem cyklu.
Powtórka planu to nowy wiersz, stary przebieg zostaje z własnymi statystykami. Plan własny układa się w edytorze planu
(dni, odpoczynki, przypisania, nowe treningi), zrobione dni aktywnego planu są zablokowane, trening wbudowany wchodzi
do planu tylko jako kopia (D-19 w domenie, nie tylko w UI); plan własny domyślnie dopasowuje się do poziomu i trenera
jak plan z kreatora, a przełącznik planu ustawia `Block.fixed`; gotowy i zaimportowany plan zmienia się przez kopię.
Plan własny i zaimportowany nie przechodzi przez `PlanGenerator` (D-34 bez zmian). Plik planu to osobny format
`mysttic-trainings-plan` (`.plan.json`), nie wariant kopii zapasowej: dni, treningi i migawki użytych ćwiczeń, bez profilu
(wzrost, waga), historii i pamięci trenera; import zawsze pod nowe id, niczego nie nadpisuje i sam nie aktywuje planu,
ćwiczenia spoza seedu dostają id `custom-<skrót treści>`; plik wychodzi tylko przez systemowe okno udostępniania albo SAF.
Statystyki planu liczymy z `sessions.programId`, tygodnie planu z kolejki, a raport PDF rysuje `android.graphics.pdf.PdfDocument`
bez nowych zależności, wykresy tymi samymi funkcjami `DrawScope` co ekran (D-23), w kolorach „Papieru i Ognia”; raport
wychodzi tylko na żądanie. `CycleReview` (D-22) liczy się po staremu. Duże ekrany: `AppLayout` z `BoxWithConstraints`,
szyna nawigacji bez ikon (D-32), treść ograniczona do czytelnej szerokości, dwa panele tylko dla list ze szczegółem;
test na AVD `Pixel_Tablet_API_37` w obu orientacjach i na `Pixel_9_Pro` w poziomie, CI dalej bez emulatora (D-40).
Tylko Android, bez sieci i konta (D-01). Pełny projekt: `docs/plany-do-udostepniania.md`.
Odpowiedzi właściciela na trzy otwarte pytania (2026-09-23): planów może być kilka, aktywny tylko jeden, a przełączyć
wolno w każdej chwili — bieżący plan zostaje wstrzymany ze swoim postępem (zmienia literę D-37: katalog gotowych programów
i lista planów są dostępne także w trakcie cyklu, po potwierdzeniu); plan od kogoś wchodzi dosłownie, tak jak go ułożył
autor (bloki `fixed`); raport pokazuje ciężar zaplanowany i RPE sesji, bez wpisywania faktycznego ciężaru przy serii.

Odrzucone: osobny tryb trenera; kolumna `workouts.programId` (drugie źródło prawdy, które `WorkoutEditing.toWorkout`
i import starej kopii by wyzerowały); tabela przebiegów planu (przebieg to osobny wiersz); plik planu jako wariant
`BackupFile` (import kopii nadpisuje po id); import pod id nadawcy; zakładka „Plany” w dolnym pasku (na razie);
`material3-adaptive` (niepewny w BOM 2026.08.00, `NavigationSuiteScaffold` wymaga ikon); biblioteki PDF i wykresów;
obrazy ćwiczeń w pliku planu w wersji 1.

Konsekwencje: Room v4 z jedną migracją, kopia zapasowa v3 (pliki v1 i v2 wczytują się dalej, źródło cyklu liczone
z `profileJson`); zapisy wieloetapowe w transakcjach; rytm odpoczynku liczony od `rhythmStart`, który przesuwa się przy
wznowieniu planu, a przeszłe dni serii ocenia rytm aktywnego planu, jak dotąd po założeniu nowego cyklu. Litera D-26
zmienia się o odsyłacz „Twoje plany”. Plan z sesją w toku jest tylko do odczytu. Pierwsza sesja nowego przebiegu nie ma
porównania „Δ względem poprzedniego razu”, bo treningi mają nowe id. Baza v4 i kopia v3 nie wczytają się w starszej
aplikacji. Plan od kogoś z ćwiczeniem z nowszego seedu pokazuje je bez ilustracji do aktualizacji aplikacji.
Uzupełnienie po testach na emulatorze (2026-09-25): skoro plan od kogoś wchodzi dosłownie, eksport nie może wysyłać
surowego szablonu INTERMEDIATE. Bloki, które u autora dopasowują się do poziomu, jadą w pliku tak, jak je widzi
(`WorkoutPlanner.shownBlocks`: wariant, cele i przerwy dla jego poziomu i sprzętu, bez korekty trenera), a pole poziomu
w pliku mówi, dla jakiego poziomu je policzono. Zrobiony dzień zostaje też na swoim numerze, nie tylko w zbiorze
odhaczonych, bo przesunięcie zmienia jego datę.

## D-40 Zrzuty palet z Robolectrica zamiast testów instrumentalnych (2026-09-19)

Kontekst: po D-39 są trzy palety w dwóch motywach, a jedyną kontrolą wyglądu był skrypt kontrastu i oko.
Testów instrumentalnych repozytorium nie ma (`app/src/androidTest` nie istnieje), a CI nie ma emulatora.

Decyzja: zrzuty rysowane w testach jednostkowych — Robolectric w trybie `NATIVE` rysuje prawdziwe komponenty,
Roborazzi zapisuje PNG. Jeden ekran (`PaletteScreenshotTest`) zbiera wszystko, co niesie kolor: kartę treningu
z akcją, kartę progresji z żarem, statystyki, chipy, linię włosową i komunikat błędu; leci przez każdą paletę
i oba motywy. Bez wtyczki Gradle — same biblioteki i trzy właściwości systemowe, bo wtyczka Roborazzi musiałaby
nadążać za AGP, a tu i tak sterujemy tym z `-Proborazzi.record` / `-Proborazzi.verify`.

Odrzucone: Paparazzi (wersja 2.0.0-alpha05 ciągnie `com.android.tools:sdk-common` 31.13.2, czyli AGP 8.13 —
przy AGP 9.3 to proszenie się o kłopot); testy instrumentalne (wymagają emulatora, którego CI nie ma);
wtyczka Gradle Roborazzi (jedna zależność więcej do pilnowania przy podnoszeniu AGP).

Konsekwencje: `testOptions.unitTests.isIncludeAndroidResources = true` i trzy nowe zależności testowe.
CI tylko rysuje (bez wzorców), więc łapie wywrotkę motywu, a nie zmianę odcienia — wzorce powstaną przy pierwszym
`-Proborazzi.record=true` na maszynie z Android SDK i wtedy wejdą do repozytorium. Zrzuty nie zastępują telefonu:
nie ma na nich czcionki systemowej, skali tekstu ani tego, jak ekran świeci po ciemku.

## D-39 Trzy motywy do wyboru w Ustawieniach, każdy skończony (2026-09-19)

Kontekst: „Ogień i Krew” (D-32) jest ciepła i ciemna, a właściciel chciał zobaczyć alternatywy — w tym wariant jasny
na dzień. Badanie palet (2026-09-19) dało cztery komplety po 35 ról, każdy przechodzący `tools/check_contrast.py`;
po obejrzeniu makiet zostały trzy.

Decyzja: wygląd jest ustawieniem, nie podmianą, i ma trzy pozycje — **„Ogień i Krew”** (domyślny, ciemny),
**„Papier i Ogień”** (jasny) i **„Stal i Bursztyn”** (ciemny, chłodny grafit). Każdy motyw to jeden skończony wygląd,
a nie para jasny/ciemny: aplikacja nie idzie już za trybem systemu, bo „jasny” jest tu osobnym motywem, nie wariantem
tego samego. `AppTheme` w `ui/theme/Themes.kt` (tam też wartości dwóch nowych motywów; „Ogień i Krew” zostaje
w `Color.kt`), trzy schematy i `schemeFor` w `Theme.kt`, wybór w DataStore jako identyfikator (`theme_id`) —
warstwa danych nie importuje `ui/`, mapuje go `AppTheme.fromId`. `MainActivity` podaje motyw do `TrainingsTheme`,
więc zmiana w Ustawieniach przemalowuje aplikację od razu, i przy okazji ustawia tło okna oraz jasność ikon pasków
systemowych z `AppTheme.dark` — inaczej jasny motyw przy ciemnym systemie miałby niewidoczne ikony statusu.
Wybór wchodzi do kopii zapasowej (D-18).

Reguły z D-32 zostają w mocy dla każdego motywu: wypełnienie `primary` znaczy akcję, `tertiary` niesie informację
i nigdy nie trafia na przycisk, `error` jest z innej rodziny odcieni niż akcja, powierzchnie są podbarwione,
elewacje zerowe. Zmienia się jedno zdanie z D-32: wygląd nie jest już jedyną tożsamością produktu, ale dalej nie jest
tapetą systemu — `dynamicColor` zostaje wyłączony (D-17).

Odrzucone: przełącznik jasny/ciemny obok motywu (wygląd ma być jedną decyzją, nie dwiema; „Papier i Ogień” jest jasny
zawsze); wariant jasny „Ognia i Krwi” z D-32, który ten motyw zastąpił — wartości `*Light` zniknęły z `Color.kt`;
„Nokturn i Lód” (granat z lodowym cyjanem — najczystszy semantycznie, ale nie pasuje do charakteru aplikacji);
podmiana domyślnego wyglądu; osobny ekran „Wygląd”; podgląd motywu na miniaturze (zmiana działa od razu na całym
ekranie, więc miniatura byłaby gorszą wersją tego, co i tak widać).

Konsekwencje: `tools/check_contrast.py` nie czyta jednego schematu, tylko wszystkie z `Theme.kt` (trzy) — nowy motyw
nie wjedzie bez kontroli; te same progi pilnuje `ThemeContractTest`, który sprawdza też, że `AppTheme.dark` nie kłamie
o jasności tła. Nowy motyw = wartości w `Themes.kt`, schemat w `Theme.kt`, pozycja w `AppTheme` i zielony skrypt.
Pierwsza klatka po starcie bierze tło okna z `res/values(-night)/colors.xml`, czyli z trybu systemu — przy motywie
niezgodnym z systemem mignie nie tym kolorem, zanim Compose narysuje swoje.

## D-38 Biblioteka: kafelki z ilustracją, panel filtrów zwinięty, wymiary „i”, wartości „lub” (2026-09-16)

Kontekst: biblioteka miała dwa rzędy chipów nad listą (tryb sprzętu jednokrotnego wyboru i jedna partia), bez rodzaju, poziomu
ani konkretnego sprzętu; właściciel chce filtrować szerzej i mieć miejsce na przeglądanie ilustracji (D-36, 194 ćwiczenia).

Decyzja: reguła filtra to czysty Kotlin w `domain/library/LibraryFilter` (test w `LibraryFilterTest`), ekran tylko ją pokazuje.
Sekcje panelu: sprzęt („Bez sprzętu” plus konkretne elementy), rodzaj (`ExerciseCategory`), poziom, partie (główne mięśnie);
wartości w sekcji łączą się „lub”, sekcje „i”, więc „Bez sprzętu” z „Hantle” znaczy „do zrobienia samymi hantlami”.
„Mój sprzęt” (`Exercise.canDoWith`) i szukanie po nazwie są osobnymi wymiarami poza panelem: pasek nad listą trzyma
„Filtry · n” (n = zaznaczenia w panelu), „Mój sprzęt”, licznik „x z y” i linię włosową. Panel jest domyślnie zwinięty
i rozwija się jako pierwszy element listy (przewija się z wynikami); chipy biorą się z `LibraryFacets.of` (tylko wartości
obecne w bibliotece, w kolejności enumów), więc sztanga bez ćwiczeń nie dostaje chipa. `Equipment.shortPl` daje krótkie
nazwy do chipów i wierszy listy. Domyślny widok to kafelki w dwóch kolumnach (`ExerciseTile`): ilustracja w całości
(kadrowanie gubiłoby drugą fazę ruchu) w ramce 16:9 bez tła, przycięta do własnych proporcji z zaokrągleniem, pod nią
nazwa, pierwsza partia i poziom w bloku o stałej wysokości; lista wierszy zostaje jako drugi widok (`ViewToggle`
w nagłówku, wybór w DataStore `libraryTiles`), oba w jednej `LazyVerticalGrid` z liczbą kolumn 2 albo 1.

Odrzucone: arkusz dolny (rozwijany panel był prośbą wprost), jednokrotny wybór w sekcji (tryb `EquipmentFilter` z M1, usunięty),
liczniki przy chipach (szum), filtr powtórzenia/czas (mało przydatny przy przeglądaniu), kadrowanie ilustracji do kwadratu
(ucina fazę ruchu), kafelki bez listy (wiersz mieści wszystkie partie i sprzęt), wymiary obrazów w seedzie (proporcje
czyta `rememberAsyncImagePainter` po wczytaniu).

Konsekwencje: `EquipmentFilter` i `EquipmentMatcher.filter` znikają; `FilterPill` dostaje opcjonalną ikonę końcową; stan filtra
i rozwinięcia żyje w `LibraryViewModel`, nie w DataStore (po ponownym uruchomieniu apki biblioteka startuje bez filtrów),
a wybór kafelki / lista w DataStore (poza kopią zapasową, jak `seedVersion`).
Zmiana reguły filtra = test w `app/src/test/.../domain/library`.

## D-43 Cel pokazany jako jedyna liczba, od której liczymy progresję, wariant i sprzątanie po cyklu (2026-09-18)

Spisane 2026-09-18 na gałęzi roboczej jako D-39. Numer nadano przy scaleniu z `develop` (2026-09-23), bo D-39 to motywy.
Część tematów ruszyły w międzyczasie 0.9.1 i D-42, więc niżej jest tylko to, co ta decyzja dokłada.

Nadrobienie długu z `docs/PLAN.md`: cztery reguły, które rozjeżdżały się z tym, co użytkownik widzi na ekranie.

**Progresja liczy się od celu pokazanego.** `SetProgression.suggest` brało cel wprost z szablonu po skalowaniu poziomem,
a player pokazuje `WorkoutPlanner.shownTarget`, czyli szablon po skalowaniu i po korekcie trenera (D-35). Karta „Podbij cele”
mówiła więc „11 → 12” tam, gdzie na ekranie stało 9, a po korekcie trenera przestawała się pojawiać w ogóle, bo log sesji
nie zgadzał się z jej własnym wyliczeniem. `suggest` dostało w 0.9.1 `owned` (strażnik propozycji trudniejszego wariantu),
a w D-42 pomijanie bloków `fixed` i pozycji AMRAP. D-43 dokłada `tuning` i `WorkoutPlanner`, a wszystkie liczby (próg
wariantu, `from`, `to`) idą przez `shownExercise`/`shownTarget`, z kluczem po ćwiczeniu pokazanym, nie po szablonie.
Propozycji trudniejszego wariantu nie ma, gdy po podmianie na ekranie stałoby to samo ćwiczenie (u początkującego
`harderId` bywa ćwiczeniem z szablonu, które poziom i tak zamienia na łatwiejsze). Skutki: propozycje działają też
na poziomie innym niż średniozaawansowany (dotąd podmiana wariantu blokowała porównanie), sesja zrobiona przy innej
korekcie propozycji nie daje (to samo zabezpieczenie co przy innym poziomie), a `to <= from` kończy temat, gdy korekta
trzyma cel na podłodze. Uzupełnia D-24 (liczby z ekranu) i D-35 (korekta po skalowaniu).

**Wariant poziomu wolno pokazać tylko z jego sprzętem.** `resolveVariant` podmieniało ćwiczenie wyłącznie po poziomie, więc
przełączenie poziomu albo odznaczenie sprzętu po założeniu cyklu wystawiało w playerze ruch, którego nie ma na czym zrobić
(w seedzie cztery takie łańcuchy, m.in. pompki → pompki z nogami na podwyższeniu wymagające ławki). Teraz `resolveVariant`
przyjmuje posiadany sprzęt i przy jego braku zostaje przy ćwiczeniu z szablonu; ten sam zbiór idzie do `shownExercise`,
`shownTarget`, `expand`, `PlanGenerator` i `CoachRepository` (propozycję trudniejszego wariantu strzeże `SetProgression`
od 0.9.1). Wariant w seedzie może wymagać sprzętu spoza ćwiczenia bazowego, to zysk dla kogoś, kto ten sprzęt ma, a strażnik
chroni resztę. Odrzucone: podmiana na zamiennik (zmieniałaby ćwiczenie przy każdej edycji seedu), rekurencyjne schodzenie
łańcuchem, zamrożenie wariantu w szablonie (zabija zmienny poziom).

**Okno EMOM w modelu, nie w przybliżeniu.** `BlockItem.windowSec` (domyślnie null, więc `blocksJson` zgodny wstecz)
mówi, ile trwa cała runda: praca i przerwa po niej. Planner wystawia przerwę z estymatą (3 s na powtórzenie), a `WorkoutEngine.advance`
podmienia jej długość na `windowSec` minus faktyczny czas pracy, bez pauz, bo `elapsedInPhaseMs` je odejmuje. Praca dłuższa
niż okno pomija przerwę i od razu startuje kolejną rundę (tak działa EMOM), a zapowiedź dalej mówi numer rundy, nie nazwę
ćwiczenia. Poziom trudności okna nie rusza: minuta to kontrakt programu. Odrzucone: `Block.windowSec` (wymagałby znacznika
startu rundy w `EngineState`), nowy `BlockKind` (psuje odczyt `blocksJson` na starszym wydaniu), liczenie przerwy w `PlayerController` (łamie D-16).
Plik planu (D-42) niesie pole jako `windowSec` z domyślnym null, więc plik v1 bez niego wczytuje się dalej; wrogi odczyt
i edytor przepuszczają okno przez `WorkoutEditing.sanitize` (≤ 0 daje null, górna granica `MAX_WINDOW_SEC` = 600 s).
Cykl Burpee co minutę założony przed aktualizacją nie dostaje okna (bez łatania `blocksJson`), działa ono od następnego
założenia programu. Edytor treningu i szczegóły okna jeszcze nie pokazują.

**Resztki po starszych „Usuń plan”.** Kasowanie planu razem z treningami weszło w 0.9.1 (`ProgramDao.deleteUnusedWorkouts`,
od D-42 także po prefiksie przez `deleteUnusedOwnedWorkouts`, w transakcji): trening z sesją w historii zostaje. Wiersze
osierocone przez wydania sprzed 0.9.1 sprząta `SeedImporter` przy podbiciu seedu (`WorkoutDao.deleteOwnerlessPlanWorkouts`):
trening `plan-…`, którego prefiks nie należy do żadnego istniejącego planu (ta sama reguła co `PlanSessions.ownerOf`),
który nie stoi w żadnym dniu planu, którego nie ma żadna sesja i który powstał ponad dobę temu (plan zapisywany w tej
chwili ma jeszcze nie mieć dni). Bez migracji Room. Odrzucone: osobna reguła `domain/plan/PlanCleanup` z gałęzi roboczej
(dublowała 0.9.1, chroniła tylko sesję w toku, a warunek „nie stoi w żadnym dniu” bez sprawdzenia właściciela skasowałby
trening planu odłączony w edytorze planu, D-42). Jej scenariusze testowe przeszły na Room w pamięci
(`data/db/WorkoutCleanupTest`, Robolectric): `ProgramRepository.delete` i oba zapytania `SeedImporter`.

**Poziom w historii także przy przerwaniu i w gotowym programie.** D-42 zapisuje przy zamknięciu poziom ze stanu silnika.
D-43 dokłada przerwanie: `Abort` idzie przez ten sam efekt `Finished` z migawką stanu, a sesja porzucona przy starcie
następnej dostaje poziom i `plannedSets` z bieżącego stanu albo z checkpointu. Trening stały (`Workout.isFixed`: gotowy
program, plan dosłowny z D-42) startuje z poziomem z profilu planu, do którego należy (`ProgramRepository.ownerOf`, potem
`byId`), a przy jego braku z bieżącego ustawienia. Odrzucone: poziom z aktywnego planu (przy kilku planach, D-42, aktywny
nie musi być właścicielem treningu). Bez migracji: kolumny już były, zmienia się tylko moment i źródło zapisu.

Przy okazji: wbudowane treningi (`full-body-a`, `full-body-b`, `core-tabata`) wypadają z seedu w wersji 7, a import kasuje
te, których nie tknęła żadna sesja (`WorkoutDao.deleteUnusedBuiltIn`, z pominięciem id z bieżącego seedu i dni planu);
od D-26 nie mają żadnego wejścia w interfejsie, a ich treść pokrywa preset TABATA i kreator. Z interfejsu znika
„Duplikuj do edycji” z D-19. Zakaz edycji w miejscu zostaje w domenie (`WorkoutEditing.canEdit`) dla wierszy z historią,
a edytor planu (D-42) kopiuje treningi już tylko z innych planów (i z takich wbudowanych resztek). Seed pilnuje
`SeedIntegrityTest`: unikalne id, obraz przy każdym ćwiczeniu, warianty istnieją i trzymają rodzaj celu, brak treningów
i brak id z prefiksem `custom-` (zarezerwowanym dla plików planów, D-42).

Przy okazji: zgoda na powiadomienia nadana w trakcie treningu. `WorkoutService` co 2 s pyta o zgodę, a `service/NotificationGate`
(zbocze, czysty Kotlin, `NotificationGateTest`) mówi, kiedy wystawić powiadomienie ponownie przez `startForeground`.
Bramka startuje ze stanem zgody z `onCreate` serwisu, więc przy zgodzie nadanej wcześniej pierwsze sprawdzenie nie udaje
jej odzyskania i nie wystawia powiadomienia drugi raz.

## D-44 Ergometry, więcej ćwiczeń ze sprzętem i notatki do ćwiczeń w planie (2026-09-25)

Kontekst: issue #20. Właściciel chce znacznie więcej ćwiczeń, także ze sprzętem, i ćwiczeń na ergometrach do treningów,
które odliczają czas; ilustracje jak dotąd z generatora zdjęć. Do tego notatki do poszczególnych ćwiczeń planu
(np. ciężar przy wyciskaniu), które przechodzą na kolejne tygodnie, ale tylko w ramach jednego planu. Wskazówka z D-42
(`BlockItem.note`) siedzi w szablonie treningu, więc w planie z kreatora i w gotowym programie nie da się jej wpisać.

Decyzja, odpowiedzi właściciela z 2026-09-25:
- **Ergometry to sprzęt:** `Equipment.ROWING_MACHINE`, `STATIONARY_BIKE`, `ELLIPTICAL`, `TREADMILL` z flagą `ergometer`.
  Ćwiczenia na nich są w bibliotece, edytorze treningu i edytorze planu, ale nie w kreatorze: `PlanGenerator` je
  pomija, ekran sprzętu w kreatorze ich nie pokazuje, a `EquipmentMatcher.substitutes` daje ergometr tylko w zamian
  za inny ergometr (inaczej „Nie chcę” wstawiłby maszynę do planu z kreatora). Plany z kreatora zostają takie jak dziś.
- **Cel na czas do godziny:** `WorkoutEditing.MAX_TARGET_SECONDS = 3600` dla czasu i okna AMRAP (powtórzenia dalej
  do 999). Stepper w edytorze idzie po siatce z domeny (`nextTarget`/`previousTarget`): co 5 s do 2 minut, co 15 s do
  10 minut, dalej co minutę, i pokazuje czas jako „12:00”, jak player.
- **Nowe ćwiczenia:** 60 definicji na koniec `ILLUSTRATED` w `tools/build_seed.py` (20 na ergometry, 40 ze sprzętem),
  z opisami po polsku; wchodzą do aplikacji razem z ilustracjami z nowej strony generatora
  (`generator-zdjec-nowe.html`, składa ją `build/zdjecia/generuj_nowe.py`). Warianty ergometrów łączą tylko ćwiczenia
  o podobnej skali czasu, bo podmiana wariantu zostawia cel z szablonu.
- **Notatki do ćwiczeń w planie:** tabela `plan_exercise_notes` (Room v5) z kluczem plan i ćwiczenie pokazane na
  ekranie (jak u trenera, D-35). Wpisuje się je w szczegółach treningu; widać je tam i w playerze (także na karcie
  „Dalej” w przerwie). Notatka nie zmienia szablonu ani postępu, więc wolno ją pisać także w gotowym programie, planie
  od kogoś i planie z sesją w toku. Kopia i powtórka planu zabierają notatki, plik planu też je niesie (pole `notes`
  z domyślną pustą listą, tylko dla ćwiczeń z pliku), kopia zapasowa w wersji 4 również; usunięcie planu je kasuje.

Odrzucone: ergometry w kreatorze (zmieniłyby generowane plany i wymagały strojenia pod czas sesji); dystans, waty
i kalorie jako cel (aplikacja odlicza czas, intensywność opisuje odczucie); notatka w szablonie treningu (nie działa
w planach tylko do odczytu i znika przy nowym przebiegu); notatka per trening zamiast per ćwiczenie (ciężar dotyczy
ćwiczenia, a ćwiczenie wraca w kilku treningach planu); notatki w playerze i podsumowaniu (właściciel wybrał tylko
szczegóły treningu).

Konsekwencje: Room v5 z jedną migracją (nowa tabela), kopia zapasowa v4 (starsze wczytują się dalej), plik planu
dalej w wersji 1 z nowym polem. Notatka kluczem po ćwiczeniu pokazanym znika z widoku, gdy zmiana poziomu podmieni
wariant, i wraca po powrocie na poziom. Nowe ćwiczenia bez ilustracji nie wchodzą do aplikacji, więc wydanie czeka
na partię zdjęć albo wychodzi bez nich z poprawionym opisem w CHANGELOG.

## D-45 Wersja 1.0.0: koniec fazy 0.x (2026-09-25)

Kontekst: po D-42 (plany do udostępniania, edytor planu, statystyki z PDF, duże ekrany), D-43 (dług techniczny) i D-44
(ergometry, notatki do ćwiczeń, 89 nowych ćwiczeń) aplikacja robi wszystko, co miała robić, a testy na emulatorze
telefonu i tabletu są przejściem całej listy z `docs/testing.md`. Wydanie 0.10.0 (motywy) nie trafiło do pobrania.

Decyzja właściciela: następne wydanie to od razu 1.0.0, pierwsze stabilne, na początek października. Niesie wszystko
od 0.9.1, także motywy z 0.10.0 (zdanie na górze opisu wydania). Do wydania bez nowych funkcji, tylko poprawki.

Konsekwencje: koniec wyjątku z D-05, od 1.0.0 każda zmiana schematu Room ma migrację i przypadek w `MigrationTest`,
nigdy „odinstaluj i zainstaluj”. `versionCode` 1.0.0 to 10000 (wzór z `app/build.gradle.kts`), więc przechodzi nad
0.9.1 i niewydanym 0.10.0. Umowa wydaniowa (D-33: tag `mysttic-trainings-v1.0.0`, plik
`mysttic-trainings-1.0.0-android.apk`) bez zmian. Numeracja dalej semantyczna: poprawki podnoszą patch, nowe funkcje minor.


## D-46 Gotowe programy po pierwszym użyciu: sprzęt, trudność, pominięte ćwiczenia i skala wysiłku (2026-09-29)

Kontekst: issue #22, uwagi właściciela po programie „Siedem minut”, i issue #21. Program wystawiał ćwiczenia na ławce
bez zaznaczonej ławki: `PresetCatalog.build` nie znał sprzętu, a bloki `fixed` (D-37) omijają wariant i „Nie chcę”.
Podsumowanie pomijało ćwiczenia pominięte w całości, bo buduje się z serii, a pominięcie serii nie zapisuje (D-22).
Skala oceny od 6 do 10 miała mylący opis, a Historia zapisywała do tej samej kolumny suwakiem od 1 do 10. Druga strona
deski bocznej szła bez przerwy i bez podpisu. Issue #21 prosi o trudność programów na liście.

Decyzja:
- Sprzęt w gotowym programie jest twardym filtrem jak w kreatorze (D-20), ale sprawdzanym przy budowie cyklu, nie
  w playerze: `PresetDefinition.substitutes(level)` to kuratorowana mapa ćwiczenie ze sprzętem → ćwiczenie bez sprzętu
  tej samej partii i rodzaju, a `PresetCatalog.build` dostaje bibliotekę i posiadany sprzęt i podmienia tylko to,
  czego nie da się zrobić. Z pełnym sprzętem protokół zostaje nietknięty. Sprzęt bez zamiennika (drążek w „Desce
  i drążku”, istota programu) daje `PresetCatalog.unmetEquipment` i wyszarza program na liście. Zamienniki pilnuje
  `PresetSeedConsistencyTest`. Mapa zamiast `EquipmentMatcher.substitutes`, bo protokół ma swoją kolejność partii.
- Trudność programu (`PresetIntensity`: łagodna, średnia, wysoka) to wrodzona intensywność protokołu, przypisana
  ręcznie, bez wartości domyślnej. Nie wylicza się jej z ćwiczeń ani minut (nie rozróżniają programów) i nie myli
  z poziomem 1, 2, 3 wybieranym przy starcie (D-37). `PresetCatalog.all` stoi w kolejności od łagodnych, test to
  pilnuje. Wyjątek od D-45 („bez nowych funkcji”) z decyzji właściciela z 2026-09-29: to mała zmiana bez danych.
- Pominięte w całości ćwiczenie liczy `SessionStats.skipped` z kroków stanu końcowego silnika, bez nowych wpisów
  w bazie: pominięcie dalej nie tworzy serii (D-22). Po przerwaniu liczą się tylko kroki przed tym, na którym trening
  stanął.
- Ocena wysiłku zostaje od 6 do 10 (RPE liczone zapasem powtórzeń), z podpisami słownymi i podpowiedzią (`Rpe`,
  `RpePicker` w obu ekranach). Skala od 1 do 5 wymagałaby niejednoznacznego przeliczenia zapisanych ocen w migracji.
  Oceny spoza zakresu z dawnego suwaka zostają w bazie i w średnich.
- Ćwiczenie jednostronne na czas w gotowym programie idzie jako dwie pozycje z przerwą na zmianę strony (5 s, jak
  w rozciąganiu) i notatkami stron, albo jako `sets = 2` w bloku serii.

Konsekwencje: poprawki programu działają w cyklach zakładanych po aktualizacji; zapisane cykle mają bloki w bazie
i zostają po staremu. Nowy program ze sprzętem = decyzja: zamiennik w `substitutes` albo świadoma blokada.

## D-47 Baza ćwiczeń wydawana niezależnie od aplikacji (2026-10-28)

Kontekst: issue #27. Właściciel chce rozwijać bazę ćwiczeń bez wydawania aplikacji: nowe albo poprawione ćwiczenie ma
dotrzeć na telefon bez nowego APK, baza ma mieć własne wydania pobierane z aplikacji, a do zarządzania nią narzędzie
tylko dla właściciela, ale w tym projekcie. Gałęzie zostają dwie, `master` i `develop`. Dziś każda zmiana ćwiczenia to
wydanie APK: `exercises.json` (222 KB) i 283 ilustracje (4,0 MB) jadą w assetach, źródłem prawdy są listy w kodzie
`tools/build_seed.py` (1650 linii), który przy każdym uruchomieniu pobiera free-exercise-db, a ilustracje leżą w repo
dwa razy (skrypt kopiuje `tools/zdjecia/<id>.jpg` bajt w bajt do `assets/seed/images/<id>/0.jpg`).

Decyzja (zakres wybrany przez właściciela 2026-10-28: same ćwiczenia):
- **Zakres:** definicje ćwiczeń i ich ilustracje. Gotowe programy zostają w Kotlinie (D-37), bo są funkcjami poziomu
  i liczby sesji, a `PresetId` to enum zapisany w profilu cyklu. Nowy program dalej wymaga wydania aplikacji.
- **Jedno repo, osobny numer i osobny workflow.** Źródłem jest katalog `baza/`: `exercises.json` w formacie `SeedFile`
  z polami `version`, `format` i `minApp` oraz `images/<id>.jpg`. W `baza/` leżą tylko dane, bo Gradle podpina go przy
  buildzie jako assety APK; `app/src/main/assets/seed/` i `tools/zdjecia/` znikają. Jednorazowe przeniesienie zamraża
  w pliku wartości z datasetu, więc `build_seed.py` nie jest już potrzebny, a D-11 przestaje obowiązywać. Numer ciągnie
  dalej `SEED_VERSION` (pierwsza baza w nowym układzie to v10), bo `seedVersion` siedzi już w ustawieniach i w pliku
  planu (D-42).
- **Wydanie bazy.** Zmiana w `baza/` idzie na `master` tym samym PR-em z `develop` co kod. Po scaleniu rusza `baza.yml`
  (także ręcznie, żeby dokończyć przewrócone wydanie): sprawdza dane, a przy nowym numerze zakłada tag `baza-v<n>`
  w repozytorium kodu i wydanie `mysttic-trainings-baza-v<n>` w `Mysttic/Mysttic` z dwoma plikami:
  `mysttic-trainings-baza-<n>.json` (manifest: `version`, `format`, `minApp`, liczba ćwiczeń, ile nowych i zmienionych,
  rozmiar i SHA-256 ZIP-a) i `mysttic-trainings-baza-<n>.zip` (`exercises.json` i `images/`). Wydanie nie dostaje
  znacznika „Latest”, a starsze wydania bazy są kasowane jak wydania APK. Numer już wydany kończy bieg bez wydania, gdy
  treść się zgadza, i czerwonym biegiem, gdy nie. Prefiks `mysttic-trainings-baza-v` nie zaczyna się od
  `mysttic-trainings-v`, więc nie złapie go `UpdateCheck`, skrypt strony ani sprzątanie w `wydanie.yml`, a tag
  `baza-v<n>` nie wchodzi w bramkę numeru APK (`git tag -l 'v[0-9]*.[0-9]*.[0-9]*'`). Obok umowy z D-33 powstaje druga
  o tej samej budowie: tag i nazwy plików bazy znają `baza.yml` i aplikacja, więc zmiana jednego = zmiana w obu.
- **Zgodność wstecz to reguły danych.** Bazę zobaczy każda zainstalowana wersja, która umie ją pobrać, więc:
  - zmiana treści to wyższy numer, inaczej telefon z tym numerem jej nie wczyta;
  - id raz wydanego ćwiczenia nie znika i nie zmienia `kind`: historia, notatki, trener, plany i programy wskazują po
    id, a rodzaj celu siedzi w zapisanych blokach;
  - usunięcie to wycofanie: `retiredAt` niesie datę wycofania (`RRRR-MM-DD`, wpisuje ją narzędzie, a przywrócenie ją
    czyści; `null` znaczy ćwiczenie w użyciu, data z przyszłości nie przejdzie walidacji). Wycofane zostaje dla historii
    i zapisanych planów, ale znika z biblioteki, kreatora, zamienników, „Nie chcę” i wyszukiwarki edytora, a nie wskazuje
    go żaden wariant ani gotowy program; szczegóły ćwiczenia otwarte z historii albo planu pokazują datę wycofania. Pole
    wchodzi od razu do formatu 1, bo starsza aplikacja zignorowałaby je (`ignoreUnknownKeys`) i dalej proponowała wycofane;
  - wartość enuma (`Equipment`, `Difficulty`, `ExerciseKind`, `ExerciseCategory`, `MuscleGroup`), której nie ma
    w `Enums.kt` ostatniego wydanego tagu `v<x>`, wymaga `minApp` wyższego niż `x`; aplikacja, która jej nie zna,
    odrzuca całą bazę;
  - nowe pole z wartością domyślną nie zmienia `format`, zmiana znaczenia istniejącego pola tak.
  Reguły danych sprawdza `tools/baza.py sprawdz`: krok w `testy.yml` przed Gradle'em (względem gałęzi docelowej)
  i `baza.yml` (względem poprzedniego wydania bazy). Programy względem bazy pilnuje `PresetSeedConsistencyTest`,
  a w telefonie jedno i drugie wroga walidacja w domenie.
- **W aplikacji.** O bazę pyta to samo zapytanie co o APK (`releases?per_page=100`), pod tym samym przełącznikiem
  i przyciskiem w Ustawieniach → Aktualizacje, więc sieć dalej tylko na żądanie (D-33). Z listy oba odczyty biorą
  najwyższy numer, nie pierwszy (lista nie idzie za kolejnością wydań), tylko tagi z gołą wersją po prefiksie
  (`X.Y.Z` dla aplikacji, liczba dla bazy) i tylko pliki w stanie `uploaded`; tak każe umowa w `RELEASES.md` strony,
  która zmieniła się po D-33 („pierwsze z listy” wyżej już nie obowiązuje). Numer wyższy niż wczytany daje
  pobranie manifestu, sprawdzenie `format` i `minApp` oraz kartę z liczbą zmian, rozmiarem i przyciskiem „Pobierz bazę”.
  Pobranie idzie przez `.part` z rozmiarem i SHA-256; rozpakowanie do `files/baza/<n>/` przyjmuje tylko `exercises.json`
  i `images/<id>.jpg` w limitach liczby i rozmiaru plików. Walidacja: komplet pól i obrazów, warianty, format id
  i prefiks `custom-` jak w pliku planu, żadne ćwiczenie z bazy w telefonie nie znika ani nie zmienia `kind`, gotowe
  programy tej wersji mają swoje ćwiczenia (reguły `PresetSeedConsistencyTest` przechodzą do domeny, test i aplikacja
  wołają tę samą funkcję). Import idzie przez `SeedImporter`, nigdy w trakcie treningu (wtedy przy następnym starcie).
  Przy starcie wygrywa wyższy numer, wbudowany albo pobrany, a brak katalogu pobranej bazy cofa do wbudowanej. Ścieżka
  obrazu z prefiksem `baza/` to plik w `files/`, bez prefiksu asset jak dziś; model Coila składa z niej jedna funkcja
  w `ui/common`. Po imporcie starsze katalogi `files/baza/` znikają.
- **Narzędzie właściciela.** `python tools/baza.py` stawia stronę na `127.0.0.1` (rozbudowany `przeglad.py`): edycja
  pól, nowe ćwiczenie z ilustracją, paczka z generatora zdjęć (dzisiejsze `--partia`), wycofanie, walidacja na bieżąco
  i podbicie numeru. Ćwiczenie bez ilustracji czeka w szkicach poza `baza/`, jak dziś definicja w `ILLUSTRATED` czeka
  na obraz (D-36). Narzędzie nie commituje i nie pcha, zmiany idą przez `develop` i PR jak kod. Aplikacja nie dostaje
  edytora bazy (zdjęte z zakresu 2026-09-06).
- **Numer i opis wydania bazy (decyzja właściciela z 2026-10-28).** Numer bazy stoi też w `VERSION.md`, pod nagłówkiem
  „# Wersja bazy ćwiczeń” obok numeru aplikacji, a opis każdego wydania bazy to sekcja `## [Baza N]` w `CHANGELOG.md`,
  z której `tools/baza.py paczka` składa opis wydania na stronie. Aplikacja czyta numer z `exercises.json` (w APK
  i w pobranej paczce), więc stoi on w dwóch plikach: `podbij` zmienia oba i dopisuje pusty nagłówek sekcji,
  a zgodność pilnują `sprawdz` i `SeedIntegrityTest`. Nowy numer bez opisu nie przejdzie `sprawdz --wzgledem` ani
  `paczka`; sekcję ma każde wydanie bazy, także wspólne z aplikacją. Numer, który jeszcze nie wyszedł, `podbij` zostawia,
  więc w CHANGELOG-u nie zostają sekcje numerów, które nigdy nie wyszły. O wydaniu aplikacji decyduje odtąd jej numer,
  a nie sama zmiana `VERSION.md` (`testy.yml`; `wydanie.yml` i tak kończy bez wydania, gdy tag już jest). Strona
  projektu pokazuje numer i datę najnowszego wydania bazy pod numerem aplikacji, bez kafelka: bazę pobiera tylko aplikacja.

Odrzucone: osobne repozytorium bazy (programy i ćwiczenia w dwóch miejscach, a APK potrzebuje bazy przy buildzie, więc
submoduł albo pobieranie w buildzie i testach); osobna gałąź na bazę (wymóg właściciela); gotowe programy w bazie
(decyzja właściciela; wymagałyby formatu etapów, `PresetId` jako tekstu, interpretera i przepisania jedenastu programów);
baza na GitHub Pages strony (binaria w repozytorium strony i druga droga obok wydań, które aplikacja już czyta); paczki
różnicowe (4 MB w całości nie uzasadniają stanu po stronie telefonu); pobieranie w tle bez pytania (sieć tylko
na żądanie); pomijanie pojedynczych ćwiczeń z nieznaną wartością (niepełna baza zgubiłaby ćwiczenie programu albo
historii, więc odrzucamy całą); kasowanie ćwiczeń (osierocona historia i plany); numer bazy wyłącznie w `VERSION.md`
(aplikacja czyta go z `exercises.json` w APK i w pobranej paczce, więc musiałby tam trafiać przy buildzie i pakowaniu).

Konsekwencje: pierwszy raz potrzebne jest wydanie aplikacji (1.1.0) z nowym importem i bazą v10 w środku, bo 1.0.0
bazy nie pobierze. Nowy sprzęt, partia, kategoria albo rodzaj celu dalej wymaga APK, a baza z nimi czeka na nie przez
`minApp`. `retiredAt` to nowa kolumna w `exercises` (tekst, `null` dla ćwiczeń w użyciu): Room v6 z migracją
i przypadkiem w `MigrationTest`. PR z samą bazą przechodzi pełne `testy.yml`, bo programy względem ćwiczeń pilnuje
Kotlin; minuty Actions nie są tu argumentem, bo repozytorium najpewniej przejdzie na publiczne (#28). Wydanie bazy
przenosi do `master` wszystko, co czeka na `develop`, a aplikacja wychodzi dalej tylko z podbitego numeru aplikacji
w `VERSION.md`. Nowa
baza zmienia plany układane po niej (kreator, zamienniki, „Nie chcę”); zapisane plany trzymają bloki w bazie danych
i stoją. Repozytorium traci drugą kopię ilustracji. Do poprawy przy wdrożeniu: `docs/SEED.md`, `docs/RELEASE.md`,
`docs/site-page.md`, `docs/DATABASE.md`, `docs/privacy-policy.md` (drugi plik z GitHuba, katalog `files/baza/`),
`docs/guide.md` i `CLAUDE.md`; zmiany widoczne dla użytkownika trafiają do `CHANGELOG.md` pod wersją 1.1.0.

## D-48 Publiczne repozytorium bez historii (2026-11-05)

Kontekst: issue #28 w dawnym repozytorium. Projekt miał przejść na publiczny, ale pierwszy commit niósł prywatny adres
e-mail, historia seedu do v4 zdjęcia z wger na CC BY-SA 4.0, a gałęzie, issue, pull requesty i logi Actions powstawały
z myślą o prywatnym repozytorium. Przepisanie historii w miejscu nie wystarcza: stare commity zostają osiągalne przez
pull requesty, a usunąć je może dopiero GitHub Support.

Decyzja (właściciel, 2026-11-02 i 2026-11-05): nowe publiczne repozytorium `Mysttic/mysttic-trainings` zaczyna od stanu
wydania 1.1.0 jednym commitem, a dawne zostaje prywatne jako `Mysttic/mysttic-trainings-archiwum`. Kod jest na licencji
MIT, a baza ćwiczeń (`baza/`: nazwy, opisy, instrukcje i ilustracje) na CC BY 4.0, autor Mysttic. Na pierwszym commicie
stoją tagi `v1.1.0` i `baza-v10`, bo `wydanie.yml` i `baza.yml` liczą z nich numer i zmiany. Sekrety (klucz podpisu,
`HUB_TOKEN`) przechodzą do nowego repozytorium z tymi samymi wartościami: inny klucz podpisu oznaczałby, że telefony
nie przyjmą aktualizacji. Otwarte issue zakłada się w nowym repozytorium od nowa, bo GitHub nie przenosi ich
z prywatnego repozytorium do publicznego.

Odrzucone: przełączenie dawnego repozytorium na publiczne po przepisaniu historii (adres zostaje w commitach osiągalnych
przez pull requesty, a zdjęcia z wger w historii wymagałyby atrybucji); publikacja dawnego repozytorium bez zmian.

Konsekwencje: zdania o prywatnym kodzie w D-12 i D-33 przestają obowiązywać, ale umowa wydań z D-33 zostaje bez zmian:
wydania jadą na stronę `Mysttic/Mysttic` i stamtąd aplikacja się aktualizuje. Uzasadnienie D-41 (płatne minuty Actions)
znika, bo publiczne repozytorium ma darmowe minuty; zasada szkicu PR-a zostaje, dopóki właściciel jej nie zmieni.
Historia sprzed 1.1.0, wydania w repozytorium kodu do 1.1.0 i dawne issue zostają w archiwum, więc w `CHANGELOG.md`
wersje do 1.0.0 nie mają odnośników. Problemy bezpieczeństwa zgłasza się prywatnie przez GitHub (`docs/SECURITY.md`),
a w repozytorium działają skanowanie sekretów i blokada wypchnięcia z sekretem.
