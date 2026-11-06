package pl.mysttic.trainings.ui.library

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.coach.Coach
import pl.mysttic.trainings.domain.engine.Announcer
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.ui.adaptive.ReadableBox
import pl.mysttic.trainings.ui.common.AppSwitch
import pl.mysttic.trainings.ui.common.BackTopBar
import pl.mysttic.trainings.ui.common.ChartLegendRow
import pl.mysttic.trainings.ui.common.DisclosureRow
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.ExerciseImagePair
import pl.mysttic.trainings.ui.common.ExerciseRow
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.RecordLineChart
import pl.mysttic.trainings.ui.common.ScreenScaffold
import pl.mysttic.trainings.ui.common.SectionTitle
import pl.mysttic.trainings.ui.common.SettingRow
import pl.mysttic.trainings.ui.common.TagChip
import pl.mysttic.trainings.ui.theme.Dimens

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScreenScaffold { padding ->
        // Na tablecie w pionie kolumna 640 dp: ilustracja na pełną szerokość zajmowała cały pierwszy ekran i była rozmyta.
        ReadableBox(modifier = Modifier.padding(padding)) {
            ExerciseDetailContent(
                state = state,
                onBack = onBack,
                onOpenExercise = onOpenExercise,
                onExcludedChange = viewModel::setExcluded,
                onResetTuning = viewModel::resetTuning,
            )
        }
    }
}

