package pl.mysttic.trainings.data.seed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.data.background.PhotoFile
import pl.mysttic.trainings.domain.background.PhotoSex
import pl.mysttic.trainings.domain.background.PhotoTopic
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.plan.share.PlanFileCodec
import pl.mysttic.trainings.domain.update.UpdateCheck
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * Integralność bazy ćwiczeń z `baza/` (D-47). Te same reguły sprawdza `tools/baza.py sprawdz` przed wydaniem bazy,
 * a test pilnuje ich w CI razem z kodem, który na tej bazie stoi.
 *
 * Sprawdza to, na czym stoi aplikacja: unikalność i format identyfikatorów, komplet ilustracji, spójność wariantów
 * (`easierId`/`harderId`), brak wbudowanych treningów i to, że katalog wozi wyłącznie dane, bo Gradle podpina go
 * w całości jako assety. Sprzętu wariantu pilnuje `WorkoutPlanner.resolveVariant` z posiadanym zestawem (D-43),
 * więc tu go nie sprawdzamy.
 */
class SeedIntegrityTest {

    @Serializable
    private data class SeedExercise(
        val id: String,
        val namePl: String,
        val level: Difficulty,
        val kind: ExerciseKind,
        val primaryMuscles: List<String> = emptyList(),
        val instructionsPl: List<String> = emptyList(),
        val images: List<String> = emptyList(),
        val easierId: String? = null,
        val harderId: String? = null,
        val retiredAt: String? = null,
    )

    @Serializable
    private data class Seed(val version: Int, val format: Int, val minApp: String, val exercises: List<SeedExercise>)

    private val root: File by lazy {
        listOf(File("../baza"), File("baza"))
            .firstOrNull { File(it, "exercises.json").isFile } ?: error("Nie znaleziono katalogu baza/")
    }

    private val seed: Seed by lazy {
        val json = Json { ignoreUnknownKeys = true }
        json.decodeFromString(Seed.serializer(), File(root, "exercises.json").readText())
    }

    private val number = Regex("\\d+\\.\\d+\\.\\d+")

    private val versionLines: List<String> by lazy {
        listOf(File("../VERSION.md"), File("VERSION.md")).first { it.isFile }.readLines().map { it.trim() }
    }

    @Test
    fun `identyfikatory sa unikalne i kazde cwiczenie ma komplet pol`() {
        val ids = seed.exercises.map { it.id }
        assertEquals("duplikaty id", ids.size, ids.toSet().size)
        seed.exercises.forEach { exercise ->
            assertTrue("${exercise.id}: pusta nazwa", exercise.namePl.isNotBlank())
            assertTrue("${exercise.id}: brak instrukcji", exercise.instructionsPl.isNotEmpty())
        }
    }

    @Test
    fun `kazde cwiczenie ma swoja ilustracje, a kazdy obraz swoje cwiczenie`() {
        seed.exercises.forEach { exercise ->
            assertEquals("${exercise.id}: ścieżka obrazu", listOf("images/${exercise.id}.jpg"), exercise.images)
            assertTrue("${exercise.id}: brak pliku images/${exercise.id}.jpg", File(root, "images/${exercise.id}.jpg").isFile)
        }
        val expected = seed.exercises.map { "${it.id}.jpg" }.toSet()
        val onDisk = File(root, "images").listFiles().orEmpty().map { it.name }.toSet()
        assertEquals("osierocone pliki obrazów", emptySet<String>(), onDisk - expected)
    }

    @Test
    fun `katalog bazy wozi tylko dane`() {
        // Gradle podpina `baza/` w całości jako assety APK (D-47), więc każdy inny plik pojechałby do aplikacji.
        val entries = root.listFiles().orEmpty().map { it.name }.toSet()
        // Zdjęcia tła (D-51) są opcjonalne, ale tylko w komplecie: tla.json razem z katalogiem tla/.
        val expected = listOf(setOf("exercises.json", "images"), setOf("exercises.json", "images", "tla.json", "tla"))
        assertTrue("zawartość baza/: $entries", entries in expected)
        val strays = File(root, "images").listFiles().orEmpty().filter { !it.isFile || !it.name.endsWith(".jpg") }
        assertEquals("w images/ tylko pliki .jpg", emptyList<File>(), strays)
    }

    @Test
    fun `format i najnizsza wersja aplikacji pasuja do tego builda`() {
        assertEquals("format bazy", 1, seed.format)
        assertTrue("minApp ${seed.minApp} poza formatem 1.2.3", number.matches(seed.minApp))
        // Baza z repo jedzie w APK z tego samego commita, więc musi ją przyjąć wersja z VERSION.md.
        val app = versionLines.first { number.matches(it) }
        assertTrue("minApp ${seed.minApp} wyższe niż $app", UpdateCheck.compareVersions(seed.minApp, app) <= 0)
    }

    @Test
    fun `numer bazy w VERSION_md zgadza sie z baza`() {
        // Numer bazy stoi też w VERSION.md, obok numeru aplikacji; podbija oba `python tools/baza.py podbij`.
        val heading = versionLines.indexOf("# Wersja bazy ćwiczeń")
        assertTrue("VERSION.md: brak nagłówka „# Wersja bazy ćwiczeń”", heading >= 0)
        val listed = versionLines.drop(heading + 1).firstOrNull { it.isNotEmpty() }
        assertEquals("numer bazy w VERSION.md", seed.version.toString(), listed)
    }

