package pl.mysttic.trainings.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.repo.CoachRepository
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.coach.Coach
import pl.mysttic.trainings.domain.coach.ExerciseTuning
import pl.mysttic.trainings.domain.editor.WorkoutEditing
import pl.mysttic.trainings.domain.engine.Announcer
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.equipment.EquipmentMatcher
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.SetProgression
import pl.mysttic.trainings.domain.plan.TargetSuggestion
import pl.mysttic.trainings.ui.navigation.WorkoutDetailRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class ItemView(
    val exerciseId: String,
    val name: String,
    /** Nazwa bazowa, jeśli poziom podmienił wariant. */
    val substitutedFrom: String?,
    val targetText: String,
    val restText: String,
    val missingEquipment: Set<Equipment>,
    val substitutes: List<Exercise>,
    val imagePath: String?,
    /** Notatka do tego ćwiczenia w planie, do którego należy trening (D-44); pusta, gdy jej nie ma. */
    val planNote: String = "",
    /** Ciężar z szablonu (D-42); null = bez ciężaru. */
    val weightKg: Double? = null,
    /** Wskazówka autora planu (D-42), ta sama co w playerze pod nazwą ćwiczenia. */
    val hint: String = "",
)

/** `param` ląduje po prawej stronie nagłówka bloku: rundy i przerwa. */
data class BlockView(val title: String, val param: String, val items: List<ItemView>)

data class WorkoutDetailUiState(
    val loading: Boolean = true,
    val workout: Workout? = null,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val ownedEquipment: Set<Equipment> = emptySet(),
    val blocks: List<BlockView> = emptyList(),
    val estimatedMinutes: Int = 0,
    val workSteps: Int = 0,
    /** Propozycje podbicia celów po ostatnim wykonaniu (M5); puste dla treningów wbudowanych i stałego protokołu. */
    val suggestions: List<TargetSuggestion> = emptyList(),
    /** Stały protokół gotowego programu (D-37): bez selektora poziomu, edycji i kasowania. */
    val fixed: Boolean = false,
    /** Trening jest dniem w kolejce cyklu (D-20/D-37) — kasowanie zostawiłoby plan z widmowym dniem. */
    val inPlan: Boolean = false,
    /** Plan-właściciel treningu (D-42); tylko wtedy da się pisać notatki do ćwiczeń (D-44). */
    val programId: String? = null,
    /**
     * Skąd jest plan-właściciel (D-42); null dla treningu spoza planów. Odróżnia gotowy program od planu dosłownego:
     * oba mają bloki `fixed`, ale tylko program jest stałym protokołem bez edycji.
     */
    val ownerSource: PlanSource? = null,
    /** Nazwa planu-właściciela, który czeka (nie jest aktywny). Start zostaje wtedy przy planie, jak w jego widoku. */
    val waitingPlanName: String? = null,
    /** Trening wolno zmienić w miejscu: własny plan albo plan z kreatora, nic z niego nie trwa (D-19, D-42). */
    val canEdit: Boolean = false,
    /** Sesja w toku innego treningu: Start by ją po cichu przerwał, więc ekran najpierw pyta. */
    val otherInProgress: Session? = null,
    /** Ten trening właśnie trwa: Start wraca do playera. */
    val resumes: Boolean = false,
)

