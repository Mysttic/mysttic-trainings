package pl.mysttic.trainings.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Duplikat i powtórka planu (D-42) na prawdziwym Room w pamięci. Duplikat to nowy plan: bez tożsamości pliku
 * i bez podpisu autora planu od kogoś. Powtórka to ten sam plan w nowym przebiegu. Obie kopie zabierają
 * wszystkie treningi planu, także te, które nie stoją w żadnym dniu.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ProgramCopyTest {

    private lateinit var db: AppDatabase
    private lateinit var programs: ProgramRepository
    private lateinit var workouts: WorkoutRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workouts = WorkoutRepository(db.workoutDao(), json)
        programs = ProgramRepository(db, db.programDao(), db.workoutDao(), db.sessionDao(), workouts, json)
    }

    @After
    fun tearDown() = db.close()

    private fun workout(id: String, createdAt: Long = 0) = Workout(
        id = id,
        name = id,
        blocks = listOf(Block(kind = BlockKind.SETS, items = listOf(BlockItem("pushups", TargetKind.REPS, 10)))),
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private suspend fun plan(id: String, source: PlanSource, author: String) {
        val program = Program(
            id = id, name = "Plan $id", profile = TrainingProfile(), startedAt = 0, cycleDays = 2, createdAt = 0,
            active = false, source = source, author = author, shareUid = "uid-$id",
            days = listOf(PlanDay(1, "$id-1"), PlanDay(2, "$id-1")),
        )
        // Drugi trening nie stoi w żadnym dniu: edytor planu pozwala go trzymać.
        programs.insert(program, listOf(workout("$id-1"), workout("$id-zapas", createdAt = 5)))
    }

    @Test
    fun `duplikat planu od kogos to nowy plan bez shareUid i bez podpisu autora`() = runBlocking {
        plan("plan-a", PlanSource.IMPORTED, author = "Jan Kowalski")

        val copyId = programs.duplicate("plan-a", now = 10)!!
        val copy = programs.byId(copyId)!!

        assertNull(copy.shareUid)
        assertEquals("", copy.author)
        assertEquals(PlanSource.CUSTOM, copy.source)
        assertEquals("plan-a", copy.originId)
        assertEquals(listOf(PlanDay(1, "$copyId-1"), PlanDay(2, "$copyId-1")), copy.days)
        assertEquals(listOf("$copyId-1", "$copyId-2"), workouts.ofProgram(copyId).map { it.id }.sorted())
    }

    @Test
    fun `duplikat planu wlasnego zostawia podpis, ale nie tozsamosc pliku`() = runBlocking {
        plan("plan-b", PlanSource.CUSTOM, author = "Ola")

        val copy = programs.byId(programs.duplicate("plan-b", now = 10)!!)!!

        assertEquals("Ola", copy.author)
        assertNull(copy.shareUid)
    }

    @Test
    fun `powtorka to ten sam plan z autorem, shareUid i wszystkimi treningami`() = runBlocking {
        plan("plan-c", PlanSource.IMPORTED, author = "Jan Kowalski")

        val againId = programs.repeat("plan-c", now = 10)!!
        val again = programs.byId(againId)!!

        assertEquals("uid-plan-c", again.shareUid)
        assertEquals("Jan Kowalski", again.author)
        assertEquals(PlanSource.IMPORTED, again.source)
        assertEquals(2, workouts.ofProgram(againId).size)
    }
}
