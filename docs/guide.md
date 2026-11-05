# Przewodnik użytkownika

Aplikacja ma być prosta w obsłudze: jeden ekran, jeden trening, jeden przycisk. Ten dokument opisuje
wszystko, co da się w niej zrobić, w kolejności, w jakiej zwykle się na to trafia.

## Pierwsze uruchomienie

Po instalacji „Dziś” pokazuje jedno wezwanie: **Ułóż plan**, a pod nim ciche odsyłacze **Gotowy program**
i **Twoje plany**. Bez planu aplikacja nie ma czego proponować, więc zaczynasz od kreatora, od gotowego programu
albo z listy planów, gdzie ułożysz własny plan od zera albo wczytasz plan z pliku od kogoś (opis niżej,
w części „Twoje plany”).

Kreator pyta o:

- **Sylwetkę** — wzrost i wagę. Wartość ustawisz przeciągnięciem po pasku albo klawiszami „−” / „+”
  (przytrzymanie powtarza krok). Z nich wychodzi BMI, a przy wysokim BMI generator odpuszcza ćwiczenia
  plyometryczne (skoki), żeby oszczędzić kolana.
- **Cel** — siła, kondycja, redukcja albo mobilność. Cel decyduje o rodzaju bloków (serie proste kontra
  obwód), długości przerw i tym, jakie ćwiczenia mają pierwszeństwo.
- **Poziom** — początkujący, średniozaawansowany, zaawansowany. Szablony pisane są dla poziomu środkowego;
  pozostałe skalują powtórzenia, czas i przerwy oraz podmieniają warianty ćwiczeń. Poziom trzyma też liczbę
  rund i serii w ryzach (początkujący 2–3, średniozaawansowany 3–4, zaawansowany 3–5): dłuższy trening to
  więcej ćwiczeń i przerw, nie więcej rund tego samego.
- **Ile i jak długo** — dni w tygodniu, długość pojedynczego treningu (co 5 minut; górna granica zależy
  od poziomu: 45, 60 albo 75 minut) i długość cyklu.
- **Sprzęt** — zaznaczasz, co masz w domu. Generator nie zaproponuje niczego, czego nie zaznaczyłeś.

Na dole widać podgląd: ile treningów wypadnie w cyklu i ile to minut tygodniowo. **Ułóż plan** zapisuje cykl,
robi go aktywnym i otwiera jego podgląd; „Wstecz” prowadzi na „Dziś”. Jeśli trwał inny plan, czeka na liście planów
ze swoim postępem.

Plan jest deterministyczny: te same dane wejściowe dają ten sam plan. Jeśli coś w nim nie pasuje, zmień
dane w kreatorze, a nie licz na to, że kolejne kliknięcie wylosuje coś innego.

## Ekran „Dziś”

Jedna karta z następnym treningiem z kolejki: nazwa, liczba ćwiczeń, szacowany czas i **Start**.
Pod spodem dwa odsyłacze: **Co jest w treningu** (pełna lista ćwiczeń z celami) i **Zobacz plan** (cały cykl).

Nad kartą: ile treningów w tym tygodniu, seria dni z rzędu i ostatni trening.

Gdy trening trwa w tle, pojawia się karta **Trening w toku** z powrotem do playera.

Bez planu i po zamknięciu cyklu pod kartą stoi też cichy odsyłacz **Twoje plany** z liczbą planów. W trakcie cyklu
lista planów jest w podglądzie planu (**Zobacz plan**).

## Trening

Odliczanie (domyślnie 5 sekund, do zmiany w Ustawieniach), potem kolejne kroki:

- **Seria na powtórzenia** czeka na ciebie. Duży przycisk **Zrobione (12)** zalicza pełny cel, a minus
  i plus obok zmieniają liczbę, gdy wyszło inaczej. Zamiast tapać można nacisnąć **przycisk głośności** —
  działa tylko wtedy, gdy trening czeka na potwierdzenie serii, więc w przerwie nadal reguluje głośność.
- **Seria na czas** odlicza sama, z beepem na 3-2-1.
- **Krok w rytmie** (oddech Wima Hofa) daje sygnał co trzy sekundy i liczy oddechy; przy wyłączonym dźwięku
  rytm niesie wibracja.
