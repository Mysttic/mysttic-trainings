package pl.mysttic.trainings.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.ui.common.AppIcons
import pl.mysttic.trainings.ui.common.BackTopBar
import pl.mysttic.trainings.ui.common.DisclosureRow
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.MenuAction
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.OverflowMenu
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.ProgressTrack
import pl.mysttic.trainings.ui.common.QuietLink
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.SegmentBar
import pl.mysttic.trainings.ui.common.TextInput
import pl.mysttic.trainings.ui.common.cardColor
import pl.mysttic.trainings.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Plan jako kolejka (D-21) w języku nowego interfejsu (D-54, za D-52): nazwa, postęp i jedna karta następnego dnia
 * z własnym Startem, a rozkład tygodniami w zwijanych wierszach. Rozwinięty jest tylko tydzień z następnym treningiem,
 * kolejne dni odpoczynku stoją jednym wierszem, a akcje planu mieszkają w menu ⋮. Plan nieaktywny (D-42) zamiast
 * Startu ma „Ustaw jako aktywny”. Na tablecie w poziomie karta i postęp po lewej, rozkład po prawej.
 */
@Composable
fun PlanScreen(
    onBack: () -> Unit,
    onStartWorkout: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onNewCycle: () -> Unit,
    onPresets: () -> Unit = {},
    onPlans: () -> Unit = {},
    onOpenPlan: (String) -> Unit = {},
    onEditPlan: ((String) -> Unit)? = null,
    onExport: ((String) -> Unit)? = null,
    onStats: ((String) -> Unit)? = null,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PlanDialog?>(null) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(Unit) { viewModel.opened.collect { onOpenPlan(it) } }
    // Start dnia przy innym treningu w toku: najpierw potwierdzenie, jak w szczegółach treningu (T3-1).
    var pendingStart by rememberSaveable { mutableStateOf<String?>(null) }
    val startDay: (String) -> Unit = { id ->
        if (state.inProgressWorkoutId != null && state.inProgressWorkoutId != id) pendingStart = id else onStartWorkout(id)
    }
    pendingStart?.let { id ->
        ConfirmDialog(
            title = "Masz trening w toku",
            text = "„${state.inProgressName.orEmpty()}” jeszcze trwa. Start tego treningu przerwie tamten bez podsumowania.",
            confirm = "Przerwij i zacznij",
            onConfirm = { pendingStart = null; onStartWorkout(id) },
            onDismiss = { pendingStart = null },
            destructive = true,
        )
    }
    LaunchedEffect(Unit) { viewModel.editCopy.collect { id -> onEditPlan?.invoke(id) ?: onOpenPlan(id) } }

    val program = state.program
    when (dialog) {
        PlanDialog.DELETE -> ConfirmDialog(
            title = "Usunąć plan?",
            text = "Zniknie rozkład dni i nieużyte treningi planu. Historia zostaje, statystyk tego planu już nie będzie.",
            confirm = "Usuń plan",
            destructive = true,
            onConfirm = { viewModel.delete() },
            onDismiss = { dialog = null },
        )
        PlanDialog.ACTIVATE -> ConfirmDialog(
            title = "Przełączyć plan?",
            text = "„${state.pausing}” zostanie wstrzymany z postępem. Wrócisz do niego z listy planów.",
            confirm = "Przełącz",
            onConfirm = { viewModel.activate() },
            onDismiss = { dialog = null },
        )
        PlanDialog.REPEAT -> ConfirmDialog(
            title = "Zacząć od nowa?",
            text = "Powstanie nowy przebieg tego planu, od pierwszego dnia. Ten zostaje na liście ze swoimi statystykami.",
            confirm = "Zacznij od nowa",
            onConfirm = { viewModel.repeat() },
            onDismiss = { dialog = null },
        )
        PlanDialog.RENAME -> if (program != null) RenameDialog(
            name = program.name,
            description = program.description,
            onSave = { name, description -> viewModel.rename(name, description) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
    state.error?.let { message ->
        ConfirmDialog(title = "Nie wyszło", text = message, confirm = "OK", onConfirm = {}, onDismiss = viewModel::dismissError)
    }

    // Plan bez ani jednego dnia z treningiem nie ma czego prowadzić: nie da się go aktywować ani zacząć od nowa.
    val hasTrainings = state.totalCount > 0
    val activate: () -> Unit = { if (state.pausing != null) dialog = PlanDialog.ACTIVATE else viewModel.activate() }
    val menu = program?.let { p ->
        val canEdit = p.editableInPlace && !state.locked
        buildList {
            if (!state.isActive && hasTrainings) add(MenuAction("Ustaw jako aktywny", activate))
            if (onStats != null) add(MenuAction("Statystyki i raport", { onStats(p.id) }))
            if (canEdit && onEditPlan != null) add(MenuAction("Edytuj plan", { onEditPlan(p.id) }))
            if (canEdit) add(MenuAction("Zmień nazwę", { dialog = PlanDialog.RENAME }))
            add(MenuAction(if (canEdit) "Duplikuj" else "Duplikuj i zmień", { viewModel.duplicate(thenEdit = !canEdit) }))
            if (hasTrainings) add(MenuAction("Zacznij od nowa", { dialog = PlanDialog.REPEAT }))
            if (onExport != null) add(MenuAction("Wyślij plan", { onExport(p.id) }))
            add(MenuAction("Twoje plany", onPlans))
            if (!state.locked) add(MenuAction("Usuń plan", { dialog = PlanDialog.DELETE }, destructive = true))
        }
    }.orEmpty()

    PlanContent(
        state = state,
        actions = PlanActions(
            onBack = onBack,
            onStartDay = startDay,
            onOpenWorkout = onOpenWorkout,
            onActivate = activate,
            onNewCycle = onNewCycle,
            onRepeat = { dialog = PlanDialog.REPEAT },
            onPresets = onPresets,
            onPlans = onPlans,
            menu = menu,
        ),
    )
}

private enum class PlanDialog { DELETE, ACTIVATE, REPEAT, RENAME }

/** Wyjścia z podglądu planu; treść jest bezstanowa, więc zrzut rysuje ją z fiksturą. */
class PlanActions(
    val onBack: () -> Unit = {},
    val onStartDay: (String) -> Unit = {},
    val onOpenWorkout: (String) -> Unit = {},
    val onActivate: () -> Unit = {},
    val onNewCycle: () -> Unit = {},
    val onRepeat: () -> Unit = {},
    val onPresets: () -> Unit = {},
    val onPlans: () -> Unit = {},
    val menu: List<MenuAction> = emptyList(),
)

@Composable
fun PlanContent(state: PlanUiState, actions: PlanActions) {
    val program = state.program
    if (program == null) {
        if (!state.loading) {
            // Plan usunięty spod otwartego ekranu: strzałka Wstecz zostaje, tekst mówi, czego brakuje.
            Column(modifier = Modifier.centeredReadable().padding(horizontal = Dimens.screenPadding, vertical = 8.dp)) {
                BackTopBar(title = null, onBack = actions.onBack)
                EmptyState(if (state.byId) "Nie znaleziono planu." else "Nie masz jeszcze planu.")
            }
        }
        return
    }
    val weeks = remember(state.days) { PlanWeeks.of(state.days) }
    // Pamiętamy tylko tygodnie przełączone ręcznie względem domyślnego: gdy następny trening przejdzie do kolejnego
    // tygodnia, rozwinie się nowy, bez starego stanu.
    var toggled by rememberSaveable { mutableStateOf(listOf<Int>()) }
    val defaultWeek = PlanWeeks.defaultExpanded(weeks)
    val schedule = ScheduleSpec(
        weeks = weeks,
        expanded = { week -> (week == defaultWeek) != (week in toggled) },
        onToggle = { week -> toggled = if (week in toggled) toggled - week else toggled + week },
        weekday = weekdayLabel(state, program),
    )
    val top: @Composable () -> Unit = {
        BackTopBar(title = null, onBack = actions.onBack) { OverflowMenu(actions.menu) }
    }

    if (LocalAppLayout.current.twoPane) {
        // Tablet w poziomie: karta i postęp po lewej, rozkład po prawej, każda kolumna przewija się sama.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = WIDE_CONTENT_MAX)
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
            ) {
                top()
                Header(state, program)
                Main(state, actions, schedule.weekday)
                Footer(state, program, actions)
            }
            LazyColumn(
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = 72.dp, bottom = 24.dp),
            ) {
                item(key = "rozklad") { Overline("Rozkład", modifier = Modifier.padding(bottom = 4.dp)) }
                schedule(schedule, actions)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.centeredReadable(),
        contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 8.dp, bottom = 24.dp),
    ) {
        item(key = "pasek") { top() }
        item(key = "naglowek") {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
                Header(state, program)
                Main(state, actions, schedule.weekday)
                if (weeks.isNotEmpty()) Overline("Rozkład", modifier = Modifier.padding(top = 6.dp))
            }
        }
        schedule(schedule, actions)
        item(key = "stopka") { Footer(state, program, actions) }
    }
}

