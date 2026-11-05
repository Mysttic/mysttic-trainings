package pl.mysttic.trainings.data.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.mysttic.trainings.BuildConfig
import pl.mysttic.trainings.domain.update.DatabaseRelease
import pl.mysttic.trainings.domain.update.DatabaseUpdate
import pl.mysttic.trainings.domain.update.ReleaseInfo
import pl.mysttic.trainings.domain.update.ReleaseParser
import pl.mysttic.trainings.domain.update.UpdateCheck
import pl.mysttic.trainings.domain.update.UpdateOutcome
import pl.mysttic.trainings.domain.update.UpdateResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Wynik jednego pytania o wydania: aplikacja i baza ćwiczeń (null = brak wydania bazy na liście). */
data class ReleasesCheck(val app: UpdateResult, val database: DatabaseRelease?)

/**
 * Pytanie do GitHuba o nowsze wydanie, pobranie APK i oddanie go systemowemu instalatorowi (M7, D-33).
 *
 * Pytamy publiczne repozytorium strony, nie repozytorium kodu: tam leżą wydania wszystkich projektów
 * (D-33), a do 1.1.0 kod był prywatny i jego API odpowiadało 404 każdej zainstalowanej kopii. Zapytanie
 * idzie bez tokenu i nie niesie ze sobą niczego poza tym, co widzi każdy serwer HTTPS.
 *
 * Nic tu nie rzuca wyjątkiem na zewnątrz: sprawdzanie aktualizacji jest czynnością poboczną i nie ma
 * prawa zepsuć niczego w aplikacji.
 */
@Singleton
class UpdateRepository @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Jedno pytanie o listę wydań strony, dwie odpowiedzi: nowsza wersja aplikacji i najnowsze wydanie bazy
     * ćwiczeń (D-47). Bazę rozpoznaje własny prefiks taga, więc obie umowy nie wchodzą sobie w drogę.
     */
    suspend fun check(current: String = BuildConfig.VERSION_NAME): ReleasesCheck = withContext(Dispatchers.IO) {
        val body = runCatching { get(RELEASES_API) }.getOrNull()
            ?: return@withContext ReleasesCheck(UpdateResult(UpdateOutcome.FAILED), database = null)
        ReleasesCheck(ReleaseParser.parse(body, current, RELEASES_PAGE), DatabaseUpdate.parseRelease(body))
    }

    /**
     * Pobiera APK do `files/updates/`. Zwraca `null`, gdy się nie udało.
     *
     * Plik powstaje jako `.part` i dostaje właściwą nazwę dopiero po sprawdzeniu rozmiaru: przerwane
     * pobieranie ma nie zostawić kadłubka, który systemowy instalator wziąłby za gotowy pakiet.
     */
    suspend fun download(release: ReleaseInfo): File? = withContext(Dispatchers.IO) {
        val url = release.assetUrl ?: return@withContext null
        val dir = File(context.filesDir, UPDATES_DIR).apply { mkdirs() }
        // Zostawiamy tylko bieżące pobranie: stare APK to kilkanaście megabajtów śmieci.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, UpdateCheck.assetNameFor(release.version))
        val partial = File(dir, target.name + PART_SUFFIX)
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = DOWNLOAD_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
            val expected = release.assetSize
            if (expected > 0 && partial.length() != expected) {
                error("Pobrano ${partial.length()} bajtów zamiast $expected")
            }
            if (!partial.renameTo(target)) error("Nie udało się zamknąć pliku ${target.name}")
            target
        }.getOrElse {
            Log.e(TAG, "Pobieranie aktualizacji nie powiodło się", it)
            partial.delete()
            target.delete()
            null
        }
    }

    /**
     * Oddaje plik systemowemu instalatorowi. To on pyta użytkownika o zgodę i wykonuje podmianę pakietu;
     * aplikacja nie instaluje niczego po cichu.
     */
    fun install(apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    }.getOrElse {
        Log.e(TAG, "Nie udało się otworzyć instalatora", it)
        false
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("GitHub odpowiedział ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "UpdateRepository"

        /**
         * Lista wydań publicznego repozytorium strony. Nie `releases/latest`: ten zwraca najnowsze
         * wydanie całego repozytorium, więc po wydaniu innego projektu aplikacja zobaczyłaby cudzy numer.
         * Filtrowanie po prefiksie robi [ReleaseParser].
         */
        const val RELEASES_API = "https://api.github.com/repos/Mysttic/Mysttic/releases?per_page=100"
        const val RELEASES_PAGE = UpdateCheck.PAGE
        private const val UPDATES_DIR = "updates"
        private const val PART_SUFFIX = ".part"
        private const val USER_AGENT = "mysttic-trainings"
        private const val TIMEOUT_MS = 10_000
        private const val DOWNLOAD_TIMEOUT_MS = 60_000
    }
}
