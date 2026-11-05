package pl.mysttic.trainings.domain.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.stats.PlanDayResult
import pl.mysttic.trainings.domain.stats.PlanStats
import pl.mysttic.trainings.domain.stats.PlanWeek
import java.time.ZoneId

class PlanReportTest {

    private val zone = ZoneId.of("Europe/Warsaw")
    private val stats = PlanStats(
        doneCount = 2, totalCount = 30, abandoned = 0, durationSec = 3725, loggedSets = 20, plannedSets = 24,
        averageRpe = 7.25, firstDoneAt = 1_790_000_000_000, lastDoneAt = 1_790_200_000_000,
        days = (1..30).map { PlanDayResult(it, "Trening $it", sessionId = if (it <= 2) "s$it" else null, loggedSets = 10, plannedSets = 12) },
        exercises = emptyList(),
        weeks = listOf(PlanWeek(1, 2, 3, 40, 60)),
        notes = listOf(1_790_000_000_000 to "ciężko"),
    )

    @Test
    fun `raport po polsku, notatki tylko na zyczenie`() {
        val report = PlanReportBuilder.build("Plan Oli", "Ola", "Janek", stats, 1_790_300_000_000, zone, includeNotes = false)
        val text = report.lines.joinToString("\n")
        assertTrue(text.contains("Ćwiczy: Janek"))
        assertTrue(text.contains("2 z 30 (6%)"))
        assertTrue(text.contains("1 h 2 min"))
        assertTrue(text.contains("7,3 z 10"))
        assertFalse(text.contains("ciężko"))
        assertTrue(PlanReportBuilder.build("Plan Oli", "", "", stats, 0, zone, includeNotes = true).lines.joinToString().contains("ciężko"))
        assertEquals("raport-plan-oli.pdf", report.fileName)
    }

    @Test
    fun `tabela przechodzaca na nowa strone dostaje tam naglowek`() {
        val report = PlanReportBuilder.build("Plan", "", "", stats, 0, zone, includeNotes = false)
        val pages = ReportPaginator.paginate(report.lines, pageHeight = 200f) { 20f }

        assertTrue(pages.size > 1)
        pages.drop(1).forEach { page ->
            val first = page.first()
            if (first is ReportLine.Row) assertTrue(first.header)
        }
        // Każda linia trafia na strony dokładnie raz (poza powtórzonymi nagłówkami tabel).
        val flat = pages.flatten().filterNot { it is ReportLine.Row && it.header }
        assertEquals(report.lines.filterNot { it is ReportLine.Row && it.header }.filterNot { it is ReportLine.Spacer }.size,
            flat.filterNot { it is ReportLine.Spacer }.size)
        pages.forEach { page -> assertTrue(page.sumOf { 20.0 } <= 200.0) }
    }

    @Test
    fun `naglowek sekcji nie zostaje sam na dole strony`() {
        val lines = listOf(ReportLine.Paragraph("a"), ReportLine.Paragraph("b"), ReportLine.Heading("H"), ReportLine.Paragraph("c"))
        val pages = ReportPaginator.paginate(lines, pageHeight = 30f) { 10f }
        assertEquals(listOf(listOf(lines[0], lines[1]), listOf(lines[2], lines[3])), pages)
    }

    @Test
    fun `naglowek sekcji nad tabela przechodzi na nowa strone z naglowkiem tabeli i pierwszym wierszem`() {
        val header = ReportLine.Row(listOf("Ćwiczenie", "Serie"), header = true, tableId = "t")
        val lines = listOf(
            ReportLine.Paragraph("a"), ReportLine.Paragraph("b"), ReportLine.Paragraph("c"),
            ReportLine.Heading("Ćwiczenia"), header,
            ReportLine.Row(listOf("Pompki", "3"), tableId = "t"), ReportLine.Row(listOf("Deska", "2"), tableId = "t"),
        )
        val pages = ReportPaginator.paginate(lines, pageHeight = 50f) { 10f }
        assertEquals(listOf(lines.take(3), lines.drop(3)), pages)
    }

    @Test
    fun `wynik na czas ma jednostke, powtorzenia nie`() {
        assertEquals("2 s", PlanReportBuilder.resultValue(2, TargetKind.SECONDS))
        assertEquals("8", PlanReportBuilder.resultValue(8, TargetKind.REPS))
    }

    @Test
    fun `treningi ponad plan tylko wtedy, gdy sa`() {
        fun text(extra: Int) = PlanReportBuilder.build("Plan", "", "", stats.copy(extraCount = extra), 0, zone, includeNotes = false)
            .lines.joinToString("\n")
        assertFalse(text(0).contains("ponad plan"))
        assertTrue(text(2).contains("Treningi ponad plan"))
    }

    @Test
    fun `ciezar i czas po polsku`() {
        assertEquals("22,5 kg", PlanReportBuilder.kg(22.5))
        assertEquals("20 kg", PlanReportBuilder.kg(20.0))
        assertEquals("45 s", PlanReportBuilder.duration(45))
        assertEquals("3 min", PlanReportBuilder.duration(200))
    }
}
