# Testy

## Co i gdzie

Testy jednostkowe (`app/src/test`) obejmują warstwę `domain/` — czysty Kotlin, bez Androida,
bez Robolectrica, bez zegara systemowego. To tam mieszkają reguły, które da się złamać po cichu:

| Obszar | Co jest sprawdzane |
|---|---|
| `domain/engine` | maszyna stanów playera: odliczanie, beepy 3-2-1, przejścia z timestampów, pauza i wznowienie, odtworzenie z checkpointu, zmiana poziomu w trakcie, okno EMOM (`windowSec`), niezmiennik „każda seria trafia do logu dokładnie raz” |
| `domain/plan` | generator planu (sprzęt jako twardy filtr, BMI a plyometria, trafianie w zadeklarowany czas z dokładnością do minuty, determinizm), rozliczanie kolejki, progresja między cyklami i progresja celów liczona od celu pokazanego z korektą trenera i sprzętem (D-43) |
| `domain/stats` | statystyki okresu, delty per ćwiczenie, seria dni z odpoczynkiem, dane pod wykresy |
| `domain/equipment` | filtry i zamienniki |
| `domain/editor` | operacje edytora na blokach i walidacja |
| `domain/reminder` | najbliższy termin przypomnienia i warunek „czy w ogóle przypominać” |
| `domain/update` | porównanie numerów wersji, decyzja o wydaniu, parsowanie odpowiedzi GitHuba |
| `domain/health` | co kwalifikuje się do zapisu w Health Connect |
| `service` | treść powiadomienia (`NotificationContent`) i zbocze zgody na powiadomienia (`NotificationGate`, D-43), oba czysty Kotlin |
| `ui/theme` | kontrakt wyglądu: pary WCAG w każdym motywie, komplet ról, błąd z innej rodziny odcieni niż akcja, zgodność flagi `dark` z jasnością tła, identyfikatory z DataStore (D-39); każde tło motywu w każdym punkcie gradientu (pary na `surface` i `background`, jasność zgodna z `dark`), warianty i ich identyfikatory (D-50) |
| `ui/player` | saver opinii z podsumowania — czy przeżywają obrót ekranu |
| `data/backup` | format pliku kopii: stara wersja wczytuje się z domyślnymi wartościami, dzisiejsza wraca w całości |
| `data/settings` | tło każdego motywu w DataStore (`BackgroundChoices`, D-50): wybór wraca z zapisu, śmieć z kopii nie wywraca odczytu |
| `data/db` | sprzątanie treningów w SQL na Room w pamięci (Robolectric): `ProgramRepository.delete` i dwa zapytania, które `SeedImporter` woła po aktualizacji (sieroty planów sprzed 0.9.1, wbudowane spoza seedu v7), D-43 |
| `data/seed` | integralność seedu: unikalne id, obraz przy każdym ćwiczeniu, warianty istnieją i trzymają rodzaj celu, id zgodne z plikiem planu, seed bez szablonów treningów (D-43) |

```
gradlew.bat testDebugUnitTest                       # całość
gradlew.bat testDebugUnitTest --tests "*PlanGenerator*"   # jeden zestaw
```

Raport HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.

Zasada z [CLAUDE.md](../CLAUDE.md): zmiana w `domain/` bez testu nie wchodzi. Testów nie dopasowujemy
do kodu, gdy zaczną padać — najpierw sprawdzamy, która strona ma rację.

## Zrzuty motywów bez emulatora (D-40)

`app/src/test/.../ui/screenshot/ThemeScreenshotTest.kt` rysuje jeden ekran ze wszystkim, co niesie kolor
(karta treningu z akcją, karta progresji, statystyki, chipy, linia włosowa, komunikat błędu) w każdym z trzech
motywów i na każdym gradiencie tła (D-50). Robolectric rysuje natywnie, Roborazzi zapisuje PNG — bez telefonu
i bez emulatora.

