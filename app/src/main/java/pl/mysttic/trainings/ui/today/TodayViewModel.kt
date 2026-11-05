package pl.mysttic.trainings.ui.today

import android.util.Log
import pl.mysttic.trainings.domain.plan.PlanSessions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pl.mysttic.trainings.data.repo.CoachRepository
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.coach.ExerciseTuning
import pl.mysttic.trainings.domain.engine.snapshot
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.plan.SetProgression
import pl.mysttic.trainings.domain.plan.TargetSuggestion
import pl.mysttic.trainings.domain.plan.preset.PresetCatalog
import pl.mysttic.trainings.domain.plan.preset.PresetId
import pl.mysttic.trainings.domain.stats.SessionStats
import pl.mysttic.trainings.service.MonotonicClock
import pl.mysttic.trainings.service.PlayerController
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TodayUiState(
    val loading: Boolean = true,
    val lastSession: Session? = null,
    val doneThisWeek: Int = 0,
)

/**
 * Plan jako kolejka (D-21): „następny” to pierwszy nieodhaczony trening, a nie wpis z dzisiejszą datą.
 * Pominięty dzień nic nie kasuje — trening czeka.
 */
data class PlanCard(
    val programName: String,
    val nextWorkoutId: String?,
    val nextWorkoutName: String?,
    val nextWorkoutMinutes: Int = 0,
    val nextWorkoutExercises: Int = 0,
    val doneCount: Int,
    val totalCount: Int,
    val isComplete: Boolean,
    val doneToday: Boolean,
    /** Postęp w bieżącym tygodniu cyklu — pasek segmentów na karcie dnia (D-32). */
    val weekDone: Int = 0,
    val weekTotal: Int = 0,
    /** Ile dni liczy cykl i ile czasu zajęły jego ukończone treningi — siatka statystyk po zamknięciu cyklu. */
    val cycleDays: Int = 0,
    val cycleDurationSec: Int = 0,
    /** Propozycje podbicia celów w następnym treningu z planu (M5). */
    val suggestions: List<TargetSuggestion> = emptyList(),
    /**
     * Ilustracje ćwiczeń następnego treningu w kolejności wykonania, bez powtórzeń (D-52): pasek miniatur na karcie dnia.
     * Ćwiczenie pokazane na ekranie (wariant poziomu, D-43), nie pozycja szablonu.
     */
    val previewImages: List<String> = emptyList(),
    /** Ile różnych ćwiczeń ma następny trening — z tego pasek liczy „+N” za ostatnią miniaturą. */
    val previewCount: Int = 0,
    /** Gotowy program (D-37): opis etapu na karcie, ostrzeżenie programu i wybory potrzebne do powtórki. */
    val isPreset: Boolean = false,
    val note: String? = null,
    val warning: String? = null,
    val presetId: String? = null,
    val level: Difficulty = Difficulty.INTERMEDIATE,
    val presetParam: Int? = null,
    val programId: String = "",
    /** Plan z kreatora: po zamknięciu pyta o odczucia i generuje kolejny cykl; inne plany się powtarza (D-42). */
    val isWizard: Boolean = true,
)

/** Ile dni z rzędu zakończyło się treningiem; przerwa kasuje licznik. */
data class StreakCard(val current: Int, val best: Int)

/**
 * Osobny stan ładowania, żeby przy zimnym starcie karta nie mignęła komunikatem „nie masz planu”,
 * zanim baza zdąży odpowiedzieć.
 */
data class PlanCardState(val loading: Boolean = true, val card: PlanCard? = null)

