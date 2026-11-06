package pl.mysttic.trainings.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.plan.ProgressionStep
import pl.mysttic.trainings.domain.plan.TargetSuggestion
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.ui.common.AppIcons
import pl.mysttic.trainings.ui.common.ExerciseImage
import pl.mysttic.trainings.ui.common.ExercisePreviewStrip
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.ProgressionCard
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.StatCellData
import pl.mysttic.trainings.ui.common.StatGrid
import pl.mysttic.trainings.ui.common.StatRow
import pl.mysttic.trainings.ui.common.WorkoutCard
import pl.mysttic.trainings.ui.common.cardColor
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
 * Układ z D-52: data, zdanie o dzisiejszym treningu i jedna karta z miniaturami ćwiczeń. Statystyki schodzą pod kartę
 * i pojawiają się dopiero, gdy jest jakaś historia — na start nie ma czego liczyć. Tablet w poziomie dostaje dwie
 * kolumny: karta po lewej, tydzień po prawej.
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
    val welcome by viewModel.welcomeImages.collectAsStateWithLifecycle()
    // Akcje pamiętamy raz na ViewModel, a nawigację czytamy zawsze świeżą.
    val openWorkout by rememberUpdatedState(onOpenWorkout)
    val startWorkout by rememberUpdatedState(onStartWorkout)
    val openPlan by rememberUpdatedState(onOpenPlan)
    val newPlan by rememberUpdatedState(onNewPlan)
    val presets by rememberUpdatedState(onPresets)
    val plans by rememberUpdatedState(onPlans)

    TodayContent(
        date = todayDate(),
        today = state,
        active = active,
        plan = plan,
        streak = streak,
        welcomeImages = welcome,
        actions = remember(viewModel) {
            TodayActions(
                onOpenWorkout = { openWorkout(it) },
                onStartWorkout = { startWorkout(it) },
                onOpenPlan = { openPlan() },
                onNewPlan = { newPlan(it) },
                onPresets = { presets() },
                onPlans = { plans() },
                onRepeat = viewModel::repeatPlan,
                onApplySuggestions = viewModel::applySuggestions,
            )
        },
    )
}

/** Wszystkie wyjścia z „Dziś” w jednym miejscu: treść jest bezstanowa, więc zrzut rysuje ją z fiksturą. */
class TodayActions(
    val onOpenWorkout: (String) -> Unit = {},
    val onStartWorkout: (String) -> Unit = {},
    val onOpenPlan: () -> Unit = {},
    val onNewPlan: (String?) -> Unit = {},
    val onPresets: () -> Unit = {},
    val onPlans: () -> Unit = {},
    val onRepeat: () -> Unit = {},
    val onApplySuggestions: (String, List<TargetSuggestion>) -> Unit = { _, _ -> },
)

@Composable
fun TodayContent(
    date: String,
    today: TodayUiState,
    active: ActiveCard?,
    plan: PlanCardState,
    streak: StreakCard,
    welcomeImages: List<String>,
    actions: TodayActions,
) {
    val card = plan.card
    val noPlan = !plan.loading && card == null && active == null
    // Statystyki dopiero z historią: przed pierwszym treningiem „0 w tym tygodniu” i „—” to szum, nie informacja.
    val hasHistory = today.lastSession != null || today.doneThisWeek > 0 || streak.current > 0
    val header: @Composable (Boolean) -> Unit = { wide ->
        Column {
            if (noPlan && welcomeImages.isNotEmpty()) {
                WelcomeStrip(welcomeImages, wide)
                Spacer(Modifier.height(Dimens.sectionGap + 6.dp))
            }
            Overline(date)
            Spacer(Modifier.height(10.dp))
            Headline(hasPlan = card != null, inProgress = active != null, cycleOpen = card?.nextWorkoutName != null, oneLine = wide)
        }
    }
    val main: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
            active?.let { running ->
                WorkoutCard(
                    name = running.workoutName,
                    counter = if (running.paused) "pauza" else null,
                    meta = "Trening w toku · seria ${running.workNumber} z ${running.workTotal}",
                ) {
                    PrimaryAction("Wróć do treningu", onClick = { actions.onStartWorkout(running.workoutId) })
                }
            }
            when {
                noPlan -> FirstRun(actions = actions)
                !plan.loading && active == null && card != null -> PlanCardView(plan = card, actions = actions)
            }
        }
    }
    val progression: @Composable () -> Unit = {
        card?.takeIf { it.suggestions.isNotEmpty() && it.nextWorkoutId != null }?.let { c ->
            ProgressionCard(
                // „powt.” kończy się kropką: drugiej nie dokładamy.
                text = ("Ostatnio wszystko na cel: " + c.suggestions.joinToString { it.describe() }).let { if (it.endsWith(".")) it else "$it." },
                actionLabel = "Podbij cele →",
                onAction = { actions.onApplySuggestions(c.nextWorkoutId!!, c.suggestions) },
            )
        }
    }

    if (LocalAppLayout.current.twoPane) {
        // Tablet w poziomie (D-52): karta dnia po lewej, tydzień po prawej; nadal jeden trening na ekranie (D-26).
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = WIDE_CONTENT_MAX)
                .padding(horizontal = 40.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Column(modifier = Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap + 8.dp)) {
                header(true)
                main()
            }
            Column(
                modifier = Modifier.weight(1f).padding(top = if (noPlan && welcomeImages.isNotEmpty()) 0.dp else 88.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap + 6.dp),
            ) {
                if (hasHistory) {
                    Overline("Ten tydzień")
                    StatGrid(cells = statCells(today, streak) + listOfNotNull(card?.let { StatCellData("${it.doneCount} z ${it.totalCount}", "zrobione z planu") }))
                }
                progression()
            }
        }
        return
    }

    // Na tablecie w pionie „Dziś” zostaje jedną kartą w czytelnej szerokości (D-26, D-42), nie rozlewa się na cały ekran.
    LazyColumn(
        modifier = Modifier.centeredReadable(),
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap + 4.dp),
    ) {
        item(key = "naglowek") { header(false) }
        item(key = "karta") { main() }
        item(key = "progresja") { progression() }
        if (hasHistory) {
            item(key = "statystyki") { StatRow(cells = statCells(today, streak)) }
        }
    }
}

