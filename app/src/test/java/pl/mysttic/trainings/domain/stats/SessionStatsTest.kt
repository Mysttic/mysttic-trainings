package pl.mysttic.trainings.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.engine.EngineState
import pl.mysttic.trainings.domain.engine.Phase
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.TargetKind
import java.time.LocalDate

class SessionStatsTest {

    private val now = 1_000_000_000_000L
    private val day = SessionStats.DAY_MS

    private fun session(
        id: String,
        startedAt: Long,
        status: SessionStatus = SessionStatus.DONE,
        durationSec: Int? = 600,
    ) = Session(
        id = id,
        workoutId = "w",
        workoutName = "Full body A",
        difficulty = Difficulty.INTERMEDIATE,
        startedAt = startedAt,
        endedAt = durationSec?.let { startedAt + it * 1000L },
        status = status,
    )

    private fun log(
        sessionId: String,
        exerciseId: String,
        actual: Int,
        kind: TargetKind = TargetKind.REPS,
        setIndex: Int = 1,
        name: String = exerciseId,
    ) = SetLog(
        sessionId = sessionId,
        exerciseId = exerciseId,
        exerciseName = name,
        blockIndex = 0,
        itemIndex = 0,
        round = 1,
        setIndex = setIndex,
        targetKind = kind,
        targetValue = actual,
        actualValue = actual,
        completedAt = now,
    )

    @Test
    fun `statystyki okresu licza tylko ukonczone sesje z zakresu`() {
        val sessions = listOf(
            session("a", now - 2 * day),
            session("b", now - 5 * day),
            session("stara", now - 20 * day),
            session("przerwana", now - day, status = SessionStatus.ABANDONED),
            session("w-toku", now, status = SessionStatus.IN_PROGRESS, durationSec = null),
        )
        val logs = listOf(
            log("a", "pushups", 12), log("a", "pushups", 10, setIndex = 2),
            log("a", "plank", 40, kind = TargetKind.SECONDS),
            log("b", "squats", 15),
            log("stara", "squats", 99),
            log("przerwana", "squats", 50),
        )

        val stats = SessionStats.period(sessions, logs, from = now - 7 * day)

        assertEquals(2, stats.sessions)
        assertEquals(1200, stats.durationSec)
        assertEquals(4, stats.sets)
        assertEquals(37, stats.reps)
        assertEquals(40, stats.workSec)
    }

    @Test
    fun `pusty okres daje zerowe statystyki`() {
        val stats = SessionStats.period(listOf(session("a", now - 30 * day)), emptyList(), from = now - 7 * day)

        assertEquals(PeriodStats(), stats)
        assertEquals(true, stats.isEmpty)
    }

    @Test
    fun `AMRAP liczy sie do powtorzen, nie do sekund pracy`() {
        val logs = listOf(log("a", "burpee", 18, kind = TargetKind.AMRAP))

        val stats = SessionStats.period(listOf(session("a", now)), logs, from = now - day)

        assertEquals(18, stats.reps)
        assertEquals(0, stats.workSec)
    }

    @Test
    fun `filtr laczy status i okres`() {
        val sessions = listOf(
            session("dzis", now - 1000),
            session("przerwana", now - 2 * day, status = SessionStatus.ABANDONED),
            session("stara", now - 40 * day),
        )

        assertEquals(listOf("dzis", "przerwana"), SessionStats.filter(sessions, null, HistoryPeriod.WEEK, now).map { it.id })
        assertEquals(listOf("dzis"), SessionStats.filter(sessions, SessionStatus.DONE, HistoryPeriod.WEEK, now).map { it.id })
        assertEquals(listOf("dzis", "stara"), SessionStats.filter(sessions, SessionStatus.DONE, HistoryPeriod.ALL, now).map { it.id })
        assertEquals(listOf("dzis", "przerwana"), SessionStats.filter(sessions, null, HistoryPeriod.MONTH, now).map { it.id })
    }

