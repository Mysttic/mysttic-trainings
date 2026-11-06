package pl.mysttic.trainings.data.update

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.BuildConfig
import pl.mysttic.trainings.data.background.PhotoFile
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.seed.SeedFile
import pl.mysttic.trainings.data.seed.SeedImporter
import pl.mysttic.trainings.data.seed.toDomain
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.update.DatabaseManifest
import pl.mysttic.trainings.domain.update.DatabaseOffer
import pl.mysttic.trainings.domain.update.DatabaseRelease
import pl.mysttic.trainings.domain.update.DatabaseUpdate
import pl.mysttic.trainings.domain.update.ExerciseDatabaseCheck
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Czym skończyło się pobranie albo wczytanie bazy ćwiczeń. */
sealed interface DatabaseInstall {
    /** Baza sprawdzona i wczytana do Room. */
    data class Imported(val version: Int) : DatabaseInstall

    /** Baza sprawdzona i zapisana; trwa trening, więc wczyta się przy następnym uruchomieniu. */
    data class Pending(val version: Int) : DatabaseInstall

    /** Kontrola odrzuciła bazę; w telefonie nic się nie zmieniło. */
    data class Rejected(val reasons: List<String>) : DatabaseInstall

    data class Failed(val message: String) : DatabaseInstall
}

/**
 * Pobieranie bazy ćwiczeń z wydania na stronie projektu (D-47). Najpierw mały manifest (numer, format, `minApp`,
 * rozmiar i SHA-256 ZIP-a), potem ZIP przez plik `.part` z kontrolą rozmiaru i sumy, rozpakowanie z limitami
 * ([DatabaseArchive]) i wroga kontrola ([ExerciseDatabaseCheck]). Baza trafia do `files/baza/<numer>/` dopiero
 * w całości sprawdzona (zmiana nazwy katalogu), a import robi [SeedImporter]. Sieć tylko na żądanie z Ustawień.
 */
