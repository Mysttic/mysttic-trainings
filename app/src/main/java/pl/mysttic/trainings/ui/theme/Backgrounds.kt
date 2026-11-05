package pl.mysttic.trainings.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativePaint

/*
 * Tło ekranów w motywie (D-50, issue #6): jednolite albo gradient z góry na dół. Każdy motyw ma własne warianty,
 * bo gradient leży pod tymi samymi kartami i napisami co tło jednolite i w każdym swoim punkcie trzyma progi
 * kontrastu tego motywu: tekst i obrysy na tle, karta i pasek odróżnione od tła. Pilnują tego `ThemeContractTest`
 * i `python tools/check_contrast.py`, które próbkują gradient tak, jak rysuje go Android (liniowo w sRGB).
 * Najciaśniejszy jest próg karty (`surfaceContainer` wobec tła), więc w ciemnych motywach gradient nie jaśnieje
 * ponad kartę, tylko zmienia odcień i schodzi w czerń, a w jasnym nie ciemnieje do karty.
 */

// ---- Ogień i Krew ----
val FireEmberTop = Color(0xFF070304)          // Żar: czerń u góry…
val FireEmberMid = Color(0xFF100506)
val FireEmberBottom = Color(0xFF260502)       // …a od dołu tli się czerwień
val FireGlowTop = Color(0xFF26040A)           // Łuna: karmazyn nad głową, gaśnie ku czerni
val FireGlowMid = Color(0xFF0E0507)
val FireGlowBottom = Color(0xFF060203)
val FireWineTop = Color(0xFF090407)           // Wino: ciemna śliwka u dołu
val FireWineBottom = Color(0xFF22051A)

// ---- Papier i Ogień ----
val PaperDawnTop = Color(0xFFFFFFFF)          // Świt: biel przechodzi w brzoskwinię
val PaperDawnBottom = Color(0xFFFCEDE4)
val PaperRoseTop = Color(0xFFFDF7F5)          // Róża: papier zabarwiony bladym różem
val PaperRoseBottom = Color(0xFFFCE9EB)
val PaperParchmentTop = Color(0xFFFDF8F0)     // Pergamin: papier żółknie ku dołowi
val PaperParchmentBottom = Color(0xFFFAEED8)

// ---- Stal i Bursztyn: tylko chłodne odcienie, bo bursztyn jest zarezerwowany dla akcji ----
val SteelNavyTop = Color(0xFF0C1116)          // Granat
val SteelNavyBottom = Color(0xFF0A1830)
val SteelDeepTop = Color(0xFF0C1317)          // Głębia: grafit schodzi w morską zieleń
val SteelDeepBottom = Color(0xFF062220)
val SteelDuskTop = Color(0xFF0D1217)          // Zmrok: grafit gaśnie w fiolecie
val SteelDuskBottom = Color(0xFF1A1430)

/**
 * Wariant tła. `id` idzie do DataStore i do kopii zapasowej razem z id motywu, więc istniejących nie zmieniamy;
 * jest unikalny w obrębie motywu. `stops` to kolory gradientu od góry do dołu, rozłożone równo (tak samo rysuje
 * je tło okna w `MainActivity`); pusta lista = jednolite `surface` motywu. Pierwszy wariant motywu jest domyślny.
 */
enum class AppBackground(val theme: AppTheme, val id: String, val labelPl: String, val stops: List<Color>) {
    FIRE_SOLID(AppTheme.FIRE, "jednolite", "Jednolite", emptyList()),
    FIRE_EMBER(AppTheme.FIRE, "zar", "Żar", listOf(FireEmberTop, FireEmberMid, FireEmberBottom)),
    FIRE_GLOW(AppTheme.FIRE, "luna", "Łuna", listOf(FireGlowTop, FireGlowMid, FireGlowBottom)),
    FIRE_WINE(AppTheme.FIRE, "wino", "Wino", listOf(FireWineTop, FireWineBottom)),
    PAPER_SOLID(AppTheme.PAPER, "jednolite", "Jednolite", emptyList()),
    PAPER_DAWN(AppTheme.PAPER, "swit", "Świt", listOf(PaperDawnTop, PaperDawnBottom)),
    PAPER_ROSE(AppTheme.PAPER, "roza", "Róża", listOf(PaperRoseTop, PaperRoseBottom)),
    PAPER_PARCHMENT(AppTheme.PAPER, "pergamin", "Pergamin", listOf(PaperParchmentTop, PaperParchmentBottom)),
    STEEL_SOLID(AppTheme.STEEL, "jednolite", "Jednolite", emptyList()),
    STEEL_NAVY(AppTheme.STEEL, "granat", "Granat", listOf(SteelNavyTop, SteelNavyBottom)),
    STEEL_DEEP(AppTheme.STEEL, "glebia", "Głębia", listOf(SteelDeepTop, SteelDeepBottom)),
    STEEL_DUSK(AppTheme.STEEL, "zmrok", "Zmrok", listOf(SteelDuskTop, SteelDuskBottom)),
    ;

    val gradient: Boolean get() = stops.size >= 2

    /** Kolory tła od góry do dołu; dla tła jednolitego jeden: `surface` schematu. */
    fun colors(scheme: ColorScheme): List<Color> = stops.ifEmpty { listOf(scheme.surface) }

    companion object {
        /** Warianty motywu w kolejności z Ustawień; pierwszy jest jednolity i domyślny. */
        fun of(theme: AppTheme): List<AppBackground> = entries.filter { it.theme == theme }

        /** Wybór z DataStore albo z kopii; nieznany id (albo tło innego motywu) spada na tło jednolite motywu. */
        fun resolve(theme: AppTheme, id: String?): AppBackground = of(theme).let { own -> own.firstOrNull { it.id == id } ?: own.first() }
    }
}

/**
 * Maluje tło ekranów: `surface` dla tła jednolitego, gradient dla pozostałych. Gradient idzie z ditheringiem,
 * bo ciemne przejścia na kilku poziomach jasności inaczej układają się w widoczne pasy; pędzel powstaje raz
 * na rozmiar (`drawWithCache`), a nie przy każdej klatce playera.
 */
fun Modifier.appBackground(background: AppBackground, surface: Color): Modifier =
    if (!background.gradient) {
        background(surface)
    } else {
        drawWithCache {
            val paint = Paint().apply {
                shader = LinearGradientShader(from = Offset.Zero, to = Offset(0f, size.height), colors = background.stops)
                nativePaint.isDither = true
            }
            onDrawBehind {
                drawIntoCanvas { it.drawRect(0f, 0f, size.width, size.height, paint) }
            }
        }
    }
