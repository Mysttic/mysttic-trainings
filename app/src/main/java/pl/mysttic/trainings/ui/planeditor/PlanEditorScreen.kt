package pl.mysttic.trainings.ui.planeditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.domain.plan.DraftDay
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.ui.adaptive.readableWidth
import pl.mysttic.trainings.ui.common.AppIcons
import pl.mysttic.trainings.ui.common.AppSwitch
import pl.mysttic.trainings.ui.common.BackTopBar
import pl.mysttic.trainings.ui.common.CategoryDivider
import pl.mysttic.trainings.ui.common.CategoryGroup
import pl.mysttic.trainings.ui.common.CategoryRow
import pl.mysttic.trainings.ui.common.DisclosureRow
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.InlineStepper
import pl.mysttic.trainings.ui.common.MenuAction
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.OverflowMenu
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.ScreenScaffold
import pl.mysttic.trainings.ui.common.SettingRow
import pl.mysttic.trainings.ui.common.TextInput
import pl.mysttic.trainings.ui.common.cardColor
import pl.mysttic.trainings.ui.theme.Dimens

/**
 * Edytor planu (D-42) w języku nowego interfejsu (D-54, za D-52): nazwa, opis i tryb planu, treningi planu w karcie
 * razem z „Nowy trening” i „Skopiuj trening”, a dni tygodniami w zwijanych wierszach. Przy dniu zostają strzałki,
 * a duplikat i usunięcie są w jego menu ⋮. Zrobione dni aktywnego planu są zablokowane (kolejka D-21 nie może zmienić
 * znaczenia sesji z historii). Na tablecie w poziomie treningi po lewej, dni po prawej.
 * Treningi otwiera zwykły edytor treningu: `onEditWorkout(id, programId, isNew, literal)`.
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
            emptyText = "Brak treningów w innych planach. Trening z tego planu skopiujesz przez „×2” przy nim.",
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

    PlanEditorContent(
        state = state,
        onBack = leave,
        actions = PlanEditorActions(
            onName = viewModel::setName,
            onDescription = viewModel::setDescription,
            onLiteral = viewModel::setLiteral,
            onEditWorkout = { workout -> onEditWorkout(workout.id, draft.programId, false, draft.literal) },
            onDuplicateWorkout = viewModel::copyWorkout,
            onNewWorkout = { viewModel.newWorkoutId()?.let { onEditWorkout(it, draft.programId, true, draft.literal) } },
            onCopyWorkout = { dialog = DIALOG_COPY },
            onPickDay = { index -> if (index < draft.days.size || state.workouts.isNotEmpty()) dayForPicker = index },
            onMoveDay = viewModel::moveDay,
            onDuplicateDay = viewModel::duplicateDay,
            onRemoveDay = viewModel::removeDay,
            onAddRest = viewModel::addRestDay,
            onSkeleton = { dialog = DIALOG_SKELETON },
            onWeekCopy = viewModel::appendWeekCopy,
            onSave = viewModel::save,
        ),
    )
}

/** Wszystkie zmiany szkicu i okna edytora; treść jest bezstanowa, więc zrzut rysuje ją z fiksturą. */
class PlanEditorActions(
    val onName: (String) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onLiteral: (Boolean) -> Unit = {},
    val onEditWorkout: (Workout) -> Unit = {},
    val onDuplicateWorkout: (String) -> Unit = {},
    val onNewWorkout: () -> Unit = {},
    val onCopyWorkout: () -> Unit = {},
    /** Indeks dnia do zmiany; `draft.days.size` = nowy dzień z treningiem na końcu. */
    val onPickDay: (Int) -> Unit = {},
    val onMoveDay: (Int, Int) -> Unit = { _, _ -> },
    val onDuplicateDay: (Int) -> Unit = {},
    val onRemoveDay: (Int) -> Unit = {},
    val onAddRest: () -> Unit = {},
    val onSkeleton: () -> Unit = {},
    val onWeekCopy: () -> Unit = {},
    val onSave: () -> Unit = {},
)