private class ScheduleSpec(
    val weeks: List<PlanWeek>,
    val expanded: (Int) -> Boolean,
    val onToggle: (Int) -> Unit,
    val weekday: (PlanDayRow) -> String,
)

/** Rozkład: tygodnie w zwijanych wierszach; plan na jeden tydzień od razu jako lista dni. */
private fun LazyListScope.schedule(spec: ScheduleSpec, actions: PlanActions) {
    if (spec.weeks.size == 1) {
        item(key = "tydzien-1") {
            Column { spec.weeks.first().entries.forEach { WeekEntry(it, spec.weekday, actions) } }
        }
        return
    }
    spec.weeks.forEach { week ->
        item(key = "tydzien-${week.number}") {
            DisclosureRow(
                title = "Tydzień ${week.number}",
                summary = weekSummary(week),
                expanded = spec.expanded(week.number),
                onToggle = { spec.onToggle(week.number) },
            ) {
                week.entries.forEach { WeekEntry(it, spec.weekday, actions) }
            }
        }
    }
}

private fun weekSummary(week: PlanWeek): String {
    val trainings = "${week.trainings} ${trainingsWord(week.trainings)}"
    return when {
        week.trainings == 0 -> "Same odpoczynki"
        week.done == week.trainings -> "$trainings · wszystkie zrobione"
        week.done > 0 -> "$trainings · zrobione ${week.done}"
        week.hasNext -> "$trainings · tu jest następny"
        else -> trainings
    }
}