    @Test
    fun `warianty wskazuja istniejace cwiczenia, bez samoodwolan`() {
        val byId = seed.exercises.associateBy { it.id }
        seed.exercises.forEach { exercise ->
            listOf("easierId" to exercise.easierId, "harderId" to exercise.harderId).forEach { (field, target) ->
                if (target == null) return@forEach
                assertTrue("${exercise.id}.$field: brak $target", byId.containsKey(target))
                assertTrue("${exercise.id}.$field wskazuje samo siebie", target != exercise.id)
            }
        }
    }

    @Test
    fun `wariant trzyma ten sam rodzaj celu co cwiczenie bazowe`() {
        // Podmiana wariantu (poziom albo „Podbij cele”) przenosi cel: ćwiczenie na czas w miejsce ćwiczenia
        // na powtórzenia dałoby 30 powtórzeń tam, gdzie miało być 30 sekund.
        val byId = seed.exercises.associateBy { it.id }
        seed.exercises.forEach { exercise ->
            listOfNotNull(exercise.easierId, exercise.harderId).forEach { id ->
                assertEquals("${exercise.id} → $id", exercise.kind, byId.getValue(id).kind)
            }
        }
    }

    @Test
    fun `identyfikatory pasuja do pliku planu i nie zajmuja prefiksu wlasnych`() {
        // Plik planu (D-42) traktuje id bez `custom-` jako rodzinę bazy, a `custom-` rezerwuje dla ćwiczeń autora.
        // Wzorzec powielony z PlanFileCodec (tam prywatny): id spoza niego odbiorca odrzuci razem z pozycją.
        val fileId = Regex("[a-z0-9]+(-[a-z0-9]+)*")
        seed.exercises.forEach { exercise ->
            assertTrue("${exercise.id}: zajmuje prefiks ${PlanFileCodec.CUSTOM_PREFIX}", !exercise.id.startsWith(PlanFileCodec.CUSTOM_PREFIX))
            assertTrue("${exercise.id}: poza formatem id pliku planu", fileId.matches(exercise.id) && exercise.id.length <= 64)
        }
    }

    @Test
    fun `data wycofania to poprawna data, nie z przyszlosci`() {
        seed.exercises.forEach { exercise ->
            val date = exercise.retiredAt ?: return@forEach
            assertTrue("${exercise.id}: retiredAt $date poza RRRR-MM-DD", Regex("\\d{4}-\\d{2}-\\d{2}").matches(date))
            val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
            assertTrue("${exercise.id}: retiredAt $date to nie data", parsed != null)
            assertTrue("${exercise.id}: retiredAt $date z przyszłości", !parsed!!.isAfter(LocalDate.now()))
        }
    }

    @Test
    fun `cwiczenie w uzyciu nie wskazuje wycofanego wariantu`() {
        // Wycofane (D-47) znika z biblioteki i kreatora, więc nie może wrócić jako łatwiejszy albo trudniejszy wariant.
        val byId = seed.exercises.associateBy { it.id }
        seed.exercises.filter { it.retiredAt == null }.forEach { exercise ->
            listOfNotNull(exercise.easierId, exercise.harderId).forEach { id ->
                assertTrue("${exercise.id} → $id: wariant wycofany", byId[id]?.retiredAt == null)
            }
        }
    }

    /**
     * Zdjęcia tła (D-51): wpis i plik razem, kadr 360×780 z `tools/baza.py tlo`, a każdy temat ma zdjęcie dla obu płci,
     * bo tło obiecuje zdjęcie pod dzisiejszy trening i pod wybór z Ustawień. Odczyt przez `PhotoFile` sprawdza też
     * tematy i płcie: wartość spoza enumów tej wersji go przewraca.
     */
    @Test
    fun `zdjecia tla maja pliki, kadr i komplet tematow dla obu plci`() {
        val file = File(root, "tla.json")
        if (!file.isFile) return
        val photos = Json.decodeFromString(PhotoFile.serializer(), file.readText()).photos
        assertEquals("id zdjęć powtarzają się", photos.size, photos.map { it.id }.toSet().size)
        photos.forEach { photo ->
            assertTrue("${photo.id}: id poza formatem", Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(photo.id))
            val image = File(root, "tla/${photo.id}.jpg")
            assertTrue("${photo.id}: brak pliku tla/${photo.id}.jpg", image.isFile)
            val decoded = requireNotNull(ImageIO.read(image)) { "${photo.id}: to nie jest obraz" }
            assertEquals("${photo.id}: kadr", 360 to 780, decoded.width to decoded.height)
        }
        val onDisk = File(root, "tla").listFiles().orEmpty().map { it.name }.toSet()
        assertEquals("pliki bez wpisu w tla.json", emptySet<String>(), onDisk - photos.map { "${it.id}.jpg" }.toSet())
        PhotoTopic.entries.forEach { topic ->
            PhotoSex.entries.forEach { sex ->
                assertTrue("brak zdjęcia $topic/$sex", photos.any { it.topic == topic && it.sex == sex })
            }
        }
    }

    @Test
    fun `baza nie wozi szablonow treningow`() {
        // Od seedu v7 (D-43) kolejkę układa kreator, gotowy program albo edytor planu (D-42).
        assertTrue("baza zawiera sekcję workouts", !File(root, "exercises.json").readText().contains("\"workouts\""))
    }
}
