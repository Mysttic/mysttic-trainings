package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout

class PlanCopyTest {

    private fun workout(id: String, fixed: Boolean = false) = Workout(
        id = id,
        name = id,
        blocks = listOf(
            Block(
                kind = BlockKind.SETS,
                fixed = fixed,
                items = listOf(BlockItem("pushups", TargetKind.REPS, 10, weightKg = 12.5, openEnded = false, cadenceSec = 3)),
            ),
        ),
        createdAt = 1,
        updatedAt = 1,
    )

    private val days = listOf(PlanDay(3, "old-b"), PlanDay(1, "old-a"), PlanDay(2, null), PlanDay(4, "old-a"), PlanDay(5, "gone"))
    private val workouts = listOf(workout("old-a"), workout("old-b", fixed = true)).associateBy { it.id }

    @Test
    fun `nowe id w kolejnosci dni, dzien bez treningu staje sie odpoczynkiem`() {
        val copy = PlanCopy.copy(days, workouts, "plan-new", now = 99)

        assertEquals(mapOf("old-a" to "plan-new-1", "old-b" to "plan-new-2"), copy.idMap)
        assertEquals(
            listOf(PlanDay(1, "plan-new-1"), PlanDay(2, null), PlanDay(3, "plan-new-2"), PlanDay(4, "plan-new-1"), PlanDay(5, null)),
            copy.days,
        )
        assertEquals(listOf("plan-new-1", "plan-new-2"), copy.workouts.map { it.id })
        assertEquals(99, copy.workouts.first().createdAt)
        assertFalse(copy.workouts.any { it.isBuiltIn })
    }

    @Test
    fun `bloki przechodza w calosci, takze fixed i pola protokolu`() {
        val copy = PlanCopy.copy(days, workouts, "plan-new", now = 99)

        assertEquals(workouts.getValue("old-b").blocks, copy.workouts[1].blocks)
        assertEquals(workouts.getValue("old-a").blocks, copy.workouts[0].blocks)
    }

    @Test
    fun `trening planu bez dnia jedzie w kopii po treningach z dni, w kolejnosci createdAt i id`() {
        val spare = listOf(
            workout("old-zapas").copy(createdAt = 7),
            workout("old-szkic").copy(createdAt = 3),
            workout("old-inny").copy(createdAt = 7),
        )
        val copy = PlanCopy.copy(days, workouts + spare.associateBy { it.id }, "plan-new", now = 99)

        assertEquals(
            listOf("old-a", "old-b", "old-szkic", "old-inny", "old-zapas"),
            copy.idMap.keys.toList(),
        )
        assertEquals(listOf("plan-new-1", "plan-new-2", "plan-new-3", "plan-new-4", "plan-new-5"), copy.workouts.map { it.id })
        // Dni się nie zmieniają: trening bez dnia nie wskakuje do kolejki.
        assertEquals(PlanCopy.copy(days, workouts, "plan-new", now = 99).days, copy.days)
    }

    @Test
    fun `kopia jest deterministyczna`() {
        assertEquals(PlanCopy.copy(days, workouts, "p", 5), PlanCopy.copy(days, workouts, "p", 5))
    }

    @Test
    fun `kopia presetu jako plan wlasny traci presetId`() {
        val profile = TrainingProfile(presetId = "TABATA", presetParam = 3)
        assertNull(PlanCopy.profileFor(profile, PlanSource.CUSTOM).presetId)
        assertNull(PlanCopy.profileFor(profile, PlanSource.CUSTOM).presetParam)
        assertEquals("TABATA", PlanCopy.profileFor(profile, PlanSource.PRESET).presetId)
    }
}
