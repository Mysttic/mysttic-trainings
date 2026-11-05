package pl.mysttic.trainings.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.plan.PlanListRow
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.PlanStatus
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.ui.common.AppIcons
import pl.mysttic.trainings.ui.common.CategoryDivider
import pl.mysttic.trainings.ui.common.CategoryGroup
import pl.mysttic.trainings.ui.common.CategoryRow
import pl.mysttic.trainings.ui.common.DisclosureRow
import pl.mysttic.trainings.ui.common.InfoCard
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.ProgressTrack
import pl.mysttic.trainings.ui.common.ScreenTitle
import pl.mysttic.trainings.ui.common.SegmentBar
import pl.mysttic.trainings.ui.theme.Dimens
import pl.mysttic.trainings.ui.theme.Pill

/**
 * Plany (D-42, w głównej nawigacji od D-53, issue #1): aktywny, czekające (wstrzymane i nierozpoczęte), zakończone
 * zwinięte — trafiają tam stare cykle z kreatora, więc nie mogą zasłaniać reszty. „Nowy plan” w nagłówku daje
 * cztery drogi naraz: kreator, gotowy program, plan własny i plik od kogoś. „Dziś” dalej pokazuje jeden trening
 * (D-26): lista jest półką, z której wybiera się plan, a aktywacja dzieje się w szczegółach planu.
 */
@Composable
fun PlansScreen(
    onOpenPlan: (String) -> Unit,
    onWizard: () -> Unit,
    onPresets: () -> Unit,
    onNewPlan: () -> Unit,
    onImport: () -> Unit,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpenPlan)
    val wizard by rememberUpdatedState(onWizard)
    val presets by rememberUpdatedState(onPresets)
    val newPlan by rememberUpdatedState(onNewPlan)
    val import by rememberUpdatedState(onImport)
    val actions = remember {
        PlansActions(
            onOpenPlan = { open(it) },
            onWizard = { wizard() },
            onPresets = { presets() },
            onNewPlan = { newPlan() },
            onImport = { import() },
        )
    }
    PlansContent(state = state, actions = actions)
}

/** Wyjścia z listy planów; treść jest bezstanowa, więc zrzut rysuje ją z fiksturą. */
class PlansActions(
    val onOpenPlan: (String) -> Unit = {},
    val onWizard: () -> Unit = {},
    val onPresets: () -> Unit = {},
    val onNewPlan: () -> Unit = {},
    val onImport: () -> Unit = {},
)

/** [newPlanOpen] otwiera od razu arkusz „Nowy plan” (zrzuty); w aplikacji otwiera go przycisk w nagłówku. */
@Composable
fun PlansContent(state: PlansUiState, actions: PlansActions, newPlanOpen: Boolean = false) {
    var showNewPlan by rememberSaveable { mutableStateOf(newPlanOpen) }
    var showFinished by rememberSaveable { mutableStateOf(false) }
    if (showNewPlan) NewPlanSheet(actions = actions, onDismiss = { showNewPlan = false })

    LazyColumn(
        modifier = Modifier.centeredReadable(),
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.sectionGap + 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "naglowek") {
            ScreenTitle("Plany") { NewPlanButton(onClick = { showNewPlan = true }) }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Aktywny jest jeden, pozostałe czekają ze swoim postępem. Przełączysz je w szczegółach planu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!state.loading && state.isEmpty) {
            // Bez planów nie ma czego przeglądać: cztery drogi do pierwszego planu od razu na ekranie.
            item(key = "pusto") {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Overline("Pierwszy plan")
                    NewPlanOptions(actions = actions, onPicked = {})
                }
            }
        }
        section("Aktywny", state.active, actions.onOpenPlan)
        section("Czekają", state.waiting, actions.onOpenPlan)
        if (state.finished.isNotEmpty()) {
            item(key = "zakonczone") {
                DisclosureRow(
                    title = "Zakończone",
                    summary = "${state.finished.size} ${finishedWord(state.finished.size)} · przebiegi zostają ze statystykami",
                    expanded = showFinished,
                    onToggle = { showFinished = !showFinished },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    state.finished.forEach { row -> PlanRow(row, onClick = { actions.onOpenPlan(row.id) }) }
                }
            }
        }
        item(key = "stopka") { Spacer(Modifier.height(16.dp)) }
    }
}

private fun LazyListScope.section(title: String, rows: List<PlanListRow>, onOpenPlan: (String) -> Unit) {
    if (rows.isEmpty()) return
    item(key = "tytul-$title") { Overline(title, modifier = Modifier.padding(top = 14.dp)) }
    items(rows, key = { it.id }) { row -> PlanRow(row, onClick = { onOpenPlan(row.id) }) }
}

/** Akcja główna listy: pigułka w ogniu przy tytule, bo dodanie planu to jedyne, co robi się tu od zera. */
@Composable
private fun NewPlanButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = Pill, color = MaterialTheme.colorScheme.primary, modifier = Modifier.height(40.dp)) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Nowy plan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Arkusz „Nowy plan”: te same cztery drogi co na pustej liście. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewPlanSheet(actions: PlansActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Overline("Nowy plan")
            NewPlanOptions(actions = actions, onPicked = onDismiss)
        }
    }
}

private class NewPlanOption(val icon: ImageVector, val title: String, val summary: String, val action: (PlansActions) -> () -> Unit)

private val newPlanOptions = listOf(
    NewPlanOption(AppIcons.Spark, "Kreator", "Cykl pod twój cel, sprzęt i czas, w czterech krokach.") { it.onWizard },
    NewPlanOption(AppIcons.Play, "Gotowy program", "Tabata, 100 pompek, oddech Wima Hofa i inne stałe protokoły.") { it.onPresets },
    NewPlanOption(AppIcons.Plans, "Własny plan", "Dni i treningi układasz sam; taki plan wyślesz też innym.") { it.onNewPlan },
    NewPlanOption(AppIcons.File, "Plan z pliku", "Plan, który ktoś ci wysłał.") { it.onImport },
)

@Composable
private fun NewPlanOptions(actions: PlansActions, onPicked: () -> Unit) {
    CategoryGroup {
        newPlanOptions.forEachIndexed { index, option ->
            if (index > 0) CategoryDivider()
            CategoryRow(
                icon = option.icon,
                title = option.title,
                summary = option.summary,
                onClick = {
                    onPicked()
                    option.action(actions)()
                },
            )
        }
    }
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
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
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

private fun finishedWord(n: Int): String = when {
    n == 1 -> "plan"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "plany"
    else -> "planów"
}
