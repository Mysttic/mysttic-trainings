# Bezpieczeństwo

## Zgłoszenia

Projekt jest jednoosobowy. Jeśli trafisz na problem bezpieczeństwa, zgłoś go prywatnie przez
[GitHub Security Advisories](https://github.com/Mysttic/mysttic-trainings/security/advisories/new), a nie
w Issues, bo te są publiczne. Nie publikuj szczegółów, zanim poprawka nie wyjdzie w wydaniu.

## Co aplikacja robi z danymi

Wszystko leży na telefonie: historia treningów w bazie Room, ustawienia w DataStore, zdjęcia ćwiczeń
w assetach APK. Nie ma konta, logowania, serwera ani telemetrii. Szczegóły: [privacy-policy.md](privacy-policy.md).

Jedyne wyjście poza urządzenie to sprawdzenie aktualizacji: zapytanie do publicznego API GitHuba bez
tokenu i bez identyfikatorów, wykonywane tylko wtedy, gdy użytkownik włączy przełącznik albo naciśnie
przycisk. Pobrany APK instaluje systemowy instalator, po zgodzie użytkownika — aplikacja nie podmienia
się po cichu.

## Podpis wydań

APK z wydań jest podpisany kluczem, którego nie ma w repozytorium (`.gitignore` blokuje `*.jks`
i `keystore.properties`). Workflow wydania zatrzymuje się, gdy w repozytorium nie ma sekretów z kluczem,
i sprawdza podpis gotowego pliku przed opublikowaniem. Aktualizacja w miejscu działa tylko wtedy, gdy
kolejne wydania są podpisane tym samym kluczem — utrata keystore znaczy reinstalację u każdego,
kto ma aplikację.

## Uprawnienia, o które prosi aplikacja

| Uprawnienie | Po co |
|---|---|
| `INTERNET` | sprawdzenie aktualizacji i pobranie APK |
| `REQUEST_INSTALL_PACKAGES` | oddanie pobranego APK systemowemu instalatorowi |
| `POST_NOTIFICATIONS` | powiadomienie treningu i przypomnienie |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | trening idący przy zgaszonym ekranie |
| `WAKE_LOCK` | odliczanie i sygnały, gdy ekran jest zgaszony |
| `RECEIVE_BOOT_COMPLETED` | ustawienie przypomnienia po restarcie telefonu |
| `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` (do API 32) | przypomnienie o wybranej godzinie, nie „kiedyś w ciągu godziny” |
| `VIBRATE` | wibracja na koniec kroku |
| `health.WRITE_EXERCISE` | opcjonalny zapis treningu do Health Connect |