/** Nazwa, skąd plan jest i jego linijka: parametry z kreatora, tagline programu albo opis od autora. */
@Composable
private fun Header(state: PlanUiState, program: Program) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Overline(
            listOfNotNull(
                program.source.labelPl,
                when {
                    state.isComplete -> "zakończony"
                    state.isActive -> "aktywny"
                    else -> "czeka"
                },
            ).joinToString(" · "),
        )
        Text(program.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(state.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Postęp i jedyna akcja ekranu: Start następnego dnia albo „Ustaw jako aktywny”. */
@Composable
private fun Main(state: PlanUiState, actions: PlanActions, weekday: (PlanDayRow) -> String) {
    val hasTrainings = state.totalCount > 0
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Light)) {
                    append(state.doneCount.toString())
                }
                append("  z ${state.totalCount} ${trainingsWord(state.totalCount, genitive = true)}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Segmenty do czterech tygodni po trzy treningi, jak na liście planów; dłuższy plan dostaje ciągły pasek.
        if (state.totalCount in 1..12) {
            SegmentBar(done = state.doneCount, total = state.totalCount, current = state.isActive)
        } else {
            ProgressTrack(fraction = if (state.totalCount == 0) 0f else state.doneCount.toFloat() / state.totalCount)
        }
        if (!state.isActive && !state.isComplete && hasTrainings) {
            PrimaryAction("Ustaw jako aktywny", onClick = actions.onActivate, enabled = !state.busy)
        }
        state.days.firstOrNull { it.isNext && it.workoutId != null }?.let { next ->
            NextDayCard(day = next, weekday = weekday(next), onStart = if (state.isActive) actions.onStartDay else null, onOpen = actions.onOpenWorkout)
        }
    }
}

@Composable
private fun Footer(state: PlanUiState, program: Program, actions: PlanActions) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.isComplete) {
            if (program.isWizard) {
                SecondaryAction("Wygeneruj kolejny cykl", onClick = actions.onNewCycle, modifier = Modifier.fillMaxWidth(), height = 48.dp)
            } else {
                SecondaryAction("Zacznij od nowa", onClick = actions.onRepeat, modifier = Modifier.fillMaxWidth(), height = 48.dp)
            }
            QuietLink("Gotowy program", onClick = actions.onPresets)
        }
        QuietLink("Twoje plany", onClick = actions.onPlans, icon = AppIcons.Plans)
    }
}

/** Dzień tygodnia ma sens tylko w aktywnym planie: rytm liczy się od chwili, w której plan wrócił. */
private fun weekdayLabel(state: PlanUiState, program: Program): (PlanDayRow) -> String {
    val zone = ZoneId.systemDefault()
    return { day ->
        when {
            !state.isActive -> "dzień ${day.dayIndex}"
            // Zrobiony dzień bierze dzień tygodnia z sesji, która go odhaczyła, bo rytm po wznowieniu się przesuwa.
            day.doneAt != null -> weekdayName(Instant.ofEpochMilli(day.doneAt).atZone(zone).toLocalDate())
            else -> weekdayName(program.dateOf(day.dayIndex, zone))
        }
    }
}

private fun weekdayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pl"))

