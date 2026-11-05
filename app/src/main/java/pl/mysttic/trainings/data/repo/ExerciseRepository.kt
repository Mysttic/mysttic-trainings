package pl.mysttic.trainings.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.mysttic.trainings.data.db.dao.ExerciseDao
import pl.mysttic.trainings.data.db.entity.ExerciseEntity
import pl.mysttic.trainings.domain.model.Exercise
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(private val dao: ExerciseDao) {

    fun observeAll(): Flow<List<Exercise>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: String): Flow<Exercise?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun getAll(): List<Exercise> = dao.getAll().map { it.toDomain() }

    suspend fun getMap(): Map<String, Exercise> = getAll().associateBy { it.id }

    suspend fun getById(id: String): Exercise? = dao.getById(id)?.toDomain()

    suspend fun save(exercise: Exercise) = dao.upsert(exercise.toEntity())

    suspend fun delete(exercise: Exercise) = dao.delete(exercise.toEntity())
}

fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    nameEn = nameEn,
    namePl = namePl,
    kind = kind,
    level = level,
    category = category,
    equipment = equipment,
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    instructionsPl = instructionsPl,
    images = images,
    easierId = easierId,
    harderId = harderId,
    defaultReps = defaultReps,
    defaultSeconds = defaultSeconds,
    isCustom = isCustom,
    retiredAt = retiredAt,
)

fun Exercise.toEntity() = ExerciseEntity(
    id = id,
    nameEn = nameEn,
    namePl = namePl,
    kind = kind,
    level = level,
    category = category,
    equipment = equipment,
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    instructionsPl = instructionsPl,
    images = images,
    easierId = easierId,
    harderId = harderId,
    defaultReps = defaultReps,
    defaultSeconds = defaultSeconds,
    isCustom = isCustom,
    retiredAt = retiredAt,
)
