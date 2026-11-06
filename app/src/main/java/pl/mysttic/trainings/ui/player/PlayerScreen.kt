package pl.mysttic.trainings.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.ui.common.AppIcons
import pl.mysttic.trainings.ui.common.IconLabelButton
import pl.mysttic.trainings.ui.common.SettingRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.coach.ExerciseFeedback
import pl.mysttic.trainings.domain.engine.Announcer
import pl.mysttic.trainings.domain.engine.Phase
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.service.SessionSummary
import pl.mysttic.trainings.ui.common.DifficultySelector
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.ExerciseIllustration
import pl.mysttic.trainings.ui.common.ExerciseImage
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.SearchField
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PlayerNumber
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.RpePicker
import pl.mysttic.trainings.ui.common.ProgressTrack
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.StatCellData
import pl.mysttic.trainings.ui.common.StatRow
import pl.mysttic.trainings.ui.common.formatDuration
import pl.mysttic.trainings.ui.adaptive.AppLayout
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.domain.engine.PlayerSnapshot
import pl.mysttic.trainings.ui.common.formatKg
import pl.mysttic.trainings.ui.common.cardColor
import pl.mysttic.trainings.ui.theme.Dimens
import pl.mysttic.trainings.ui.theme.PlayerCountdown
import pl.mysttic.trainings.ui.theme.PlayerRest
import pl.mysttic.trainings.ui.theme.PlayerSetValue

/**
 * Prowadzenie po treningu (D-32): wielka liczba czytelna z półtora metra, jedna akcja pod kciukiem,
 * reszta w drugim rzędzie. Liczba jest w `onSurface` wszędzie poza odliczaniem — start ma moment gorąca.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onExit: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var showAbortDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Android 13+: bez POST_NOTIFICATIONS serwis działa, ale powiadomienia z odliczaniem nie widać.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.ensureStarted()
    }
    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.ensureStarted()
    }

    // Klawisz głośności działa jak „Zrobione” tylko na tym ekranie (M6).
    DisposableEffect(Unit) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }

    val view = LocalView.current
    DisposableEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val summary = state.summary
    // Wstecz nie przerywa treningu: silnik żyje w serwisie, powrót przez powiadomienie albo kartę na „Dziś”.
    BackHandler(enabled = summary == null) { onBack() }
    // Na podsumowaniu wstecz pytamy: opinie dla trenera (D-35) nie mają drugiego wejścia, a „Zakończ” je zapisuje.
    BackHandler(enabled = summary != null) { showExitDialog = true }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Wyjść bez zapisania?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "Trening jest już w Historii. Przepadną ocena, notatka i opinie o ćwiczeniach — " +
                        "zapisuje je dopiero „Zakończ”.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = { TextButton(onClick = { showExitDialog = false }) { Text("Wróć do podsumowania") } },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false; onExit() }) {
                    Text("Wyjdź bez zapisu", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (showAbortDialog) {
        val snapshot = state.snapshot
        AlertDialog(
            onDismissRequest = { showAbortDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Przerwać trening?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    // Serie zapisane w sesji, nie pozycja w treningu: pominięte serie do historii nie trafiają.
                    text = if (snapshot != null) {
                        "Zapiszemy ${state.loggedSets} z ${snapshot.workTotal} serii. " +
                            "Trening wróci do kolejki, nic nie przepadnie."
                    } else {
                        "Wykonane serie zostaną zapisane, trening wróci do kolejki."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = { TextButton(onClick = { showAbortDialog = false }) { Text("Wróć do treningu") } },
            dismissButton = {
                TextButton(onClick = { showAbortDialog = false; viewModel.abort() }) { Text("Przerwij i zapisz") }
            },
        )
    }

    if (showMenu && summary == null) {
        PlayerMenu(
            state = state,
            onDifficulty = viewModel::changeDifficulty,
            onAbort = { showMenu = false; showAbortDialog = true },
            onDismiss = { showMenu = false },
        )
    }

    when {
        state.error != null -> EmptyState(state.error ?: "")
        summary != null -> SummaryContent(summary, viewModel, onExit, fixed = state.fixed)
        state.loading || state.snapshot == null -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        else -> PlayerContent(state, remember(viewModel) { PlayerActions.of(viewModel) }, onAbort = { showAbortDialog = true }, onMenu = { showMenu = true })
    }
}

/**
 * Sterowanie playerem bez ViewModelu: treść kroku i przyciski dostają same funkcje, więc zrzut (D-40) narysuje
 * player z migawką silnika, a w aplikacji każda funkcja idzie wprost do `PlayerController` przez `PlayerViewModel`.
 */
