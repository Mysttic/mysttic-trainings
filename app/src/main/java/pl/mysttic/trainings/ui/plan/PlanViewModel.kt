package pl.mysttic.trainings.ui.plan

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.repo.CoachRepository
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.plan.PlanProgress
import pl.mysttic.trainings.domain.plan.PlanSessions
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.preset.PresetCatalog
import pl.mysttic.trainings.ui.navigation.PlanRoute
import javax.inject.Inject

/** Wiersz w podglądzie planu: pozycja w kolejce z nazwą treningu, czasem i statusem. */
data class PlanDayRow(
    val dayIndex: Int,
    val workoutId: String?,
    val workoutName: String?,
    /** Szacowany czas tego treningu; etapy gotowego programu różnią się długością (D-37). */
    val minutes: Int,
    /** Pierwszy nieodhaczony trening — ten pójdzie po naciśnięciu Start na „Dziś”. */
    val isNext: Boolean,
    val done: Boolean,
    /** Start sesji, która odhaczyła ten dzień: data zrobionego dnia pochodzi z historii, nie z rytmu planu. */
    val doneAt: Long? = null,
) {
    val isRest: Boolean get() = workoutId == null
}

data class PlanUiState(
    val loading: Boolean = true,
    /** Trening w toku (najwyżej jeden): start innego dnia zamknąłby go jako przerwany, więc ekran najpierw pyta (T3-1). */
    val inProgressWorkoutId: String? = null,
    val inProgressName: String? = null,
    /** Trasa wskazuje konkretny plan: jego brak to „nie znaleziono”, a nie „nie masz jeszcze planu”. */
    val byId: Boolean = false,
    val program: Program? = null,
    val days: List<PlanDayRow> = emptyList(),
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val isComplete: Boolean = false,
    val deleted: Boolean = false,
    /** Cykl z gotowego programu (D-37): inny podtytuł, bez edycji protokołu. */
    val isPreset: Boolean = false,
    /** Linijka pod nagłówkiem: parametry z kreatora, tagline programu albo opis od autora. */
    val subtitle: String = "",
    val isActive: Boolean = false,
    /** Aktywny plan w trakcie, który zostanie wstrzymany, gdy aktywujemy ten (D-42). */
    val pausing: String? = null,
    /** Trening tego planu jest właśnie w toku: plan tylko do odczytu. */
    val locked: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Podgląd planu jako kolejki (D-21). „Wykonane” liczymy z sesji tego planu (D-42): N ukończonych sesji danego
 * treningu odhacza N pierwszych jego wystąpień. `PlanRoute(programId = null)` pokazuje plan aktywny.
 */
@HiltViewModel
class PlanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programRepo: ProgramRepository,
    workoutRepo: WorkoutRepository,
    private val sessionRepo: SessionRepository,
    private val exerciseRepo: ExerciseRepository,
    private val coachRepo: CoachRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    private val programId: String? = runCatching { savedStateHandle.toRoute<PlanRoute>().programId }.getOrNull()
    private val deleted = MutableStateFlow(false)
    private val busy = MutableStateFlow<String?>(null)
    private val planner = WorkoutPlanner()

    private val _opened = Channel<String>(Channel.BUFFERED)
    /** Id planu do otwarcia po duplikacie albo powtórce. */
    val opened: Flow<String> = _opened.receiveAsFlow()

    private val _editCopy = Channel<String>(Channel.BUFFERED)
    /** Id kopii do otwarcia od razu w edytorze: „Duplikuj i zmień” planu, którego nie da się zmieniać w miejscu (T3-3). */
    val editCopy: Flow<String> = _editCopy.receiveAsFlow()

    private val programFlow: Flow<Program?> =
        if (programId == null) programRepo.observeActive() else programRepo.observeById(programId)

    val uiState: StateFlow<PlanUiState> = combine(
        combine(programFlow, programRepo.observeActive(), ::Pair),
        workoutRepo.observeAll(),
        sessionRepo.observeSessions(),
        settingsRepo.settings,
        combine(deleted, busy, ::Pair),
    ) { (program, active), workouts, sessions, settings, (isDeleted, busyState) ->
        if (program == null) return@combine PlanUiState(loading = false, byId = programId != null, deleted = isDeleted)
        val byId = workouts.associateBy { it.id }
        val progress = PlanSessions.progress(sessions, program)
        // Ta sama zachłanna reguła co kolejka (D-21), ale z pozycją sesji: dzień dostaje datę sesji, która go odhaczyła.
        val doneSessions = PlanSessions.done(sessions, program).filter { it.workoutId != null }
        val doneAt = PlanProgress.assign(program.days, doneSessions.mapNotNull { it.workoutId })
            .mapValues { (_, index) -> doneSessions[index].startedAt }
        // Czas liczony tak, jak liczy go player; trening, którego nie ma w bazie, dostaje estymatę z profilu.
        val exercises = exerciseRepo.getMap()
        val minutesById = program.days.mapNotNull { it.workoutId }.distinct().associateWith { id ->
            byId[id]?.let { workout ->
                val steps =
                    planner.expand(workout, exercises, settings.difficulty, settings.ownedEquipment, settings.countdownSec)
                (planner.estimateSeconds(steps) + 59) / 60
            } ?: program.profile.sessionMinutes
        }
        val rows = program.days.map { day ->
            PlanDayRow(
                dayIndex = day.dayIndex,
                workoutId = day.workoutId,
                workoutName = day.workoutId?.let { byId[it]?.name },
                minutes = day.workoutId?.let { minutesById[it] } ?: 0,
                isNext = day.dayIndex == progress.next?.dayIndex,
                done = day.dayIndex in progress.doneDayIndexes,
                doneAt = doneAt[day.dayIndex],
            )
        }
        val activeInProgress = active?.takeIf { it.id != program.id }?.let { other ->
            val otherProgress = PlanSessions.progress(sessions, other)
            other.name.takeIf { !otherProgress.isComplete && otherProgress.next != null }
        }
        val inProgress = sessions.firstOrNull { it.status == SessionStatus.IN_PROGRESS }
        PlanUiState(
            loading = false,
            inProgressWorkoutId = inProgress?.workoutId,
            inProgressName = inProgress?.workoutName,
            byId = programId != null,
            program = program,
            days = rows,
            doneCount = progress.doneCount,
            totalCount = progress.totalCount,
            isComplete = progress.isComplete,
            deleted = isDeleted,
            isPreset = program.isPreset,
            subtitle = subtitleFor(program),
            isActive = program.active,
            pausing = activeInProgress,
            locked = inProgress != null && inProgress.let { PlanSessions.belongsTo(it, program) },
            busy = busyState == BUSY,
            error = busyState?.takeIf { it.isNotEmpty() && it != BUSY },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    /** Ten plan staje się aktywny; bieżący czeka na liście ze swoim postępem (D-42). */
    fun activate() {
        val id = uiState.value.program?.id ?: return
        launchOp {
            programRepo.activate(id)
            // Trener (D-35): wykluczone ćwiczenia wypadają też z planu, który właśnie wrócił.
            runCatching { coachRepo.replaceExcludedInActiveProgram() }.onFailure { Log.e(TAG, "coach replace failed", it) }
        }
    }

    fun duplicate(thenEdit: Boolean = false) {
        val id = uiState.value.program?.id ?: return
        launchOp { programRepo.duplicate(id)?.let { copy -> if (thenEdit) _editCopy.send(copy) else _opened.send(copy) } }
    }

    /** Ten sam plan od zera, jako nowy przebieg z własnymi statystykami. */
    fun repeat() {
        val id = uiState.value.program?.id ?: return
        launchOp { programRepo.repeat(id)?.let { _opened.send(it) } }
    }

    fun rename(name: String, description: String) {
        val id = uiState.value.program?.id ?: return
        if (name.isBlank()) return
        launchOp { programRepo.rename(id, name, description) }
    }

    fun delete() {
        val id = uiState.value.program?.id ?: return
        if (uiState.value.locked) return
        launchOp {
            programRepo.delete(id)
            deleted.value = true
        }
    }

    fun dismissError() {
        busy.value = null
    }

    private fun launchOp(block: suspend () -> Unit) {
        if (busy.value == BUSY) return
        busy.value = BUSY
        viewModelScope.launch {
            try {
                block()
                busy.value = null
            } catch (t: Throwable) {
                Log.e(TAG, "Operacja na planie nie wyszła", t)
                busy.value = t.message ?: "Nie udało się."
            }
        }
    }

    /**
     * Kreator: cel, rytm i czas z profilu. Gotowy program (D-37): tagline i rytm. Plan własny: opis autora.
     * Opis wpisany w „Zmień nazwę” widać w każdym planie, także z kreatora.
     */
    private fun subtitleFor(program: Program): String {
        val profile = program.profile
        val preset = program.presetId?.let { PresetCatalog.get(it) }
        val description = program.description.takeIf { it.isNotBlank() }
        return when {
            preset != null -> listOfNotNull(
                preset.tagline,
                "Cykl ${program.cycleDays} dni · ${preset.pattern.labelPl} · poziom ${profile.level.level}",
                description,
            ).joinToString("\n")
            program.source == PlanSource.WIZARD -> listOfNotNull(
                "Cykl ${program.cycleDays} dni · ${profile.goal.labelPl.lowercase()} · " +
                    "${profile.daysPerWeek} razy w tygodniu · ${profile.sessionMinutes} min",
                description,
            ).joinToString("\n")
            else -> listOfNotNull(
                program.author.takeIf { it.isNotBlank() }?.let { "Autor: $it" },
                program.description.takeIf { it.isNotBlank() },
                "${program.days.size} dni · ${program.source.labelPl.lowercase()}",
            ).joinToString("\n")
        }
    }

    private companion object {
        const val TAG = "PlanViewModel"
        const val BUSY = ""
    }
}
