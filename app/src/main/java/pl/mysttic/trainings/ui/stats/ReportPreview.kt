package pl.mysttic.trainings.ui.stats

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Podgląd raportu: pierwsze strony jako obrazy i liczba wszystkich stron pliku. */
class ReportPreview(val pages: List<ImageBitmap>, val pageCount: Int)

/**
 * Podgląd raportu w aplikacji (D-42) przez platformowy `PdfRenderer`, bez bibliotek: rysuje ten sam plik,
 * który idzie do udostępnienia, więc widać dokładnie to, co dostanie odbiorca. Tylko pierwsze strony,
 * bo każda to kilka megabajtów bitmapy.
 */
object ReportPreviewRenderer {

    const val MAX_PAGES = 3

    /** Szerokość strony w pikselach: A4 czytelne na telefonie i w czytelnej szerokości tabletu. */
    private const val WIDTH_PX = 1080

    fun render(file: File, maxPages: Int = MAX_PAGES): ReportPreview {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (t: Throwable) {
            descriptor.close()
            throw t
        }
        // PdfRenderer zamyka deskryptor razem ze sobą.
        return renderer.use {
            val pages = (0 until minOf(maxPages, renderer.pageCount)).map { index ->
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(WIDTH_PX, WIDTH_PX * page.height / page.width, Bitmap.Config.ARGB_8888)
                    // PDF nie maluje tła: biel kartki, na której raport się drukuje, a nie kolor motywu.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }
            ReportPreview(pages, renderer.pageCount)
        }
    }
}
