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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ProgressStatsTest {

    private val today = LocalDate.of(2026, 9, 9) // środa
    private val zeroDate = LocalDate.of(2026, 1, 1)

    /** Sztuczny „zegar”: milisekundy traktujemy jako liczbę dni od 2026-01-01, żeby testy były czytelne. */
    private val toDate: (Long) -> LocalDate = { zeroDate.plusDays(it) }

    private fun at(date: LocalDate): Long = ChronoUnit.DAYS.between(zeroDate, date)

    private fun session(id: String, date: LocalDate, status: SessionStatus = SessionStatus.DONE) = Session(
        id = id, workoutId = "w", workoutName = "Trening", difficulty = Difficulty.INTERMEDIATE,
        startedAt = at(date), endedAt = at(date), status = status,
    )

    private fun log(
        sessionId: String,
        exerciseId: String,
        actual: Int,
        date: LocalDate,
        kind: TargetKind = TargetKind.REPS,
    ) = SetLog(
        sessionId = sessionId, exerciseId = exerciseId, exerciseName = exerciseId,
        blockIndex = 0, itemIndex = 0, round = 1, setIndex = 1,
        targetKind = kind, targetValue = actual, actualValue = actual, completedAt = at(date),
    )

    @Test
    fun `wolumen tygodniowy zwraca pelny zakres z pustymi tygodniami`() {
        val sessions = listOf(session("a", today), session("b", today.minusWeeks(2)))
        val logs = listOf(
            log("a", "pushups", 12, today), log("a", "plank", 60, today, TargetKind.SECONDS),
            log("b", "squats", 20, today.minusWeeks(2)),
        )

        val weeks = ProgressStats.weeklyVolume(sessions, logs, today, weeks = 4, toDate = toDate)

        assertEquals(4, weeks.size)
        assertEquals(listOf(0, 1, 0, 1), weeks.map { it.sessions })
        assertEquals(today.with(DayOfWeek.MONDAY), weeks.last().weekStart)
        assertEquals(12, weeks.last().reps)
        assertEquals(60, weeks.last().workSec)
        // wolumen łączy powtórzenia i sekundy: 12 + 60/3
        assertEquals(32, weeks.last().volume)
    }

    @Test
    fun `wolumen pomija sesje nieukonczone`() {
        val sessions = listOf(session("a", today, SessionStatus.ABANDONED), session("b", today, SessionStatus.IN_PROGRESS))
        val logs = listOf(log("a", "pushups", 12, today))

        val weeks = ProgressStats.weeklyVolume(sessions, logs, today, weeks = 2, toDate = toDate)

        assertEquals(0, weeks.sumOf { it.sessions })
        assertEquals(0, weeks.sumOf { it.volume })
    }

    @Test
    fun `historia cwiczenia bierze najlepsza serie z dnia`() {
        val logs = listOf(
            log("a", "pushups", 10, today.minusDays(4)),
            log("a", "pushups", 12, today.minusDays(4)),
            log("b", "pushups", 11, today),
            log("b", "squats", 30, today),
        )

        val history = ProgressStats.exerciseHistory(logs, "pushups", toDate)

        assertEquals(2, history.size)
        assertEquals(listOf(today.minusDays(4), today), history.map { it.date })
        assertEquals(listOf(12, 11), history.map { it.best })
        assertEquals(12, ProgressStats.personalBest(history)?.best)
    }

    @Test
    fun `historia pomija serie zerowe i nieznane cwiczenia`() {
        val logs = listOf(log("a", "pushups", 0, today), log("a", "squats", 20, today))

        assertTrue(ProgressStats.exerciseHistory(logs, "pushups", toDate).isEmpty())
        assertNull(ProgressStats.personalBest(emptyList()))
        assertTrue(ProgressStats.exerciseHistory(logs, "burpee", toDate).isEmpty())
    }

    @Test
    fun `kalendarz ma stala dlugosc i konczy sie dzisiaj`() {
        val sessions = listOf(session("a", today), session("b", today), session("c", today.minusDays(10)))

        val heat = ProgressStats.heatmap(sessions, today, days = 14, toDate = toDate)

        assertEquals(14, heat.size)
        assertEquals(today, heat.last().date)
        assertEquals(today.minusDays(13), heat.first().date)
        assertEquals(2, heat.last().sessions)
        assertEquals(1, heat.first { it.date == today.minusDays(10) }.sessions)
        assertEquals(0, heat.first { it.date == today.minusDays(1) }.sessions)
    }

    @Test
    fun `puste dane nie wywracaja wykresow`() {
        assertTrue(ProgressStats.weeklyVolume(emptyList(), emptyList(), today, weeks = 0, toDate = toDate).isEmpty())
        assertEquals(4, ProgressStats.weeklyVolume(emptyList(), emptyList(), today, weeks = 4, toDate = toDate).size)
        assertTrue(ProgressStats.heatmap(emptyList(), today, days = 0, toDate = toDate).isEmpty())
        assertTrue(ProgressStats.heatmap(emptyList(), today, days = 7, toDate = toDate).all { it.sessions == 0 })
    }
}
