package pl.mysttic.trainings.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout

/** Kiedy przycisk głośności ma znaczyć „Zrobione” (M6). */
class AwaitingRepsDoneTest {

    private val planner = WorkoutPlanner()

    private fun started(): EngineState {
        val state = EngineState(
            sessionId = "s1",
            difficulty = Difficulty.INTERMEDIATE,
            steps = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet()),
        )
        return WorkoutEngine.reduce(state, EngineEvent.Start(0L)).first
    }

    @Test
    fun `odliczanie i przerwa oddaja klawisz systemowi`() {
        var s = started()
        assertTrue(s.currentStep is Step.Countdown)
        assertFalse(s.awaitingRepsDone)

        // odliczanie mija, wchodzi seria na powtórzenia
        s = WorkoutEngine.reduce(s, EngineEvent.Tick(5_000L)).first
        assertTrue(s.awaitingRepsDone)

        // po potwierdzeniu serii jest przerwa — znowu klawisz należy do systemu
        s = WorkoutEngine.reduce(s, EngineEvent.Done(6_000L, null)).first
        assertTrue(s.currentStep is Step.Rest)
        assertFalse(s.awaitingRepsDone)
    }

    @Test
    fun `pauza i koniec treningu oddaja klawisz`() {
        var s = WorkoutEngine.reduce(started(), EngineEvent.Tick(5_000L)).first
        assertTrue(s.awaitingRepsDone)

        s = WorkoutEngine.reduce(s, EngineEvent.Pause(6_000L)).first
        assertFalse(s.awaitingRepsDone)

        s = WorkoutEngine.reduce(s, EngineEvent.Resume(7_000L)).first
        assertTrue(s.awaitingRepsDone)

        s = WorkoutEngine.reduce(s, EngineEvent.Abort(8_000L)).first
        assertFalse(s.awaitingRepsDone)
    }

    @Test
    fun `seria na czas konczy sie sama, wiec klawisz jej nie dotyczy`() {
        var s = WorkoutEngine.reduce(started(), EngineEvent.Tick(5_000L)).first
        // przewijamy do pierwszego kroku na czas (deska w drugim elemencie bloku)
        var guard = 0
        while (guard++ < 200 && !(s.currentStep as? Step.Work).let { it != null && it.durationSec != null }) {
            s = WorkoutEngine.reduce(s, EngineEvent.Skip(10_000L + guard * 1_000L)).first
        }
        assertTrue((s.currentStep as Step.Work).durationSec != null)
        assertFalse(s.awaitingRepsDone)
    }

    @Test
    fun `krok bez limitu czeka na klawisz jak seria na powtorzenia`() {
        val workout = Workout(
            id = "w-open", name = "Oddech", blocks = listOf(
                Block(kind = BlockKind.SETS, fixed = true, items = listOf(BlockItem("plank", TargetKind.SECONDS, 90, sets = 1, openEnded = true))),
            ), createdAt = 0L, updatedAt = 0L,
        )
        val idle = EngineState(
            sessionId = "s2",
            difficulty = Difficulty.INTERMEDIATE,
            steps = planner.expand(workout, TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet()),
        )
        var s = WorkoutEngine.reduce(idle, EngineEvent.Start(0L)).first
        s = WorkoutEngine.reduce(s, EngineEvent.Tick(5_000L)).first
        assertTrue((s.currentStep as Step.Work).openEnded)
        assertTrue(s.awaitingRepsDone)

        // klawisz to Done bez wartości: loguje czas, który minął, i idzie dalej
        val (after, fx) = WorkoutEngine.reduce(s, EngineEvent.Done(45_000L, null))
        assertEquals(40, fx.filterIsInstance<EngineEffect.LogSet>().single().result.actualValue)
        assertTrue(after.isOver)
        assertFalse(after.awaitingRepsDone)
    }
}
