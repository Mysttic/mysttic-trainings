package pl.mysttic.trainings.ui.plan

import android.util.Log
import pl.mysttic.trainings.domain.plan.PlanSessions
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.mysttic.trainings.ui.navigation.PlanWizardRoute
import pl.mysttic.trainings.data.repo.CoachRepository
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.editor.WorkoutEditing
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.plan.CycleReview
import pl.mysttic.trainings.domain.plan.Goal
import pl.mysttic.trainings.domain.plan.PlanGenerator
import pl.mysttic.trainings.domain.plan.PlanProgression
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.ProgressionStep
import pl.mysttic.trainings.domain.plan.TrainingProfile
import javax.inject.Inject

data class WizardUiState(
    val loading: Boolean = true,
    val profile: TrainingProfile = TrainingProfile(),
    /** Kolejny cykl: formularz startuje z poprzednich wartości, użytkownik je koryguje. */
    val isNextCycle: Boolean = false,
    val generating: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    /** Rozliczenie poprzedniego cyklu (D-22); null przy pierwszym planie. */
    val review: CycleReview? = null,
    val step: ProgressionStep = ProgressionStep.HOLD,
    /** Mnożnik celów poprzedniego cyklu — punkt wyjścia dla [nextScale]. */
    val previousScale: Double = 1.0,
    /** Aktywny plan w trakcie kolejki, który nowy cykl wstrzyma (D-42); null = nic nie zostanie wstrzymane. */
    val pausing: String? = null,
    /** Z ilu ćwiczeń kreator ułoży plan przy zaznaczonym sprzęcie i bez sprzętu (D-57); null, dopóki baza się czyta. */
    val candidates: Int? = null,
    val bodyweightCandidates: Int? = null,
) {
    val sessionsInCycle: Int get() = PlanGenerator.sessionsInCycle(profile)
    val weeklyMinutes: Int get() = PlanGenerator.weeklyMinutes(profile)
    val nextScale: Double get() = PlanProgression.nextScale(previousScale, step)
    val scaleLabel: String get() = PlanProgression.scaleLabel(nextScale)
    val explanation: String? get() = review?.let { PlanProgression.explain(it, PlanProgression.suggest(it)) }
}

/**
 * Kreator cyklu (D-20). Formularz startuje z ustawień (sprzęt, poziom) albo z profilu poprzedniego cyklu,
 * żeby „wygeneruj kolejne 30 dni” było poprawieniem dwóch liczb, a nie wypełnianiem wszystkiego od nowa.
 */