class PlayerActions(
    val done: (Int?) -> Unit = {},
    val count: (Int) -> Unit = {},
    val skip: () -> Unit = {},
    val back: () -> Unit = {},
    val pause: () -> Unit = {},
    val resume: () -> Unit = {},
    val addRest: (Int) -> Unit = {},
) {
    companion object {
        fun of(viewModel: PlayerViewModel) = PlayerActions(
            done = { viewModel.done(it) },
            count = viewModel::count,
            skip = viewModel::skip,
            back = viewModel::back,
            pause = viewModel::pause,
            resume = viewModel::resume,
            addRest = viewModel::addRest,
        )
    }
}

/**
 * Menu treningu (D-52): to, co rzadko potrzebne w trakcie serii, schodzi z ekranu do arkusza pod ⋮ — poziom
 * trudności (dalej zmienny w każdej chwili, D-06) i przerwanie treningu. Ekran zostaje dla liczby i jednej akcji.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerMenu(
    state: PlayerUiState,
    onDifficulty: (Difficulty) -> Unit,
    onAbort: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Overline("Poziom trudności")
            if (state.fixed) {
                // Stały protokół (D-37): poziom nie ma tu nic do roboty, więc nie kusimy przełącznikiem.
                Text(
                    text = "Stały protokół: poziom nie zmienia celów",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DifficultySelector(
                    selected = state.difficulty,
                    onSelect = onDifficulty,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Silnik przelicza od razu wszystko, czego jeszcze nie zrobiono, także trwającą serię
                // (`WorkoutPlanner.merge`, D-06); napis nie może obiecywać „od następnej serii”.
                Text(
                    text = "Zrobione serie zostają, reszta, także ta seria, przelicza się od razu. Teraz: ${state.difficulty.labelPl.lowercase()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Hairline(modifier = Modifier.padding(top = 4.dp))
            val snapshot = state.snapshot
            SettingRow(
                title = "Przerwij trening",
                description = if (snapshot != null) {
                    "Zapiszemy ${state.loggedSets} z ${snapshot.workTotal} serii, trening wróci do kolejki."
                } else {
                    "Wykonane serie zostaną zapisane, trening wróci do kolejki."
                },
                onClick = onAbort,
                trailing = {
                    Icon(AppIcons.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                },
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Wróć do treningu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun PlayerContent(state: PlayerUiState, actions: PlayerActions, onAbort: () -> Unit, onMenu: () -> Unit) {
    val snapshot = state.snapshot ?: return
    val step = snapshot.step
    val controls: @Composable () -> Unit = {
        Controls(
            step = step,
            paused = snapshot.paused,
            phase = snapshot.phase,
            pendingCount = snapshot.pendingCount,
            actions = actions,
        )
    }

    val layout = LocalAppLayout.current
    // Jedno przewinięcie na krok i bez zapisu: każdy krok (i ekran po obrocie) zaczyna się od góry, od nazwy
    // i wielkiej liczby. Wspólny stan przenosił przewinięcie z przerwy na pracę i ucinał liczbę.
    val stepScroll = remember(snapshot.stepIndex) { ScrollState(0) }

    // Tablet i telefon w poziomie (D-42): po lewej sam krok na całą wysokość, bo 208 sp odliczania nie mieści się
    // pod nagłówkiem na telefonie; po prawej nagłówek i przyciski, bez przewijania do akcji.
    if (layout.playerSideBySide) {
        val compact = layout.heightDp < AppLayout.LOW_HEIGHT
        Row(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.screenPadding, vertical = if (compact) 8.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.screenPadding),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                StepContent(state, snapshot, step, scroll = stepScroll, compact = compact)
            }
            Column(
                modifier = Modifier.width(360.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                // Nagłówek z pytaniem o wznowienie bywa wyższy niż miejsce nad przyciskami: przewija się on, a nie akcje.
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    PlayerHeader(state, snapshot, actions, onAbort, onMenu)
                }
                Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                controls()
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = Dimens.screenPadding, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlayerHeader(state, snapshot, actions, onAbort, onMenu)
        Spacer(Modifier.height(12.dp))
        // Krok dostaje to, co zostaje po nagłówku i przyciskach: w niskim oknie przewija się treść, a akcje
        // (z „Przerwij trening”) zostają całe. Krótsza treść stoi pośrodku, jak wcześniej między dwoma odstępami.
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            StepContent(state, snapshot, step, scroll = stepScroll)
        }
        Spacer(Modifier.height(12.dp))
        controls()
    }
}

/**
 * Nagłówek playera (D-52): nazwa treningu, czas i licznik serii w jednej linii, menu ⋮ i pasek postępu.
 * Poziom trudności przeszedł do menu — w trakcie serii nikt go nie szuka, a zabierał wiersz nad liczbą.
 */
