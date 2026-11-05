package pl.mysttic.trainings.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migracje Room na prawdziwym SQLite (D-05): baza powstaje dokładnie z wyeksportowanego schematu starej wersji
 * (`app/schemas/.../<wersja>.json`, te same `createSql` i `setupQueries`, których użył Room), dostaje dane
 * jak z wydanej aplikacji, a potem otwiera ją bieżący `AppDatabase` z łańcuchem [AppDatabase.MIGRATIONS].
 * Room po migracji sam porównuje schemat z tym, którego oczekuje kod, więc test łapie też błąd w SQL migracji,
 * a nie tylko zgubione wiersze. Bez `room-testing`: nie potrzeba nowej zależności ani emulatora.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true }
    private var room: AppDatabase? = null

    @After
    fun tearDown() {
        room?.close()
        context.deleteDatabase(NAME)
    }

    /** Baza w wersji [version] zbudowana tak, jak zbudowałby ją Room tamtej wersji. */
    private fun createAt(version: Int, fill: SQLiteDatabase.() -> Unit = {}) {
        context.deleteDatabase(NAME)
        val schema = json.parseToJsonElement(File("schemas/$SCHEMAS/$version.json").readText()).jsonObject
        val database = schema.getValue("database").jsonObject
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(NAME).apply { parentFile?.mkdirs() }, null)
        database.getValue("entities").jsonArray.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            db.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        database.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.fill()
        db.version = version
        db.close()
    }

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, NAME)
        .addMigrations(*AppDatabase.MIGRATIONS)
        .allowMainThreadQueries()
        .build()
        .also { room = it }

    @Test
    fun `baza z 0_9 w wersji 3 przechodzi do najnowszej z historia, planem i trenerem`() = runBlocking {
        createAt(3) {
            // Gotowy program (D-37) z dwoma dniami i treningiem etapu.
            execSQL(
                "INSERT INTO programs (id, name, profileJson, startedAt, cycleDays, createdAt, active) VALUES " +
                    "('plan-aa11', 'Tabata', '{\"level\":\"INTERMEDIATE\",\"presetId\":\"TABATA\"}', 1000, 14, 1000, 1)",
            )
            execSQL("INSERT INTO program_days (programId, dayIndex, workoutId) VALUES ('plan-aa11', 1, 'plan-aa11-s1')")
            execSQL("INSERT INTO program_days (programId, dayIndex, workoutId) VALUES ('plan-aa11', 2, NULL)")
            execSQL(
                "INSERT INTO workouts (id, name, note, blocksJson, createdAt, updatedAt, isBuiltIn) VALUES " +
                    "('plan-aa11-s1', 'Etap 1', '', '[]', 1000, 1000, 0), ('moj', 'Własny', '', '[]', 500, 500, 0)",
            )
            // Sesja planu (po starcie planu), sesja sprzed planu i sesja własnego treningu.
            execSQL(
                "INSERT INTO sessions (id, workoutId, workoutName, difficulty, startedAt, endedAt, status, rpe, note) VALUES " +
                    "('s-plan', 'plan-aa11-s1', 'Etap 1', 'INTERMEDIATE', 2000, 2600, 'DONE', 7, 'ok'), " +
                    "('s-przed', 'plan-aa11-s1', 'Etap 1', 'INTERMEDIATE', 900, 1200, 'DONE', NULL, ''), " +
                    "('s-wlasny', 'moj', 'Własny', 'BEGINNER', 3000, 3300, 'ABANDONED', NULL, '')",
            )
            execSQL(
                "INSERT INTO set_logs (sessionId, exerciseId, exerciseName, blockIndex, itemIndex, round, setIndex, " +
                    "targetKind, targetValue, actualValue, weightKg, completedAt) VALUES " +
                    "('s-plan', 'burpees', 'Burpee', 0, 0, 1, 1, 'SECONDS', 20, 20, NULL, 2100)",
            )
            execSQL("INSERT INTO exercise_tuning (exerciseId, repsDelta, secondsDelta, excluded, updatedAt) VALUES ('burpees', 0, 5, 0, 10)")
        }

        val db = open()

        val program = db.programDao().all().single()
        assertEquals("PRESET", program.source)
        assertEquals(1000L, program.updatedAt)
        assertEquals(2, db.programDao().days("plan-aa11").size)
        val sessions = db.sessionDao().allSessions().associateBy { it.id }
        assertEquals(3, sessions.size)
        // Plan w sesji liczy ta sama reguła, co dawna kolejka: trening z dni planu i start po starcie planu.
        assertEquals("plan-aa11", sessions.getValue("s-plan").programId)
        assertNull(sessions.getValue("s-przed").programId)
        assertNull(sessions.getValue("s-wlasny").programId)
        assertNull(sessions.getValue("s-plan").plannedSets)
        assertEquals(1, db.sessionDao().allSetLogs().size)
        assertEquals(5, db.tuningDao().all().single().secondsDelta)
        assertTrue(db.programDao().notes("plan-aa11").isEmpty())
    }

    @Test
    fun `baza w wersji 4 dostaje w 5 pusta tabele notatek, plan zostaje`() = runBlocking {
        createAt(4) {
            execSQL(
                "INSERT INTO programs (id, name, profileJson, startedAt, cycleDays, createdAt, active, source, description, " +
                    "author, originId, shareUid, updatedAt, rhythmStart) VALUES " +
                    "('plan-bb22', 'Mój plan', '{}', 0, 7, 0, 0, 'CUSTOM', 'opis', 'Ola', NULL, 'uid-1', 5, 0)",
            )
        }

        val db = open()

        assertEquals("Ola", db.programDao().all().single().author)
        assertTrue(db.programDao().notes("plan-bb22").isEmpty())
    }

    @Test
    fun `baza z 1_0 w wersji 5 dostaje w 6 pusta date wycofania, cwiczenia i historia zostaja`() = runBlocking {
        createAt(5) {
            // Ćwiczenie wbudowane i własne z pliku planu, jak w 1.0.0, plus seria w historii.
            execSQL(
                "INSERT INTO exercises (id, nameEn, namePl, kind, level, category, equipment, primaryMuscles, secondaryMuscles, " +
                    "instructionsPl, images, easierId, harderId, defaultReps, defaultSeconds, isCustom) VALUES " +
                    "('pushups', 'Pushups', 'Pompki', 'REPS', 'BEGINNER', 'STRENGTH', '[]', '[\"CHEST\"]', '[]', '[\"krok\"]', " +
                    "'[\"seed/images/pushups/0.jpg\"]', 'knee-pushups', NULL, 12, 30, 0), " +
                    "('custom-1a2b3c4d', 'Mine', 'Moje', 'TIME', 'INTERMEDIATE', 'CARDIO', '[]', '[]', '[]', '[\"a\"]', '[]', NULL, NULL, 10, 45, 1)",
            )
            execSQL(
                "INSERT INTO sessions (id, workoutId, workoutName, difficulty, startedAt, endedAt, status, rpe, note, programId, plannedSets) " +
                    "VALUES ('s1', 'moj', 'Własny', 'BEGINNER', 1000, 1600, 'DONE', NULL, '', NULL, NULL)",
            )
            execSQL(
                "INSERT INTO set_logs (sessionId, exerciseId, exerciseName, blockIndex, itemIndex, round, setIndex, " +
                    "targetKind, targetValue, actualValue, weightKg, completedAt) VALUES " +
                    "('s1', 'pushups', 'Pompki', 0, 0, 1, 1, 'REPS', 12, 12, NULL, 1100)",
            )
        }

        val db = open()

        val pushups = db.exerciseDao().getById("pushups")!!
        assertNull(pushups.retiredAt)
        assertEquals("Pompki", pushups.namePl)
        assertEquals("knee-pushups", pushups.easierId)
        val own = db.exerciseDao().getById("custom-1a2b3c4d")!!
        assertTrue(own.isCustom)
        assertNull(own.retiredAt)
        assertEquals("pushups", db.sessionDao().allSetLogs().single().exerciseId)
        assertEquals(6, db.openHelper.readableDatabase.version)
    }

    @Test
    fun `pusta baza z pierwszej wersji przechodzi caly lancuch do najnowszej`() = runBlocking {
        createAt(1)

        val db = open()

        assertTrue(db.programDao().all().isEmpty())
        assertTrue(db.sessionDao().allSessions().isEmpty())
        assertEquals(AppDatabase.MIGRATIONS.last().endVersion, db.openHelper.readableDatabase.version)
    }

    private companion object {
        const val NAME = "migracja.db"
        const val SCHEMAS = "pl.mysttic.trainings.data.db.AppDatabase"
    }
}