@Composable
fun PlanEditorContent(state: PlanEditorUiState, actions: PlanEditorActions, onBack: () -> Unit) {
    val draft = state.draft ?: return
    val weeks = remember(draft.days) { draft.days.withIndex().groupBy { it.index / WEEK }.values.toList() }
    // Domyślnie rozwinięte: przy krótkim planie wszystko, przy dłuższym pierwszy tydzień do zmiany i ostatni, do którego
    // dochodzą nowe dni. Pamiętamy tylko tygodnie przełączone ręcznie względem tej reguły.
    val firstEditable = weeks.indexOfFirst { week -> week.any { it.value.key !in state.lockedKeys } }
    val defaultOpen: Set<Int> = if (weeks.size <= 2) weeks.indices.toSet() else setOfNotNull(firstEditable.takeIf { it >= 0 }, weeks.lastIndex)
    var toggled by rememberSaveable { mutableStateOf(listOf<Int>()) }
    val days = DaysSpec(
        weeks = weeks,
        expanded = { week -> (week in defaultOpen) != (week in toggled) },
        onToggle = { week -> toggled = if (week in toggled) toggled - week else toggled + week },
    )
    val wide = LocalAppLayout.current.twoPane

    ScreenScaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .then(if (wide) Modifier.widthIn(max = WIDE_CONTENT_MAX).fillMaxWidth() else Modifier.readableWidth())
                    .padding(horizontal = if (wide) 32.dp else Dimens.screenPadding, vertical = 12.dp),
            ) {
                PrimaryAction("Zapisz plan", onClick = actions.onSave, enabled = !state.locked)
            }
        },
    ) { padding ->
        if (wide) {
            Row(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = WIDE_CONTENT_MAX)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    header(state, draft, actions, onBack)
                    trainings(state, actions)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 72.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    days(state, draft, days, actions)
                }
            }
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).centeredReadable(),
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            header(state, draft, actions, onBack)
            trainings(state, actions)
            days(state, draft, days, actions)
        }
    }
}

private class DaysSpec(
    val weeks: List<List<IndexedValue<DraftDay>>>,
    val expanded: (Int) -> Boolean,
    val onToggle: (Int) -> Unit,
)