- **Krok bez limitu** (zatrzymanie oddechu) liczy czas w górę; kończysz go przyciskiem **Zakończ** albo klawiszem
  głośności, a wynik w sekundach trafia do historii. Po dziesięciokrotności celu aplikacja sama robi pauzę.
- **Przerwa** odlicza i zapowiada kolejne ćwiczenie. **+15 s** dokłada czasu, **Pomiń** kończy przerwę.

Pod ćwiczeniem widać zdjęcie i instrukcję. Ciężar ustawiony przy ćwiczeniu, wskazówka do pozycji z edytora
treningu i notatka z planu stoją przy celu serii. **Wstecz** nie przerywa treningu: silnik żyje w serwisie,
więc możesz wyjść do innego ekranu albo zgasić ekran, a trening idzie dalej. Powrót: karta na „Dziś”
albo powiadomienie, które ma chronometr i przyciski (Pauza, Zrobione, +15 s, Pomiń).

**Przerwij trening** kończy sesję ze statusem „przerwany”; wykonane serie zostają w historii.

Poziom trudności można zmienić w trakcie (chipy 1/2/3 na górze). Wykonane serie zostają, reszta planu
przelicza się na nowy poziom.

Po ostatnim kroku pojawia się podsumowanie: czas, liczba serii, powtórzenia, sekundy pracy i porównanie
z poprzednim wykonaniem tego treningu. Ćwiczenie pominięte w całości też tam stoi, z dopiskiem „pominięte”.

Ocena wysiłku idzie od 6 do 10 i liczy zapas: przy 6 zostały cztery powtórzenia i więcej, przy 8 dwa, przy 10 żadne.
Pod pigułkami są podpisy od „lekko” do „do oporu”; ponowne tapnięcie czyści ocenę. Tę samą ocenę poprawisz później
w szczegółach sesji w Historii.

Pod każdym ćwiczeniem w podsumowaniu, także pominiętym, są cztery pigułki dla trenera: **Bez zmian**, **Więcej**, **Mniej**
i **Nie chcę**. „Więcej” i „Mniej” przesuwają cel o 2 powtórzenia albo 5 sekund we wszystkich kolejnych seriach
tego ćwiczenia (także w innych treningach cyklu), a kolejne opinie się sumują. „Nie chcę” od razu podmienia
ćwiczenie w twoim cyklu na zamiennik na tę samą partię i nie proponuje go w kolejnych planach. Zapis dzieje się
przy **Zakończ**, więc do tego momentu można zmienić zdanie.

## Plan i progresja

**Zobacz plan** pokazuje cały cykl: dni treningowe, dni odpoczynku i wyróżniony następny trening.
Plan rozliczany jest kolejką, nie datami — jeśli ominiesz dzień, trening czeka, a nie przepada.
Menu **⋮** w prawym górnym rogu podglądu ma akcje planu: statystyki, edycję, kopię, wysyłkę i usunięcie
(opisane w części „Twoje plany”).

Seria dni z rzędu liczy dni, w których trenowałeś, i **przeżywa zaplanowany odpoczynek**: dzień wolny
z planu jej nie kasuje, przerwa poza planem tak.

Gdy w poprzednim wykonaniu wszystkie serie ćwiczenia wyszły na cel, aplikacja proponuje podbicie:
+1 powtórzenie albo +5 sekund, a po dobiciu limitu (20 powtórzeń, 90 sekund) — trudniejszy wariant
ćwiczenia. Propozycja jest widoczna na karcie „Dziś” i w szczegółach treningu; jedno tapnięcie
**Zastosuj** zapisuje nowe cele.

W szczegółach treningu pod każdym ćwiczeniem jest **+ Notatka w tym planie**: wpisz tam na przykład ciężar
albo ustawienie maszyny. Notatka wraca przy tym ćwiczeniu w każdym treningu i tygodniu tego planu, w playerze pod celem
serii i na karcie „Dalej” w przerwie. W innym planie jej nie ma, a kopia planu i plik wysłany komuś zabierają ją ze sobą.

Po ukończeniu całego cyklu z kreatora karta na „Dziś” proponuje **Wygeneruj kolejny cykl**. Kreator startuje
z poprzednich ustawień i pyta, jak poszło: zależnie od frekwencji i kompletu serii kolejny cykl dostaje
cele wyższe, takie same albo niższe. Plan własny i plan od kogoś nie mają progresji do rozliczenia, więc po ich
zakończeniu karta proponuje **Powtórz plan**.