private fun statCells(today: TodayUiState, streak: StreakCard): List<StatCellData> = listOf(
    StatCellData(today.doneThisWeek.toString(), "w tym tygodniu"),
    StatCellData("${streak.current} ${dniWord(streak.current)}", "z rzędu", accent = streak.current > 0),
    StatCellData(
        value = today.lastSession?.durationSec?.let { formatDuration(it) } ?: "—",
        label = today.lastSession?.let { "ostatnio, ${it.workoutName}" } ?: "brak treningów",
    ),
)

/** Trzy ilustracje nad powitaniem: zapowiedź, czym aplikacja się zajmuje, zanim padnie pierwsze pytanie. */
@Composable
private fun WelcomeStrip(images: List<String>, wide: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        images.forEach { path ->
            ExerciseImage(
                assetPath = path,
                modifier = Modifier.weight(1f).height(if (wide) 132.dp else 92.dp),
                shape = RoundedCornerShape(18.dp),
            )
        }
    }
}

/**
 * Nagłówek „Dziś”: ostatnie słowo grubszą wagą, jak w projekcie (D-52); na tablecie w jednej linii, większy.
 * Przy planie nie nazywa partii ciała (D-55): „Dziś trenujesz całe ciało” nad zdjęciem dnia ogłaszało to, czego tło
 * miało nie ogłaszać (D-51), a nazwę treningu i tak mówi karta pod spodem.
 */
@Composable
private fun Headline(hasPlan: Boolean, inProgress: Boolean, cycleOpen: Boolean, oneLine: Boolean) {
    val br = if (oneLine) " " else "\n"
    val text = when {
        inProgress -> buildAnnotatedString {
            append("Trening ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("w toku") }
        }
        !hasPlan -> buildAnnotatedString {
            append("Zacznij od$br")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("planu") }
        }
        cycleOpen -> buildAnnotatedString {
            append("Trening$br")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("na dziś") }
        }
        else -> buildAnnotatedString {
            append("Cykl ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("zrobiony") }
        }
    }
    val style: TextStyle = if (oneLine) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium
    Text(text = text, style = style, color = MaterialTheme.colorScheme.onSurface)
}

/**
 * Przed pierwszym planem (D-52): jedno zdanie i dwa wybory zamiast formularza i pustych statystyk. Ułożenie planu
 * to akcja główna (ogień), gotowy program — druga droga na stopniu karty. Plan własny i plik od kogoś mieszkają
 * w zakładce Plany (D-53), więc tu zostaje do nich jeden cichy przycisk.
 */
@Composable
private fun FirstRun(actions: TodayActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Powiedz, ile masz czasu i co masz w domu. Ułożę cykl i od tej pory pokażę jeden trening na dziś.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        StartChoice(
            title = "Ułóż plan",
            description = "Cztery kroki: sylwetka, cel, czas i sprzęt.",
            primary = true,
            icon = AppIcons.ArrowRight,
            onClick = { actions.onNewPlan(null) },
        )
        StartChoice(
            title = "Gotowy program",
            description = "Tabata, 100 pompek, oddech Wima Hofa i inne stałe protokoły.",
            primary = false,
            icon = AppIcons.ChevronRight,
            onClick = actions.onPresets,
        )
        QuietButton("Własny plan albo plik od kogoś", onClick = actions.onPlans)
    }
}

