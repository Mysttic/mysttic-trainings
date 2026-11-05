package pl.mysttic.trainings.domain.equipment

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.mysttic.trainings.domain.engine.TestFixtures
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup

class EquipmentMatcherTest {

    private val pushups = TestFixtures.pushups
    private val dbPress = TestFixtures.exercise("db-floor-press", "Wyciskanie hantli", equipment = setOf(Equipment.DUMBBELLS))
    private val dips = TestFixtures.exercise("bench-dips", "Dipy na krześle", equipment = setOf(Equipment.BENCH), primary = listOf(MuscleGroup.TRICEPS))
    private val stepUps = TestFixtures.exercise("db-step-ups", "Wejścia z hantlami", equipment = setOf(Equipment.DUMBBELLS, Equipment.BENCH), primary = listOf(MuscleGroup.QUADS))
    private val plankChest = TestFixtures.exercise("plyo-pushups", "Pompki plyo", level = Difficulty.ADVANCED)
    private val all = listOf(pushups, dbPress, dips, stepUps, plankChest, TestFixtures.squats)

    @Test
    fun `missing equipment lists what the user lacks`() {
        assertEquals(setOf(Equipment.BENCH), stepUps.missingEquipment(setOf(Equipment.DUMBBELLS)))
        assertEquals(emptySet<Equipment>(), pushups.missingEquipment(emptySet()))
    }

    @Test
    fun `substitutes prefer same muscle, same kind, closest level, no equipment`() {
        val subs = EquipmentMatcher.substitutes(dbPress, all, owned = emptySet())
        assertEquals(listOf("pushups", "plyo-pushups"), subs.map { it.id })
    }

    @Test
    fun `substitutes respect owned equipment`() {
        val bandPress = TestFixtures.exercise("band-press", "Wyciskanie gumy", equipment = setOf(Equipment.RESISTANCE_BAND))
        val subs = EquipmentMatcher.substitutes(dbPress, all + bandPress, owned = setOf(Equipment.RESISTANCE_BAND))
        // ten sam poziom z posiadanym sprzętem wygrywa z trudniejszym wariantem bez sprzętu
        assertEquals(listOf("pushups", "band-press", "plyo-pushups"), subs.map { it.id })
    }

    @Test
    fun `time based exercise prefers time based substitute`() {
        val plankBand = TestFixtures.exercise("band-plank", "Deska z gumą", kind = ExerciseKind.TIME, equipment = setOf(Equipment.RESISTANCE_BAND), primary = listOf(MuscleGroup.ABS))
        val crunch = TestFixtures.exercise("crunch", "Brzuszki", primary = listOf(MuscleGroup.ABS))
        val subs = EquipmentMatcher.substitutes(plankBand, listOf(TestFixtures.plank, crunch, plankBand), owned = emptySet())
        assertEquals(listOf("plank", "crunch"), subs.map { it.id })
    }

    @Test
    fun `workout missing map only lists exercises lacking equipment`() {
        val exercises = all.associateBy { it.id }
        val workout = TestFixtures.setsWorkout().let { w ->
            w.copy(blocks = listOf(w.blocks[0].copy(items = w.blocks[0].items + w.blocks[0].items[0].copy(exerciseId = "db-step-ups"))))
        }
        val missing = EquipmentMatcher.missingInWorkout(workout, exercises, owned = setOf(Equipment.BENCH))
        assertEquals(mapOf("db-step-ups" to setOf(Equipment.DUMBBELLS)), missing)
    }

    @Test
    fun `ergometr zastepuje tylko inny ergometr`() {
        val bieg = TestFixtures.exercise("run-in-place", kind = ExerciseKind.TIME, primary = listOf(MuscleGroup.QUADS))
        val rower = TestFixtures.exercise("bike-easy", kind = ExerciseKind.TIME, equipment = setOf(Equipment.STATIONARY_BIKE), primary = listOf(MuscleGroup.QUADS))
        val bieznia = TestFixtures.exercise("treadmill-run", kind = ExerciseKind.TIME, equipment = setOf(Equipment.TREADMILL), primary = listOf(MuscleGroup.QUADS))
        val owned = setOf(Equipment.STATIONARY_BIKE)

        // Zwykłe ćwiczenie nie dostaje maszyny, nawet posiadanej: tak trafiłaby do planu z kreatora przez „Nie chcę”.
        assertEquals(emptyList<String>(), EquipmentMatcher.substitutes(bieg, listOf(bieg, rower), owned).map { it.id })
        // Brak bieżni: zamiennikiem jest rower, który masz, a przed nim ćwiczenie bez sprzętu.
        assertEquals(listOf("run-in-place", "bike-easy"), EquipmentMatcher.substitutes(bieznia, listOf(bieg, rower, bieznia), owned).map { it.id })
    }

    @Test
    fun `cwiczenie z biblioteki wygrywa z wlasnym albo z pliku planu`() {
        // „Aaa” z pliku planu wygrałoby samą nazwą; biblioteka ma pierwszeństwo.
        val zPliku = TestFixtures.exercise("custom-abc", "Aaa pompki z pliku").copy(isCustom = true)
        val subs = EquipmentMatcher.substitutes(dbPress, listOf(zPliku, pushups), owned = emptySet())

        assertEquals(listOf("pushups", "custom-abc"), subs.map { it.id })
    }

    @Test
    fun `wycofane z bazy nie jest zamiennikiem`() {
        // Wycofanie (D-47) nie może wrócić tylnymi drzwiami: przez brakujący sprzęt albo „Nie chcę” u trenera.
        val wycofanePompki = pushups.copy(retiredAt = "2026-10-28")
        val subs = EquipmentMatcher.substitutes(dbPress, listOf(wycofanePompki, plankChest), owned = emptySet())

        assertEquals(listOf("plyo-pushups"), subs.map { it.id })
    }
}
