package pl.mysttic.trainings.ui.stats

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.mysttic.trainings.domain.report.PlanReportBuilder
import pl.mysttic.trainings.domain.stats.PlanExerciseResult
import pl.mysttic.trainings.domain.stats.PlanWeek
import pl.mysttic.trainings.ui.adaptive.ReadableBox
import pl.mysttic.trainings.ui.common.AppSwitch
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.SettingRow
import pl.mysttic.trainings.ui.common.StatCellData
import pl.mysttic.trainings.ui.common.StatGrid
import pl.mysttic.trainings.ui.common.formatKg
import pl.mysttic.trainings.ui.theme.Dimens
import pl.mysttic.trainings.domain.engine.Announcer

/**
 * Statystyki jednego planu (D-42): frekwencja z kolejki, tygodnie planu, ćwiczenia i raport PDF do wysłania
 * (np. autorowi planu). Historia dalej pokazuje całość; tu jest tylko ten plan.
 */
@Composable
fun PlanStatsScreen(onBack: () -> Unit, viewModel: PlanStatsViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.saveReport(uri)
    }
    LaunchedEffect(state.shareUri) {
        val uri = state.shareUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Raport z planu: ${state.program?.name.orEmpty()}")
            clipData = ClipData.newRawUri(state.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Wyślij raport"))
        viewModel.consumeShare()
    }

    val program = state.program
    val stats = state.stats
    if (program == null || stats == null) {
        if (!state.loading) {
            // Plan usunięty spod otwartego ekranu: strzałka Wstecz zostaje, jak na ekranie planu (C3-13).
            ReadableBox {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.screenPadding, vertical = 8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    EmptyState("Nie znaleziono planu.")
                }
            }
        }
        return
    }

    ReadableBox {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "naglowek") {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("Statystyki planu", style = MaterialTheme.typography.headlineMedium)
                Text(program.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Dimens.sectionGap))
                StatGrid(
                    cells = listOfNotNull(
                        StatCellData("${stats.doneCount} z ${stats.totalCount}", "treningów", accent = true),
                        StatCellData(PlanReportBuilder.duration(stats.durationSec), "w ruchu"),
                        stats.completionPct?.let { StatCellData("$it%", "wykonanych serii") },
                        stats.averageRpe?.let { StatCellData("%.1f".format(java.util.Locale.forLanguageTag("pl"), it), "średni wysiłek") },
                        StatCellData("${stats.abandoned}", "przerwanych").takeIf { stats.abandoned > 0 },
                        StatCellData("${stats.extraCount}", "ponad plan").takeIf { stats.extraCount > 0 },
                    ),
                )
            }
            if (stats.weeks.isNotEmpty()) {
                item(key = "tygodnie") {
                    Overline("Tygodnie planu", modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(8.dp))
                    WeeksChart(stats.weeks)
                }
            }
            if (stats.exercises.isNotEmpty()) {
                item(key = "cwiczenia-tytul") { Overline("Ćwiczenia", modifier = Modifier.padding(top = 12.dp)) }
                items(stats.exercises, key = { it.exerciseId }) { ExerciseStatRow(it) }
            }
            item(key = "raport") {
                Overline("Raport PDF", modifier = Modifier.padding(top = 18.dp))
                SettingRow(
                    title = "Notatki z treningów w raporcie",
                    description = "Raport idzie do innej osoby — notatki dołączasz świadomie.",
                    trailing = { AppSwitch(checked = state.includeNotes, onCheckedChange = viewModel::setIncludeNotes) },
                )
                Spacer(Modifier.height(8.dp))
                PrimaryAction("Udostępnij raport", onClick = viewModel::shareReport, enabled = !state.busy)
                Spacer(Modifier.height(10.dp))
                // Okno zapisu tworzy plik od razu, więc w trakcie innej operacji nie otwieramy go wcale.
                SecondaryAction(
                    "Zapisz PDF",
                    onClick = { if (!state.busy) scope.launch { savePdf.launch(viewModel.suggestedName()) } },
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryAction(
                    if (state.preview == null) "Podgląd raportu" else "Ukryj podgląd",
                    onClick = viewModel::togglePreview,
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp,
                )
                state.message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            state.preview?.let { preview ->
                items(preview.pages.size, key = { "podglad-$it" }) { index ->
                    Image(
                        bitmap = preview.pages[index],
                        contentDescription = "Strona ${index + 1} z ${preview.pageCount} raportu",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                if (preview.pageCount > preview.pages.size) {
                    item(key = "podglad-reszta") {
                        Text(
                            text = "Podgląd pokazuje ${preview.pages.size} z ${preview.pageCount} stron. Całość jest w pliku po udostępnieniu albo zapisie.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "koniec") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Tydzień planu: zaplanowane jako tor, zrobione ogniem (D-32), jak słupki w Historii (D-23). */
@Composable
private fun WeeksChart(weeks: List<PlanWeek>) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val fireColor = MaterialTheme.colorScheme.primary
    val max = weeks.maxOf { it.planned }.coerceAtLeast(1)
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            val slot = size.width / weeks.size
            val width = (slot - 9.dp.toPx()).coerceIn(4.dp.toPx(), 36.dp.toPx())
            val radius = CornerRadius(4.dp.toPx())
            weeks.forEachIndexed { i, week ->
                val left = i * slot + (slot - width) / 2
                val planned = size.height * week.planned / max
                val done = size.height * week.done / max
                drawRoundRect(trackColor, Offset(left, size.height - planned), Size(width, planned), radius)
                if (week.done > 0) drawRoundRect(fireColor, Offset(left, size.height - done), Size(width, done), radius)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            weeks.forEach { week ->
                Text(
                    text = "${week.done}/${week.planned}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ExerciseStatRow(e: PlanExerciseResult) {
    Column {
        Hairline()
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(
                        "${e.sets} ${Announcer.plural(e.sets, "seria", "serie", "serii")}",
                        e.hitRate?.let { "$it% w celu" },
                        e.plannedWeightKg?.let { formatKg(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Jednostka jak w raporcie PDF: seria na czas „2 s → 4 s”, na powtórzenia „8 → 10”.
            Text(
                text = "${PlanReportBuilder.resultValue(e.first, e.targetKind)} → ${PlanReportBuilder.resultValue(e.last, e.targetKind)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
