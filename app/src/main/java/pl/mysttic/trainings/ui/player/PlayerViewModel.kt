package pl.mysttic.trainings.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.mysttic.trainings.data.repo.CoachRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.domain.coach.ExerciseFeedback
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import pl.mysttic.trainings.domain.engine.PlayerSnapshot
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.engine.snapshot
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.service.MonotonicClock
import pl.mysttic.trainings.service.PlayerController
import pl.mysttic.trainings.service.SessionSummary
import pl.mysttic.trainings.service.WorkoutService
import pl.mysttic.trainings.ui.navigation.PlayerRoute
import javax.inject.Inject
import pl.mysttic.trainings.data.repo.ProgramRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class PlayerUiState(
    val loading: Boolean = true,
    val workoutName: String = "",
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val snapshot: PlayerSnapshot? = null,
    val currentExercise: Exercise? = null,
    /** Ćwiczenie z karty „Dalej” w przerwie (D-32). */
    val nextExercise: Exercise? = null,
    val keepScreenOn: Boolean = true,
    val restoredFromCheckpoint: Boolean = false,
    val summary: SessionSummary? = null,
    val error: String? = null,
    /** Stały protokół (D-37): poziom nie zmienia celów, a trener nie koryguje ćwiczeń w tym treningu. */
    val fixed: Boolean = false,
    /** Notatka do bieżącego ćwiczenia w planie tej sesji (D-44). */
    val planNote: String = "",
    /** Notatka do ćwiczenia z karty „Dalej”, żeby w przerwie przygotować ciężar. */
    val nextPlanNote: String = "",
    /** Serie zapisane w tej sesji: tyle zostanie w historii po „Przerwij” (pominięte się nie liczą). */
    val loggedSets: Int = 0,
)

/**
 * M2: cienki widok na [PlayerController]. Silnikiem tyka [WorkoutService]; ten ViewModel tylko odświeża
 * snapshot dla UI co [TICK_MS] i przekazuje zdarzenia.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val controller: PlayerController,
    private val sessionRepo: SessionRepository,
    private val coachRepo: CoachRepository,
    private val programRepo: ProgramRepository,
    private val clock: MonotonicClock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PlayerRoute>()

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    /** Notatki planu, do którego należy sesja (D-44); czyta je widok, silnik o nich nie wie (D-16). */
    private val planNotes = controller.active.map { it?.session?.programId }.distinctUntilChanged().flatMapLatest { programId ->
        if (programId == null) flowOf(emptyMap()) else programRepo.observeNotes(programId)
    }

    private val baseUi = combine(
        controller.state,
        controller.active,
        controller.summary,
        controller.error,
        ticker,
    ) { state, active, summary, error, _ ->
        val snapshot = state?.snapshot(clock.now())
        val exercise = (snapshot?.step as? Step.Work)?.let { active?.exercises?.get(it.exerciseId) }
        val next = snapshot?.nextWork?.let { active?.exercises?.get(it.exerciseId) }
        PlayerUiState(
            loading = state == null && error == null && summary == null,
            workoutName = active?.workout?.name.orEmpty(),
            difficulty = state?.difficulty ?: active?.settings?.difficulty ?: Difficulty.INTERMEDIATE,
            snapshot = snapshot,
            currentExercise = exercise,
            nextExercise = next,
            keepScreenOn = active?.settings?.keepScreenOn ?: true,
            restoredFromCheckpoint = active?.restoredFromCheckpoint == true,
            summary = summary,
            error = error,
            fixed = active?.workout?.isFixed == true,
        )
    }

    /** Liczba serii w logu bieżącej sesji; okno „Przerwać trening?” podaje ją zamiast numeru serii. */
    private val loggedSets = controller.active.map { it?.session?.id }.distinctUntilChanged().flatMapLatest { sessionId ->
        if (sessionId == null) flowOf(0) else sessionRepo.observeSetLogs(sessionId).map { it.size }
    }

    val ui: StateFlow<PlayerUiState> = combine(baseUi, planNotes, loggedSets) { ui, notes, logged ->
        ui.copy(
            planNote = ui.currentExercise?.id?.let { notes[it] }.orEmpty(),
            nextPlanNote = ui.nextExercise?.id?.let { notes[it] }.orEmpty(),
            loggedSets = logged,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    private var startRequested = false

    /**
     * Startuje foreground service, który ładuje trening do kontrolera. Raz na życie ViewModelu: obrót ekranu
     * (nowa kompozycja, ten sam VM) nie może po podsumowaniu zacząć drugiej sesji. Kontroler i tak ignoruje start tego samego trwającego treningu.
     */
    fun ensureStarted() {
        if (startRequested) return
        startRequested = true
        WorkoutService.start(appContext, route.workoutId)
    }

    /** Wyjście z trasy playera (nie obrót): zwolnij zakończony stan; trwający trening zostaje w kontrolerze. */
    override fun onCleared() {
        super.onCleared()
        controller.clear()
    }

    /**
     * Ocena wysiłku i notatka trafiają do sesji od razu po treningu, a nie dopiero w Historii (D-32);
     * opinie o ćwiczeniach idą do trenera (D-35). Zapis nie może zginąć z ekranem, stąd [NonCancellable].
     */
    fun saveFeedback(sessionId: String, rpe: Int?, note: String, exercises: Map<String, ExerciseFeedback> = emptyMap()) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                if (rpe != null || note.isNotBlank()) sessionRepo.updateFeedback(sessionId, rpe, note)
                if (exercises.values.any { it != ExerciseFeedback.NONE }) coachRepo.applyFeedback(exercises)
            }
        }
    }

    fun done(actual: Int? = null) = controller.done(actual)
    /** Licznik AMRAP z ekranu, żeby upływ czasu zalogował to, co użytkownik naliczył. */
    fun count(value: Int) = controller.count(value)
    fun skip() = controller.skip()
    fun back() = controller.back()
    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun addRest(seconds: Int = 15) = controller.addRest(seconds)
    fun abort() = controller.abort()
    fun changeDifficulty(difficulty: Difficulty) = controller.changeDifficulty(difficulty)

    /** Ekran playera na wierzchu — tylko wtedy klawisz głośności potwierdza serię (M6). */
    fun setScreenVisible(visible: Boolean) = controller.setPlayerVisible(visible)

    companion object {
        private const val TICK_MS = 250L
    }
}
