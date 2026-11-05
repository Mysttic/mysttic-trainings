package pl.mysttic.trainings.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.engine.EngineEvent
import pl.mysttic.trainings.domain.engine.EngineState
import pl.mysttic.trainings.domain.engine.Phase
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.engine.TestFixtures
import pl.mysttic.trainings.domain.engine.WorkoutEngine
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.TargetKind

class NotificationContentTest {

    private val planner = WorkoutPlanner()

    private fun started(now: Long = 1_000L): EngineState {
        val steps = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet(), countdownSec = 0)
        val idle = EngineState(sessionId = "s", difficulty = Difficulty.INTERMEDIATE, steps = steps)
        return WorkoutEngine.reduce(idle, EngineEvent.Start(now)).first
    }

    @Test
    fun `seria na powtórzenia liczy czas w górę od startu fazy i ma akcję Zrobione`() {
        val state = started(now = 1_000L)
        val content = NotificationContent.from(state, nowMono = 3_000L, nowWall = 50_000L)

        assertEquals("Pompki, seria 1 z 3", content.title)
        assertEquals("10 powtórzeń", content.text)
        assertNull(content.countDownTo)
        assertEquals(48_000L, content.countUpFrom)
        assertTrue(content.showDone)
        assertTrue(content.showPause)
        assertTrue(content.showSkip)
        assertFalse(content.showAddRest)
        assertFalse(content.showResume)
    }

    @Test
    fun `przerwa odlicza do końca fazy na zegarze ściennym i ma akcję +15 s`() {
        val afterSet = WorkoutEngine.reduce(started(1_000L), EngineEvent.Done(5_000L)).first
        assertTrue(afterSet.currentStep is Step.Rest)

        val content = NotificationContent.from(afterSet, nowMono = 7_000L, nowWall = 100_000L)

        assertEquals("Przerwa", content.title)
        assertEquals("Następnie: Pompki, 10 powtórzeń", content.text)
        assertEquals(100_000L + 28_000L, content.countDownTo)
        assertNull(content.countUpFrom)
        assertTrue(content.showAddRest)
        assertFalse(content.showDone)
    }

    @Test
    fun `pauza wyłącza chronometr i pokazuje Wznów zamiast Pauza`() {
        val paused = WorkoutEngine.reduce(started(1_000L), EngineEvent.Pause(4_000L)).first
        val content = NotificationContent.from(paused, nowMono = 6_000L, nowWall = 90_000L)

        assertTrue(content.title.endsWith("pauza"))
        assertNull(content.countDownTo)
        assertNull(content.countUpFrom)
        assertTrue(content.showResume)
        assertFalse(content.showPause)
    }

    @Test
    fun `seria na czas odlicza i nie ma akcji Zrobione`() {
        val work = Step.Work(
            blockIndex = 0, itemIndex = 1, round = 1, setIndex = 2, setsInItem = 3,
            exerciseId = "plank", exerciseName = "Deska", targetKind = TargetKind.SECONDS, targetValue = 30,
        )
        val state = EngineState(
            sessionId = "s", difficulty = Difficulty.INTERMEDIATE, steps = listOf(work),
            phase = Phase.RUNNING, phaseStartedAt = 1_000L, workoutStartedAt = 1_000L,
        )
        val content = NotificationContent.from(state, nowMono = 11_000L, nowWall = 200_000L)

        assertEquals("Deska, seria 2 z 3", content.title)
        assertEquals("30 sekund", content.text)
        assertEquals(220_000L, content.countDownTo)
        assertFalse(content.showDone)
    }

    @Test
    fun `ostatnia przerwa bez następnego kroku pracy dostaje tekst zastępczy`() {
        val rest = Step.Rest(seconds = 10, kind = pl.mysttic.trainings.domain.model.RestKind.BETWEEN_SETS)
        val state = EngineState(
            sessionId = "s", difficulty = Difficulty.INTERMEDIATE, steps = listOf(rest),
            phase = Phase.RUNNING, phaseStartedAt = 0L, workoutStartedAt = 0L,
        )
        assertEquals("Ostatni krok", NotificationContent.from(state, 1_000L, 1_000L).text)
    }

    @Test
    fun `zakończony i przerwany trening bez akcji i bez chronometru`() {
        val finished = started().copy(phase = Phase.FINISHED)
        val aborted = started().copy(phase = Phase.ABORTED)

        val done = NotificationContent.from(finished, 0L, 0L)
        val stopped = NotificationContent.from(aborted, 0L, 0L)

        assertEquals("Trening ukończony", done.title)
        assertEquals("Trening przerwany", stopped.title)
        for (c in listOf(done, stopped)) {
            assertNull(c.countDownTo)
            assertNull(c.countUpFrom)
            assertFalse(c.showPause || c.showResume || c.showDone || c.showAddRest || c.showSkip)
        }
    }

    @Test
    fun `obwód liczy rundy w tytule zamiast serii`() {
        val steps = planner.expand(TestFixtures.tabataWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet(), countdownSec = 0)
        val idle = EngineState(sessionId = "s", difficulty = Difficulty.INTERMEDIATE, steps = steps)
        var state = WorkoutEngine.reduce(idle, EngineEvent.Start(1_000L)).first
        // wspinaczka r1, przerwa, przysiady r1, przerwa: cztery pominięcia dają wspinaczkę w rundzie 2
        repeat(4) { state = WorkoutEngine.reduce(state, EngineEvent.Skip(2_000L + it * 1_000L)).first }
        val work = state.currentStep as Step.Work
        assertEquals(2, work.round)

        val content = NotificationContent.from(state, nowMono = 7_000L, nowWall = 100_000L)

        assertEquals("Wspinaczka, runda 2 z 4", content.title)
        assertEquals("20 sekund", content.text)
        assertFalse(content.showDone)
    }

    @Test
    fun `krok bez limitu liczy w górę i ma akcję Zrobione`() {
        val work = Step.Work(
            blockIndex = 0, itemIndex = 0, round = 1, setIndex = 1, setsInItem = 1,
            exerciseId = "breath-hold", exerciseName = "Zatrzymanie oddechu", targetKind = TargetKind.SECONDS, targetValue = 90,
            openEnded = true,
        )
        val state = EngineState(
            sessionId = "s", difficulty = Difficulty.INTERMEDIATE, steps = listOf(work),
            phase = Phase.RUNNING, phaseStartedAt = 1_000L, workoutStartedAt = 1_000L,
        )
        val content = NotificationContent.from(state, nowMono = 11_000L, nowWall = 200_000L)

        assertEquals("Zatrzymanie oddechu, seria 1 z 1", content.title)
        assertEquals("jak najdłużej", content.text)
        assertNull(content.countDownTo)
        assertEquals(190_000L, content.countUpFrom)
        assertTrue(content.showDone)
    }
}