```
gradlew.bat testDebugUnitTest                              # rysuje, niczego nie porównuje (tyle robi CI)
gradlew.bat testDebugUnitTest -Proborazzi.record=true      # zapisuje wzorce do app/src/test/screenshots
gradlew.bat testDebugUnitTest -Proborazzi.verify=true      # porównuje z wzorcami, czerwieni się przy różnicy
```

Wzorce powstają na maszynie z Android SDK i wchodzą do repozytorium — dopiero wtedy zmiana odcienia widać
w diffie. Do tego czasu test jest wart tyle, co kompilacja i samo rysowanie: łapie wywrotkę motywu, nie zmianę
odcienia. Zrzuty nie zastępują telefonu — nie widać na nich czcionki systemowej, skali tekstu ani tego,
jak ekran świeci po ciemku.

## Czego nie da się złapać testem jednostkowym

UI, foreground service, Room (poza sprzątaniem treningów), powiadomienia, alarmy, Health Connect i instalacja aktualizacji są
sprawdzane ręcznie na emulatorze albo telefonie. Lista rzeczy, które warto przejść przed wydaniem:

1. Pełny trening z planu: odliczanie, seria na powtórzenia (tapnięcie i przycisk głośności), seria
   na czas, przerwa z „+15 s” i „Pomiń”, podsumowanie, wpis w Historii.
2. Trening przy zgaszonym ekranie kilkanaście minut: sygnały, chronometr w powiadomieniu, akcje
   z powiadomienia.
3. Ubicie procesu w trakcie (`adb shell run-as <pakiet> kill -9 <pid>`) i powrót ze stanu z checkpointu.
4. Zmiana poziomu w trakcie treningu: wykonane serie zostają, reszta przelicza się.
5. Kreator: nowy cykl, zgodność szacowanego czasu z zadeklarowanym, brak ćwiczeń na sprzęt, którego nie ma.
6. Kopia zapasowa: eksport, usunięcie danych, import, zgodność historii.
7. Przypomnienie: ustawienie godziny tuż przed obecną, powiadomienie, przestawienie alarmu na kolejny
   dzień, powrót alarmu po restarcie telefonu.
8. Aktualizacja: „Sprawdź teraz”, karta wydania, pobranie i ekran systemowego instalatora.
   Pełną ścieżkę sprawdza się buildem zgłaszającym starszy numer: podmień numer w `VERSION.md`,
   `gradlew.bat installDebug`, przywróć plik. Wtedy „Sprawdź teraz” ma znaleźć wydanie ze strony.
9. Wygląd (D-50): w każdym motywie każde tło z Ustawień. Gradient leży pod wszystkimi ekranami (Dziś, biblioteka
   z panelem filtrów, szczegóły treningu, player z przerwą, edytory, Historia i szczegóły sesji), stoi w miejscu
   przy przewijaniu i przejściu między ekranami, nie układa się w pasy, a karty, pigułki i napisy są czytelne.
   Zmiana motywu i powrót do poprzedniego przywraca jego tło; tablet w poziomie ma gradient także pod szyną.

