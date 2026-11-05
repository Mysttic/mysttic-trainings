package pl.mysttic.trainings.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import pl.mysttic.trainings.R
import pl.mysttic.trainings.domain.report.PlanReport
import pl.mysttic.trainings.domain.report.ReportLine
import pl.mysttic.trainings.domain.report.ReportPaginator
import pl.mysttic.trainings.ui.theme.AppTheme
import pl.mysttic.trainings.ui.theme.schemeFor
import java.io.OutputStream

/**
 * Raport planu do PDF (D-42) bez żadnej biblioteki: `android.graphics.pdf.PdfDocument`, strona A4 w punktach,
 * tekst przez `StaticLayout` fontem Sora (polskie znaki), kolory „Papieru i Ognia” — jasny motyw, bo raport
 * się drukuje i czyta na białym. Wykres tygodni to zwykłe prostokąty na Canvasie, jak na ekranie (D-23).
 * Dwa przebiegi: najpierw podział na strony tą samą miarą, potem rysowanie ze stopką „strona X z Y”.
 */
class PdfReportRenderer(private val context: Context) {

    private val scheme = schemeFor(AppTheme.PAPER)
    private val ink = scheme.onSurface.toArgb()
    private val muted = scheme.onSurfaceVariant.toArgb()
    private val fire = scheme.primary.toArgb()
    private val track = scheme.outlineVariant.toArgb()
    private val line = scheme.outline.toArgb()

