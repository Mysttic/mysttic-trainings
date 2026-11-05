package pl.mysttic.trainings.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.plan.PlanDay
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.TrainingProfile

class PlanStatsTest {

    private val program = Program(
        id = "p", name = "Plan", profile = TrainingProfile(), startedAt = 0, cycleDays = 9, createdAt = 0, active = true,
        days = listOf(
            PlanDay(1, "p-a"), PlanDay(2, null), PlanDay(3, "p-b"), PlanDay(4, null), PlanDay(5, "p-a"),
            PlanDay(6, null), PlanDay(7, null), PlanDay(8, "p-b"), PlanDay(9, null),
        ),
    )
    private val names = mapOf("p-a" to "Góra", "p-b" to "Dół")

    private fun session(id: String, workout: String, at: Long, status: SessionStatus = SessionStatus.DONE, planned: Int? = 4, rpe: Int? = null, note: String = "") =
        Session(id, workout, "x", Difficulty.INTERMEDIATE, at, at + 600_000, status, rpe = rpe, note = note, programId = "p", plannedSets = planned)

    private fun log(session: String, exercise: String, set: Int, actual: Int, target: Int = 10, at: Long, kind: TargetKind = TargetKind.REPS, weight: Double? = null, item: Int = 0) =
        SetLog(0, session, exercise, exercise, 0, item, 1, set, kind, target, actual, weight, at)

    private val sessions = listOf(
        session("s1", "p-a", 100, rpe = 6, note = "barki bolą"),
        session("s2", "p-b", 200, planned = null, rpe = 8),
        session("s3", "p-a", 300, status = SessionStatus.ABANDONED),
        session("s4", "p-a", 400),
    )
    private val logs = listOf(
        log("s1", "pushups", 1, 10, at = 101, weight = 10.0),
        log("s1", "pushups", 2, 8, at = 102),
        // „Wstecz” w playerze: druga seria zapisana jeszcze raz, liczy się ostatni zapis.
        log("s1", "pushups", 2, 10, at = 103),
        log("s2", "plank", 1, 60, target = 45, at = 201, kind = TargetKind.SECONDS),
        log("s2", "burpees", 1, 22, target = 60, at = 202, kind = TargetKind.AMRAP, item = 1),
        log("s4", "pushups", 1, 12, at = 401, weight = 12.5),
    )

    @Test
    fun `dni kolejki, frekwencja i przerwane osobno`() {
        val stats = PlanStatsCalculator.compute(program, names, sessions, logs, plannedFallback = mapOf("p-b" to 3))

        assertEquals(3, stats.doneCount)
        assertEquals(4, stats.totalCount)
        assertEquals(75, stats.attendancePct)
        assertEquals(1, stats.abandoned)
        assertEquals(listOf("s1", null, "s2", null, "s4", null, null, null, null), stats.days.map { it.sessionId })
        assertEquals(2, stats.days[0].loggedSets)
        assertEquals(3, stats.days[2].plannedSets)
        assertTrue(stats.days[2].plannedEstimated)
        assertEquals(7.0, stats.averageRpe!!, 0.001)
        assertEquals(listOf(100L to "barki bolą"), stats.notes)
    }

    @Test
    fun `seria zapisana dwa razy liczy sie raz, AMRAP poza celem`() {
        val stats = PlanStatsCalculator.compute(program, names, sessions, logs)
        val pushups = stats.exercises.first { it.exerciseId == "pushups" }
        assertEquals(3, pushups.sets)
        assertEquals(3, pushups.hitSets)
        assertEquals(100, pushups.hitRate)
        assertEquals(10, pushups.first)
        assertEquals(12, pushups.last)
        assertEquals(12.5, pushups.plannedWeightKg)
        val burpees = stats.exercises.first { it.exerciseId == "burpees" }
        assertNull(burpees.hitRate)
    }

    @Test
    fun `tygodnie z kolejki, nie z kalendarza`() {
        val stats = PlanStatsCalculator.compute(program, names, sessions, logs)
        assertEquals(2, stats.weeks.size)
        assertEquals(3, stats.weeks[0].done)
        assertEquals(3, stats.weeks[0].planned)
        assertEquals(0, stats.weeks[1].done)
        assertEquals(1, stats.weeks[1].planned)
        assertEquals(60, stats.weeks[0].workSec)
    }

    @Test
    fun `trening ponad plan liczy sie osobno, reszta raportu ma jedna podstawe`() {
        val single = program.copy(days = listOf(PlanDay(1, "p-a"), PlanDay(2, null)))
        val twice = listOf(
            session("a1", "p-a", 100, rpe = 6, note = "pierwszy"),
            session("a2", "p-a", 5_000_000, rpe = 10, note = "drugi"),
        )
        val twiceLogs = listOf(
            log("a1", "pushups", 1, 10, at = 101),
            log("a2", "pushups", 1, 15, at = 5_000_001),
        )

        val stats = PlanStatsCalculator.compute(single, names, twice, twiceLogs)

        assertEquals(1, stats.doneCount)
        assertEquals(1, stats.extraCount)
        // Czas, RPE, okres, ćwiczenia i notatki tylko z sesji przypisanej do dnia, jak tabela „Dzień po dniu”.
        assertEquals(600, stats.durationSec)
        assertEquals(stats.days.sumOf { it.durationSec ?: 0 }, stats.durationSec)
        assertEquals(6.0, stats.averageRpe!!, 0.001)
        assertEquals(100L, stats.lastDoneAt)
        assertEquals(1, stats.exercises.single().sets)
        assertEquals(listOf(100L to "pierwszy"), stats.notes)
    }

    @Test
    fun `bez nadwyzki licznik ponad plan stoi na zerze`() {
        assertEquals(0, PlanStatsCalculator.compute(program, names, sessions, logs).extraCount)
    }

    @Test
    fun `kompletnosc serii tylko z treningow ze znana liczba serii`() {
        val stats = PlanStatsCalculator.compute(program, names, sessions, logs)
        // s2 bez plannedSets i bez zapasu: poza licznikiem i mianownikiem.
        assertEquals(3, stats.loggedSets)
        assertEquals(8, stats.plannedSets)
        assertEquals(37, stats.completionPct)
    }
}
