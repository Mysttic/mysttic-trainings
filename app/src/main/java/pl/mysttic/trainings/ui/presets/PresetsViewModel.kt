package pl.mysttic.trainings.ui.presets

import android.util.Log
import pl.mysttic.trainings.domain.plan.PlanSessions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.plan.preset.PresetCatalog
import pl.mysttic.trainings.domain.plan.preset.PresetId
import pl.mysttic.trainings.domain.plan.preset.PresetIntensity
import pl.mysttic.trainings.domain.plan.preset.PresetParam
import pl.mysttic.trainings.domain.plan.preset.ProgramKind
import javax.inject.Inject

/** Karta programu na liście: definicja przełożona na to, co widać, plus zgodność z biblioteką i sprzętem. */
data class PresetRow(
    val id: PresetId,
    val name: String,
    val tagline: String,
    val description: String,
    val warning: String?,
    val intensity: PresetIntensity,
    val kind: ProgramKind,
    val lengthOptions: List<Int>,
    val defaultLength: Int,
    val asks: PresetParam?,
    val patternLabel: String,
    /** Ćwiczenia programu, których nie ma w bibliotece; niepuste = programu nie da się wybrać. */
    val missingExercises: Set<String>,
    val equipment: Set<Equipment>,
    /** Sprzęt programu, którego użytkownik nie ma, a który zastąpią ćwiczenia bez sprzętu (D-46). */
    val missingEquipment: Set<Equipment>,
    /** Sprzęt bez zamiennika, którego użytkownik nie ma; niepusty = programu nie da się wybrać (D-46). */
    val unmetEquipment: Set<Equipment> = emptySet(),
) {
    val available: Boolean get() = missingExercises.isEmpty() && unmetEquipment.isEmpty()
}

/** Co wychodzi z wybranych ustawień: liczba sesji i czas pierwszego oraz ostatniego etapu. */
data class PresetPreview(val sessions: Int, val firstMinutes: Int, val lastMinutes: Int)

data class PresetsUiState(
    val loading: Boolean = true,
    val rows: List<PresetRow> = emptyList(),
    val selected: PresetId? = null,
    val level: Difficulty = Difficulty.INTERMEDIATE,
    val cycleDays: Int = 0,
    val param: Int? = null,
    val preview: PresetPreview? = null,
    /**
     * Nazwa aktywnego planu z niedomkniętą kolejką (D-42): nowy program go nie kasuje, tylko wstrzymuje,
     * a wrócić do niego można z listy planów. null = nic nie zostanie wstrzymane.
     */
    val pausing: String? = null,
    val starting: Boolean = false,
    val started: Boolean = false,
    val error: String? = null,
) {
    val selectedRow: PresetRow? get() = rows.firstOrNull { it.id == selected }
}

/**
 * Wybór gotowego programu (D-37). Program to deterministyczna funkcja (poziom, długość, parametr), więc podgląd
 * liczy się z tych samych tabel, z których powstanie cykl. Start zapisuje cykl przez [ProgramRepository.startPreset]
 * i przenosi wybrany poziom do ustawień, jak kreator.
 */