/** Pasek, nazwa, opis i tryb planu w karcie, jak przełączniki w kategoriach Ustawień. */
private fun LazyListScope.header(state: PlanEditorUiState, draft: PlanDraft, actions: PlanEditorActions, onBack: () -> Unit) {
    item(key = "pasek") { BackTopBar(title = if (draft.isNew) "Nowy plan" else "Edycja planu", onBack = onBack) }
    if (state.locked) {
        item(key = "blokada") {
            Text(
                text = "Trening z tego planu właśnie trwa. Plan zmienisz po jego zakończeniu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    item(key = "o-planie") {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap), modifier = Modifier.padding(top = 4.dp)) {
            TextInput("Nazwa planu", draft.name, actions.onName)
            TextInput("Opis (dla kogo, jak ćwiczyć)", draft.description, actions.onDescription, singleLine = false, minLines = 2)
            Surface(color = cardColor(), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "Dopasowuj cele do poziomu i trenera",
                    description = if (draft.literal) {
                        "Wyłączone: wartości dokładnie jak wpisane, bez poziomu, trenera i „Podbij cele”."
                    } else {
                        "Włączone: poziom trudności skaluje cele, trener koryguje je po opiniach, jak w planie z kreatora."
                    },
                    trailing = { AppSwitch(checked = !draft.literal, onCheckedChange = { actions.onLiteral(!it) }) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            state.errors.forEach { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Treningi planu i dwie drogi do nowego: jedna karta, jak lista „Nowy plan” w zakładce Plany. */
private fun LazyListScope.trainings(state: PlanEditorUiState, actions: PlanEditorActions) {
    item(key = "treningi-tytul") { Overline("Treningi planu", modifier = Modifier.padding(top = 14.dp)) }
    if (state.workouts.isEmpty()) {
        item(key = "treningi-pusto") {
            Text(
                text = "Zacznij od treningu: ułóż nowy albo skopiuj istniejący. Potem rozpisz dni.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "treningi") {
        CategoryGroup {
            state.workouts.forEach { workout ->
                WorkoutRow(
                    workout = workout,
                    onEdit = { actions.onEditWorkout(workout) },
                    onDuplicate = { actions.onDuplicateWorkout(workout.id) },
                    duplicateEnabled = !state.locked,
                )
                CategoryDivider()
            }
            CategoryRow(
                icon = AppIcons.Plus,
                title = "Nowy trening",
                summary = "Ułożysz go od zera w edytorze treningu.",
                onClick = actions.onNewWorkout,
            )
            CategoryDivider()
            CategoryRow(
                icon = AppIcons.File,
                title = "Skopiuj trening",
                summary = "Z innego planu. Trening z tego planu skopiuje „×2” przy nim.",
                onClick = actions.onCopyWorkout,
            )
        }
    }
}

/** Dni planu tygodniami i dokładanie dni na koniec. */
private fun LazyListScope.days(state: PlanEditorUiState, draft: PlanDraft, spec: DaysSpec, actions: PlanEditorActions) {
    item(key = "dni-tytul") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 14.dp)) {
            Overline("Dni (${draft.days.size})")
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
                )
            }
        }
    }
    val dayRows: @Composable (List<IndexedValue<DraftDay>>) -> Unit = { week ->
        week.forEach { (index, day) ->
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
                onPick = { actions.onPickDay(index) },
                onMove = { to -> actions.onMoveDay(index, to) },
                onDuplicate = { actions.onDuplicateDay(index) },
                onRemove = { actions.onRemoveDay(index) },
            )
        }
    }
    if (spec.weeks.size == 1) {
        item(key = "tydzien-0") { Column { dayRows(spec.weeks.first()) } }
    } else {
        spec.weeks.forEachIndexed { weekIndex, week ->
            item(key = "tydzien-$weekIndex") {
                DisclosureRow(
                    title = "Tydzień ${weekIndex + 1}",
                    summary = weekSummary(week.map { it.value }, state.lockedKeys),
                    expanded = spec.expanded(weekIndex),
                    onToggle = { spec.onToggle(weekIndex) },
                ) {
                    dayRows(week)
                }
            }
        }
    }
    item(key = "dni-akcje") {
        CategoryGroup(modifier = Modifier.padding(top = 6.dp)) {
            CategoryRow(
                icon = AppIcons.Plus,
                title = "Dzień z treningiem",
                summary = if (state.workouts.isEmpty()) "Najpierw dodaj trening planu." else "Wybierzesz jeden z treningów planu.",
                onClick = { actions.onPickDay(draft.days.size) },
            )
            CategoryDivider()
            CategoryRow(icon = AppIcons.Pause, title = "Odpoczynek", summary = "Dzień przerwy; seria dni z rzędu nie pęka.", onClick = actions.onAddRest)
            if (draft.days.isEmpty() && state.workouts.isNotEmpty()) {
                CategoryDivider()
                CategoryRow(
                    icon = AppIcons.Calendar,
                    title = "Rozpisz tygodnie",
                    summary = "Tygodnie razy treningi; treningi planu po kolei, reszta to odpoczynek.",
                    onClick = actions.onSkeleton,
                )
            }
            if (draft.days.isNotEmpty()) {
                CategoryDivider()
                CategoryRow(
                    icon = AppIcons.Calendar,
                    title = "Kolejny tydzień jak ostatni",
                    summary = "Kopia ostatnich 7 dni na koniec, potem zmieniasz liczby.",
                    onClick = actions.onWeekCopy,
                )
            }
        }
    }
}

private fun weekSummary(days: List<DraftDay>, lockedKeys: Set<String>): String {
    val trainings = days.count { !it.isRest }
    val rests = days.size - trainings
    val parts = buildList {
        add("$trainings ${plural(trainings, "trening", "treningi", "treningów")}")
        if (rests > 0) add("$rests ${plural(rests, "odpoczynek", "odpoczynki", "odpoczynków")}")
        if (days.isNotEmpty() && days.all { it.key in lockedKeys }) add("zrobiony")
    }
    return parts.joinToString(" · ")
}

private fun plural(n: Int, one: String, few: String, many: String): String = when {
    n == 1 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}

/** Plan, którego nie da się tu edytować: strzałka Wstecz zostaje, żeby nie trzeba było szukać systemowego gestu. */
@Composable
private fun Unavailable(text: String, onBack: () -> Unit) {
    Column(modifier = Modifier.centeredReadable().padding(horizontal = Dimens.screenPadding, vertical = 8.dp)) {
        BackTopBar(title = null, onBack = onBack)
        EmptyState(text)
    }
}

private const val DIALOG_DISCARD = "discard"
private const val DIALOG_SKELETON = "skeleton"
private const val DIALOG_COPY = "copy"
private const val WEEK = 7

/** Szerokość dwóch kolumn na tablecie, jak „Dziś” w poziomie (D-52). */
private val WIDE_CONTENT_MAX = 1100.dp

/** Trening planu: tapnięcie otwiera edytor treningu, „×2” dokłada kopię z „(kopia)” w nazwie (D-49). */
@Composable
private fun WorkoutRow(workout: Workout, onEdit: () -> Unit, onDuplicate: () -> Unit, duplicateEnabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onEdit)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Exercises, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(workout.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${workout.exerciseIds.size} ćw. · ${workout.blocks.size} ${if (workout.blocks.size == 1) "blok" else "bloki"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Czytnik ekranu przeczytałby „razy dwa”; opis mówi, co przycisk robi.
        TextButton(
            onClick = onDuplicate,
            enabled = duplicateEnabled,
            modifier = Modifier.semantics { contentDescription = "Duplikuj trening" },
        ) {
            Text("×2", style = MaterialTheme.typography.labelMedium)
        }
        Icon(AppIcons.Edit, contentDescription = "Edytuj trening", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/**
 * Dzień planu: numer w kółku, trening i stan. Strzałki przesuwają dzień, menu ⋮ ma duplikat i usunięcie
 * (rzadziej potrzebne niż przesunięcie, więc nie zajmują miejsca w wierszu).
 */
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (locked) Modifier.alpha(0.55f) else Modifier.clickable(role = Role.Button, onClick = onPick))
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (day.isRest) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (locked) {
                Icon(AppIcons.Check, contentDescription = "Zrobiony", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            } else {
                Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        if (locked) return@Row
        IconButton(onClick = { onMove(index - 1) }, enabled = canMoveUp) {
            Icon(AppIcons.ChevronUp, contentDescription = "Dzień wcześniej", tint = tint(canMoveUp))
        }
        IconButton(onClick = { onMove(index + 1) }, enabled = canMoveDown) {
            Icon(AppIcons.ChevronDown, contentDescription = "Dzień później", tint = tint(canMoveDown))
        }
        OverflowMenu(
            actions = if (canShift) {
                listOf(MenuAction("Duplikuj dzień", onDuplicate), MenuAction("Usuń dzień", onRemove, destructive = true))
            } else {
                emptyList()
            },
            contentDescription = "Więcej o dniu ${index + 1}",
        )
    }
}

/** Wyłączona strzałka przygaszona, nie w `outlineVariant`: w „Ogniu i Krwi” to krwista czerwień i wyglądała jak błąd. */
@Composable
private fun tint(enabled: Boolean) =
    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

/** Wybór treningu dla dnia (albo odpoczynku). Lista, nie rozwijane menu: na tablecie i w poziomie też się czyta. */
@Composable
private fun WorkoutPickerDialog(
    title: String,
    workouts: List<Workout>,
    allowRest: Boolean,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    emptyText: String = "Brak treningów do wyboru.",
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
                        Text(emptyText, style = MaterialTheme.typography.bodyMedium)
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
                Spacer(Modifier.height(4.dp))
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
