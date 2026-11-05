package pl.mysttic.trainings.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.library.LibraryFacets
import pl.mysttic.trainings.domain.library.LibraryFilter
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.ui.adaptive.AppLayout
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.ExerciseRow
import pl.mysttic.trainings.ui.common.ExerciseTile
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.SearchField
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.ViewToggle
import pl.mysttic.trainings.ui.theme.Dimens

/**
 * Biblioteka: nagłówek z przełącznikiem kafelki / lista, szukanie i pasek „Filtry · n” / „Mój sprzęt” / licznik zostają
 * na górze; panel z chipami rozwija się jako pierwszy element siatki (przewija się z wynikami), a zwinięty oddaje
 * ekran ilustracjom. Jedna siatka na oba widoki: dwie kolumny kafelków albo jedna kolumna wierszy.
 */
@Composable
fun LibraryScreen(
    onOpenExercise: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filter = state.filter
    val gridState = rememberLazyGridState()

    // Rozwinięcie ma pokazać panel, zwinięcie nie może zostawić siatki przewiniętej o resztkę panelu.
    LaunchedEffect(state.panelExpanded) { gridState.animateScrollToItem(0) }
    // Zmiana filtra przy zwiniętym panelu (szukanie, „Mój sprzęt”) zaczyna od góry; przy rozwiniętym panelu
    // nie ruszamy przewinięcia, żeby chip na dole panelu nie uciekał spod palca.
    LaunchedEffect(filter) { if (!state.panelExpanded) gridState.scrollToItem(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = Dimens.screenPadding)) {
            Spacer(Modifier.height(Dimens.sectionGap))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ćwiczenia", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                ViewToggle(tiles = state.tiles, onChange = viewModel::setTiles)
            }
            Spacer(Modifier.height(12.dp))
            SearchField(value = filter.query, onValueChange = viewModel::setQuery, placeholder = "Szukaj ćwiczenia")
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterPill(
                    text = if (filter.selectedCount == 0) "Filtry" else "Filtry · ${filter.selectedCount}",
                    selected = state.panelExpanded,
                    onClick = viewModel::togglePanel,
                    trailingIcon = if (state.panelExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                )
                FilterPill(text = "Mój sprzęt", selected = filter.onlyOwned, onClick = viewModel::toggleOwned)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${state.exercises.size} z ${state.totalCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Hairline()
        }
        LazyVerticalGrid(
            // Kafelki: 2 kolumny na telefonie, 3–4 na tablecie; lista: jedna, a od szerokości tabletu dwie (D-42).
            columns = GridCells.Fixed(
                if (state.tiles) LocalAppLayout.current.tileColumns
                else if (LocalAppLayout.current.widthDp >= AppLayout.EXPANDED_MIN) 2 else 1,
            ),
            state = gridState,
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                AnimatedVisibility(
                    visible = state.panelExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    FilterPanel(
                        filter = filter,
                        facets = state.facets,
                        onToggleNoEquipment = viewModel::toggleNoEquipment,
                        onToggleEquipment = viewModel::toggleEquipment,
                        onToggleCategory = viewModel::toggleCategory,
                        onToggleLevel = viewModel::toggleLevel,
                        onToggleMuscle = viewModel::toggleMuscle,
                        onClear = viewModel::clearFilters,
                    )
                }
            }
            if (!state.loading && state.exercises.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyState("Nic nie pasuje do filtrów.") }
            }
            items(state.exercises, key = { it.id }) { exercise ->
                val missing = exercise.equipment.any { it !in state.ownedEquipment }
                val warning = if (missing) "Nie masz tego sprzętu" else null
                if (state.tiles) {
                    ExerciseTile(
                        name = exercise.namePl,
                        secondary = tileSecondary(exercise),
                        imagePath = exercise.images.lastOrNull(),
                        warning = warning,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        onClick = { onOpenExercise(exercise.id) },
                    )
                } else {
                    ExerciseRow(
                        name = exercise.namePl,
                        secondary = exercise.primaryMuscles.joinToString { it.labelPl } + " · " + exercise.level.labelPl +
                            " · " + (exercise.equipment.joinToString { it.shortPl }.ifEmpty { "bez sprzętu" }),
                        imagePath = exercise.images.lastOrNull(),
                        warning = warning,
                        large = true,
                        onClick = { onOpenExercise(exercise.id) },
                    )
                }
            }
        }
    }
}

/** Opis pod kafelkiem: pierwsza partia, poziom i sprzęt, jeśli jakiś trzeba (dwie linie to za mało na pełną listę partii). */
private fun tileSecondary(exercise: Exercise): String = listOfNotNull(
    exercise.primaryMuscles.firstOrNull()?.labelPl,
    exercise.level.labelPl,
    exercise.equipment.joinToString { it.shortPl }.ifEmpty { null },
).joinToString(" · ")

/** Cztery sekcje chipów: wartości w sekcji łączą się „lub”, sekcje „i” (reguła w `LibraryFilter`). */
@Composable
private fun FilterPanel(
    filter: LibraryFilter,
    facets: LibraryFacets,
    onToggleNoEquipment: () -> Unit,
    onToggleEquipment: (Equipment) -> Unit,
    onToggleCategory: (ExerciseCategory) -> Unit,
    onToggleLevel: (Difficulty) -> Unit,
    onToggleMuscle: (MuscleGroup) -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilterSection("Sprzęt") {
            FilterPill(text = "Bez sprzętu", selected = filter.noEquipment, onClick = onToggleNoEquipment)
            facets.equipment.forEach { item ->
                FilterPill(text = item.shortPl, selected = item in filter.equipment, onClick = { onToggleEquipment(item) })
            }
        }
        FilterSection("Rodzaj") {
            facets.categories.forEach { category ->
                FilterPill(text = category.labelPl, selected = category in filter.categories, onClick = { onToggleCategory(category) })
            }
        }
        FilterSection("Poziom") {
            facets.levels.forEach { level ->
                FilterPill(text = level.labelPl, selected = level in filter.levels, onClick = { onToggleLevel(level) })
            }
        }
        FilterSection("Partie") {
            facets.muscles.forEach { muscle ->
                FilterPill(text = muscle.labelPl, selected = muscle in filter.muscles, onClick = { onToggleMuscle(muscle) })
            }
        }
        if (filter.hasSelection) {
            Spacer(Modifier.height(Dimens.sectionGap))
            SecondaryAction(text = "Wyczyść filtry", onClick = onClear)
        }
        Spacer(Modifier.height(Dimens.sectionGap))
        Hairline()
        Spacer(Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Overline(title, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}
