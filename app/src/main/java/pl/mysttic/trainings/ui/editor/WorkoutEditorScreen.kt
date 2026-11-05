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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import pl.mysttic.trainings.ui.common.formatKg
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.editor.WorkoutEditing
import pl.mysttic.trainings.domain.engine.Announcer
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.ui.adaptive.ReadableBox
import pl.mysttic.trainings.ui.adaptive.readableWidth
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.ExerciseRow
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.InlineStepper
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.SearchField
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.TextInput
import pl.mysttic.trainings.ui.common.formatDuration
import pl.mysttic.trainings.ui.common.ScreenScaffold
import pl.mysttic.trainings.ui.theme.Dimens
import kotlinx.serialization.json.Json

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

@OptIn(ExperimentalMaterial3Api::class)
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
                onDismiss = { editing = null },
            )
        }
    }

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
                if (state.readOnly == null) PrimaryAction("Zapisz trening", onClick = viewModel::save)
            }
        },
    ) { padding ->
        if (state.notFound) {
            EmptyState("Nie znaleziono treningu.", Modifier.padding(padding))
            return@ScreenScaffold
        }
        state.readOnly?.let { reason ->
            EmptyState(reason, Modifier.padding(padding))
            return@ScreenScaffold
        }
        // Na tablecie treść w czytelnej szerokości (D-42), na telefonie bez zmian.
        ReadableBox(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
            ) {
                item {
                    IconButton(onClick = leave, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = if (state.isNew) "Nowy trening" else "Edycja treningu",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(Dimens.sectionGap))
                    TextInput("Nazwa treningu", state.name, viewModel::setName)
                    Spacer(Modifier.height(Dimens.sectionGap))
                    TextInput("Notatka (opcjonalnie)", state.note, viewModel::setNote, singleLine = false, minLines = 2)
                    if (state.blocks.isNotEmpty()) {
                        // Czas liczy WorkoutPlanner tak jak w szczegółach treningu, więc obie liczby się zgadzają.
                        Text(
                            text = "${state.workCount} ${Announcer.plural(state.workCount, "seria", "serie", "serii")} do wykonania" +
                                if (estimatedMinutes > 0) " · ok. $estimatedMinutes min" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    state.errors.forEach { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                state.blocks.forEachIndexed { blockIndex, block ->
                    item(key = "block-$blockIndex") {
                        BlockCard(
                            block = block,
                            index = blockIndex,
                            blockCount = state.blocks.size,
                            exerciseName = { id -> state.exercises[id]?.namePl ?: id },
                            onKindChange = { viewModel.changeBlockKind(blockIndex, it) },
                            onRounds = { viewModel.setRounds(blockIndex, it) },
                            onRestBetweenRounds = { viewModel.setRestBetweenRounds(blockIndex, it) },
                            onRemoveBlock = { viewModel.removeBlock(blockIndex) },
                            onDuplicateBlock = { viewModel.duplicateBlock(blockIndex) },
                            onLabel = { viewModel.setBlockLabel(blockIndex, it) },
                            onRestAfter = { viewModel.setBlockRestAfter(blockIndex, it) },
                            onMoveBlock = { to -> viewModel.moveBlock(blockIndex, to) },
                            onAddExercise = { pickerForBlock = blockIndex },
                            onEditItem = { itemIndex -> editing = EditingItem(blockIndex, itemIndex) },
                            onMoveItem = { from, to -> viewModel.moveItem(blockIndex, from, to) },
                            onRemoveItem = { itemIndex -> viewModel.removeItem(blockIndex, itemIndex) },
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryAction("Blok serii", onClick = { viewModel.addBlock(BlockKind.SETS) }, modifier = Modifier.weight(1f), height = 48.dp)
                        SecondaryAction("Blok obwodu", onClick = { viewModel.addBlock(BlockKind.CIRCUIT) }, modifier = Modifier.weight(1f), height = 48.dp)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun BlockCard(
    block: Block,
    index: Int,
    blockCount: Int,
    exerciseName: (String) -> String,
    onKindChange: (BlockKind) -> Unit,
    onRounds: (Int) -> Unit,
    onRestBetweenRounds: (Int) -> Unit,
    onRemoveBlock: () -> Unit,
    onDuplicateBlock: () -> Unit,
    onLabel: (String) -> Unit,
    onRestAfter: (Int?) -> Unit,
    onMoveBlock: (Int) -> Unit,
    onAddExercise: () -> Unit,
    onEditItem: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Overline(block.label.ifBlank { "Blok ${index + 1}" }, modifier = Modifier.weight(1f))
                // Czytnik ekranu przeczytałby „razy dwa”; opis mówi, co przycisk robi, jak przy sąsiednich ikonach.
                TextButton(onClick = onDuplicateBlock, modifier = Modifier.semantics { contentDescription = "Duplikuj blok" }) {
                    Text("×2", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { onMoveBlock(index - 1) }, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Blok wyżej")
                }
                IconButton(onClick = { onMoveBlock(index + 1) }, enabled = index < blockCount - 1) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Blok niżej")
                }
                IconButton(onClick = onRemoveBlock) {
                    Icon(Icons.Filled.Delete, contentDescription = "Usuń blok", tint = MaterialTheme.colorScheme.error)
                }
            }

            TextInput("Nazwa bloku (opcjonalnie)", block.label, onLabel)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Serie", selected = block.kind == BlockKind.SETS, onClick = { onKindChange(BlockKind.SETS) })
                FilterPill("Obwód", selected = block.kind == BlockKind.CIRCUIT, onClick = { onKindChange(BlockKind.CIRCUIT) })
            }

            if (block.kind == BlockKind.CIRCUIT) {
                NumberStepper("Rundy", block.rounds, step = 1, range = 1..WorkoutEditing.MAX_ROUNDS, onChange = onRounds)
                NumberStepper(
                    label = "Przerwa między rundami",
                    value = block.restBetweenRoundsSec,
                    step = 15,
                    range = 0..WorkoutEditing.MAX_REST_SEC,
                    unit = "s",
                    onChange = onRestBetweenRounds,
                )
            }

            // Przerwa po bloku: null = domyślna minuta; pokazujemy ją jako liczbę, żeby autor planu widział, co zagra player.
            val restAfter = block.restAfterSec ?: WorkoutPlanner.DEFAULT_BLOCK_REST_SEC
            NumberStepper(
                label = "Przerwa po bloku",
                value = restAfter,
                step = 15,
                range = 0..WorkoutEditing.MAX_REST_SEC,
                unit = "s",
                onChange = { onRestAfter(it) },
            )
            Spacer(Modifier.height(4.dp))
            // Przeciąganie „na żywo”: po przekroczeniu wysokości wiersza od razu zamieniamy elementy miejscami,
            // dzięki czemu nie trzeba liczyć docelowej pozycji przy upuszczeniu.
            var dragIndex by remember(block.items.size) { mutableStateOf<Int?>(null) }
            var dragOffset by remember(block.items.size) { mutableFloatStateOf(0f) }
            var rowHeight by remember { mutableIntStateOf(1) }

            block.items.forEachIndexed { itemIndex, item ->
                val dragging = dragIndex == itemIndex
                Hairline()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { rowHeight = it.height.coerceAtLeast(1) }
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                        .then(if (dragging) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
                        .clickable { onEditItem(itemIndex) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Przeciągnij, aby zmienić kolejność",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 12.dp)
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
                                            onMoveItem(current, current + 1)
                                            dragIndex = current + 1
                                            dragOffset -= rowHeight
                                        }
                                        dragOffset < -rowHeight && current > 0 -> {
                                            onMoveItem(current, current - 1)
                                            dragIndex = current - 1
                                            dragOffset += rowHeight
                                        }
                                    }
                                }
                            },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exerciseName(item.exerciseId),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
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
                    IconButton(onClick = { onEditItem(itemIndex) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Zmień cel")
                    }
                    IconButton(onClick = { onRemoveItem(itemIndex) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Usuń ćwiczenie", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (block.items.size > 1) {
                Text(
                    text = "Kolejność zmienisz, przeciągając za uchwyt z lewej.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (block.items.isEmpty()) {
                Text(
                    "Blok jest pusty.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            SecondaryAction("Dodaj ćwiczenie", onClick = onAddExercise, modifier = Modifier.fillMaxWidth(), height = 48.dp)
        }
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
 * Okno pozycji jako arkusz na całą szerokość (projekt §3), nie wąskie okno dialogowe: etykiety stepperów nie łamią się
 * w środku słowa. Arkusz odsuwa treść od klawiatury (wcięcie IME w [BottomSheetDefaults.windowInsets]), pola przewijają
 * się nad nią, a „Anuluj” i „Gotowe” stoją pod przewijaną częścią, więc klawiatura ich nie zasłania.
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
                FilterPill(
                    text = "Powtórzenia",
                    selected = draft.targetKind == TargetKind.REPS,
                    onClick = { draft = withKind(TargetKind.REPS) },
                )
                FilterPill(
                    text = "Czas",
                    selected = draft.targetKind == TargetKind.SECONDS,
                    onClick = { draft = withKind(TargetKind.SECONDS) },
                )
                FilterPill(
                    text = "AMRAP",
                    selected = draft.targetKind == TargetKind.AMRAP,
                    onClick = { draft = withKind(TargetKind.AMRAP) },
                )
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                TextButton(onClick = onReplace) { Text("Zmień ćwiczenie") }
                TextButton(onClick = onDuplicate) { Text("Duplikuj") }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
            TextButton(onClick = { onSave(draft) }) { Text("Gotowe") }
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
                SearchField(value = query, onValueChange = { query = it }, placeholder = "Szukaj")
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