    private val sora: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.sora) }.getOrNull() ?: Typeface.SANS_SERIF

    private fun paint(size: Float, weight: Int = 400, color: Int = ink) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sora
        textSize = size
        this.color = color
        // Sora jest fontem zmiennym (D-32): grubość z osi `wght`, nie z pseudo-pogrubienia.
        // Setter zwraca Boolean, więc Kotlin nie widzi go jako właściwości — wywołanie wprost.
        setFontVariationSettings("'wght' $weight")
    }

    private val title = paint(20f, 600)
    private val subtitle = paint(9.5f, 400, muted)
    private val heading = paint(12f, 600, fire)
    private val body = paint(9.5f)
    private val bodyMuted = paint(9.5f, 400, muted)
    private val cellHeader = paint(8.5f, 600, muted)
    private val cell = paint(8.5f)
    private val footer = paint(8f, 400, muted)

    fun render(report: PlanReport, out: OutputStream) {
        val pages = ReportPaginator.paginate(report.lines, CONTENT_HEIGHT) { measure(it) }
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, lines ->
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val page = document.startPage(info)
                val canvas = page.canvas
                var y = MARGIN
                lines.forEach { l -> y += draw(canvas, l, y) }
                drawFooter(canvas, index + 1, pages.size)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun measure(l: ReportLine): Float = when (l) {
        is ReportLine.Title -> layout(l.text, title, CONTENT_WIDTH).height + 4f
        is ReportLine.Subtitle -> layout(l.text, subtitle, CONTENT_WIDTH).height + 2f
        is ReportLine.Heading -> layout(l.text, heading, CONTENT_WIDTH).height + 10f
        is ReportLine.KeyValue -> maxOf(layout(l.key, bodyMuted, KEY_WIDTH).height, layout(l.value, body, CONTENT_WIDTH - KEY_WIDTH).height) + 4f
        is ReportLine.Paragraph -> layout(l.text, body, CONTENT_WIDTH).height + 4f
        is ReportLine.Row -> rowHeight(l)
        is ReportLine.Bars -> BARS_HEIGHT
        ReportLine.Spacer -> 12f
    }

    private fun draw(canvas: Canvas, l: ReportLine, y: Float): Float {
        when (l) {
            is ReportLine.Title -> text(canvas, l.text, title, MARGIN, y, CONTENT_WIDTH)
            is ReportLine.Subtitle -> text(canvas, l.text, subtitle, MARGIN, y, CONTENT_WIDTH)
            is ReportLine.Heading -> text(canvas, l.text, heading, MARGIN, y + 6f, CONTENT_WIDTH)
            is ReportLine.KeyValue -> {
                text(canvas, l.key, bodyMuted, MARGIN, y, KEY_WIDTH)
                text(canvas, l.value, body, MARGIN + KEY_WIDTH, y, CONTENT_WIDTH - KEY_WIDTH)
            }
            is ReportLine.Paragraph -> text(canvas, l.text, body, MARGIN, y, CONTENT_WIDTH)
            is ReportLine.Row -> drawRow(canvas, l, y)
            is ReportLine.Bars -> drawBars(canvas, l, y)
            ReportLine.Spacer -> Unit
        }
        return measure(l)
    }

    private fun columns(count: Int): List<Float> {
        // Pierwsza kolumna na nazwę (ćwiczenie, trening), reszta po równo na liczby.
        val first = if (count <= 1) CONTENT_WIDTH else CONTENT_WIDTH * 0.34f
        val rest = if (count <= 1) 0f else (CONTENT_WIDTH - first) / (count - 1)
        return List(count) { if (it == 0) first else rest }
    }

    private fun rowHeight(row: ReportLine.Row): Float {
        val widths = columns(row.cells.size)
        val paint = if (row.header) cellHeader else cell
        return row.cells.mapIndexed { i, c -> layout(c, paint, widths[i] - CELL_PAD).height.toFloat() }.maxOrNull().orZero() + 7f
    }

    private fun drawRow(canvas: Canvas, row: ReportLine.Row, y: Float) {
        val widths = columns(row.cells.size)
        val paint = if (row.header) cellHeader else cell
        var x = MARGIN
        row.cells.forEachIndexed { i, c ->
            text(canvas, c, paint, x, y + 3f, widths[i] - CELL_PAD)
            x += widths[i]
        }
        val bottom = y + rowHeight(row) - 1f
        canvas.drawLine(MARGIN, bottom, MARGIN + CONTENT_WIDTH, bottom, Paint().apply { color = if (row.header) line else track; strokeWidth = 0.5f })
    }

    /** Tydzień jako słupek: zaplanowane w kolorze toru, zrobione ogniem (ta sama zasada co na ekranie, D-32). */
    private fun drawBars(canvas: Canvas, bars: ReportLine.Bars, y: Float) {
        val n = bars.bars.size.coerceAtLeast(1)
        val max = bars.bars.maxOfOrNull { it.planned }?.coerceAtLeast(1) ?: 1
        val slot = CONTENT_WIDTH / n
        val width = (slot * 0.6f).coerceAtMost(40f)
        val chartHeight = BARS_HEIGHT - 22f
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track }
        val donePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fire }
        bars.bars.forEachIndexed { i, bar ->
            val left = MARGIN + i * slot + (slot - width) / 2
            val plannedH = chartHeight * bar.planned / max
            val doneH = chartHeight * bar.done / max
            canvas.drawRoundRect(RectF(left, y + chartHeight - plannedH, left + width, y + chartHeight), 3f, 3f, trackPaint)
            if (bar.done > 0) canvas.drawRoundRect(RectF(left, y + chartHeight - doneH, left + width, y + chartHeight), 3f, 3f, donePaint)
            val label = "${bar.label}  ${bar.done}/${bar.planned}"
            val labelWidth = cell.measureText(label)
            if (labelWidth <= slot) canvas.drawText(label, left + width / 2 - labelWidth / 2, y + chartHeight + 12f, cell)
            else canvas.drawText(bar.label, left + width / 2 - cell.measureText(bar.label) / 2, y + chartHeight + 12f, cell)
        }
    }

    private fun drawFooter(canvas: Canvas, page: Int, pages: Int) {
        val text = "Mysttic Trainings · strona $page z $pages"
        canvas.drawText(text, MARGIN + CONTENT_WIDTH - footer.measureText(text), PAGE_HEIGHT - MARGIN / 2, footer)
    }

    private fun text(canvas: Canvas, value: String, paint: TextPaint, x: Float, y: Float, width: Float) {
        val layout = layout(value, paint, width)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun layout(value: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    private fun Float?.orZero(): Float = this ?: 0f

    private companion object {
        /** A4 w punktach PostScript. */
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        /** Wysokość treści bez marginesów i stopki. */
        const val CONTENT_HEIGHT = PAGE_HEIGHT - 2 * MARGIN - 16f
        const val KEY_WIDTH = 170f
        const val CELL_PAD = 6f
        const val BARS_HEIGHT = 110f
    }
}
