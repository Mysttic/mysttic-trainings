package pl.mysttic.trainings.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.engine.TestFixtures
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout

class HealthExportTest {

    private val start = 1_000_000L

    @Test
    fun `do zdrowia trafia tylko ukonczona sesja dluzsza niz minuta`() {
        assertTrue(HealthExport.exportable(SessionStatus.DONE, start, start + 60_000))
        assertFalse(HealthExport.exportable(SessionStatus.DONE, start, start + 59_000))
        assertFalse(HealthExport.exportable(SessionStatus.ABANDONED, start, start + 600_000))
        assertFalse(HealthExport.exportable(SessionStatus.IN_PROGRESS, start, null))
        assertFalse(HealthExport.exportable(SessionStatus.DONE, start, null))
    }

    @Test
    fun `obwod to interwaly, serie to kalistenika, oddech wygrywa z oboma`() {
        assertEquals(HealthExerciseKind.HIIT, HealthExport.kind(hasCircuit = true))
        assertEquals(HealthExerciseKind.CALISTHENICS, HealthExport.kind(hasCircuit = false))
        assertEquals(HealthExerciseKind.BREATHING, HealthExport.kind(hasCircuit = true, breathing = true))
        assertEquals(HealthExerciseKind.BREATHING, HealthExport.kind(hasCircuit = false, breathing = true))
    }

    @Test
    fun `trening oddechowy to same cwiczenia z kategorii oddech`() {
        val breath = TestFixtures.exercise("power-breathing", kind = ExerciseKind.TIME, category = ExerciseCategory.BREATHING)
        val hold = TestFixtures.exercise("breath-hold", kind = ExerciseKind.TIME, category = ExerciseCategory.BREATHING)
        val exercises = TestFixtures.exercises + mapOf(breath.id to breath, hold.id to hold)
        fun workout(vararg ids: String) = Workout(
            id = "w", name = "w", blocks = listOf(
                Block(kind = BlockKind.CIRCUIT, rounds = 3, items = ids.map { BlockItem(it, TargetKind.SECONDS, 30) }),
            ), createdAt = 0L, updatedAt = 0L,
        )

        assertTrue(HealthExport.isBreathing(workout("power-breathing", "breath-hold"), exercises))
        assertFalse(HealthExport.isBreathing(workout("power-breathing", "plank"), exercises))
        assertFalse(HealthExport.isBreathing(workout("nieznane"), exercises))
        assertFalse(HealthExport.isBreathing(TestFixtures.tabataWorkout(), exercises))
        assertFalse(HealthExport.isBreathing(null, exercises))
    }

    @Test
    fun `brak nazwy treningu nie zostawia pustego wpisu`() {
        assertEquals("Góra A", HealthExport.title("Góra A"))
        assertEquals("Trening w domu", HealthExport.title(null))
        assertEquals("Trening w domu", HealthExport.title("   "))
    }
}