## Gotowe programy

Zamiast kreatora możesz wziąć stały protokół: Tabata, Iron Man, Wyzwanie brzucha, 100 pompek, Siedem minut, Deska
i drążek, Poranny rozruch, Wieczorne rozciąganie, Wyzwanie przysiadów, Burpee co minutę albo Oddech Wima Hofa.
Wybierasz program, długość cyklu i poziom (100 pompek pyta jeszcze, ile pompek robisz z rzędu), a aplikacja układa
etapy: te same ćwiczenia, co jakiś czas więcej. Karta „Dziś” mówi, który to etap i co w nim rośnie. W Burpee co minutę
przerwa to tyle, ile zostało do końca minuty.

Lista idzie od łagodnych do wymagających, a karta programu podaje jego trudność (łagodna, średnia, wysoka). To cecha
samego programu; poziom 1, 2 albo 3 wybierany w następnym kroku ustawia liczby w etapach.

Program liczy się ze sprzętem z Ustawień. W Siedmiu minutach bez ławki wejścia i dipy zastępują ćwiczenia bez sprzętu,
a notatka etapu mówi, co podmieniono. Deska i drążek bez zaznaczonego drążka jest na liście wyszarzona, bo drążka
nie ma czym zastąpić. Zaznaczony później sprzęt działa od następnego założenia programu.

Poziom wchodzi w tabele etapów przy starcie; w trakcie protokół jest stały, więc chipy poziomu, „Podbij cele”
i podmiana „Nie chcę” go nie ruszają („Nie chcę” zadziała od następnego planu z kreatora). Program zmienisz przez
kopię: **Duplikuj i zmień** w menu planu.

Gotowy program wybierzesz w każdej chwili. Aktywny jest zawsze jeden plan, więc gdy trwa inny, aplikacja uprzedza,
że zostanie wstrzymany z postępem, i wrócisz do niego z listy planów. Po domknięciu kolejki karta proponuje
**Powtórz program**, **Ułóż plan** albo **Inny gotowy program**.

Oddech Wima Hofa tylko na siedząco lub leżąco, nigdy w wodzie, na stojąco ani za kierownicą; ostrzeżenie stoi na karcie
programu i na „Dziś”.

## Twoje plany

Aplikacja trzyma listę planów: z kreatora, gotowe programy, własne i od innych osób. Aktywny jest jeden i tylko z niego
„Dziś” podaje trening. Pozostałe czekają ze swoim postępem, więc plan przełączysz w każdej chwili, niczego nie tracąc.

Lista (**Twoje plany**) ma trzy grupy: **Aktywny**, **Czekają** (wstrzymane i jeszcze nierozpoczęte) oraz zwinięte
**Zakończone**. Karta planu mówi, skąd jest (z kreatora, gotowy program, twój plan albo „Od:” z imieniem autora),
w jakim jest stanie i ile treningów z kolejki już za tobą. Tapnięcie otwiera podgląd planu, a na dole listy są
**Ułóż własny plan**, **Wczytaj plan z pliku**, **Kreator** i **Gotowy program**.

W podglądzie planu, który nie jest aktywny, stoi **Ustaw jako aktywny**. Menu **⋮** ma resztę:

- **Statystyki i raport**: przebieg tego planu i raport PDF (niżej).
- **Edytuj plan** i **Zmień nazwę**: dla planu własnego i planu z kreatora. Gotowy program i plan od kogoś zmieniasz
  przez kopię, stąd **Duplikuj i zmień**.
- **Duplikuj**: kopia jako twój plan, nieaktywna, z notatkami do ćwiczeń.
- **Zacznij od nowa**: ten sam plan od pierwszego dnia jako nowy przebieg z osobnymi statystykami; poprzedni zostaje
  na liście.
- **Wyślij plan**: plik dla innej osoby (niżej).
- **Usuń plan**: znika rozkład dni i nieużyte treningi planu. Historia zostaje, statystyk tego planu już nie będzie.

Gdy trening z planu właśnie trwa, planu nie da się zmienić ani usunąć, dopóki trening się nie skończy.

### Własny plan

Poradnik krok po kroku dla osoby, która układa plany dla innych: [poradnik-trenera.md](poradnik-trenera.md).

