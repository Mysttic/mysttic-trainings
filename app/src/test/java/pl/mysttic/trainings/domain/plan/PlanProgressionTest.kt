package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup

class PlanProgressionTest {

    private fun review(done: Int, planned: Int, logged: Int, plannedSets: Int) =
        CycleReview(plannedSessions = planned, doneSessions = done, plannedSets = plannedSets, loggedSets = logged)

    @Test
    fun `trzymanie planu daje podbicie celow`() {
        val r = review(done = 16, planned = 18, logged = 500, plannedSets = 560)

        assertEquals(ProgressionStep.UP, PlanProgression.suggest(r))
        assertEquals(89, r.adherencePercent)
        assertEquals(89, r.setCompletionPercent)
    }

    @Test
    fun `polowa treningow to utrzymanie poziomu`() {
        val r = review(done = 10, planned = 18, logged = 300, plannedSets = 350)

        assertEquals(ProgressionStep.HOLD, PlanProgression.suggest(r))
    }

    @Test
    fun `rzadkie treningi to zejscie z celow`() {
        val r = review(done = 4, planned = 18, logged = 100, plannedSets = 140)

        assertEquals(ProgressionStep.DOWN, PlanProgression.suggest(r))
    }

    @Test
    fun `frekwencja bez zapisanych serii nie podbija celow`() {
        // wszystkie treningi „ukończone”, ale przeklikane: zero zapisanych serii
        val r = review(done = 18, planned = 18, logged = 0, plannedSets = 560)

        assertEquals(ProgressionStep.HOLD, PlanProgression.suggest(r))
    }

    @Test
    fun `pusty cykl nie zmienia niczego`() {
        assertEquals(ProgressionStep.HOLD, PlanProgression.suggest(CycleReview()))
        assertTrue(CycleReview().isEmpty)
    }

    @Test
    fun `mnoznik kumuluje sie i trzyma sie zakresu`() {
        assertEquals(1.1, PlanProgression.nextScale(1.0, ProgressionStep.UP), 0.001)
        assertEquals(1.21, PlanProgression.nextScale(1.1, ProgressionStep.UP), 0.001)
        assertEquals(1.0, PlanProgression.nextScale(1.0, ProgressionStep.HOLD), 0.001)
        assertEquals(0.9, PlanProgression.nextScale(1.0, ProgressionStep.DOWN), 0.001)

        assertEquals(PlanProgression.MAX_SCALE, PlanProgression.nextScale(1.55, ProgressionStep.UP), 0.001)
        assertEquals(PlanProgression.MIN_SCALE, PlanProgression.nextScale(0.72, ProgressionStep.DOWN), 0.001)
    }

    @Test
    fun `etykieta mnoznika po polsku`() {
        assertEquals("cele bez zmian", PlanProgression.scaleLabel(1.0))
        assertEquals("cele +10%", PlanProgression.scaleLabel(1.1))
        assertEquals("cele −10%", PlanProgression.scaleLabel(0.9))
    }

    @Test
    fun `opis mowi, co widac w danych`() {
        val r = review(done = 16, planned = 18, logged = 500, plannedSets = 560)

        val text = PlanProgression.explain(r, ProgressionStep.UP)

        assertTrue(text.contains("16 z 18"))
        assertTrue(text.contains("89%"))
        assertTrue(text.contains("podbijam"))
        assertTrue(PlanProgression.explain(CycleReview(), ProgressionStep.HOLD).contains("bez ukończonego treningu"))
    }

    // --- wpływ mnożnika na wygenerowany plan

    private fun exercise(id: String, primary: List<MuscleGroup>, kind: ExerciseKind = ExerciseKind.REPS) = Exercise(
        id = id, nameEn = id, namePl = id, kind = kind, level = Difficulty.INTERMEDIATE,
        category = ExerciseCategory.STRENGTH, equipment = emptySet<Equipment>(),
        primaryMuscles = primary, secondaryMuscles = emptyList(),
        instructionsPl = listOf("krok"), images = emptyList(), defaultReps = 10, defaultSeconds = 30,
    )

    private val library = listOf(
        exercise("squats", listOf(MuscleGroup.QUADS)),
        exercise("pushups", listOf(MuscleGroup.CHEST)),
        exercise("rows", listOf(MuscleGroup.LATS)),
        exercise("bridge", listOf(MuscleGroup.HAMSTRINGS)),
        exercise("plank", listOf(MuscleGroup.ABS), kind = ExerciseKind.TIME),
        exercise("climbers", listOf(MuscleGroup.FULL_BODY), kind = ExerciseKind.TIME),
    )

    private val profile = TrainingProfile(
        goal = Goal.STRENGTH, level = Difficulty.INTERMEDIATE,
        daysPerWeek = 3, sessionMinutes = 30, cycleDays = 21,
    )

    private fun repsIn(profile: TrainingProfile): List<Int> =
        PlanGenerator.generate(profile, library, now = 0L).workouts
            .flatMap { w -> w.blocks.flatMap { b -> b.items.map { it.targetValue } } }

    @Test
    fun `podbity mnoznik zwieksza cele w nowym cyklu`() {
        val base = repsIn(profile)
        val harder = repsIn(profile.copy(targetScale = 1.2))

        assertTrue(base.isNotEmpty())
        assertEquals(base.size, harder.size)
        assertTrue("cele nie urosły: $base -> $harder", harder.zip(base).all { (h, b) -> h >= b })
        assertTrue("żaden cel nie urósł: $base -> $harder", harder.zip(base).any { (h, b) -> h > b })
    }

    @Test
    fun `zejscie z mnoznika obniza cele, ale nie do zera`() {
        val easier = repsIn(profile.copy(targetScale = PlanProgression.MIN_SCALE))

        assertTrue(easier.isNotEmpty())
        assertTrue("cel poniżej minimum: $easier", easier.all { it >= 1 })
    }

    @Test
    fun `mnoznik trafia do opisu treningu`() {
        val plan = PlanGenerator.generate(profile.copy(targetScale = 1.1), library, now = 0L)

        assertTrue(plan.workouts.first().note.contains("cele +10%"))
        assertTrue(PlanGenerator.generate(profile, library, now = 0L).workouts.first().note.endsWith("średniozaawansowany."))
    }
}
