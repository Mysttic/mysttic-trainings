package pl.mysttic.trainings.ui.presets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.plan.preset.PresetId
import pl.mysttic.trainings.ui.adaptive.AppLayout
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.adaptive.readableWidth
import pl.mysttic.trainings.ui.common.ChoiceCard
import pl.mysttic.trainings.ui.common.DifficultySelector
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.InfoCard
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.StepProgress
import pl.mysttic.trainings.ui.common.ValueStepper
import pl.mysttic.trainings.ui.plan.PausingNote
import pl.mysttic.trainings.ui.plan.StepHeader
import pl.mysttic.trainings.ui.theme.Dimens
import kotlin.math.roundToInt

private const val STEPS = 2

/**
 * Gotowe programy (D-37) w oprawie kreatora (D-32): dwa kroki, jedno pytanie na ekran, stopka na bieżąco mówi,
 * co z tego wyjdzie. Krok 1 to lista programów, krok 2 długość, poziom i ewentualny parametr.
 * Aktywny plan w trakcie nowy program wstrzymuje (D-42): oba kroki o tym uprzedzają.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetsScreen(
    onBack: () -> Unit,
    onWizard: () -> Unit,
    onStarted: () -> Unit,
    viewModel: PresetsViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    // rememberSaveable: obrót ekranu nie cofa wyboru programu do kroku 1.
    var step by rememberSaveable { mutableIntStateOf(1) }
    val selected = state.selectedRow

    LaunchedEffect(state.started) { if (state.started) onStarted() }
    BackHandler { if (step > 1) step-- else onBack() }

    // Niskie okno (telefon w poziomie): podgląd schodzi ze stopki do przewijanej treści, stopka to przyciski.
    val lowWindow = LocalAppLayout.current.heightDp < AppLayout.LOW_HEIGHT
    val preview = previewText(state)

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Jak w kreatorze: na miejscu tylko pasek kroków, pytanie przewija się z krokiem, całość w czytelnej szerokości.
        StepProgress(
            step = step,
            total = STEPS,
            modifier = Modifier
                .readableWidth()
                .padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.sectionGap),
        )

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).readableWidth(),
            transitionSpec = {
                val forward = targetState > initialState
                val width = if (forward) 1 else -1
                (slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { it * width })
                    .togetherWith(slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it * width })
            },
            label = "krok programu",
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
            ) {
                StepHeader(step = current, total = STEPS, question = questionFor(current), explanation = explanationFor(current))
                // Uprzedzenie o wstrzymaniu stoi też na kroku z „Zacznij program”, który je wywołuje (D-42).
                state.pausing?.let { PausingNote(it) }
                if (current == 1) {
                    ProgramList(state = state, onSelect = viewModel::select, onWizard = onWizard)
                } else if (selected != null) {
                    ProgramSetup(state = state, row = selected, viewModel = viewModel)
                }
                if (lowWindow) {
                    Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(Dimens.sectionGap))
            }
        }

        PresetsFooter(
            step = step,
            preview = preview.takeUnless { lowWindow },
            error = state.error,
            starting = state.starting,
            nextEnabled = selected != null && !state.loading,
            onBack = { if (step > 1) step-- else onBack() },
            onNext = { if (step < STEPS) step++ else viewModel.start() },
        )
    }
}

/**
 * Krok 1: karty programów od łagodnych do wymagających, z trudnością na karcie (D-46). Program bez ćwiczeń
 * w bibliotece albo bez niezastępowalnego sprzętu zostaje na liście, ale wyszarzony, z powodem i bez kliku.
 */