@Singleton
class ExerciseDatabaseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseRepo: ExerciseRepository,
    private val settings: SettingsRepository,
    private val seedImporter: SeedImporter,
    private val json: Json,
) {

    /** Manifest wydania przed decyzją o pobraniu ZIP-a; null przy błędzie sieci albo pliku nie do odczytania. */
    suspend fun manifest(release: DatabaseRelease): DatabaseManifest? = withContext(Dispatchers.IO) {
        val url = release.manifestUrl ?: return@withContext null
        runCatching { DatabaseUpdate.parseManifest(readText(url)) }
            .onFailure { Log.e(TAG, "Manifest bazy nie przyszedł", it) }
            .getOrNull()
    }

    /** Pobiera ZIP z [offer], sprawdza rozmiar i SHA-256 z manifestu, potem sprawdza i wczytuje bazę. */
    suspend fun download(offer: DatabaseOffer.Available, importNow: Boolean): DatabaseInstall = withContext(Dispatchers.IO) {
        val manifest = offer.manifest
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.deleteRecursively() }
        val part = File(dir, DatabaseUpdate.archiveName(manifest.version) + ".part")
        try {
            downloadTo(offer.archiveUrl, part)
            if (part.length() != manifest.size) return@withContext DatabaseInstall.Failed("Pobrany plik ma inny rozmiar niż w wydaniu.")
            if (sha256(part) != manifest.sha256) return@withContext DatabaseInstall.Failed("Suma kontrolna pobranej bazy się nie zgadza.")
            install(part, expectedVersion = manifest.version, importNow = importNow)
        } catch (e: Exception) {
            Log.e(TAG, "Pobieranie bazy nie powiodło się", e)
            DatabaseInstall.Failed("Pobieranie bazy nie powiodło się.")
        } finally {
            part.delete()
        }
    }

    /** Baza z pliku ZIP (build debug, test bez publikacji): ta sama kontrola i ten sam import co z sieci. */
    suspend fun installFrom(uri: Uri, importNow: Boolean): DatabaseInstall = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val copy = File(dir, "z-pliku.zip")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext DatabaseInstall.Failed("Nie da się otworzyć pliku.")
            input.use { source -> copy.outputStream().use { source.copyTo(it) } }
            install(copy, expectedVersion = null, importNow = importNow)
        } catch (e: Exception) {
            Log.e(TAG, "Wczytanie bazy z pliku nie powiodło się", e)
            DatabaseInstall.Failed("Wczytanie bazy z pliku nie powiodło się.")
        } finally {
            copy.delete()
        }
    }

    private suspend fun install(zip: File, expectedVersion: Int?, importNow: Boolean): DatabaseInstall {
        val root = SeedImporter.downloadedRoot(context).apply { mkdirs() }
        val staging = File(root, STAGING).apply { deleteRecursively() }
        try {
            try {
                zip.inputStream().use { DatabaseArchive.extract(it, staging) }
            } catch (e: IllegalArgumentException) {
                return DatabaseInstall.Rejected(listOf(e.message ?: "paczka bazy jest uszkodzona"))
            }
            // Wartość spoza enumów tej wersji (nowy sprzęt, partia) przewraca odczyt: baza jest dla nowszej aplikacji.
            val seed = runCatching { json.decodeFromString(SeedFile.serializer(), File(staging, DatabaseArchive.JSON).readText()) }
                .getOrElse { return DatabaseInstall.Rejected(listOf("baza jest dla nowszej aplikacji albo plik jest uszkodzony")) }
            if (expectedVersion != null && seed.version != expectedVersion) {
                return DatabaseInstall.Rejected(listOf("plik ma bazę v${seed.version}, a wydanie v$expectedVersion"))
            }
            val images = File(staging, "images").list()?.toSet().orEmpty()
            // Zdjęcia tła (D-51) są w bazie opcjonalne; plik, którego ta wersja nie odczyta, odrzuca całą bazę jak wyżej.
            val photosFile = File(staging, DatabaseArchive.PHOTOS_JSON)
            val photos = if (photosFile.isFile) {
                runCatching { json.decodeFromString(PhotoFile.serializer(), photosFile.readText()) }
                    .getOrElse { return DatabaseInstall.Rejected(listOf("zdjęcia tła są dla nowszej aplikacji albo plik jest uszkodzony")) }
                    .photos
            } else {
                emptyList()
            }
            val problems = ExerciseDatabaseCheck.problems(
                version = seed.version,
                format = seed.format,
                minApp = seed.minApp,
                exercises = seed.exercises.map { it.toDomain() },
                imageFiles = images,
                installed = exerciseRepo.getAll(),
                installedVersion = settings.current().seedVersion,
                appVersion = BuildConfig.VERSION_NAME,
                photoIds = photos.map { it.id },
                photoFiles = File(staging, "tla").list()?.toSet().orEmpty(),
            )
            if (problems.isNotEmpty()) return DatabaseInstall.Rejected(problems)
            val ready = SeedImporter.downloadedDir(context, seed.version).apply { deleteRecursively() }
            if (!staging.renameTo(ready)) return DatabaseInstall.Failed("Nie udało się zapisać bazy.")
            if (!importNow) return DatabaseInstall.Pending(seed.version)
            seedImporter.importDownloaded(seed.version)
            return DatabaseInstall.Imported(seed.version)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun connection(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = DOWNLOAD_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun readText(url: String): String {
        val connection = connection(url, "application/json, application/octet-stream")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("Serwer odpowiedział ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadTo(url: String, target: File) {
        val connection = connection(url, "application/octet-stream")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("Serwer odpowiedział ${connection.responseCode}")
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ExerciseDatabase"
        private const val CACHE_DIR = "baza"
        /** Katalog roboczy w `files/baza/`: nazwa nie jest numerem, więc `SeedImporter` go nie weźmie. */
        private const val STAGING = "przyjecie.part"
        private const val USER_AGENT = "mysttic-trainings"
        private const val TIMEOUT_MS = 10_000
        private const val DOWNLOAD_TIMEOUT_MS = 60_000
    }
}
