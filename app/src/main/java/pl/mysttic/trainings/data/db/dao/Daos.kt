package pl.mysttic.trainings.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pl.mysttic.trainings.data.db.entity.ExerciseEntity
import pl.mysttic.trainings.data.db.entity.ExerciseTuningEntity
import pl.mysttic.trainings.data.db.entity.PlanExerciseNoteEntity
import pl.mysttic.trainings.data.db.entity.PlayerCheckpointEntity
import pl.mysttic.trainings.data.db.entity.ProgramDayEntity
import pl.mysttic.trainings.data.db.entity.ProgramEntity
import pl.mysttic.trainings.data.db.entity.SessionEntity
import pl.mysttic.trainings.data.db.entity.SetLogEntity
import pl.mysttic.trainings.data.db.entity.WorkoutEntity

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY namePl COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY namePl COLLATE NOCASE")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    /** Tylko ćwiczenia użytkownika — wbudowane odtwarza seed, więc nie trafiają do kopii zapasowej. */
    @Query("SELECT * FROM exercises WHERE isCustom = 1")
    suspend fun allCustom(): List<ExerciseEntity>

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Delete
    suspend fun delete(exercise: ExerciseEntity)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY isBuiltIn DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeById(id: String): Flow<WorkoutEntity?>

    /** Treningi planu po prefiksie id (D-42); `pattern` to `"$programId-%"`. */
    @Query("SELECT * FROM workouts WHERE isBuiltIn = 0 AND id LIKE :pattern ORDER BY createdAt, id")
    fun observeByPrefix(pattern: String): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE isBuiltIn = 0 AND id LIKE :pattern ORDER BY createdAt, id")
    suspend fun byPrefix(pattern: String): List<WorkoutEntity>

    /** Tylko treningi użytkownika: wbudowane nie idą do kopii (od seedu v7 zostały tylko resztki z historią, D-43). */
    @Query("SELECT * FROM workouts WHERE isBuiltIn = 0")
    suspend fun allUser(): List<WorkoutEntity>

    /**
     * Wbudowane treningi spoza bieżącego seedu ([keep]), których nie tknęła żadna sesja. Od seedu v7 seed ich
     * nie wozi (D-43), a import tylko dopisuje, więc stare wiersze trzeba skasować osobno. Te z historią zostają
     * (Δ w Historii, kopia do planu w edytorze, D-42). Warunek na dni planu to obrona niezmiennika I2 z D-42:
     * wbudowany nie stoi w dniu planu.
     */
    @Query(
        """
        DELETE FROM workouts
        WHERE isBuiltIn = 1
          AND id NOT IN (:keep)
          AND id NOT IN (SELECT workoutId FROM sessions WHERE workoutId IS NOT NULL)
          AND id NOT IN (SELECT workoutId FROM program_days WHERE workoutId IS NOT NULL)
        """
    )
    suspend fun deleteUnusedBuiltIn(keep: List<String>)

    /**
     * Resztki po planach skasowanych przed 0.9.1, kiedy „Usuń plan” kasował tylko dni i cykl (D-43).
     * Kasujemy trening `plan-%` bez właściciela po prefiksie (ta sama reguła co [pl.mysttic.trainings.domain.plan.PlanSessions.ownerOf]),
     * bez dnia planu, bez żadnej sesji i starszy niż [before]. Trening istniejącego planu bez dnia (pula edytora,
     * szkic) ma właściciela i zostaje; trening z sesją zostaje dla Historii, tak jak w `ProgramRepository.delete`.
     */
    @Query(
        """
        DELETE FROM workouts
        WHERE isBuiltIn = 0 AND id LIKE 'plan-%' AND createdAt < :before
          AND NOT EXISTS (SELECT 1 FROM programs p WHERE substr(workouts.id, 1, length(p.id) + 1) = p.id || '-')
          AND id NOT IN (SELECT workoutId FROM program_days WHERE workoutId IS NOT NULL)
          AND id NOT IN (SELECT workoutId FROM sessions WHERE workoutId IS NOT NULL)
        """
    )
    suspend fun deleteOwnerlessPlanWorkouts(before: Long)

    @Upsert
    suspend fun upsert(workout: WorkoutEntity)

    @Upsert
    suspend fun upsertAll(workouts: List<WorkoutEntity>)

    @Delete
    suspend fun delete(workout: WorkoutEntity)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE status = 'DONE' ORDER BY startedAt DESC LIMIT 1")
    fun observeLastDone(): Flow<SessionEntity?>

    /** Ostatnia ukończona sesja danego treningu przed `before` — do porównania „Δ względem poprzedniego razu”. */
    @Query("SELECT * FROM sessions WHERE workoutId = :workoutId AND status = 'DONE' AND startedAt < :before ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastDoneBefore(workoutId: String, before: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun inProgress(): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'DONE' AND startedAt >= :since")
    fun observeDoneCountSince(since: Long): Flow<Int>

    @Insert
    suspend fun insertSetLog(log: SetLogEntity): Long

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY completedAt")
    suspend fun setLogs(sessionId: String): List<SetLogEntity>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY completedAt")
    fun observeSetLogs(sessionId: String): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId ORDER BY completedAt DESC LIMIT :limit")
    suspend fun recentSetLogsFor(exerciseId: String, limit: Int): List<SetLogEntity>

    /** Wszystkie serie jednego ćwiczenia — do wykresu postępu (M5). */
    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId ORDER BY completedAt")
    fun observeSetLogsForExercise(exerciseId: String): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM sessions ORDER BY startedAt")
    suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM set_logs ORDER BY completedAt")
    suspend fun allSetLogs(): List<SetLogEntity>

    /** Wszystkie serie wszystkich sesji jednym zapytaniem — do statystyk okresu w Historii. */
    @Query("SELECT * FROM set_logs ORDER BY completedAt")
    fun observeAllSetLogs(): Flow<List<SetLogEntity>>

    @Query("SELECT id FROM sessions")
    suspend fun allSessionIds(): List<String>

    @Insert
    suspend fun insertSessions(sessions: List<SessionEntity>)

    /** `id = 0` w encji: baza nadaje nowe klucze, więc import nie zderza się z istniejącymi. */
    @Insert
    suspend fun insertSetLogs(logs: List<SetLogEntity>)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM set_logs WHERE sessionId = :sessionId")
    suspend fun deleteSetLogs(sessionId: String)
}

