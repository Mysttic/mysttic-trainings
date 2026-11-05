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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pl.mysttic.trainings.data.db.entity.ProgramDayEntity
import pl.mysttic.trainings.data.db.entity.ProgramEntity
import pl.mysttic.trainings.data.db.entity.SessionEntity
import pl.mysttic.trainings.data.db.entity.WorkoutEntity
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.SessionStatus

/**
 * Sprzątanie treningów żyje w SQL (D-43 zastąpił `PlanCleanup` zapytaniami z D-42), więc sprawdzamy je
 * na prawdziwym Room w pamięci: `ProgramRepository.delete` i dwa zapytania, które `SeedImporter` woła
 * przy pierwszym starcie po aktualizacji. Scenariusze przeniesione z dawnego `PlanCleanupTest`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class WorkoutCleanupTest {

    private lateinit var db: AppDatabase
    private lateinit var programs: ProgramRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private var sessionNo = 0

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

    // --- ProgramRepository.delete ---

    @Test
    fun `usuniecie planu kasuje jego treningi raz mimo rotacji, razem z dniami i wierszem planu`() = runBlocking {
        workouts("$P-1", "$P-2", "$P-3")
        plan(P, "$P-1", "$P-2", null, "$P-1", "$P-3")

        programs.delete(P)

        assertTrue(remaining().toString(), remaining().isEmpty())
        assertNull(db.programDao().byId(P))
        assertTrue(db.programDao().days(P).isEmpty())
    }

    @Test
    fun `etap programu bez dnia w cyklu tez znika`() = runBlocking {
        workouts("$P-s1", "$P-s2", "$P-s3")
        plan(P, "$P-s1", "$P-s2")

        programs.delete(P)

        assertTrue(remaining().toString(), remaining().isEmpty())
    }

    @Test
    fun `plan z samymi odpoczynkami nie rusza treningow innych planow ani wlasnych`() = runBlocking {
        val others = setOf("plan-ab12cd3-1", "plan-ab12cd345-1", "8f1c-uuid-wlasny")
        workouts(*others.toTypedArray())
        workouts("full-body-a", builtIn = true)
        plan("plan-ab12cd3", "plan-ab12cd3-1")
        plan("plan-ab12cd345", "plan-ab12cd345-1")
        plan(P, null, null)

        programs.delete(P)

        assertEquals(others + "full-body-a", remaining())
        assertEquals(listOf("plan-ab12cd3-1"), db.programDao().days("plan-ab12cd3").map { it.workoutId })
    }

    @Test
    fun `trening z sesja zostaje dla Historii, takze z sesja w toku`() = runBlocking {
        workouts("$P-1", "$P-2", "$P-3")
        plan(P, "$P-1", "$P-2", "$P-3")
        session("$P-1", SessionStatus.DONE)
        session("$P-2", SessionStatus.IN_PROGRESS)

        programs.delete(P)

        assertEquals(setOf("$P-1", "$P-2"), remaining())
    }

    @Test
    fun `drugie usuniecie tego samego planu niczego nie rusza`() = runBlocking {
        workouts("$P-1", "plan-ab12cd3-1")
        plan(P, "$P-1")
        plan("plan-ab12cd3", "plan-ab12cd3-1")
        programs.delete(P)

        programs.delete(P)

        assertEquals(setOf("plan-ab12cd3-1"), remaining())
    }

    // --- SeedImporter: sieroty po planach skasowanych przed 0.9.1 ---

    @Test
    fun `sierota planu znika tylko bez wlasciciela, dnia, sesji i po karencji`() = runBlocking {
        workouts(
            "plan-x-1",     // bez planu, dnia i sesji: znika
            "plan-x-2",     // z sesją: zostaje dla Historii
            "plan-x-9",     // stoi w dniu cudzego planu: zostaje (obrona przed złamanym I1)
            "plan-y-1",     // dzień istniejącego planu
            "plan-y-wabc",  // pula istniejącego planu bez dnia: ma właściciela, zostaje
            "plan-yz-1",    // prefiks „plan-y” nie jest właścicielem „plan-yz”: znika
            "8f1c-uuid",    // trening spoza planów: nie jest ruszany
        )
        workouts("plan-x-3", createdAt = NOW)  // świeży: karencja doby
        plan("plan-y", "plan-y-1", "plan-x-9")
        session("plan-x-2")

        db.workoutDao().deleteOwnerlessPlanWorkouts(before = NOW - GRACE_MS)

        assertEquals(setOf("plan-x-2", "plan-x-3", "plan-x-9", "plan-y-1", "plan-y-wabc", "8f1c-uuid"), remaining())
    }

    // --- SeedImporter: wbudowane, których seed v7 już nie wozi ---

    @Test
    fun `wbudowany bez sesji znika przy pustym keep, z sesja albo w dniu planu zostaje`() = runBlocking {
        workouts("full-body-a", "full-body-b", "core-tabata", builtIn = true)
        workouts("8f1c-uuid")
        session("full-body-b")
        plan("plan-y", "core-tabata")

        db.workoutDao().deleteUnusedBuiltIn(keep = emptyList())

        assertEquals(setOf("full-body-b", "core-tabata", "8f1c-uuid"), remaining())
    }

    @Test
    fun `wbudowany z biezacego seedu zostaje`() = runBlocking {
        workouts("full-body-a", "full-body-b", builtIn = true)

        db.workoutDao().deleteUnusedBuiltIn(keep = listOf("full-body-a"))

        assertEquals(setOf("full-body-a"), remaining())
    }

    private suspend fun workouts(vararg ids: String, builtIn: Boolean = false, createdAt: Long = OLD) =
        db.workoutDao().upsertAll(ids.map { WorkoutEntity(it, it, "", "[]", createdAt, createdAt, builtIn) })

    private suspend fun plan(id: String, vararg days: String?) {
        db.programDao().insertProgram(
            ProgramEntity(id = id, name = id, profileJson = "{}", startedAt = OLD, cycleDays = days.size, createdAt = OLD, active = false),
        )
        if (days.isNotEmpty()) db.programDao().insertDays(days.mapIndexed { i, w -> ProgramDayEntity(id, i + 1, w) })
    }

    private suspend fun session(workoutId: String, status: SessionStatus = SessionStatus.DONE) =
        db.sessionDao().insertSession(
            SessionEntity(
                id = "s${++sessionNo}", workoutId = workoutId, workoutName = workoutId, difficulty = Difficulty.INTERMEDIATE,
                startedAt = OLD, endedAt = null, status = status, rpe = null, note = "",
            ),
        )

    private fun remaining(): Set<String> =
        db.openHelper.readableDatabase.query("SELECT id FROM workouts").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    private companion object {
        const val P = "plan-ab12cd34"
        const val OLD = 1_000L
        const val GRACE_MS = 24L * 60 * 60 * 1000
        const val NOW = 10 * GRACE_MS
    }
}