    @Test
    fun `seria liczy dni z rzedu wstecz od dzisiaj`() {
        val today = LocalDate.of(2026, 9, 6)
        val dates = listOf(today, today.minusDays(1), today.minusDays(2))

        assertEquals(3, SessionStats.streak(dates, today))
        assertEquals(3, SessionStats.bestStreak(dates, today))
    }

    @Test
    fun `brak treningu dzisiaj jeszcze nie kasuje serii, brak wczoraj kasuje`() {
        val today = LocalDate.of(2026, 9, 6)

        // trening wczoraj i przedwczoraj, dziś jeszcze nie: seria żyje
        assertEquals(2, SessionStats.streak(listOf(today.minusDays(1), today.minusDays(2)), today))
        // ostatni trening dwa dni temu: dzień przerwy skasował serię
        assertEquals(0, SessionStats.streak(listOf(today.minusDays(2), today.minusDays(3)), today))
        assertEquals(0, SessionStats.streak(emptyList(), today))
    }

    @Test
    fun `dwa treningi tego samego dnia to jeden dzien serii`() {
        val today = LocalDate.of(2026, 9, 6)
        val dates = listOf(today, today, today.minusDays(1))

        assertEquals(2, SessionStats.streak(dates, today))
    }

    @Test
    fun `rekord serii bierze najdluzszy ciag z calej historii`() {
        val today = LocalDate.of(2026, 9, 6)
        val dates = listOf(
            today.minusDays(10), today.minusDays(9), today.minusDays(8), today.minusDays(7),
            today.minusDays(3), today,
        )

        assertEquals(4, SessionStats.bestStreak(dates, today))
        assertEquals(1, SessionStats.streak(dates, today))
        assertEquals(0, SessionStats.bestStreak(emptyList(), today))
    }

    @Test
    fun `zaplanowany odpoczynek nie przerywa serii`() {
        val today = LocalDate.of(2026, 9, 6)
        // trening dziś i 2 dni temu, wczoraj plan przewidywał odpoczynek
        val dates = listOf(today, today.minusDays(2))
        val rest = setOf(today.minusDays(1))

        assertEquals(1, SessionStats.streak(dates, today))
        assertEquals(3, SessionStats.streak(dates, today) { it in rest })
    }

    @Test
    fun `pominiety dzien treningowy dalej kasuje serie`() {
        val today = LocalDate.of(2026, 9, 6)
        val dates = listOf(today, today.minusDays(2))
        // wczoraj plan czekał na trening, treningu nie było
        val rest = setOf(today.minusDays(3))

        assertEquals(1, SessionStats.streak(dates, today) { it in rest })
    }

    @Test
    fun `dzien odpoczynku dzisiaj podtrzymuje serie`() {
        val today = LocalDate.of(2026, 9, 6)
        val dates = listOf(today.minusDays(1), today.minusDays(2))
        val rest = setOf(today)

        assertEquals(3, SessionStats.streak(dates, today) { it in rest })
    }

    @Test
    fun `rekord liczony ta sama regula co seria`() {
        val today = LocalDate.of(2026, 9, 6)
        // ciąg: trening, odpoczynek, trening, odpoczynek, trening = 5 dni; potem przerwa i jeden trening dziś
        val dates = listOf(today.minusDays(10), today.minusDays(8), today.minusDays(6), today)
        val rest = setOf(today.minusDays(9), today.minusDays(7))

        assertEquals(5, SessionStats.bestStreak(dates, today) { it in rest })
        assertEquals(1, SessionStats.streak(dates, today) { it in rest })
    }

