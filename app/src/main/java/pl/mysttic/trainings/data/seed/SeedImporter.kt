package pl.mysttic.trainings.data.seed

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.data.db.dao.ExerciseDao
import pl.mysttic.trainings.data.db.dao.WorkoutDao
import pl.mysttic.trainings.data.db.entity.ExerciseEntity
import pl.mysttic.trainings.data.db.entity.WorkoutEntity
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.domain.update.DatabaseUpdate
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Format bazy ćwiczeń (D-47): `baza/exercises.json` w repo, w APK jako asset `exercises.json`, w wydaniu bazy w ZIP-ie.
 * [version] rośnie z każdą zmianą treści, [format] tylko ze zmianą znaczenia pól, a [minApp] to najniższa wersja
 * aplikacji, która tę bazę przyjmie. Edytuje go `tools/baza.py`.
 */
@Serializable
data class SeedFile(
    val version: Int,
    val format: Int = 1,
    val minApp: String? = null,
    val exercises: List<SeedExercise>,
    val workouts: List<SeedWorkout> = emptyList(),
)

@Serializable
data class SeedExercise(
    val id: String,
    val sourceId: String? = null,
    val nameEn: String,
    val namePl: String,
    val kind: ExerciseKind,
    val level: Difficulty,
    val category: ExerciseCategory,
    val equipment: List<Equipment> = emptyList(),
    val primaryMuscles: List<MuscleGroup> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val instructionsPl: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val easierId: String? = null,
    val harderId: String? = null,
    val defaultReps: Int = 12,
    val defaultSeconds: Int = 30,
    /** Data wycofania (D-47); upsert nowej bazy bez tego pola przywraca ćwiczenie. */
    val retiredAt: String? = null,
)

@Serializable
data class SeedWorkout(
    val id: String,
    val name: String,
    val note: String = "",
    val blocks: List<Block>,
)

/**
 * Importuje bazę ćwiczeń do Room przy pierwszym uruchomieniu i przy każdym wyższym numerze. Źródła są dwa (D-47):
 * baza wbudowana w APK (asset `exercises.json`) i baza pobrana z wydania bazy, która leży w `files/baza/<numer>/`
 * dopiero po sprawdzeniu (`ExerciseDatabaseCheck`) i zmianie nazwy katalogu. Wygrywa wyższy numer; obrazy pobranej
 * bazy mają w Room ścieżki `baza/<numer>/images/<id>.jpg`. Ćwiczenia własne użytkownika (`isCustom = true`) nie są
 * ruszane, wbudowane są nadpisywane.
 */
