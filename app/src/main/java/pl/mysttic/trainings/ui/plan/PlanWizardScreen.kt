package pl.mysttic.trainings.ui.plan

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.plan.Goal
import pl.mysttic.trainings.domain.plan.ProgressionStep
import pl.mysttic.trainings.domain.plan.TrainingProfile
import pl.mysttic.trainings.ui.adaptive.AppLayout
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.adaptive.readableWidth
import pl.mysttic.trainings.ui.common.AppIcons
import pl.mysttic.trainings.ui.common.CategoryDivider
import pl.mysttic.trainings.ui.common.CategoryGroup
import pl.mysttic.trainings.ui.common.CategoryRow
import pl.mysttic.trainings.ui.common.ChoiceCard
import pl.mysttic.trainings.ui.common.DifficultySelector
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.InfoCard
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.StepProgress
import pl.mysttic.trainings.ui.common.ValueStepper
import pl.mysttic.trainings.ui.settings.SettingsSummary
import pl.mysttic.trainings.ui.theme.Dimens
import kotlin.math.roundToInt

private const val STEPS = 4

/**
 * Kreator w czterech krokach (D-32) w języku nowego interfejsu (D-54, za D-52): jedno pytanie na ekran, zamknięcie
 * z każdego kroku, a na ostatnim kroku streszczenie odpowiedzi, z którego wraca się do kroku jednym tapnięciem.
 * Od D-57 kolejność to sprzęt („Mój sprzęt”), sylwetka i poziom (wzrost i waga z „O mnie”), cel, czas. Stopka pokazuje
 * na bieżąco, co z tego wyjdzie. Na tablecie w poziomie pytanie stoi po lewej, odpowiedzi po prawej. Profil
 * i generowanie zostają w [PlanWizardViewModel].
 */
@Composable
fun PlanWizardScreen(
    onBack: () -> Unit,
    onGenerated: () -> Unit,
    viewModel: PlanWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    // rememberSaveable: obrót ekranu nie cofa kreatora do kroku 1.
    var step by rememberSaveable { mutableIntStateOf(1) }

    LaunchedEffect(state.saved) { if (state.saved) onGenerated() }
    BackHandler { if (step > 1) step-- else onBack() }

    val actions = remember(viewModel) {
        WizardActions(
            onHeight = viewModel::setHeight,
            onWeight = viewModel::setWeight,
            onLevel = viewModel::setLevel,
            onGoal = viewModel::setGoal,
            onProgression = viewModel::setStep,
            onDaysPerWeek = viewModel::setDaysPerWeek,
            onSessionMinutes = viewModel::setSessionMinutes,
            onCycleDays = viewModel::setCycleDays,
            onToggleEquipment = viewModel::toggleEquipment,
            onGenerate = viewModel::generate,
        )
    }
    PlanWizardContent(state = state, step = step, onStep = { step = it }, actions = actions, onClose = onBack)
}

/** Odpowiedzi kreatora; treść jest bezstanowa, więc zrzut rysuje każdy krok z fiksturą. */
class WizardActions(
    val onHeight: (Int) -> Unit = {},
    val onWeight: (Double) -> Unit = {},
    val onLevel: (Difficulty) -> Unit = {},
    val onGoal: (Goal) -> Unit = {},
    val onProgression: (ProgressionStep) -> Unit = {},
    val onDaysPerWeek: (Int) -> Unit = {},
    val onSessionMinutes: (Int) -> Unit = {},
    val onCycleDays: (Int) -> Unit = {},
    val onToggleEquipment: (Equipment) -> Unit = {},
    val onGenerate: () -> Unit = {},
)

