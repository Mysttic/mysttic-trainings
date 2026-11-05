package pl.mysttic.trainings.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.engine.Announcer
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.SetProgression
import pl.mysttic.trainings.domain.plan.preset.PresetCatalog
import pl.mysttic.trainings.ui.adaptive.ReadableBox
import pl.mysttic.trainings.ui.adaptive.readableWidth
import pl.mysttic.trainings.ui.common.BlockHeader
import pl.mysttic.trainings.ui.common.DifficultySelector
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.ExerciseRow
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.ProgressionCard
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.theme.Dimens
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import pl.mysttic.trainings.ui.common.TextInput
import pl.mysttic.trainings.ui.common.formatKg
import pl.mysttic.trainings.domain.plan.PlanNotes
import androidx.compose.foundation.layout.Column

/**
 * Co jest w treningu: bloki, cele i przerwy, plus propozycja podbicia celów.
 * Poziom trudności zmienia się tutaj i działa od razu — skalowanie robi WorkoutPlanner, nie ten ekran.
 */
@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onEdit: (String) -> Unit = {},
    onDeleted: () -> Unit = {},
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val workout = state.workout
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInProgressDialog by rememberSaveable { mutableStateOf(false) }
    // Otwarta notatka do ćwiczenia (D-44): id ćwiczenia, nazwa i tekst przeżywają obrót.
    var noteExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var noteExerciseName by rememberSaveable { mutableStateOf("") }
    var noteDraft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(deleted) { if (deleted) onDeleted() }

    noteExerciseId?.let { exerciseId ->
        AlertDialog(
            onDismissRequest = { noteExerciseId = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(noteExerciseName, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        text = "Notatka wraca przy tym ćwiczeniu w każdym treningu i tygodniu tego planu, także w playerze.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextInput(
                        label = "Notatka w tym planie",
                        value = noteDraft,
                        onValueChange = { noteDraft = it.take(PlanNotes.MAX_NOTE) },
                        singleLine = false,
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setPlanNote(exerciseId, noteDraft); noteExerciseId = null }) { Text("Zapisz") }
            },
            dismissButton = {
                TextButton(onClick = { noteExerciseId = null }) { Text("Anuluj") }
            },
        )
    }

    if (showDeleteDialog && workout != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Usunąć trening?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Zniknie z listy. Zapisane sesje w Historii zostają.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Zostaw") } },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) {
                    Text("Usuń trening", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    // Start przy innym treningu w toku zamknąłby tamten jako przerwany, bez podsumowania (D-16): najpierw pytamy.
    val running = state.otherInProgress
    // Tamten trening skończył się sam (ostatnia seria na czas): okno nie może wrócić przy następnej sesji w toku.
    LaunchedEffect(running, state.loading) { if (!state.loading && running == null) showInProgressDialog = false }
    if (showInProgressDialog && workout != null && running != null) {
        val runningId = running.workoutId
        AlertDialog(
            onDismissRequest = { showInProgressDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Masz trening w toku", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "„${running.workoutName}” jeszcze trwa. Start tego treningu przerwie tamten bez podsumowania, " +
                        "a w Historii zostanie jako przerwany.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            // Ta sama kolejność co w pozostałych oknach: po prawej bezpieczny wybór, po lewej przerwanie ogniem.
            confirmButton = {
                if (runningId != null) {
                    TextButton(onClick = { showInProgressDialog = false; onStart(runningId) }) { Text("Wróć do treningu") }
                } else {
                    TextButton(onClick = { showInProgressDialog = false }) { Text("Zostaw") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showInProgressDialog = false; onStart(workout.id) }) {
                    Text("Zacznij ten", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (workout != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .readableWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Gotowy program i plan od kogoś zmienia się przez kopię (D-37, D-42), reszta w edytorze.
                    if (state.canEdit) {
                        SecondaryAction("Edytuj", onClick = { onEdit(workout.id) }, modifier = Modifier.width(120.dp), height = Dimens.primaryButton)
                    }
                    val waitingPlan = state.waitingPlanName
                    if (waitingPlan != null) {
                        // Jak w widoku planu: plan, który czeka, nie ma startu, bo sesja odhaczyłaby dzień w nim, a nie w aktywnym.
                        Text(
                            text = "Plan „$waitingPlan” czeka. Ten trening zaczniesz, gdy ustawisz plan jako aktywny.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PrimaryAction(
                            text = if (state.resumes) "Wróć do treningu" else "Start · ok. ${state.estimatedMinutes} min",
                            onClick = { if (state.otherInProgress != null) showInProgressDialog = true else onStart(workout.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (workout == null) {
            if (!state.loading) EmptyState("Nie znaleziono treningu.", Modifier.padding(padding))
            return@Scaffold
        }
        ReadableBox(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            ) {
                item(key = "naglowek") {
                    IconButton(onClick = onBack, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Overline("Trening")
                    Spacer(Modifier.height(8.dp))
                    Text(workout.name, style = MaterialTheme.typography.headlineMedium)
                    if (workout.note.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(workout.note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${state.workSteps} ${Announcer.plural(state.workSteps, "seria", "serie", "serii")} · ok. ${state.estimatedMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Dimens.sectionGap))
                    if (state.fixed) {
                        Text(
                            text = fixedNote(state.ownerSource),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        DifficultySelector(selected = state.difficulty, onSelect = viewModel::setDifficulty, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (state.suggestions.isNotEmpty()) {
                    item(key = "progresja") {
                        Spacer(Modifier.height(Dimens.sectionGap))
                        ProgressionCard(
                            text = SetProgression.cardText(state.suggestions),
                            actionLabel = "Zastosuj",
                            onAction = viewModel::applySuggestions,
                            boxed = true,
                        )
                    }
                }
                state.blocks.forEachIndexed { index, block ->
                    item(key = "blok-$index") { BlockHeader(block.title, block.param, first = index == 0 && state.suggestions.isEmpty()) }
                    items(block.items.size, key = { i -> "blok-$index-poz-$i" }) { i ->
                        val item = block.items[i]
                        ExerciseRow(
                            name = item.name,
                            secondary = listOfNotNull(item.targetText, item.weightKg?.let(::formatKg), item.restText).joinToString(" · "),
                            imagePath = item.imagePath,
                            warning = if (item.missingEquipment.isEmpty()) {
                                null
                            } else {
                                "Brakuje: ${item.missingEquipment.joinToString { it.labelPl }}" +
                                    if (item.substitutes.isNotEmpty()) " → ${item.substitutes.first().namePl}" else ""
                            },
                            onClick = { onOpenExercise(item.exerciseId) },
                        )
                        // Wskazówka autora planu (D-42), jak w playerze: to informacja, więc tertiary.
                        if (item.hint.isNotBlank()) {
                            Text(
                                text = item.hint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(start = Dimens.thumbSmall + Dimens.rowGap, bottom = 6.dp),
                            )
                        }
                        item.substitutedFrom?.let {
                            Text(
                                text = "wariant dla Twojego poziomu (zamiast: $it)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Dimens.thumbSmall + Dimens.rowGap, bottom = 6.dp),
                            )
                        }
                        if (state.programId != null) {
                            PlanNoteLine(
                                note = item.planNote,
                                onClick = {
                                    noteExerciseId = item.exerciseId
                                    noteExerciseName = item.name
                                    noteDraft = item.planNote
                                },
                            )
                        }
                    }
                }
                item(key = "stopka") {
                    Spacer(Modifier.height(Dimens.sectionGap))
                    val footer = footerText(state)
                    if (footer != null) {
                        Text(
                            text = footer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text("Usuń trening", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Opis stałych celów pod nazwą treningu. Bloki `fixed` ma gotowy program (D-37) i plan dosłowny (D-42):
 * własny z wyłączonym dopasowaniem albo plan od kogoś. Stałym protokołem jest tylko program.
 */
private fun fixedNote(source: PlanSource?): String = when (source) {
    PlanSource.CUSTOM, PlanSource.WIZARD -> "Plan bez dopasowania: cele dokładnie jak wpisane, poziom i trener ich nie zmieniają"
    PlanSource.IMPORTED -> "Plan od kogoś: cele dokładnie jak w pliku, poziom i trener ich nie zmieniają"
    PlanSource.PRESET, null -> PresetCatalog.FIXED_NOTE
}

/** Dlaczego trening nie ma „Usuń trening”; null, gdy da się go skasować. */
private fun footerText(state: WorkoutDetailUiState): String? = when {
    state.ownerSource == PlanSource.PRESET || (state.fixed && state.ownerSource == null) ->
        "Trening z gotowego programu: stały protokół, bez edycji i kasowania. Etapy idą w kolejce cyklu."
    state.ownerSource == PlanSource.IMPORTED ->
        "Trening z planu od kogoś: cele jak w pliku, bez zmian w miejscu. " +
            "Zmienisz go w kopii planu, „Duplikuj i zmień” jest w menu planu."
    state.inPlan ->
        "Trening z kolejki planu: dzień planu wskazuje właśnie na niego, więc nie kasujemy go osobno. " +
            "Znika razem z planem, „Usuń plan” jest w menu planu."
    else -> null
}

/** Notatka do ćwiczenia w planie (D-44) pod wierszem ćwiczenia: sam tekst, a bez notatki cichy odsyłacz do jej dopisania. */
@Composable
private fun PlanNoteLine(note: String, onClick: () -> Unit) {
    val start = Dimens.thumbSmall + Dimens.rowGap
    if (note.isBlank()) {
        TextButton(onClick = onClick, modifier = Modifier.padding(start = start - 12.dp)) {
            Text("+ Notatka w tym planie", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = start, bottom = 8.dp)
                .clickable(onClickLabel = "Zmień notatkę", onClick = onClick),
        )
    }
}
