# Prywatność

Krótka wersja: aplikacja nie ma konta, serwera ani telemetrii. Wszystko, co o tobie wie, leży na twoim
telefonie i wyjeżdża stamtąd tylko wtedy, gdy sam je wyeksportujesz.

## Co aplikacja przechowuje

| Dane | Gdzie | Kiedy znikają |
|---|---|---|
| Profil z kreatora (wzrost, waga, cel, poziom, sprzęt, czas sesji) | baza aplikacji (Room), w pamięci prywatnej | z odinstalowaniem albo usunięciem planu |
| Plan treningowy i historia sesji (serie, powtórzenia, czas, RPE, notatki) | baza aplikacji | z odinstalowaniem albo usunięciem wpisu w Historii |
| Ustawienia (poziom, sprzęt, sygnały, przypomnienie, przełączniki) | DataStore, pamięć prywatna | z odinstalowaniem |
| Zdjęcia i opisy ćwiczeń | assety w APK | nie są danymi użytkownika |
| Pobrana baza ćwiczeń (opisy i ilustracje, D-47) | `files/baza/<numer>/` w pamięci prywatnej | przy wczytaniu kolejnej bazy albo odinstalowaniu; nie są danymi użytkownika |
| Pobrany plik aktualizacji | `files/updates/` w pamięci prywatnej | przy kolejnym pobraniu albo odinstalowaniu |

Pamięć prywatna aplikacji jest niedostępna dla innych aplikacji na niezrootowanym telefonie.

## Co wychodzi poza telefon

Jedna rzecz: **sprawdzenie aktualizacji**. Aplikacja pyta `https://api.github.com/repos/Mysttic/Mysttic/releases`
o listę wydań strony projektu. Gdy zdecydujesz się aktualizować, pobiera z GitHuba plik APK. Gdy na stronie jest
nowsza baza ćwiczeń, pobiera jej mały opis (manifest), a po „Pobierz bazę” paczkę z opisami i ilustracjami ćwiczeń.

- Zapytanie idzie **bez tokenu**, bez identyfikatora urządzenia i bez czegokolwiek o twoich treningach.
- GitHub, jak każdy serwer HTTPS, widzi adres IP, czas zapytania i nagłówek `User-Agent`
  (`mysttic-trainings`). Tyle i nic więcej.
- Dzieje się to wyłącznie wtedy, gdy włączysz przełącznik „Pytaj o nowe wydanie raz dziennie” albo
  naciśniesz „Sprawdź teraz”. Domyślnie przełącznik jest wyłączony i aplikacja nie odzywa się nigdzie.

Poza tym aplikacja nie ma żadnych połączeń: brak reklam, brak analityki, brak crash reportingu,
brak synchronizacji.

## Health Connect

Jeśli włączysz zapis do Health Connect, po każdym ukończonym treningu dłuższym niż minuta aplikacja
dopisuje tam jeden wpis: czas rozpoczęcia i zakończenia, rodzaj wysiłku i nazwę treningu. Nic nie czyta
z Health Connect. Dane trafiają do systemowego magazynu na telefonie, a to, kto ma do niego dostęp,
ustawiasz w Health Connect, nie tutaj. Przełącznik jest domyślnie wyłączony i możesz cofnąć zgodę
w każdej chwili w ustawieniach systemu.

## Kopia zapasowa

Eksport tworzy plik JSON z historią, ustawieniami i twoimi ćwiczeniami. Miejsce zapisu wybierasz sam
przez systemowe okno; od tego momentu za ten plik odpowiadasz ty. Import czyta plik, który mu wskażesz.

## Plany i raporty wysyłane innym

Plan wysłany innym (D-42) to plik JSON z nazwą, opisem, dniami, treningami i opisami użytych ćwiczeń,
podpisany imieniem z Ustawień, jeśli je wpiszesz. Nie ma w nim wzrostu, wagi, historii treningów, notatek
ani pamięci trenera. Raport PDF z przebiegu planu zawiera frekwencję, czasy, serie, RPE i — tylko gdy
zaznaczysz przełącznik — notatki z podsumowań. Oba pliki powstają wyłącznie na twoje żądanie i wychodzą
przez systemowe okno udostępniania albo do miejsca, które wskażesz; aplikacja sama ich nigdzie nie wysyła.
Kopie robocze w pamięci podręcznej aplikacji są kasowane po dobie. Plan od kogoś wczytujesz sam, a przed
zapisem widzisz, co w nim jest.

## Kopia systemowa Androida

Aplikacja ma domyślnie włączoną kopię zapasową Androida (`allowBackup`), więc dane mogą trafić do kopii
robionej przez system, jeśli masz ją włączoną na telefonie. Wyłączenie jej w ustawieniach systemu
wyłącza też kopię tej aplikacji.
