package pl.mysttic.trainings.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.mysttic.trainings.data.db.dao.TuningDao
import pl.mysttic.trainings.data.db.entity.ExerciseTuningEntity
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.coach.Coach
import pl.mysttic.trainings.domain.coach.ExerciseFeedback
import pl.mysttic.trainings.domain.coach.ExerciseTuning
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.Exercise
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trener (D-35): pamięć per ćwiczenie i jej zastosowanie. Opinie z podsumowania przesuwają korektę celu,
 * „nie chcę” wyklucza ćwiczenie i od razu podmienia je w treningach aktywnego cyklu, żeby następny trening
 * już go nie miał. Kolejne cykle dostają listę wykluczeń przez [excludedIds]. Neutralny stan nie zajmuje wiersza.
 */
@Singleton
class CoachRepository @Inject constructor(
    private val dao: TuningDao,
    private val programRepo: ProgramRepository,
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val settingsRepo: SettingsRepository,
    private val sessionRepo: SessionRepository,
) {
    private val planner = WorkoutPlanner()

    fun observeAll(): Flow<Map<String, ExerciseTuning>> =
        dao.observeAll().map { list -> list.associate { it.exerciseId to it.toDomain() } }

    suspend fun all(): Map<String, ExerciseTuning> = dao.all().associate { it.exerciseId to it.toDomain() }

    suspend fun get(exerciseId: String): ExerciseTuning = dao.get(exerciseId)?.toDomain() ?: ExerciseTuning(exerciseId)

    suspend fun excludedIds(): Set<String> = dao.all().filter { it.excluded }.map { it.exerciseId }.toSet()

    /** Opinie z jednego podsumowania (klucz: ćwiczenie pokazane w playerze). Zwraca, ile ćwiczeń zmieniło stan. */
    suspend fun applyFeedback(feedback: Map<String, ExerciseFeedback>, now: Long = System.currentTimeMillis()): Int {
        var changed = 0
        for ((exerciseId, verdict) in feedback) {
            if (verdict == ExerciseFeedback.NONE) continue
            val before = get(exerciseId)
            val after = Coach.apply(before, verdict, now)
            if (after != before) {
                save(after)
                changed++
            }
        }
        if (feedback.values.any { it == ExerciseFeedback.NEVER }) replaceExcludedInActiveProgram(now)
        return changed
    }

    /** Przełącznik ze szczegółów ćwiczenia: wykluczenie działa od razu na aktywny cykl, cofnięcie tylko na kolejne plany. */
    suspend fun setExcluded(exerciseId: String, excluded: Boolean, now: Long = System.currentTimeMillis()) {
        save(get(exerciseId).copy(excluded = excluded, updatedAt = now))
        if (excluded) replaceExcludedInActiveProgram(now)
    }

    /** Zeruje korektę celu; wykluczenie zostaje, bo to osobna decyzja. */
    suspend fun resetDeltas(exerciseId: String, now: Long = System.currentTimeMillis()) {
        save(get(exerciseId).copy(repsDelta = 0, secondsDelta = 0, updatedAt = now))
    }

    private suspend fun save(tuning: ExerciseTuning) {
        if (tuning.isNeutral) dao.delete(tuning.exerciseId) else dao.upsert(tuning.toEntity())
    }

    /**
     * Treningi aktywnego cyklu bez wykluczonych ćwiczeń: zamiennik albo usunięcie pozycji (D-35).
     * Gotowy program (D-37) to stały protokół, więc go nie podmieniamy; wykluczenie zostaje zapisane
     * i zadziała na kolejne plany z kreatora. Trening z sesją w toku też zostaje jak był (D-42): po wznowieniu
     * procesu player rozwija szablon od nowa, więc podmiana pod trwającą sesją pomieszałaby w niej dwa ćwiczenia.
     */
    suspend fun replaceExcludedInActiveProgram(now: Long = System.currentTimeMillis()) {
        val program = programRepo.active()?.takeIf { !it.isPreset } ?: return
        val excluded = excludedIds()
        if (excluded.isEmpty()) return
        val running = sessionRepo.inProgress()?.workoutId
        val exercises = exerciseRepo.getMap()
        val settings = settingsRepo.current()
        val resolve: (Exercise) -> Exercise =
            { planner.resolveVariant(it, settings.difficulty, exercises, settings.ownedEquipment) }
        for (workoutId in program.days.mapNotNull { it.workoutId }.distinct()) {
            if (workoutId == running) continue
            val workout = workoutRepo.getById(workoutId) ?: continue
            val blocks = Coach.withoutExcluded(workout.blocks, exercises, settings.ownedEquipment, excluded, resolve)
            if (blocks !== workout.blocks) workoutRepo.save(workout.copy(blocks = blocks, updatedAt = now))
        }
    }
}

private fun ExerciseTuningEntity.toDomain() = ExerciseTuning(exerciseId, repsDelta, secondsDelta, excluded, updatedAt)
private fun ExerciseTuning.toEntity() = ExerciseTuningEntity(exerciseId, repsDelta, secondsDelta, excluded, updatedAt)