Na telefonie dochodzi to, czego emulator nie pokaże: słyszalność sygnałów, wibracja, zachowanie przy
agresywnym oszczędzaniu baterii ([dontkillmyapp.com](https://dontkillmyapp.com)) i ikona na launcherze.

### Przebieg treningu na emulatorze automatem

`tools/przebieg.py` przeklikuje punkt 1 z listy wyżej przez adb i zapisuje zrzuty ekranu: puste „Dziś”,
cztery kroki kreatora (początkujący, 2 dni, 15 min, bez sprzętu; przytrzymanie „−” przy wadze pokazuje
powtarzanie kroku), podgląd cyklu, „Dziś” z planem, szczegóły treningu, player (odliczanie, trzy pierwsze serie, przerwa),
podsumowanie z oceną RPE, „Dziś” po treningu, podgląd cyklu i Historia. Serie kończy od razu, przerwy pomija.

```
emulator -avd Pixel_9_Pro            # albo telefon z debugowaniem USB
gradlew.bat installDebug
python tools/przebieg.py             # zrzuty w build/przebieg/, numerowane w kolejności
python tools/przebieg.py --tempo 3   # realne tempo: 3 s na powtórzenie, serie na czas i przerwy do końca (ok. 15 min)
```

Po przebiegu warto przejść ręcznie dwie rzeczy, których skrypt nie robi (D-43):

- EMOM: gotowy program „Burpee co minutę”; runda ma trwać minutę niezależnie od tempa serii, a seria dłuższa niż
  okno ma od razu przechodzić w kolejną rundę.
- Zgoda na powiadomienia nadana w trakcie treningu:
  `adb shell pm grant pl.mysttic.trainings.debug android.permission.POST_NOTIFICATIONS`. Powiadomienie ma wrócić
  w ciągu dwóch sekund, bez czekania na zmianę kroku.

Skrypt czyści dane aplikacji (`pm clear`), więc nie odpalać go na telefonie z historią. Elementy znajduje
po tekście w drzewie dostępności (`uiautomator dump`), więc zmiana etykiety w UI wymaga poprawki w skrypcie;
gdy czegoś nie znajdzie, zostawia `blad.png` i `blad-drzewo.xml` w katalogu wyjściowym.

### Plany do udostępniania i duże ekrany (D-42)

Tego nie złapie test jednostkowy — do przejścia na emulatorze po każdym etapie z `docs/PLAN.md`:

1. Lista planów: kreator, potem gotowy program — pierwszy plan ląduje w „Czekają” z postępem; „Ustaw jako aktywny”
   z potwierdzeniem przełącza z powrotem, a kolejka obu planów zostaje (dzień odhaczony w A nie znika po B → A).
2. Duplikat i „Zacznij od nowa”: kopia ma pusty postęp, usunięcie kopii nie rusza oryginału.
3. Edytor planu: „Ułóż własny plan” (przełącznik „Dopasowuj cele…” wyłączony, D-49), nowy trening A, „×2” przy nim
   (kopia „A (kopia)” na końcu listy, drugie „×2” daje „A (kopia 2)”), kopia przemianowana na B, „Rozpisz tygodnie” 4 × 3,
   zapis, aktywacja, trening dnia 1, potem próba przesunięcia dnia 1 — zapis ma odmówić. W zapisanym planie samo „×2”
   i Wstecz pyta „Porzucić zmiany?”, a „Porzuć” zabiera kopie zrobione w tej edycji. „Skopiuj trening” z planu
   z kreatora przy poziomie początkującym do nowego planu daje kopię z wariantem i liczbami, które pokazuje źródło. `adb shell am kill pl.mysttic.trainings.debug` w trakcie edycji niczego nie gubi
   (szkic w `SavedStateHandle`).
4. Plik planu: „Wyślij plan” → „Zapisz do pliku”, potem na drugim emulatorze (albo po `pm clear`) „Wczytaj plan z pliku”;
   podgląd pokazuje treningi i braki sprzętu, po dodaniu plan czeka nieaktywny, a ponowny import mówi „Masz już ten plan”.
   Plik planu podany w „Importuj kopię” w Ustawieniach daje czytelny błąd. Na telefonie: wysłanie i otwarcie przez
   Messenger, Gmail i Pliki Google (zapisać, z jakim MIME przyszło).
5. Statystyki i PDF: dwie sesje planu, „Statystyki i raport”, „Zapisz PDF”. Plik sprawdzić poza aplikacją:
   `adb pull` i `python -c "d=open('raport.pdf','rb').read(); print(d[:5], d.count(b'/Type /Page'))"` —
   nagłówek `%PDF-` i liczba stron; na podglądzie polskie znaki i pogrubienie nagłówków (oś `wght` Sory).

Duże ekrany sprawdza drugi AVD — ten sam obraz systemu co `Pixel_9_Pro` (API 37, x86_64, 16 KB), profil tabletu:

```
avdmanager create avd -n Pixel_Tablet_API_37 -d pixel_tablet -k "system-images;android-37;google_apis;x86_64"
emulator -avd Pixel_Tablet_API_37 -port 5556
set ANDROID_SERIAL=emulator-5556
python tools/przebieg.py --orientacja land     # 1280 × 800 dp
python tools/przebieg.py --orientacja port
python tools/przebieg_biblioteka.py
```

Skrypty biorą wymiary ekranu z korzenia `uiautomator dump`, więc przewijają dobrze na każdym rozmiarze.
Do obejrzenia na tablecie w obu orientacjach i na `Pixel_9_Pro` w poziomie: szyna z czterema etykietami zamiast
dolnego paska, „Dziś” jako jedna karta nie szersza niż ok. 640 dp, lista planów i szczegóły planu w czytelnej
szerokości, biblioteka w 3–4 kolumnach, player w poziomie z liczbą po lewej i przyciskami po prawej bez przewijania,
obrót i podzielony ekran w trakcie treningu (player biegnie dalej, bo stan żyje w `PlayerController`, D-16).

## CI

`.github/workflows/testy.yml` uruchamia testy jednostkowe i build debugowego APK przy pull requeście
z `develop` do `master`, na Temurin 17. Ten sam job pisze w podsumowaniu, czy PR wyda nową wersję.
Wydanie ma osobny workflow — patrz [RELEASE.md](RELEASE.md).

Czego CI **nie** sprawdzi (świadome zawężenie, żeby nie palić minut Actions). To dwa różne
stany i warto je rozróżniać:

* **Job pominięty, przebieg zielony** — PR do `master` z innej gałęzi niż `develop` oraz PR
  w stanie Draft. Przebieg powstaje, ale warunek `if:` pomija job, a pominięty job GitHub
  pokazuje jako sukces. Zielone kółko nie znaczy wtedy „testy przeszły”, tylko „testy nie
  ruszyły”. Każda zmiana kodu ma iść przez `develop`.
* **Brak przebiegu i brak jakiegokolwiek statusu** — PR ruszający wyłącznie dokumentację
  odciętą filtrem `paths` (`docs/**` z wyjątkiem `docs/DATABASE.md`, oraz `README.md`).
  Filtr ścieżek działa na poziomie wyzwalacza, więc workflow w ogóle nie startuje: nie ma
  przebiegu, nie ma pominiętego joba, nie ma statusu.

`docs/DATABASE.md` jest w tym filtrze wyjątkiem i bieg wyzwala: porównuje go ze schematem Room
`tools/check_schema.py` uruchamiany w tym samym workflow, więc jego zmiana jest zmianą wejścia
CI, a nie samą dokumentacją. Gdyby wyciąć go razem z `docs/**`, literówka w opisie kolumny
przeszłaby do `master` bez żadnego biegu, a przewróciłby się dopiero następny, niewinny PR.

Gdyby `testy` miało kiedyś być **wymaganym statusem** w ochronie gałęzi `master`: PR czysto
dokumentacyjny nie wygeneruje przebiegu, więc wymagany status utknie na „Expected — waiting for
status to be reported” i takiego PR-a nie da się scalić. Trzeba by wtedy albo wyjąć PR-y czysto
dokumentacyjne z wymogu, albo przenieść filtr ścieżek z wyzwalacza na poziom joba lub kroków,
żeby przebieg powstawał i raportował sukces.

Bieg nie zostawia artefaktów: raport z padniętego testu czyta się w logu kroku i w podsumowaniu
biegu, a nie pobiera z zakładki Artifacts — artefakty idą na wspólny dla całego konta limit
magazynu Actions. Żaden workflow w repozytorium nie chodzi też z harmonogramu: CI rusza wyłącznie
z pull requesta (`testy.yml`), z pchnięcia `VERSION.md` na `master` (`wydanie.yml`) albo z ręki
(*Run workflow*).
