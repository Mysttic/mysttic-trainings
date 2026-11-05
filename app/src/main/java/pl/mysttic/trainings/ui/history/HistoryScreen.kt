package pl.mysttic.trainings.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.model.Rpe
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.stats.HistoryPeriod
import pl.mysttic.trainings.domain.stats.PeriodStats
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.ui.common.ActivityHeatmap
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.RpePicker
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.StatCellData
import pl.mysttic.trainings.ui.common.StatRow
import pl.mysttic.trainings.ui.theme.Dimens
import pl.mysttic.trainings.ui.common.SectionTitle
import pl.mysttic.trainings.ui.common.VolumeBarChart
import pl.mysttic.trainings.ui.common.formatDuration
import pl.mysttic.trainings.ui.common.ScreenScaffold
import pl.mysttic.trainings.ui.common.screenTopBarColors
import pl.mysttic.trainings.domain.engine.Announcer
import pl.mysttic.trainings.ui.adaptive.ReadableBox
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun statusLabel(status: SessionStatus) = when (status) {
    SessionStatus.DONE -> "ukończony"
    SessionStatus.ABANDONED -> "przerwany"
    SessionStatus.IN_PROGRESS -> "w toku"
}

/** Data sesji po polsku niezależnie od języka telefonu, jak nagłówek na „Dziś”: „23 września 2026, 18:04”. */
private val sessionDate = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("pl"))

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(sessionDate)

