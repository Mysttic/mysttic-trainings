package pl.mysttic.trainings.service

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.domain.health.HealthExerciseKind
import pl.mysttic.trainings.domain.health.HealthExport
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.Workout
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zapis ukończonego treningu do Health Connect (M6). Jednostronny: nic stamtąd nie czytamy, historia w bazie
 * zostaje źródłem prawdy. Funkcja jest opcjonalna — wyłączona domyślnie i cicho pomijana, gdy nie ma dostawcy
 * albo użytkownik nie dał uprawnienia.
 */
@Singleton
class HealthConnectExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseRepo: ExerciseRepository,
) {

    /** Uprawnienie do zapisu sesji treningowej — o tyle prosimy w Ustawieniach. */
    val permissions: Set<String> = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))

    val available: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient? =
        if (available) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    suspend fun hasPermission(): Boolean {
        val client = client() ?: return false
        return runCatching { client.permissionController.getGrantedPermissions().containsAll(permissions) }
            .getOrDefault(false)
    }

    /** Best effort: błąd zapisu do dziennika zdrowia nie może wywrócić końca treningu. */
    suspend fun export(session: Session, workout: Workout?) {
        val endedAt = session.endedAt ?: return
        if (!HealthExport.exportable(session.status, session.startedAt, endedAt)) return
        val client = client() ?: return
        if (!hasPermission()) return

        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(session.startedAt)
        val end = Instant.ofEpochMilli(endedAt)
        val hasCircuit = workout?.blocks?.any { it.kind == BlockKind.CIRCUIT } == true
        // Praktyka oddechowa (D-37) idzie do dziennika jako oddech prowadzony, nie jako trening.
        val breathing = workout != null && HealthExport.isBreathing(workout, exerciseRepo.getMap())
        val record = ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = zone.rules.getOffset(start),
            endTime = end,
            endZoneOffset = zone.rules.getOffset(end),
            exerciseType = exerciseType(HealthExport.kind(hasCircuit, breathing)),
            title = HealthExport.title(session.workoutName),
            // Sesję mierzy stoper aplikacji w trakcie treningu, więc to zapis „na żywo”, nie wpis ręczny.
            metadata = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE)),
        )
        runCatching { client.insertRecords(listOf(record)) }
            .onFailure { Log.e(TAG, "Zapis do Health Connect nie powiódł się", it) }
    }

    private fun exerciseType(kind: HealthExerciseKind): Int = when (kind) {
        HealthExerciseKind.HIIT -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        HealthExerciseKind.CALISTHENICS -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
        HealthExerciseKind.BREATHING -> ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING
    }

    companion object {
        private const val TAG = "HealthConnect"
    }
}
