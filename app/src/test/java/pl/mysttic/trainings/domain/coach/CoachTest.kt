package pl.mysttic.trainings.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.domain.model.TargetKind

class CoachTest {

    private fun exercise(
        id: String,
        primary: List<MuscleGroup> = listOf(MuscleGroup.CHEST),
        kind: ExerciseKind = ExerciseKind.REPS,
        equipment: Set<Equipment> = emptySet(),
        level: Difficulty = Difficulty.INTERMEDIATE,
        easierId: String? = null,
    ) = Exercise(
        id = id, nameEn = id, namePl = id, kind = kind, level = level, category = ExerciseCategory.STRENGTH,
        equipment = equipment, primaryMuscles = primary, secondaryMuscles = emptyList(),
        instructionsPl = listOf("krok"), images = emptyList(), easierId = easierId, defaultReps = 12, defaultSeconds = 30,
    )

    private fun item(id: String, value: Int = 10, rest: Int = 10) =
        BlockItem(exerciseId = id, targetKind = TargetKind.REPS, targetValue = value, sets = 1, restAfterSetSec = 75, restAfterItemSec = rest)

    private fun circuit(vararg items: BlockItem) = Block(kind = BlockKind.CIRCUIT, rounds = 2, restBetweenRoundsSec = 40, items = items.toList())

    private val pushups = exercise("pushups", easierId = "knee-pushups")
    private val kneePushups = exercise("knee-pushups", level = Difficulty.BEGINNER)
    private val widePushups = exercise("wide-pushups")
    private val diamondPushups = exercise("diamond-pushups", level = Difficulty.ADVANCED)
    private val benchDips = exercise("bench-dips", equipment = setOf(Equipment.BENCH))
    private val squats = exercise("squats", primary = listOf(MuscleGroup.QUADS))
    private val plank = exercise("plank", primary = listOf(MuscleGroup.ABS), kind = ExerciseKind.TIME)
    private val library = listOf(pushups, kneePushups, widePushups, diamondPushups, benchDips, squats, plank).associateBy { it.id }

    @Test
    fun `opinia przesuwa cel o staly krok i kumuluje sie`() {
        val once = Coach.apply(ExerciseTuning("pushups"), ExerciseFeedback.LESS, now = 1L)
        assertEquals(-2, once.repsDelta)
        assertEquals(-5, once.secondsDelta)
        assertEquals(1L, once.updatedAt)

        val twice = Coach.apply(once, ExerciseFeedback.LESS, now = 2L)
        assertEquals(-4, twice.repsDelta)
        assertEquals(-10, twice.secondsDelta)

        val back = Coach.apply(twice, ExerciseFeedback.MORE, now = 3L)
        assertEquals(-2, back.repsDelta)
        assertFalse(back.excluded)

        assertEquals(twice, Coach.apply(twice, ExerciseFeedback.NONE, now = 9L))
    }

    @Test
    fun `korekta dziala na cel po skalowaniu i nie schodzi ponizej minimum`() {
        val tuning = ExerciseTuning("x", repsDelta = -2, secondsDelta = -5)

        assertEquals(4, tuning.adjust(TargetKind.REPS, 6))
        assertEquals(8, tuning.adjust(TargetKind.REPS, 10))
        assertEquals(1, tuning.adjust(TargetKind.REPS, 2))
        assertEquals(15, tuning.adjust(TargetKind.SECONDS, 20))
        assertEquals(5, tuning.adjust(TargetKind.SECONDS, 8))
        assertEquals(25, tuning.adjust(TargetKind.AMRAP, 30))
        assertTrue(ExerciseTuning("y").isNeutral)
        assertFalse(tuning.isNeutral)
    }

    @Test
    fun `krok nie wychodzi poza widelki`() {
        var tuning = ExerciseTuning("x")
        repeat(30) { tuning = Coach.apply(tuning, ExerciseFeedback.MORE, now = it.toLong()) }
        assertEquals(Coach.MAX_REPS_DELTA, tuning.repsDelta)
        assertEquals(Coach.MAX_SECONDS_DELTA, tuning.secondsDelta)
    }

