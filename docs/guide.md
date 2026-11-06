# Przewodnik użytkownika

Aplikacja ma być prosta w obsłudze: jeden ekran, jeden trening, jeden przycisk. Ten dokument opisuje
wszystko, co da się w niej zrobić, w kolejności, w jakiej zwykle się na to trafia.

Dolny pasek ma pięć zakładek z ikonami: **Dziś**, **Plany**, **Ćwiczenia**, **Historia** i **Ustawienia**. Na tablecie
i na telefonie w poziomie te same zakładki stoją w szynie z boku ekranu.

## Pierwsze uruchomienie

Po instalacji „Dziś” pokazuje trzy ilustracje, jedno zdanie i dwa wybory: **Ułóż plan** (kreator) i **Gotowy program**.
Pod nimi cichy przycisk **Własny plan albo plik od kogoś** prowadzi do zakładki **Plany**. Bez planu aplikacja nie ma
czego proponować, więc zaczynasz od kreatora, od gotowego programu albo od zakładki Plany, gdzie ułożysz własny plan
od zera albo wczytasz plan z pliku od kogoś (opis niżej, w części „Plany”). Statystyk na start nie ma: pojawią się
po pierwszym treningu.

Kreator pyta w czterech krokach:

1. **Sprzęt**: zaznaczasz pigułkami, co masz w domu (ta sama lista co **Mój sprzęt** w Ustawieniach). Generator
   weźmie tylko ćwiczenia, które z tym sprzętem zrobisz, a pod pigułkami widać, z ilu ćwiczeń ułoży plan i ile
   zostałoby bez sprzętu. Bez zaznaczenia plan jest z masy własnego ciała.
2. **Sylwetka i poziom**: wzrost i waga przychodzą z **O mnie** w Ustawieniach, a to, co tu poprawisz, wraca tam
   po ułożeniu planu. Wartość ustawisz przeciągnięciem po pasku albo klawiszami „−” / „+” (przytrzymanie powtarza
   krok). Z nich wychodzi BMI, a przy wysokim BMI generator odpuszcza ćwiczenia plyometryczne (skoki), żeby oszczędzić
   kolana. Poziom to początkujący, średniozaawansowany albo zaawansowany. Szablony pisane są dla poziomu środkowego;
   pozostałe skalują powtórzenia, czas i przerwy oraz podmieniają warianty ćwiczeń. Poziom trzyma też liczbę rund
   i serii w ryzach (początkujący 2 do 3, średniozaawansowany 3 do 4, zaawansowany 3 do 5): dłuższy trening to więcej
   ćwiczeń i przerw, nie więcej rund tego samego.
3. **Cel**: siła, kondycja, redukcja albo mobilność. Cel decyduje o rodzaju bloków (serie proste kontra obwód),
   długości przerw i tym, jakie ćwiczenia mają pierwszeństwo.
4. **Ile i jak długo**: dni w tygodniu, długość pojedynczego treningu (co 5 minut; górna granica zależy od poziomu:
   45, 60 albo 75 minut) i długość cyklu.

Ostatni krok streszcza odpowiedzi (sprzęt, sylwetka i poziom, cel): tapnięcie w wiersz wraca do jego kroku, bez
cofania się krok po kroku. **×** w lewym górnym rogu zamyka kreator z każdego kroku.

Na dole widać podgląd: ile treningów wypadnie w cyklu i ile to minut tygodniowo. **Ułóż plan** zapisuje cykl,
robi go aktywnym i otwiera jego podgląd; „Wstecz” prowadzi na „Dziś”. Jeśli trwał inny plan, czeka na liście planów
ze swoim postępem.

Plan jest deterministyczny: te same dane wejściowe dają ten sam plan. Jeśli coś w nim nie pasuje, zmień
dane w kreatorze, a nie licz na to, że kolejne kliknięcie wylosuje coś innego.

## Ekran „Dziś”

Jedna karta z następnym treningiem z kolejki: miniatury jego ćwiczeń, nazwa, liczba ćwiczeń, szacowany czas, pasek
tygodnia i **Start**. Pod przyciskiem dwa ciche odsyłacze: **Co jest w treningu** (pełna lista ćwiczeń z celami;
to samo otwiera tapnięcie w miniatury) i **Zobacz plan** (cały cykl).

Pod kartą: ile treningów w tym tygodniu, seria dni z rzędu i ostatni trening — gdy jest już jakaś historia.

Gdy trening trwa w tle, pojawia się karta **Trening w toku** z powrotem do playera.

