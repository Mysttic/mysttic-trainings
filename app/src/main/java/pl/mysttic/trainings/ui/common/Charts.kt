package pl.mysttic.trainings.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.mysttic.trainings.domain.stats.DayHeat
import pl.mysttic.trainings.domain.stats.ExerciseRecord
import pl.mysttic.trainings.domain.stats.WeekVolume
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

/*
 * Wykresy rysowane wprost na Canvasie (D-23): trzy proste obrazki nie są warte osobnej biblioteki,
 * którą trzeba potem trzymać w zgodzie z Compose BOM. Kolory wyłącznie z `MaterialTheme.colorScheme`.
 * Dane przygotowuje `domain/stats/ProgressStats`, więc tutaj zostaje samo rysowanie.
 */

private val dayMonth = DateTimeFormatter.ofPattern("d.MM")

/** Słupki tygodniowego wolumenu; bieżący tydzień wyróżniony. */
@Composable
fun VolumeBarChart(weeks: List<WeekVolume>, modifier: Modifier = Modifier) {
    if (weeks.isEmpty()) {
        Text("Brak danych", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = weeks.maxOf { it.volume }.coerceAtLeast(1)
    // Ta sama logika co w paskach postępu (D-32): dwa ostatnie tygodnie ogniem, wcześniejsze przygaszone,
    // puste tory w outlineVariant — „wypalone kontra jeszcze nie”, nigdy szarość.
    val barColor = MaterialTheme.colorScheme.primary
    val pastColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val gap = 9.dp.toPx()
            val slot = size.width / weeks.size
            val barWidth = (slot - gap).coerceAtLeast(4.dp.toPx())
            val radius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            weeks.forEachIndexed { index, week ->
                val left = index * slot + (slot - barWidth) / 2
                val height = size.height * (week.volume.toFloat() / max)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius,
                )
                if (week.volume > 0) {
                    drawRoundRect(
                        color = if (index >= weeks.lastIndex - 1) barColor else pastColor,
                        topLeft = Offset(left, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = radius,
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weeks.forEach { week ->
                Text(
                    text = week.weekStart.format(dayMonth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Najlepsza seria ćwiczenia w czasie: linia z kropkami, bez osi — liczby są pod spodem. */
@Composable
fun RecordLineChart(history: List<ExerciseRecord>, modifier: Modifier = Modifier) {
    if (history.size < 2) {
        Text(
            text = if (history.isEmpty()) "Jeszcze nie robiłeś tego ćwiczenia." else "Jedno wykonanie — wykres pojawi się przy drugim.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val values = history.map { it.best }
    val max = values.max()
    val min = values.min()
    val span = (max - min).coerceAtLeast(1)
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.tertiary

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val stepX = if (values.size == 1) 0f else size.width / (values.size - 1)
            // Margines u góry i dołu, żeby kropki skrajne nie ucinały się o krawędź.
            val top = 8f
            val usable = size.height - 2 * top
            fun yFor(value: Int) = top + usable * (1f - (value - min).toFloat() / span)

            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val y = yFor(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 6f))
            values.forEachIndexed { index, value ->
                drawCircle(color = dotColor, radius = 9f, center = Offset(index * stepX, yFor(value)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = history.first().date.format(dayMonth),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = history.last().date.format(dayMonth),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Kalendarz aktywności: kolumna to tydzień, wiersz to dzień tygodnia. */
@Composable
fun ActivityHeatmap(days: List<DayHeat>, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    // Trzy poziomy: pusty dzień w outlineVariant (krwisty, nie szary), jeden trening przygaszonym ogniem, więcej pełnym.
    val empty = MaterialTheme.colorScheme.outlineVariant
    val one = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val many = MaterialTheme.colorScheme.primary

    // Pierwsza kolumna zaczyna się od poniedziałku, więc dokładamy puste miejsca przed pierwszym dniem.
    val leading = days.first().date.dayOfWeek.value - 1
    val cells: List<DayHeat?> = List(leading) { null } + days
    val columns = (cells.size + 6) / 7

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until 7) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (row % 2 == 0) DayOfWeek.of(row + 1).polishShort() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(width = 22.dp, height = 14.dp),
                )
                for (column in 0 until columns) {
                    val cell = cells.getOrNull(column * 7 + row)
                    HeatCell(
                        color = when {
                            cell == null -> null
                            cell.sessions == 0 -> empty
                            cell.sessions == 1 -> one
                            else -> many
                        },
                    )
                }
            }
        }
    }
}

/** Kwadracik dnia; `null` to puste miejsce przed pierwszym dniem zakresu. */
@Composable
private fun HeatCell(color: Color?) {
    Spacer(
        modifier = Modifier
            .size(11.dp)
            .clip(RoundedCornerShape(3.dp))
            .then(if (color == null) Modifier else Modifier.background(color)),
    )
}

private fun DayOfWeek.polishShort(): String = when (this) {
    DayOfWeek.MONDAY -> "pon"
    DayOfWeek.TUESDAY -> "wt"
    DayOfWeek.WEDNESDAY -> "śr"
    DayOfWeek.THURSDAY -> "czw"
    DayOfWeek.FRIDAY -> "pt"
    DayOfWeek.SATURDAY -> "sob"
    DayOfWeek.SUNDAY -> "nd"
}

/** Wiersz „etykieta: wartość” pod wykresem. */
@Composable
fun ChartLegendRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
