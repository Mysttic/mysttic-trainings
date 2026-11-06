package pl.mysttic.trainings.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.mysttic.trainings.ui.common.VerticalHairline

/**
 * Lista i szczegół obok siebie (D-42, wdrożone w D-52) — tylko gdy [AppLayout.twoPane]. Lista ma stałą szerokość,
 * szczegół bierze resztę i sam pilnuje czytelnej szerokości; między nimi linia włosowa, bez kart i cieni.
 */
@Composable
fun TwoPane(
    modifier: Modifier = Modifier,
    listWidth: Dp = AppLayout.LIST_PANE_WIDTH.dp,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    Row(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(listWidth).fillMaxHeight()) { list() }
        VerticalHairline()
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { detail() }
    }
}
