package pl.mysttic.trainings.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.domain.editor.WorkoutEditing
import pl.mysttic.trainings.domain.engine.Announcer
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.ui.adaptive.readableWidth
import pl.mysttic.trainings.ui.common.AppIcons
import pl.mysttic.trainings.ui.common.BackTopBar
import pl.mysttic.trainings.ui.common.DisclosureRow
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.ExerciseRow
import pl.mysttic.trainings.ui.common.ExerciseThumbnail
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.InlineStepper
import pl.mysttic.trainings.ui.common.MenuAction
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.OverflowMenu
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.QuietLink
import pl.mysttic.trainings.ui.common.ScreenScaffold
import pl.mysttic.trainings.ui.common.SearchField
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.TextInput
import pl.mysttic.trainings.ui.common.cardColor
import pl.mysttic.trainings.ui.common.formatDuration
import pl.mysttic.trainings.ui.common.formatKg
import pl.mysttic.trainings.ui.theme.Dimens
import kotlin.math.roundToInt

/** Który element jest edytowany w oknie celu. */
private data class EditingItem(val blockIndex: Int, val itemIndex: Int)

/** Otwarte okno pozycji przeżywa obrót i podział ekranu (aktywność powstaje od nowa, D-42 etap 6). */
private val EditingItemSaver = Saver<EditingItem?, List<Int>>(
    save = { it?.let { e -> listOf(e.blockIndex, e.itemIndex) } },
    restore = { EditingItem(it[0], it[1]) },
)

/** Niezatwierdzony stan okna pozycji (ciężar, wskazówka, cele) jako JSON, jak bloki w bazie (D-04). */
private val BlockItemSaver = Saver<BlockItem, String>(
    save = { Json.encodeToString(BlockItem.serializer(), it) },
    restore = { Json.decodeFromString(BlockItem.serializer(), it) },
)

/**
 * Edytor treningu w języku nowego interfejsu (D-54, za D-52): nazwa i notatka, a pod nimi bloki jako karty. Karta
 * bloku pokazuje ćwiczenia z miniaturą i celem, a ustawienia bloku (nazwa, rodzaj, rundy, przerwy) czekają w zwijanym
 * wierszu ze streszczeniem. Przesunięcie, duplikat i usunięcie bloku są w jego menu ⋮, a wszystko o ćwiczeniu
 * (cel, zamiana, duplikat, usunięcie) w arkuszu po tapnięciu. Kolejność zmienia się przeciągnięciem za uchwyt.
 */
