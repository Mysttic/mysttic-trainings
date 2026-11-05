package pl.mysttic.trainings.domain.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.data.seed.SeedFile
import pl.mysttic.trainings.data.seed.toDomain
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseKind
import java.io.File

/**
 * Wroga kontrola bazy pobranej z sieci (D-47). Punktem wyjścia jest prawdziwa `baza/` jako „następna wersja”
 * nad tą samą bazą w telefonie: przechodzi czysto, a każda pojedyncza zmiana z listy reguł musi ją zatrzymać.
 */
class ExerciseDatabaseCheckTest {

    private val baza: List<Exercise> by lazy {
        val file = listOf("../baza/exercises.json", "baza/exercises.json").map { File(it) }.first { it.exists() }
        Json { ignoreUnknownKeys = true }.decodeFromString(SeedFile.serializer(), file.readText()).exercises.map { it.toDomain() }
    }
    private val images: Set<String> by lazy { baza.map { "${it.id}.jpg" }.toSet() }

    private fun problems(
        exercises: List<Exercise> = baza,
        imageFiles: Set<String> = images,
        installed: List<Exercise> = baza,
        version: Int = 11,
        format: Int = 1,
        minApp: String? = "1.1.0",
    ) = ExerciseDatabaseCheck.problems(version, format, minApp, exercises, imageFiles, installed, installedVersion = 10, appVersion = "1.1.0")

    private fun replaced(id: String, change: (Exercise) -> Exercise) = baza.map { if (it.id == id) change(it) else it }

    private fun assertRejected(found: List<String>, fragment: String) =
        assertTrue("oczekiwany powód z „$fragment”, są: $found", found.any { fragment in it })

    @Test
    fun `nastepna wersja tej samej bazy przechodzi`() {
        assertEquals(emptyList<String>(), problems())
    }

    @Test
    fun `numer, format i wersja aplikacji`() {
        assertRejected(problems(version = 10), "nie jest nowsza")
        assertRejected(problems(format = 2), "format 2")
        assertRejected(problems(minApp = "1.2.0"), "wymaga aplikacji 1.2.0")
    }

    @Test
    fun `cwiczenie z telefonu nie znika i nie zmienia rodzaju celu`() {
        assertRejected(problems(exercises = baza.filter { it.id != "bird-dog" }, imageFiles = images - "bird-dog.jpg"), "bird-dog: ćwiczenie z telefonu zniknęło")
        val kind = if (baza.first { it.id == "bird-dog" }.kind == ExerciseKind.REPS) ExerciseKind.TIME else ExerciseKind.REPS
        assertRejected(problems(exercises = replaced("bird-dog") { it.copy(kind = kind, easierId = null, harderId = null) }), "bird-dog: zmienił się rodzaj celu")
        // Własne ćwiczenia w telefonie (pliki planów) nie należą do bazy, więc ich brak nie przeszkadza.
        val own = Exercise("custom-1a2b3c4d", "Mine", "Moje", ExerciseKind.REPS, baza[0].level, baza[0].category, emptySet(),
            emptyList(), emptyList(), listOf("a"), emptyList(), isCustom = true)
        assertEquals(emptyList<String>(), problems(installed = baza + own))
    }

    @Test
    fun `ilustracje, id i pola`() {
        assertRejected(problems(imageFiles = images - "bird-dog.jpg"), "bird-dog: brak ilustracji")
        assertRejected(problems(imageFiles = images + "zbedny.jpg"), "obrazy bez ćwiczeń")
        val obce = baza[0].copy(id = "custom-zly", images = listOf("images/custom-zly.jpg"))
        assertRejected(problems(exercises = baza + obce, imageFiles = images + "custom-zly.jpg"), "custom-zly: prefiks")
        assertRejected(problems(exercises = baza + baza[0]), "${baza[0].id}: id powtórzone")
        assertRejected(problems(exercises = replaced("bird-dog") { it.copy(namePl = " ") }), "bird-dog: nazwa")
        assertRejected(problems(exercises = replaced("bird-dog") { it.copy(defaultReps = 999) }), "bird-dog: cel domyślny")
        assertRejected(problems(exercises = replaced("bird-dog") { it.copy(retiredAt = "28.10.2026") }), "bird-dog: data wycofania")
    }

    @Test
    fun `wariant i program nie wskazuja wycofanego`() {
        val variantTarget = baza.first { e -> !e.isRetired && e.easierId != null }.easierId!!
        assertRejected(problems(exercises = replaced(variantTarget) { it.copy(retiredAt = "2026-10-28") }), "wariant $variantTarget jest wycofany")
        // „burpees” jest w Tabacie: wycofana baza zostawiłaby program bez ćwiczenia.
        assertRejected(problems(exercises = replaced("burpees") { it.copy(retiredAt = "2026-10-28") }), "burpees jest wycofane")
    }
}