/**
 * Szczegół ćwiczenia (D-52): ilustracja, nazwa, znaczniki partii i sprzętu oraz „Jak wykonać” widać od razu;
 * wyniki, warianty, zamienniki i trener czekają w zwijanych wierszach ze streszczeniem. Ta sama treść jest osobnym
 * ekranem na telefonie i panelem obok listy na tablecie — wtedy bez strzałki wstecz ([onBack] = null), a [wide]
 * stawia ilustrację obok nazwy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailContent(
    state: ExerciseDetailUiState,
    onBack: (() -> Unit)?,
    onOpenExercise: (String) -> Unit,
    onExcludedChange: (Boolean) -> Unit,
    onResetTuning: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    val exercise = state.exercise
    if (exercise == null) {
        Column(modifier = modifier.fillMaxSize()) {
            if (onBack != null) BackTopBar(title = null, onBack = onBack, modifier = Modifier.padding(horizontal = Dimens.screenPadding))
            if (!state.loading) EmptyState(if (onBack == null) "Wybierz ćwiczenie z listy." else "Nie znaleziono ćwiczenia.")
        }
        return
    }
    // Nowe ćwiczenie w panelu zaczyna od góry i ze zwiniętymi wierszami.
    val scroll = remember(exercise.id) { ScrollState(0) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = if (wide) 48.dp else Dimens.screenPadding, vertical = if (wide) 32.dp else 8.dp),
    ) {
        if (onBack != null) BackTopBar(title = null, onBack = onBack)
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                ExerciseImagePair(exercise, modifier = Modifier.widthIn(max = 340.dp))
                Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) { Heading(exercise, state) }
            }
        } else {
            ExerciseImagePair(exercise)
            Spacer(Modifier.height(Dimens.sectionGap + 2.dp))
            Heading(exercise, state)
        }
        Column(modifier = Modifier.widthIn(max = 640.dp)) {
            SectionTitle("Jak wykonać", modifier = Modifier.padding(top = 4.dp))
            exercise.instructionsPl.forEachIndexed { i, step ->
                Row(modifier = Modifier.padding(vertical = 5.dp)) {
                    Text(
                        text = "${i + 1}",
                        modifier = Modifier.padding(end = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(step, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(Dimens.sectionGap))
            Hairline()
            HiddenDetails(exercise, state, onOpenExercise, onExcludedChange, onResetTuning)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Nazwa, jedna linia metadanych, wycofanie z bazy i znaczniki: partie (główna mocniej) oraz sprzęt (brakujący na czerwono). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Heading(exercise: Exercise, state: ExerciseDetailUiState) {
    Text(exercise.namePl, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(6.dp))
    Text(
        text = "${exercise.level.labelPl} · ${exercise.category.labelPl} · " +
            (if (exercise.kind == ExerciseKind.TIME) "na czas" else "na powtórzenia"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Wycofane z bazy (D-47) otwiera się już tylko z historii i planów: mówimy wprost, czemu nie ma go w bibliotece.
    exercise.retiredAt?.let { date ->
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Wycofane z bazy ${plDate(date)}: zostaje w historii i planach, ale nie ma go " +
                "w bibliotece, kreatorze ani zamiennikach.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        exercise.primaryMuscles.forEach { TagChip(it.labelPl, strong = true) }
        exercise.secondaryMuscles.forEach { TagChip(it.labelPl) }
        if (exercise.equipment.isEmpty()) {
            TagChip("bez sprzętu", outlined = true)
        } else {
            exercise.equipment.forEach { eq ->
                val missing = eq !in state.ownedEquipment
                TagChip(if (missing) "${eq.labelPl} (brak)" else eq.labelPl, outlined = true, error = missing)
            }
        }
    }
}

/** Wszystko poza instrukcją: zwinięte, ze streszczeniem w jednej linii. Stan rozwinięcia przetrwa obrót ekranu. */
@Composable
private fun HiddenDetails(
    exercise: Exercise,
    state: ExerciseDetailUiState,
    onOpenExercise: (String) -> Unit,
    onExcludedChange: (Boolean) -> Unit,
    onResetTuning: () -> Unit,
) {
    var resultsOpen by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var variantsOpen by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var substitutesOpen by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var coachOpen by rememberSaveable(exercise.id) { mutableStateOf(false) }

    val best = state.personalBest
    val unit = if (best?.targetKind == TargetKind.SECONDS) "s" else "powt."
    DisclosureRow(
        title = "Twoje wyniki",
        summary = if (best == null) {
            "jeszcze nie ćwiczone"
        } else {
            "rekord ${best.best} $unit · ${state.history.size} " +
                Announcer.plural(state.history.size, "wykonanie", "wykonania", "wykonań")
        },
        expanded = resultsOpen,
        onToggle = { resultsOpen = !resultsOpen },
    ) {
        RecordLineChart(state.history)
        if (best != null) {
            ChartLegendRow("Rekord w serii", "${best.best} $unit")
            state.history.lastOrNull()?.let { last -> ChartLegendRow("Ostatnio", "${last.best} $unit") }
            ChartLegendRow("Wykonań", "${state.history.size}")
        }
    }

    val easier = state.easier
    val harder = state.harder
    if (easier != null || harder != null) {
        DisclosureRow(
            title = "Łatwiej i trudniej",
            summary = listOfNotNull(easier?.namePl, harder?.namePl).joinToString(" · "),
            expanded = variantsOpen,
            onToggle = { variantsOpen = !variantsOpen },
        ) {
            easier?.let { e -> VariantRow(e, "łatwiej", onOpenExercise) }
            harder?.let { h -> VariantRow(h, "trudniej", onOpenExercise) }
        }
    }

    if (state.missingEquipment.isNotEmpty()) {
        DisclosureRow(
            title = "Zamienniki",
            summary = "Brakuje: " + state.missingEquipment.joinToString { it.labelPl } + " · " +
                if (state.substitutes.isEmpty()) "brak zamiennika" else "${state.substitutes.size} z twoim sprzętem",
            expanded = substitutesOpen,
            onToggle = { substitutesOpen = !substitutesOpen },
        ) {
            if (state.substitutes.isEmpty()) {
                Text(
                    text = "Brak zamiennika na tę grupę mięśni z twoim sprzętem.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.substitutes.forEach { s -> VariantRow(s, s.primaryMuscles.joinToString { it.labelPl }, onOpenExercise) }
            }
        }
    }

    val tuning = state.tuning
    val excluded = tuning?.excluded == true
    val correction = Coach.deltaLabel(tuning, exercise.defaultTargetKind)
    DisclosureRow(
        title = "Trener",
        summary = when {
            excluded -> "wykluczone z planów"
            correction != null -> "korekta celu: $correction"
            else -> "bez korekty · proponowane w planach"
        },
        expanded = coachOpen,
        onToggle = { coachOpen = !coachOpen },
    ) {
        SettingRow(
            title = "Nie proponuj w planach",
            description = if (excluded) {
                "Wykluczone: w aktywnym cyklu dostało zamiennik, kolejne plany je pominą."
            } else {
                "Wyklucza z kreatora i od razu podmienia w aktywnym cyklu."
            },
            onClick = { onExcludedChange(!excluded) },
            trailing = { AppSwitch(checked = excluded, onCheckedChange = onExcludedChange) },
        )
        if (correction != null) {
            SettingRow(
                title = "Korekta celu: $correction",
                description = "Z opinii w podsumowaniach; działa na każdą kolejną serię tego ćwiczenia.",
                value = "Wyzeruj",
                onClick = onResetTuning,
            )
        } else {
            Text(
                text = "Bez korekty. Po treningu powiedz „Więcej” albo „Mniej”, a cel przesunie się w kolejnych seriach.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Wiersz innego ćwiczenia (wariant, zamiennik) z miniaturą; tapnięcie otwiera jego szczegół. */
@Composable
private fun VariantRow(exercise: Exercise, caption: String, onOpenExercise: (String) -> Unit) {
    ExerciseRow(
        name = exercise.namePl,
        secondary = caption,
        imagePath = exercise.images.lastOrNull(),
        onClick = { onOpenExercise(exercise.id) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Data z bazy (`RRRR-MM-DD`) tak, jak czyta się ją po polsku: 28.10.2026. */
private fun plDate(iso: String): String = iso.split("-").reversed().joinToString(".")
