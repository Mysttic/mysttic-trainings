package pl.mysttic.trainings.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.library.LibraryFacets
import pl.mysttic.trainings.domain.library.LibraryFilter
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.MuscleGroup
import javax.inject.Inject

data class LibraryUiState(
    val loading: Boolean = true,
    val filter: LibraryFilter = LibraryFilter(),
    /** Panel filtrów jest domyślnie zwinięty: lista ze zdjęciami dostaje cały ekran. */
    val panelExpanded: Boolean = false,
    /** Kafelki (dwie kolumny z ilustracją) albo lista wierszy; zapamiętane w ustawieniach. */
    val tiles: Boolean = true,
    val facets: LibraryFacets = LibraryFacets(),
    val ownedEquipment: Set<Equipment> = emptySet(),
    val exercises: List<Exercise> = emptyList(),
    val totalCount: Int = 0,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    exerciseRepo: ExerciseRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter())
    private val panelExpanded = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        exerciseRepo.observeAll().map { all -> LibraryFilter.visible(all).let { shelf -> shelf to LibraryFacets.of(shelf) } },
        settingsRepo.settings,
        filter,
        panelExpanded,
    ) { (all, facets), settings, f, expanded ->
        LibraryUiState(
            loading = false,
            filter = f,
            panelExpanded = expanded,
            tiles = settings.libraryTiles,
            facets = facets,
            ownedEquipment = settings.ownedEquipment,
            exercises = f.apply(all, settings.ownedEquipment),
            totalCount = all.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) = filter.update { it.copy(query = value) }

    fun toggleOwned() = filter.update { it.copy(onlyOwned = !it.onlyOwned) }

    fun toggleNoEquipment() = filter.update { it.copy(noEquipment = !it.noEquipment) }

    fun toggleEquipment(item: Equipment) = filter.update { it.toggleEquipment(item) }

    fun toggleCategory(category: ExerciseCategory) = filter.update { it.toggleCategory(category) }

    fun toggleLevel(level: Difficulty) = filter.update { it.toggleLevel(level) }

    fun toggleMuscle(muscle: MuscleGroup) = filter.update { it.toggleMuscle(muscle) }

    fun clearFilters() = filter.update { it.cleared() }

    fun togglePanel() = panelExpanded.update { !it }

    fun setTiles(tiles: Boolean) {
        viewModelScope.launch { settingsRepo.setLibraryTiles(tiles) }
    }
}