Lista wszystkich planów jest w zakładce **Plany**. Na tablecie w poziomie „Dziś” ma dwie kolumny: kartę treningu
po lewej, tydzień i propozycję podbicia celów po prawej.

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

Nad liczbą stoi pozycja (runda albo seria) i nazwa ćwiczenia dużym tytułem, pod przyciskiem **Zrobione** ciche
**Wstecz**, **Pauza** i **Pomiń**.

Pod ćwiczeniem widać zdjęcie i instrukcję. Ciężar ustawiony przy ćwiczeniu, wskazówka do pozycji z edytora
treningu i notatka z planu stoją przy celu serii. **Wstecz** nie przerywa treningu: silnik żyje w serwisie,
więc możesz wyjść do innego ekranu albo zgasić ekran, a trening idzie dalej. Powrót: karta na „Dziś”
albo powiadomienie, które ma chronometr i przyciski (Pauza, Zrobione, +15 s, Pomiń).

Menu **⋮** w prawym górnym rogu ma dwie rzeczy, których w trakcie serii zwykle się nie szuka: poziom trudności
i **Przerwij trening**. Przerwanie kończy sesję ze statusem „przerwany”; wykonane serie zostają w historii.

Poziom trudności zmienisz w każdej chwili (menu ⋮ → Poziom 1, 2 albo 3). Wykonane serie zostają, reszta planu
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

**Zobacz plan** pokazuje cykl: postęp, kartę następnego treningu ze Startem i rozkład tygodniami. Rozwinięty jest
tydzień z następnym treningiem; pozostałe zwijają się do jednej linii („3 treningi · zrobione 2”), a kolejne dni
odpoczynku stoją jednym wierszem. Plan rozliczany jest kolejką, nie datami: jeśli ominiesz dzień, trening czeka,
a nie przepada.
Menu **⋮** w prawym górnym rogu podglądu ma akcje planu: statystyki, edycję, kopię, wysyłkę i usunięcie
(opisane w części „Plany”).

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

Poziom wchodzi w tabele etapów przy starcie; w trakcie protokół jest stały, więc poziom w menu playera, „Podbij cele”
i podmiana „Nie chcę” go nie ruszają („Nie chcę” zadziała od następnego planu z kreatora). Program zmienisz przez
kopię: **Duplikuj i zmień** w menu planu.

Gotowy program wybierzesz w każdej chwili. Aktywny jest zawsze jeden plan, więc gdy trwa inny, aplikacja uprzedza,
że zostanie wstrzymany z postępem, i wrócisz do niego z zakładki Plany. Po domknięciu kolejki karta proponuje
**Powtórz program**, **Ułóż plan** albo **Inny gotowy program**.

Oddech Wima Hofa tylko na siedząco lub leżąco, nigdy w wodzie, na stojąco ani za kierownicą; ostrzeżenie stoi na karcie
programu i na „Dziś”.

## Plany

Aplikacja trzyma listę planów: z kreatora, gotowe programy, własne i od innych osób. Aktywny jest jeden i tylko z niego
„Dziś” podaje trening. Pozostałe czekają ze swoim postępem, więc plan przełączysz w każdej chwili, niczego nie tracąc.

Zakładka **Plany** ma trzy grupy: **Aktywny**, **Czekają** (wstrzymane i jeszcze nierozpoczęte) oraz zwinięte
**Zakończone**. Karta planu mówi, skąd jest (z kreatora, gotowy program, twój plan albo „Od:” z imieniem autora),
w jakim jest stanie i ile treningów z kolejki już za tobą. Tapnięcie otwiera podgląd planu. **Nowy plan** w nagłówku
daje cztery drogi: **Kreator**, **Gotowy program**, **Własny plan** i **Plan z pliku**; bez żadnego planu te same
cztery stoją od razu na liście.

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

**Nowy plan → Własny plan** otwiera edytor planu: nazwa, opis (dla kogo, jak ćwiczyć), treningi planu i rozkład dni.
Trening dodajesz jako nowy, kopiujesz z innego planu (**Skopiuj trening**) albo duplikujesz trening tego planu
(**×2** przy treningu; kopia dostaje w nazwie „(kopia)”). Każdy dzień to trening albo odpoczynek; dni są pogrupowane
tygodniami. Strzałki przy dniu przesuwają go o jeden wcześniej lub później, a menu **⋮** przy dniu ma **Duplikuj dzień**
i **Usuń dzień**. **Rozpisz tygodnie** układa naraz kilka tygodni (na przykład 4 tygodnie po 3 treningi): treningi
idą po kolei (A, B, A…), a reszta dni to odpoczynek. **Kolejny tydzień jak ostatni** dokłada tydzień na wzór ostatniego.

