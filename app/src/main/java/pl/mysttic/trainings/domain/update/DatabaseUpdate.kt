package pl.mysttic.trainings.domain.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Wydania bazy ćwiczeń (D-47). Czysty Kotlin: ta sama lista wydań strony co przy aktualizacji APK
 * (`GET /repos/Mysttic/Mysttic/releases`), ale inny prefiks taga. Umowa z `.github/workflows/baza.yml`
 * i `tools/baza.py paczka`: tag `mysttic-trainings-baza-v<numer>`, pliki `mysttic-trainings-baza-<numer>.json`
 * (manifest) i `.zip`. Zmiana nazw tutaj = zmiana w obu tamtych miejscach.
 */
object DatabaseUpdate {

    const val TAG_PREFIX = "${UpdateCheck.PROJECT}-baza-v"

    /** Najwyższy format bazy, który ta wersja aplikacji umie wczytać (`SeedFile.format`). */
    const val SUPPORTED_FORMAT = 1

    /** Pobrana baza leży w `files/baza/<numer>/`; ścieżka obrazu z tym prefiksem to plik, nie asset. */
    const val IMAGE_PREFIX = "baza/"

    fun manifestName(version: Int): String = "${UpdateCheck.PROJECT}-baza-$version.json"

    fun archiveName(version: Int): String = "${UpdateCheck.PROJECT}-baza-$version.zip"

    /** Numer bazy z taga; null dla wydań aplikacji, innych projektów i numerów, które nie są gołą liczbą. */
    fun versionFromTag(tag: String?): Int? = tag?.trim()
        ?.takeIf { it.startsWith(TAG_PREFIX) }
        ?.removePrefix(TAG_PREFIX)
        ?.takeIf { BARE_NUMBER.matches(it) }
        ?.toIntOrNull()

    private val json = Json { ignoreUnknownKeys = true }
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val BARE_NUMBER = Regex("""[1-9]\d*""")

    /**
     * Najnowsze wydanie bazy z listy wydań: najwyższy numer z prefiksem bazy, bez szkiców i wydań kandydujących,
     * a nie pierwsze z listy (jak w [ReleaseParser], umowa w `RELEASES.md` strony). Null, gdy lista bazy nie ma,
     * gdy jej pliki jeszcze się wgrywają albo gdy nie da się jej odczytać; to nie jest błąd, aplikacja ma bazę w APK.
     */
    fun parseRelease(body: String): DatabaseRelease? {
        val list = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return null
        val (version, release) = list.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            .filter { it.flag("draft") != true && it.flag("prerelease") != true }
            .mapNotNull { candidate -> versionFromTag(candidate.text("tag_name"))?.let { it to candidate } }
            .maxByOrNull { it.first }
            ?: return null
        val assets = (release["assets"] as? JsonArray)?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()
        val manifest = assets.firstOrNull { it.text("name") == manifestName(version) }
        val archive = assets.firstOrNull { it.text("name") == archiveName(version) }
        if (listOfNotNull(manifest, archive).any { it.text("state") != UpdateCheck.UPLOADED }) return null
        return DatabaseRelease(
            version = version,
            manifestUrl = manifest?.text("browser_download_url"),
            archiveUrl = archive?.text("browser_download_url"),
            archiveSize = archive?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    /** Manifest z wydania; null, gdy to nie JSON albo brakuje pól, bez których nie da się sprawdzić ZIP-a. */
    fun parseManifest(body: String): DatabaseManifest? =
        runCatching { json.decodeFromString(DatabaseManifest.serializer(), body) }.getOrNull()
            ?.takeIf { it.version > 0 && it.size > 0 && SHA256.matches(it.sha256) }

    /**
     * Co zrobić z wydaniem bazy względem wczytanej ([installedVersion]) i tej wersji aplikacji ([appVersion]).
     * Format nowszy niż [SUPPORTED_FORMAT] i `minApp` wyższe od aplikacji znaczą to samo: najpierw aktualizacja aplikacji.
     */
    fun decide(release: DatabaseRelease?, manifest: DatabaseManifest?, installedVersion: Int, appVersion: String): DatabaseOffer {
        if (release == null || release.version <= installedVersion) return DatabaseOffer.UpToDate
        if (manifest == null || manifest.version != release.version || release.archiveUrl == null) {
            return DatabaseOffer.Unreadable(release.version)
        }
        val minApp = manifest.minApp
        if (manifest.format > SUPPORTED_FORMAT || (minApp != null && UpdateCheck.compareVersions(appVersion, minApp) < 0)) {
            return DatabaseOffer.NeedsApp(release.version, minApp)
        }
        if (release.archiveSize > 0 && release.archiveSize != manifest.size) return DatabaseOffer.Unreadable(release.version)
        return DatabaseOffer.Available(manifest, release.archiveUrl)
    }

    /** Opis zmian na karcie: „3 nowe ćwiczenia, 1 zmienione, bez wycofanych”. Pusty, gdy manifest liczb nie ma. */
    fun changesText(manifest: DatabaseManifest): String {
        val added = manifest.added ?: return ""
        val changed = manifest.changed ?: 0
        val retired = manifest.retired ?: 0
        val parts = listOf(
            "$added " + plural(added, "nowe ćwiczenie", "nowe ćwiczenia", "nowych ćwiczeń"),
            "$changed " + plural(changed, "zmienione", "zmienione", "zmienionych"),
            if (retired == 0) "bez wycofanych" else "$retired " + plural(retired, "wycofane", "wycofane", "wycofanych"),
        )
        return parts.joinToString(", ")
    }

    private fun plural(n: Int, one: String, few: String, many: String): String = when {
        n == 1 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

    private fun JsonObject.flag(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
}

/** Wydanie bazy sprowadzone do tego, czego potrzeba do pobrania. */
data class DatabaseRelease(
    val version: Int,
    val manifestUrl: String?,
    val archiveUrl: String?,
    val archiveSize: Long,
)

/** Manifest wydania bazy (`tools/baza.py paczka`). Liczby zmian są względem poprzedniego wydania, mogą nie przyjść. */
@Serializable
data class DatabaseManifest(
    val format: Int = 1,
    val version: Int = 0,
    val minApp: String? = null,
    val exercises: Int = 0,
    val added: Int? = null,
    val changed: Int? = null,
    val retired: Int? = null,
    val file: String = "",
    val size: Long = 0,
    val sha256: String = "",
)

/** Co aplikacja może zrobić z najnowszym wydaniem bazy. */
sealed interface DatabaseOffer {
    /** Brak wydania albo wczytana baza jest taka sama albo nowsza. */
    data object UpToDate : DatabaseOffer

    /** Baza dla nowszej aplikacji: najpierw aktualizacja APK. */
    data class NeedsApp(val version: Int, val minApp: String?) : DatabaseOffer

    /** Wydanie bez manifestu albo ZIP-a, albo z manifestem, który się nie zgadza. */
    data class Unreadable(val version: Int) : DatabaseOffer

    data class Available(val manifest: DatabaseManifest, val archiveUrl: String) : DatabaseOffer
}
