package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import java.time.LocalDate
import java.time.ZoneId

class PlanListTest {

    private fun program(id: String, active: Boolean, updatedAt: Long, vararg workouts: String?) = Program(
        id = id, name = id, profile = TrainingProfile(), startedAt = 0, cycleDays = workouts.size, createdAt = 0,
        active = active, days = workouts.mapIndexed { i, w -> PlanDay(i + 1, w) }, updatedAt = updatedAt,
    )

    private fun done(workoutId: String, programId: String, at: Long) =
        Session("s-$at", workoutId, "x", Difficulty.INTERMEDIATE, at, at + 1, SessionStatus.DONE, programId = programId)

    @Test
    fun `status i kolejnosc listy`() {
        val active = program("a", true, 1, "a-1", "a-2")
        val paused = program("b", false, 1, "b-1", "b-2")
        val fresh = program("c", false, 5, "c-1")
        val finished = program("d", false, 9, "d-1")
        val sessions = listOf(done("b-1", "b", 50), done("d-1", "d", 60))

        val rows = PlanList.rows(listOf(finished, fresh, paused, active), sessions)

        assertEquals(listOf("a", "b", "c", "d"), rows.map { it.id })
        assertEquals(
            listOf(PlanStatus.ACTIVE, PlanStatus.PAUSED, PlanStatus.NOT_STARTED, PlanStatus.COMPLETE),
            rows.map { it.status },
        )
        assertEquals(1, rows[1].done)
        assertEquals(2, rows[1].total)
        assertEquals(50L, rows[1].lastDoneAt)
    }

    @Test
    fun `plan bez dni treningowych jest pusty`() {
        assertEquals(PlanStatus.EMPTY, PlanList.row(program("e", false, 0, null, null), emptyList()).status)
    }

    @Test
    fun `wznowienie ustawia nastepny dzien kolejki na dzis`() {
        val zone = ZoneId.of("Europe/Warsaw")
        val today = LocalDate.of(2026, 9, 23)
        val now = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val p = program("b", false, 0, "b-1", null, "b-2", null)
        val progress = PlanProgress.resolve(p.days, listOf("b-1"))

        val anchor = PlanRhythm.anchorFor(progress, now, zone)
        val resumed = p.copy(rhythmStart = anchor)

        assertEquals(3, resumed.dayIndexAt(now, zone))
        assertEquals(true, resumed.isRestDate(today.plusDays(1), zone))
    }
}
