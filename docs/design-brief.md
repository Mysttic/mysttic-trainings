# Brief dla designera: mysttic-trainings

## 1. Co projektujemy

Aplikacja na Androida do treningów w domu dla jednej osoby. Użytkownik raz podaje wzrost, wagę, cel,
poziom, sprzęt i ile minut ma na trening; aplikacja układa cykl na 30 dni i od tej pory pokazuje
**jeden trening na dziś** z jednym przyciskiem Start. Prowadzi przez serie i przerwy (odliczanie,
powtórzenia, beep, głos), zapisuje historię, sama proponuje podniesienie celów i po cyklu generuje
kolejny. Bez konta, bez reklam, bez chmury: dane zostają na telefonie.

Aplikacja działa i jest kompletna funkcjonalnie (wersja 0.1.0). Projektujemy **wygląd i odczucie**,
nie funkcje. Obecne UI to domyślny Material 3 z przyzwoitą paletą, ale wygląda jak formularz
techniczny: płasko, bez charakteru, bez hierarchii, wszystko w tej samej szarej karcie.

## 2. Czego nie zmieniamy (decyzje produktowe)

- **Jeden trening na ekranie.** „Dziś” pokazuje wyłącznie następny trening z kolejki. Żadnych list
  treningów do wybierania, żadnych „polecanych”, żadnego przewijanego katalogu.
- **Wybór tylko w kreatorze.** Cel, poziom, sprzęt i czas ustawia się raz przy zakładaniu cyklu.
- **Plan to kolejka, nie kalendarz.** Pominięty dzień nic nie kasuje, trening czeka. Seria dni
  z rzędu przeżywa zaplanowany odpoczynek.
- **Powtórzenia tam, gdzie mają sens, czas tylko dla izometrii i cardio.** W playerze użytkownik
  potwierdza serię przyciskiem „Zrobione (12)” albo klawiszem głośności.
- **Poziom trudności zmienny w każdej chwili**, także w trakcie treningu.
- **Polski język, jeden użytkownik, jeden telefon.** Bez onboardingu z logowaniem, bez social.

## 3. Co mi się nie podoba dzisiaj

- Wszystko wygląda tak samo: karta na karcie, ten sam odcień tła, ta sama waga tekstu. Oko nie wie,
  gdzie patrzeć.
- Ekran główny nie ma energii. Karta z treningiem jest poprawna, ale nie zachęca, żeby wstać z kanapy.
- Player jest czytelny, ale „inżynierski”: liczba, podpis, zdjęcie, rząd przycisków. Brakuje rytmu
  i poczucia postępu w trakcie treningu.
- Kreator to długi formularz ze stepperami i chipami. Powinien być przyjemniejszy, może krokowy.
- Brak jakiegokolwiek elementu, który byłby „nasz”: ilustracji, charakterystycznej typografii,
  własnego sposobu pokazywania postępu.

## 4. Kierunek

Chcę zobaczyć **2–3 kierunki wizualne** do wyboru, każdy na tych samych trzech ekranach
(Dziś, Player, Kreator), a potem rozwinięcie wybranego na całość.

Odczucie, o które chodzi: spokojna energia, dom, poranek albo wieczór, „ktoś kompetentny pilnuje,
żebym ćwiczył”. Dojrzała aplikacja fitness, nie gra. Ciepło i motywacja bez krzyku.

Czego unikać: gamifikacji (odznaki, konfetti, poziomy „wojownika”), neonów i gradientów z siłowni,
stockowych zdjęć wyrzeźbionych torsów, kolorów „sportowych” (czerwień, elektryczny błękit),
zaokrąglonych pastelowych „wellness” bez wyrazu.

Punkt wyjścia marki, do zachowania lub świadomej zmiany: teal `#0E7C7B` jako kolor główny,
bursztyn `#B7791F` jako akcent, ikona aplikacji z hantlem. Nazwa: mysttic-trainings (w aplikacji
skrót „Treningi”). Ton tekstów: bezpośredni, po polsku, na „ty”, bez wykrzykników.

## 5. Ekrany do zaprojektowania

Każdy w wersji jasnej i ciemnej. Ciemna jest ważna: wieczorne treningi przy słabym świetle.