/** Trening trwający w tle (serwis): karta „Wróć do treningu” na Dziś. */
data class ActiveCard(
    val workoutId: String,
    val workoutName: String,
    val workNumber: Int,
    val workTotal: Int,
    val paused: Boolean,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val sessionRepo: SessionRepository,
    private val settingsRepo: SettingsRepository,
    private val programRepo: ProgramRepository,
    private val coachRepo: CoachRepository,
    controller: PlayerController,
    clock: MonotonicClock,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val planner = WorkoutPlanner()

    /** Karta planu na górze „Dziś”: `card == null` po załadowaniu = nie ma jeszcze cyklu. */
    val plan: StateFlow<PlanCardState> = combine(
        programRepo.observeActive(),
        workoutRepo.observeAll(),
        sessionRepo.observeSessions(),
        settingsRepo.settings,
        coachRepo.observeAll(),
    ) { program, workouts, sessions, settings, tuning ->
        if (program == null) return@combine PlanCardState(loading = false, card = null)
        val done = PlanSessions.done(sessions, program)
        val progress = PlanSessions.progress(sessions, program)
        val todayDate = LocalDate.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), zone)
        val nextId = progress.next?.workoutId
        val week = progress.next?.let { next ->
            val weekIndex = (next.dayIndex - 1) / 7
            val range = (weekIndex * 7 + 1)..(weekIndex * 7 + 7)
            val trainingDays = program.days.filter { it.dayIndex in range && !it.isRest }
            trainingDays.count { it.dayIndex in progress.doneDayIndexes } to trainingDays.size
        } ?: (0 to 0)
        val next = nextId?.let { id -> workouts.firstOrNull { it.id == id } }
        val exercises = exerciseRepo.getMap()
        val steps = next?.let {
            planner.expand(it, exercises, settings.difficulty, settings.ownedEquipment, settings.countdownSec, tuning)
        }.orEmpty()
        val shownIds = steps.filterIsInstance<Step.Work>().map { it.exerciseId }.distinct()
        // Gotowy program (D-37): stały protokół, więc bez propozycji progresji; karta pokazuje etap i ostrzeżenie.
        val preset = program.presetId?.let { PresetCatalog.get(it) }
        PlanCardState(
            loading = false,
            card = PlanCard(
                programName = program.name,
                nextWorkoutId = nextId,
                nextWorkoutName = next?.name,
                nextWorkoutMinutes = if (steps.isEmpty()) 0 else (planner.estimateSeconds(steps) + 59) / 60,
                nextWorkoutExercises = next?.exerciseIds?.size ?: 0,
                previewImages = shownIds.mapNotNull { exercises[it]?.images?.lastOrNull() }.take(PREVIEW_MAX),
                previewCount = shownIds.size,
                doneCount = progress.doneCount,
                totalCount = progress.totalCount,
                isComplete = progress.isComplete,
                doneToday = done.any { LocalDate.ofInstant(Instant.ofEpochMilli(it.startedAt), zone) == todayDate },
                weekDone = week.first,
                weekTotal = week.second,
                cycleDays = program.cycleDays,
                cycleDurationSec = done.sumOf { it.durationSec ?: 0 },
                suggestions = if (program.isPreset) {
                    emptyList()
                } else {
                    nextId?.let { suggestionsFor(it, settings.difficulty, settings.ownedEquipment, tuning) }.orEmpty()
                },
                isPreset = program.isPreset,
                note = if (!program.isWizard) next?.note?.takeIf { it.isNotBlank() } else null,
                warning = preset?.warning,
                presetId = program.presetId,
                level = program.profile.level,
                presetParam = program.profile.presetParam,
                programId = program.id,
                isWizard = program.isWizard,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanCardState())

    /** Seria dni zgodnych z planem: trening albo zaplanowany odpoczynek (D-21). */
    val streak: StateFlow<StreakCard> = combine(
        sessionRepo.observeSessions(),
        programRepo.observeActive(),
    ) { sessions, program ->
        val dates = sessions.filter { it.status == SessionStatus.DONE }
            .map { LocalDate.ofInstant(Instant.ofEpochMilli(it.startedAt), zone) }
        val isRestDay: (LocalDate) -> Boolean = { date -> program?.isRestDate(date, zone) == true }
        val today = LocalDate.now(zone)
        StreakCard(
            current = SessionStats.streak(dates, today, isRestDay),
            best = SessionStats.bestStreak(dates, today, isRestDay),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakCard(0, 0))

    val active: StateFlow<ActiveCard?> = combine(controller.state, controller.active) { state, active ->
        if (state == null || state.isOver || active == null) return@combine null
        val snapshot = state.snapshot(clock.now())
        ActiveCard(
            workoutId = active.workout.id,
            workoutName = active.workout.name,
            workNumber = snapshot.workNumber,
            workTotal = snapshot.workTotal,
            paused = snapshot.paused,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val weekStartMillis: Long = LocalDate.now()
        .with(DayOfWeek.MONDAY)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    val uiState: StateFlow<TodayUiState> = combine(
        sessionRepo.observeLastDone(),
        sessionRepo.observeDoneCountSince(weekStartMillis),
    ) { last, doneCount ->
        TodayUiState(loading = false, lastSession = last, doneThisWeek = doneCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /**
     * Propozycje progresji dla następnego treningu z planu, liczone od celu pokazanego (poziom, sprzęt, trener,
     * D-43); wbudowanych nie ruszamy (D-19).
     */
    private suspend fun suggestionsFor(
        workoutId: String,
        difficulty: Difficulty,
        owned: Set<Equipment>,
        tuning: Map<String, ExerciseTuning>,
    ): List<TargetSuggestion> {
        val workout = workoutRepo.getById(workoutId) ?: return emptyList()
        if (workout.isBuiltIn) return emptyList()
        val previous = sessionRepo.previousDone(workoutId, System.currentTimeMillis()) ?: return emptyList()
        return SetProgression.suggest(
            workout.blocks, sessionRepo.setLogs(previous.id), exerciseRepo.getMap(), difficulty, owned, tuning, planner,
        )
    }

    /** Jeden tap na „Dziś”: podbite cele lądują w szablonie następnego treningu. */
    fun applySuggestions(workoutId: String, suggestions: List<TargetSuggestion>) {
        if (suggestions.isEmpty()) return
        viewModelScope.launch {
            val workout = workoutRepo.getById(workoutId) ?: return@launch
            workoutRepo.save(
                workout.copy(
                    blocks = SetProgression.apply(workout.blocks, suggestions),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Trzy ilustracje na powitanie przed pierwszym planem (D-52): nie informacja, tylko zapowiedź tego, co będzie.
     * Stałe ćwiczenia z bazy; gdy któregoś zabraknie (wycofane, D-47), w jego miejsce wchodzi pierwsze z ilustracją.
     */
    val welcomeImages: StateFlow<List<String>> = exerciseRepo.observeAll().map { all ->
        val withImages = all.filter { it.retiredAt == null && it.images.isNotEmpty() }
        val preferred = WELCOME_IDS.mapNotNull { id -> withImages.firstOrNull { it.id == id } }
        (preferred + withImages.filter { it !in preferred }).take(WELCOME_COUNT).mapNotNull { it.images.lastOrNull() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Powtórka w toku: blokada podwójnego tapnięcia „Powtórz plan”. */
    private var repeating = false

    /**
     * Powtórka zamkniętego planu. Gotowy program buduje się od nowa z katalogu (D-37); plan własny i od kogoś
     * to nowy przebieg z tymi samymi treningami (D-42).
     */
    fun repeatPlan() {
        val card = plan.value.card ?: return
        if (!card.isComplete && card.nextWorkoutId != null) return
        // Drugie tapnięcie przed odświeżeniem karty zrobiłoby drugą kopię planu i wstrzymało pierwszą.
        if (repeating) return
        repeating = true
        val presetId = card.presetId?.let { id -> PresetId.entries.firstOrNull { it.name == id } }
        viewModelScope.launch {
            try {
                if (presetId == null) {
                    programRepo.repeat(card.programId)
                } else {
                    programRepo.startPreset(
                        id = presetId,
                        level = card.level,
                        cycleDays = card.cycleDays,
                        param = card.presetParam,
                        exercises = exerciseRepo.getMap(),
                        equipment = settingsRepo.current().ownedEquipment,
                    )
                }
                // Blokada trzyma, dopóki karta nie pokaże nowego planu: zapis w bazie wyprzedza odświeżenie ekranu.
                withTimeoutOrNull(REFRESH_WAIT_MS) { plan.first { it.card?.programId != card.programId } }
            } catch (t: Throwable) {
                Log.e(TAG, "Nie udało się powtórzyć programu", t)
            } finally {
                repeating = false
            }
        }
    }

    companion object {
        private const val TAG = "TodayViewModel"
        private const val REFRESH_WAIT_MS = 3_000L
        /** Miniatur na karcie dnia: cztery, a przy dłuższym treningu trzy i „+N”. */
        const val PREVIEW_MAX = 4
        private const val WELCOME_COUNT = 3
        private val WELCOME_IDS = listOf("pushups", "high-knees", "plank")
    }
}