Przełącznik **Dopasowuj cele do poziomu i trenera** decyduje, czy cele skalują się z poziomem trudności, korektą trenera
i „Podbij cele”, czy zostają dokładnie takie, jak wpisane. W nowym planie jest wyłączony: plan dla kogoś jedzie
z twoimi liczbami, a w planie dla siebie, który ma rosnąć razem z tobą, go włączasz. Dni już zrobione w aktywnym planie
są zablokowane i zostają na swoich numerach. **Zapisz plan** zapisuje całość; nowy plan czeka na liście, aż ustawisz go jako aktywny.

W edytorze treningu każdy blok jest kartą z listą ćwiczeń. Tapnięcie w ćwiczenie otwiera jego arkusz: cel, serie,
przerwy, ciężar i wskazówkę, którą player pokaże w trakcie serii, a pod nimi **Zmień ćwiczenie** (z zachowaniem serii
i przerw), **Duplikuj** i **Usuń z bloku**. Kolejność ćwiczeń zmieniasz, przeciągając za uchwyt z lewej. Nazwa bloku,
rodzaj (serie albo obwód), rundy i przerwy czekają w zwiniętym wierszu **Ustawienia bloku**, a menu **⋮** bloku
ma **Duplikuj blok**, przesunięcie wyżej lub niżej i **Usuń blok**.

### Wysyłanie i wczytywanie planu

**Wyślij plan** tworzy plik, który przekażesz komunikatorem albo mailem (**Udostępnij**) albo zapiszesz w Plikach
(**Zapisz do pliku**). Plik niesie nazwę, opis, dni, treningi z seriami, powtórzeniami, czasem, przerwami, ciężarem
i wskazówkami oraz notatki do ćwiczeń. Nie ma w nim twojego wzrostu i wagi, historii treningów, notatek z sesji ani
pamięci trenera. Plan, który u ciebie dopasowuje się do poziomu, jedzie z ćwiczeniami i liczbami, które widzisz na swoim
poziomie i ze swoim sprzętem, a odbiorca dostaje go dokładnie takim, bez skalowania.

Plik jest podpisany twoim imieniem z **Ustawienia → O mnie** (pole **Podpis** na ekranie wysyłki zapisuje je tam
od razu) i kluczem tego telefonu. Klucz powstaje w telefonie przy pierwszym podpisie i nigdy z niego nie wychodzi,
a jego odcisk (pięć grup po cztery znaki) widać w **O mnie**. Kto zmieni w pliku autora albo treść, ten zepsuje podpis
i plik się nie wczyta. Plan od kogoś przekazany dalej, także jego kopia, podaje w pliku swojego autora, a ty jesteś
w nim jako przekazujący. Podpis nie zatrzyma kogoś, kto podpisze plan własnym kluczem pod własnym imieniem, ale wtedy
odcisk będzie inny niż twój: jeśli komuś zależy na pewności, pokaż mu swój odcisk.

**Chroń hasłem** zamyka plik hasłem (co najmniej 6 znaków): bez hasła pliku nie otworzy nikt, także aplikacja, a hasła
nie da się odzyskać. Przekaż je odbiorcy osobno, na przykład SMS-em. Nowy telefon to nowy klucz podpisu; pliki
wysłane wcześniej dalej się wczytują.

Plik od kogoś otworzysz w aplikacji prosto z komunikatora, maila albo Plików („Otwórz za pomocą”) albo przez
**Nowy plan → Plan z pliku** w zakładce Plany. Plik z hasłem najpierw o nie pyta. Podgląd pokazuje treningi,
autora, podpis, sprzęt, którego nie zaznaczyłeś, i ćwiczenia, które dojdą do biblioteki. Przy podpisie zgodnym
z treścią stoi odcisk klucza: jeśli zgadza się z tym, który autor widzi u siebie w **O mnie**, plan jest od niego.
Plik z wcześniejszej wersji aplikacji wchodzi jako „bez podpisu”, a plik zmieniony po podpisaniu się nie wczyta. **Dodaj do planów** dopisuje plan do listy; czeka tam, aż ustawisz go jako
aktywny. Jeśli ten plan już masz, podgląd o tym mówi, a dodanie zrobi drugą, osobną kopię.

