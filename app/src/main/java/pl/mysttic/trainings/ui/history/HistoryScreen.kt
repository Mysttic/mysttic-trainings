package pl.mysttic.trainings.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.engine.Announcer
import pl.mysttic.trainings.domain.model.Rpe
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.stats.ExerciseDelta
import pl.mysttic.trainings.domain.stats.HistoryPeriod
import pl.mysttic.trainings.domain.stats.PeriodStats
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.adaptive.TwoPane
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.ui.common.ActivityHeatmap
import pl.mysttic.trainings.ui.common.AppIcons
import pl.mysttic.trainings.ui.common.BackTopBar
import pl.mysttic.trainings.ui.common.CategoryDivider
import pl.mysttic.trainings.ui.common.CategoryGroup
import pl.mysttic.trainings.ui.common.DisclosureRow
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.MenuAction
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.OverflowMenu
import pl.mysttic.trainings.ui.common.RpePicker
import pl.mysttic.trainings.ui.common.ScreenTitle
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.SegmentedText
import pl.mysttic.trainings.ui.common.StatCellData
import pl.mysttic.trainings.ui.common.StatRow
import pl.mysttic.trainings.ui.common.TextInput
import pl.mysttic.trainings.ui.common.VolumeBarChart
import pl.mysttic.trainings.ui.common.formatDuration
import pl.mysttic.trainings.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Historia w języku nowego interfejsu (D-54, za D-52): na start tylko to, co się wydarzyło. Bez treningów jedno zdanie
 * zamiast pustych statystyk i filtrów. Okres steruje statystykami, a stan (ukończone, przerwane) filtruje listę, więc
 * stoi nad listą. Wykres wolumenu i kalendarz aktywności czekają w zwijanym wierszu „Postęp”. Lista idzie miesiącami,
 * w kartach jak kategorie Ustawień. Na tablecie w poziomie lista i szczegół sesji stoją obok siebie, jak w bibliotece.
 */

private val polish: Locale = Locale.forLanguageTag("pl")

/** Data sesji po polsku niezależnie od języka telefonu, jak nagłówek na „Dziś”: „czwartek, 5 listopada 2026, 18:04”. */
private val sessionDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", polish)
private val monthHeader = DateTimeFormatter.ofPattern("LLLL yyyy", polish)
private val hourMinute = DateTimeFormatter.ofPattern("HH:mm", polish)
private val shortWeekday = DateTimeFormatter.ofPattern("EEE", polish)