**Ułóż własny plan** otwiera edytor planu: nazwa, opis (dla kogo, jak ćwiczyć), treningi planu i rozkład dni.
Trening dodajesz jako nowy, kopiujesz z innego planu (**Skopiuj trening**) albo duplikujesz trening tego planu
(**×2** przy treningu; kopia dostaje w nazwie „(kopia)”). Każdy dzień to trening albo odpoczynek;
dni przesuwasz o jeden w górę lub w dół i usuwasz. **Rozpisz tygodnie** układa naraz kilka tygodni (na przykład
4 tygodnie po 3 treningi): treningi idą po kolei (A, B, A…), a reszta dni to odpoczynek. **Kolejny tydzień jak ostatni**
dokłada tydzień na wzór ostatniego.

Przełącznik **Dopasowuj cele do poziomu i trenera** decyduje, czy cele skalują się z poziomem trudności, korektą trenera
i „Podbij cele”, czy zostają dokładnie takie, jak wpisane. W nowym planie jest wyłączony: plan dla kogoś jedzie
z twoimi liczbami, a w planie dla siebie, który ma rosnąć razem z tobą, go włączasz. Dni już zrobione w aktywnym planie
są zablokowane i zostają na swoich numerach. **Zapisz plan** zapisuje całość; nowy plan czeka na liście, aż ustawisz go jako aktywny.

W edytorze treningu ćwiczenie ma cel, ciężar i wskazówkę do pozycji, którą player pokaże w trakcie serii. Blok może mieć
nazwę i przerwę po bloku. **Zmień ćwiczenie** podmienia ćwiczenie z zachowaniem serii i przerw, a **Duplikuj** kopiuje
pozycję albo cały blok.

### Wysyłanie i wczytywanie planu

**Wyślij plan** tworzy plik, który przekażesz komunikatorem albo mailem (**Udostępnij**) albo zapiszesz w Plikach
(**Zapisz do pliku**). Plik niesie nazwę, opis, dni, treningi z seriami, powtórzeniami, czasem, przerwami, ciężarem
i wskazówkami oraz notatki do ćwiczeń. Nie ma w nim twojego wzrostu i wagi, historii treningów, notatek z sesji ani
pamięci trenera. Plan, który u ciebie dopasowuje się do poziomu, jedzie z ćwiczeniami i liczbami, które widzisz na swoim
poziomie i ze swoim sprzętem, a odbiorca dostaje go dokładnie takim, bez skalowania. Podpis autora to imię
z Ustawień → Plany.

Plik od kogoś otworzysz w aplikacji prosto z komunikatora, maila albo Plików („Otwórz za pomocą”) albo przez
**Wczytaj plan z pliku** na liście planów. Podgląd pokazuje treningi, autora, sprzęt, którego nie zaznaczyłeś,
i ćwiczenia, które dojdą do biblioteki. **Dodaj do planów** dopisuje plan do listy; czeka tam, aż ustawisz go jako
aktywny. Jeśli ten plan już masz, podgląd o tym mówi, a dodanie zrobi drugą, osobną kopię.

### Statystyki i raport PDF

**Statystyki i raport** z menu planu pokazują przebieg jednego planu: ile treningów z kolejki zrobione, czas w ruchu,
procent wykonanych serii, średni wysiłek (RPE), przerwane treningi, tygodnie planu na wykresie i ćwiczenia (serie,
trafienie w cel, ciężar, pierwszy i ostatni wynik). Liczby biorą treningi przypisane do dni planu. Trening zrobiony
drugi raz ponad kolejkę widać osobno jako „ponad plan”, a w całości w Historii.

Raport PDF zbiera to samo na stronach A4 z tabelą „Dzień po dniu”. **Udostępnij raport** wysyła go na przykład osobie,
która ułożyła plan, **Zapisz PDF** zapisuje go w Plikach, a **Podgląd raportu** pokazuje pierwsze strony w aplikacji.
Notatki z treningów trafiają do raportu tylko po włączeniu przełącznika **Notatki z treningów w raporcie**. W nagłówku
raportu stoi autor planu i, jako osoba ćwicząca, twoje imię z Ustawień → Plany.

## Ćwiczenia