@Composable
fun HistoryScreen(
    onOpenSession: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Nagłówek jedzie w tej samej LazyColumn co sesje: stojąc poza nią zajmował większość ekranu
    // i na listę zostawały trzy wiersze.
    LazyColumn(
        modifier = Modifier.centeredReadable(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "naglowek") {
            Column {
                Text(
                    text = "Historia",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.sectionGap),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryPeriod.entries.forEach { period ->
                        FilterPill(
                            text = period.labelPl,
                            selected = state.period == period,
                            onClick = { viewModel.setPeriod(period) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(SessionStatus.DONE, SessionStatus.ABANDONED).forEach { status ->
                        FilterPill(
                            text = statusLabel(status).replaceFirstChar { it.uppercase() },
                            selected = state.status == status,
                            onClick = { viewModel.setStatus(status) },
                        )
                    }
                }

                StatsCard(state.stats, state.period, state.streak, state.bestStreak)
                // Bez ani jednej sesji wykres wolumenu i kalendarz rysują puste ramki pod „Jeszcze nic tu nie ma”.
                if (state.totalCount > 0) ProgressCard(state)
            }
        }

        if (!state.loading && state.sessions.isEmpty()) {
            item(key = "pusto") {
                EmptyState(
                    if (state.totalCount == 0) "Jeszcze nic tu nie ma. Pierwszy ukończony trening pojawi się na tej liście."
                    else "Nic nie pasuje do filtrów.",
                )
            }
        }

        items(state.sessions, key = { it.id }) { session ->
            Column(modifier = Modifier.padding(horizontal = Dimens.screenPadding)) {
                SessionRow(session, onClick = { onOpenSession(session.id) })
                Hairline()
            }
        }
    }
}

/** Statystyki wybranego okresu; zawsze z sesji ukończonych, niezależnie od filtra statusu. */
@Composable
private fun StatsCard(stats: PeriodStats, period: HistoryPeriod, streak: Int, bestStreak: Int) {
    Column(modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.sectionGap)) {
        Overline(if (period == HistoryPeriod.ALL) "Wszystkie ukończone" else "Ukończone, ostatnie ${period.labelPl}")
        Spacer(Modifier.height(10.dp))
        if (stats.isEmpty) {
            Text(
                text = "Brak ukończonych treningów w tym okresie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StatRow(
                cells = listOf(
                    StatCellData("$streak ${if (streak == 1) "dzień" else "dni"}", "z rzędu", accent = streak > 0),
                    StatCellData(stats.sessions.toString(), Announcer.plural(stats.sessions, "trening", "treningi", "treningów")),
                    StatCellData(formatDuration(stats.durationSec), "w ruchu"),
                ),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${stats.sets} ${setsWord(stats.sets)} · ${Announcer.reps(stats.reps)} · ${stats.workSec} s pracy" +
                    if (bestStreak > streak) " · rekord serii $bestStreak" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Postęp w czasie: tygodniowy wolumen i kalendarz aktywności (M5, D-23). */
@Composable
private fun ProgressCard(state: HistoryUiState) {
    if (state.weeks.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = Dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SessionRow(session: Session, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(session.workoutName, style = MaterialTheme.typography.titleMedium)
            Text(session.durationSec?.let { formatDuration(it) } ?: "—", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = "${formatDate(session.startedAt)} · ${statusLabel(session.status)} · ${session.difficulty.labelPl.lowercase()}" +
                (session.rpe?.let { " · RPE $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = if (session.status == SessionStatus.ABANDONED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session
    // Ta sama skala co w podsumowaniu (D-46). Sesja bez oceny zostaje bez niej, a stara ocena spoza skali
    // (dawny suwak od 1 do 10) przetrwa zapis samej notatki, bo stan startuje z tego, co jest w bazie.
    var rpe by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Usunąć sesję?") },
            text = { Text("Zniknie razem z zapisanymi seriami. Tego nie da się cofnąć.") },
            // Ta sama kolejność co w pozostałych oknach potwierdzenia: po prawej „nie rób nic”, kasowanie ogniem.
            confirmButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Zostaw") } },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) {
                    Text("Usuń sesję", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    LaunchedEffect(session?.id) {
        session?.let {
            rpe = it.rpe
            note = it.note
        }
    }

    ScreenScaffold(
        topBar = {
            TopAppBar(
                colors = screenTopBarColors(),
                title = { Text(session?.workoutName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz") }
                },
            )
        },
    ) { padding ->
        if (session == null) {
            if (!state.loading) EmptyState("Nie znaleziono sesji.", Modifier.padding(padding))
            return@ScreenScaffold
        }
        // Na tablecie w czytelnej szerokości (D-42): nazwy serii i wyniki nie rozjeżdżają się na 1280 dp.
        ReadableBox(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    Text("${formatDate(session.startedAt)} · ${statusLabel(session.status)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Poziom: ${session.difficulty.labelPl} · czas: ${session.durationSec?.let { formatDuration(it) } ?: "—"}")
                    SectionTitle("Serie (${state.logs.size})")
                }
                items(state.logs, key = { it.id }) { log ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${log.exerciseName} · seria ${log.setIndex}" + if (log.round > 1) " · runda ${log.round}" else "", modifier = Modifier.weight(1f))
                        val unit = if (log.targetKind == TargetKind.SECONDS) "s" else "powt."
                        Text(
                            text = "${log.actualValue}/${log.targetValue} $unit",
                            color = if (log.hitTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (state.deltas.isNotEmpty()) {
                    item { SectionTitle("Względem poprzedniego razu") }
                    items(state.deltas, key = { it.exerciseId }) { d ->
                        val delta = d.delta
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("${d.exerciseName} · ${d.sets} ${setsWord(d.sets)}", modifier = Modifier.weight(1f))
                            val unit = if (d.targetKind == TargetKind.SECONDS) "s" else "powt."
                            Text(
                                text = "${d.total} $unit" + deltaText(delta),
                                color = when {
                                    delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    delta > 0 -> MaterialTheme.colorScheme.primary
                                    delta < 0 -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
                item {
                    SectionTitle(rpe?.let { "Jak było? RPE $it/10" } ?: "Jak było?")
                    RpePicker(value = rpe?.takeIf { it in Rpe.RANGE }, onChange = { rpe = it })
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Notatka") },
                        minLines = 2,
                    )
                    TextButton(onClick = { viewModel.saveFeedback(rpe, note) }) { Text("Zapisz") }
                    Spacer(Modifier.height(24.dp))
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Usuń sesję", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
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
