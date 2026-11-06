package pl.mysttic.trainings.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import pl.mysttic.trainings.ui.adaptive.TwoPane
import pl.mysttic.trainings.ui.common.AppIcons
import pl.mysttic.trainings.ui.common.AppSwitch
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.ExerciseRow
import pl.mysttic.trainings.ui.common.ExerciseTile
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.ScreenTitle
import pl.mysttic.trainings.ui.common.SearchField
import pl.mysttic.trainings.ui.common.SegmentedText
import pl.mysttic.trainings.ui.common.SettingRow
import pl.mysttic.trainings.ui.theme.Dimens

/**
 * Biblioteka (D-38, przebudowana w D-52): nad listą zostają tylko tytuł i pole szukania z przyciskiem filtrów.
 * „Mój sprzęt”, przełącznik kafelki / lista i chipy siedzą w panelu, który rozwija się jako pierwszy element siatki
 * (przewija się z wynikami); licznik i „Wyczyść filtry” pojawiają się dopiero, gdy coś filtruje. Na tablecie
 * w poziomie lista i szczegół stoją obok siebie, a tapnięcie kafelka wybiera ćwiczenie w panelu zamiast otwierać ekran.
 */
@Composable
fun LibraryScreen(
    onOpenExercise: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        LibraryActions(
            onQuery = viewModel::setQuery,
            onTogglePanel = viewModel::togglePanel,
            onToggleOwned = viewModel::toggleOwned,
            onToggleNoEquipment = viewModel::toggleNoEquipment,
            onToggleEquipment = viewModel::toggleEquipment,
            onToggleCategory = viewModel::toggleCategory,
            onToggleLevel = viewModel::toggleLevel,
            onToggleMuscle = viewModel::toggleMuscle,
            onClear = viewModel::clearFilters,
            onTiles = viewModel::setTiles,
        )
    }
    val layout = LocalAppLayout.current
    if (!layout.twoPane) {
        LibraryContent(
            state = state,
            actions = actions,
            onOpenExercise = onOpenExercise,
            // Kafelki: 2 kolumny na telefonie, 3–4 na tablecie; lista: jedna, a od szerokości tabletu dwie (D-42).
            columns = if (state.tiles) layout.tileColumns else if (layout.widthDp >= AppLayout.EXPANDED_MIN) 2 else 1,
        )
        return
    }
    // Panel szczegółu ma własny ViewModel z kluczem: ten sam, co ekran szczegółu, tylko z wyborem zamiast trasy.
    val detailViewModel: ExerciseDetailViewModel = hiltViewModel(key = DETAIL_PANE_KEY)
    val detail by detailViewModel.uiState.collectAsStateWithLifecycle()
    val selected by detailViewModel.selected.collectAsStateWithLifecycle()
    val first = state.exercises.firstOrNull()?.id
    LaunchedEffect(selected, first) {
        if (selected == null && first != null) detailViewModel.select(first)
    }
    TwoPane(
        listWidth = LIBRARY_PANE_WIDTH,
        list = {
            LibraryContent(
                state = state,
                actions = actions,
                onOpenExercise = detailViewModel::select,
                columns = if (state.tiles) 2 else 1,
                selectedId = selected,
            )
        },
        detail = {
            ExerciseDetailContent(
                state = detail,
                onBack = null,
                onOpenExercise = detailViewModel::select,
                onExcludedChange = detailViewModel::setExcluded,
                onResetTuning = detailViewModel::resetTuning,
                wide = true,
            )
        },
    )
}

/** Wszystkie zmiany filtra i widoku; treść jest bezstanowa, więc zrzut rysuje ją z fiksturą. */
class LibraryActions(
    val onQuery: (String) -> Unit = {},
    val onTogglePanel: () -> Unit = {},
    val onToggleOwned: () -> Unit = {},
    val onToggleNoEquipment: () -> Unit = {},
    val onToggleEquipment: (Equipment) -> Unit = {},
    val onToggleCategory: (ExerciseCategory) -> Unit = {},
    val onToggleLevel: (Difficulty) -> Unit = {},
    val onToggleMuscle: (MuscleGroup) -> Unit = {},
    val onClear: () -> Unit = {},
    val onTiles: (Boolean) -> Unit = {},
)

/**
 * Lista biblioteki: nagłówek z szukaniem i jedna siatka na oba widoki (kafelki albo wiersze). [selectedId] podświetla
 * ćwiczenie otwarte w panelu obok (tablet); na telefonie null.
 */