Zakładka **Ćwiczenia** to biblioteka: kafelki z ilustracjami (przełącznik w nagłówku zmienia je na listę wierszy i pamięta
wybór), wyszukiwarka, przełącznik **Mój sprzęt** i przycisk **Filtry**, który rozwija panel
z sekcjami sprzęt, rodzaj, poziom i partie. W sekcji można zaznaczyć kilka wartości naraz (którakolwiek pasuje), sekcje
zawężają się nawzajem, a licznik na pasku mówi, ile ćwiczeń zostało; **Wyczyść filtry** zeruje panel.
W szczegółach ćwiczenia są dwa zdjęcia (pozycja startowa i docelowa), instrukcja, warianty łatwiejszy
i trudniejszy, zamienniki przy brakującym sprzęcie oraz twoje wyniki: wykres najlepszej serii w czasie
i rekord. Sekcja **Trener** pokazuje korektę celu z twoich opinii (z przyciskiem **Wyzeruj**) i przełącznik
**Nie proponuj w planach**, którym wykluczysz ćwiczenie albo cofniesz „Nie chcę”.

Ergometry (wioślarz, rower stacjonarny, orbitrek, bieżnia) zaznaczasz w Ustawieniach → Sprzęt. Ćwiczenia na nich są na
czas, więc player odlicza minuty; trening na ergometrach układasz w edytorze treningu albo planu, bo kreator ich
nie używa. Czas w edytorze sięga godziny.

## Historia

Lista sesji z filtrami okresu (7 dni, 30 dni, wszystko) i statusu. Karta statystyk pokazuje serię dni,
liczbę treningów, czas, serie i powtórzenia w wybranym okresie. Niżej wykresy: tygodniowy wolumen
i kalendarz aktywności z ostatnich 12 tygodni.

Wpis sesji otwiera szczegóły: wszystkie serie, RPE i notatka do dopisania po fakcie.

Historia pokazuje wszystkie treningi razem, ze wszystkich planów. Przebieg jednego planu jest w jego statystykach
(menu planu → **Statystyki i raport**).

## Ustawienia

- **Poziom trudności** i **mój sprzęt** — te same, których używa kreator i player.
- **Wygląd**: jeden z trzech motywów, „Ogień i Krew” (domyślny), jasny „Papier i Ogień” albo „Stal i Bursztyn”.
  Aplikacja trzyma wybrany motyw i nie przełącza się sama za trybem jasnym czy ciemnym systemu.
  Pod motywami jest **Tło**: jednolite (domyślne) albo jeden z trzech gradientów w kolorach motywu: Żar, Łuna
  i Wino w „Ogniu i Krwi”, Świt, Róża i Pergamin w „Papierze i Ogniu”, Granat, Głębia i Zmrok w „Stali
  i Bursztynie”. Gradient leży pod wszystkimi ekranami, a karty i napisy zostają tak samo czytelne. Każdy motyw
  pamięta swoje tło, więc po powrocie do motywu wraca też jego gradient.
  Ostatnia opcja tła to **Zdjęcie dnia**: rozmyte zdjęcie dobrane do dzisiejszego treningu z planu (brzuch, nogi,
  góra ciała, kardio, joga albo całe ciało, gdy trening jest mieszany albo planu nie ma). W **Na zdjęciach** wybierasz,
  kogo pokazywać: kobietę, mężczyznę albo oboje. Zdjęcie zmienia się raz dziennie i z kolejnym treningiem, a karty
  są przy nim szklane, żeby prześwitywało.
- **Odliczanie przed startem** — 0, 3, 5 albo 10 sekund.
- **Sygnały** — dźwięk, głos (TTS), wibracja, „nie gaś ekranu w trakcie treningu”.
- **Przycisk głośności potwierdza serię** — opisany wyżej skrót; da się wyłączyć.
- **Przypomnienie** — codzienne o wybranej godzinie. Powiadomienie przychodzi tylko wtedy, gdy tego dnia
  nie było jeszcze treningu, a plan ma co podać. Alarm przeżywa restart telefonu.
- **Health Connect** — opcjonalny zapis ukończonych treningów do systemowego dziennika zdrowia. Zapis
  jest jednostronny: aplikacja nic stamtąd nie czyta. Sekcja pojawia się tylko na telefonach,
  które mają Health Connect.
