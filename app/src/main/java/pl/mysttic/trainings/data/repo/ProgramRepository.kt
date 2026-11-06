package pl.mysttic.trainings.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.data.db.AppDatabase
import pl.mysttic.trainings.data.db.dao.ProgramDao
import pl.mysttic.trainings.data.db.dao.SessionDao
import pl.mysttic.trainings.data.db.dao.WorkoutDao
import pl.mysttic.trainings.data.db.entity.PlanExerciseNoteEntity
import pl.mysttic.trainings.data.db.entity.ProgramDayEntity
import pl.mysttic.trainings.data.db.entity.ProgramEntity
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.domain.plan.GeneratedPlan
import pl.mysttic.trainings.domain.plan.PlanCopy
import pl.mysttic.trainings.domain.plan.PlanDay
import pl.mysttic.trainings.domain.plan.PlanNotes
import pl.mysttic.trainings.domain.plan.PlanRhythm
import pl.mysttic.trainings.domain.plan.PlanSessions
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.TrainingProfile
import pl.mysttic.trainings.domain.plan.preset.PresetCatalog
import pl.mysttic.trainings.domain.plan.preset.PresetId
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plany: z kreatora (D-20), z gotowych programów (D-37), własne i od kogoś (D-42). Aktywny jest najwyżej jeden;
 * pozostałe czekają na liście z własnym postępem. Treningi planu mają id z prefiksem `"$programId-"`, więc
 * trening należy do jednego planu, a każda kopia planu nadaje nowe id (`PlanCopy`). Zapisy wieloetapowe idą
 * w transakcji: przerwany zapis nie zostawi planu bez dni ani dwóch aktywnych planów.
 */
