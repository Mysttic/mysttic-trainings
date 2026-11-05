package pl.mysttic.trainings.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.data.db.dao.WorkoutDao
import pl.mysttic.trainings.data.db.entity.WorkoutEntity
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.Workout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
    private val json: Json,
) {
    fun observeAll(): Flow<List<Workout>> = dao.observeAll().map { list -> list.map { it.toDomain(json) } }

    fun observeById(id: String): Flow<Workout?> = dao.observeById(id).map { it?.toDomain(json) }

    suspend fun getById(id: String): Workout? = dao.getById(id)?.toDomain(json)

    /** Treningi planu (D-42): id z prefiksem planu. */
    fun observeOfProgram(programId: String): Flow<List<Workout>> =
        dao.observeByPrefix("$programId-%").map { list -> list.map { it.toDomain(json) } }

    suspend fun ofProgram(programId: String): List<Workout> = dao.byPrefix("$programId-%").map { it.toDomain(json) }

    suspend fun save(workout: Workout) = dao.upsert(workout.toEntity(json))

    suspend fun delete(workout: Workout) = dao.delete(workout.toEntity(json))
}

private val blocksSerializer = ListSerializer(Block.serializer())

fun WorkoutEntity.toDomain(json: Json) = Workout(
    id = id,
    name = name,
    note = note,
    blocks = if (blocksJson.isBlank()) emptyList() else json.decodeFromString(blocksSerializer, blocksJson),
    createdAt = createdAt,
    updatedAt = updatedAt,
    isBuiltIn = isBuiltIn,
)

fun Workout.toEntity(json: Json) = WorkoutEntity(
    id = id,
    name = name,
    note = note,
    blocksJson = json.encodeToString(blocksSerializer, blocks),
    createdAt = createdAt,
    updatedAt = updatedAt,
    isBuiltIn = isBuiltIn,
)