@Singleton
class SeedImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val settings: SettingsRepository,
    private val json: Json,
) {
    private val mutex = Mutex()

    /** Start aplikacji: wczytuje wyższą z baz (wbudowanej i pobranej), gdy jest nowsza od wczytanej. */
    suspend fun ensureSeeded() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val bundled = bundledVersion()
            val downloaded = downloadedVersions().maxOrNull()?.takeIf { it > bundled }
            val stored = settings.current().seedVersion
            // Katalog pobranej bazy, z której są obrazy, zniknął: wracamy do najlepszej, jaka jest, nawet niższej.
            val broken = imagesMissing()
            if ((downloaded ?: bundled) <= stored && exerciseDao.count() > 0 && !broken) return@withContext
            importLocked(if (downloaded != null) Source.Downloaded(downloaded) else Source.Bundled)
        }
    }

    /** Pobrana i sprawdzona baza [version] z `files/baza/` od razu, poza treningiem (Ustawienia, D-47). */
    suspend fun importDownloaded(version: Int) = mutex.withLock {
        withContext(Dispatchers.IO) { importLocked(Source.Downloaded(version)) }
    }

    private suspend fun importLocked(source: Source) {
        val seed = read(source)
        val stored = settings.current().seedVersion
        Log.i(TAG, "Importing seed v${seed.version} from $source (stored v$stored): ${seed.exercises.size} exercises")
        exerciseDao.upsertAll(seed.exercises.map { it.toEntity(source.imagePrefix) })
        val now = System.currentTimeMillis()
        workoutDao.upsertAll(seed.workouts.map { it.toEntity(now, json) })
        // Import tylko dopisuje, więc sprzątanie po starszych wersjach idzie osobno (D-43): wbudowane treningi
        // bez sesji, których seed już nie wozi (v7), i treningi planów skasowanych przed 0.9.1. Planów z listy
        // (D-42) ani historii to nie rusza.
        workoutDao.deleteUnusedBuiltIn(keep = seed.workouts.map { it.id })
        workoutDao.deleteOwnerlessPlanWorkouts(before = now - ORPHAN_GRACE_MS)
        settings.setSeedVersion(seed.version)
        // Pobrane bazy inne niż wczytana do niczego już nie służą: obrazy biorą się wyłącznie z wczytanej.
        removeDownloaded(keep = (source as? Source.Downloaded)?.version)
    }

    /** Numer bazy z APK bez czytania całego pliku: `version` to pierwszy klucz (tak zapisuje `tools/baza.py`). */
    private fun bundledVersion(): Int {
        val head = context.assets.open(SEED_PATH).bufferedReader().use { reader ->
            val buffer = CharArray(HEAD_CHARS)
            val read = reader.read(buffer)
            if (read > 0) String(buffer, 0, read) else ""
        }
        return VERSION.find(head)?.groupValues?.get(1)?.toIntOrNull() ?: read(Source.Bundled).version
    }

    private fun downloadedVersions(): List<Int> =
        downloadedRoot(context).listFiles().orEmpty()
            .filter { it.isDirectory && it.name.all(Char::isDigit) && File(it, SEED_PATH).isFile }
            .mapNotNull { it.name.toIntOrNull() }

    private suspend fun imagesMissing(): Boolean {
        val path = exerciseDao.getAll().firstOrNull { !it.isCustom }?.images?.firstOrNull() ?: return false
        return path.startsWith(DatabaseUpdate.IMAGE_PREFIX) && !File(context.filesDir, path).exists()
    }

    private fun read(source: Source): SeedFile {
        val stream = when (source) {
            Source.Bundled -> context.assets.open(SEED_PATH)
            is Source.Downloaded -> File(downloadedDir(context, source.version), SEED_PATH).inputStream()
        }
        return stream.bufferedReader().use { json.decodeFromString(SeedFile.serializer(), it.readText()) }
    }

    private fun removeDownloaded(keep: Int?) {
        downloadedRoot(context).listFiles().orEmpty()
            .filter { it.name != keep?.toString() }
            .forEach { it.deleteRecursively() }
    }

    private sealed interface Source {
        val imagePrefix: String

        data object Bundled : Source {
            override val imagePrefix = ""
        }

        data class Downloaded(val version: Int) : Source {
            override val imagePrefix get() = "${DatabaseUpdate.IMAGE_PREFIX}$version/"
        }
    }

    companion object {
        private const val TAG = "SeedImporter"
        /** Katalog `baza/` jest korzeniem assetów (Gradle, D-47), więc plik leży na samej górze. */
        const val SEED_PATH = "exercises.json"

        /** Trening młodszy niż doba może właśnie powstawać (plan zapisywany w tej chwili), więc go nie ruszamy. */
        private const val ORPHAN_GRACE_MS = 24L * 60 * 60 * 1000

        private const val HEAD_CHARS = 256
        private val VERSION = Regex("\"version\"\\s*:\\s*(\\d+)")

        /** `files/baza/`: pobrane bazy, każda w katalogu z numerem (D-47). */
        fun downloadedRoot(context: Context): File = File(context.filesDir, DatabaseUpdate.IMAGE_PREFIX.trimEnd('/'))

        fun downloadedDir(context: Context, version: Int): File = File(downloadedRoot(context), version.toString())
    }
}

/** Rekord z pliku bazy jako ćwiczenie domeny; [imagePrefix] dokleja katalog pobranej bazy do ścieżek obrazów. */
fun SeedExercise.toDomain(imagePrefix: String = "") = Exercise(
    id = id,
    nameEn = nameEn,
    namePl = namePl,
    kind = kind,
    level = level,
    category = category,
    equipment = equipment.toSet(),
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    instructionsPl = instructionsPl,
    images = images.map { imagePrefix + it },
    easierId = easierId,
    harderId = harderId,
    defaultReps = defaultReps,
    defaultSeconds = defaultSeconds,
    isCustom = false,
    retiredAt = retiredAt,
)

private fun SeedExercise.toEntity(imagePrefix: String) = ExerciseEntity(
    id = id,
    nameEn = nameEn,
    namePl = namePl,
    kind = kind,
    level = level,
    category = category,
    equipment = equipment.toSet(),
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    instructionsPl = instructionsPl,
    images = images.map { imagePrefix + it },
    easierId = easierId,
    harderId = harderId,
    defaultReps = defaultReps,
    defaultSeconds = defaultSeconds,
    isCustom = false,
    retiredAt = retiredAt,
)

private fun SeedWorkout.toEntity(now: Long, json: Json) = WorkoutEntity(
    id = id,
    name = name,
    note = note,
    blocksJson = json.encodeToString(blocks),
    createdAt = now,
    updatedAt = now,
    isBuiltIn = true,
)