@Composable
fun PlanWizardContent(
    state: WizardUiState,
    step: Int,
    onStep: (Int) -> Unit,
    actions: WizardActions,
    onClose: () -> Unit,
) {
    val profile = state.profile
    val layout = LocalAppLayout.current
    val wide = layout.twoPane
    // Niskie okno (telefon w poziomie): podgląd schodzi ze stopki do przewijanej treści, stopka to same przyciski.
    val lowWindow = layout.heightDp < AppLayout.LOW_HEIGHT
    val preview = "Wychodzi ${state.sessionsInCycle} ${sessionsWord(state.sessionsInCycle)} w ${profile.cycleDays} dni, " +
        "${profile.daysPerWeek} razy w tygodniu po ${profile.sessionMinutes} min."
    val width: Modifier = if (wide) Modifier.widthIn(max = WIDE_CONTENT_MAX).fillMaxWidth() else Modifier.readableWidth()

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Na miejscu zostaje tylko zamknięcie i pasek kroków; pytanie przewija się razem z krokiem.
        Row(
            modifier = width.padding(start = Dimens.screenPadding - 12.dp, end = Dimens.screenPadding, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(AppIcons.Close, contentDescription = "Zamknij kreator", tint = MaterialTheme.colorScheme.onSurface)
            }
            StepProgress(step = step, total = STEPS, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).then(width),
            transitionSpec = {
                val forward = targetState > initialState
                val direction = if (forward) 1 else -1
                (slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { it * direction })
                    .togetherWith(slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it * direction })
            },
            label = "krok kreatora",
        ) { current ->
            val header: @Composable () -> Unit = {
                StepHeader(
                    step = current,
                    total = STEPS,
                    question = questionFor(current, state.isNextCycle),
                    explanation = explanationFor(current, state.isNextCycle),
                )
                // Nowy cykl wstrzymuje bieżący plan (D-42): uprzedzamy na starcie i na kroku z przyciskiem, który to robi.
                state.pausing?.takeIf { current == 1 || current == STEPS }?.let { PausingNote(it) }
            }
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
                    ) {
                        header()
                        if (current == STEPS) WizardSummary(state, onStep)
                    }
                    Column(
                        modifier = Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
                    ) {
                        StepControls(current, state, actions)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
                ) {
                    header()
                    StepControls(current, state, actions)
                    if (current == STEPS) WizardSummary(state, onStep)
                    if (lowWindow) {
                        Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(Dimens.sectionGap))
                }
            }
        }

        WizardFooter(
            step = step,
            preview = preview.takeUnless { lowWindow },
            generating = state.generating,
            isNextCycle = state.isNextCycle,
            wide = wide,
            onBack = { if (step > 1) onStep(step - 1) else onClose() },
            onNext = { if (step < STEPS) onStep(step + 1) else actions.onGenerate() },
        )
    }
}

