# Build i środowisko

## Czego potrzeba

- **JDK 17 lub nowszy.** Na maszynie deweloperskiej używamy JBR z Android Studio, bo osobnego JDK
  na PATH tam nie ma: `set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`
  (w bashu: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`). CI stoi na Temurin 17.
- **Android SDK** z platformą 37. AGP dociąga ją sam, jeśli masz `sdkmanager`.
- **Python 3 z Pillow** — tylko do narzędzi w `tools/` (seed, sygnały, kontrast, wersja).

Wersje bibliotek trzyma `gradle/libs.versions.toml` i pochodzą z zestawu JetNews
(android/compose-samples). Podnosimy je razem, nie pojedynczo.

## Codzienna praca

```
gradlew.bat assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
gradlew.bat installDebug           # instalacja na podłączonym urządzeniu
gradlew.bat testDebugUnitTest      # testy jednostkowe warstwy domain
```

Przy kilku urządzeniach naraz: `set ANDROID_SERIAL=<serial>` (`adb devices` pokaże listę).

Debug ma `applicationIdSuffix = ".debug"` i `versionNameSuffix = "-debug"`, więc stoi na telefonie obok
wersji z wydania i nie podmienia jej.

## Numer wersji

`versionName` i `versionCode` biorą się z [VERSION.md](../VERSION.md) — Gradle czyta ten plik przy każdym
buildzie. `versionCode` to `major * 10000 + minor * 100 + patch`. Numeru nie zmieniamy ręcznie
w `build.gradle.kts`; służy do tego `python tools/bump_version.py 0.2.0`.

## Narzędzia w `tools/`

```
python tools/baza.py               # edytor bazy ćwiczeń w przeglądarce (lokalnie, 127.0.0.1:8770)
python tools/baza.py sprawdz --wzgledem origin/master  # baza ćwiczeń w baza/: reguły i zgodność wstecz (D-47)
python tools/baza.py podbij        # numer bazy +1 w exercises.json i VERSION.md, nagłówek [Baza N] w CHANGELOG
python tools/baza.py partia <zip>  # ilustracje z generatora zdjęć do baza/images/ albo tools/szkice/ (Pillow)
python tools/build_beeps.py        # próbki sygnałów do res/raw
python tools/check_contrast.py     # kontrast par wszystkich schematów z ui/theme/Theme.kt (WCAG), kod 1 przy złamanym progu
python tools/bump_version.py 0.2.0 # podniesienie wersji i zamknięcie sekcji „Niewydane” w CHANGELOG
python tools/release_notes.py 0.2.0  # opis wydania wyciągnięty z CHANGELOG (używa go workflow); --baza N dla bazy
```

## Podpis wydania

Klucz nie leży w repozytorium (`.gitignore` blokuje `*.jks`, `*.keystore`, `keystore.properties`).

```
keytool -genkeypair -v -keystore release.jks -alias trainings -keyalg RSA -keysize 2048 -validity 10000
```

Lokalnie build czyta klucz z `%USERPROFILE%\.gradle\gradle.properties`:

```
trainings.keystore.path=C:/keys/release.jks
trainings.keystore.password=...
trainings.key.alias=trainings
trainings.key.password=...
```

albo ze zmiennych `TRAININGS_KEYSTORE_PATH`, `TRAININGS_KEYSTORE_PASSWORD`, `TRAININGS_KEY_ALIAS`,
`TRAININGS_KEY_PASSWORD`. Bez nich `assembleRelease` daje `app-release-unsigned.apk`, którego nie da się
zainstalować — do testów używaj buildu debugowego.

Utrata keystore = koniec aktualizacji w miejscu: Android przyjmuje tylko APK podpisane tym samym kluczem.
Kopia klucza i haseł powinna leżeć w menedżerze haseł.

## Sekrety w repozytorium (CI)

Workflow wydania potrzebuje czterech sekretów (Settings → Secrets and variables → Actions):

| Sekret | Zawartość |
|---|---|
| `KEYSTORE_BASE64` | plik `.jks` zakodowany base64 (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | hasło do keystore |
| `KEY_ALIAS` | alias klucza (np. `trainings`) |
| `KEY_PASSWORD` | hasło klucza |

Bez nich workflow zatrzyma się z komunikatem, zamiast wydać APK, którego nie da się zainstalować.

## Emulator

Weryfikacja odbywa się na AVD Pixel_9_Pro (API 37). `adb` i `emulator` bywają poza PATH — leżą
w `%LOCALAPPDATA%\Android\Sdk\platform-tools` i `...\emulator`. Obraz z 16 KB rozmiarem strony pokazuje
przy starcie dialog zgodności dla bibliotek x86_64 z zależności AndroidX; to nie jest problem naszego kodu.
