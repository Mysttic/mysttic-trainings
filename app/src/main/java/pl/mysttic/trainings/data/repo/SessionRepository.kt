package pl.mysttic.trainings.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.data.db.dao.CheckpointDao
import pl.mysttic.trainings.data.db.dao.SessionDao
import pl.mysttic.trainings.data.db.entity.PlayerCheckpointEntity
import pl.mysttic.trainings.data.db.entity.SessionEntity
import pl.mysttic.trainings.data.db.entity.SetLogEntity
import pl.mysttic.trainings.domain.engine.EngineState
import pl.mysttic.trainings.domain.engine.SetResult
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.Workout
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Checkpoint silnika odczytany z bazy. */
data class SavedCheckpoint(val state: EngineState, val savedAtWall: Long, val savedAtElapsed: Long)

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val checkpointDao: CheckpointDao,
    private val json: Json,
) {
    suspend fun startSession(
        workout: Workout,
        difficulty: Difficulty,
        programId: String? = null,
        startedAt: Long = System.currentTimeMillis(),
    ): Session {
        val session = Session(
            id = UUID.randomUUID().toString(),
            workoutId = workout.id,
            workoutName = workout.name,
            difficulty = difficulty,
            startedAt = startedAt,
            programId = programId,
        )
        sessionDao.insertSession(session.toEntity())
        return session
    }

    suspend fun logSet(sessionId: String, result: SetResult, completedAtWall: Long = System.currentTimeMillis()) {
        val step = result.step
        sessionDao.insertSetLog(
            SetLogEntity(
                sessionId = sessionId,
                exerciseId = step.exerciseId,
                exerciseName = step.exerciseName,
                blockIndex = step.blockIndex,
                itemIndex = step.itemIndex,
                round = step.round,
                setIndex = step.setIndex,
                targetKind = step.targetKind,
                targetValue = step.targetValue,
                actualValue = result.actualValue,
                weightKg = step.weightKg,
                completedAt = completedAtWall,
            ),
        )
    }

    /**
     * Zamknięcie sesji. [difficulty] to poziom, na którym trening był prowadzony (stan silnika przy końcu),
     * bo poziom wolno zmienić w trakcie: historia ma opisywać to, co użytkownik zrobił, a nie ustawienie
     * z chwili wciśnięcia Start. [plannedSets] to liczba kroków pracy po ostatnim przeliczeniu (raport planu).
     * Oba pola przychodzą z migawki stanu silnika (D-42, D-43); null zostawia wartość zapisaną wcześniej.
     */
    suspend fun finish(
        sessionId: String,
        status: SessionStatus,
        plannedSets: Int? = null,
        difficulty: Difficulty? = null,
        endedAt: Long = System.currentTimeMillis(),
    ) {
        val entity = sessionDao.getSession(sessionId) ?: return
        sessionDao.updateSession(
            entity.copy(
                status = status,
                endedAt = endedAt,
                plannedSets = plannedSets ?: entity.plannedSets,
                difficulty = difficulty ?: entity.difficulty,
            ),
        )
        checkpointDao.delete(sessionId)
    }

    suspend fun updateFeedback(sessionId: String, rpe: Int?, note: String) {
        val entity = sessionDao.getSession(sessionId) ?: return
        sessionDao.updateSession(entity.copy(rpe = rpe, note = note))
    }

    suspend fun getSession(id: String): Session? = sessionDao.getSession(id)?.toDomain()

    fun observeSessions(): Flow<List<Session>> = sessionDao.observeSessions().map { list -> list.map { it.toDomain() } }

    fun observeLastDone(): Flow<Session?> = sessionDao.observeLastDone().map { it?.toDomain() }

    fun observeDoneCountSince(since: Long): Flow<Int> = sessionDao.observeDoneCountSince(since)

    suspend fun setLogs(sessionId: String): List<SetLog> = sessionDao.setLogs(sessionId).map { it.toDomain() }

    fun observeSetLogs(sessionId: String): Flow<List<SetLog>> = sessionDao.observeSetLogs(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun previousDone(workoutId: String, before: Long): Session? = sessionDao.lastDoneBefore(workoutId, before)?.toDomain()

    suspend fun inProgress(): Session? = sessionDao.inProgress()?.toDomain()

    /** Serie jednego ćwiczenia w czasie — do wykresu postępu (M5). */
    fun observeSetLogsForExercise(exerciseId: String): Flow<List<SetLog>> =
        sessionDao.observeSetLogsForExercise(exerciseId).map { list -> list.map { it.toDomain() } }

    /** Cała historia — do rozliczenia cyklu w kreatorze (D-22). */
    suspend fun allSessions(): List<Session> = sessionDao.allSessions().map { it.toDomain() }

    suspend fun allSetLogs(): List<SetLog> = sessionDao.allSetLogs().map { it.toDomain() }

    /** Wszystkie serie — do statystyk okresu w Historii. */
    fun observeAllSetLogs(): Flow<List<SetLog>> = sessionDao.observeAllSetLogs().map { list -> list.map { it.toDomain() } }

    /** Usuwa sesję razem z jej seriami i checkpointem. Nieodwracalne, w UI za potwierdzeniem. */
    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSetLogs(sessionId)
        checkpointDao.delete(sessionId)
        sessionDao.deleteSession(sessionId)
    }

    suspend fun saveCheckpoint(state: EngineState, savedAtWall: Long, savedAtElapsed: Long) {
        checkpointDao.upsert(
            PlayerCheckpointEntity(
                sessionId = state.sessionId,
                stateJson = json.encodeToString(EngineState.serializer(), state),
                savedAtWall = savedAtWall,
                savedAtElapsed = savedAtElapsed,
            ),
        )
    }

    suspend fun loadCheckpoint(sessionId: String): SavedCheckpoint? = checkpointDao.get(sessionId)?.toSaved()

    suspend fun latestCheckpoint(): SavedCheckpoint? = checkpointDao.latest()?.toSaved()

    suspend fun clearCheckpoint(sessionId: String) = checkpointDao.delete(sessionId)

    private fun PlayerCheckpointEntity.toSaved() = SavedCheckpoint(
        state = json.decodeFromString(EngineState.serializer(), stateJson),
        savedAtWall = savedAtWall,
        savedAtElapsed = savedAtElapsed,
    )
}

fun SessionEntity.toDomain() = Session(
    id = id, workoutId = workoutId, workoutName = workoutName, difficulty = difficulty,
    startedAt = startedAt, endedAt = endedAt, status = status, rpe = rpe, note = note,
    programId = programId, plannedSets = plannedSets,
)

fun Session.toEntity() = SessionEntity(
    id = id, workoutId = workoutId, workoutName = workoutName, difficulty = difficulty,
    startedAt = startedAt, endedAt = endedAt, status = status, rpe = rpe, note = note,
    programId = programId, plannedSets = plannedSets,
)

fun SetLogEntity.toDomain() = SetLog(
    id = id, sessionId = sessionId, exerciseId = exerciseId, exerciseName = exerciseName,
    blockIndex = blockIndex, itemIndex = itemIndex, round = round, setIndex = setIndex,
    targetKind = targetKind, targetValue = targetValue, actualValue = actualValue,
    weightKg = weightKg, completedAt = completedAt,
)
