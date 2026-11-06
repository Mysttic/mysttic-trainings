package pl.mysttic.trainings.ui.planeditor

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.domain.plan.DraftDay
import pl.mysttic.trainings.domain.plan.PlanDay
import pl.mysttic.trainings.domain.plan.PlanEditing
import pl.mysttic.trainings.domain.plan.PlanSessions
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.TrainingProfile
import pl.mysttic.trainings.ui.navigation.PlanEditorRoute
import javax.inject.Inject

/** Szkic edycji: przeżywa obrót, zmianę okna i śmierć procesu (SavedStateHandle). */
@Serializable
data class PlanDraft(
    val programId: String,
    val isNew: Boolean,
    val name: String,
    val description: String,
    /** true = plan dosłowny (bloki `fixed`), false = dopasowany do poziomu i trenera. */
    val literal: Boolean,
    val days: List<DraftDay>,
)

data class PlanEditorUiState(
    val loading: Boolean = true,
    val draft: PlanDraft? = null,
    /** Treningi planu (id z prefiksem planu). */
    val workouts: List<Workout> = emptyList(),
    /** Treningi, które da się skopiować do planu: z innych planów (i wbudowane sprzed seedu v7, jeśli mają historię). */
    val library: List<Workout> = emptyList(),
    /** Klucze dni zrobionych przed edycją (D-21): tych nie wolno ruszać. */
    val lockedKeys: Set<String> = emptySet(),
    /** Dni, które od razu odhaczy trening zrobiony więcej razy, niż stał w planie (`PlanEditing.surplusKeys`). */
    val surplusKeys: Set<String> = emptySet(),
    /** Trening tego planu właśnie trwa: plan tylko do odczytu. */
    val locked: Boolean = false,
    val notEditable: Boolean = false,
    val errors: List<String> = emptyList(),
    val saved: Boolean = false,
    val discarded: Boolean = false,
) {
    val workoutsById: Map<String, Workout> get() = workouts.associateBy { it.id }
}

/**
 * Edytor planu (D-42): dni, odpoczynki, przypisanie treningów, szkielet tygodni, plan dosłowny albo dopasowany.
 * Operacje na dniach to czyste funkcje [PlanEditing]; zapis idzie jedną transakcją w [ProgramRepository.savePlan].
 * Treningi planu edytuje zwykły edytor treningu — zapisuje je od razu, więc wracają tu z bazy.
 * Nowy plan dostaje id od razu (prefiks treningów), ale wiersz w bazie dopiero przy pierwszym zapisie: porzucony
 * szkic (zamknięcie aplikacji, dwa tapnięcia) nie zostaje na liście planów jako „Nowy plan · Bez treningów”.
 */
