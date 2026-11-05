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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.saveable.rememberSaveable
import pl.mysttic.trainings.ui.adaptive.ReadableBox
import pl.mysttic.trainings.ui.common.TextInput
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.ProgressTrack
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Plan jako kolejka (D-21) w oprawie „Ognia i Krwi” (D-32): odhaczone przygaszone, odpoczynek celowo szary,
 * a jedyne wyróżnienie na ekranie to karta następnego dnia z paskiem od lewej i własnym Startem.
 * Plan nieaktywny (D-42) zamiast Startu ma „Ustaw jako aktywny”; bieżący wtedy czeka na liście z postępem.
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

    if (program == null) {
        if (!state.loading) {
            // Plan usunięty spod otwartego ekranu: strzałka Wstecz zostaje, tekst mówi, czego brakuje.
            ReadableBox {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.screenPadding, vertical = 8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    EmptyState(if (state.byId) "Nie znaleziono planu." else "Nie masz jeszcze planu.")
                }
            }
        }
        return
    }
    val zone = ZoneId.systemDefault()
    // Plan bez ani jednego dnia z treningiem nie ma czego prowadzić: nie da się go aktywować ani zacząć od nowa.
    val hasTrainings = state.totalCount > 0
    val activate: () -> Unit = {
        if (state.pausing != null) dialog = PlanDialog.ACTIVATE else viewModel.activate()
    }

    ReadableBox {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
        ) {
            item(key = "naglowek") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.weight(1f))
                    PlanMenu(
                        active = state.isActive,
                        hasTrainings = hasTrainings,
                        canEdit = program.editableInPlace && !state.locked,
                        canDelete = !state.locked,
                        onActivate = activate,
                        onRename = { dialog = PlanDialog.RENAME },
                        onEdit = onEditPlan?.let { edit -> { edit(program.id) } },
                        onDuplicate = { viewModel.duplicate(thenEdit = !(program.editableInPlace && !state.locked)) },
                        onRepeat = { dialog = PlanDialog.REPEAT },
                        onExport = onExport?.let { export -> { export(program.id) } },
                        onStats = onStats?.let { stats -> { stats(program.id) } },
                        onPlans = onPlans,
                        onDelete = { dialog = PlanDialog.DELETE },
                    )
                }
                Text(program.name, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Light)) {
                            append(state.doneCount.toString())
                        }
                        append("  z ${state.totalCount} treningów")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ProgressTrack(fraction = if (state.totalCount == 0) 0f else state.doneCount.toFloat() / state.totalCount)
                Spacer(Modifier.height(18.dp))
                if (!state.isActive && !state.isComplete && hasTrainings) {
                    PrimaryAction("Ustaw jako aktywny", onClick = activate, enabled = !state.busy)
                    Spacer(Modifier.height(18.dp))
                }
            }

            items(state.days, key = { it.dayIndex }) { day ->
                // Dzień tygodnia ma sens tylko w aktywnym planie: rytm liczy się od chwili, w której plan wrócił.
                // Zrobiony dzień bierze dzień tygodnia z sesji, która go odhaczyła, bo rytm po wznowieniu się przesuwa.
                val weekday = when {
                    !state.isActive -> "dzień ${day.dayIndex}"
                    day.doneAt != null -> weekdayName(Instant.ofEpochMilli(day.doneAt).atZone(zone).toLocalDate())
                    else -> weekdayName(program.dateOf(day.dayIndex, zone))
                }
                DayRow(
                    day = day,
                    weekday = weekday,
                    canStart = state.isActive,
                    onStart = startDay,
                    onOpen = onOpenWorkout,
                )
            }

            item(key = "stopka") {
                Spacer(Modifier.height(20.dp))
                if (state.isComplete) {
                    if (program.isWizard) {
                        SecondaryAction("Wygeneruj kolejny cykl", onClick = onNewCycle, modifier = Modifier.fillMaxWidth(), height = 48.dp)
                    } else {
                        SecondaryAction(
                            "Zacznij od nowa",
                            onClick = { dialog = PlanDialog.REPEAT },
                            modifier = Modifier.fillMaxWidth(),
                            height = 48.dp,
                        )
                    }
                    TextButton(onClick = onPresets, modifier = Modifier.fillMaxWidth()) {
                        Text("Gotowy program", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onPlans, modifier = Modifier.fillMaxWidth()) {
                    Text("Twoje plany", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private enum class PlanDialog { DELETE, ACTIVATE, REPEAT, RENAME }

private fun weekdayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pl"))

/** Menu planu: akcje, które nie zasługują na przycisk na ekranie. Niedostępne pozycje nie są pokazywane. */
@Composable
private fun PlanMenu(
    active: Boolean,
    hasTrainings: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onEdit: (() -> Unit)?,
    onDuplicate: () -> Unit,
    onRepeat: () -> Unit,
    onExport: (() -> Unit)?,
    onStats: (() -> Unit)?,
    onPlans: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Więcej", tint = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val close: (() -> Unit) -> () -> Unit = { action -> { open = false; action() } }
            if (!active && hasTrainings) MenuEntry("Ustaw jako aktywny", close(onActivate))
            if (onStats != null) MenuEntry("Statystyki i raport", close(onStats))
            if (canEdit && onEdit != null) MenuEntry("Edytuj plan", close(onEdit))
            if (canEdit) MenuEntry("Zmień nazwę", close(onRename))
            MenuEntry(if (canEdit) "Duplikuj" else "Duplikuj i zmień", close(onDuplicate))
            if (hasTrainings) MenuEntry("Zacznij od nowa", close(onRepeat))
            if (onExport != null) MenuEntry("Wyślij plan", close(onExport))
            MenuEntry("Twoje plany", close(onPlans))
            if (canDelete) MenuEntry("Usuń plan", close(onDelete), destructive = true)
        }
    }
}

@Composable
private fun MenuEntry(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        onClick = onClick,
    )
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

@Composable
private fun DayRow(day: PlanDayRow, weekday: String, canStart: Boolean, onStart: (String) -> Unit, onOpen: (String) -> Unit) {
    if (day.isNext && day.workoutId != null) {
        NextDayCard(day, weekday, if (canStart) onStart else null, onOpen)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (day.workoutId != null) Modifier.clickable { onOpen(day.workoutId) } else Modifier)
            .padding(vertical = 10.dp)
            .alpha(if (day.done) 0.55f else 1f),
        horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayMarker(day)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.workoutName ?: "Odpoczynek",
                style = MaterialTheme.typography.titleMedium,
                color = if (day.isRest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (day.isRest) "$weekday · seria dni zostaje" else "$weekday · ${day.minutes} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Jedyna karta na ekranie: to jest ten trening, który pójdzie po naciśnięciu Start. */
@Composable
private fun NextDayCard(day: PlanDayRow, weekday: String, onStart: ((String) -> Unit)?, onOpen: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(22.dp),
        onClick = { day.workoutId?.let(onOpen) },
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(Dimens.nowAccent)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, bottom = 14.dp)) {
                Overline("Następny")
                Spacer(Modifier.height(4.dp))
                Text(day.workoutName.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "$weekday · ${day.minutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onStart != null) day.workoutId?.let { id ->
                Box(modifier = Modifier.padding(end = 14.dp)) {
                    PrimaryAction(
                        text = "Start",
                        onClick = { onStart(id) },
                        modifier = Modifier.width(104.dp),
                        height = 44.dp,
                    )
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
            day.done -> Icon(
                Icons.Filled.Check,
                contentDescription = "Zrobione",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
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
