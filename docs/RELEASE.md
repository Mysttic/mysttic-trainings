# Wydania

Wydanie powstaje z jednej rzeczy: podniesionego numeru aplikacji w [VERSION.md](../VERSION.md), który trafił
do `master`. Założenie taga nie uruchamia niczego, scalenie samej poprawki kodu czy dokumentacji też
nie: trigger `push` patrzy wyłącznie na `VERSION.md`. Ten plik niesie też numer bazy ćwiczeń, ale jego
podbicie aplikacji nie wydaje: workflow rusza, widzi istniejący tag i kończy bez wydania.

Zostaje jeszcze droga ręczna: *Actions → Wydanie → Run workflow* na `master`. Workflow ma
`workflow_dispatch`, a bramki są dwie: czy tag `v<numer>` już istnieje (wtedy bieg kończy się bez
wydawania) i czy numer w `VERSION.md` nie cofnął się poniżej najwyższego wydanego tagu (wtedy bieg
jest czerwony). Poza tym ręczne odpalenie wyda wersję dokładnie tak samo jak scalenie. Jest po to, żeby dokończyć wydanie, które przewróciło się
z powodu niezależnego od kodu; normalną drogą zostaje PR do `master`.

Powtórka nieudanego biegu (*Re-run jobs*) to co innego niż nowe odpalenie: bieg wraca z definicją
workflow z commita, na którym powstał. Po zmianie w `wydanie.yml` powtórka nadal robi to, co robiła
stara wersja pliku — poprawkę widać dopiero w nowym biegu.

## Przebieg

1. Praca na `develop`, zmiany widoczne dla użytkownika lądują w sekcji **Niewydane**
   w [CHANGELOG.md](../CHANGELOG.md).
2. `python tools/bump_version.py 0.2.0` — podnosi numer i zamyka sekcję „Niewydane” datą.
   Skrypt odmówi, gdy numer nie rośnie albo gdy nie ma czego wydać.
3. Commit, PR z `develop` do `master`. Workflow **Testy** (`.github/workflows/testy.yml`) uruchamia testy
   jednostkowe i build debugowego APK, a w podsumowaniu pisze, czy ten PR wyda wersję i z jakim opisem.
4. Scalenie PR-a. Workflow **Wydanie** (`.github/workflows/wydanie.yml`) rusza, bo w zmianach był
   `VERSION.md`, i po kolei:
   - czyta numer i sprawdza, czy CHANGELOG ma dla niego sekcję (inaczej stop),
   - sprawdza, czy numer nie jest niższy od najwyższego wydanego tagu (cofnięty numer = czerwony bieg),
   - sprawdza, czy tag `v<numer>` już nie istnieje (jeśli tak, kończy bez wydawania),
   - buduje `assembleRelease` z kluczem z sekretów i weryfikuje podpis `apksigner`em,
   - tworzy wydanie `v<numer>` z plikiem `mysttic-trainings-<numer>-android.apk` i opisem z CHANGELOG-a,
   - kopiuje ten plik na stronę: wydanie `mysttic-trainings-v<numer>` w publicznym `Mysttic/Mysttic`,
     z opisem złożonym z numeru i adresu strony, i kasuje starsze wydania tego projektu (D-33).

Nazwa pliku jest częścią umowy: szukają jej `UpdateCheck.assetNameFor` w aplikacji i skrypt strony
(po końcówce `-android.apk`). Zmiana nazwy w workflow = zmiana w aplikacji i na stronie.

Prefiks `mysttic-trainings-` w tagu na stronie też jest umową: w `Mysttic/Mysttic` leżą wydania wszystkich
projektów, więc bez prefiksu aplikacja wzięłaby cudze wydanie za swoje.

## Co dostaje użytkownik

Stronę <https://mysttic.github.io/Mysttic/mysttic-trainings/> z przyciskiem pobierania i wydanie
`mysttic-trainings-v<numer>` w publicznym `Mysttic/Mysttic`. Na stronie stoi zawsze jedna wersja: najnowsza.
Pełna historia wydań zostaje w repozytorium kodu jako `v<numer>`, od 1.1.0; starszych tagów i wydań
tu nie ma (D-48).

Instalacja pierwszej wersji jest ręczna (sideload). Kolejne wersje aplikacja umie pobrać sama:
Ustawienia → Aktualizacje → „Sprawdź teraz”, a potem „Pobierz i zainstaluj”, które oddaje plik
systemowemu instalatorowi.

Kopie zainstalowane przed 0.3.0 pytały prywatne repozytorium kodu, które odpowiada 404 każdemu bez
tokenu. One aktualizacji nie zobaczą — 0.3.0 trzeba na nie założyć ręcznie, ze strony. To jednorazowy
koszt zmiany adresu i dlatego adres, schemat tagów i nazwy plików lepiej mieć ustalone przed
pierwszym publicznym wydaniem.

## Sprawdzenie gotowego wydania

Po opublikowaniu warto zerknąć na plik z wydania, a nie tylko na zielony bieg:

```
apksigner verify -v --print-certs mysttic-trainings-<wersja>-android.apk
aapt2 dump badging mysttic-trainings-<wersja>-android.apk | head -1
```