### Statystyki i raport PDF

**Statystyki i raport** z menu planu pokazują przebieg jednego planu: ile treningów z kolejki zrobione, czas w ruchu,
procent wykonanych serii, średni wysiłek (RPE), przerwane treningi, tygodnie planu na wykresie i ćwiczenia (serie,
trafienie w cel, ciężar, pierwszy i ostatni wynik). Liczby biorą treningi przypisane do dni planu. Trening zrobiony
drugi raz ponad kolejkę widać osobno jako „ponad plan”, a w całości w Historii.

Raport PDF zbiera to samo na stronach A4 z tabelą „Dzień po dniu”. **Udostępnij raport** wysyła go na przykład osobie,
która ułożyła plan, **Zapisz PDF** zapisuje go w Plikach, a **Podgląd raportu** pokazuje pierwsze strony w aplikacji.
Notatki z treningów trafiają do raportu tylko po włączeniu przełącznika **Notatki z treningów w raporcie**. W nagłówku
raportu stoi autor planu i, jako osoba ćwicząca, twoje imię z Ustawienia → O mnie.

## Ćwiczenia

Zakładka **Ćwiczenia** to biblioteka: kafelki z ilustracjami i nad nimi tylko pole szukania. Lejek w polu (**Filtry**)
rozwija panel: **Tylko mój sprzęt**, sekcje sprzęt, rodzaj, poziom i partie oraz **Widok** (kafelki albo lista wierszy,
wybór zostaje zapamiętany). W sekcji można zaznaczyć kilka wartości naraz (którakolwiek pasuje), sekcje zawężają się
nawzajem, a liczba zaznaczeń stoi przy lejku. Gdy coś zawęża listę, nad nią pojawia się licznik („41 z 283”)
i **Wyczyść filtry**.
Szczegóły ćwiczenia pokazują ilustrację, partie i sprzęt (brakujący na czerwono) i instrukcję. Reszta czeka
w zwijanych wierszach ze streszczeniem: **Twoje wyniki** (wykres najlepszej serii w czasie i rekord), **Łatwiej
i trudniej**, **Zamienniki** przy brakującym sprzęcie i **Trener** — korekta celu z twoich opinii (z przyciskiem
**Wyzeruj**) i przełącznik **Nie proponuj w planach**, którym wykluczysz ćwiczenie albo cofniesz „Nie chcę”.
Na tablecie w poziomie lista i szczegół stoją obok siebie: tapnięcie kafelka otwiera ćwiczenie w panelu po prawej.

Ergometry (wioślarz, rower stacjonarny, orbitrek, bieżnia) zaznaczasz w Ustawieniach → Mój sprzęt → Ergometry. Ćwiczenia na nich są na
czas, więc player odlicza minuty; trening na ergometrach układasz w edytorze treningu albo planu, bo kreator ich
nie używa. Czas w edytorze sięga godziny.

## Historia

Przed pierwszym treningiem Historia mówi tylko, że jeszcze nic tu nie ma. Potem pokazuje okres (7 dni, 30 dni,
wszystko) i jego statystyki: serię dni, liczbę treningów, czas, serie i powtórzenia. Wykresy, czyli tygodniowy wolumen
i kalendarz aktywności z ostatnich 12 tygodni, czekają w zwiniętym wierszu **Postęp**. Nad listą wybierasz
**Wszystkie**, **Ukończone** albo **Przerwane**, a sesje idą miesiącami.

Wpis sesji otwiera szczegóły: czas, serie i powtórzenia, porównanie z poprzednim razem, zwinięta lista wszystkich serii
oraz ocena RPE i notatka do dopisania po fakcie (**Zapisz ocenę**). Sesję usuniesz z menu **⋮**.

Historia pokazuje wszystkie treningi razem, ze wszystkich planów. Przebieg jednego planu jest w jego statystykach
(menu planu → **Statystyki i raport**).

Ustawienia to siedem kategorii; wiersz każdej mówi, co jest w środku ustawione, a tapnięcie otwiera ją jako podstronę
(na tablecie w poziomie lista i kategoria stoją obok siebie):

- **O mnie** — imię, którym podpisujesz plany wysłane innym i raporty z przebiegu planu, płeć (na zdjęciu dnia są
  osoby twojej płci, a bez wyboru obie; plan i cele od niej nie zależą), wzrost i waga (od nich startuje kreator,
  a to, co w nim poprawisz, wraca tutaj) oraz **klucz podpisu**: odcisk klucza, którym telefon podpisuje plany.
