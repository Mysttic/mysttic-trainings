package pl.mysttic.trainings.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.TargetKind

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val nameEn: String,
    val namePl: String,
    val kind: ExerciseKind,
    val level: Difficulty,
    val category: ExerciseCategory,
    val equipment: Set<Equipment>,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val instructionsPl: List<String>,
    val images: List<String>,
    val easierId: String?,
    val harderId: String?,
    val defaultReps: Int,
    val defaultSeconds: Int,
    val isCustom: Boolean,
    /** Data wycofania z bazy (D-47, Room v6); null = w użyciu. */
    val retiredAt: String? = null,
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val note: String,
    /** JSON: List<Block> — patrz docs/DECISIONS.md (D-04). */
    val blocksJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isBuiltIn: Boolean,
)

@Entity(tableName = "sessions", indices = [Index("startedAt"), Index("workoutId"), Index("programId")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val workoutId: String?,
    val workoutName: String,
    val difficulty: Difficulty,
    val startedAt: Long,
    val endedAt: Long?,
    val status: SessionStatus,
    val rpe: Int?,
    val note: String,
    /** v4 (D-42): plan, z którego trening był uruchomiony; snapshot z chwili startu. */
    val programId: String? = null,
    /** v4: liczba serii przewidzianych w chwili zakończenia (po zmianie poziomu w trakcie). */
    val plannedSets: Int? = null,
)

@Entity(tableName = "set_logs", indices = [Index("sessionId"), Index("exerciseId")])
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val blockIndex: Int,
    val itemIndex: Int,
    val round: Int,
    val setIndex: Int,
    val targetKind: TargetKind,
    val targetValue: Int,
    val actualValue: Int,
    val weightKg: Double?,
    val completedAt: Long,
)

/** Snapshot silnika do odtworzenia po ubiciu procesu / reboocie. Jeden wiersz na sesję w toku. */
@Entity(tableName = "player_checkpoints")
data class PlayerCheckpointEntity(
    @PrimaryKey val sessionId: String,
    /** JSON: EngineState. */
    val stateJson: String,
    /** System.currentTimeMillis() w chwili zapisu — odporne na reboot. */
    val savedAtWall: Long,
    /** SystemClock.elapsedRealtime() w chwili zapisu — spójne z timestampami w stanie. */
    val savedAtElapsed: Long,
)

/**
 * Cykl treningowy z kreatora (M4, D-20). `profileJson` to snapshot [pl.mysttic.trainings.domain.plan.TrainingProfile]
 * z chwili generowania — kolejny cykl startuje z tych wartości, użytkownik je tylko koryguje.
 */
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val profileJson: String,
    /** Początek dnia 1 cyklu (wall clock). */
    val startedAt: Long,
    val cycleDays: Int,
    val createdAt: Long,
    /** Tylko jeden cykl bywa aktywny; pozostałe czekają na liście planów (D-42). */
    val active: Boolean,
    /** v4: `PlanSource` jako tekst (WIZARD, PRESET, CUSTOM, IMPORTED). */
    val source: String = "WIZARD",
    val description: String = "",
    val author: String = "",
    val originId: String? = null,
    val shareUid: String? = null,
    val updatedAt: Long = 0,
    /** Kotwica rytmu dni; 0 = od `startedAt`. */
    val rhythmStart: Long = 0,
)

/** Dzień cyklu: `workoutId == null` to dzień odpoczynku. */
@Entity(
    tableName = "program_days",
    primaryKeys = ["programId", "dayIndex"],
    indices = [Index("programId"), Index("workoutId")],
)
data class ProgramDayEntity(
    val programId: String,
    val dayIndex: Int,
    val workoutId: String?,
)

/**
 * Notatka do ćwiczenia w obrębie jednego planu (D-44), np. ciężar przy wyciskaniu. Klucz to plan i ćwiczenie
 * pokazane na ekranie, więc notatka wraca w każdym treningu i tygodniu tego planu, a w innym planie jej nie ma.
 */
@Entity(
    tableName = "plan_exercise_notes",
    primaryKeys = ["programId", "exerciseId"],
    indices = [Index("programId")],
)
data class PlanExerciseNoteEntity(
    val programId: String,
    val exerciseId: String,
    val note: String,
    val updatedAt: Long,
)

/** Pamięć trenera per ćwiczenie (D-35): korekta celu po skalowaniu poziomem i wykluczenie z planów. */
@Entity(tableName = "exercise_tuning")
data class ExerciseTuningEntity(
    @PrimaryKey val exerciseId: String,
    val repsDelta: Int,
    val secondsDelta: Int,
    val excluded: Boolean,
    val updatedAt: Long,
)