@Composable
private fun ProgramList(state: PresetsUiState, onSelect: (PresetId) -> Unit, onWizard: () -> Unit) {
    state.rows.forEach { row ->
        val intensity = "Trudność: ${row.intensity.labelPl}"
        if (row.available) {
            ChoiceCard(
                title = row.name,
                description = row.tagline,
                selected = state.selected == row.id,
                onClick = { onSelect(row.id) },
                meta = intensity,
            )
        } else {
            InfoCard(modifier = Modifier.alpha(0.55f)) {
                Text(row.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(row.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(intensity, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                Text(
                    text = if (row.missingExercises.isNotEmpty()) {
                        "Czeka na ćwiczenia w bibliotece"
                    } else {
                        "Wymaga: ${row.unmetEquipment.joinToString { it.labelPl.lowercase() }}. Masz? Zaznacz w Ustawieniach, w „Mój sprzęt”."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
    TextButton(onClick = onWizard, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Wolisz własny plan? Ułóż plan",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Krok 2: opis i ostrzeżenie programu, sprzęt, długość cyklu, poziom i parametr, o który program pyta. */
@Composable
private fun ProgramSetup(state: PresetsUiState, row: PresetRow, viewModel: PresetsViewModel) {
    Text(row.description, style = MaterialTheme.typography.bodyLarge)
    row.warning?.let { warning ->
        InfoCard {
            Text(warning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Sprzęt: " + if (row.equipment.isEmpty()) "bez sprzętu" else row.equipment.joinToString { it.labelPl.lowercase() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (row.missingEquipment.isNotEmpty()) {
            Text(
                text = "Nie masz: " + row.missingEquipment.joinToString { it.labelPl.lowercase() } +
                    ". W treningach będą zamienniki bez sprzętu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
    Column {
        Overline("Długość")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.lengthOptions.forEach { days ->
                FilterPill(
                    text = "$days dni",
                    selected = state.cycleDays == days,
                    onClick = { viewModel.setCycleDays(days) },
                )
            }
        }
    }
    Column {
        Overline("Poziom")
        Spacer(Modifier.height(8.dp))
        DifficultySelector(selected = state.level, onSelect = viewModel::setLevel, modifier = Modifier.fillMaxWidth())
    }
    row.asks?.let { asks ->
        val param = state.param ?: asks.default
        val min = asks.range.first.toFloat()
        val max = asks.range.last.toFloat()
        ValueStepper(
            label = asks.labelPl,
            value = "$param ${asks.unitPl}",
            fraction = fraction(param.toFloat(), min, max),
            onLess = { viewModel.setParam(param - 1) },
            onMore = { viewModel.setParam(param + 1) },
            onFraction = { viewModel.setParam(valueAt(it, min, max)) },
            hint = "Przeciągnij po pasku albo przytrzymaj − lub +.",
        )
    }
}

@Composable
private fun PresetsFooter(
    step: Int,
    preview: String?,
    error: String?,
    starting: Boolean,
    nextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .readableWidth()
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.sectionGap),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            preview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (starting) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryAction(
                        text = "Wstecz",
                        onClick = onBack,
                        modifier = Modifier.width(96.dp),
                        height = Dimens.primaryButton,
                    )
                    PrimaryAction(
                        text = if (step < STEPS) "Dalej" else "Zacznij program",
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                        enabled = nextEnabled,
                    )
                }
            }
        }
    }
}

/** Zdanie w stopce: liczba sesji, rytm i czas etapów, liczone z tych samych tabel, z których powstanie cykl. */
private fun previewText(state: PresetsUiState): String {
    val row = state.selectedRow
    val preview = state.preview
    return when {
        row == null || preview == null -> "Wybierz program. Długość i poziom ustawisz w następnym kroku."
        else -> "Wychodzi ${preview.sessions} ${sessionsWord(preview.sessions)} w ${state.cycleDays} dni, ${row.patternLabel}, " +
            if (preview.firstMinutes == preview.lastMinutes) {
                "po ok. ${preview.firstMinutes} min."
            } else {
                "od ${preview.firstMinutes} do ${preview.lastMinutes} min."
            }
    }
}

private fun questionFor(step: Int): String = if (step == 1) "Który program" else "Ile i jak mocno"

private fun explanationFor(step: Int): String = if (step == 1) {
    "Sprawdzone protokoły z własnym rytmem i progresją. Po wyborze zostaje tylko długość i poziom."
} else {
    "Poziom ustawia tabele etapów, długość liczbę sesji. W trakcie programu cele są stałe, trener ich nie zmienia."
}

private fun sessionsWord(n: Int): String = when {
    n == 1 -> "trening"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "treningi"
    else -> "treningów"
}

private fun fraction(value: Float, min: Float, max: Float): Float = ((value - min) / (max - min)).coerceIn(0f, 1f)

/** Odwrotność [fraction]: wartość z pozycji na pasku, do pełnej jednostki. */
private fun valueAt(fraction: Float, min: Float, max: Float): Int = (min + (max - min) * fraction.coerceIn(0f, 1f)).roundToInt()