Podpis musi mieć ten sam odcisk SHA-256 co poprzednie wydania (`3e2c0130c91fea9ed0b4573d37cf8c72878a2dbf73a4ebf92cb5d41d552da742`),
`versionName` musi zgadzać się z `VERSION.md`, a `versionCode` rosnąć. Wydanie release ma włączony R8,
więc pierwsze uruchomienie po instalacji jest częścią sprawdzenia: brak reguł `keep` widać dopiero tam.

## Klucz i sekrety

Podpis, keystore i sekrety w repozytorium opisuje [building.md](building.md). Skrót: bez
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` i `KEY_PASSWORD` workflow wydania zatrzyma się
z czytelnym błędem, zamiast opublikować plik, którego nie da się zainstalować.

Kopia na stronę potrzebuje piątego sekretu, `HUB_TOKEN`: domyślny `GITHUB_TOKEN` sięga wyłącznie
tego repozytorium, w którym działa workflow. Token robi się na
<https://github.com/settings/personal-access-tokens> jako fine-grained: właściciel `Mysttic`, dostęp
ograniczony do repozytorium `Mysttic/Mysttic`, uprawnienie **Contents: Read and write**. Wpisuje się
go w *Settings → Secrets and variables → Actions → New repository secret* pod nazwą `HUB_TOKEN`.

Token ma datę ważności. Gdy wygaśnie, job „Kopia na stronę” zatrzyma się z czytelnym błędem,
a wydanie w repozytorium kodu i tak powstanie — do naprawy zostaje wtedy sama kopia.

Opis strony projektu (sekcje, teksty, umowa o linkach) leży w [site-page.md](site-page.md).

## Wycofanie wydania

Usunięcie wydania i tagu na GitHubie plus `git revert` commitu z podniesioną wersją. Numer raz wydany
nie wraca — kolejna próba to następny numer, bo Android nie przyjmie APK z `versionCode` nie wyższym
niż zainstalowany.

## Wydanie bazy ćwiczeń (D-47)

Baza ćwiczeń (`baza/`) ma własny numer (`version` w `baza/exercises.json`, ten sam pod „# Wersja bazy
ćwiczeń” w `VERSION.md`) i własne wydania, niezależne od numeru aplikacji. Opis każdego wydania bazy to sekcja
`## [Baza <numer>]` w `CHANGELOG.md`. Workflow **Baza** (`.github/workflows/baza.yml`) rusza, gdy do `master` trafia zmiana w `baza/`,
i ręcznie (*Actions → Baza → Run workflow*), żeby dokończyć przewrócone wydanie; poza `master` robi tylko próbę.

Przebieg:

1. Zmiana w bazie na `develop` (edytor `python tools/baza.py` albo wprost w plikach), podbity numer
   (`python tools/baza.py podbij`: `baza/exercises.json`, `VERSION.md` i nagłówek w `CHANGELOG.md`) i opis
   w sekcji `## [Baza <numer>]`. Krok „Reguły bazy ćwiczeń” w `testy.yml` sprawdza przy PR-ze względem `master`,
   przed Gradle'em, reguły bazy, zgodność numeru w obu plikach i to, że nowy numer ma opis.
2. Scalenie PR-a do `master`. **Baza** po kolei:
   - czyta numer i sprawdza, czy tag `baza-v<numer>` już istnieje: ta sama treść kończy bieg bez wydania,
     inna treść to czerwony bieg (zmiana bez podbicia numeru);
   - sprawdza reguły bazy względem poprzedniego wydania bazy (przed pierwszym względem ostatniego `v*`);
   - składa paczkę: `mysttic-trainings-baza-<numer>.zip` (`exercises.json` i `images/`) i manifest
     `mysttic-trainings-baza-<numer>.json` (numer, `format`, `minApp`, liczba ćwiczeń, nowe, zmienione,
     wycofane, rozmiar i SHA-256 ZIP-a); ta sama treść daje ten sam ZIP;
   - tworzy wydanie `mysttic-trainings-baza-v<numer>` w publicznym `Mysttic/Mysttic` bez znacznika „Latest”
     (sekret `HUB_TOKEN`, ten sam co dla APK), z opisem: numer, sekcja `[Baza <numer>]` z `CHANGELOG.md`,
     liczby zmian, najniższa wersja aplikacji;
   - zakłada tag `baza-v<numer>` w repozytorium kodu (dopiero po udanym wydaniu, więc powtórka po błędzie
     go nie widzi);
   - kasuje starsze wydania `mysttic-trainings-baza-v` na stronie.

Prefiks `mysttic-trainings-baza-v` nie zaczyna się od `mysttic-trainings-v`, więc wydania bazy nie łapie
`UpdateCheck` w aplikacji, skrypt strony ani sprzątanie w `wydanie.yml`, a tag `baza-v<numer>` nie wchodzi
w bramkę numeru APK. Tag i nazwy plików to umowa z aplikacją, która pobiera bazę: zmiana jednego = zmiana w obu.

Scalenie z samą bazą przenosi do `master` wszystko, co czeka na `develop`, ale aplikacji nie wydaje: ta wychodzi
tylko z podbitego numeru aplikacji w `VERSION.md`. Scalenie z jednym i drugim uruchamia oba workflowy naraz.

Wycofanie wydania bazy: usunięcie wydania na stronie i tagu `baza-v<numer>`, a poprawka idzie jako następny
numer. Numer raz wydany nie wraca, bo telefon, który go wczytał, nie przyjmie bazy o tym samym numerze.