/** Duży wybór na start: karta z tytułem, jednym zdaniem i strzałką. `primary` = wypełnienie ogniem, bo to akcja. */
@Composable
private fun StartChoice(title: String, description: String, primary: Boolean, icon: ImageVector, onClick: () -> Unit) {
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = if (primary) MaterialTheme.colorScheme.primary else cardColor(),
    ) {
        Row(
            modifier = Modifier.padding(start = 22.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = content)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (primary) content else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (primary) content else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Karta dnia w cyklu: miniatury ćwiczeń, nazwa, licznik, pasek tygodnia i jedno „Start”. Szczegóły treningu i plan
 * to ciche odsyłacze pod przyciskiem, nie drugi rząd obrysowanych przycisków (D-52). Po cyklu: podsumowanie.
 */
@Composable
private fun PlanCardView(plan: PlanCard, actions: TodayActions) {
    if (plan.isComplete || plan.nextWorkoutId == null) {
        CycleClosed(plan = plan, actions = actions)
        return
    }
    val workoutId = plan.nextWorkoutId
    WorkoutCard(
        name = plan.nextWorkoutName.orEmpty(),
        counter = "${plan.doneCount + 1} z ${plan.totalCount}",
        meta = "${plan.nextWorkoutExercises} ${exercisesWord(plan.nextWorkoutExercises)} · ok. ${plan.nextWorkoutMinutes} min" +
            if (plan.doneToday) " · dzisiaj już trenowałeś" else "",
        weekDone = plan.weekDone,
        weekTotal = plan.weekTotal,
        top = if (plan.previewImages.isEmpty()) null else {
            { ExercisePreviewStrip(images = plan.previewImages, total = plan.previewCount, onClick = { actions.onOpenWorkout(workoutId) }) }
        },
    ) {
        // Gotowy program (D-37): co rośnie w tym etapie i ostrzeżenie protokołu, zanim padnie Start.
        plan.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        plan.warning?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        PrimaryAction("Start", onClick = { actions.onStartWorkout(workoutId) })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CardLink("Co jest w treningu", onClick = { actions.onOpenWorkout(workoutId) })
            CardLink("Zobacz plan", onClick = actions.onOpenPlan, trailing = AppIcons.ChevronRight)
        }
    }
}

/** Cichy odsyłacz na karcie: tekst w onSurface, bez obwódki i wypełnienia. */
@Composable
private fun CardLink(text: String, onClick: () -> Unit, trailing: ImageVector? = null) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        if (trailing != null) {
            Spacer(Modifier.size(4.dp))
            Icon(trailing, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Zamknięty cykl: liczby z tego, co się wydarzyło, i jedno pytanie o odczucia.
 * Pytamy tutaj, a nie w kreatorze (D-32) — odpowiedź steruje progresją następnego cyklu i wchodzi do niego
 * jako wybór wstępny, który dalej można zmienić.
 */
@Composable
private fun CycleClosed(plan: PlanCard, actions: TodayActions) {
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
            PrimaryAction(if (plan.isPreset) "Powtórz program" else "Powtórz plan", onClick = actions.onRepeat)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryAction("Ułóż plan", onClick = { actions.onNewPlan(null) }, modifier = Modifier.weight(1f))
                SecondaryAction("Zobacz plan", onClick = actions.onOpenPlan, modifier = Modifier.weight(1f))
            }
            QuietButton("Inny gotowy program", onClick = actions.onPresets)
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
        PrimaryAction("Wygeneruj kolejny cykl", onClick = { actions.onNewPlan(choice?.name) })
        SecondaryAction("Zobacz plan", onClick = actions.onOpenPlan, modifier = Modifier.fillMaxWidth())
        QuietButton("Gotowy program", onClick = actions.onPresets)
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

/** Najszersza treść „Dziś” w dwóch kolumnach; szersza rozjeżdża kartę i tydzień po bokach tabletu. */
private val WIDE_CONTENT_MAX = 1080.dp

private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("pl"))

private fun todayDate(): String =
    LocalDate.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault()).format(dateFormat)

private fun dniWord(n: Int): String = if (n == 1) "dzień" else "dni"

private fun exercisesWord(n: Int): String = when {
    n == 1 -> "ćwiczenie"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "ćwiczenia"
    else -> "ćwiczeń"
}