1. **Dziś** (ekran główny). Stany: brak planu (jedno wezwanie „Ułóż plan”), następny trening
   (nazwa, liczba ćwiczeń, szacowany czas, Start, skróty „Co jest w treningu” i „Zobacz plan”),
   trening w toku (powrót do playera), cykl ukończony („Wygeneruj kolejny cykl”). Nad kartą:
   treningi w tym tygodniu, seria dni z rzędu, ostatni trening. Propozycja progresji
   („Ostatnio wszystko na cel: Deska 40 → 45 s”, przycisk „Podbij cele”).
2. **Kreator planu.** Sylwetka (wzrost, waga, BMI), cel (siła / kondycja / redukcja / mobilność)
   z jednym zdaniem opisu, poziom, dni w tygodniu, długość treningu (co 5 min), długość cyklu,
   sprzęt (lista 10 pozycji do zaznaczania), podgląd („18 treningów w ciągu 30 dni, 4 razy w tygodniu
   po 45 min”), przycisk generowania. Przy kolejnym cyklu dodatkowo pytanie „Jak poszedł poprzedni
   cykl” (łatwiej / tak samo / trudniej) z wyjaśnieniem. Może być krokowy albo jednoekranowy.
3. **Co jest w treningu** (szczegół treningu). Opis, szacowany czas, przełącznik poziomu,
   lista ćwiczeń w blokach (obwód z rundami albo serie proste) z celem („9 powtórzeń”, „40 s”)
   i przerwą, karta propozycji progresji z „Zastosuj”, przyciski „Edytuj trening” i Start.
4. **Player.** Najważniejszy ekran. Kroki: odliczanie, seria na powtórzenia (czeka na „Zrobione (N)”,
   minus i plus), seria na czas (odlicza), przerwa (odlicza, „+15 s”, „Pomiń”, zapowiedź kolejnego
   ćwiczenia). Elementy: nazwa treningu, czas całkowity, „Seria 12 z 48”, pasek postępu, chipy
   poziomu 1/2/3, nazwa ćwiczenia, wielka liczba z jednostką, „seria 2 z 3”, zdjęcie ćwiczenia,
   jedno zdanie instrukcji, przyciski Wstecz / Pauza / Pomiń, „Przerwij trening”. Stan pauzy.
   Karta „Wznowiono przerwany trening. Kontynuować od tego miejsca?”.
5. **Podsumowanie** po treningu: czas, serie, powtórzenia, sekundy pracy, porównanie
   z poprzednim wykonaniem (delty per ćwiczenie), RPE i notatka, „Zakończ”.
6. **Twój plan.** Lista dni cyklu: odhaczone, następny (wyróżniony), odpoczynek. Nagłówek
   z postępem („7 z 18 treningów”). „Wygeneruj kolejny cykl”, „Usuń plan”.
7. **Ćwiczenia** (biblioteka). Szukaj, filtry sprzętu (wszystkie / bez sprzętu / mój sprzęt),
   chipy partii mięśniowych, lista z miniaturą, nazwą, partią, poziomem, sprzętem.
8. **Szczegół ćwiczenia.** Dwa zdjęcia (pozycja startowa i docelowa), instrukcja w 2–4 krokach,
   partie, sprzęt, warianty łatwiejszy i trudniejszy, zamienniki przy brakującym sprzęcie,
   wykres najlepszej serii w czasie, rekord, liczba wykonań.
9. **Historia.** Filtry okresu (7 dni / 30 dni / wszystko) i statusu, karta statystyk (seria dni,
   treningi, czas, serie, powtórzenia), wykres wolumenu tygodniowego, kalendarz aktywności z 12 tygodni,
   lista sesji. Szczegół sesji: wszystkie serie, RPE, notatka, usuwanie.
10. **Ustawienia.** Poziom, sprzęt, odliczanie, sygnały (dźwięk, głos, wibracja, nie gaś ekranu),
    przycisk głośności, przypomnienie (przełącznik i godzina), Health Connect, aktualizacje
    (wersja, przełącznik, „Sprawdź teraz”, karta nowego wydania), kopia zapasowa (eksport, import).
11. **Powiadomienia**: trening w toku (chronometr, akcje Pauza / Zrobione / +15 s / Pomiń),
    przypomnienie „Czas na trening, Góra A · ok. 45 min”. Ikona aplikacji i mała ikona statusu.

Dolna nawigacja: Dziś, Ćwiczenia, Historia, Ustawienia. Można zaproponować inną strukturę, ale
„Dziś” musi zostać pierwszym i domyślnym ekranem.