@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val programRepo: ProgramRepository,
    private val exerciseRepo: ExerciseRepository,
    private val settingsRepo: SettingsRepository,
    private val sessionRepo: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(PresetsUiState())
    val ui: StateFlow<PresetsUiState> = _ui.asStateFlow()

    /** Biblioteka z chwili wejścia: podgląd minut liczy się z niej synchronicznie przy każdej zmianie wyboru. */
    private var exercises: Map<String, Exercise> = emptyMap()
    private var owned: Set<Equipment> = emptySet()
    private var countdownSec: Int = WorkoutPlanner.DEFAULT_COUNTDOWN_SEC

    init {
        viewModelScope.launch {
            val settings = settingsRepo.current()
            exercises = exerciseRepo.getMap()
            owned = settings.ownedEquipment
            countdownSec = settings.countdownSec
            val rows = PresetCatalog.all.map { definition ->
                val equipment = PresetCatalog.equipmentFor(definition, exercises)
                PresetRow(
                    id = definition.id,
                    name = definition.name,
                    tagline = definition.tagline,
                    description = definition.description,
                    warning = definition.warning,
                    intensity = definition.intensity,
                    kind = definition.kind,
                    lengthOptions = definition.lengthOptions,
                    defaultLength = definition.defaultLength,
                    asks = definition.asks,
                    patternLabel = definition.pattern.labelPl,
                    missingExercises = PresetCatalog.missingExercises(definition, exercises),
                    equipment = equipment,
                    missingEquipment = equipment - owned,
                    unmetEquipment = PresetCatalog.unmetEquipment(definition, exercises, owned),
                )
            }
            _ui.value = PresetsUiState(loading = false, rows = rows, level = settings.difficulty, pausing = pausingName())
        }
    }

    /** Wybór programu ustawia jego domyślną długość i parametr; program bez ćwiczeń w bibliotece nie daje się wybrać. */
    fun select(id: PresetId) {
        val row = _ui.value.rows.firstOrNull { it.id == id }?.takeIf { it.available } ?: return
        _ui.update { state ->
            withPreview(state.copy(selected = id, cycleDays = row.defaultLength, param = row.asks?.default, error = null))
        }
    }

    fun setLevel(level: Difficulty) = _ui.update { withPreview(it.copy(level = level, error = null)) }

    fun setCycleDays(days: Int) = _ui.update { state ->
        if (state.selectedRow?.lengthOptions?.contains(days) != true) state else withPreview(state.copy(cycleDays = days, error = null))
    }

    fun setParam(value: Int) = _ui.update { state ->
        val asks = state.selectedRow?.asks ?: return@update state
        withPreview(state.copy(param = value.coerceIn(asks.range), error = null))
    }

    /** Zapisuje cykl z programu; bieżący plan zostaje wstrzymany ze swoim postępem (D-42). */
    fun start() {
        val state = _ui.value
        val row = state.selectedRow ?: return
        if (state.starting || state.started) return
        _ui.update { it.copy(starting = true, error = null) }
        viewModelScope.launch {
            try {
                // Te same biblioteka i sprzęt co w podglądzie: cykl ma wyjść taki, jaki użytkownik widział.
                programRepo.startPreset(
                    id = row.id,
                    level = state.level,
                    cycleDays = state.cycleDays,
                    param = state.param,
                    exercises = exercises,
                    equipment = owned,
                )
                settingsRepo.setDifficulty(state.level)
                _ui.update { it.copy(starting = false, started = true) }
            } catch (t: Throwable) {
                Log.e(TAG, "Nie udało się uruchomić programu", t)
                _ui.update { it.copy(starting = false, error = t.message ?: "Nie udało się uruchomić programu") }
            }
        }
    }

    /** Podgląd z tych samych tabel co cykl: identyfikator tymczasowy, bo nic nie zapisujemy. */
    private fun withPreview(state: PresetsUiState): PresetsUiState {
        val id = state.selected ?: return state.copy(preview = null)
        val plan = PresetCatalog.build(id, state.level, state.cycleDays, state.param, PREVIEW_ID, now = 0L, exercises, owned)
        val minutes = plan.workouts.map { PresetCatalog.minutes(it.blocks, exercises, countdownSec) }
        return state.copy(
            preview = PresetPreview(
                sessions = plan.sessionCount,
                firstMinutes = minutes.firstOrNull() ?: 0,
                lastMinutes = minutes.lastOrNull() ?: 0,
            ),
        )
    }

    /** Aktywny plan w trakcie kolejki (D-21) — ten zostanie wstrzymany, gdy wystartuje nowy program. */
    private suspend fun pausingName(): String? {
        val program = programRepo.active() ?: return null
        val progress = PlanSessions.progress(sessionRepo.allSessions(), program)
        return program.name.takeIf { !progress.isComplete && progress.next != null }
    }

    companion object {
        private const val TAG = "PresetsViewModel"
        private const val PREVIEW_ID = "podglad"
    }
}