/**
 * Odpowiedzi jednego kroku: pigułki, suwaki i karty wyboru. Od D-57 najpierw sprzęt: to on wyznacza, z czego plan
 * w ogóle da się ułożyć, więc kreator pyta o niego na starcie, a licznik od razu mówi, ile ćwiczeń zostaje.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepControls(step: Int, state: WizardUiState, actions: WizardActions) {
    val profile = state.profile
    when (step) {
        1 -> {
            // Pigułki jak w panelu filtrów biblioteki (D-52). Ergometrów kreator nie używa (D-44), więc ich tu nie ma;
            // zaznaczone w Ustawieniach zostają.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Overline("Sprzęt")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Equipment.entries.filterNot { it.ergometer }.forEach { equipment ->
                        FilterPill(
                            text = equipment.labelPl,
                            selected = equipment in profile.equipment,
                            onClick = { actions.onToggleEquipment(equipment) },
                        )
                    }
                }
                Text(
                    text = candidatesLine(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        2 -> {
            ValueStepper(
                label = "Wzrost",
                value = "${profile.heightCm} cm",
                fraction = fraction(profile.heightCm.toFloat(), HEIGHT_MIN, HEIGHT_MAX),
                onLess = { actions.onHeight(profile.heightCm - 1) },
                onMore = { actions.onHeight(profile.heightCm + 1) },
                onFraction = { actions.onHeight(valueAt(it, HEIGHT_MIN, HEIGHT_MAX)) },
                hint = "Przeciągnij po pasku albo przytrzymaj − lub +.",
            )
            ValueStepper(
                label = "Waga",
                value = "${formatKg(profile.weightKg)} kg",
                fraction = fraction(profile.weightKg.toFloat(), WEIGHT_MIN, WEIGHT_MAX),
                onLess = { actions.onWeight(profile.weightKg - 1) },
                onMore = { actions.onWeight(profile.weightKg + 1) },
                onFraction = { actions.onWeight(valueAt(it, WEIGHT_MIN, WEIGHT_MAX).toDouble()) },
                hint = "BMI ${profile.bmiRounded}" + if (profile.avoidJumping) ". Plan ominie skoki, żeby oszczędzić stawy." else "",
            )
            Column {
                Overline("Poziom")
                Spacer(Modifier.height(8.dp))
                DifficultySelector(selected = profile.level, onSelect = actions.onLevel, modifier = Modifier.fillMaxWidth())
            }
        }
        3 -> {
            state.review?.let {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Overline("Jak poszedł poprzedni cykl")
                    Text(state.explanation.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgressionStep.entries.forEach { progression ->
                            FilterPill(
                                text = progression.labelPl,
                                selected = state.step == progression,
                                onClick = { actions.onProgression(progression) },
                            )
                        }
                    }
                    Text(
                        text = "Nowy cykl: ${state.scaleLabel}. Czas treningu zostaje bez zmian: " +
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
                    onClick = { actions.onGoal(goal) },
                )
            }
        }
        else -> {
            Column {
                Overline("Dni w tygodniu")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrainingProfile.DAYS_RANGE.forEach { days ->
                        FilterPill(
                            text = days.toString(),
                            selected = profile.daysPerWeek == days,
                            onClick = { actions.onDaysPerWeek(days) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            val minutesRange = TrainingProfile.minutesRange(profile.level)
            ValueStepper(
                label = "Długość treningu",
                value = "${profile.sessionMinutes} min",
                fraction = fraction(profile.sessionMinutes.toFloat(), minutesRange.first.toFloat(), minutesRange.last.toFloat()),
                onLess = { actions.onSessionMinutes(profile.sessionMinutes - 5) },
                onMore = { actions.onSessionMinutes(profile.sessionMinutes + 5) },
                onFraction = {
                    val minutes = valueAt(it, minutesRange.first.toFloat(), minutesRange.last.toFloat())
                    actions.onSessionMinutes((minutes + 2) / 5 * 5)
                },
                hint = "Plan trafia w ten czas z dokładnością do minuty. " +
                    "${profile.level.labelPl}: od ${minutesRange.first} do ${minutesRange.last} min, " +
                    "bo rundy i serie trzymają się poziomu.",
            )
            Column {
                Overline("Długość cyklu")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrainingProfile.CYCLE_DAYS_OPTIONS.forEach { days ->
                        FilterPill(
                            text = "$days dni",
                            selected = profile.cycleDays == days,
                            onClick = { actions.onCycleDays(days) },
                        )
                    }
                }
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** „Z tym sprzętem plan ułożę z 142 ćwiczeń (bez sprzętu: 98).” — licznik z reguł generatora (D-57). */
private fun candidatesLine(state: WizardUiState): String {
    val all = state.candidates ?: return "Plan użyje tylko tego, co zaznaczysz."
    val bodyweight = state.bodyweightCandidates ?: all
    return if (state.profile.equipment.isEmpty()) {
        "Bez sprzętu plan ułożę z $all ${exercisesWord(all)} z masą własnego ciała."
    } else {
        "Z tym sprzętem plan ułożę z $all ${exercisesWord(all)} (bez sprzętu: $bodyweight). Ćwiczeń na sprzęt, " +
            "którego nie zaznaczysz, w planie nie będzie."
    }
}

