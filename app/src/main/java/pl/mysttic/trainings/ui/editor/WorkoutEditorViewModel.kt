package pl.mysttic.trainings.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.repo.CoachRepository
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.data.settings.UserSettings
import pl.mysttic.trainings.domain.coach.ExerciseTuning
import pl.mysttic.trainings.domain.editor.WorkoutEditing
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.ui.navigation.WorkoutEditorRoute
import java.util.UUID
import javax.inject.Inject

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val note: String = "",
    val blocks: List<Block> = emptyList(),
    val exercises: Map<String, Exercise> = emptyMap(),
    val allExercises: List<Exercise> = emptyList(),
    val ownedEquipment: Set<Equipment> = emptySet(),
    /** Komunikaty walidacji pokazywane po nieudanej próbie zapisu. */
    val errors: List<String> = emptyList(),
    val saved: Boolean = false,
    val notFound: Boolean = false,
    /** Trening wbudowany, z gotowego programu, od kogoś albo trwający — do zmiany tylko przez kopię (D-19, D-42). */
    val readOnly: String? = null,
) {
    val workCount: Int get() = WorkoutEditing.workCount(blocks)
}

/**
 * Edytor treningu (M4). Stan trzymany w jednym [MutableStateFlow]; wszystkie zmiany przechodzą przez
 * czyste funkcje [WorkoutEditing], więc kolejność, walidacja i zakresy są testowane bez Androida.
 * Wbudowanych treningów nie edytujemy: do planu wchodzą jako kopia ([ProgramRepository.copyWorkoutInto], D-19, D-42).
 */
