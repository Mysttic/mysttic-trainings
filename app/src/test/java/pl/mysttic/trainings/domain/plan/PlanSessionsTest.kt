package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus

class PlanSessionsTest {

    private fun program(id: String, startedAt: Long, vararg workouts: String?) = Program(
        id = id,
        name = id,
        profile = TrainingProfile(),
        startedAt = startedAt,
        cycleDays = workouts.size,
        createdAt = startedAt,
        active = true,
        days = workouts.mapIndexed { index, w -> PlanDay(index + 1, w) },
    )

    private fun session(
        id: String,
        workoutId: String?,
        startedAt: Long,
        programId: String? = null,
        status: SessionStatus = SessionStatus.DONE,
    ) = Session(id, workoutId, "x", Difficulty.INTERMEDIATE, startedAt, startedAt + 1, status, programId = programId)

    private val a = program("plan-a", 100, "plan-a-1", null, "plan-a-2", "plan-a-1")

    @Test
    fun `sesja z programId nalezy tylko do swojego planu`() {
        val s = session("s1", "plan-a-1", 200, programId = "plan-b")
        assertFalse(PlanSessions.belongsTo(s, a))
        assertTrue(PlanSessions.belongsTo(s.copy(programId = "plan-a"), a))
    }

    @Test
    fun `stara sesja bez programId liczy sie dawna regula`() {
        assertTrue(PlanSessions.belongsTo(session("s1", "plan-a-2", 200), a))
        // Przed startem planu albo trening spoza dni — nie.
        assertFalse(PlanSessions.belongsTo(session("s2", "plan-a-2", 50), a))
        assertFalse(PlanSessions.belongsTo(session("s3", "full-body-a", 200), a))
    }

    @Test
    fun `postep taki sam jak dawna kolejka, nieukonczone pominiete`() {
        val sessions = listOf(
            session("s1", "plan-a-1", 200),
            session("s2", "plan-a-2", 300, programId = "plan-a"),
            session("s3", "plan-a-1", 400, status = SessionStatus.ABANDONED),
        )
        val progress = PlanSessions.progress(sessions, a)

        assertEquals(setOf(1, 3), progress.doneDayIndexes)
        assertEquals(4, progress.next?.dayIndex)
        assertEquals(PlanProgress.resolve(a.days, listOf("plan-a-1", "plan-a-2")), progress)
    }

    @Test
    fun `postep planu nie zabiera sesji innego planu z tym samym treningiem`() {
        // Kopia planu dostaje nowe id treningów, ale nawet przy wspólnym id sesja z programId trzyma się swojego planu.
        val b = program("plan-b", 100, "plan-a-1")
        val sessions = listOf(session("s1", "plan-a-1", 200, programId = "plan-a"))

        assertEquals(0, PlanSessions.progress(sessions, b).doneCount)
        assertEquals(1, PlanSessions.progress(sessions, a).doneCount)
    }

    @Test
    fun `wlasciciel treningu po najdluzszym prefiksie`() {
        val ids = listOf("plan-a", "plan-ab", "plan-c")
        assertEquals("plan-a", PlanSessions.ownerOf("plan-a-3", ids))
        assertEquals("plan-ab", PlanSessions.ownerOf("plan-ab-1", ids))
        assertNull(PlanSessions.ownerOf("full-body-a", ids))
        assertNull(PlanSessions.ownerOf("plan-a", ids))
    }
}
