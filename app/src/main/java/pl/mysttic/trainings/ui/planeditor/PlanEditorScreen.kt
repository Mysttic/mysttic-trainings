package pl.mysttic.trainings.ui.planeditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.domain.plan.DraftDay
import pl.mysttic.trainings.ui.adaptive.ReadableBox
import pl.mysttic.trainings.ui.common.AppSwitch
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.InlineStepper
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.SettingRow
import pl.mysttic.trainings.ui.common.TextInput
import pl.mysttic.trainings.ui.theme.Dimens
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Edytor planu (D-42): nazwa i opis, plan dosłowny albo dopasowany, treningi planu i rozkład dni.
 * Zrobione dni aktywnego planu są zablokowane (kolejka D-21 nie może zmienić znaczenia sesji z historii).
 * Treningi otwiera zwykły edytor treningu; `onEditWorkout(id, isNew, literal)`.
 */
@Composable
fun PlanEditorScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onEditWorkout: (workoutId: String, programId: String, isNew: Boolean, literal: Boolean) -> Unit,
    viewModel: PlanEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var dayForPicker by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.saved) { if (state.saved) state.draft?.let { onSaved(it.programId) } }
    LaunchedEffect(state.discarded) { if (state.discarded) onBack() }

    val leave: () -> Unit = { if (viewModel.hasChanges()) dialog = DIALOG_DISCARD else onBack() }
    BackHandler { leave() }

    val draft = state.draft
    if (draft == null || state.loading) {
        if (!state.loading) Unavailable("Nie znaleziono planu.", onBack)
        return
    }
    if (state.notEditable) {
        Unavailable("Ten plan zmienisz przez kopię: „Duplikuj i zmień” w menu planu.", onBack)
        return
    }

    when (dialog) {
        DIALOG_DISCARD -> AlertDialog(
            onDismissRequest = { dialog = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Porzucić zmiany?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = if (draft.isNew) "Nowy plan zniknie razem z treningami, które w nim powstały."
                    else "Rozkład dni wróci do zapisanego, a treningi dodane w tej edycji znikną. Zmiany w treningach, które już były, zostają.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Wróć do edycji") } },
            dismissButton = {
                TextButton(onClick = { dialog = null; viewModel.discard() }) {
                    Text("Porzuć", color = MaterialTheme.colorScheme.error)
                }
            },
        )
        DIALOG_SKELETON -> SkeletonDialog(
            onCreate = { weeks, perWeek -> viewModel.skeleton(weeks, perWeek) },
            onDismiss = { dialog = null },
        )
        DIALOG_COPY -> WorkoutPickerDialog(
            title = "Skopiuj trening do planu",
            workouts = state.library,
            allowRest = false,
            onPick = { id -> id?.let(viewModel::copyWorkout) },
            onDismiss = { dialog = null },
        )
    }
    dayForPicker?.let { index ->
        WorkoutPickerDialog(
            title = "Dzień ${index + 1}",
            workouts = state.workouts,
            allowRest = true,
            onPick = { id -> if (index < draft.days.size) viewModel.setDayWorkout(index, id) else if (id != null) viewModel.addTrainingDay(id) },
            onDismiss = { dayForPicker = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
            ) {
                PrimaryAction("Zapisz plan", onClick = viewModel::save, enabled = !state.locked)
            }
        },
    ) { padding ->
        ReadableBox(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "naglowek") {
                    IconButton(onClick = leave, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(if (draft.isNew) "Nowy plan" else "Edycja planu", style = MaterialTheme.typography.headlineMedium)
                    if (state.locked) {
                        Text(
                            text = "Trening z tego planu właśnie trwa. Plan zmienisz po jego zakończeniu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(Dimens.sectionGap))
                    TextInput("Nazwa planu", draft.name, viewModel::setName)
                    Spacer(Modifier.height(Dimens.sectionGap))
                    TextInput("Opis (dla kogo, jak ćwiczyć)", draft.description, viewModel::setDescription, singleLine = false, minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    SettingRow(
                        title = "Dopasowuj cele do poziomu i trenera",
                        description = if (draft.literal) {
                            "Wyłączone: wartości dokładnie jak wpisane — bez poziomu, trenera i „Podbij cele”."
                        } else {
                            "Włączone: poziom trudności skaluje cele, trener koryguje je po opiniach, jak w planie z kreatora."
                        },
                        trailing = { AppSwitch(checked = !draft.literal, onCheckedChange = { viewModel.setLiteral(!it) }) },
                    )
                    state.errors.forEach { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                item(key = "treningi") {
                    Overline("Treningi planu", modifier = Modifier.padding(top = 12.dp))
                    if (state.workouts.isEmpty()) {
                        Text(
                            text = "Zacznij od treningu: ułóż nowy albo skopiuj istniejący. Potem rozpisz dni.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(state.workouts, key = { "w-" + it.id }) { workout ->
                    WorkoutRow(
                        workout = workout,
                        onEdit = { onEditWorkout(workout.id, draft.programId, false, draft.literal) },
                    )
                }
                item(key = "treningi-akcje") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        SecondaryAction(
                            "Nowy trening",
                            onClick = { viewModel.newWorkoutId()?.let { onEditWorkout(it, draft.programId, true, draft.literal) } },
                            modifier = Modifier.weight(1f),
                            height = 48.dp,
                        )
                        SecondaryAction("Skopiuj trening", onClick = { dialog = DIALOG_COPY }, modifier = Modifier.weight(1f), height = 48.dp)
                    }
                }

                item(key = "dni") {
                    Overline("Dni (${draft.days.size})", modifier = Modifier.padding(top = 18.dp))
                    Text(
                        text = "Kolejka jak w każdym planie: dzień odhacza ukończony trening, a pominięty czeka na swoją kolej.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.surplusKeys.isNotEmpty()) {
                        Text(
                            text = "„Od razu zrobiony”: ten trening masz ukończony więcej razy, niż stał w planie, " +
                                "więc nowy dzień z nim odhaczy się sam.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                itemsIndexed(draft.days, key = { _, day -> day.key }) { index, day ->
                    // Zrobiony dzień zostaje na swoim numerze: sąsiad nie zamienia się z nim miejscami,
                    // a dzień przed nim nie znika ani się nie podwaja, bo przesunąłby go dalej.
                    val lockedAfter = draft.days.drop(index + 1).any { it.key in state.lockedKeys }
                    DayRow(
                        index = index,
                        day = day,
                        workout = day.workoutId?.let { state.workoutsById[it] },
                        locked = day.key in state.lockedKeys,
                        surplus = day.key in state.surplusKeys,
                        canMoveUp = index > 0 && draft.days[index - 1].key !in state.lockedKeys,
                        canMoveDown = index < draft.days.lastIndex && draft.days[index + 1].key !in state.lockedKeys,
                        canShift = !lockedAfter,
                        onPick = { dayForPicker = index },
                        onMove = { to -> viewModel.moveDay(index, to) },
                        onDuplicate = { viewModel.duplicateDay(index) },
                        onRemove = { viewModel.removeDay(index) },
                    )
                }
                item(key = "dni-akcje") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            SecondaryAction(
                                "Dzień z treningiem",
                                onClick = { if (state.workouts.isNotEmpty()) dayForPicker = draft.days.size },
                                modifier = Modifier.weight(1f),
                                height = 48.dp,
                            )
                            SecondaryAction("Odpoczynek", onClick = viewModel::addRestDay, modifier = Modifier.weight(1f), height = 48.dp)
                        }
                        if (draft.days.isEmpty() && state.workouts.isNotEmpty()) {
                            SecondaryAction("Rozpisz tygodnie", onClick = { dialog = DIALOG_SKELETON }, modifier = Modifier.fillMaxWidth(), height = 48.dp)
                        }
                        if (draft.days.isNotEmpty()) {
                            SecondaryAction("Kolejny tydzień jak ostatni", onClick = viewModel::appendWeekCopy, modifier = Modifier.fillMaxWidth(), height = 48.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Plan, którego nie da się tu edytować: strzałka Wstecz zostaje, żeby nie trzeba było szukać systemowego gestu. */
@Composable
private fun Unavailable(text: String, onBack: () -> Unit) {
    ReadableBox {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.screenPadding, vertical = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
            }
            EmptyState(text)
        }
    }
}

private const val DIALOG_DISCARD = "discard"
private const val DIALOG_SKELETON = "skeleton"
private const val DIALOG_COPY = "copy"

@Composable
private fun WorkoutRow(workout: Workout, onEdit: () -> Unit) {
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(workout.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${workout.exerciseIds.size} ćw. · ${workout.blocks.size} ${if (workout.blocks.size == 1) "blok" else "bloki"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.Edit, contentDescription = "Edytuj trening", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayRow(
    index: Int,
    day: DraftDay,
    workout: Workout?,
    locked: Boolean,
    surplus: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canShift: Boolean,
    onPick: () -> Unit,
    onMove: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (locked) Modifier.alpha(0.55f) else Modifier.clickable(onClick = onPick))
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        day.isRest -> "Odpoczynek"
                        workout == null -> "Brak treningu"
                        else -> workout.name
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (day.isRest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Dzień ${index + 1}" + when {
                        locked -> " · zrobiony"
                        surplus -> " · od razu zrobiony"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (locked) {
                Icon(Icons.Filled.Check, contentDescription = "Zrobiony", tint = MaterialTheme.colorScheme.primary)
                return@Row
            }
            IconButton(onClick = { onMove(index - 1) }, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Dzień wcześniej")
            }
            IconButton(onClick = { onMove(index + 1) }, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Dzień później")
            }
            TextButton(
                onClick = onDuplicate,
                enabled = canShift,
                modifier = Modifier.semantics { contentDescription = "Duplikuj dzień" },
            ) { Text("×2", style = MaterialTheme.typography.labelMedium) }
            IconButton(onClick = onRemove, enabled = canShift) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Usuń dzień",
                    tint = if (canShift) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

/** Wybór treningu dla dnia (albo odpoczynku). Lista, nie rozwijane menu: na tablecie i w poziomie też się czyta. */
@Composable
private fun WorkoutPickerDialog(
    title: String,
    workouts: List<Workout>,
    allowRest: Boolean,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                if (allowRest) {
                    item(key = "odpoczynek") {
                        PickerRow("Odpoczynek", onClick = { onDismiss(); onPick(null) })
                    }
                }
                items(workouts, key = { it.id }) { workout ->
                    PickerRow(workout.name, onClick = { onDismiss(); onPick(workout.id) })
                }
                if (workouts.isEmpty()) {
                    item(key = "pusto") {
                        Text("Brak treningów do wyboru.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun PickerRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** Szkielet: ile tygodni i ile treningów w tygodniu; rotacja z treningów planu po kolei. */
@Composable
private fun SkeletonDialog(onCreate: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var weeks by rememberSaveable { mutableIntStateOf(4) }
    var perWeek by rememberSaveable { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Rozpisz tygodnie", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                InlineStepper(
                    label = "Tygodnie",
                    value = "$weeks",
                    onLess = { weeks = (weeks - 1).coerceAtLeast(1) },
                    onMore = { weeks = (weeks + 1).coerceAtMost(17) },
                    lessEnabled = weeks > 1,
                    moreEnabled = weeks < 17,
                )
                InlineStepper(
                    label = "Treningi w tygodniu",
                    value = "$perWeek",
                    onLess = { perWeek = (perWeek - 1).coerceAtLeast(1) },
                    onMore = { perWeek = (perWeek + 1).coerceAtMost(7) },
                    lessEnabled = perWeek > 1,
                    moreEnabled = perWeek < 7,
                )
                Text(
                    text = "Treningi planu pójdą po kolei (A, B, A…), reszta dni to odpoczynek.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onCreate(weeks, perWeek) }) { Text("Rozpisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
