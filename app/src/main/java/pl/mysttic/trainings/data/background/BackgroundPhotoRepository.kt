package pl.mysttic.trainings.data.background

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.seed.SeedImporter
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.background.BackgroundPhoto
import pl.mysttic.trainings.domain.background.PhotoPeople
import pl.mysttic.trainings.domain.model.Gender
import pl.mysttic.trainings.domain.background.PhotoPicker
import pl.mysttic.trainings.domain.background.PhotoSex
import pl.mysttic.trainings.domain.background.PhotoTopic
import pl.mysttic.trainings.domain.update.DatabaseUpdate
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** `baza/tla.json` (D-51): zdjęcia tła jadą w bazie ćwiczeń, z tym samym numerem i tymi samymi wydaniami (D-47). */
@Serializable
data class PhotoFile(val photos: List<SeedPhoto> = emptyList())

/** Wpis zdjęcia; plik leży w `tla/<id>.jpg`. Wartość spoza enumów tej wersji przewraca odczyt: baza jest dla nowszej aplikacji. */
@Serializable
data class SeedPhoto(val id: String, val topic: PhotoTopic, val sex: PhotoSex)

/**
 * Zdjęcie dnia w tle (D-51): zdjęcia aktywnej bazy ćwiczeń i wybór pod dzisiejsze treningi. Aktywna baza to ta,
 * którą wczytał `SeedImporter`: pobrana leży w `files/baza/<numer>/`, wbudowana w assetach APK. Zdjęcia nie idą do Room,
 * bo nic poza tłem o nich nie wie, a lista jest krótka.
 */
@Singleton
class BackgroundPhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val settings: SettingsRepository,
    private val programs: ProgramRepository,
    private val workouts: WorkoutRepository,
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
) {
    @Volatile
    private var cache: Pair<Int, List<BackgroundPhoto>>? = null

    /** Zdjęcia wczytanej bazy; baza bez `tla.json` albo z uszkodzonym plikiem = brak zdjęć, tło zostaje bez zdjęcia. */
    suspend fun photos(): List<BackgroundPhoto> = withContext(Dispatchers.IO) {
        val version = settings.current().seedVersion
        cache?.takeIf { it.first == version }?.let { return@withContext it.second }
        load(version).also { cache = version to it }
    }

    private fun load(version: Int): List<BackgroundPhoto> {
        val downloaded = File(SeedImporter.downloadedDir(context, version), PHOTOS_PATH)
        val (text, prefix) = if (downloaded.isFile) {
            runCatching { downloaded.readText() }.getOrNull() to "${DatabaseUpdate.IMAGE_PREFIX}$version/"
        } else {
            runCatching { context.assets.open(PHOTOS_PATH).bufferedReader().use { it.readText() } }.getOrNull() to ""
        }
        val file = text?.let { runCatching { json.decodeFromString(PhotoFile.serializer(), it) }.getOrNull() } ?: return emptyList()
        return file.photos.map { BackgroundPhoto(it.id, it.topic, it.sex, "${prefix}tla/${it.id}.jpg") }
    }

    /**
     * Ścieżka zdjęcia dnia (jak ilustracji ćwiczeń: asset albo plik pobranej bazy), null = brak zdjęć w bazie.
     * Skład dnia to treningi ze wszystkich planów w użyciu (`PhotoPicker.todaysWorkoutIds`), z nich trzy tematy,
     * których jest najwięcej, a z nich losowanie z wagą liczby ćwiczeń, stałe przez dzień. Zdjęcie tylko nawiązuje
     * do dnia: żaden napis nie mówi, jaką partię dziś ćwiczysz.
     */
    fun today(): Flow<String?> = combine(
        programs.observeAll(),
        workouts.observeAll(),
        sessions.observeSessions(),
        settings.settings.map { PhotoPeople.of(Gender.fromId(it.gender)) to it.seedVersion }.distinctUntilChanged(),
    ) { plans, all, done, (people, _) ->
        val day = LocalDate.now()
        val byId = all.associateBy { it.id }
        val library = exercises.getMap()
        val todays = PhotoPicker.todaysWorkoutIds(plans, done, day, ZoneId.systemDefault())
            .mapNotNull { byId[it] }
            .flatMap { workout -> workout.exerciseIds.mapNotNull { library[it] } }
        PhotoPicker.pick(photos(), PhotoPicker.topTopics(todays), people, day.toEpochDay())?.image
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    companion object {
        /** Katalog `baza/` jest korzeniem assetów (D-47), a pobrana baza ma ten sam układ w swoim katalogu. */
        const val PHOTOS_PATH = "tla.json"
    }
}