@Composable
private fun PlayerHeader(
    state: PlayerUiState,
    snapshot: PlayerSnapshot,
    actions: PlayerActions,
    onAbort: () -> Unit,
    onMenu: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.workoutName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Odstęp, żeby długa nazwa nie zlewała się z zegarem; skraca się nazwa, nie czas.
            Spacer(Modifier.width(12.dp))
            Text(
                text = formatDuration(snapshot.totalElapsedSec) + " · seria ${snapshot.workNumber} z ${snapshot.workTotal}" +
                    if (snapshot.paused) " · pauza" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            IconButton(onClick = onMenu, modifier = Modifier.offset(x = 10.dp)) {
                Icon(AppIcons.More, contentDescription = "Menu treningu", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(6.dp))
        ProgressTrack(fraction = snapshot.progress, height = Dimens.progressTrackPlayer)
        if (state.restoredFromCheckpoint && snapshot.paused) {
            // Faza minęła dawno (WorkoutEngine.RESTORE_OVERDUE_PAUSE_MS): użytkownik decyduje, czy kontynuować.
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = cardColor(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Wznowiono przerwany trening. Kontynuować od tego miejsca?", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PrimaryAction("Kontynuuj", onClick = actions.resume, modifier = Modifier.weight(1f), height = 48.dp)
                        SecondaryAction("Przerwij", onClick = onAbort, modifier = Modifier.weight(1f), height = 48.dp)
                    }
                }
            }
        }
    }
}

/**
 * Treść kroku. [compact] = telefon w poziomie: mniejsze odstępy i niższe zdjęcie, żeby liczba, „Dalej” w przerwie
 * i zdjęcie przy pracy mieściły się bez przewijania. Wielkie liczby zostają w pełnym rozmiarze.
 */
