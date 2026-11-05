package pl.mysttic.trainings.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
import pl.mysttic.trainings.ui.navigation.SessionDetailRoute
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

    /** Ponowne kliknięcie zaznaczonego statusu zdejmuje filtr. */
    fun setStatus(value: SessionStatus?) { status.value = if (status.value == value) null else value }
}

data class SessionDetailUiState(
    val loading: Boolean = true,
    val session: Session? = null,
    val logs: List<SetLog> = emptyList(),
    /** Porównanie z poprzednim ukończonym wykonaniem tego treningu. */
    val deltas: List<ExerciseDelta> = emptyList(),
    val deleted: Boolean = false,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepo: SessionRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<SessionDetailRoute>()
    private val session = MutableStateFlow<Session?>(null)
    private val previousLogs = MutableStateFlow<List<SetLog>>(emptyList())
    private val deleted = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val current = sessionRepo.getSession(route.sessionId)
            session.value = current
            previousLogs.value = current?.workoutId
                ?.let { sessionRepo.previousDone(it, current.startedAt) }
                ?.let { sessionRepo.setLogs(it.id) }
                .orEmpty()
        }
    }

    val uiState: StateFlow<SessionDetailUiState> = combine(
        session,
        sessionRepo.observeSetLogs(route.sessionId),
        previousLogs,
        deleted,
    ) { s, logs, previous, isDeleted ->
        SessionDetailUiState(
            loading = false,
            session = s,
            logs = logs,
            deltas = if (logs.isEmpty()) emptyList() else SessionStats.perExercise(logs, previous),
            deleted = isDeleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    fun saveFeedback(rpe: Int?, note: String) {
        viewModelScope.launch {
            sessionRepo.updateFeedback(route.sessionId, rpe, note)
            session.value = sessionRepo.getSession(route.sessionId)
        }
    }

    /** Usuwa sesję z seriami; ekran zamyka się po `deleted`. */
    fun delete() {
        viewModelScope.launch {
            sessionRepo.deleteSession(route.sessionId)
            deleted.value = true
        }
    }
}