@HiltViewModel
class PlanEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val programRepo: ProgramRepository,
    private val workoutRepo: WorkoutRepository,
    private val sessionRepo: SessionRepository,
    private val settingsRepo: SettingsRepository,
    private val exerciseRepo: ExerciseRepository,
    private val json: Json,
) : ViewModel() {

    private val routeId: String? = runCatching { savedStateHandle.toRoute<PlanEditorRoute>().programId }.getOrNull()
    private val draft = MutableStateFlow<PlanDraft?>(restoreDraft())
    private val meta = MutableStateFlow(Meta())
    private var original: PlanDraft? = null
    private var saving = false
    private val copying = Mutex()

    private data class Meta(
        val lockedKeys: Set<String> = emptySet(),
        /** Treningi z ukończonych sesji planu w kolejności wykonania: z nich kolejka odhacza dni szkicu. */
        val completed: List<String> = emptyList(),
        val locked: Boolean = false,
        val notEditable: Boolean = false,
        val errors: List<String> = emptyList(),
        val saved: Boolean = false,
        val discarded: Boolean = false,
        val loading: Boolean = true,
    )

    val ui: StateFlow<PlanEditorUiState> = combine(
        draft,
        // Bez szkicu (plan nie istnieje) też emitujemy, inaczej combine milczy i ekran zostaje na „ładowaniu”.
        draft.map { it?.programId }.distinctUntilChanged().flatMapLatest { id ->
            if (id == null) flowOf(emptyList<Workout>()) else workoutRepo.observeOfProgram(id)
        },
        workoutRepo.observeAll(),
        meta,
    ) { d, workouts, all, m ->
        val prefix = d?.programId?.let { "$it-" }
        PlanEditorUiState(
            loading = m.loading,
            draft = d,
            workouts = workouts,
            library = all.filter { prefix == null || !it.id.startsWith(prefix) },
            lockedKeys = m.lockedKeys,
            surplusKeys = d?.let { PlanEditing.surplusKeys(it.days, m.lockedKeys, m.completed) }.orEmpty(),
            locked = m.locked,
            notEditable = m.notEditable,
            errors = m.errors,
            saved = m.saved,
            discarded = m.discarded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanEditorUiState())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val restored = draft.value
            val isNew = routeId == null
            val program = (restored?.programId ?: routeId)?.let { programRepo.byId(it) }
            if (program == null && !isNew) {
                meta.update { it.copy(loading = false, notEditable = true) }
                return
            }
            val sessions = if (program != null) sessionRepo.allSessions() else emptyList()
            val completed = program?.let { PlanSessions.completedWorkoutIds(sessions, it) }.orEmpty()
            val base = if (program == null) {
                // Nowy plan, jeszcze bez wiersza w bazie: ten powstanie przy zapisie (`createRow`).
                PlanDraft(
                    programId = restored?.programId ?: programRepo.newProgramId(),
                    isNew = true,
                    name = NEW_PLAN_NAME,
                    description = "",
                    literal = NEW_PLAN_LITERAL,
                    days = emptyList(),
                )
            } else {
                PlanDraft(
                    programId = program.id,
                    isNew = isNew,
                    name = program.name,
                    description = program.description,
                    literal = literalOf(program.days, workoutRepo.ofProgram(program.id)),
                    days = PlanEditing.fromPlan(program.days),
                )
            }
            original = base
            // Treningi planu sprzed tej edycji (C3-5): „Porzuć” usunie tylko te, które doszły w trakcie. Zapis w stanie,
            // bo po śmierci procesu szkic wraca, a lista z bazy miałaby już kopie z tej edycji.
            if (savedStateHandle.get<ArrayList<String>>(KEY_INITIAL_WORKOUTS) == null) {
                savedStateHandle[KEY_INITIAL_WORKOUTS] = ArrayList(workoutRepo.ofProgram(base.programId).map { it.id })
            }
            // Szkic przed zdjęciem „ładowania”, żeby ekran nie mignął „Nie znaleziono planu”.
            if (restored == null) setDraft(base)
            val inProgress = sessions.firstOrNull { it.status == SessionStatus.IN_PROGRESS }
            meta.update {
                it.copy(
                    loading = false,
                    lockedKeys = PlanEditing.doneKeys(base.days, completed),
                    completed = completed,
                    locked = program != null && inProgress != null && PlanSessions.belongsTo(inProgress, program),
                    notEditable = program != null && !program.editableInPlace,
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Nie udało się otworzyć planu", t)
            meta.update { it.copy(loading = false, notEditable = true) }
        }
    }

    fun setName(value: String) = change { it.copy(name = value.take(ProgramRepository.MAX_NAME)) }
    fun setDescription(value: String) = change { it.copy(description = value.take(ProgramRepository.MAX_DESCRIPTION)) }
    fun setLiteral(value: Boolean) = change { it.copy(literal = value) }

    fun addTrainingDay(workoutId: String) = days { PlanEditing.addDay(it, workoutId) }
    fun addRestDay() = days { PlanEditing.addDay(it, null) }
    fun removeDay(index: Int) = days { PlanEditing.removeDay(it, index) }
    fun moveDay(from: Int, to: Int) = days { PlanEditing.moveDay(it, from, to) }
    fun duplicateDay(index: Int) = days { PlanEditing.duplicateDay(it, index) }
    fun setDayWorkout(index: Int, workoutId: String?) = days { PlanEditing.setWorkout(it, index, workoutId) }
    fun appendWeekCopy() = days { PlanEditing.appendWeekCopy(it) }

    fun skeleton(weeks: Int, perWeek: Int) {
        val rotation = ui.value.workouts.map { it.id }
        if (rotation.isEmpty()) return
        days { PlanEditing.skeleton(weeks, perWeek, rotation) }
    }

    /** Id dla nowego treningu planu; edytor treningu zapisze go pod tym id. */
    fun newWorkoutId(): String? = draft.value?.programId?.let { programRepo.newWorkoutId(it) }

    /**
     * Kopia treningu jako nowy trening tego planu: „Skopiuj trening” bierze trening z innego planu (albo wbudowany
     * z historią), a „×2” przy treningu planu (D-49) jego kopię obok, żeby trening B ułożyć z A zamiast od zera.
     * Kopia dostaje tryb planu ze szkicu, jak „Nowy trening”: w planie dosłownym bloki `fixed`, w dopasowanym bez.
     * Inaczej kopia z kreatora w planie dosłownym przełączyłaby go przy zapisie na dopasowany. Bloki i nazwę liczy
     * domena ([PlanEditing.copyBlocks], [PlanEditing.copyName]): kopia z innego planu do planu dosłownego niesie
     * liczby, które użytkownik widział, a kopia z tego samego planu dostaje „(kopia)”. Kopie idą po kolei pod zamkiem:
     * dwa szybkie tapnięcia dają „(kopia)” i „(kopia 2)”, a nie dwie takie same nazwy.
     */
    fun copyWorkout(workoutId: String) {
        val d = draft.value ?: return
        viewModelScope.launch {
            copying.withLock {
                runCatching {
                    val planWorkouts = workoutRepo.ofProgram(d.programId)
                    val fromSamePlan = planWorkouts.any { it.id == workoutId }
                    val settings = settingsRepo.current()
                    val exercises = exerciseRepo.getMap()
                    programRepo.copyWorkoutInto(d.programId, workoutId) { source ->
                        source.copy(
                            name = PlanEditing.copyName(source.name, planWorkouts.map { it.name }),
                            blocks = PlanEditing.copyBlocks(
                                blocks = source.blocks,
                                literal = d.literal,
                                fromSamePlan = fromSamePlan,
                                difficulty = settings.difficulty,
                                exercises = exercises,
                                owned = settings.ownedEquipment,
                            ),
                        )
                    }
                }.onFailure { Log.e(TAG, "Kopia treningu nie wyszła", it) }
            }
        }
    }

    fun hasChanges(): Boolean {
        val d = draft.value ?: return false
        // Nowy plan bez zmian i bez treningów nie zostawia nic w bazie, więc wychodzi bez pytania.
        if (d.isNew) return d != original || ui.value.workouts.isNotEmpty()
        // Trening dodany w tej edycji (nowy, skopiowany, „×2”) jest już w bazie. Wyjście bez pytania zostawiłoby go
        // w planie bez dnia i bez sposobu na usunięcie, więc pytamy: „Porzuć” go zabiera, „Zapisz plan” zostawia.
        val initial = savedStateHandle.get<ArrayList<String>>(KEY_INITIAL_WORKOUTS)?.toSet() ?: return d != original
        return d != original || ui.value.workouts.any { it.id !in initial }
    }

    fun save() {
        val d = draft.value ?: return
        val state = ui.value
        if (state.locked || state.notEditable || saving) return
        val errors = PlanEditing.validate(d.name, d.days, state.workouts.map { it.id }.toSet(), state.lockedKeys, meta.value.completed)
        if (errors.isNotEmpty()) {
            meta.update { it.copy(errors = errors) }
            return
        }
        saving = true
        viewModelScope.launch {
            try {
                val days = PlanEditing.toPlanDays(d.days)
                if (d.isNew && programRepo.byId(d.programId) == null) createRow(d, days)
                programRepo.savePlan(d.programId, d.name, d.description, days, literal = d.literal)
                savedStateHandle.remove<String>(KEY_DRAFT)
                meta.update { it.copy(saved = true) }
            } catch (t: Throwable) {
                saving = false
                Log.e(TAG, "Zapis planu nie wyszedł", t)
                meta.update { it.copy(errors = listOf(t.message ?: "Nie udało się zapisać planu.")) }
            }
        }
    }

    /** Wiersz nowego planu przy pierwszym zapisie: własny, nieaktywny, od razu z nazwą i dniami ze szkicu. */
    private suspend fun createRow(d: PlanDraft, days: List<PlanDay>) {
        val settings = settingsRepo.current()
        val now = System.currentTimeMillis()
        val program = Program(
            id = d.programId,
            name = d.name.trim().take(ProgramRepository.MAX_NAME),
            profile = TrainingProfile.neutral(settings.difficulty, cycleDays = days.size.coerceAtLeast(1)),
            startedAt = now,
            cycleDays = days.size,
            createdAt = now,
            active = false,
            days = days,
            source = PlanSource.CUSTOM,
            description = d.description.trim().take(ProgramRepository.MAX_DESCRIPTION),
            author = settings.authorName,
            updatedAt = now,
        )
        programRepo.insert(program, emptyList())
    }

    /**
     * Tryb planu z treningów, które stoją w dniach: nieużyta kopia treningu nie przełącza planu dosłownego
     * na dopasowany. Plan bez dni ocenia po wszystkich swoich treningach, a plan bez treningów dostaje tryb
     * nowego planu własnego ([NEW_PLAN_LITERAL]).
     */
    private fun literalOf(days: List<PlanDay>, workouts: List<Workout>): Boolean {
        val used = days.mapNotNull { it.workoutId }.toSet()
        val deciding = workouts.filter { it.id in used }.ifEmpty { workouts }
        return if (deciding.isEmpty()) NEW_PLAN_LITERAL else deciding.all { it.isFixed }
    }

    /**
     * Porzucenie: nowy plan znika razem z treningami, które w nim powstały; istniejący zostaje jak był, a treningi
     * dodane w tej edycji (kopie, nowe z edytora treningu) znikają, o ile żadna sesja ich nie tknęła (C3-5).
     */
    fun discard() {
        val d = draft.value
        viewModelScope.launch {
            if (d != null && d.isNew) runCatching { programRepo.delete(d.programId) }
            if (d != null && !d.isNew) {
                runCatching {
                    val initial = savedStateHandle.get<ArrayList<String>>(KEY_INITIAL_WORKOUTS).orEmpty().toSet()
                    val used = sessionRepo.allSessions().mapNotNull { it.workoutId }.toSet()
                    workoutRepo.ofProgram(d.programId)
                        .filter { it.id !in initial && it.id !in used }
                        .forEach { workoutRepo.delete(it) }
                }.onFailure { Log.e(TAG, "Sprzątanie po porzuconej edycji nie wyszło", it) }
            }
            savedStateHandle.remove<ArrayList<String>>(KEY_INITIAL_WORKOUTS)
            savedStateHandle.remove<String>(KEY_DRAFT)
            meta.update { it.copy(discarded = true) }
        }
    }

    private fun days(transform: (List<DraftDay>) -> List<DraftDay>) = change { it.copy(days = transform(it.days)) }

    private fun change(transform: (PlanDraft) -> PlanDraft) {
        val current = draft.value ?: return
        setDraft(transform(current))
        meta.update { it.copy(errors = emptyList()) }
    }

    private fun setDraft(value: PlanDraft) {
        draft.value = value
        savedStateHandle[KEY_DRAFT] = json.encodeToString(PlanDraft.serializer(), value)
    }

    private fun restoreDraft(): PlanDraft? = savedStateHandle.get<String>(KEY_DRAFT)?.let { text ->
        runCatching { json.decodeFromString(PlanDraft.serializer(), text) }.getOrNull()
    }

    private companion object {
        const val TAG = "PlanEditorViewModel"
        const val KEY_DRAFT = "plan_draft"
        const val KEY_INITIAL_WORKOUTS = "plan_initial_workouts"
        const val NEW_PLAN_NAME = "Nowy plan"

        /**
         * Nowy plan własny startuje dosłownie (D-49, zmiana względem D-42): cele dokładnie jak wpisane, a „Wyślij plan”
         * nie przelicza ich na poziom i sprzęt autora. Plan dla siebie przełącza się na dopasowany jednym przełącznikiem.
         */
        const val NEW_PLAN_LITERAL = true
    }
}