@Singleton
class ProgramRepository @Inject constructor(
    private val db: AppDatabase,
    private val programDao: ProgramDao,
    private val workoutDao: WorkoutDao,
    private val sessionDao: SessionDao,
    private val workoutRepo: WorkoutRepository,
    private val json: Json,
) {
    fun observeActive(): Flow<Program?> = programDao.observeActive().flatMapLatest { entity ->
        if (entity == null) flowOf(null)
        else programDao.observeDays(entity.id).map { days -> entity.toDomain(days) }
    }

    suspend fun active(): Program? = programDao.active()?.let { it.toDomain(programDao.days(it.id)) }

    /** Wszystkie plany z dniami — pod listę planów. */
    fun observeAll(): Flow<List<Program>> = combine(programDao.observeAll(), programDao.observeAllDays()) { programs, days ->
        val byProgram = days.groupBy { it.programId }
        programs.map { it.toDomain(byProgram[it.id].orEmpty()) }
    }

    /** Plan po id; null, gdy zniknął (usunięty na innym ekranie). */
    fun observeById(id: String): Flow<Program?> = combine(programDao.observeById(id), programDao.observeDays(id)) { entity, days ->
        entity?.toDomain(days)
    }

    suspend fun byId(id: String): Program? = programDao.byId(id)?.let { it.toDomain(programDao.days(it.id)) }

    /**
     * Czy trening jest pozycją w kolejce któregoś cyklu. Takiego nie wolno skasować ze szczegółów:
     * dzień planu wskazywałby na nieistniejący trening, a kolejka stanęłaby na nim na zawsze
     * (`PlanProgress` odhacza dzień dopiero ukończoną sesją tego treningu).
     */
    fun observeInProgram(workoutId: String): Flow<Boolean> = programDao.observeDayCount(workoutId).map { it > 0 }

    /**
     * Ostatni cykl z kreatora, aktywny albo nie. Po gotowym programie kreator startuje z jego profilu
     * (sylwetka, mnożnik celów z D-22), a preset bierze z niego wzrost i wagę. Plany własne i od kogoś
     * nie mają sylwetki, więc się nie liczą (D-42).
     */
    suspend fun lastWizardProgram(): Program? = programDao.all()
        .firstOrNull { PlanSource.parse(it.source) == PlanSource.WIZARD }
        ?.let { it.toDomain(programDao.days(it.id)) }

    /**
     * Cykl z gotowego programu (D-37): plan jest deterministyczną funkcją wyboru, więc powtórka z tymi samymi
     * argumentami daje ten sam rozkład. Zapis idzie tą samą drogą co plan z kreatora, kolejka i „Dziś” nie
     * widzą różnicy. Ćwiczenia bez posiadanego sprzętu dostają zamienniki (D-46). Rzuca [IllegalStateException],
     * gdy program dla tej długości nie ma ani jednej sesji albo wymaga sprzętu, którego nie da się zastąpić.
     */
    suspend fun startPreset(
        id: PresetId,
        level: Difficulty,
        cycleDays: Int,
        param: Int?,
        exercises: Map<String, Exercise>,
        equipment: Set<Equipment>,
        now: Long = System.currentTimeMillis(),
    ): Program {
        val unmet = PresetCatalog.unmetEquipment(PresetCatalog.get(id), exercises, equipment)
        check(unmet.isEmpty()) { "Program ${id.labelPl} wymaga: ${unmet.joinToString { it.labelPl.lowercase() }}" }
        val programId = newProgramId()
        val plan = PresetCatalog.build(id, level, cycleDays, param, programId, now, exercises, equipment)
        check(plan.workouts.isNotEmpty() && plan.sessionCount > 0) {
            "Program ${id.labelPl} nie ma treningów dla cyklu $cycleDays dni"
        }
        val firstStageMinutes = PresetCatalog.minutes(plan.workouts.first().blocks, exercises)
        val base = lastWizardProgram()?.profile
        val profile = PresetCatalog.profileFor(id, level, cycleDays, param, equipment, firstStageMinutes, base)
        return save(profile, plan, startedAt = now, programId = programId)
    }

    /** Zapisuje wygenerowany cykl i robi go aktywnym; poprzedni czeka na liście z własnym postępem. */
    suspend fun save(profile: TrainingProfile, plan: GeneratedPlan, startedAt: Long, programId: String): Program {
        val program = Program(
            id = programId,
            name = Program.nameFor(profile),
            profile = profile,
            startedAt = startedAt,
            cycleDays = profile.cycleDays,
            createdAt = startedAt,
            active = true,
            days = plan.days,
            source = if (profile.isPreset) PlanSource.PRESET else PlanSource.WIZARD,
            updatedAt = startedAt,
        )
        insert(program, plan.workouts)
        return program
    }

    /**
     * Zapis całego planu w jednej transakcji: treningi, wiersz planu, dni. Aktywny plan wyłącza pozostałe.
     * Każdy trening musi należeć do tego planu (prefiks id), inaczej dwa plany dzieliłyby postęp.
     */
    suspend fun insert(program: Program, workouts: List<Workout>, notes: Map<String, String> = emptyMap()) {
        require(workouts.all { it.id.startsWith("${program.id}-") }) { "Trening spoza planu ${program.id}" }
        db.withTransaction {
            workouts.forEach { workoutRepo.save(it) }
            if (program.active) programDao.deactivateAll()
            programDao.insertProgram(program.toEntity())
            programDao.insertDays(program.days.map { ProgramDayEntity(program.id, it.dayIndex, it.workoutId) })
            val clean = notes.mapValues { PlanNotes.clean(it.value) }.filterValues { it.isNotEmpty() }
            if (clean.isNotEmpty()) {
                programDao.upsertNotes(clean.map { (exerciseId, note) -> PlanExerciseNoteEntity(program.id, exerciseId, note, program.updatedAt) })
            }
        }
    }

    /** Notatki do ćwiczeń planu (D-44): id ćwiczenia pokazanego na ekranie → tekst. */
    fun observeNotes(programId: String): Flow<Map<String, String>> =
        programDao.observeNotes(programId).map { rows -> rows.associate { it.exerciseId to it.note } }

    suspend fun notes(programId: String): Map<String, String> =
        programDao.notes(programId).associate { it.exerciseId to it.note }

    /** Zapis notatki; pusta kasuje. Notatka nie zmienia szablonu ani postępu, więc wolno ją pisać także w planie w toku. */
    suspend fun setNote(programId: String, exerciseId: String, note: String, now: Long = System.currentTimeMillis()) {
        val clean = PlanNotes.clean(note)
        if (clean.isEmpty()) {
            programDao.deleteNote(programId, exerciseId)
        } else {
            programDao.upsertNote(PlanExerciseNoteEntity(programId, exerciseId, clean, now))
        }
    }

    /**
     * Ustawia plan jako aktywny (D-42): bieżący zostaje wstrzymany ze swoim postępem, bo postęp liczy się
     * z sesji zapisanych z `programId`. Rytm dni wznowionego planu liczy się od miejsca w kolejce.
     */
    suspend fun activate(id: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val program = byId(id) ?: return
        val sessions = sessionDao.allSessions().map { it.toDomain() }
        val progress = PlanSessions.progress(sessions, program)
        val anchor = PlanRhythm.anchorFor(progress, now, zone)
        db.withTransaction {
            programDao.setRhythm(id, anchor, now)
            programDao.activate(id)
        }
    }

    /**
     * Kopia planu do zmian: własny, nieaktywny, z nowymi id treningów (D-42). Zwraca id kopii. To nowy plan,
     * więc nie bierze tożsamości pliku (`shareUid`), a kopia planu od kogoś nie idzie dalej pod nazwiskiem jego autora.
     */
    suspend fun duplicate(id: String, now: Long = System.currentTimeMillis()): String? =
        copyOf(id, PlanSource.CUSTOM, nameSuffix = " (kopia)", activate = false, sameRun = false, now = now)

    /**
     * Ten sam plan jeszcze raz, od zera: nowy wiersz tego samego źródła, aktywny. Stary przebieg zostaje
     * na liście ze swoimi statystykami. To dalej ten sam plan: autor i `shareUid` zostają.
     */
    suspend fun repeat(id: String, now: Long = System.currentTimeMillis()): String? {
        val source = byId(id)?.source ?: return null
        return copyOf(id, source, nameSuffix = "", activate = true, sameRun = true, now = now)
    }

    private suspend fun copyOf(
        id: String,
        target: PlanSource,
        nameSuffix: String,
        activate: Boolean,
        sameRun: Boolean,
        now: Long,
    ): String? {
        val original = byId(id) ?: return null
        // Treningi z dni i te bez dnia, które plan ma po prefiksie id (edytor pozwala je trzymać): kopia nie gubi żadnego.
        val workouts = (original.days.mapNotNull { it.workoutId }.distinct().mapNotNull { workoutRepo.getById(it) } +
            workoutRepo.ofProgram(id)).associateBy { it.id }
        val newId = newProgramId()
        val copied = PlanCopy.copy(original.days, workouts, newId, now)
        val program = original.copy(
            id = newId,
            name = (original.name + nameSuffix).take(MAX_NAME),
            profile = PlanCopy.profileFor(original.profile, target),
            startedAt = now,
            createdAt = now,
            updatedAt = now,
            active = activate,
            days = copied.days,
            source = target,
            originId = original.id,
            // Autor zostaje przy powtórce, przy kopii własnego planu i przy kopii planu od kogoś (D-58): kopia nie może
            // zdjąć z planu jego autora. Kopia planu z kreatora albo gotowego programu jest Twoja.
            author = if (sameRun || original.source == PlanSource.CUSTOM || original.source == PlanSource.IMPORTED) original.author else "",
            shareUid = if (sameRun) original.shareUid else null,
            rhythmStart = 0,
        )
        // Kopia i powtórka planu zabierają notatki do ćwiczeń (D-44): ciężary z poprzedniego przebiegu są punktem wyjścia.
        insert(program, copied.workouts, notes(id))
        return newId
    }

    /** Id nowego treningu w planie; losowy ogon, żeby nie zderzył się z `"$programId-<n>"` z kopii. */
    fun newWorkoutId(programId: String): String = "$programId-w" + UUID.randomUUID().toString().take(8)

    /**
     * Kopia dowolnego treningu (wbudowanego, z innego planu albo z tego samego, D-49) jako trening tego planu
     * (D-19, D-42). `transform` zmienia treść kopii przed jedynym zapisem (nazwa, tryb planu); id i daty nadaje kopia.
     */
    suspend fun copyWorkoutInto(
        programId: String,
        workoutId: String,
        now: Long = System.currentTimeMillis(),
        transform: (Workout) -> Workout = { it },
    ): Workout? {
        val source = workoutRepo.getById(workoutId) ?: return null
        val copy = transform(source).copy(id = newWorkoutId(programId), createdAt = now, updatedAt = now, isBuiltIn = false)
        workoutRepo.save(copy)
        return copy
    }

    /**
     * Zapis edycji planu w jednej transakcji: nazwa, opis, dni (gęsto od 1) i — gdy `literal` nie jest null —
     * flaga `fixed` we wszystkich treningach planu (plan dosłowny albo dopasowany do poziomu i trenera).
     */
    suspend fun savePlan(
        id: String,
        name: String,
        description: String,
        days: List<PlanDay>,
        literal: Boolean?,
        now: Long = System.currentTimeMillis(),
    ) {
        val program = byId(id) ?: return
        require(days.all { day -> day.workoutId?.startsWith("$id-") ?: true }) { "Trening spoza planu $id" }
        val profile = program.profile.copy(cycleDays = days.size.coerceAtLeast(1))
        db.withTransaction {
            if (literal != null) {
                workoutRepo.ofProgram(id).forEach { workout ->
                    if (workout.blocks.any { it.fixed != literal }) {
                        workoutRepo.save(workout.copy(blocks = workout.blocks.map { it.copy(fixed = literal) }, updatedAt = now))
                    }
                }
            }
            programDao.updateShape(
                id = id,
                name = name.trim().take(MAX_NAME),
                description = description.trim().take(MAX_DESCRIPTION),
                cycleDays = days.size,
                profileJson = json.encodeToString(TrainingProfile.serializer(), profile),
                now = now,
            )
            programDao.deleteDays(id)
            programDao.insertDays(days.map { ProgramDayEntity(id, it.dayIndex, it.workoutId) })
        }
    }

    suspend fun rename(id: String, name: String, description: String, now: Long = System.currentTimeMillis()) {
        programDao.updateMeta(id, name.trim().take(MAX_NAME), description.trim().take(MAX_DESCRIPTION), now)
    }

    suspend fun setShareUid(id: String, uid: String) = programDao.setShareUid(id, uid)

    /**
     * Plan, do którego należy trening (D-42): po prefiksie id, a gdy go nie ma — po dniach planu.
     * Trening spoza planów (wbudowany, własny) daje null.
     */
    suspend fun ownerOf(workoutId: String): String? =
        PlanSessions.ownerOf(workoutId, programDao.allIds()) ?: programDao.programsWithWorkout(workoutId).firstOrNull()

    /** Id nadawane z góry, bo generator używa go jako prefiksu identyfikatorów treningów. */
    fun newProgramId(): String = "plan-" + UUID.randomUUID().toString().take(8)

    /**
     * Kasuje plan razem z jego treningami, o ile żadnego nie tknęła sesja — inaczej zostawały w bazie na zawsze,
     * bez wejścia w interfejsie (D-26) i z biletem do kopii zapasowej. Trening z sesją w historii zostaje,
     * bo Historia sięga po niego przy „Δ względem poprzedniego razu”. Sesje planu zostają w Historii.
     */
    suspend fun delete(programId: String) {
        db.withTransaction {
            programDao.deleteUnusedWorkouts(programId)
            programDao.deleteUnusedOwnedWorkouts("$programId-%")
            programDao.deleteDays(programId)
            programDao.deleteNotes(programId)
            programDao.deleteProgram(programId)
        }
    }

    private fun ProgramEntity.toDomain(days: List<ProgramDayEntity>): Program {
        val profile = json.decodeFromString(TrainingProfile.serializer(), profileJson)
        return Program(
            id = id,
            name = name,
            profile = profile,
            startedAt = startedAt,
            cycleDays = cycleDays,
            createdAt = createdAt,
            active = active,
            days = days.sortedBy { it.dayIndex }.map { PlanDay(it.dayIndex, it.workoutId) },
            source = PlanSource.parse(source) ?: if (profile.isPreset) PlanSource.PRESET else PlanSource.WIZARD,
            description = description,
            author = author,
            originId = originId,
            shareUid = shareUid,
            updatedAt = if (updatedAt > 0) updatedAt else createdAt,
            rhythmStart = rhythmStart,
        )
    }

    private fun Program.toEntity() = ProgramEntity(
        id = id,
        name = name,
        profileJson = json.encodeToString(TrainingProfile.serializer(), profile),
        startedAt = startedAt,
        cycleDays = cycleDays,
        createdAt = createdAt,
        active = active,
        source = source.name,
        description = description,
        author = author,
        originId = originId,
        shareUid = shareUid,
        updatedAt = updatedAt,
        rhythmStart = rhythmStart,
    )

    companion object {
        const val MAX_NAME = 80
        const val MAX_DESCRIPTION = 2000
    }
}