/** Dopełniacz po liczbie: „z 1 ćwiczenia”, „z 98 ćwiczeń”. */
private fun exercisesWord(n: Int): String = if (n == 1) "ćwiczenia" else "ćwiczeń"

/** Streszczenie odpowiedzi na ostatnim kroku: każdy wiersz wraca do swojego kroku, nic nie trzeba klikać wstecz. */
@Composable
private fun WizardSummary(state: WizardUiState, onStep: (Int) -> Unit) {
    val profile = state.profile
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline("Twoje odpowiedzi")
        CategoryGroup {
            CategoryRow(
                icon = AppIcons.Exercises,
                title = "Sprzęt",
                summary = SettingsSummary.equipment(profile.equipment.filterNot { it.ergometer }.toSet()),
                onClick = { onStep(1) },
            )
            CategoryDivider()
            CategoryRow(
                icon = AppIcons.Person,
                title = "Sylwetka i poziom",
                summary = "${profile.heightCm} cm · ${formatKg(profile.weightKg)} kg · poziom ${profile.level.level}",
                onClick = { onStep(2) },
            )
            CategoryDivider()
            CategoryRow(icon = AppIcons.Target, title = "Cel", summary = profile.goal.labelPl, onClick = { onStep(3) })
        }
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
    wide: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .then(if (wide) Modifier.widthIn(max = WIDE_CONTENT_MAX).fillMaxWidth() else Modifier.readableWidth())
                .padding(horizontal = if (wide) 32.dp else Dimens.screenPadding, vertical = Dimens.sectionGap),
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

/** Szerokość kreatora w dwóch kolumnach na tablecie, jak „Dziś” w poziomie (D-52). */
private val WIDE_CONTENT_MAX = 1100.dp

private fun fraction(value: Float, min: Float, max: Float): Float = ((value - min) / (max - min)).coerceIn(0f, 1f)

/** Odwrotność [fraction]: wartość z pozycji na pasku, do pełnej jednostki. */
private fun valueAt(fraction: Float, min: Float, max: Float): Int = (min + (max - min) * fraction.coerceIn(0f, 1f)).roundToInt()

/** Waga w pełnych kilogramach; ułamek pokazujemy tylko wtedy, gdy został po starszym profilu. */
private fun formatKg(kg: Double): String {
    val tenths = (kg * 10).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else (tenths / 10.0).toString()
}

private fun questionFor(step: Int, isNextCycle: Boolean): String = when (step) {
    1 -> "Czym dysponujesz"
    2 -> "Od czego zaczynamy"
    3 -> if (isNextCycle) "Po co ten cykl" else "Po co ten plan"
    else -> "Ile czasu masz"
}

private fun explanationFor(step: Int, isNextCycle: Boolean): String = when (step) {
    1 -> "Zaznacz sprzęt, który masz. Kreator weźmie tylko ćwiczenia, które z nim zrobisz; bez zaznaczenia ułoży " +
        "trening z masy własnego ciała. To ta sama lista co „Mój sprzęt” w Ustawieniach."
    2 -> if (isNextCycle) {
        "Wzrost i waga z „O mnie”. Popraw wagę, jeśli się zmieniła; kreator zapisze ją z powrotem."
    } else {
        "Wzrost i waga z „O mnie” wpływają na dobór ćwiczeń, poziom na liczbę powtórzeń, rund i długość przerw."
    }
    3 -> "Cel decyduje o tym, jak długie są serie i przerwy oraz ile jest kardio."
    else -> "Plan dopasuje liczbę ćwiczeń tak, żeby trafić w zadeklarowany czas."
}

private fun nextLabel(step: Int, isNextCycle: Boolean): String = when (step) {
    1 -> "Dalej: sylwetka"
    2 -> "Dalej: cel"
    3 -> "Dalej: czas"
    else -> if (isNextCycle) "Wygeneruj kolejny cykl" else "Ułóż plan"
}
