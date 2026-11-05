package pl.mysttic.trainings.domain.update

import java.util.Locale

/**
 * Reguły sprawdzania aktualizacji (M7, D-33). Czysty Kotlin: porównanie numerów, nazwa pliku w wydaniu
 * i decyzja „czy jest po co pobierać”. Sieć i instalacja siedzą w `data/update`.
 *
 * Pytanie idzie do publicznego repozytorium strony (`Mysttic/Mysttic`), w którym leżą wydania wszystkich
 * projektów, dlatego tag musi nieść prefiks projektu — bez niego aplikacja wzięłaby wydanie cudzego
 * programu za swoje. To jedyne miejsce, w którym aplikacja odzywa się poza urządzenie, i dzieje się to
 * wyłącznie wtedy, gdy użytkownik włączy przełącznik albo sam naciśnie przycisk.
 */
object UpdateCheck {

    /**
     * Identyfikator projektu w nazwach tagów i plików na stronie. Musi zgadzać się z katalogiem strony
     * i z `initDownloads({ tagPrefix })` w jej `index.html`, bo to jedno i to samo umówione słowo.
     */
    const val PROJECT = "mysttic-trainings"

    /** Tag wydania na stronie: `mysttic-trainings-v0.3.1`. */
    const val TAG_PREFIX = "$PROJECT-v"

    /** Strona projektu; adres z opisu wydania i wyjście awaryjne, gdy nie ma pliku dla Androida. */
    const val PAGE = "https://mysttic.github.io/Mysttic/mysttic-trainings/"

    /** Nazwa pliku w wydaniu; ta sama umowa co krok „Nazwa z numerem wersji” w `.github/workflows/wydanie.yml`. */
    fun assetNameFor(version: String): String = "$PROJECT-${version.trim().trimStart('v', 'V')}-android.apk"

    /**
     * Numer wersji z taga wydania; `null`, gdy tag należy do innego projektu albo po prefiksie nie stoi gołe
     * `X.Y.Z`. Umowa strony (`RELEASES.md` w `Mysttic/Mysttic`): samo „zaczyna się od” wzięłoby za nasze
     * wydanie przyszłego projektu o prefiksie zaczynającym się od naszego.
     */
    fun versionFromTag(tag: String?): String? = tag?.trim()
        ?.takeIf { it.startsWith(TAG_PREFIX) }
        ?.removePrefix(TAG_PREFIX)
        ?.takeIf { BARE_VERSION.matches(it) }

    /**
     * Stan pliku wydania, od którego wolno go pobrać. Plik w innym stanie jeszcze się wgrywa, a wydanie
     * z takim plikiem to jeszcze nie aktualizacja, po cichu (ta sama umowa strony).
     */
    const val UPLOADED = "uploaded"

    private val BARE_VERSION = Regex("""\d+\.\d+\.\d+""")

    /**
     * Porównuje numery wersji. Ujemne, gdy [a] jest starsze niż [b].
     * Bierze pod uwagę trzy człony i nic więcej: „-debug” z buildu debugowego ani „+build” nie decydują
     * o tym, czy warto się aktualizować.
     */
    fun compareVersions(a: String, b: String): Int {
        val left = parts(a)
        val right = parts(b)
        for (i in 0..2) {
            val diff = left[i].compareTo(right[i])
            if (diff != 0) return diff
        }
        return 0
    }

    /** Czy wydanie ze strony jest czymś, co warto zaproponować. */
    fun decide(current: String, tag: String?, draft: Boolean, prerelease: Boolean): UpdateOutcome {
        // Szkic i wydanie kandydujące to nie jest coś, co ktoś ma dostać automatycznie.
        if (draft || prerelease) return UpdateOutcome.UP_TO_DATE
        val version = versionFromTag(tag) ?: return UpdateOutcome.FAILED
        if (parts(version).all { it == 0 }) return UpdateOutcome.FAILED
        return if (compareVersions(current, version) < 0) UpdateOutcome.NEWER else UpdateOutcome.UP_TO_DATE
    }

    /** Rozmiar pliku po ludzku: „12,4 MB”. Locale przypięte, bo napis jest polski niezależnie od maszyny. */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes >= 1_000_000 -> String.format(PL, "%.1f MB", bytes / 1_000_000.0)
        else -> String.format(PL, "%d kB", bytes / 1000)
    }

    private val PL: Locale = Locale.forLanguageTag("pl")

    private fun parts(version: String): List<Int> {
        val text = version.trim().trimStart('v', 'V').substringBefore('-').substringBefore('+')
        val numbers = text.split(".").map { it.trim().toIntOrNull() ?: 0 }
        return (numbers + listOf(0, 0, 0)).take(3)
    }
}

/** Czym skończyło się pytanie o wydanie: brak sieci to co innego niż „nic nowego”. */
enum class UpdateOutcome { NEWER, UP_TO_DATE, FAILED }

/** Wydanie sprowadzone do tego, co aplikacja pokazuje i pobiera. */
data class ReleaseInfo(
    /** Numer bez prefiksu i bez „v” z przodu. */
    val version: String,
    /** Opis wydania ze strony: numer i adres strony projektu. */
    val notes: String,
    val pageUrl: String,
    /** Adres APK; `null`, gdy wydanie nie ma pliku o oczekiwanej nazwie. */
    val assetUrl: String?,
    val assetSize: Long,
)

/** Wynik sprawdzenia: przy [UpdateOutcome.NEWER] jest też [release]. */
data class UpdateResult(val outcome: UpdateOutcome, val release: ReleaseInfo? = null) {
    val isNewer: Boolean get() = outcome == UpdateOutcome.NEWER && release != null
}
