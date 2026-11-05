package pl.mysttic.trainings.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Układ okna dla całego drzewa; ustawia go `TrainingsRoot` z `BoxWithConstraints`. */
val LocalAppLayout = staticCompositionLocalOf { AppLayout.PHONE }

/**
 * Kolumna treści nie szersza niż [AppLayout.CONTENT_MAX_WIDTH]; na telefonie bez zmian. Kolejność ma znaczenie:
 * `fillMaxWidth` przed `widthIn` ustawia minimum na szerokość rodzica i `widthIn` już jej nie zmniejszy.
 */
fun Modifier.readableWidth(): Modifier = this.widthIn(max = AppLayout.CONTENT_MAX_WIDTH.dp).fillMaxWidth()

/**
 * Ekran przewijany na całą wysokość, a w poziomie wyśrodkowana kolumna czytelnej szerokości — do `LazyColumn`
 * i przewijanych `Column` na ekranach głównych. Na telefonie nic nie zmienia.
 */
fun Modifier.centeredReadable(): Modifier = this
    .fillMaxSize()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = AppLayout.CONTENT_MAX_WIDTH.dp)

/** Treść ekranu wyśrodkowana w czytelnej szerokości; na tablecie nie rozlewa się na 1280 dp. */
@Composable
fun ReadableBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.readableWidth().fillMaxSize(), content = content)
    }
}