@HiltViewModel
class WorkoutEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val settingsRepo: SettingsRepository,
    private val programRepo: ProgramRepository,
    private val sessionRepo: SessionRepository,
    private val coachRepo: CoachRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<WorkoutEditorRoute>()
    private var existing: Workout? = null
    private val planner = WorkoutPlanner()

    /** Ustawienia i pamięć trenera z chwili wejścia do edytora: tylko do szacowania czasu. */
    private var settings: UserSettings? = null
    private var tuning: Map<String, ExerciseTuning> = emptyMap()

    private val _ui = MutableStateFlow(EditorUiState())
    val ui: StateFlow<EditorUiState> = _ui.asStateFlow()

    /**
     * Szacowany czas treningu w minutach, liczony tak samo jak w szczegółach treningu i w playerze: przez
     * [WorkoutPlanner] z poziomem, sprzętem, odliczaniem i trenerem. Liczy się od nowa tylko przy zmianie bloków.
     */
    val estimatedMinutes: StateFlow<Int> = _ui
        .map { it.blocks to it.exercises }
        .distinctUntilChanged()
        .map { (blocks, exercises) -> estimate(blocks, exercises) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val all = exerciseRepo.getAll()
        val current = settingsRepo.current()
        settings = current
        tuning = runCatching { coachRepo.all() }.getOrElse { emptyMap() }
        val owned = current.ownedEquipment
        val workout = route.workoutId?.let { workoutRepo.getById(it) }
        existing = workout
        val readOnly = workout?.let { readOnlyReason(it) }
        _ui.value = EditorUiState(
            loading = false,
            isNew = workout == null,
            name = workout?.name.orEmpty(),
            note = workout?.note.orEmpty(),
            blocks = workout?.blocks.orEmpty(),
            exercises = all.associateBy { it.id },
            // Oddech (D-37) żyje tylko w gotowych programach: rytm i krok bez limitu niesie definicja programu,
            // a pozycja dopisana ręcznie wyszłaby zwykłym timerem. Do wyboru w edytorze nie wchodzi, tak jak
            // ćwiczenie wycofane z bazy (D-47); obecne w treningu dalej widać przez `exercises`.
            allExercises = all.filter { it.category != ExerciseCategory.BREATHING && !it.isRetired },
            ownedEquipment = owned,
            // Nowy trening planu przychodzi z id nadanym w edytorze planu — w bazie go jeszcze nie ma.
            notFound = route.workoutId != null && workout == null && route.programId == null,
            readOnly = readOnly,
        )
    }

    private suspend fun readOnlyReason(workout: Workout): String? {
        val owner = programRepo.ownerOf(workout.id)?.let { programRepo.byId(it) }
        val inProgress = sessionRepo.inProgress()?.workoutId == workout.id
        return when {
            workout.isBuiltIn -> "Wbudowany trening zmienisz przez kopię."
            inProgress -> "Ten trening właśnie trwa. Zmienisz go po zakończeniu."
            !WorkoutEditing.canEdit(workout, owner?.editableInPlace, inProgress) ->
                "Ten plan zmienisz przez kopię: „Duplikuj i zmień” w menu planu."
            else -> null
        }
    }

    /**
     * Czy stan ekranu odszedł od wczytanego treningu. Ekran pyta o to przy wyjściu: zapis leci tylko
     * z „Zapisz trening”, więc bez pytania strzałka wstecz kasowała całą pracę bez śladu.
     */
    fun hasChanges(): Boolean {
        val state = _ui.value
        if (state.loading || state.saved) return false
        val base = existing
        return state.name != base?.name.orEmpty() ||
            state.note != base?.note.orEmpty() ||
            state.blocks != base?.blocks.orEmpty()
    }

    fun setName(value: String) = _ui.update { it.copy(name = value, errors = emptyList()) }

    fun setNote(value: String) = _ui.update { it.copy(note = value) }

    fun addBlock(kind: BlockKind) = edit { WorkoutEditing.addBlock(it, kind) }

    fun removeBlock(index: Int) = edit { WorkoutEditing.removeBlock(it, index) }

    fun moveBlock(from: Int, to: Int) = edit { WorkoutEditing.moveBlock(it, from, to) }

    fun changeBlockKind(index: Int, kind: BlockKind) = edit { WorkoutEditing.changeBlockKind(it, index, kind) }

    fun setRounds(index: Int, rounds: Int) = edit { WorkoutEditing.setRounds(it, index, rounds) }

    fun setRestBetweenRounds(index: Int, seconds: Int) = edit { WorkoutEditing.setRestBetweenRounds(it, index, seconds) }

    fun addExercise(blockIndex: Int, exerciseId: String) {
        val exercise = _ui.value.exercises[exerciseId] ?: return
        val kind = _ui.value.blocks.getOrNull(blockIndex)?.kind ?: return
        edit { WorkoutEditing.addItem(it, blockIndex, WorkoutEditing.newItem(exercise, kind)) }
    }

    fun updateItem(blockIndex: Int, itemIndex: Int, item: BlockItem) =
        edit { WorkoutEditing.updateItem(it, blockIndex, itemIndex, item) }

    fun removeItem(blockIndex: Int, itemIndex: Int) = edit { WorkoutEditing.removeItem(it, blockIndex, itemIndex) }

    fun moveItem(blockIndex: Int, from: Int, to: Int) = edit { WorkoutEditing.moveItem(it, blockIndex, from, to) }

    fun duplicateItem(blockIndex: Int, itemIndex: Int) = edit { WorkoutEditing.duplicateItem(it, blockIndex, itemIndex) }

    fun duplicateBlock(index: Int) = edit { WorkoutEditing.duplicateBlock(it, index) }

    fun setBlockLabel(index: Int, label: String) = edit { WorkoutEditing.setBlockLabel(it, index, label) }

    fun setBlockRestAfter(index: Int, seconds: Int?) = edit { WorkoutEditing.setBlockRestAfter(it, index, seconds) }

    fun replaceExercise(blockIndex: Int, itemIndex: Int, exerciseId: String) {
        val exercise = _ui.value.exercises[exerciseId] ?: return
        edit { WorkoutEditing.replaceExercise(it, blockIndex, itemIndex, exercise) }
    }

    fun save() {
        val state = _ui.value
        if (state.readOnly != null) return
        val errors = WorkoutEditing.validate(state.name, state.blocks)
        if (errors.isNotEmpty()) {
            _ui.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            val workout = WorkoutEditing.toWorkout(
                existing = existing,
                id = route.workoutId ?: UUID.randomUUID().toString(),
                name = state.name,
                note = state.note,
                blocks = blocksToSave(state.blocks),
                now = System.currentTimeMillis(),
            )
            workoutRepo.save(workout)
            _ui.update { it.copy(saved = true) }
        }
    }

    /**
     * Bloki w postaci, w jakiej pójdą do bazy. Trening planu (D-42): id z prefiksem planu przychodzi z edytora planu;
     * plan dosłowny = bloki `fixed`. Istniejący trening dziedziczy `fixed` z siebie, żeby nowy blok w dosłownym planie
     * nie zaczął się skalować.
     */
    private fun blocksToSave(blocks: List<Block>): List<Block> {
        val literal = if (existing != null) existing?.isFixed == true else route.fixed
        return if (route.programId != null || existing?.isFixed == true) WorkoutEditing.setFixed(blocks, literal) else blocks
    }

    private fun estimate(blocks: List<Block>, exercises: Map<String, Exercise>): Int {
        val current = settings ?: return 0
        if (blocks.isEmpty()) return 0
        val workout = WorkoutEditing.toWorkout(existing, route.workoutId.orEmpty(), "", "", blocksToSave(blocks), now = 0L)
        val steps = planner.expand(workout, exercises, current.difficulty, current.ownedEquipment, current.countdownSec, tuning)
        return (planner.estimateSeconds(steps) + 59) / 60
    }

    private fun edit(transform: (List<Block>) -> List<Block>) =
        _ui.update { it.copy(blocks = transform(it.blocks), errors = emptyList()) }
}
