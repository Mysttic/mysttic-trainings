package pl.mysttic.trainings.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.stats.ExerciseDelta
import pl.mysttic.trainings.domain.stats.HistoryPeriod
import pl.mysttic.trainings.domain.stats.DayHeat
import pl.mysttic.trainings.domain.stats.PeriodStats
import pl.mysttic.trainings.domain.stats.ProgressStats
import pl.mysttic.trainings.domain.stats.SessionStats
import pl.mysttic.trainings.domain.stats.WeekVolume
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HistoryUiState(
    val loading: Boolean = true,
    val sessions: List<Session> = emptyList(),
    val period: HistoryPeriod = HistoryPeriod.ALL,
    val status: SessionStatus? = null,
    /** Statystyki wybranego okresu (zawsze z sesji ukończonych, niezależnie od filtra statusu). */
    val stats: PeriodStats = PeriodStats(),
    val totalCount: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val weeks: List<WeekVolume> = emptyList(),
    val heatmap: List<DayHeat> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    sessionRepo: SessionRepository,
    programRepo: ProgramRepository,
) : ViewModel() {

    private val period = MutableStateFlow(HistoryPeriod.ALL)
    private val status = MutableStateFlow<SessionStatus?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        sessionRepo.observeSessions(),
        sessionRepo.observeAllSetLogs(),
        period,
        status,
        programRepo.observeActive(),
    ) { sessions, logs, selectedPeriod, selectedStatus, program ->
        val now = System.currentTimeMillis()
        val since = selectedPeriod.days?.let { now - it * SessionStats.DAY_MS } ?: Long.MIN_VALUE
        val zone = ZoneId.systemDefault()
        val doneDates = sessions.filter { it.status == SessionStatus.DONE }
            .map { LocalDate.ofInstant(Instant.ofEpochMilli(it.startedAt), zone) }
        val isRestDay: (LocalDate) -> Boolean = { date -> program?.isRestDate(date, zone) == true }
        val today = LocalDate.now(zone)
        val toDate: (Long) -> LocalDate = { LocalDate.ofInstant(Instant.ofEpochMilli(it), zone) }
        HistoryUiState(
            loading = false,
            sessions = SessionStats.filter(sessions, selectedStatus, selectedPeriod, now),
            period = selectedPeriod,
            status = selectedStatus,
            stats = SessionStats.period(sessions, logs, from = since),
            totalCount = sessions.size,
            streak = SessionStats.streak(doneDates, today, isRestDay),
            bestStreak = SessionStats.bestStreak(doneDates, today, isRestDay),
            weeks = ProgressStats.weeklyVolume(sessions, logs, today, weeks = 8, toDate = toDate),
            heatmap = ProgressStats.heatmap(sessions, today, days = 84, toDate = toDate),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setPeriod(value: HistoryPeriod) { period.value = value }

    /** Stan listy z segmentów nad nią (D-54): null = wszystkie. */
    fun setStatus(value: SessionStatus?) { status.value = value }
}

data class SessionDetailUiState(
    val loading: Boolean = true,
    val session: Session? = null,
    val logs: List<SetLog> = emptyList(),
    /** Porównanie z poprzednim ukończonym wykonaniem tego treningu. */
    val deltas: List<ExerciseDelta> = emptyList(),
    val deleted: Boolean = false,
)

/**
 * Szczegóły sesji. Z trasy `SessionDetailRoute` id przychodzi od razu; w Historii na tablecie (D-54) ten sam ViewModel
 * siedzi w panelu obok listy i dostaje wybór przez [select], jak szczegół ćwiczenia w bibliotece (D-52). Klucz
 * w `SavedStateHandle` to nazwa pola trasy, więc wybór w panelu przetrwa odtworzenie procesu.
 */
@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepo: SessionRepository,
) : ViewModel() {

    private val handle = savedStateHandle
    private val selectedId = savedStateHandle.getStateFlow<String?>(KEY_SESSION_ID, null)

    /** Id pokazanej sesji; null = panel jeszcze nic nie pokazuje. */
    val selected: StateFlow<String?> get() = selectedId

    /** Id sesji usuniętej z tego ekranu: `deleted` dotyczy tylko jej, nie kolejnego wyboru w panelu. */
    private val deletedId = MutableStateFlow<String?>(null)

    fun select(sessionId: String) {
        handle[KEY_SESSION_ID] = sessionId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val logs = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else sessionRepo.observeSetLogs(id)
    }

    /** Serie poprzedniego ukończonego wykonania tego treningu, do Δ; liczone raz na wybraną sesję. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val previousLogs = selectedId.flatMapLatest { id ->
        flow {
            val current = id?.let { sessionRepo.getSession(it) }
            emit(
                current?.workoutId
                    ?.let { sessionRepo.previousDone(it, current.startedAt) }
                    ?.let { sessionRepo.setLogs(it.id) }
                    .orEmpty(),
            )
        }
    }

    val uiState: StateFlow<SessionDetailUiState> = combine(
        selectedId,
        // Obserwowana lista sesji: ocena i notatka po „Zapisz” wracają same, bez ponownego czytania.
        sessionRepo.observeSessions(),
        logs,
        previousLogs,
        deletedId,
    ) { id, sessions, sessionLogs, previous, deleted ->
        SessionDetailUiState(
            loading = false,
            session = sessions.firstOrNull { it.id == id },
            logs = sessionLogs,
            deltas = if (sessionLogs.isEmpty()) emptyList() else SessionStats.perExercise(sessionLogs, previous),
            deleted = id != null && id == deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    fun saveFeedback(rpe: Int?, note: String) {
        val id = selectedId.value ?: return
        viewModelScope.launch { sessionRepo.updateFeedback(id, rpe, note) }
    }

    /** Usuwa sesję z seriami; ekran zamyka się po `deleted`, a panel na tablecie przechodzi do innej sesji. */
    fun delete() {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            sessionRepo.deleteSession(id)
            deletedId.value = id
        }
    }

    companion object {
        /** Nazwa pola `SessionDetailRoute.sessionId`: pod nią nawigacja kładzie id z trasy. */
        const val KEY_SESSION_ID = "sessionId"
    }
}
