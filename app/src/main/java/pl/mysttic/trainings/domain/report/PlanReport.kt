package pl.mysttic.trainings.domain.report

import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.stats.PlanStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Raport z przebiegu planu (D-42) jako lista linii, niezależna od PDF: tę samą listę da się pokazać na ekranie,
 * podzielić na strony i narysować. Czysty Kotlin, więc treść i paginację sprawdzają testy bez Androida.
 */
sealed interface ReportLine {
    data class Title(val text: String) : ReportLine
    data class Subtitle(val text: String) : ReportLine
    data class Heading(val text: String) : ReportLine
    data class KeyValue(val key: String, val value: String) : ReportLine
    data class Paragraph(val text: String) : ReportLine
    /** Wiersz tabeli; `header` powtarza się na każdej stronie, na którą tabela przechodzi. */
    data class Row(val cells: List<String>, val header: Boolean = false, val tableId: String = "") : ReportLine
    /** Słupki tygodni: zrobione z zaplanowanych. */
    data class Bars(val bars: List<Bar>) : ReportLine
    object Spacer : ReportLine
}

data class Bar(val label: String, val done: Int, val planned: Int)

data class PlanReport(val lines: List<ReportLine>, val fileName: String)

object PlanReportBuilder {

    private val date = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("pl"))
    private val shortDate = DateTimeFormatter.ofPattern("d.MM", Locale.forLanguageTag("pl"))

    fun build(
        planName: String,
        author: String,
        person: String,
        stats: PlanStats,
        generatedAt: Long,
        zone: ZoneId,
        includeNotes: Boolean,
    ): PlanReport {
        fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val lines = ArrayList<ReportLine>()
        lines += ReportLine.Title(planName)
        lines += ReportLine.Subtitle(
            listOfNotNull(
                person.takeIf { it.isNotBlank() }?.let { "Ćwiczy: $it" },
                author.takeIf { it.isNotBlank() }?.let { "Plan: $it" },
                "Raport z ${day(generatedAt).format(date)}",
            ).joinToString(" · "),
        )
        lines += ReportLine.Spacer

        lines += ReportLine.Heading("Podsumowanie")
        lines += ReportLine.KeyValue("Treningi", "${stats.doneCount} z ${stats.totalCount} (${stats.attendancePct}%)")
        if (stats.firstDoneAt != null && stats.lastDoneAt != null) {
            lines += ReportLine.KeyValue("Okres", "${day(stats.firstDoneAt).format(date)} – ${day(stats.lastDoneAt).format(date)}")
        }
        lines += ReportLine.KeyValue("Czas w ruchu", duration(stats.durationSec))
        stats.completionPct?.let { lines += ReportLine.KeyValue("Wykonane serie", "${stats.loggedSets} z ${stats.plannedSets} ($it%)") }
        stats.averageRpe?.let { lines += ReportLine.KeyValue("Średni wysiłek (RPE)", "%.1f z 10".format(Locale.forLanguageTag("pl"), it)) }
        if (stats.abandoned > 0) {
            lines += ReportLine.KeyValue("Przerwane treningi", "${stats.abandoned}")
        }
        if (stats.extraCount > 0) {
            lines += ReportLine.KeyValue("Treningi ponad plan", "${stats.extraCount}, poza liczbami tego raportu")
        }
        if (stats.days.any { it.plannedEstimated }) {
            lines += ReportLine.Paragraph("Liczba serii w części starszych treningów policzona z dzisiejszego planu.")
        }
        lines += ReportLine.Spacer

        if (stats.weeks.isNotEmpty()) {
            lines += ReportLine.Heading("Tygodnie planu")
            lines += ReportLine.Bars(stats.weeks.map { Bar("T${it.week}", it.done, it.planned) })
            lines += ReportLine.Spacer
        }

        if (stats.exercises.isNotEmpty()) {
            lines += ReportLine.Heading("Ćwiczenia")
            val id = "cwiczenia"
            lines += ReportLine.Row(listOf("Ćwiczenie", "Serie", "W celu", "Pierwsza", "Ostatnia", "Najlepsza", "Ciężar"), header = true, tableId = id)
            stats.exercises.forEach { e ->
                lines += ReportLine.Row(
                    listOf(
                        e.name,
                        "${e.sets}",
                        e.hitRate?.let { "$it%" } ?: "–",
                        resultValue(e.first, e.targetKind),
                        resultValue(e.last, e.targetKind),
                        resultValue(e.best, e.targetKind),
                        e.plannedWeightKg?.let(::kg) ?: "–",
                    ),
                    tableId = id,
                )
            }
            lines += ReportLine.Spacer
        }

        lines += ReportLine.Heading("Dzień po dniu")
        val id = "dni"
        lines += ReportLine.Row(listOf("Dzień", "Trening", "Kiedy", "Czas", "Serie", "RPE"), header = true, tableId = id)
        stats.days.filterNot { it.isRest }.forEach { d ->
            lines += ReportLine.Row(
                listOf(
                    "${d.dayIndex}",
                    d.workoutName.orEmpty(),
                    d.doneAt?.let { day(it).format(shortDate) } ?: "–",
                    d.durationSec?.let(::duration) ?: "–",
                    if (d.done) "${d.loggedSets}" + (d.plannedSets?.let { " z $it" } ?: "") else "–",
                    d.rpe?.toString() ?: "–",
                ),
                tableId = id,
            )
        }

        if (includeNotes && stats.notes.isNotEmpty()) {
            lines += ReportLine.Spacer
            lines += ReportLine.Heading("Notatki po treningach")
            stats.notes.forEach { (at, text) -> lines += ReportLine.Paragraph("${day(at).format(shortDate)} — $text") }
        }
        return PlanReport(lines, fileName = "raport-" + slug(planName) + ".pdf")
    }

    /** Wynik serii z jednostką: ta sama postać w PDF i na ekranie statystyk (sekundy z „s”, powtórzenia bez). */
    fun resultValue(v: Int, kind: TargetKind): String = when (kind) {
        TargetKind.REPS -> "$v"
        TargetKind.SECONDS -> "$v s"
        TargetKind.AMRAP -> "$v"
    }

    fun kg(v: Double): String {
        val tenths = Math.round(v * 10)
        return if (tenths % 10 == 0L) "${tenths / 10} kg" else "${tenths / 10},${tenths % 10} kg"
    }

    fun duration(totalSec: Int): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return when {
            h > 0 -> "$h h $m min"
            m > 0 -> "$m min"
            else -> "$totalSec s"
        }
    }

    private fun slug(name: String): String = java.text.Normalizer.normalize(name.replace('ł', 'l').replace('Ł', 'L'), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "plan" }
}