@Composable
private fun WeekEntry(entry: PlanWeekEntry, weekday: (PlanDayRow) -> String, actions: PlanActions) {
    when (entry) {
        is PlanWeekEntry.Training -> TrainingRow(entry.day, weekday(entry.day), onOpen = actions.onOpenWorkout)
        is PlanWeekEntry.Rest -> RestRow(entry.days, entry.days.map(weekday))
    }
}

@Composable
private fun TrainingRow(day: PlanDayRow, weekday: String, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (day.workoutId != null) Modifier.clickable(role = Role.Button) { onOpen(day.workoutId) } else Modifier)
            .padding(vertical = 8.dp)
            .alpha(if (day.done) 0.55f else 1f),
        horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayMarker(day)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.workoutName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$weekday · ${day.minutes} min" + if (day.isNext) " · następny" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (day.isNext) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Kolejne dni odpoczynku jednym wierszem; seria dni z rzędu przez nie nie pęka. */
@Composable
private fun RestRow(days: List<PlanDayRow>, weekdays: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayMarker(days.first())
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (days.size == 1) "Odpoczynek" else "Odpoczynek · ${days.size} dni",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${weekdays.joinToString(", ")} · seria dni zostaje",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Jedyna karta na ekranie: ten trening pójdzie po naciśnięciu Start. */
@Composable
private fun NextDayCard(day: PlanDayRow, weekday: String, onStart: ((String) -> Unit)?, onOpen: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardColor(),
        shape = RoundedCornerShape(22.dp),
        onClick = { day.workoutId?.let(onOpen) },
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(Dimens.nowAccent)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, bottom = 14.dp)) {
                Overline("Następny")
                Spacer(Modifier.height(4.dp))
                Text(day.workoutName.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "$weekday · ${day.minutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onStart != null) day.workoutId?.let { id ->
                Box(modifier = Modifier.padding(end = 14.dp)) {
                    PrimaryAction(text = "Start", onClick = { onStart(id) }, modifier = Modifier.width(104.dp), height = 44.dp)
                }
            }
        }
    }
}

/** Znacznik dnia: ptaszek dla zrobionych, kreska dla odpoczynku, przerywany okrąg dla przyszłych. */
@Composable
private fun DayMarker(day: PlanDayRow) {
    val outline = MaterialTheme.colorScheme.outline
    val restColor = MaterialTheme.colorScheme.secondaryContainer
    Box(
        modifier = Modifier
            .size(30.dp)
            .then(
                when {
                    day.isRest -> Modifier.clip(CircleShape).background(restColor)
                    day.done -> Modifier.drawBehind {
                        drawCircle(color = outline, radius = size.minDimension / 2, style = Stroke(width = 1.dp.toPx()))
                    }
                    else -> Modifier.drawBehind {
                        drawCircle(
                            color = outline,
                            radius = size.minDimension / 2,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                            ),
                        )
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            day.done -> Icon(AppIcons.Check, contentDescription = "Zrobione", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            day.isRest -> Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onSecondaryContainer),
            )
            else -> Text(
                text = day.dayIndex.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val action: @Composable () -> Unit = {
        TextButton(onClick = { onDismiss(); onConfirm() }) {
            Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        // Kasowanie trzyma konwencję reszty aplikacji („Usunąć sesję?”, „Porzucić zmiany?”): po prawej „nie rób nic”,
        // czerwona akcja po lewej.
        confirmButton = if (destructive) {
            { TextButton(onClick = onDismiss) { Text("Zostaw") } }
        } else {
            action
        },
        dismissButton = if (destructive) action else {
            { TextButton(onClick = onDismiss) { Text("Anuluj") } }
        },
    )
}

@Composable
private fun RenameDialog(name: String, description: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var newName by rememberSaveable { mutableStateOf(name) }
    var newDescription by rememberSaveable { mutableStateOf(description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Nazwa planu", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(label = "Nazwa", value = newName, onValueChange = { newName = it.take(80) })
                TextInput(label = "Opis", value = newDescription, onValueChange = { newDescription = it.take(2000) }, singleLine = false, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onSave(newName, newDescription) }, enabled = newName.isNotBlank()) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

/** Liczebnik treningów: „1 trening”, „3 treningi”, „5 treningów”; po „z” zawsze dopełniacz („z 18 treningów”). */
private fun trainingsWord(n: Int, genitive: Boolean = false): String = when {
    genitive -> if (n == 1) "treningu" else "treningów"
    n == 1 -> "trening"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "treningi"
    else -> "treningów"
}

/** Szerokość dwóch kolumn na tablecie, jak „Dziś” w poziomie (D-52). */
private val WIDE_CONTENT_MAX = 1100.dp
