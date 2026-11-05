package pl.mysttic.trainings.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.data.db.AppDatabase
import pl.mysttic.trainings.data.db.dao.ExerciseDao
import pl.mysttic.trainings.data.db.dao.ProgramDao
import pl.mysttic.trainings.data.db.dao.SessionDao
import pl.mysttic.trainings.data.db.dao.TuningDao
import pl.mysttic.trainings.data.db.dao.WorkoutDao
import pl.mysttic.trainings.data.db.entity.ExerciseEntity
import pl.mysttic.trainings.data.db.entity.ProgramDayEntity
import pl.mysttic.trainings.data.db.entity.ProgramEntity
import pl.mysttic.trainings.data.db.entity.SessionEntity
import pl.mysttic.trainings.data.db.entity.SetLogEntity
import pl.mysttic.trainings.data.db.entity.ExerciseTuningEntity
import pl.mysttic.trainings.data.db.entity.WorkoutEntity
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.PlanSessions
import pl.mysttic.trainings.domain.plan.TrainingProfile
import javax.inject.Inject
import javax.inject.Singleton
import pl.mysttic.trainings.domain.plan.PlanNotes
import pl.mysttic.trainings.data.db.entity.PlanExerciseNoteEntity

/**
 * Kopia zapasowa danych użytkownika do pliku JSON przez Storage Access Framework (M3, D-18).
 * Eksport: historia, ćwiczenia własne, treningi własne, cykle treningowe, pamięć trenera i ustawienia —
 * czyli wszystko, czego nie odtworzy seed. Import scala: pozycja o znanym id jest pomijana (sesja razem
 * z seriami, cykl razem z dniami), reszta dopisywana. Aktywny cykl z pliku staje się aktywnym cyklem
 * aplikacji: kopię wczytuje się po to, żeby wrócić do swojego planu.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val sessionDao: SessionDao,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    private val tuningDao: TuningDao,
    private val settings: SettingsRepository,
    private val json: Json,
) {
    private val prettyJson = Json(json) { prettyPrint = true }
    private val blocksSerializer = ListSerializer(Block.serializer())

    suspend fun export(uri: Uri, exportedAt: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val file = snapshot(exportedAt)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(prettyJson.encodeToString(BackupFile.serializer(), file).toByteArray())
        } ?: error("Nie udało się otworzyć pliku do zapisu.")
        file
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        // Limit sprawdzany w trakcie czytania, nie po wczytaniu całości.
        val text = context.contentResolver.openInputStream(uri)?.use { BackupFile.readText(it) }
            ?: error("Nie udało się otworzyć pliku.")
        val file = BackupFile.parse(text, json)
        val result = db.withTransaction { apply(file) }
        // Ustawienia (DataStore) poza transakcją bazy: nie da się ich wycofać razem z nią, więc idą po udanym zapisie.
        file.settings?.let { restore(it, file.version) }
        result
    }

    private suspend fun snapshot(exportedAt: Long): BackupFile {
        val current = settings.current()
        return BackupFile(
            exportedAt = exportedAt,
            settings = BackupSettings(
                difficulty = current.difficulty,
                ownedEquipment = current.ownedEquipment.sortedBy { it.name },
                countdownSec = current.countdownSec,
                soundEnabled = current.soundEnabled,
                ttsEnabled = current.ttsEnabled,
                vibrationEnabled = current.vibrationEnabled,
                keepScreenOn = current.keepScreenOn,
                reminderEnabled = current.reminderEnabled,
                reminderHour = current.reminderHour,
                reminderMinute = current.reminderMinute,
                volumeKeyDone = current.volumeKeyDone,
                healthConnectEnabled = current.healthConnectEnabled,
                updateChecks = current.updateChecks,
                libraryTiles = current.libraryTiles,
                themeId = current.themeId,
                authorName = current.authorName,
            ),
            exercises = exerciseDao.allCustom().map { it.toBackup() },
            workouts = workoutDao.allUser().map { it.toBackup() },
            sessions = sessionDao.allSessions().map { it.toBackup() },
            setLogs = sessionDao.allSetLogs().map { it.toBackup() },
            tuning = tuningDao.all().map { BackupTuning(it.exerciseId, it.repsDelta, it.secondsDelta, it.excluded, it.updatedAt) },
            programs = programDao.all().map { it.toBackup(programDao.days(it.id)) },
            planNotes = programDao.allNotes().map { BackupPlanNote(it.programId, it.exerciseId, it.note, it.updatedAt) },
        )
    }

    private suspend fun apply(file: BackupFile): ImportResult {
        val knownSessions = sessionDao.allSessionIds().toSet()
        val newSessions = file.sessions.filter { it.id !in knownSessions }
        val newIds = newSessions.map { it.id }.toSet()
        val newLogs = file.setLogs.filter { it.sessionId in newIds }

        // Ćwiczenie o id wbudowanego nie nadpisuje seedu: kopia niesie tylko własne, więc to plik podrobiony albo stary.
        val builtIn = exerciseDao.getAll().filterNot { it.isCustom }.map { it.id }.toSet()
        val exercises = file.exercises.filter { it.id !in builtIn }
        if (exercises.isNotEmpty()) exerciseDao.upsertAll(exercises.map { it.toEntity() })
        // Cykl, który już jest w bazie, pomijamy razem z jego treningami: upsert cofnąłby podbite cele
        // i zmiany z edytorów do stanu z pliku (plany-do-udostepniania.md, kopia v3).
        val workouts = workoutsToRestore(file.workouts, programDao.all().map { it.id }.toSet(), file.programs.map { it.id })
        if (workouts.isNotEmpty()) workoutDao.upsertAll(workouts.map { it.toEntity() })
        if (newSessions.isNotEmpty()) sessionDao.insertSessions(newSessions.map { it.toEntity() })
        if (newLogs.isNotEmpty()) sessionDao.insertSetLogs(newLogs.map { it.toEntity() })
        if (file.tuning.isNotEmpty()) {
            tuningDao.upsertAll(file.tuning.map { ExerciseTuningEntity(it.exerciseId, it.repsDelta, it.secondsDelta, it.excluded, it.updatedAt) })
        }
        val newPrograms = restorePrograms(file.programs)
        // Notatki wchodzą tylko do planów, które przyszły z pliku; plan, który już masz, zostaje nietknięty (D-44).
        val freshIds = newPrograms.map { it.id }.toSet()
        val notes = file.planNotes.filter { it.programId in freshIds }
            .map { it.copy(note = PlanNotes.clean(it.note)) }
            .filter { it.note.isNotEmpty() }
        if (notes.isNotEmpty()) {
            programDao.upsertNotes(notes.map { PlanExerciseNoteEntity(it.programId, it.exerciseId, it.note, it.updatedAt) })
        }

        return ImportResult(
            sessions = newSessions.size,
            setLogs = newLogs.size,
            exercises = exercises.size,
            workouts = workouts.size,
            programs = newPrograms.size,
            skippedSessions = file.sessions.size - newSessions.size,
            settingsRestored = file.settings != null,
            planRestored = newPrograms.any { it.active },
        )
    }

    /**
     * Cykle o nieznanym id trafiają do bazy razem z dniami; znany cykl zostaje taki, jaki jest w bazie
     * (jego dni mogły już zostać rozliczone). Jeśli plik przynosi aktywny cykl, to on zostaje aktywny —
     * dwa aktywne naraz nie mają prawa istnieć (D-20), a użytkownik wczytuje kopię po to, żeby wrócić do planu.
     */
    private suspend fun restorePrograms(programs: List<BackupProgram>): List<BackupProgram> {
        if (programs.isEmpty()) return emptyList()
        val known = programDao.all().map { it.id }.toSet()
        val fresh = programs.filter { it.id !in known }
        if (fresh.isEmpty()) return emptyList()
        // Jeden aktywny plan (D-42): aktywny z pliku wygrywa, a gdyby plik miał ich kilka — pierwszy.
        val activeId = fresh.firstOrNull { it.active }?.id
        if (activeId != null) programDao.deactivateAll()
        fresh.forEach { program ->
            programDao.insertProgram(program.toEntity().copy(active = program.id == activeId))
            programDao.insertDays(program.days.map { ProgramDayEntity(program.id, it.dayIndex, it.workoutId) })
        }
        return fresh
    }

    private suspend fun restore(s: BackupSettings, version: Int) {
        settings.setDifficulty(s.difficulty)
        settings.setOwnedEquipment(s.ownedEquipment.toSet())
        settings.setCountdownSec(s.countdownSec)
        settings.setSoundEnabled(s.soundEnabled)
        settings.setTtsEnabled(s.ttsEnabled)
        settings.setVibrationEnabled(s.vibrationEnabled)
        settings.setKeepScreenOn(s.keepScreenOn)
        settings.setReminderEnabled(s.reminderEnabled)
        settings.setReminderTime(s.reminderHour, s.reminderMinute)
        settings.setVolumeKeyDone(s.volumeKeyDone)
        settings.setHealthConnectEnabled(s.healthConnectEnabled)
        settings.setUpdateChecks(s.updateChecks)
        settings.setLibraryTiles(s.libraryTiles)
        settings.setThemeId(s.themeId)
        // Pole nieobecne w starszej kopii to nie puste imię: wpisane ręcznie zostaje.
        if (version >= BackupFile.AUTHOR_NAME_SINCE) settings.setAuthorName(s.authorName)
    }

    private fun ProgramEntity.toBackup(days: List<ProgramDayEntity>) = BackupProgram(
        id = id, name = name, profileJson = profileJson, startedAt = startedAt, cycleDays = cycleDays,
        createdAt = createdAt, active = active,
        days = days.map { BackupProgramDay(it.dayIndex, it.workoutId) },
        source = source, description = description, author = author, originId = originId, shareUid = shareUid,
        updatedAt = updatedAt, rhythmStart = rhythmStart,
    )

    private fun BackupProgram.toEntity() = ProgramEntity(
        id = id, name = name, profileJson = profileJson, startedAt = startedAt, cycleDays = cycleDays,
        createdAt = createdAt, active = active,
        source = PlanSource.parse(source)?.name ?: sourceFromProfile(profileJson),
        description = description, author = author, originId = originId, shareUid = shareUid,
        updatedAt = if (updatedAt > 0) updatedAt else createdAt, rhythmStart = rhythmStart,
    )

    /** Kopia sprzed wersji 3: ta sama reguła co migracja bazy 3 → 4. */
    private fun sourceFromProfile(profileJson: String): String {
        val preset = runCatching { json.decodeFromString(TrainingProfile.serializer(), profileJson).isPreset }.getOrDefault(false)
        return if (preset) PlanSource.PRESET.name else PlanSource.WIZARD.name
    }

    private fun ExerciseEntity.toBackup() = BackupExercise(
        id = id, nameEn = nameEn, namePl = namePl, kind = kind, level = level, category = category,
        equipment = equipment.toList(), primaryMuscles = primaryMuscles, secondaryMuscles = secondaryMuscles,
        instructionsPl = instructionsPl, images = images, easierId = easierId, harderId = harderId,
        defaultReps = defaultReps, defaultSeconds = defaultSeconds,
    )

    private fun BackupExercise.toEntity() = ExerciseEntity(
        id = id, nameEn = nameEn, namePl = namePl, kind = kind, level = level, category = category,
        equipment = equipment.toSet(), primaryMuscles = primaryMuscles, secondaryMuscles = secondaryMuscles,
        instructionsPl = instructionsPl, images = images, easierId = easierId, harderId = harderId,
        defaultReps = defaultReps, defaultSeconds = defaultSeconds, isCustom = true,
    )

    private fun WorkoutEntity.toBackup() = BackupWorkout(
        id = id, name = name, note = note, createdAt = createdAt, updatedAt = updatedAt,
        blocks = if (blocksJson.isBlank()) emptyList() else json.decodeFromString(blocksSerializer, blocksJson),
    )

    private fun BackupWorkout.toEntity() = WorkoutEntity(
        id = id, name = name, note = note, blocksJson = json.encodeToString(blocksSerializer, blocks),
        createdAt = createdAt, updatedAt = updatedAt, isBuiltIn = false,
    )

    private fun SessionEntity.toBackup() = BackupSession(
        id = id, workoutId = workoutId, workoutName = workoutName, difficulty = difficulty,
        startedAt = startedAt, endedAt = endedAt, status = status, rpe = rpe, note = note,
        programId = programId, plannedSets = plannedSets,
    )

    private fun BackupSession.toEntity() = SessionEntity(
        id = id, workoutId = workoutId, workoutName = workoutName, difficulty = difficulty,
        startedAt = startedAt, endedAt = endedAt, status = status, rpe = rpe, note = note,
        programId = programId, plannedSets = plannedSets,
    )

    private fun SetLogEntity.toBackup() = BackupSetLog(
        sessionId = sessionId, exerciseId = exerciseId, exerciseName = exerciseName,
        blockIndex = blockIndex, itemIndex = itemIndex, round = round, setIndex = setIndex,
        targetKind = targetKind, targetValue = targetValue, actualValue = actualValue,
        weightKg = weightKg, completedAt = completedAt,
    )

    /** `id = 0`: klucz nadaje baza, więc import nigdy nie nadpisze istniejącej serii. */
    private fun BackupSetLog.toEntity() = SetLogEntity(
        id = 0, sessionId = sessionId, exerciseId = exerciseId, exerciseName = exerciseName,
        blockIndex = blockIndex, itemIndex = itemIndex, round = round, setIndex = setIndex,
        targetKind = targetKind, targetValue = targetValue, actualValue = actualValue,
        weightKg = weightKg, completedAt = completedAt,
    )

    companion object {
        /**
         * Treningi z pliku do zapisu: bez tych, których właściciel (prefiks `"$programId-"`, D-42) jest już w bazie.
         * Treningi spoza planów (własne, kopie wbudowanych) i treningi nowych cykli wchodzą jak dotąd. Właściciela
         * szukamy wśród planów z bazy i z pliku razem, żeby najdłuższy prefiks rozstrzygał jak w `PlanSessions`.
         */
        internal fun workoutsToRestore(
            workouts: List<BackupWorkout>,
            knownProgramIds: Set<String>,
            fileProgramIds: Collection<String>,
        ): List<BackupWorkout> {
            val all = knownProgramIds + fileProgramIds
            return workouts.filter { PlanSessions.ownerOf(it.id, all) !in knownProgramIds }
        }
    }
}