@Composable
private fun StepContent(
    state: PlayerUiState,
    snapshot: PlayerSnapshot,
    step: Step?,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            is Step.Countdown -> {
                Overline("Zaczynamy")
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                PlayerNumber(
                    text = snapshot.remainingSec?.toString().orEmpty(),
                    style = PlayerCountdown,
                    color = MaterialTheme.colorScheme.primary,
                )
                snapshot.nextWork?.let {
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                    Text("pierwsze ćwiczenie", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it.exerciseName, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(
                        text = Announcer.target(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is Step.Rest -> {
                Overline("Przerwa")
                Spacer(Modifier.height(8.dp))
                PlayerNumber(snapshot.remainingSec?.toString().orEmpty(), style = PlayerRest, color = MaterialTheme.colorScheme.onSurface)
                Text("sekund", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                snapshot.nextWork?.let { next ->
                    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                    NextUpCard(next, state)
                }
            }
            is Step.Work -> WorkStep(step, state, snapshot.remainingSec, snapshot.elapsedInPhaseSec, compact)
            null -> Unit
        }
    }
}

@Composable
private fun WorkStep(step: Step.Work, state: PlayerUiState, remainingSec: Int?, elapsedSec: Int, compact: Boolean) {
    // Pozycja w żarze nad nazwą, nazwa dużym tytułem (D-52): z półtora metra czyta się nazwę, nie wersaliki 11 sp.
    Overline(positionLabel(step))
    Spacer(Modifier.height(10.dp))
    Text(
        text = step.exerciseName,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(6.dp))
    val cadenceSec = step.cadenceSec
    when {
        step.targetKind == TargetKind.REPS -> BigValue(
            value = step.targetValue.toString(),
            unit = Announcer.plural(step.targetValue, "powtórzenie", "powtórzenia", "powtórzeń"),
        )
        step.targetKind == TargetKind.AMRAP -> BigValue(
            value = clockValue(remainingSec),
            unit = if ((remainingSec ?: 0) >= 60) "ile dasz radę" else "s, ile dasz radę",
        )
        // Krok bez limitu (D-37): czas leci w górę, kończy go użytkownik.
        step.openEnded -> BigValue(value = clockValue(elapsedSec), unit = "jak najdłużej")
        // Krok z rytmem: liczymy oddechy, nie sekundy; czas do końca schodzi do małej linii pod spodem.
        cadenceSec != null && step.cadenceCount > 0 -> BigValue(
            value = minOf(elapsedSec / cadenceSec + 1, step.cadenceCount).toString(),
            unit = "oddech z ${step.cadenceCount}",
        )
        else -> BigValue(
            value = clockValue(remainingSec),
            unit = if ((remainingSec ?: 0) >= 60) null else "sekund",
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = listOfNotNull(
            if (step.targetKind == TargetKind.REPS) "czas serii ${formatDuration(elapsedSec)}" else null,
            if (cadenceSec != null && remainingSec != null) "jeszcze ${shortTime(remainingSec)}" else null,
            step.weightKg?.let { formatKg(it) },
            step.blockLabel.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Wskazówka autora planu (D-42): ważniejsza niż opis z biblioteki, bo pisana dla tej osoby.
    if (step.note.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = step.note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
    // Notatka do ćwiczenia w tym planie (D-44), np. ciężar: wraca w każdym tygodniu planu.
    if (state.planNote.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Twoja notatka: ${state.planNote}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
    ExerciseIllustration(
        assetPath = state.currentExercise?.images?.lastOrNull(),
        maxHeight = when {
            compact -> 120.dp
            LocalAppLayout.current.playerSideBySide -> 240.dp
            else -> 186.dp
        },
        contentDescription = step.exerciseName,
    )
    state.currentExercise?.instructionsPl?.firstOrNull()?.let {
        Spacer(Modifier.height(14.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Karta „Dalej” w przerwie: przerwa przestaje być pustym ekranem z liczbą. */
@Composable
private fun NextUpCard(next: Step.Work, state: PlayerUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardColor(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseImage(
                assetPath = state.nextExercise?.images?.lastOrNull(),
                modifier = Modifier.size(Dimens.thumbLarge),
                contentDescription = next.exerciseName,
                shape = RoundedCornerShape(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Overline("Dalej")
                Spacer(Modifier.height(4.dp))
                Text(next.exerciseName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = Announcer.target(next) + " · " + positionLabel(next),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // W przerwie widać, co przygotować: np. ciężar z notatki do następnego ćwiczenia (D-44).
                if (state.nextPlanNote.isNotBlank()) {
                    Text(
                        text = state.nextPlanNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Obwód liczy rundy, serie proste numer serii; ta sama reguła co w powiadomieniu. */
private fun positionLabel(step: Step.Work): String =
    if (step.roundsInBlock > 1) "runda ${step.round} z ${step.roundsInBlock}" else "seria ${step.setIndex} z ${step.setsInItem}"

/** Wielka liczba czasu: do minuty same sekundy, dalej „1:30”; null (silnik nie biegnie) = pusto. */
private fun clockValue(sec: Int?): String = when {
    sec == null -> ""
    sec >= 60 -> formatDuration(sec)
    else -> sec.toString()
}

/** Czas w małym tekście: „40 s” albo „1:15”. */
private fun shortTime(sec: Int): String = if (sec >= 60) formatDuration(sec) else "$sec s"

/** Wielka liczba z jednostką; jednostka siada na linii bazowej, nie pod liczbą. */
@Composable
private fun BigValue(value: String, unit: String?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PlayerNumber(text = value, style = PlayerSetValue, color = MaterialTheme.colorScheme.onSurface)
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Liczba ma przyciętą interlinię, więc jednostkę trzeba podnieść do jej linii bazowej.
                modifier = Modifier.padding(bottom = 26.dp),
            )
        }
    }
}

@Composable
private fun Controls(
    step: Step?,
    paused: Boolean,
    phase: Phase,
    pendingCount: Int?,
    actions: PlayerActions,
) {
    val work = step as? Step.Work
    val amrap = work?.targetKind == TargetKind.AMRAP
    // REPS startuje od celu (najczęściej tyle wyjdzie). AMRAP liczy od zera to, co zrobione, a silnik pamięta
    // licznik, więc po powrocie na ekran nie zaczynamy od nowa i upływ czasu zaloguje właściwą liczbę.
    var actualReps by remember(step) { mutableIntStateOf(if (amrap) pendingCount ?: 0 else work?.targetValue ?: 0) }
    val counted = work != null && (work.targetKind == TargetKind.REPS || amrap)
    val setCount: (Int) -> Unit = { n ->
        actualReps = n
        if (amrap) actions.count(n)
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            counted -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepperButton("−", onClick = { if (actualReps > 0) setCount(actualReps - 1) })
                PrimaryAction(
                    text = "Zrobione ($actualReps)",
                    onClick = { actions.done(actualReps) },
                    modifier = Modifier.weight(1f),
                    height = Dimens.playerAction,
                )
                StepperButton("+", onClick = { setCount(actualReps + 1) })
            }
            // Krok bez limitu: nie ma serii do „zakończenia”, jest chwila, w której użytkownik przestaje.
            work != null && work.openEnded -> PrimaryAction("Zakończ", onClick = { actions.done(null) }, height = Dimens.playerAction)
            work != null -> PrimaryAction("Zakończ serię", onClick = { actions.done(null) }, height = Dimens.playerAction)
            step is Step.Rest -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryAction("+15 s", onClick = { actions.addRest(15) }, modifier = Modifier.weight(1f), height = Dimens.playerAction)
                RowButton("Pomiń", onClick = actions.skip, modifier = Modifier.weight(1f), height = Dimens.playerAction)
            }
            step is Step.Countdown -> PrimaryAction("Pomiń odliczanie", onClick = actions.skip, height = Dimens.playerAction)
        }
        // Drugi rząd (D-52): ciche przyciski z ikoną, bez wypełnienia. „Pomiń” w przerwie i odliczaniu stoi już
        // w pierwszym rzędzie, a „Przerwij trening” mieszka w menu ⋮.
        Row(modifier = Modifier.fillMaxWidth()) {
            IconLabelButton(AppIcons.Previous, "Wstecz", onClick = actions.back, modifier = Modifier.weight(1f))
            if (phase == Phase.RUNNING) {
                if (paused) {
                    IconLabelButton(AppIcons.Play, "Wznów", onClick = actions.resume, modifier = Modifier.weight(1f))
                } else {
                    IconLabelButton(AppIcons.Pause, "Pauza", onClick = actions.pause, modifier = Modifier.weight(1f))
                }
            }
            if (step !is Step.Rest && step !is Step.Countdown) {
                IconLabelButton(AppIcons.Next, "Pomiń", onClick = actions.skip, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * „−” i „+” przy liczbie powtórzeń: 58 x 62 dp, obwódka, bez wypełnienia (to nie jest akcja główna).
 * Przezroczyste, a nie w kolorze `surface`, bo pod playerem może leżeć gradient tła (D-50).
 */
@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(width = Dimens.playerStepper, height = Dimens.playerAction)
            .semantics { contentDescription = if (text == "+") "Jedno powtórzenie więcej" else "Jedno powtórzenie mniej" },
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (text == "+") AppIcons.Plus else AppIcons.Minus,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Przycisk drugiego rzędu: wypełnienie surfaceContainer, bez obwódki, jedna linia. */
@Composable
private fun RowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = Dimens.touchMin) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = cardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}

/** Mapa opinii do Bundle'a i z powrotem: płaska lista `id, opinia, id, opinia…`. */
internal val FeedbackSaver: Saver<Map<String, ExerciseFeedback>, Any> = listSaver(
    save = { map -> map.flatMap { (id, feedback) -> listOf(id, feedback.name) } },
    restore = { flat ->
        flat.chunked(2)
            .filter { it.size == 2 }
            .associate { (id, name) -> id to ExerciseFeedback.valueOf(name) }
    },
)

/**
 * Podsumowanie: liczby zamiast pochwał. Poprawa dostaje ogień, brak zmian zostaje w onSurfaceVariant —
 * brak progresji nie jest porażką (D-32).
 */
@Composable
private fun SummaryContent(summary: SessionSummary, viewModel: PlayerViewModel, onExit: () -> Unit, fixed: Boolean) {
    // rememberSaveable, nie remember: obrót ekranu nie może kasować oceny, notatki ani opinii —
    // nie ma ich gdzie wpisać po wyjściu z podsumowania.
    var rpe by rememberSaveable(summary.sessionId) { mutableStateOf<Int?>(null) }
    var note by rememberSaveable(summary.sessionId) { mutableStateOf("") }
    // Opinie dla trenera (D-35): domyślnie „Bez zmian”, zapis dopiero przy „Zakończ”, więc zmiana zdania nic nie psuje.
    var feedback by rememberSaveable(summary.sessionId, stateSaver = FeedbackSaver) {
        mutableStateOf<Map<String, ExerciseFeedback>>(emptyMap())
    }
    // Stały protokół (D-37): „Więcej” i „Mniej” nie mają na co działać, zostaje tylko wykluczenie.
    val feedbackOptions = if (fixed) listOf(ExerciseFeedback.NONE, ExerciseFeedback.NEVER) else ExerciseFeedback.entries
    Column(
        modifier = Modifier
            // Na tablecie czytelna szerokość (D-42): pigułki opinii i RPE, wynik przy nazwie i „Zakończ” nie rozlewają się na 1280 dp.
            .centeredReadable()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.screenPadding),
    ) {
        Overline(if (summary.status == SessionStatus.DONE) "Zrobione" else "Przerwane")
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (summary.status == SessionStatus.DONE) {
                "Cały trening w ${summary.durationSec / 60} min"
            } else {
                "Trening przerwany po ${summary.durationSec / 60} min"
            },
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(Modifier.height(Dimens.sectionGap))
        StatRow(
            cells = listOf(
                StatCellData(summary.setsDone.toString(), Announcer.plural(summary.setsDone, "seria", "serie", "serii")),
                StatCellData(
                    summary.totalReps.toString(),
                    Announcer.plural(summary.totalReps, "powtórzenie", "powtórzenia", "powtórzeń"),
                ),
                StatCellData(
                    summary.totalSeconds.toString(),
                    Announcer.plural(summary.totalSeconds, "sekunda pracy", "sekundy pracy", "sekund pracy"),
                ),
            ),
        )
        if (summary.perExercise.isNotEmpty() || summary.skipped.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Overline("Ćwiczenia")
            Spacer(Modifier.height(10.dp))
            summary.perExercise.forEach { d ->
                val unit = if (d.targetKind == TargetKind.SECONDS) "s" else "powt."
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${d.exerciseName} · ${d.sets}×",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${d.total} $unit",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = deltaLabel(d.delta),
                        style = MaterialTheme.typography.bodySmall,
                        color = if ((d.delta ?: 0) > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FeedbackPills(feedbackOptions, feedback[d.exerciseId]) { feedback = feedback + (d.exerciseId to it) }
                Hairline()
            }
            // Pominięte w całości (issue #22): bez wyniku, ale z opinią, bo „Nie chcę” ma sens właśnie tu.
            summary.skipped.forEach { skip ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = skip.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "pominięte",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FeedbackPills(feedbackOptions, feedback[skip.exerciseId]) { feedback = feedback + (skip.exerciseId to it) }
                Hairline()
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (fixed) {
                    "Cele w tym treningu są stałe, więc poziom i opinie ich nie zmieniają. „Nie chcę” zadziała od następnego planu z kreatora."
                } else {
                    "Więcej i mniej przesuwają cel o 2 powtórzenia albo 5 s we wszystkich kolejnych seriach tego ćwiczenia. " +
                        "„Nie chcę” zastępuje je w planie."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary.previousSetsDone == null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Pierwsze wykonanie tego treningu. Następnym razem zobaczysz porównanie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))
        Overline("Jak było")
        Spacer(Modifier.height(10.dp))
        RpePicker(value = rpe, onChange = { rpe = it })
        Spacer(Modifier.height(Dimens.sectionGap))
        SearchField(value = note, onValueChange = { note = it }, placeholder = "Notatka, jeśli coś warto zapamiętać")
        Spacer(Modifier.height(24.dp))
        PrimaryAction(
            text = "Zakończ",
            onClick = {
                val verdicts = feedback.filterValues { it != ExerciseFeedback.NONE }
                if (rpe != null || note.isNotBlank() || verdicts.isNotEmpty()) {
                    viewModel.saveFeedback(summary.sessionId, rpe, note, verdicts)
                }
                onExit()
            },
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** Pigułki opinii pod jednym ćwiczeniem podsumowania; brak wyboru = „Bez zmian”. */
@Composable
private fun FeedbackPills(options: List<ExerciseFeedback>, selected: ExerciseFeedback?, onSelect: (ExerciseFeedback) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            FilterPill(
                text = option.labelPl,
                selected = (selected ?: ExerciseFeedback.NONE) == option,
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun deltaLabel(value: Int?): String = when {
    value == null -> ""
    value > 0 -> "+$value"
    value < 0 -> value.toString()
    else -> "bez zmian"
}
