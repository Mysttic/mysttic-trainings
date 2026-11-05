package pl.mysttic.trainings.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.plan.ProgressionStep
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.StatCellData
import pl.mysttic.trainings.ui.common.StatGrid
import pl.mysttic.trainings.ui.common.StatRow
import pl.mysttic.trainings.ui.common.ProgressionCard
import pl.mysttic.trainings.ui.common.WorkoutCard
import pl.mysttic.trainings.ui.common.formatDuration
import pl.mysttic.trainings.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Jeden ekran, jeden trening: aplikacja pokazuje wyłącznie następną pozycję z cyklu, a nie listę
 * treningów do wybierania (D-26). Parametry (cel, czas sesji, sprzęt) ustala kreator przy zakładaniu cyklu.
 * Układ z systemu „Ogień i Krew” (D-32): data w żarze, zdanie o dzisiejszym treningu, pasek trzech statystyk,
 * jedna karta dnia. Nic poza kartą nie dostaje wypełnienia kolorem.
 */
@Composable
fun TodayScreen(
    onOpenWorkout: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
    onOpenPlan: () -> Unit = {},
    onNewPlan: (String?) -> Unit = {},
    onPresets: () -> Unit = {},
    onPlans: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val planCount by viewModel.planCount.collectAsStateWithLifecycle()

    // Na tablecie „Dziś” zostaje jedną kartą w czytelnej szerokości (D-26, D-42), nie rozlewa się na cały ekran.
    LazyColumn(
        modifier = Modifier.centeredReadable(),
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
    ) {
        item(key = "naglowek") {
            Column {
                Overline(todayDate())
                Spacer(Modifier.height(10.dp))
                Headline(plan.card?.nextWorkoutName, hasPlan = plan.card != null, inProgress = active != null)
            }
        }
        item(key = "statystyki") {
            StatRow(
                cells = listOf(
                    StatCellData(state.doneThisWeek.toString(), "w tym tygodniu"),
                    StatCellData("${streak.current} ${dniWord(streak.current)}", "z rzędu", accent = streak.current > 0),
                    StatCellData(
                        value = state.lastSession?.durationSec?.let { formatDuration(it) } ?: "—",
                        label = state.lastSession?.let { "ostatnio, ${it.workoutName}" } ?: "brak treningów",
                    ),
                ),
            )
        }
        active?.let { card ->
            item(key = "wtoku") {
                WorkoutCard(
                    name = card.workoutName,
                    counter = if (card.paused) "pauza" else null,
                    meta = "Trening w toku · seria ${card.workNumber} z ${card.workTotal}",
                ) {
                    PrimaryAction("Wróć do treningu", onClick = { onStartWorkout(card.workoutId) })
                }
            }
        }
        if (!plan.loading && active == null) {
            item(key = "plan") {
                PlanCardView(
                    plan = plan.card,
                    onStart = onStartWorkout,
                    onOpenWorkout = onOpenWorkout,
                    onOpenPlan = onOpenPlan,
                    onNewPlan = onNewPlan,
                    onPresets = onPresets,
                    onRepeat = viewModel::repeatPlan,
                    // Bez planu też: tylko z listy da się ułożyć własny plan albo wczytać plik od kogoś.
                    onPlans = onPlans,
                    planCount = planCount,
                )
            }
        }
        plan.card?.takeIf { it.suggestions.isNotEmpty() && it.nextWorkoutId != null }?.let { card ->
            item(key = "progresja") {
                ProgressionCard(
                    text = "Ostatnio wszystko na cel: " + card.suggestions.joinToString { it.describe() } + ".",
                    actionLabel = "Podbij cele →",
                    onAction = { viewModel.applySuggestions(card.nextWorkoutId!!, card.suggestions) },
                )
            }
        }
    }
}