@Composable
fun WorkoutEditorScreen(
    onBack: () -> Unit,
    viewModel: WorkoutEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val estimatedMinutes by viewModel.estimatedMinutes.collectAsStateWithLifecycle()
    var pickerForBlock by rememberSaveable { mutableStateOf<Int?>(null) }
    var editing by rememberSaveable(stateSaver = EditingItemSaver) { mutableStateOf<EditingItem?>(null) }
    var replacing by rememberSaveable(stateSaver = EditingItemSaver) { mutableStateOf<EditingItem?>(null) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    // Zapis leci tylko z „Zapisz trening”, więc wyjście z niezapisanymi zmianami wymaga potwierdzenia.
    val leave: () -> Unit = { if (viewModel.hasChanges()) showDiscardDialog = true else onBack() }
    BackHandler { leave() }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Porzucić zmiany?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Nazwa, notatka i bloki przepadną. Zapisuje je „Zapisz trening”.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Wróć do edycji") } },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("Porzuć zmiany", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    pickerForBlock?.let { blockIndex ->
        ExercisePickerDialog(
            exercises = state.allExercises,
            owned = state.ownedEquipment,
            onPick = { exercise ->
                viewModel.addExercise(blockIndex, exercise.id)
                pickerForBlock = null
            },
            onDismiss = { pickerForBlock = null },
        )
    }

    replacing?.let { target ->
        ExercisePickerDialog(
            exercises = state.allExercises,
            owned = state.ownedEquipment,
            title = "Zmień ćwiczenie",
            onPick = { exercise ->
                viewModel.replaceExercise(target.blockIndex, target.itemIndex, exercise.id)
                replacing = null
            },
            onDismiss = { replacing = null },
        )
    }

    editing?.let { target ->
        val block = state.blocks.getOrNull(target.blockIndex)
        val item = block?.items?.getOrNull(target.itemIndex)
        if (block == null || item == null) {
            editing = null
        } else {
            ItemTargetSheet(
                item = item,
                blockKind = block.kind,
                exerciseName = state.exercises[item.exerciseId]?.namePl ?: item.exerciseId,
                onSave = {
                    viewModel.updateItem(target.blockIndex, target.itemIndex, it)
                    editing = null
                },
                onReplace = {
                    editing = null
                    replacing = target
                },
                onDuplicate = {
                    viewModel.duplicateItem(target.blockIndex, target.itemIndex)
                    editing = null
                },
                onRemove = {
                    viewModel.removeItem(target.blockIndex, target.itemIndex)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }

    WorkoutEditorContent(
        state = state,
        estimatedMinutes = estimatedMinutes,
        onBack = leave,
        actions = WorkoutEditorActions(
            onName = viewModel::setName,
            onNote = viewModel::setNote,
            onAddBlock = viewModel::addBlock,
            onKindChange = viewModel::changeBlockKind,
            onRounds = viewModel::setRounds,
            onRestBetweenRounds = viewModel::setRestBetweenRounds,
            onRemoveBlock = viewModel::removeBlock,
            onDuplicateBlock = viewModel::duplicateBlock,
            onBlockLabel = viewModel::setBlockLabel,
            onRestAfter = viewModel::setBlockRestAfter,
            onMoveBlock = viewModel::moveBlock,
            onAddExercise = { pickerForBlock = it },
            onEditItem = { blockIndex, itemIndex -> editing = EditingItem(blockIndex, itemIndex) },
            onMoveItem = viewModel::moveItem,
            onSave = viewModel::save,
        ),
    )
}

/** Wszystkie zmiany bloków i okna edytora; treść jest bezstanowa, więc zrzut rysuje ją z fiksturą. */
class WorkoutEditorActions(
    val onName: (String) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onAddBlock: (BlockKind) -> Unit = {},
    val onKindChange: (Int, BlockKind) -> Unit = { _, _ -> },
    val onRounds: (Int, Int) -> Unit = { _, _ -> },
    val onRestBetweenRounds: (Int, Int) -> Unit = { _, _ -> },
    val onRemoveBlock: (Int) -> Unit = {},
    val onDuplicateBlock: (Int) -> Unit = {},
    val onBlockLabel: (Int, String) -> Unit = { _, _ -> },
    val onRestAfter: (Int, Int?) -> Unit = { _, _ -> },
    val onMoveBlock: (Int, Int) -> Unit = { _, _ -> },
    val onAddExercise: (Int) -> Unit = {},
    val onEditItem: (Int, Int) -> Unit = { _, _ -> },
    val onMoveItem: (Int, Int, Int) -> Unit = { _, _, _ -> },
    val onSave: () -> Unit = {},
)

@Composable
fun WorkoutEditorContent(
    state: EditorUiState,
    estimatedMinutes: Int,
    actions: WorkoutEditorActions,
    onBack: () -> Unit,
) {
    // Ustawienia bloku zwinięte na start: na karcie bloku zostają ćwiczenia, które zmienia się najczęściej.
    var openSettings by rememberSaveable { mutableStateOf(listOf<Int>()) }
    ScreenScaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .readableWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
            ) {
                // Trening tylko do odczytu: `save` i tak nic nie robi, więc przycisk by kłamał.
                if (state.readOnly == null && !state.notFound) PrimaryAction("Zapisz trening", onClick = actions.onSave)
            }
        },
    ) { padding ->
        // Na tablecie treść w czytelnej szerokości (D-42): bloki to jedna kolumna kart.
        LazyColumn(
            modifier = Modifier.padding(padding).centeredReadable(),
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
        ) {
            item(key = "pasek") { BackTopBar(title = if (state.isNew) "Nowy trening" else "Edycja treningu", onBack = onBack) }
            if (state.notFound) {
                item(key = "brak") { EmptyState("Nie znaleziono treningu.") }
                return@LazyColumn
            }
            state.readOnly?.let { reason ->
                item(key = "tylko-odczyt") { EmptyState(reason) }
                return@LazyColumn
            }
            item(key = "naglowek") {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
                    TextInput("Nazwa treningu", state.name, actions.onName)
                    TextInput("Notatka (opcjonalnie)", state.note, actions.onNote, singleLine = false, minLines = 2)
                    if (state.blocks.isNotEmpty()) {
                        // Czas liczy WorkoutPlanner tak jak w szczegółach treningu, więc obie liczby się zgadzają.
                        Overline(
                            "${state.workCount} ${Announcer.plural(state.workCount, "seria", "serie", "serii")} do wykonania" +
                                if (estimatedMinutes > 0) " · ok. $estimatedMinutes min" else "",
                        )
                    }
                    state.errors.forEach { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            state.blocks.forEachIndexed { blockIndex, block ->
                item(key = "blok-$blockIndex") {
                    BlockCard(
                        block = block,
                        index = blockIndex,
                        blockCount = state.blocks.size,
                        exercises = state.exercises,
                        settingsOpen = blockIndex in openSettings,
                        onToggleSettings = {
                            openSettings = if (blockIndex in openSettings) openSettings - blockIndex else openSettings + blockIndex
                        },
                        actions = actions,
                    )
                }
            }

            item(key = "nowy-blok") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Overline(if (state.blocks.isEmpty()) "Pierwszy blok" else "Nowy blok")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryAction("Blok serii", onClick = { actions.onAddBlock(BlockKind.SETS) }, modifier = Modifier.weight(1f), height = 48.dp)
                        SecondaryAction("Blok obwodu", onClick = { actions.onAddBlock(BlockKind.CIRCUIT) }, modifier = Modifier.weight(1f), height = 48.dp)
                    }
                    Text(
                        text = "Serie: ćwiczenie po ćwiczeniu, każde w swoich seriach. Obwód: wszystkie po kolei, potem kolejna runda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Karta bloku: nazwa i streszczenie w nagłówku z menu ⋮, ćwiczenia z miniaturą i celem, „Dodaj ćwiczenie”
 * i zwijane ustawienia bloku na dole.
 */
@Composable
private fun BlockCard(
    block: Block,
    index: Int,
    blockCount: Int,
    exercises: Map<String, Exercise>,
    settingsOpen: Boolean,
    onToggleSettings: () -> Unit,
    actions: WorkoutEditorActions,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = cardColor(), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    Overline(block.label.ifBlank { "Blok ${index + 1}" })
                    Text(
                        text = blockSummary(block),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OverflowMenu(
                    actions = buildList {
                        add(MenuAction("Duplikuj blok", { actions.onDuplicateBlock(index) }))
                        if (index > 0) add(MenuAction("Blok wyżej", { actions.onMoveBlock(index, index - 1) }))
                        if (index < blockCount - 1) add(MenuAction("Blok niżej", { actions.onMoveBlock(index, index + 1) }))
                        add(MenuAction("Usuń blok", { actions.onRemoveBlock(index) }, destructive = true))
                    },
                    contentDescription = "Więcej o bloku ${index + 1}",
                )
            }
            Spacer(Modifier.height(6.dp))
            Column(modifier = Modifier.padding(end = 8.dp)) {
                ItemList(block, index, exercises, actions)
                QuietLink(
                    text = "Dodaj ćwiczenie",
                    onClick = { actions.onAddExercise(index) },
                    icon = AppIcons.Plus,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DisclosureRow(
                    title = "Ustawienia bloku",
                    summary = settingsSummary(block),
                    expanded = settingsOpen,
                    onToggle = onToggleSettings,
                ) {
                    TextInput("Nazwa bloku (opcjonalnie)", block.label, { actions.onBlockLabel(index, it) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPill("Serie", selected = block.kind == BlockKind.SETS, onClick = { actions.onKindChange(index, BlockKind.SETS) })
                        FilterPill("Obwód", selected = block.kind == BlockKind.CIRCUIT, onClick = { actions.onKindChange(index, BlockKind.CIRCUIT) })
                    }
                    if (block.kind == BlockKind.CIRCUIT) {
                        NumberStepper("Rundy", block.rounds, step = 1, range = 1..WorkoutEditing.MAX_ROUNDS, onChange = { actions.onRounds(index, it) })
                        NumberStepper(
                            label = "Przerwa między rundami",
                            value = block.restBetweenRoundsSec,
                            step = 15,
                            range = 0..WorkoutEditing.MAX_REST_SEC,
                            unit = "s",
                            onChange = { actions.onRestBetweenRounds(index, it) },
                        )
                    }
                    // Przerwa po bloku: null = domyślna minuta; pokazujemy ją jako liczbę, żeby autor widział, co zagra player.
                    NumberStepper(
                        label = "Przerwa po bloku",
                        value = block.restAfterSec ?: WorkoutPlanner.DEFAULT_BLOCK_REST_SEC,
                        step = 15,
                        range = 0..WorkoutEditing.MAX_REST_SEC,
                        unit = "s",
                        onChange = { actions.onRestAfter(index, it) },
                    )
                }
            }
        }
    }
}

/** Ćwiczenia bloku z przeciąganiem za uchwyt; tapnięcie wiersza otwiera arkusz ćwiczenia. */
@Composable
private fun ItemList(block: Block, blockIndex: Int, exercises: Map<String, Exercise>, actions: WorkoutEditorActions) {
    if (block.items.isEmpty()) {
        Text(
            "Blok jest pusty.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }
    // Przeciąganie „na żywo”: po przekroczeniu wysokości wiersza od razu zamieniamy elementy miejscami,
    // dzięki czemu nie trzeba liczyć docelowej pozycji przy upuszczeniu.
    var dragIndex by remember(block.items.size) { mutableStateOf<Int?>(null) }
    var dragOffset by remember(block.items.size) { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(1) }

    block.items.forEachIndexed { itemIndex, item ->
        val dragging = dragIndex == itemIndex
        val exercise = exercises[item.exerciseId]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowHeight = it.height.coerceAtLeast(1) }
                .zIndex(if (dragging) 1f else 0f)
                .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                .clip(RoundedCornerShape(14.dp))
                .then(if (dragging) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
                .clickable(role = Role.Button, onClickLabel = "Zmień cel") { actions.onEditItem(blockIndex, itemIndex) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = AppIcons.Drag,
                contentDescription = "Przeciągnij, aby zmienić kolejność",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .pointerInput(itemIndex, block.items.size) {
                        detectDragGestures(
                            onDragStart = {
                                dragIndex = itemIndex
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                dragIndex = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                dragIndex = null
                                dragOffset = 0f
                            },
                        ) { change, amount ->
                            change.consume()
                            val current = dragIndex ?: return@detectDragGestures
                            dragOffset += amount.y
                            when {
                                dragOffset > rowHeight && current < block.items.lastIndex -> {
                                    actions.onMoveItem(blockIndex, current, current + 1)
                                    dragIndex = current + 1
                                    dragOffset -= rowHeight
                                }
                                dragOffset < -rowHeight && current > 0 -> {
                                    actions.onMoveItem(blockIndex, current, current - 1)
                                    dragIndex = current - 1
                                    dragOffset += rowHeight
                                }
                            }
                        }
                    },
            )
            if (exercise != null) ExerciseThumbnail(exercise)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise?.namePl ?: item.exerciseId,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = WorkoutEditing.targetLabel(item, block.kind) + " · przerwa " +
                        (if (block.kind == BlockKind.SETS) "${item.restAfterSetSec} s" else "${item.restAfterItemSec} s") +
                        (item.weightKg?.let { " · " + formatKg(it) } ?: "") +
                        (if (item.note.isNotBlank()) " · ${item.note}" else ""),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (itemIndex < block.items.lastIndex) Hairline(modifier = Modifier.padding(start = 34.dp))
    }
    if (block.items.size > 1) {
        Text(
            text = "Kolejność zmienisz, przeciągając za uchwyt z lewej. Tapnięcie otwiera cel i resztę opcji.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Streszczenie w nagłówku karty: rodzaj, rundy i liczba ćwiczeń. */
private fun blockSummary(block: Block): String {
    val count = "${block.items.size} ${Announcer.plural(block.items.size, "ćwiczenie", "ćwiczenia", "ćwiczeń")}"
    return when (block.kind) {
        BlockKind.CIRCUIT -> "Obwód · ${block.rounds} ${Announcer.plural(block.rounds, "runda", "rundy", "rund")} · $count"
        BlockKind.SETS -> "Serie · $count"
    }
}

/** Streszczenie zwiniętych ustawień: przerwy, których nie widać na liście ćwiczeń. */
private fun settingsSummary(block: Block): String {
    val after = "${block.restAfterSec ?: WorkoutPlanner.DEFAULT_BLOCK_REST_SEC} s po bloku"
    return when (block.kind) {
        BlockKind.CIRCUIT -> "Nazwa, rodzaj, rundy · ${block.restBetweenRoundsSec} s między rundami · $after"
        BlockKind.SETS -> "Nazwa, rodzaj · $after"
    }
}

/** Liczba z przyciskami mniej/więcej; wartość jest przycinana do zakresu przez [WorkoutEditing]. */
@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    step: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    unit: String = "",
) {
    InlineStepper(
        label = label,
        value = if (unit.isBlank()) "$value" else "$value $unit",
        onLess = { onChange((value - step).coerceIn(range)) },
        onMore = { onChange((value + step).coerceIn(range)) },
        lessEnabled = value > range.first,
        moreEnabled = value < range.last,
    )
}

/**
 * Arkusz ćwiczenia na całą szerokość (projekt §3), nie wąskie okno dialogowe: etykiety stepperów nie łamią się
 * w środku słowa. Arkusz odsuwa treść od klawiatury (wcięcie IME w [BottomSheetDefaults.windowInsets]), pola przewijają
 * się nad nią, a „Anuluj” i „Gotowe” stoją pod przewijaną częścią, więc klawiatura ich nie zasłania. Tu mieszka też
 * zamiana, duplikat i usunięcie ćwiczenia (D-54), żeby wiersz w bloku nie miał czterech przycisków.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ItemTargetSheet(
    item: BlockItem,
    blockKind: BlockKind,
    exerciseName: String,
    onSave: (BlockItem) -> Unit,
    onReplace: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(item, stateSaver = BlockItemSaver) { mutableStateOf(item) }
    // Zmiana rodzaju celu przycina wartość: 20 minut na ergometrze nie może zostać jako 1200 powtórzeń.
    fun withKind(kind: TargetKind) = draft.copy(targetKind = kind, targetValue = draft.targetValue.coerceAtMost(WorkoutEditing.maxTarget(kind)))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(text = "Powtórzenia", selected = draft.targetKind == TargetKind.REPS, onClick = { draft = withKind(TargetKind.REPS) })
                FilterPill(text = "Czas", selected = draft.targetKind == TargetKind.SECONDS, onClick = { draft = withKind(TargetKind.SECONDS) })
                FilterPill(text = "AMRAP", selected = draft.targetKind == TargetKind.AMRAP, onClick = { draft = withKind(TargetKind.AMRAP) })
            }
            // Czas po siatce z domeny (5 s, 15 s, potem minuty) i od minuty jako „12:00”, jak w playerze.
            InlineStepper(
                label = if (draft.targetKind == TargetKind.REPS) "Powtórzenia" else "Czas",
                value = if (draft.targetKind == TargetKind.REPS || draft.targetValue < 60) {
                    if (draft.targetKind == TargetKind.REPS) "${draft.targetValue}" else "${draft.targetValue} s"
                } else {
                    formatDuration(draft.targetValue)
                },
                onLess = { draft = draft.copy(targetValue = WorkoutEditing.previousTarget(draft.targetKind, draft.targetValue)) },
                onMore = { draft = draft.copy(targetValue = WorkoutEditing.nextTarget(draft.targetKind, draft.targetValue)) },
                lessEnabled = draft.targetValue > 1,
                moreEnabled = draft.targetValue < WorkoutEditing.maxTarget(draft.targetKind),
            )
            if (blockKind == BlockKind.SETS) {
                NumberStepper(
                    label = "Serie",
                    value = draft.sets,
                    step = 1,
                    range = 1..WorkoutEditing.MAX_SETS,
                    onChange = { draft = draft.copy(sets = it) },
                )
                NumberStepper(
                    label = "Przerwa między seriami",
                    value = draft.restAfterSetSec,
                    step = 15,
                    range = 0..WorkoutEditing.MAX_REST_SEC,
                    unit = "s",
                    onChange = { draft = draft.copy(restAfterSetSec = it) },
                )
            }
            NumberStepper(
                label = if (blockKind == BlockKind.SETS) "Przerwa po ćwiczeniu" else "Przerwa po ćwiczeniu w rundzie",
                value = draft.restAfterItemSec,
                step = 15,
                range = 0..WorkoutEditing.MAX_REST_SEC,
                unit = "s",
                onChange = { draft = draft.copy(restAfterItemSec = it) },
            )
            // Ciężar po siatce z domeny: co 0,5 kg przy lekkich hantlach, dalej rzadziej; zero = bez ciężaru.
            val kg = draft.weightKg?.takeIf { it > 0.0 }
            InlineStepper(
                label = "Ciężar",
                value = kg?.let(::formatKg) ?: "bez",
                onLess = { draft = draft.copy(weightKg = WorkoutEditing.previousWeight(kg)) },
                onMore = { draft = draft.copy(weightKg = WorkoutEditing.nextWeight(kg)) },
                lessEnabled = kg != null,
                moreEnabled = (kg ?: 0.0) < WorkoutEditing.MAX_WEIGHT_KG,
            )
            Spacer(Modifier.height(8.dp))
            TextInput(
                label = "Wskazówka (pokaże się w trakcie)",
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it.take(WorkoutEditing.MAX_NOTE)) },
                singleLine = false,
                minLines = 2,
            )
            Hairline(modifier = Modifier.padding(top = 14.dp))
            FlowRow(modifier = Modifier.padding(top = 4.dp)) {
                QuietLink("Zmień ćwiczenie", onClick = onReplace, color = MaterialTheme.colorScheme.onSurface)
                QuietLink("Duplikuj", onClick = onDuplicate, color = MaterialTheme.colorScheme.onSurface)
                QuietLink("Usuń z bloku", onClick = onRemove, icon = AppIcons.Trash, color = MaterialTheme.colorScheme.error)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
            PrimaryAction("Gotowe", onClick = { onSave(draft) }, modifier = Modifier.weight(1f), height = 48.dp)
        }
    }
}

@Composable
private fun ExercisePickerDialog(
    exercises: List<Exercise>,
    owned: Set<Equipment>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Dodaj ćwiczenie",
) {
    var query by rememberSaveable { mutableStateOf("") }
    var onlyMine by rememberSaveable { mutableStateOf(false) }

    val filtered = remember(exercises, query, onlyMine, owned) {
        val byEquipment = if (onlyMine) exercises.filter { it.canDoWith(owned) } else exercises
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) byEquipment
        else byEquipment.filter { it.namePl.lowercase().contains(needle) || it.nameEn.lowercase().contains(needle) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Szukaj",
                    leadingIcon = {
                        Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    },
                )
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterPill("Tylko mój sprzęt", selected = onlyMine, onClick = { onlyMine = !onlyMine })
                    Text(
                        text = "${filtered.size} z ${exercises.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Lista bierze tylko to, co zostaje po nagłówku i „Zamknij”: w poziomie okno jest niskie
                // i sztywne 420 dp wypychało przycisk poza okno.
                LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
                    items(filtered, key = { it.id }) { exercise ->
                        ExerciseRow(
                            name = exercise.namePl,
                            secondary = exercise.equipment.joinToString { it.labelPl }.ifEmpty { "bez sprzętu" },
                            imagePath = exercise.images.lastOrNull(),
                            warning = if (exercise.canDoWith(owned)) null else "Nie masz tego sprzętu",
                            onClick = { onPick(exercise) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SecondaryAction("Zamknij", onClick = onDismiss, modifier = Modifier.align(Alignment.End), height = 44.dp)
            }
        }
    }
}
