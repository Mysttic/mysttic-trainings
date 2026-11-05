package pl.mysttic.trainings.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.plan.PlanListRow
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.PlanStatus
import pl.mysttic.trainings.ui.adaptive.ReadableBox
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.InfoCard
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.SegmentBar
import pl.mysttic.trainings.ui.common.ProgressTrack
import pl.mysttic.trainings.ui.theme.Dimens

/**
 * Lista planów (D-42): aktywny, czekające (wstrzymane i nierozpoczęte), zakończone zwinięte — trafiają tam
 * stare cykle z kreatora, więc nie mogą zasłaniać reszty. „Dziś” dalej pokazuje jeden trening (D-26):
 * lista jest półką, z której wybiera się plan, a aktywacja dzieje się w szczegółach planu.
 */
@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onWizard: () -> Unit,
    onPresets: () -> Unit,
    onNewPlan: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFinished by rememberSaveable { mutableStateOf(false) }

    ReadableBox {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "naglowek") {
                IconButton(onClick = onBack, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("Twoje plany", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Aktywny jest jeden. Pozostałe czekają ze swoim postępem — przełączysz je w szczegółach planu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!state.loading && state.isEmpty) {
                item(key = "pusto") { EmptyState("Nie masz jeszcze żadnego planu.") }
            }
            section("Aktywny", state.active, onOpenPlan)
            section("Czekają", state.waiting, onOpenPlan)
            if (state.finished.isNotEmpty()) {
                item(key = "zakonczone") {
                    TextButton(onClick = { showFinished = !showFinished }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (showFinished) "Ukryj zakończone" else "Zakończone (${state.finished.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showFinished) {
                    items(state.finished, key = { "z-" + it.id }) { row -> PlanRow(row, onClick = { onOpenPlan(row.id) }) }
                }
            }
            item(key = "akcje") {
                Column(
                    modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    onNewPlan?.let { SecondaryAction("Ułóż własny plan", onClick = it, modifier = Modifier.fillMaxWidth(), height = 48.dp) }
                    onImport?.let { SecondaryAction("Wczytaj plan z pliku", onClick = it, modifier = Modifier.fillMaxWidth(), height = 48.dp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SecondaryAction("Kreator", onClick = onWizard, modifier = Modifier.weight(1f))
                        SecondaryAction("Gotowy program", onClick = onPresets, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    rows: List<PlanListRow>,
    onOpenPlan: (String) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "tytul-$title") { Overline(title, modifier = Modifier.padding(top = 14.dp)) }
    items(rows, key = { it.id }) { row -> PlanRow(row, onClick = { onOpenPlan(row.id) }) }
}

/**
 * Wiersz planu: nazwa, skąd jest, stan i pasek kolejki. Aktywny dostaje ogień na pasku, reszta resztki.
 * Karta to [InfoCard] z DesignSystem (stopień, promień i marginesy jak w innych kartach); klik obejmuje całą kartę.
 */
@Composable
private fun PlanRow(row: PlanListRow, onClick: () -> Unit) {
    InfoCard(
        modifier = Modifier
            .clip(InfoCardShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${row.done} z ${row.total}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = listOf(sourceLabel(row), row.status.labelPl).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Segmenty do czterech tygodni po trzy treningi; dłuższy plan dostaje ciągły pasek.
        if (row.total in 1..12) {
            SegmentBar(done = row.done, total = row.total, current = row.status == PlanStatus.ACTIVE)
        } else if (row.total > 0) {
            ProgressTrack(fraction = row.done.toFloat() / row.total)
        }
    }
}

/** Przycięcie śladu dotyku (ripple) do promienia [InfoCard], żeby nie wychodził poza zaokrąglone rogi. */
private val InfoCardShape = RoundedCornerShape(20.dp)

private fun sourceLabel(row: PlanListRow): String = when {
    row.source == PlanSource.IMPORTED && row.author.isNotBlank() -> "Od: ${row.author}"
    else -> row.source.labelPl
}