private fun zoned(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

@Composable
fun HistoryScreen(
    onOpenSession: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { HistoryActions(onPeriod = viewModel::setPeriod, onStatus = viewModel::setStatus) }
    if (!LocalAppLayout.current.twoPane) {
        HistoryContent(state = state, actions = actions, onOpenSession = onOpenSession)
        return
    }
    // Panel szczegółu ma własny ViewModel z kluczem: ten sam co ekran sesji, tylko z wyborem zamiast trasy (D-54).
    val detailViewModel: SessionDetailViewModel = hiltViewModel(key = DETAIL_PANE_KEY)
    val selected by detailViewModel.selected.collectAsStateWithLifecycle()
    val first = state.sessions.firstOrNull()?.id
    LaunchedEffect(selected, first) {
        if (selected == null && first != null) detailViewModel.select(first)
    }
    TwoPane(
        listWidth = HISTORY_PANE_WIDTH,
        list = { HistoryContent(state = state, actions = actions, onOpenSession = detailViewModel::select, selectedId = selected) },
        detail = {
            SessionDetail(
                viewModel = detailViewModel,
                onBack = null,
                // Po usunięciu panel przechodzi do najnowszej z pozostałych sesji, zamiast zostać pusty.
                onDeleted = { deletedId -> state.sessions.firstOrNull { it.id != deletedId }?.let { detailViewModel.select(it.id) } },
            )
        },
    )
}

/** Zmiany filtrów Historii; treść jest bezstanowa, więc zrzut rysuje ją z fiksturą. */
class HistoryActions(
    val onPeriod: (HistoryPeriod) -> Unit = {},
    val onStatus: (SessionStatus?) -> Unit = {},
)

/** [selectedId] podświetla sesję otwartą w panelu obok (tablet); na telefonie null. */
@Composable
fun HistoryContent(
    state: HistoryUiState,
    actions: HistoryActions,
    onOpenSession: (String) -> Unit,
    selectedId: String? = null,
) {
    var showProgress by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.centeredReadable(),
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.sectionGap + 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "naglowek") { ScreenTitle("Historia") }
        if (state.loading) return@LazyColumn
        if (state.totalCount == 0) {
            // Przed pierwszym treningiem filtry i zera to szum: jedno zdanie, jak „Dziś” na start (D-52).
            item(key = "pusto") {
                EmptyState("Jeszcze nic tu nie ma. Pierwszy ukończony trening pojawi się na tej liście.")
            }
            return@LazyColumn
        }
        item(key = "okres") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryPeriod.entries.forEach { period ->
                    FilterPill(text = period.labelPl, selected = state.period == period, onClick = { actions.onPeriod(period) })
                }
            }
        }
        item(key = "statystyki") { PeriodSummary(state.stats, state.period, state.streak, state.bestStreak) }
        if (state.weeks.isNotEmpty()) {
            item(key = "postep") {
                DisclosureRow(
                    title = "Postęp",
                    summary = progressSummary(state),
                    expanded = showProgress,
                    onToggle = { showProgress = !showProgress },
                ) {
                    Overline("Wolumen tygodniowy")
                    VolumeBarChart(state.weeks)
                    Text(
                        text = "Powtórzenia plus sekundy pracy przeliczone po 3 s na powtórzenie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Overline("Aktywność, 12 tygodni")
                    ActivityHeatmap(state.heatmap)
                }
            }
        }
        item(key = "stan") {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Overline("Treningi")
                SegmentedText(
                    options = STATUS_OPTIONS.map { it.first },
                    selectedIndex = STATUS_OPTIONS.indexOfFirst { it.second == state.status }.coerceAtLeast(0),
                    onSelect = { index -> actions.onStatus(STATUS_OPTIONS[index].second) },
                )
            }
        }
        if (state.sessions.isEmpty()) {
            item(key = "brak") { EmptyState("Nic nie pasuje do filtrów.") }
        }
        state.sessions.groupBy { monthHeader.format(zoned(it.startedAt)) }.forEach { (month, sessions) ->
            item(key = "miesiac-$month") { Overline(month, modifier = Modifier.padding(top = 8.dp)) }
            item(key = "sesje-$month") {
                CategoryGroup {
                    sessions.forEachIndexed { index, session ->
                        if (index > 0) CategoryDivider()
                        SessionRow(session, selected = session.id == selectedId, onClick = { onOpenSession(session.id) })
                    }
                }
            }
        }
        item(key = "stopka") { Spacer(Modifier.height(8.dp)) }
    }
}

/** Stan listy jak segmenty jednej pigułki: wszystkie, ukończone, przerwane. */
private val STATUS_OPTIONS: List<Pair<String, SessionStatus?>> = listOf(
    "Wszystkie" to null,
    "Ukończone" to SessionStatus.DONE,
    "Przerwane" to SessionStatus.ABANDONED,
)