/** „Dziś trenujesz górę”: ostatnie słowo grubszą wagą, jak w projekcie. */
@Composable
private fun Headline(workoutName: String?, hasPlan: Boolean, inProgress: Boolean) {
    val accusative = workoutName?.let { accusativeOf(it) }
    val text = when {
        inProgress -> buildAnnotatedString {
            append("Trening ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("w toku") }
        }
        !hasPlan -> buildAnnotatedString {
            append("Zacznij od ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("planu") }
        }
        accusative != null -> buildAnnotatedString {
            append("Dziś trenujesz\n")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(accusative) }
        }
        workoutName != null -> buildAnnotatedString {
            append("Dziś\n")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(workoutName) }
        }
        else -> buildAnnotatedString {
            append("Cykl ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("zrobiony") }
        }
    }
    Text(text = text, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
}

private fun plansLabel(count: Int): String = if (count > 0) "Twoje plany ($count)" else "Twoje plany"

/**
 * Karta dnia: bez planu zachęta do kreatora albo gotowego programu, w cyklu następny trening, po cyklu kolejna edycja.
 * Jedno `PrimaryAction` na ekranie; drugie wejście (gotowy program) jest cichym przyciskiem tekstowym.
 */
@Composable
private fun PlanCardView(
    plan: PlanCard?,
    onStart: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenPlan: () -> Unit,
    onNewPlan: (String?) -> Unit,
    onPresets: () -> Unit,
    onRepeat: () -> Unit,
    onPlans: (() -> Unit)? = null,
    planCount: Int = 0,
) {
    if (plan == null) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
            Text(
                text = "Podaj wzrost, wagę, cel, sprzęt i ile minut chcesz ćwiczyć. Ułożę cykl i od tej pory " +
                    "będę pokazywać jeden trening na dziś.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryAction("Ułóż plan", onClick = { onNewPlan(null) })
            QuietButton("Gotowy program", onClick = onPresets)
            onPlans?.let { QuietButton(plansLabel(planCount), onClick = it) }
        }
        return
    }
    if (plan.isComplete || plan.nextWorkoutId == null) {
        Column {
            CycleClosed(plan = plan, onNewPlan = onNewPlan, onOpenPlan = onOpenPlan, onPresets = onPresets, onRepeat = onRepeat)
            onPlans?.let { QuietButton(plansLabel(planCount), onClick = it) }
        }
        return
    }
    WorkoutCard(
        name = plan.nextWorkoutName.orEmpty(),
        counter = "${plan.doneCount + 1} z ${plan.totalCount}",
        meta = "${plan.nextWorkoutExercises} ${exercisesWord(plan.nextWorkoutExercises)} · ok. ${plan.nextWorkoutMinutes} min" +
            if (plan.doneToday) " · dzisiaj już trenowałeś" else "",
        weekDone = plan.weekDone,
        weekTotal = plan.weekTotal,
    ) {
        // Gotowy program (D-37): co rośnie w tym etapie i ostrzeżenie protokołu, zanim padnie Start.
        plan.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        plan.warning?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        PrimaryAction("Start", onClick = { onStart(plan.nextWorkoutId) })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryAction("Co jest w treningu", onClick = { onOpenWorkout(plan.nextWorkoutId) }, modifier = Modifier.weight(1f))
            SecondaryAction("Zobacz plan", onClick = onOpenPlan, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Zamknięty cykl: liczby z tego, co się wydarzyło, i jedno pytanie o odczucia.
 * Pytamy tutaj, a nie w kreatorze (D-32) — odpowiedź steruje progresją następnego cyklu i wchodzi do niego
 * jako wybór wstępny, który dalej można zmienić.
 */
@Composable
private fun CycleClosed(
    plan: PlanCard,
    onNewPlan: (String?) -> Unit,
    onOpenPlan: () -> Unit,
    onPresets: () -> Unit,
    onRepeat: () -> Unit,
) {
    var choice by remember(plan.programName) { mutableStateOf<ProgressionStep?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
        StatGrid(
            cells = listOf(
                StatCellData("${plan.doneCount} z ${plan.totalCount}", "treningów"),
                StatCellData(formatDuration(plan.cycleDurationSec), "w ruchu"),
                StatCellData("${plan.cycleDays}", "dni cyklu"),
                StatCellData(
                    value = formatDuration(plan.cycleDurationSec / plan.doneCount.coerceAtLeast(1)),
                    label = "średnio na trening",
                ),
            ),
        )
        if (!plan.isWizard) {
            // Gotowy program (D-37) i plan własny albo od kogoś (D-42) nie mają progresji celów do rozliczenia:
            // bez pytania o odczucia, za to powtórka.
            PrimaryAction(if (plan.isPreset) "Powtórz program" else "Powtórz plan", onClick = onRepeat)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryAction("Ułóż plan", onClick = { onNewPlan(null) }, modifier = Modifier.weight(1f))
                SecondaryAction("Zobacz plan", onClick = onOpenPlan, modifier = Modifier.weight(1f))
            }
            QuietButton("Inny gotowy program", onClick = onPresets)
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline("Jak poszedł ten cykl")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                cycleFeedback.forEach { (label, step) ->
                    FilterPill(
                        text = label,
                        selected = choice == step,
                        onClick = { choice = if (choice == step) null else step },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = choice?.let { hintFor(it) }
                    ?: "Odpowiedź ustawia trudność kolejnego cyklu. Bez niej dobiorę ją z tego, ile treningów udało się zrobić.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryAction("Wygeneruj kolejny cykl", onClick = { onNewPlan(choice?.name) })
        SecondaryAction("Zobacz plan", onClick = onOpenPlan, modifier = Modifier.fillMaxWidth())
        QuietButton("Gotowy program", onClick = onPresets)
    }
}

/** Cichy przycisk tekstowy pod akcją główną, jak „Usuń plan” w podglądzie cyklu. */
@Composable
private fun QuietButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Pytamy o odczucie („za łatwo”), nie o polecenie dla generatora („trudniej”). */
private val cycleFeedback = listOf(
    "Za łatwo" to ProgressionStep.UP,
    "W porządku" to ProgressionStep.HOLD,
    "Za ciężko" to ProgressionStep.DOWN,
)

private fun hintFor(step: ProgressionStep): String = when (step) {
    ProgressionStep.UP -> "Kolejny cykl dostanie wyższe cele w tym samym czasie treningu."
    ProgressionStep.HOLD -> "Kolejny cykl zostanie na tym samym poziomie celów."
    ProgressionStep.DOWN -> "Kolejny cykl zejdzie z celami, żeby dało się go domknąć."
}

private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("pl"))

private fun todayDate(): String =
    LocalDate.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault()).format(dateFormat)

/**
 * Biernik nazwy treningu do zdania „Dziś trenujesz…”. Nazwy pochodzą z kreatora i są zamknięte:
 * „Góra A/B”, „Dół A/B”, „Całe ciało (A/B/C)”. Cokolwiek innego zostaje w mianowniku.
 */
private fun accusativeOf(name: String): String? = when {
    name.startsWith("Góra", ignoreCase = true) -> "górę"
    name.startsWith("Dół", ignoreCase = true) -> "dół"
    name.startsWith("Całe ciało", ignoreCase = true) -> "całe ciało"
    else -> null
}

private fun dniWord(n: Int): String = if (n == 1) "dzień" else "dni"

private fun exercisesWord(n: Int): String = when {
    n == 1 -> "ćwiczenie"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "ćwiczenia"
    else -> "ćwiczeń"
}