/**
 * Podział linii na strony: zachłannie, z pomiarem wstrzykniętym z zewnątrz (PDF mierzy tekst fontem, test —
 * stałą). Tabela, która przechodzi na nową stronę, dostaje tam znowu nagłówek; nagłówek sekcji nie zostaje
 * sam na dole strony.
 */
object ReportPaginator {

    fun paginate(lines: List<ReportLine>, pageHeight: Float, measure: (ReportLine) -> Float): List<List<ReportLine>> {
        val pages = ArrayList<List<ReportLine>>()
        var page = ArrayList<ReportLine>()
        var used = 0f
        val headers = HashMap<String, ReportLine.Row>()

        fun newPage() {
            if (page.isNotEmpty()) pages += page
            page = ArrayList()
            used = 0f
        }

        lines.forEachIndexed { index, line ->
            if (line is ReportLine.Row && line.header) headers[line.tableId] = line
            val height = measure(line)
            val keepWith = keepWithHeight(lines, index, measure)
            if (used > 0f && used + height + keepWith > pageHeight) {
                newPage()
                if (line is ReportLine.Spacer) return@forEachIndexed
                if (line is ReportLine.Row && !line.header) {
                    headers[line.tableId]?.let { header ->
                        page += header
                        used += measure(header)
                    }
                }
            }
            page += line
            used += height
        }
        newPage()
        return pages
    }

    /**
     * Ile miejsca musi zostać pod linią, żeby nie wisiała sama na dole strony. Nagłówek sekcji i nagłówek tabeli
     * ciągną za sobą następną linię, a gdy ta też jest nagłówkiem, jeszcze kolejną: nagłówek sekcji nad tabelą
     * przechodzi na nową stronę razem z nagłówkiem tabeli i pierwszym wierszem.
     */
    private fun keepWithHeight(lines: List<ReportLine>, index: Int, measure: (ReportLine) -> Float): Float {
        var sum = 0f
        var i = index
        while (lines[i].keepsNext()) {
            val next = lines.getOrNull(i + 1) ?: break
            sum += measure(next)
            i++
        }
        return sum
    }

    private fun ReportLine.keepsNext(): Boolean = this is ReportLine.Heading || (this is ReportLine.Row && header)
}