    @Test
    fun `nie chce wyklucza, ale nie kasuje korekty`() {
        val tuned = ExerciseTuning("x", repsDelta = 2)
        val never = Coach.apply(tuned, ExerciseFeedback.NEVER, now = 5L)
        assertTrue(never.excluded)
        assertEquals(2, never.repsDelta)
        assertEquals(5L, never.updatedAt)
    }

    @Test
    fun `podpis korekty`() {
        assertNull(Coach.label(null, TargetKind.REPS))
        assertNull(Coach.label(ExerciseTuning("x"), TargetKind.REPS))
        assertEquals("trener +2 powt.", Coach.label(ExerciseTuning("x", repsDelta = 2, secondsDelta = 5), TargetKind.REPS))
        assertEquals("trener −5 s", Coach.label(ExerciseTuning("x", repsDelta = -2, secondsDelta = -5), TargetKind.SECONDS))
    }

    @Test
    fun `wykluczone cwiczenie dostaje zamiennik na te sama partie z posiadanym sprzetem`() {
        val blocks = listOf(circuit(item("pushups", value = 12, rest = 10), item("squats", value = 15)))

        val result = Coach.withoutExcluded(blocks, library, owned = emptySet(), excluded = setOf("pushups"))

        val first = result.single().items[0]
        assertEquals("wide-pushups", first.exerciseId)
        assertEquals(12, first.targetValue) // domyślne powtórzenia zamiennika
        assertEquals(10, first.restAfterItemSec) // przerwa pozycji zostaje
        assertEquals(item("squats", value = 15), result.single().items[1])
        assertEquals(2, result.single().rounds)
    }

    @Test
    fun `zamiennik nie powtarza cwiczenia juz obecnego w treningu ani nie wymaga brakujacego sprzetu`() {
        val blocks = listOf(circuit(item("pushups"), item("wide-pushups")))

        val result = Coach.withoutExcluded(blocks, library, owned = emptySet(), excluded = setOf("pushups"))

        assertEquals(listOf("diamond-pushups", "wide-pushups"), result.single().items.map { it.exerciseId })
    }

    @Test
    fun `staly blok zostaje nietkniety mimo wykluczenia`() {
        val blocks = listOf(circuit(item("pushups")).copy(fixed = true), circuit(item("pushups")))

        val result = Coach.withoutExcluded(blocks, library, owned = emptySet(), excluded = setOf("pushups"))

        assertEquals("pushups", result[0].items.single().exerciseId)
        assertEquals("wide-pushups", result[1].items.single().exerciseId)
    }

    @Test
    fun `bez zamiennika pozycja znika, pusty blok tez`() {
        val tiny = listOf(pushups, squats).associateBy { it.id }
        val blocks = listOf(circuit(item("pushups")), circuit(item("squats")))

        val result = Coach.withoutExcluded(blocks, tiny, owned = emptySet(), excluded = setOf("pushups"))

        assertEquals(1, result.size)
        assertEquals("squats", result.single().items.single().exerciseId)
    }

    @Test
    fun `opinia o wariancie poziomu podmienia pozycje bazowa`() {
        val blocks = listOf(circuit(item("pushups"), item("squats")))
        val resolve: (Exercise) -> Exercise = { if (it.id == "pushups") kneePushups else it }

        val result = Coach.withoutExcluded(blocks, library, owned = emptySet(), excluded = setOf("knee-pushups"), resolve = resolve)

        assertEquals("wide-pushups", result.single().items[0].exerciseId)
    }

    @Test
    fun `bez wykluczen i bez trafien wraca ta sama lista`() {
        val blocks = listOf(circuit(item("squats"), item("plank")))

        assertSame(blocks, Coach.withoutExcluded(blocks, library, owned = emptySet(), excluded = emptySet()))
        assertSame(blocks, Coach.withoutExcluded(blocks, library, owned = emptySet(), excluded = setOf("pushups")))
    }
}
