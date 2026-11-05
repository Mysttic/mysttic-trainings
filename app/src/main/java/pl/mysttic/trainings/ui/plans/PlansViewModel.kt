package pl.mysttic.trainings.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.domain.plan.PlanList
import pl.mysttic.trainings.domain.plan.PlanListRow
import pl.mysttic.trainings.domain.plan.PlanStatus
import javax.inject.Inject

data class PlansUiState(
    val loading: Boolean = true,
    val active: List<PlanListRow> = emptyList(),
    val waiting: List<PlanListRow> = emptyList(),
    val finished: List<PlanListRow> = emptyList(),
) {
    val isEmpty: Boolean get() = active.isEmpty() && waiting.isEmpty() && finished.isEmpty()
}

/** Lista planów (D-42): stan każdego liczony z jego sesji, bez kolumny w bazie. */
@HiltViewModel
class PlansViewModel @Inject constructor(
    programRepo: ProgramRepository,
    sessionRepo: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<PlansUiState> = combine(programRepo.observeAll(), sessionRepo.observeSessions()) { programs, sessions ->
        val rows = PlanList.rows(programs, sessions)
        PlansUiState(
            loading = false,
            active = rows.filter { it.status == PlanStatus.ACTIVE },
            waiting = rows.filter { it.status == PlanStatus.PAUSED || it.status == PlanStatus.NOT_STARTED || it.status == PlanStatus.EMPTY },
            finished = rows.filter { it.status == PlanStatus.COMPLETE },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())
}
