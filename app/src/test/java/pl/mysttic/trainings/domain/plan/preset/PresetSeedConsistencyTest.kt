package pl.mysttic.trainings.domain.plan.preset

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.data.seed.SeedFile
import pl.mysttic.trainings.data.seed.toDomain
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import java.io.File

/**
 * Gotowe programy wskazują ćwiczenia po identyfikatorze z bazy. Ten test czyta `baza/exercises.json` (D-47) i woła
 * [PresetCatalog.libraryProblems], tę samą funkcję, którą aplikacja sprawdza bazę pobraną z sieci przed wczytaniem:
 * ćwiczenie istnieje i nie jest wycofane, rodzaj celu pasuje do pozycji (D-27), oddech tylko w programie
 * oddechowym (D-37), zamiennik bez sprzętu tej samej partii i rodzaju (D-46). Nowe ćwiczenie w programie
 * bez wpisu w bazie wywala ten test, nie aplikację.
 */
class PresetSeedConsistencyTest {

    private val baza: Map<String, Exercise> by lazy {
        val file = listOf("../baza/exercises.json", "baza/exercises.json")
            .map { File(it) }.firstOrNull { it.exists() } ?: error("Nie znaleziono baza/exercises.json")
        Json { ignoreUnknownKeys = true }.decodeFromString(SeedFile.serializer(), file.readText())
            .exercises.map { it.toDomain() }.associateBy { it.id }
    }

    @Test
    fun `gotowe programy pasuja do bazy cwiczen`() {
        assertEquals(emptyList<String>(), PresetCatalog.libraryProblems(baza))
    }

    @Test
    fun `oddech to dokladnie cztery cwiczenia programu Wima Hofa`() {
        val breathing = PresetCatalog.exerciseIds(PresetCatalog.get(PresetId.WIM_HOF))
        assertEquals(setOf("power-breathing", "breath-hold", "recovery-breath", "calm-breathing"), breathing)
        breathing.forEach { id -> assertEquals(id, ExerciseCategory.BREATHING, baza.getValue(id).category) }
    }

    @Test
    fun `brak, wycofanie, zmiana rodzaju i oddech poza programem to problemy`() {
        fun problems(change: MutableMap<String, Exercise>.() -> Unit) = PresetCatalog.libraryProblems(baza.toMutableMap().apply(change))
        fun assertMentions(found: List<String>, fragment: String) = assertTrue("„$fragment” w $found", found.any { fragment in it })

        assertMentions(problems { remove("burpees") }, "burpees nie ma w bazie")
        assertMentions(problems { this["burpees"] = getValue("burpees").copy(retiredAt = "2026-10-28") }, "burpees jest wycofane")
        assertMentions(problems { this["burpees"] = getValue("burpees").copy(kind = ExerciseKind.TIME) }, "burpees: cel")
        assertMentions(problems { this["burpees"] = getValue("burpees").copy(category = ExerciseCategory.BREATHING) }, "burpees to oddech")
    }
}
