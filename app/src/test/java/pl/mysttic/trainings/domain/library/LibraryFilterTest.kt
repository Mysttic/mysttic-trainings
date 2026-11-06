package pl.mysttic.trainings.domain.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.engine.TestFixtures
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.MuscleGroup

class LibraryFilterTest {

    private val pushups = TestFixtures.pushups      // klatka, średniozaawansowany, siła, bez sprzętu
    private val squats = TestFixtures.squats        // czworogłowe, początkujący
    private val plank = TestFixtures.plank          // brzuch, początkujący, czas
    private val climbers = TestFixtures.climbers    // czworogłowe, kardio, czas
    private val dbPress = TestFixtures.exercise("db-floor-press", "Wyciskanie hantli", equipment = setOf(Equipment.DUMBBELLS))
    private val stepUps = TestFixtures.exercise(
        "db-step-ups", "Wejścia z hantlami",
        equipment = setOf(Equipment.DUMBBELLS, Equipment.BENCH), primary = listOf(MuscleGroup.QUADS),
    )
    private val dips = TestFixtures.exercise("bench-dips", "Dipy na krześle", equipment = setOf(Equipment.BENCH), primary = listOf(MuscleGroup.TRICEPS))
    private val all = listOf(pushups, squats, plank, climbers, dbPress, stepUps, dips)

    private fun ids(filter: LibraryFilter, owned: Set<Equipment> = emptySet()) = filter.apply(all, owned).map { it.id }

    @Test
    fun `empty filter keeps everything in order`() {
        assertEquals(all, LibraryFilter().apply(all, emptySet()))
        assertEquals(0, LibraryFilter().selectedCount)
        assertFalse(LibraryFilter().hasSelection)
    }

    @Test
    fun `panel count adds my equipment to chips because both live in the panel`() {
        assertEquals(0, LibraryFilter(query = "pomp").panelCount)
        assertEquals(1, LibraryFilter(onlyOwned = true).panelCount)
        val mixed = LibraryFilter(onlyOwned = true, noEquipment = true, equipment = setOf(Equipment.DUMBBELLS), levels = setOf(Difficulty.BEGINNER))
        assertEquals(3, mixed.selectedCount)
        assertEquals(4, mixed.panelCount)
        assertEquals(0, mixed.cleared().panelCount)
    }

    @Test
    fun `no equipment keeps only bodyweight`() {
        assertEquals(listOf("pushups", "squats", "plank", "climbers"), ids(LibraryFilter(noEquipment = true)))
    }

    @Test
    fun `my equipment requires all pieces`() {
        val mine = LibraryFilter(onlyOwned = true)
        assertEquals(listOf("pushups", "squats", "plank", "climbers", "db-floor-press"), ids(mine, setOf(Equipment.DUMBBELLS)))
        assertTrue("db-step-ups" in ids(mine, setOf(Equipment.DUMBBELLS, Equipment.BENCH)))
        assertTrue(mine.hasSelection)
        assertEquals(0, mine.selectedCount)
    }

    @Test
    fun `equipment values join with or and no equipment adds bodyweight`() {
        assertEquals(listOf("db-floor-press", "db-step-ups", "bench-dips"), ids(LibraryFilter(equipment = setOf(Equipment.DUMBBELLS, Equipment.BENCH))))
        val dumbbellsOnly = LibraryFilter(noEquipment = true, equipment = setOf(Equipment.DUMBBELLS))
        assertEquals(listOf("pushups", "squats", "plank", "climbers", "db-floor-press", "db-step-ups"), ids(dumbbellsOnly))
        assertEquals(2, dumbbellsOnly.selectedCount)
    }

    @Test
    fun `dimensions join with and`() {
        assertEquals(listOf("climbers"), ids(LibraryFilter(categories = setOf(ExerciseCategory.CARDIO), muscles = setOf(MuscleGroup.QUADS))))
        assertEquals(listOf("squats"), ids(LibraryFilter(levels = setOf(Difficulty.BEGINNER), muscles = setOf(MuscleGroup.QUADS))))
        assertEquals(listOf("db-floor-press"), ids(LibraryFilter(onlyOwned = true, equipment = setOf(Equipment.DUMBBELLS)), setOf(Equipment.DUMBBELLS)))
        assertEquals(listOf("squats", "plank"), ids(LibraryFilter(levels = setOf(Difficulty.BEGINNER), muscles = setOf(MuscleGroup.QUADS, MuscleGroup.ABS))))
    }

    @Test
    fun `query matches polish or english name ignoring case and spaces`() {
        assertEquals(listOf("squats"), ids(LibraryFilter(query = " PRZYS ")))
        assertEquals(listOf("climbers"), ids(LibraryFilter(query = "climb")))
        assertEquals(listOf("db-floor-press", "db-step-ups"), ids(LibraryFilter(query = "hantl")))
        assertEquals(emptyList<String>(), ids(LibraryFilter(query = "sztanga")))
    }

    @Test
    fun `query is not a selection and survives clearing`() {
        val filter = LibraryFilter(query = "pomp", onlyOwned = true, categories = setOf(ExerciseCategory.STRENGTH), muscles = setOf(MuscleGroup.CHEST))
        assertEquals(2, filter.selectedCount)
        assertEquals(LibraryFilter(query = "pomp"), filter.cleared())
        assertFalse(filter.cleared().hasSelection)
    }

    @Test
    fun `toggles add and remove values`() {
        val once = LibraryFilter().toggleMuscle(MuscleGroup.ABS).toggleCategory(ExerciseCategory.CARDIO)
            .toggleLevel(Difficulty.ADVANCED).toggleEquipment(Equipment.BENCH)
        assertEquals(
            LibraryFilter(
                equipment = setOf(Equipment.BENCH), categories = setOf(ExerciseCategory.CARDIO),
                levels = setOf(Difficulty.ADVANCED), muscles = setOf(MuscleGroup.ABS),
            ),
            once,
        )
        val twice = once.toggleMuscle(MuscleGroup.ABS).toggleCategory(ExerciseCategory.CARDIO)
            .toggleLevel(Difficulty.ADVANCED).toggleEquipment(Equipment.BENCH)
        assertEquals(LibraryFilter(), twice)
    }

    @Test
    fun `facets list only present values in enum order`() {
        val facets = LibraryFacets.of(all)
        assertEquals(listOf(Equipment.DUMBBELLS, Equipment.BENCH), facets.equipment)
        assertEquals(listOf(ExerciseCategory.STRENGTH, ExerciseCategory.CARDIO), facets.categories)
        assertEquals(listOf(Difficulty.BEGINNER, Difficulty.INTERMEDIATE), facets.levels)
        assertEquals(listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS, MuscleGroup.ABS, MuscleGroup.QUADS), facets.muscles)
        assertEquals(LibraryFacets(), LibraryFacets.of(emptyList()))
    }

    @Test
    fun `biblioteka nie pokazuje wycofanych z bazy`() {
        val wycofanePrzysiady = squats.copy(retiredAt = "2026-10-28")
        val widoczne = LibraryFilter.visible(listOf(pushups, wycofanePrzysiady, plank))

        assertEquals(listOf("pushups", "plank"), widoczne.map { it.id })
        // Chipy liczą się z tego, co widać: partia samego wycofanego ćwiczenia znika z panelu.
        assertFalse(MuscleGroup.QUADS in LibraryFacets.of(widoczne).muscles)
    }
}