## 6. Sytuacje użycia, które muszą działać

- **Player z półtora metra.** Telefon leży na podłodze albo stoi oparty o ścianę; użytkownik
  jest w podporze, spocony, zdyszany. Liczba i nazwa ćwiczenia muszą być czytelne z daleka,
  przyciski trafialne bez patrzenia, kontrast wysoki także w ciemnym pokoju.
- **Jedna ręka, mokre palce.** Kluczowe akcje w zasięgu kciuka, duże cele dotyku.
- **Sekundy mają znaczenie.** W przerwie użytkownik patrzy na odliczanie i chce wiedzieć, co dalej.
  Beep i głos są, ale ekran ma to wspierać, nie dublować.
- **Codzienne otwieranie.** Ekran główny widzi się 5 razy w tygodniu przez miesiące; ma zapraszać,
  nie męczyć. Pusty stan (brak planu) widzi się raz i ma prowadzić prosto do kreatora.

## 7. Ograniczenia techniczne

- Android 8.0+, Jetpack Compose, komponenty **Material 3**. Projekt może wyglądać dowolnie,
  ale kolory chcę dostać jako komplet ról Material 3 (primary, onPrimary, primaryContainer,
  onPrimaryContainer, secondary…, surface, surfaceVariant, surfaceContainer*, outline, error itd.),
  osobno dla trybu jasnego i ciemnego. Typografia jako skala Material 3 (displayLarge … labelSmall)
  z podaniem kroju, wagi, rozmiaru, interlinii. Kształty jako promienie zaokrągleń per rozmiar.
- **Kontrast**: tekst 4,5:1, elementy interfejsu 3:1 (WCAG). Mam skrypt, który to liczy, więc
  pary tekst/tło muszą przechodzić w obu motywach.
- **Krój pisma**: może być własny (Google Fonts albo inny z licencją pozwalającą na wbudowanie
  w aplikację). Proszę o wskazanie kroju i wag.
- **Ikony**: dziś tylko zestaw Material Icons (core). Mogę dodać własne ikony jako wektory
  (SVG → VectorDrawable), więc jeśli kierunek wymaga własnego zestawu, proszę o SVG.
- **Zdjęcia ćwiczeń** już są: 81 ćwiczeń, po 1–2 zdjęcia (studyjne fotografie plus kilka
  ilustracji kreskowych), proporcje 3:2. Projekt ma z nimi żyć; nie mam budżetu na nowe sesje,
  ale mogę je jednolicie obrobić (kadr, filtr, maska).
- **Wykresy** rysuję sam na Canvasie (słupki, linia, kalendarz-heatmapa): proszę o styl,
  nie o bibliotekę.
- **Bez animowanych ilustracji 3D i wideo.** Proste animacje przejść i odliczania są mile widziane.
- Wszystkie teksty po polsku. Długie polskie słowa („Średniozaawansowany”, „Wygeneruj kolejny
  cykl”) muszą się mieścić bez łamania w połowie.

## 8. Czego oczekuję na wyjściu

1. 2–3 kierunki (moodboard + Dziś, Player, Kreator w każdym), krótkie uzasadnienie każdego.
2. Po wyborze: system projektowy (kolory jako role Material 3 w obu motywach, typografia, odstępy,
   kształty, elewacje, styl ikon i zdjęć, styl wykresów) oraz komplet ekranów z punktu 5 w obu
   motywach, ze stanami pustymi i błędów.
3. Komponenty wielokrotnego użytku: karta treningu na „Dziś”, wiersz ćwiczenia, blok w szczegółach
   treningu, wielki przycisk akcji w playerze, chipy poziomu, statystyka (liczba + etykieta),
   przełącznik w ustawieniach, karta propozycji progresji.
4. Ikona aplikacji (adaptive: warstwa pierwszego planu i tło) i mała ikona do powiadomień
   (monochromatyczna).
5. Uwagi do mikrotekstów, jeśli obecne brzmią źle w nowym kontekście.

## 9. Załączniki

Obecny wygląd: zrzuty z emulatora (Dziś, Co jest w treningu, Player, Plan, Historia, Kreator,
Health Connect) oraz prezentacja produktu z opisem każdego ekranu. Wszystkie decyzje produktowe
z punktu 2 są ostateczne; wszystko poza nimi jest do dyskusji.
