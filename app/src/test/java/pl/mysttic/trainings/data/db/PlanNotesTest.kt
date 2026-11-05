package pl.mysttic.trainings.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.domain.plan.PlanDay
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.TrainingProfile

/**
 * Notatki do ćwiczeń w planie (D-44) na prawdziwym Room w pamięci: zapis i kasowanie pustej, kopia planu
 * zabiera notatki pod nowe id, a usunięcie planu sprząta je razem z nim. Inny plan ich nie widzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PlanNotesTest {

    private lateinit var db: AppDatabase
    private lateinit var programs: ProgramRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        programs = ProgramRepository(
            db, db.programDao(), db.workoutDao(), db.sessionDao(), WorkoutRepository(db.workoutDao(), json), json,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun plan(id: String) {
        val workout = Workout(
            id = "$id-1",
            name = "Klatka",
            blocks = listOf(Block(kind = BlockKind.SETS, items = listOf(BlockItem("barbell-bench-press", TargetKind.REPS, 8)))),
            createdAt = 0,
            updatedAt = 0,
        )
        val program = Program(
            id = id, name = "Plan $id", profile = TrainingProfile(), startedAt = 0, cycleDays = 2, createdAt = 0,
            active = false, source = PlanSource.CUSTOM, days = listOf(PlanDay(1, "$id-1"), PlanDay(2, "$id-1")),
        )
        programs.insert(program, listOf(workout))
    }

    @Test
    fun `notatka zapisuje sie w planie, pusta ja kasuje, inny plan jej nie widzi`() = runBlocking {
        plan("plan-a")
        plan("plan-b")

        programs.setNote("plan-a", "barbell-bench-press", "  60 kg, drugi otwór  ", now = 5)
        assertEquals(mapOf("barbell-bench-press" to "60 kg, drugi otwór"), programs.notes("plan-a"))
        assertTrue(programs.notes("plan-b").isEmpty())

        programs.setNote("plan-a", "barbell-bench-press", "   ")
        assertTrue(programs.notes("plan-a").isEmpty())
    }

    @Test
    fun `kopia planu zabiera notatki, usuniecie planu je sprzata`() = runBlocking {
        plan("plan-a")
        programs.setNote("plan-a", "barbell-bench-press", "60 kg")

        val copy = programs.duplicate("plan-a", now = 10)
        assertNotNull(copy)
        assertEquals(mapOf("barbell-bench-press" to "60 kg"), programs.notes(copy!!))

        programs.delete("plan-a")
        assertTrue(programs.notes("plan-a").isEmpty())
        // Kopia ma własne notatki: usunięcie oryginału ich nie rusza.
        assertEquals(mapOf("barbell-bench-press" to "60 kg"), programs.notes(copy))
    }
}