/** Statystyki wybranego okresu; zawsze z sesji ukończonych, niezależnie od stanu wybranego nad listą. */
@Composable
private fun PeriodSummary(stats: PeriodStats, period: HistoryPeriod, streak: Int, bestStreak: Int) {
    Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline(if (period == HistoryPeriod.ALL) "Wszystkie ukończone" else "Ukończone, ostatnie ${period.labelPl}")
        if (stats.isEmpty) {
            Text(
                text = "Brak ukończonych treningów w tym okresie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        StatRow(
            cells = listOf(
                StatCellData("$streak ${if (streak == 1) "dzień" else "dni"}", "z rzędu", accent = streak > 0),
                StatCellData(stats.sessions.toString(), Announcer.plural(stats.sessions, "trening", "treningi", "treningów")),
                StatCellData(formatDuration(stats.durationSec), "w ruchu"),
            ),
        )
        Text(
            text = "${stats.sets} ${setsWord(stats.sets)} · ${Announcer.reps(stats.reps)} · ${stats.workSec} s pracy" +
                if (bestStreak > streak) " · rekord serii $bestStreak" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Streszczenie zwiniętego „Postępu”: ile dni z treningiem w kalendarzu i ile treningów w bieżącym tygodniu. */
private fun progressSummary(state: HistoryUiState): String {
    val activeDays = state.heatmap.count { it.sessions > 0 }
    val thisWeek = state.weeks.lastOrNull()?.sessions ?: 0
    return "$activeDays ${if (activeDays == 1) "dzień" else "dni"} z treningiem w 12 tygodniach · " +
        "w tym tygodniu $thisWeek"
}

/**
 * Wiersz sesji w karcie miesiąca: kafelek z dniem zamiast ikony, nazwa i jedno zdanie. Przerwana mówi to w kolorze
 * błędu. [selected] podświetla sesję otwartą w panelu obok (tablet).
 */
@Composable
private fun SessionRow(session: Session, selected: Boolean, onClick: () -> Unit) {
    val date = zoned(session.startedAt)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(shortWeekday.format(date).removeSuffix("."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.workoutName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    hourMinute.format(date),
                    session.durationSec?.let { formatDuration(it) },
                    "poziom ${session.difficulty.level}",
                    session.rpe?.let { "RPE $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.status == SessionStatus.ABANDONED) {
                Text("przerwany", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

// --- szczegóły sesji -------------------------------------------------------------------------------------------------

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    SessionDetail(viewModel = viewModel, onBack = onBack, onDeleted = { onBack() })
}

/** Stan edycji oceny i okno usuwania; treść rysuje [SessionDetailContent]. */
@Composable
private fun SessionDetail(viewModel: SessionDetailViewModel, onBack: (() -> Unit)?, onDeleted: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // Ta sama skala co w podsumowaniu (D-46). Stan startuje z bazy osobno dla każdej sesji, więc stara ocena spoza
    // skali (dawny suwak od 1 do 10) przetrwa zapis samej notatki, a sesja bez oceny zostaje bez niej.
    var rpe by rememberSaveable(session?.id) { mutableStateOf(session?.rpe) }
    var note by rememberSaveable(session?.id) { mutableStateOf(session?.note.orEmpty()) }

    LaunchedEffect(state.deleted) { if (state.deleted) viewModel.selected.value?.let(onDeleted) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Usunąć sesję?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Zniknie razem z zapisanymi seriami. Tego nie da się cofnąć.", style = MaterialTheme.typography.bodyLarge) },
            // Ta sama kolejność co w pozostałych oknach potwierdzenia: po prawej „nie rób nic”, kasowanie czerwienią.
            confirmButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Zostaw") } },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) {
                    Text("Usuń sesję", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    SessionDetailContent(
        state = state,
        rpe = rpe,
        note = note,
        actions = SessionDetailActions(
            onRpe = { rpe = it },
            onNote = { note = it },
            onSave = { viewModel.saveFeedback(rpe, note) },
            onDelete = { showDeleteDialog = true },
        ),
        onBack = onBack,
    )
}

class SessionDetailActions(
    val onRpe: (Int?) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/**
 * Szczegóły sesji (D-54): data, nazwa i stan, trzy liczby, porównanie z poprzednim razem, a serie po kolei
 * w zwijanym wierszu. Ocena i notatka na dole; usunięcie w menu ⋮, nie jako czerwony przycisk na ekranie.
 * Bez `onBack` (panel na tablecie) pasek zostaje bez strzałki.
 */
@Composable
fun SessionDetailContent(
    state: SessionDetailUiState,
    rpe: Int?,
    note: String,
    actions: SessionDetailActions,
    onBack: (() -> Unit)?,
) {
    val session = state.session
    var showSets by rememberSaveable(session?.id) { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.centeredReadable(),
        contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "pasek") {
            BackTopBar(title = null, onBack = onBack) {
                if (session != null) OverflowMenu(listOf(MenuAction("Usuń sesję", actions.onDelete, destructive = true)))
            }
        }
        if (session == null) {
            if (!state.loading) {
                item(key = "brak") {
                    EmptyState(if (onBack == null) "Tu pokażę trening wybrany z listy." else "Nie znaleziono sesji.")
                }
            }
            return@LazyColumn
        }
        item(key = "naglowek") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Overline(sessionDate.format(zoned(session.startedAt)))
                Text(session.workoutName, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "${statusLabel(session.status)} · poziom ${session.difficulty.level}, ${session.difficulty.labelPl.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (session.status == SessionStatus.ABANDONED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "liczby") { StatRow(cells = sessionCells(session, state.logs), modifier = Modifier.padding(top = 4.dp)) }
        if (state.deltas.isNotEmpty()) {
            item(key = "delty-tytul") { Overline("Względem poprzedniego razu", modifier = Modifier.padding(top = 10.dp)) }
            item(key = "delty") {
                CategoryGroup {
                    state.deltas.forEachIndexed { index, delta ->
                        if (index > 0) Hairline(modifier = Modifier.padding(start = 16.dp))
                        DeltaRow(delta)
                    }
                }
            }
        }
        if (state.logs.isNotEmpty()) {
            item(key = "serie") {
                val hit = state.logs.count { it.hitTarget }
                DisclosureRow(
                    title = "Serie",
                    summary = "${state.logs.size} ${setsWord(state.logs.size)} · w celu $hit",
                    expanded = showSets,
                    onToggle = { showSets = !showSets },
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    state.logs.forEach { log -> LogRow(log) }
                }
            }
        }
        item(key = "ocena") {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Overline(rpe?.let { "Jak było · RPE $it/10" } ?: "Jak było")
                RpePicker(value = rpe?.takeIf { it in Rpe.RANGE }, onChange = actions.onRpe)
                TextInput(label = "Notatka", value = note, onValueChange = actions.onNote, singleLine = false, minLines = 2)
                SecondaryAction("Zapisz ocenę", onClick = actions.onSave, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun sessionCells(session: Session, logs: List<SetLog>): List<StatCellData> {
    val reps = logs.filter { it.targetKind != TargetKind.SECONDS }.sumOf { it.actualValue }
    val seconds = logs.filter { it.targetKind == TargetKind.SECONDS }.sumOf { it.actualValue }
    return listOf(
        StatCellData(session.durationSec?.let { formatDuration(it) } ?: "—", "czas"),
        StatCellData(logs.size.toString(), Announcer.plural(logs.size, "seria", "serie", "serii")),
        if (reps > 0 || seconds == 0) {
            StatCellData(reps.toString(), Announcer.plural(reps, "powtórzenie", "powtórzenia", "powtórzeń"))
        } else {
            StatCellData("$seconds s", "pracy")
        },
    )
}

/** Ćwiczenie względem poprzedniego razu: nowe przygaszone, więcej ogniem, mniej czerwienią. */
@Composable
private fun DeltaRow(d: ExerciseDelta) {
    val delta = d.delta
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(d.exerciseName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("${d.sets} ${setsWord(d.sets)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val unit = if (d.targetKind == TargetKind.SECONDS) "s" else "powt."
        Text(
            text = "${d.total} $unit" + deltaText(delta),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
                delta > 0 -> MaterialTheme.colorScheme.primary
                delta < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun LogRow(log: SetLog) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${log.exerciseName} · seria ${log.setIndex}" + if (log.round > 1) " · runda ${log.round}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        val unit = if (log.targetKind == TargetKind.SECONDS) "s" else "powt."
        Text(
            text = "${log.actualValue}/${log.targetValue} $unit",
            style = MaterialTheme.typography.labelLarge,
            color = if (log.hitTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun statusLabel(status: SessionStatus) = when (status) {
    SessionStatus.DONE -> "Ukończony"
    SessionStatus.ABANDONED -> "Przerwany"
    SessionStatus.IN_PROGRESS -> "W toku"
}

private fun setsWord(n: Int) = when {
    n == 1 -> "seria"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "serie"
    else -> "serii"
}

private fun deltaText(delta: Int?): String = when {
    delta == null -> " (nowe)"
    delta > 0 -> " (+$delta)"
    delta < 0 -> " ($delta)"
    else -> " (=)"
}

/** Klucz ViewModelu panelu szczegółu na tablecie: osobny od ekranu sesji z trasy. */
private const val DETAIL_PANE_KEY = "historia-szczegol"

/** Szerokość listy obok szczegółu sesji na tablecie, jak lista ćwiczeń (D-52). */
private val HISTORY_PANE_WIDTH = 460.dp