/** Plan-właściciel treningu z notatkami do ćwiczeń; trening spoza planów nie ma ani planu, ani notatek. */
private data class Owner(val programId: String?, val program: Program?, val notes: Map<String, String>)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepo: WorkoutRepository,
    exerciseRepo: ExerciseRepository,
    private val settingsRepo: SettingsRepository,
    private val sessionRepo: SessionRepository,
    coachRepo: CoachRepository,
    private val programRepo: ProgramRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<WorkoutDetailRoute>()
    private val planner = WorkoutPlanner()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** Plan-właściciel, jego stan (aktywny, źródło) i notatki; trening spoza planów notatek nie ma. */
    private val owner = flow { emit(programRepo.ownerOf(route.workoutId)) }.flatMapLatest { programId ->
        if (programId == null) {
            flowOf(Owner(null, null, emptyMap()))
        } else {
            combine(programRepo.observeById(programId), programRepo.observeNotes(programId)) { program, notes ->
                Owner(programId, program, notes)
            }
        }
    }

    /** Sesja w toku (najwyżej jedna): Start innego treningu zamknąłby ją jako przerwaną (D-16). */
    private val inProgress = sessionRepo.observeSessions()
        .map { sessions -> sessions.firstOrNull { it.status == SessionStatus.IN_PROGRESS } }
        .distinctUntilChanged()

    private val baseState = combine(
        workoutRepo.observeById(route.workoutId),
        exerciseRepo.observeAll(),
        settingsRepo.settings,
        coachRepo.observeAll(),
        programRepo.observeInProgram(route.workoutId),
    ) { workout, exercises, settings, tuning, inPlan ->
        if (workout == null) return@combine WorkoutDetailUiState(loading = false)
        val map = exercises.associateBy { it.id }
        val difficulty = settings.difficulty
        val suggestions = suggestionsFor(workout, map, difficulty, settings.ownedEquipment, tuning)
        val steps = planner.expand(workout, map, difficulty, settings.ownedEquipment, settings.countdownSec, tuning)
        WorkoutDetailUiState(
            loading = false,
            workout = workout,
            difficulty = difficulty,
            ownedEquipment = settings.ownedEquipment,
            estimatedMinutes = (planner.estimateSeconds(steps) + 59) / 60,
            workSteps = steps.count { it is Step.Work },
            suggestions = suggestions,
            fixed = workout.isFixed,
            inPlan = inPlan,
            blocks = workout.blocks.mapIndexed { index, block ->
                // Nazwa bloku od autora (D-42), jak w playerze; bez niej numer.
                val name = block.label.ifBlank { "Blok ${index + 1}" }
                val title = when (block.kind) {
                    BlockKind.SETS -> "$name · serie"
                    BlockKind.CIRCUIT -> "$name · obwód"
                }
                val param = when (block.kind) {
                    BlockKind.SETS -> ""
                    BlockKind.CIRCUIT ->
                        "${block.rounds} ${roundsWord(block.rounds)} · przerwa ${planner.restSec(block.restBetweenRoundsSec, block, difficulty)} s"
                }
                BlockView(
                    title = title,
                    param = param,
                    items = block.items.map { item ->
                        val base = map[item.exerciseId]
                        // Te same funkcje co w playerze (D-37): wariant, skalowanie i korekta trenera, albo nic dla stałego protokołu.
                        val resolved = planner.shownExercise(item, block, difficulty, map, settings.ownedEquipment)
                        val tuned = if (block.fixed) null else tuning[resolved?.id ?: item.exerciseId]
                        val value = planner.shownTarget(item, block, difficulty, tuning, map, settings.ownedEquipment)
                        val target = when (item.targetKind) {
                            TargetKind.REPS -> Announcer.reps(value)
                            TargetKind.SECONDS -> when {
                                item.openEnded -> "jak najdłużej (ok. ${Announcer.seconds(value)})"
                                item.cadenceSec != null && item.cadenceSec > 0 -> "${Announcer.breaths(value / item.cadenceSec)} w rytmie, ${Announcer.seconds(value)}"
                                else -> Announcer.seconds(value)
                            }
                            TargetKind.AMRAP -> "ile dasz radę w ${Announcer.seconds(value)}"
                        }
                        val targetText = if (block.kind == BlockKind.SETS) "${item.sets} × $target" else target
                        val rest = if (block.kind == BlockKind.SETS) {
                            "przerwa ${planner.restSec(item.restAfterSetSec, block, difficulty)} s"
                        } else {
                            "przerwa ${planner.restSec(item.restAfterItemSec, block, difficulty)} s"
                        }
                        val restText = Coach.label(tuned, item.targetKind)?.let { "$rest · $it" } ?: rest
                        val missing = resolved?.missingEquipment(settings.ownedEquipment).orEmpty()
                        ItemView(
                            exerciseId = resolved?.id ?: item.exerciseId,
                            name = resolved?.namePl ?: item.exerciseId,
                            substitutedFrom = if (resolved != null && base != null && resolved.id != base.id) base.namePl else null,
                            targetText = targetText,
                            restText = restText,
                            missingEquipment = missing,
                            // Blok stały player gra dosłownie, więc strzałka do zamiennika obiecywałaby podmianę, której nie będzie.
                            substitutes = if (resolved != null && missing.isNotEmpty() && !block.fixed) {
                                EquipmentMatcher.substitutes(resolved, exercises, settings.ownedEquipment)
                            } else {
                                emptyList()
                            },
                            imagePath = resolved?.images?.lastOrNull(),
                            weightKg = item.weightKg,
                            hint = item.note,
                        )
                    },
                )
            },
        )
    }

    val uiState: StateFlow<WorkoutDetailUiState> = combine(baseState, owner, inProgress) { state, owner, running ->
        val workout = state.workout
        val program = owner.program
        val resumes = workout != null && running?.workoutId == workout.id
        state.copy(
            programId = owner.programId,
            ownerSource = program?.source,
            // Sesja liczy się do planu-właściciela (D-42), więc start treningu z planu, który czeka, odhaczyłby
            // dzień w tamtym planie, a „Dziś” prowadzi inny. Wyjątek: ten trening już trwa, wtedy wracamy do niego.
            waitingPlanName = program?.takeIf { !it.active && !resumes }?.name,
            canEdit = workout != null && WorkoutEditing.canEdit(workout, program?.editableInPlace, resumes),
            otherInProgress = running?.takeIf { !resumes },
            resumes = resumes,
            blocks = state.blocks.map { block ->
                block.copy(items = block.items.map { it.copy(planNote = owner.notes[it.exerciseId].orEmpty()) })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUiState())

    /** Notatka do ćwiczenia w planie tego treningu (D-44); pusta kasuje. */
    fun setPlanNote(exerciseId: String, note: String) {
        val programId = uiState.value.programId ?: return
        viewModelScope.launch { programRepo.setNote(programId, exerciseId, note) }
    }

    fun setDifficulty(difficulty: Difficulty) {
        viewModelScope.launch { settingsRepo.setDifficulty(difficulty) }
    }

    /**
     * Propozycje progresji z ostatniego ukończonego wykonania, liczone od celu pokazanego (poziom, sprzęt, trener,
     * D-43). Stały protokół gotowego programu (D-37) ma własną progresję etapami, a wbudowanych nie ruszamy (D-19).
     */
    private suspend fun suggestionsFor(
        workout: Workout,
        exercises: Map<String, Exercise>,
        difficulty: Difficulty,
        owned: Set<Equipment>,
        tuning: Map<String, ExerciseTuning>,
    ): List<TargetSuggestion> {
        if (workout.isBuiltIn || workout.isFixed) return emptyList()
        val previous = sessionRepo.previousDone(workout.id, System.currentTimeMillis()) ?: return emptyList()
        return SetProgression.suggest(
            workout.blocks, sessionRepo.setLogs(previous.id), exercises, difficulty, owned, tuning, planner,
        )
    }

    /** Jeden tap: podbite cele lądują w szablonie treningu. */
    fun applySuggestions() {
        val state = uiState.value
        val workout = state.workout ?: return
        if (state.suggestions.isEmpty()) return
        viewModelScope.launch {
            workoutRepo.save(
                workout.copy(
                    blocks = SetProgression.apply(workout.blocks, state.suggestions),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Wbudowanych (resztki sprzed seedu v7 z historią), etapów gotowego programu i treningów z kolejki cyklu
     * nie kasujemy: dzień planu wskazywałby na nieistniejący trening, a kolejka stanęłaby na nim na zawsze
     * (jedynym wyjściem byłoby „Usuń plan”).
     */
    fun delete() {
        val workout = uiState.value.workout ?: return
        if (workout.isBuiltIn || workout.isFixed || uiState.value.inPlan) return
        viewModelScope.launch {
            workoutRepo.delete(workout)
            _deleted.value = true
        }
    }

    private fun roundsWord(n: Int) = Announcer.plural(n, "runda", "rundy", "rund")
}