@Dao
interface CheckpointDao {
    @Upsert
    suspend fun upsert(checkpoint: PlayerCheckpointEntity)

    @Query("SELECT * FROM player_checkpoints WHERE sessionId = :sessionId")
    suspend fun get(sessionId: String): PlayerCheckpointEntity?

    @Query("SELECT * FROM player_checkpoints ORDER BY savedAtWall DESC LIMIT 1")
    suspend fun latest(): PlayerCheckpointEntity?

    @Query("DELETE FROM player_checkpoints WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("DELETE FROM player_checkpoints")
    suspend fun clear()
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs WHERE active = 1 ORDER BY createdAt DESC LIMIT 1")
    fun observeActive(): Flow<ProgramEntity?>

    @Query("SELECT * FROM programs WHERE active = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun active(): ProgramEntity?

    @Query("SELECT * FROM programs ORDER BY createdAt DESC")
    suspend fun all(): List<ProgramEntity>

    @Query("SELECT id FROM programs")
    suspend fun allIds(): List<String>

    @Query("SELECT * FROM programs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM program_days ORDER BY programId, dayIndex")
    fun observeAllDays(): Flow<List<ProgramDayEntity>>

    @Query("SELECT * FROM programs WHERE id = :id")
    fun observeById(id: String): Flow<ProgramEntity?>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun byId(id: String): ProgramEntity?

    /** Jeden aktywny plan (D-42): jedno zapytanie przestawia wszystkie wiersze naraz. */
    @Query("UPDATE programs SET active = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun activate(id: String)

    @Query("UPDATE programs SET rhythmStart = :rhythmStart, updatedAt = :now WHERE id = :id")
    suspend fun setRhythm(id: String, rhythmStart: Long, now: Long)

    @Query("UPDATE programs SET name = :name, description = :description, updatedAt = :now WHERE id = :id")
    suspend fun updateMeta(id: String, name: String, description: String, now: Long)

    /** Kształt planu po edycji: nazwa, opis, długość i profil (długość siedzi też w profilu). */
    @Query(
        "UPDATE programs SET name = :name, description = :description, cycleDays = :cycleDays, " +
            "profileJson = :profileJson, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateShape(id: String, name: String, description: String, cycleDays: Int, profileJson: String, now: Long)

    @Query("UPDATE programs SET shareUid = :uid WHERE id = :id")
    suspend fun setShareUid(id: String, uid: String)

    @Query("SELECT * FROM programs WHERE shareUid = :uid")
    suspend fun byShareUid(uid: String): List<ProgramEntity>

    /**
     * Treningi z prefiksem planu, których nie tknęła sesja — także te bez dnia (odłączone w edytorze).
     * `pattern` to `"$programId-%"`; podkreślnik w id planu nie występuje, więc LIKE nie łapie cudzych.
     */
    @Query(
        """
        DELETE FROM workouts
        WHERE isBuiltIn = 0 AND id LIKE :pattern
          AND id NOT IN (SELECT workoutId FROM sessions WHERE workoutId IS NOT NULL)
        """
    )
    suspend fun deleteUnusedOwnedWorkouts(pattern: String)

    @Query("SELECT DISTINCT d.programId FROM program_days d JOIN programs p ON p.id = d.programId WHERE d.workoutId = :workoutId ORDER BY p.createdAt DESC")
    suspend fun programsWithWorkout(workoutId: String): List<String>

    @Query("SELECT * FROM program_days WHERE programId = :programId ORDER BY dayIndex")
    fun observeDays(programId: String): Flow<List<ProgramDayEntity>>

    @Query("SELECT * FROM program_days WHERE programId = :programId ORDER BY dayIndex")
    suspend fun days(programId: String): List<ProgramDayEntity>

    @Insert
    suspend fun insertProgram(program: ProgramEntity)

    @Insert
    suspend fun insertDays(days: List<ProgramDayEntity>)

    /** Nowy cykl wyłącza poprzedni; historia cykli zostaje w tabeli. */
    @Query("UPDATE programs SET active = 0 WHERE active = 1")
    suspend fun deactivateAll()

    @Query("DELETE FROM programs WHERE id = :programId")
    suspend fun deleteProgram(programId: String)

    // --- notatki do ćwiczeń w planie (D-44) ---

    @Query("SELECT * FROM plan_exercise_notes WHERE programId = :programId")
    fun observeNotes(programId: String): Flow<List<PlanExerciseNoteEntity>>

    @Query("SELECT * FROM plan_exercise_notes WHERE programId = :programId")
    suspend fun notes(programId: String): List<PlanExerciseNoteEntity>

    @Query("SELECT * FROM plan_exercise_notes")
    suspend fun allNotes(): List<PlanExerciseNoteEntity>

    @Upsert
    suspend fun upsertNote(note: PlanExerciseNoteEntity)

    @Upsert
    suspend fun upsertNotes(notes: List<PlanExerciseNoteEntity>)

    @Query("DELETE FROM plan_exercise_notes WHERE programId = :programId AND exerciseId = :exerciseId")
    suspend fun deleteNote(programId: String, exerciseId: String)

    @Query("DELETE FROM plan_exercise_notes WHERE programId = :programId")
    suspend fun deleteNotes(programId: String)

    @Query("DELETE FROM program_days WHERE programId = :programId")
    suspend fun deleteDays(programId: String)

    /** Ile dni cyklu wskazuje na ten trening — zero znaczy, że wolno go skasować ze szczegółów. */
    @Query("SELECT COUNT(*) FROM program_days WHERE workoutId = :workoutId")
    fun observeDayCount(workoutId: String): Flow<Int>

    /**
     * Treningi tego cyklu, których nie tknęła żadna sesja. Kasujemy je razem z planem, żeby nie zostawały
     * sierotami bez wejścia w interfejsie (i nie jechały potem w kopii zapasowej). Trening, który ma sesję
     * w historii, zostaje — Historia sięga po niego przy „Δ względem poprzedniego razu”.
     * Wołać przed [deleteDays], bo bierze identyfikatory właśnie stamtąd.
     */
    @Query(
        """
        DELETE FROM workouts
        WHERE isBuiltIn = 0
          AND id IN (SELECT workoutId FROM program_days WHERE programId = :programId AND workoutId IS NOT NULL)
          AND id NOT IN (SELECT workoutId FROM sessions WHERE workoutId IS NOT NULL)
        """
    )
    suspend fun deleteUnusedWorkouts(programId: String)
}

@Dao
interface TuningDao {
    @Query("SELECT * FROM exercise_tuning")
    fun observeAll(): Flow<List<ExerciseTuningEntity>>

    @Query("SELECT * FROM exercise_tuning")
    suspend fun all(): List<ExerciseTuningEntity>

    @Query("SELECT * FROM exercise_tuning WHERE exerciseId = :exerciseId")
    suspend fun get(exerciseId: String): ExerciseTuningEntity?

    @Upsert
    suspend fun upsert(tuning: ExerciseTuningEntity)

    @Upsert
    suspend fun upsertAll(tuning: List<ExerciseTuningEntity>)

    @Query("DELETE FROM exercise_tuning WHERE exerciseId = :exerciseId")
    suspend fun delete(exerciseId: String)
}