@Composable
fun LibraryContent(
    state: LibraryUiState,
    actions: LibraryActions,
    onOpenExercise: (String) -> Unit,
    columns: Int,
    selectedId: String? = null,
) {
    val filter = state.filter
    val gridState = rememberLazyGridState()

    // Rozwinięcie ma pokazać panel, zwinięcie nie może zostawić siatki przewiniętej o resztkę panelu.
    LaunchedEffect(state.panelExpanded) { gridState.animateScrollToItem(0) }
    // Zmiana filtra przy zwiniętym panelu (szukanie) zaczyna od góry; przy rozwiniętym panelu
    // nie ruszamy przewinięcia, żeby chip na dole panelu nie uciekał spod palca.
    LaunchedEffect(filter) { if (!state.panelExpanded) gridState.scrollToItem(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = Dimens.screenPadding)) {
            Spacer(Modifier.height(Dimens.sectionGap + 4.dp))
            ScreenTitle("Ćwiczenia")
            Spacer(Modifier.height(14.dp))
            SearchField(
                value = filter.query,
                onValueChange = actions.onQuery,
                placeholder = "Szukaj ćwiczenia",
                leadingIcon = {
                    Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    FilterButton(count = filter.panelCount, expanded = state.panelExpanded, onClick = actions.onTogglePanel)
                },
            )
            // Licznik i czyszczenie tylko wtedy, gdy coś zawęża listę — inaczej „283 z 283” to szum.
            if (filter.panelCount > 0 || filter.query.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.exercises.size} z ${state.totalCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (filter.hasSelection) {
                        TextButton(onClick = actions.onClear) {
                            Text("Wyczyść filtry", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(
                start = Dimens.screenPadding - if (selectedId != null) TILE_SELECT_PAD else 0.dp,
                end = Dimens.screenPadding - if (selectedId != null) TILE_SELECT_PAD else 0.dp,
                top = 4.dp,
                bottom = 4.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (selectedId != null) 4.dp else 12.dp),
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
                        ownedEquipment = state.ownedEquipment,
                        tiles = state.tiles,
                        actions = actions,
                        modifier = Modifier.padding(horizontal = if (selectedId != null) TILE_SELECT_PAD else 0.dp),
                    )
                }
            }
            if (!state.loading && state.exercises.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyState("Nic nie pasuje do filtrów.") }
            }
            items(state.exercises, key = { it.id }) { exercise ->
                val missing = exercise.equipment.any { it !in state.ownedEquipment }
                val warning = if (missing) "Nie masz tego sprzętu" else null
                // Tablet: wybrane ćwiczenie dostaje stopień wyżej; odstęp wokół każdego elementu jest stały, żeby
                // podświetlenie nie przesuwało siatki.
                val highlight = if (selectedId != null) {
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (exercise.id == selectedId) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                        .padding(TILE_SELECT_PAD)
                } else {
                    Modifier
                }
                if (state.tiles) {
                    ExerciseTile(
                        name = exercise.namePl,
                        secondary = tileSecondary(exercise),
                        imagePath = exercise.images.lastOrNull(),
                        warning = warning,
                        modifier = Modifier
                            .padding(top = if (selectedId != null) 4.dp else 8.dp, bottom = if (selectedId != null) 4.dp else 16.dp)
                            .then(highlight),
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
                        modifier = highlight,
                        onClick = { onOpenExercise(exercise.id) },
                    )
                }
            }
        }
    }
}

/**
 * Przycisk filtrów w polu szukania: lejek, a przy zaznaczeniach znaczek z liczbą w żarze (informacja, nie akcja).
 * Opis „Filtry” / „Filtry · n” czyta czytnik ekranu i skrypt przebiegu.
 */
@Composable
private fun FilterButton(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(end = 4.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                .semantics { stateDescription = if (expanded) "rozwinięte" else "zwinięte" },
        ) {
            Icon(
                imageVector = AppIcons.Filter,
                contentDescription = if (count == 0) "Filtry" else "Filtry · $count",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onTertiary,
                )
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

/**
 * Panel filtrów: „Tylko mój sprzęt”, cztery sekcje chipów (wartości w sekcji łączą się „lub”, sekcje „i”, reguła
 * w `LibraryFilter`) i wybór widoku. Wszystko, co kiedyś stało nad listą, mieszka teraz tutaj (D-52).
 */
@Composable
private fun FilterPanel(
    filter: LibraryFilter,
    facets: LibraryFacets,
    ownedEquipment: Set<Equipment>,
    tiles: Boolean,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Hairline()
        SettingRow(
            title = "Tylko mój sprzęt",
            description = ownedEquipment.takeIf { it.isNotEmpty() }?.joinToString { it.shortPl.lowercase() }
                ?: "bez sprzętu; zaznaczysz go w Ustawieniach",
            onClick = actions.onToggleOwned,
            trailing = { AppSwitch(checked = filter.onlyOwned, onCheckedChange = { actions.onToggleOwned() }) },
        )
        FilterSection("Sprzęt") {
            FilterPill(text = "Bez sprzętu", selected = filter.noEquipment, onClick = actions.onToggleNoEquipment)
            facets.equipment.forEach { item ->
                FilterPill(text = item.shortPl, selected = item in filter.equipment, onClick = { actions.onToggleEquipment(item) })
            }
        }
        FilterSection("Rodzaj") {
            facets.categories.forEach { category ->
                FilterPill(text = category.labelPl, selected = category in filter.categories, onClick = { actions.onToggleCategory(category) })
            }
        }
        FilterSection("Poziom") {
            facets.levels.forEach { level ->
                FilterPill(text = level.labelPl, selected = level in filter.levels, onClick = { actions.onToggleLevel(level) })
            }
        }
        FilterSection("Partie") {
            facets.muscles.forEach { muscle ->
                FilterPill(text = muscle.labelPl, selected = muscle in filter.muscles, onClick = { actions.onToggleMuscle(muscle) })
            }
        }
        Overline("Widok", modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        SegmentedText(options = listOf("Kafelki", "Lista"), selectedIndex = if (tiles) 0 else 1, onSelect = { actions.onTiles(it == 0) })
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

/** Szerokość listy obok szczegółu: dwie kolumny kafelków z czytelnymi nazwami. */
private val LIBRARY_PANE_WIDTH = 460.dp

/** Margines wokół kafelka w siatce z podświetleniem wyboru (tablet). */
private val TILE_SELECT_PAD = 8.dp

private const val DETAIL_PANE_KEY = "biblioteka-szczegol"