- **Trening** — poziom trudności (ten sam, którego używa kreator i player), **odliczanie przed startem** (0, 3, 5 albo
  10 sekund), **sygnały** (dźwięk, głos TTS, wibracja, „nie gaś ekranu w trakcie treningu”) i **przycisk głośności**
  potwierdzający serię (opisany wyżej skrót; da się wyłączyć).
- **Mój sprzęt** — sprzęt domowy, a pod nim ergometry.
- **Przypomnienie** — codzienne o wybranej godzinie. Powiadomienie przychodzi tylko wtedy, gdy tego dnia
  nie było jeszcze treningu, a plan ma co podać. Alarm przeżywa restart telefonu.
- **Wygląd**: jeden z czterech motywów, „Ogień i Krew” (domyślny), jasny „Papier i Ogień”, „Stal i Bursztyn”
  albo jasny „Brzoza i Mech”. Aplikacja trzyma wybrany motyw i nie przełącza się sama za trybem jasnym czy ciemnym
  systemu. Pod ekranami zawsze leży **zdjęcie dnia**: rozmyte zdjęcie w klimacie dzisiejszych treningów. Aplikacja
  patrzy na treningi dnia ze wszystkich planów, w których ćwiczysz (zrobione dziś i czekające w kolejce), bierze trzy
  tematy, których jest najwięcej (brzuch, nogi, góra ciała, całe ciało, kardio, joga), i losuje jeden z nich, częściej
  ten liczniejszy. Zdjęcie losuje się raz dziennie i nic w aplikacji nie mówi, jaką partię dziś ćwiczysz; osoby na nim
  są twojej płci z **O mnie**. **Tło** wybiera, co leży na zdjęciu: samo zdjęcie (domyślnie) albo jeden z trzech
  gradientów w kolorach motywu: Żar, Łuna i Wino w „Ogniu i Krwi”, Świt, Róża i Pergamin w „Papierze i Ogniu”, Granat,
  Głębia i Zmrok w „Stali i Bursztynie”, Mgła, Paproć i Wrzos w „Brzozie i Mechu”. Gradient zabarwia zdjęcie, ale go
  nie zasłania. Karty są szklane, żeby zdjęcie prześwitywało, a napisy zostają tak samo czytelne. Każdy motyw pamięta
  swoje tło.
- **Dane i kopia** — kopia zapasowa, **Health Connect** (opcjonalny, jednostronny zapis ukończonych treningów
  do systemowego dziennika zdrowia; tylko na telefonach, które go mają).
- **Aktualizacje** — patrz niżej; gdy jest nowa wersja, wiersz kategorii mówi o tym na liście.
- **Dane i kopia → Kopia zapasowa**: eksport i import jednego pliku JSON z historią, wszystkimi planami (z autorami i notatkami
  do ćwiczeń), pamięcią trenera, ustawieniami oraz własnymi ćwiczeniami i treningami. Po wczytaniu na nowym telefonie
  aktywny plan wraca tam, gdzie był. Import dokłada brakujące sesje i plany, a pomija te, które już masz: plan, który
  jest w telefonie, zostaje nietknięty razem ze swoimi treningami. Ustawienia wracają z pliku; imię z kopii sprzed
  wersji z listą planów nie kasuje wpisanego. Plik planu od kogoś wczytujesz z zakładki Plany, nie tutaj.

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
czytelną szerokość zamiast rozciągać się na cały ekran. Na tablecie w poziomie Ćwiczenia, Historia i Ustawienia stoją
jako lista i szczegół obok siebie, a „Dziś”, podgląd planu, kreator i edytor planu mają dwie kolumny. Biblioteka
w pionie ma więcej kolumn kafelków, a player stawia liczbę po lewej i przyciski po prawej.

## Czego aplikacja nie robi

- Nie ma konta, logowania ani synchronizacji między telefonami. Przenosisz dane plikiem kopii zapasowej, a pojedynczy
  plan plikiem planu.
- Nie liczy kalorii i nie waży cię — od tego są inne aplikacje i Health Connect.
- Nie pozwala wybierać treningu z listy na „Dziś”. Wybierasz plan (z kreatora, gotowy program, własny albo od kogoś),
  a „Dziś” podaje z niego jeden trening, następny z kolejki. Inny plan włączysz w każdej chwili z listy planów;
  bieżący czeka wtedy ze swoim postępem.