    @Test
    fun `delty per cwiczenie porownuja z poprzednim wykonaniem`() {
        val current = listOf(
            log("teraz", "pushups", 12, name = "Pompki"), log("teraz", "pushups", 11, setIndex = 2, name = "Pompki"),
            log("teraz", "plank", 45, kind = TargetKind.SECONDS, name = "Deska"),
            log("teraz", "squats", 20, name = "Przysiady"),
        )
        val previous = listOf(
            log("wcz", "pushups", 10, name = "Pompki"), log("wcz", "pushups", 10, setIndex = 2, name = "Pompki"),
            log("wcz", "plank", 60, kind = TargetKind.SECONDS, name = "Deska"),
            log("wcz", "lunges", 12, name = "Wykroki"),
        )

        val deltas = SessionStats.perExercise(current, previous)

        assertEquals(listOf("pushups", "plank", "squats"), deltas.map { it.exerciseId })
        val pushups = deltas.first { it.exerciseId == "pushups" }
        assertEquals(2, pushups.sets)
        assertEquals(23, pushups.total)
        assertEquals(20, pushups.previousTotal)
        assertEquals(3, pushups.delta)
        assertEquals("Pompki", pushups.exerciseName)

        val plank = deltas.first { it.exerciseId == "plank" }
        assertEquals(45, plank.total)
        assertEquals(-15, plank.delta)

        val squats = deltas.first { it.exerciseId == "squats" }
        assertNull(squats.previousTotal)
        assertNull(squats.delta)
    }

    @Test
    fun `brak poprzedniej sesji daje delty puste`() {
        val deltas = SessionStats.perExercise(listOf(log("teraz", "pushups", 12)), emptyList())

        assertEquals(1, deltas.size)
        assertNull(deltas.single().delta)
        assertEquals(12, deltas.single().total)
    }

    private fun work(exerciseId: String, setIndex: Int = 1) = Step.Work(
        blockIndex = 0, itemIndex = 0, round = 1, setIndex = setIndex, setsInItem = 2, exerciseId = exerciseId,
        exerciseName = exerciseId.uppercase(), targetKind = TargetKind.REPS, targetValue = 10,
    )

    private val steps = listOf(
        Step.Countdown(5),
        work("pushups", 1), Step.Rest(30, pl.mysttic.trainings.domain.model.RestKind.BETWEEN_SETS), work("pushups", 2),
        work("step-ups", 1), work("step-ups", 2),
        work("plank"),
        work("squats"),
    )

    private fun ended(phase: Phase, stepIndex: Int) =
        EngineState(sessionId = "s", difficulty = Difficulty.INTERMEDIATE, steps = steps, stepIndex = stepIndex, phase = phase)

    @Test
    fun `pominiete w calosci cwiczenie wchodzi do podsumowania z liczba serii, zrobione czesciowo nie`() {
        // Pompki: jedna seria zrobiona, druga pominięta; wejścia pominięte obie; deska i przysiady zrobione.
        val logs = listOf(log("s", "pushups", 10), log("s", "plank", 30, TargetKind.SECONDS), log("s", "squats", 10))

        val skipped = SessionStats.skipped(ended(Phase.FINISHED, steps.lastIndex), logs)

        assertEquals(listOf(SkippedExercise("step-ups", "STEP-UPS", 2)), skipped)
    }

    @Test
    fun `po przerwaniu krok, na ktorym stanal trening, i dalsze nie sa pominiete`() {
        // Przerwane na desce (indeks 6): wejścia bez logu są pominięte, deska i przysiady niezaczęte.
        val logs = listOf(log("s", "pushups", 10))

        assertEquals(listOf("step-ups"), SessionStats.skipped(ended(Phase.ABORTED, 6), logs).map { it.exerciseId })
        // Przerwane jeszcze na odliczaniu: nic nie jest pominięte.
        assertTrue(SessionStats.skipped(ended(Phase.ABORTED, 0), emptyList()).isEmpty())
        // Bez żadnej serii ukończony trening ma pominięte wszystkie ćwiczenia, w kolejności planu.
        assertEquals(
            listOf("pushups", "step-ups", "plank", "squats"),
            SessionStats.skipped(ended(Phase.FINISHED, steps.lastIndex), emptyList()).map { it.exerciseId },
        )
    }
}