- **Aktualizacje** — patrz niżej.
- **Plany**: twoje imię, którym podpisujesz plany wysłane innym i raporty z przebiegu planu. Puste pole to brak podpisu.
- **Kopia zapasowa**: eksport i import jednego pliku JSON z historią, wszystkimi planami (z autorami i notatkami
  do ćwiczeń), pamięcią trenera, ustawieniami oraz własnymi ćwiczeniami i treningami. Po wczytaniu na nowym telefonie
  aktywny plan wraca tam, gdzie był. Import dokłada brakujące sesje i plany, a pomija te, które już masz: plan, który
  jest w telefonie, zostaje nietknięty razem ze swoimi treningami. Ustawienia wracają z pliku; imię z kopii sprzed
  wersji z listą planów nie kasuje wpisanego. Plik planu od kogoś wczytujesz z listy planów, nie tutaj.

## Aktualizacje

Sekcja **Aktualizacje** pokazuje zainstalowaną wersję i ma dwa sposoby na sprawdzenie nowej:

- przełącznik **Pytaj o nowe wydanie raz dziennie** — aplikacja pyta przy wejściu do Ustawień, nie częściej
  niż raz na dobę,
- przycisk **Sprawdź teraz** — pyta natychmiast.

Zapytanie idzie do publicznego API GitHuba, bez tokenu i bez niczego, co by cię identyfikowało: pyta
o wydania [strony Mysttic](https://mysttic.github.io/Mysttic/mysttic-trainings/), gdzie ląduje gotowy plik.
Gdy jest nowsze wydanie, pojawia się karta z numerem, rozmiarem pliku i opisem.
**Pobierz i zainstaluj** ściąga APK i oddaje go systemowemu instalatorowi — to on pyta o zgodę
i podmienia aplikację. Nic nie instaluje się po cichu. Przy pierwszej takiej instalacji Android poprosi
o pozwolenie na instalowanie z tego źródła, a Google Play Protect może zaproponować skan pliku.

Trzy możliwe odpowiedzi i każda znaczy co innego: karta z numerem (jest nowsza wersja), „Masz najnowszą
wersję” (nie ma po co pobierać) i „Nie udało się sprawdzić” (nie udało się zapytać, zwykle brak sieci).

Historia treningów przeżywa aktualizację, bo baza zostaje na miejscu. Przed większymi zmianami i tak warto
zrobić eksport z sekcji **Kopia zapasowa**.

### Baza ćwiczeń

Opisy i ilustracje ćwiczeń mają własne wydania, niezależne od aplikacji; numer bazy stoi obok wersji aplikacji
(„baza ćwiczeń v10”). To samo pytanie, które szuka nowej wersji aplikacji, szuka też nowszej bazy. Gdy jest,
pojawia się druga karta: numer, ile ćwiczeń przybyło, zmieniło się albo zostało wycofanych, i rozmiar paczki.
**Pobierz bazę** ściąga ją, sprawdza i od razu wczytuje, bez reinstalacji aplikacji. Historia, plany, notatki
i pamięć trenera zostają. Gdy akurat trwa trening, baza czeka i wczytuje się przy następnym uruchomieniu.

Aplikacja odrzuca bazę, która zgubiłaby ćwiczenie z twojej historii albo gotowego programu, i mówi dlaczego;
w telefonie nic się wtedy nie zmienia. Baza przygotowana dla nowszej aplikacji czeka, aż aplikację zaktualizujesz.
Ćwiczenie wycofane z bazy znika z biblioteki, kreatora i zamienników, ale zostaje w historii i zapisanych planach.

## Tablet i ekran w poziomie

Na tablecie i na telefonie obróconym w poziom nawigacja przechodzi z dołu na bok ekranu, a listy i formularze trzymają
czytelną szerokość zamiast rozciągać się na cały ekran. Biblioteka ma więcej kolumn kafelków, a player stawia liczbę
po lewej i przyciski po prawej.

## Czego aplikacja nie robi

- Nie ma konta, logowania ani synchronizacji między telefonami. Przenosisz dane plikiem kopii zapasowej, a pojedynczy
  plan plikiem planu.
- Nie liczy kalorii i nie waży cię — od tego są inne aplikacje i Health Connect.
- Nie pozwala wybierać treningu z listy na „Dziś”. Wybierasz plan (z kreatora, gotowy program, własny albo od kogoś),
  a „Dziś” podaje z niego jeden trening, następny z kolejki. Inny plan włączysz w każdej chwili z listy planów;
  bieżący czeka wtedy ze swoim postępem.
