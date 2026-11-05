package pl.mysttic.trainings.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.plan.Goal
import pl.mysttic.trainings.domain.plan.ProgressionStep
import pl.mysttic.trainings.domain.plan.TrainingProfile
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
import pl.mysttic.trainings.ui.theme.Dimens
import kotlin.math.roundToInt

private const val STEPS = 4

/**
 * Kreator w czterech krokach (D-32): jedno pytanie na ekran, stopka pokazuje na bieżąco, co z tego wyjdzie.
 * Sam profil i generowanie zostają w [PlanWizardViewModel] — tutaj zmienia się wyłącznie sposób pytania.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanWizardScreen(
    onBack: () -> Unit,
    onGenerated: () -> Unit,
    viewModel: PlanWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val profile = state.profile
    // rememberSaveable: obrót ekranu nie cofa kreatora do kroku 1.
    var step by rememberSaveable { mutableIntStateOf(1) }

    LaunchedEffect(state.saved) { if (state.saved) onGenerated() }
    BackHandler { if (step > 1) step-- else onBack() }

    // Niskie okno (telefon w poziomie): podgląd schodzi ze stopki do przewijanej treści, stopka to same przyciski.
    val lowWindow = LocalAppLayout.current.heightDp < AppLayout.LOW_HEIGHT
    val preview = "Wychodzi ${state.sessionsInCycle} ${sessionsWord(state.sessionsInCycle)} w ${profile.cycleDays} dni, " +
        "${profile.daysPerWeek} razy w tygodniu po ${profile.sessionMinutes} min."

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Na miejscu zostaje tylko pasek kroków. Pytanie przewija się razem z krokiem, żeby w niskim oknie
        // nagłówek nie zjadał wysokości, a na tablecie całość stoi w czytelnej szerokości (D-42).
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
            label = "krok kreatora",
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
            ) {
                StepHeader(
                    step = current,
                    total = STEPS,
                    question = questionFor(current, state.isNextCycle),
                    explanation = explanationFor(current, state.isNextCycle),
                )
                // Nowy cykl wstrzymuje bieżący plan (D-42): uprzedzamy na starcie i na kroku z przyciskiem, który to robi.
                state.pausing?.takeIf { current == 1 || current == STEPS }?.let { PausingNote(it) }
                when (current) {
                    1 -> {
                        ValueStepper(
                            label = "Wzrost",
                            value = "${profile.heightCm} cm",
                            fraction = fraction(profile.heightCm.toFloat(), HEIGHT_MIN, HEIGHT_MAX),
                            onLess = { viewModel.setHeight(profile.heightCm - 1) },
                            onMore = { viewModel.setHeight(profile.heightCm + 1) },
                            onFraction = { viewModel.setHeight(valueAt(it, HEIGHT_MIN, HEIGHT_MAX)) },
                            hint = "Przeciągnij po pasku albo przytrzymaj − lub +.",
                        )
                        ValueStepper(
                            label = "Waga",
                            value = "${formatKg(profile.weightKg)} kg",
                            fraction = fraction(profile.weightKg.toFloat(), WEIGHT_MIN, WEIGHT_MAX),
                            onLess = { viewModel.setWeight(profile.weightKg - 1) },
                            onMore = { viewModel.setWeight(profile.weightKg + 1) },
                            onFraction = { viewModel.setWeight(valueAt(it, WEIGHT_MIN, WEIGHT_MAX).toDouble()) },
                            hint = "BMI ${profile.bmiRounded}" +
                                if (profile.avoidJumping) " — plan ominie skoki, żeby oszczędzić stawy." else "",
                        )
                        Column {
                            Overline("Poziom")
                            Spacer(Modifier.height(8.dp))
                            DifficultySelector(selected = profile.level, onSelect = viewModel::setLevel, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    2 -> {
                        state.review?.let {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Overline("Jak poszedł poprzedni cykl")
                                Text(state.explanation.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ProgressionStep.entries.forEach { progression ->
                                        FilterPill(
                                            text = progression.labelPl,
                                            selected = state.step == progression,
                                            onClick = { viewModel.setStep(progression) },
                                        )
                                    }
                                }
                                Text(
                                    text = "Nowy cykl: ${state.scaleLabel}. Czas treningu zostaje bez zmian — " +
                                        "trudniej znaczy więcej pracy w tych samych minutach.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Goal.entries.forEach { goal ->
                            ChoiceCard(
                                title = goal.labelPl,
                                description = goal.descriptionPl,
                                selected = profile.goal == goal,
                                onClick = { viewModel.setGoal(goal) },
                            )
                        }
                    }
                    3 -> {
                        Column {
                            Overline("Dni w tygodniu")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TrainingProfile.DAYS_RANGE.forEach { days ->
                                    FilterPill(
                                        text = days.toString(),
                                        selected = profile.daysPerWeek == days,
                                        onClick = { viewModel.setDaysPerWeek(days) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        val minutesRange = TrainingProfile.minutesRange(profile.level)
                        ValueStepper(
                            label = "Długość treningu",
                            value = "${profile.sessionMinutes} min",
                            fraction = fraction(
                                profile.sessionMinutes.toFloat(),
                                minutesRange.first.toFloat(),
                                minutesRange.last.toFloat(),
                            ),
                            onLess = { viewModel.setSessionMinutes(profile.sessionMinutes - 5) },
                            onMore = { viewModel.setSessionMinutes(profile.sessionMinutes + 5) },
                            onFraction = {
                                val minutes = valueAt(it, minutesRange.first.toFloat(), minutesRange.last.toFloat())
                                viewModel.setSessionMinutes((minutes + 2) / 5 * 5)
                            },
                            hint = "Plan trafia w ten czas z dokładnością do minuty. " +
                                "${profile.level.labelPl}: od ${minutesRange.first} do ${minutesRange.last} min, " +
                                "bo rundy i serie trzymają się poziomu.",
                        )
                        Column {
                            Overline("Długość cyklu")
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TrainingProfile.CYCLE_DAYS_OPTIONS.forEach { days ->
                                    FilterPill(
                                        text = "$days dni",
                                        selected = profile.cycleDays == days,
                                        onClick = { viewModel.setCycleDays(days) },
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Column {
                            // Ergometrów kreator nie używa (D-44), więc ich tu nie pokazujemy; zaznaczone w Ustawieniach zostają.
                            Equipment.entries.filterNot { it.ergometer }.forEach { equipment ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleEquipment(equipment) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = equipment in profile.equipment,
                                        onCheckedChange = { viewModel.toggleEquipment(equipment) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary,
                                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                            uncheckedColor = MaterialTheme.colorScheme.outline,
                                        ),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(equipment.labelPl, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        state.error?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (lowWindow) {
                    Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(Dimens.sectionGap))
            }
        }

        WizardFooter(
            step = step,
            preview = preview.takeUnless { lowWindow },
            generating = state.generating,
            isNextCycle = state.isNextCycle,
            onBack = { if (step > 1) step-- else onBack() },
            onNext = { if (step < STEPS) step++ else viewModel.generate() },
        )
    }
}

/** Numer kroku, pytanie i objaśnienie: część przewijanej treści kroku, a nie stały nagłówek. */
@Composable
internal fun StepHeader(step: Int, total: Int, question: String, explanation: String) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Overline("Krok $step z $total")
        Spacer(Modifier.height(8.dp))
        Text(question, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Uprzedzenie, że start nowego planu wstrzyma bieżący (D-42), jak w katalogu gotowych programów. */
@Composable
internal fun PausingNote(name: String) {
    InfoCard {
        Text(
            text = "„$name” zostanie wstrzymany z postępem. Wrócisz do niego z listy planów.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WizardFooter(
    step: Int,
    preview: String?,
    generating: Boolean,
    isNextCycle: Boolean,
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
            if (generating) {
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
                        text = nextLabel(step, isNextCycle),
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun sessionsWord(n: Int): String = when {
    n == 1 -> "trening"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "treningi"
    else -> "treningów"
}

private const val HEIGHT_MIN = 140f
private const val HEIGHT_MAX = 210f
private const val WEIGHT_MIN = 40f
private const val WEIGHT_MAX = 150f

private fun fraction(value: Float, min: Float, max: Float): Float = ((value - min) / (max - min)).coerceIn(0f, 1f)

/** Odwrotność [fraction]: wartość z pozycji na pasku, do pełnej jednostki. */
private fun valueAt(fraction: Float, min: Float, max: Float): Int = (min + (max - min) * fraction.coerceIn(0f, 1f)).roundToInt()

/** Waga w pełnych kilogramach; ułamek pokazujemy tylko wtedy, gdy został po starszym profilu. */
private fun formatKg(kg: Double): String {
    val tenths = (kg * 10).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else (tenths / 10.0).toString()
}

private fun questionFor(step: Int, isNextCycle: Boolean): String = when (step) {
    1 -> "Od czego zaczynamy"
    2 -> if (isNextCycle) "Po co ten cykl" else "Po co ten plan"
    3 -> "Ile czasu masz"
    else -> "Czym dysponujesz"
}

private fun explanationFor(step: Int, isNextCycle: Boolean): String = when (step) {
    1 -> if (isNextCycle) {
        "Zaczynamy od ustawień poprzedniego cyklu. Popraw wagę, jeśli się zmieniła."
    } else {
        "Wzrost i waga wpływają na dobór ćwiczeń, poziom na liczbę powtórzeń, rund i długość przerw."
    }
    2 -> "Cel decyduje o tym, jak długie są serie i przerwy oraz ile jest kardio."
    3 -> "Plan dopasuje liczbę ćwiczeń tak, żeby trafić w zadeklarowany czas."
    else -> "Plan użyje tylko tego, co zaznaczysz. Bez zaznaczenia ułoży trening z masy własnej ciała."
}

private fun nextLabel(step: Int, isNextCycle: Boolean): String = when (step) {
    1 -> "Dalej: cel"
    2 -> "Dalej: czas"
    3 -> "Dalej: sprzęt"
    else -> if (isNextCycle) "Wygeneruj kolejny cykl" else "Ułóż plan"
}