@HiltViewModel
class PlanWizardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programRepo: ProgramRepository,
    private val exerciseRepo: ExerciseRepository,
    private val settingsRepo: SettingsRepository,
    private val sessionRepo: SessionRepository,
    private val workoutRepo: WorkoutRepository,
    private val coachRepo: CoachRepository,
) : ViewModel() {

    /** Ocena wybrana na „Dziś” po zamknięciu cyklu (D-32); bez niej kreator proponuje sam. */
    private val fromToday = runCatching {
        savedStateHandle.toRoute<PlanWizardRoute>().step?.let { ProgressionStep.valueOf(it) }
    }.getOrNull()

    private val _ui = MutableStateFlow(WizardUiState())
    val ui: StateFlow<WizardUiState> = _ui.asStateFlow()

    /** Biblioteka do licznika ćwiczeń w kroku sprzętu (D-57); generowanie i tak czyta ją od nowa. */
    private var library: List<Exercise> = emptyList()

    init {
        viewModelScope.launch {
            val settings = settingsRepo.current()
            library = exerciseRepo.getAll()
            // Po gotowym programie (D-37) kreator startuje z ostatniego profilu kreatora, żeby nie zgubić mnożnika
            // celów (D-22); preset nie ma frekwencji do rozliczenia, więc bez oceny cyklu.
            val active = programRepo.active()
            // Plany własne i od kogoś (D-42) nie mają sylwetki ani frekwencji kreatora — liczy się ostatni z kreatora.
            val program = active?.takeIf { it.isWizard } ?: programRepo.lastWizardProgram()
            val previous = program?.profile?.copy(presetId = null, presetParam = null)
            val activeProgress = active?.let { PlanSessions.progress(sessionRepo.allSessions(), it) }
            val inProgress = activeProgress != null && !activeProgress.isComplete && activeProgress.next != null
            // Ocena tylko domkniętego cyklu (D-22): kreator otwarty z listy planów w trakcie cyklu nie czyta
            // niedokończonej kolejki jako „za ciężko” i nie obniża celów; zostaje przy poprzednim mnożniku.
            val review = active?.takeIf { it.isWizard && !inProgress }?.let { reviewOf(it) }
            _ui.value = WizardUiState(
                loading = false,
                isNextCycle = previous != null,
                // Sprzęt to „Mój sprzęt”, a wzrost i waga to „O mnie” (D-57): kreator startuje od tego, co użytkownik
                // o sobie zapisał, i zapisuje tam z powrotem to, co w nim poprawi.
                profile = (previous ?: TrainingProfile()).let { base ->
                    base.copy(
                        level = previous?.level ?: settings.difficulty,
                        equipment = settings.ownedEquipment,
                        heightCm = settings.heightCm ?: base.heightCm,
                        weightKg = settings.weightKg ?: base.weightKg,
                    )
                },
                review = review,
                step = fromToday ?: review?.let { PlanProgression.suggest(it) } ?: ProgressionStep.HOLD,
                previousScale = previous?.targetScale ?: 1.0,
                pausing = active?.name?.takeIf { inProgress },
            ).withCounts()
        }
    }

    fun setHeight(cm: Int) = update { it.copy(heightCm = cm) }
    fun setWeight(kg: Double) = update { it.copy(weightKg = kg) }
    fun setGoal(goal: Goal) = update { it.copy(goal = goal) }
    fun setLevel(level: Difficulty) = update { it.copy(level = level) }
    fun setDaysPerWeek(days: Int) = update { it.copy(daysPerWeek = days) }
    fun setSessionMinutes(minutes: Int) = update { it.copy(sessionMinutes = minutes) }
    fun setCycleDays(days: Int) = update { it.copy(cycleDays = days) }

    /** Ręczne nadpisanie propozycji progresji. */
    fun setStep(step: ProgressionStep) = _ui.update { it.copy(step = step, error = null) }

    fun toggleEquipment(equipment: Equipment) = update {
        it.copy(equipment = if (equipment in it.equipment) it.equipment - equipment else it.equipment + equipment)
    }

    /** Generuje cykl, zapisuje go i przenosi sprzęt, poziom oraz wzrost i wagę („O mnie”) do ustawień. */
    fun generate() {
        val state = _ui.value
        val profile = TrainingProfile.sanitize(state.profile.copy(targetScale = state.nextScale))
        _ui.update { it.copy(generating = true, error = null) }
        viewModelScope.launch {
            try {
                val exercises = exerciseRepo.getAll()
                val programId = programRepo.newProgramId()
                // Trener (D-35): ćwiczenia z „nie chcę” nie wchodzą do nowego cyklu.
                val excluded = coachRepo.excludedIds()
                val plan = PlanGenerator.generate(profile, exercises, System.currentTimeMillis(), idPrefix = programId, excluded = excluded)
                if (plan.workouts.isEmpty()) {
                    _ui.update {
                        it.copy(
                            generating = false,
                            error = "Z tym sprzętem i poziomem nie udało się ułożyć treningu. Zaznacz więcej sprzętu albo zmień cel.",
                        )
                    }
                    return@launch
                }
                programRepo.save(profile, plan, startedAt = System.currentTimeMillis(), programId = programId)
                settingsRepo.setOwnedEquipment(profile.equipment)
                settingsRepo.setDifficulty(profile.level)
                settingsRepo.setBody(profile.heightCm, profile.weightKg)
                _ui.update { it.copy(generating = false, saved = true, profile = profile) }
            } catch (t: Throwable) {
                Log.e(TAG, "Nie udało się wygenerować planu", t)
                _ui.update { it.copy(generating = false, error = t.message ?: "Nie udało się wygenerować planu") }
            }
        }
    }

    /**
     * Ile z zaplanowanych treningów zrobiono i ile serii przy tym zapisano.
     * Mianownikiem serii są tylko treningi ukończone — pominięta sesja już obniża frekwencję,
     * więc liczenie jej serii drugi raz byłoby podwójną karą.
     */
    private suspend fun reviewOf(program: Program): CycleReview {
        val planWorkoutIds = program.days.mapNotNull { it.workoutId }.toSet()
        val done = PlanSessions.done(sessionRepo.allSessions(), program)
        val logsBySession = sessionRepo.allSetLogs().groupBy { it.sessionId }
        val setsPerWorkout = planWorkoutIds.associateWith { id ->
            workoutRepo.getById(id)?.let { WorkoutEditing.workCount(it.blocks) } ?: 0
        }
        return CycleReview(
            plannedSessions = program.days.count { !it.isRest },
            doneSessions = done.size,
            plannedSets = done.sumOf { setsPerWorkout[it.workoutId] ?: 0 },
            loggedSets = done.sumOf { logsBySession[it.id]?.size ?: 0 },
        )
    }

    private fun update(transform: (TrainingProfile) -> TrainingProfile) =
        _ui.update { it.copy(profile = TrainingProfile.sanitize(transform(it.profile)), error = null).withCounts() }

    /** Licznik ćwiczeń kroku sprzętu z tych samych reguł co generator (`PlanGenerator.candidates`). */
    private fun WizardUiState.withCounts(): WizardUiState =
        if (library.isEmpty()) this else copy(
            candidates = PlanGenerator.candidates(library, profile.equipment).size,
            bodyweightCandidates = PlanGenerator.candidates(library, emptySet()).size,
        )

    companion object {
        private const val TAG = "PlanWizardViewModel"
    }
}
