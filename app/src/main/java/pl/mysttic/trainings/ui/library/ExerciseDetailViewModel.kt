package pl.mysttic.trainings.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.repo.CoachRepository
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.coach.ExerciseTuning
import pl.mysttic.trainings.domain.equipment.EquipmentMatcher
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.stats.ExerciseRecord
import pl.mysttic.trainings.domain.stats.ProgressStats
import pl.mysttic.trainings.ui.navigation.ExerciseDetailRoute
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ExerciseDetailUiState(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    val easier: Exercise? = null,
    val harder: Exercise? = null,
    val ownedEquipment: Set<Equipment> = emptySet(),
    val missingEquipment: Set<Equipment> = emptySet(),
    val substitutes: List<Exercise> = emptyList(),
    /** Najlepsza seria dzień po dniu (M5); pusta lista = ćwiczenie jeszcze nierobione. */
    val history: List<ExerciseRecord> = emptyList(),
    val personalBest: ExerciseRecord? = null,
    /** Pamięć trenera o tym ćwiczeniu (D-35); null = bez korekty i bez wykluczenia. */
    val tuning: ExerciseTuning? = null,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepo: ExerciseRepository,
    settingsRepo: SettingsRepository,
    sessionRepo: SessionRepository,
    private val coachRepo: CoachRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ExerciseDetailRoute>()
    private val zone: ZoneId = ZoneId.systemDefault()

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        exerciseRepo.observeAll(),
        settingsRepo.settings,
        sessionRepo.observeSetLogsForExercise(route.exerciseId),
        coachRepo.observeAll(),
    ) { all, settings, logs, tuning ->
        val byId = all.associateBy { it.id }
        val exercise = byId[route.exerciseId]
        if (exercise == null) {
            ExerciseDetailUiState(loading = false)
        } else {
            val missing = exercise.missingEquipment(settings.ownedEquipment)
            val history = ProgressStats.exerciseHistory(logs, route.exerciseId) {
                LocalDate.ofInstant(Instant.ofEpochMilli(it), zone)
            }
            ExerciseDetailUiState(
                loading = false,
                exercise = exercise,
                easier = exercise.easierId?.let { byId[it] },
                harder = exercise.harderId?.let { byId[it] },
                ownedEquipment = settings.ownedEquipment,
                missingEquipment = missing,
                substitutes = if (missing.isEmpty()) emptyList() else EquipmentMatcher.substitutes(exercise, all, settings.ownedEquipment),
                history = history,
                personalBest = ProgressStats.personalBest(history),
                tuning = tuning[route.exerciseId],
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    /** Trener (D-35): wykluczenie z planów albo jego cofnięcie. */
    fun setExcluded(excluded: Boolean) {
        viewModelScope.launch { coachRepo.setExcluded(route.exerciseId, excluded) }
    }

    fun resetTuning() {
        viewModelScope.launch { coachRepo.resetDeltas(route.exerciseId) }
    }
}
