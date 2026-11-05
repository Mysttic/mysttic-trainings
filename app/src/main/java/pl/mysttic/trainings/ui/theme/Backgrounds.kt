package pl.mysttic.trainings.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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

/*
 * Zdjęcie dnia (D-51): rozmyte zdjęcie z bazy ćwiczeń, sprowadzone filtrem do pasma między dwoma kolorami motywu.
 * Karty leżą na nim jako szkło: `onSurface` z alfą motywu (`cardColor()` w DesignSystem.kt), więc zdjęcie przez nie
 * prześwituje. Pasmo i alfa są policzone razem: w każdym punkcie pasma tekst na tle i na szklanej karcie trzyma progi,
 * a karta odróżnia się od tła (`ThemeContractTest`, `python tools/check_contrast.py`, oba w 8 bitach na kanał, jak ekran). Najciaśniej jest w „Ogniu
 * i Krwi”: obrys przycisku na szklanej karcie, dlatego tam zdjęcie jest najciemniejsze.
 */
val FirePhotoLow = Color(0xFF050303)
val FirePhotoHigh = Color(0xFF1A1110)
val PaperPhotoLow = Color(0xFFDFD3CA)
val PaperPhotoHigh = Color(0xFFFFFFFF)
val SteelPhotoLow = Color(0xFF060809)
val SteelPhotoHigh = Color(0xFF2D363F)

/** Pasmo zdjęcia tła: najciemniejszy i najjaśniejszy kolor po filtrze oraz alfa szklanych kart nad nim. */
data class PhotoBand(val low: Color, val high: Color, val glass: Float) {
    /** Jasność piksela (wagi WCAG) wybiera punkt na odcinku low–high, więc każdy piksel zdjęcia leży w paśmie. */
    fun colorFilter(): ColorFilter {
        fun row(l: Float, h: Float) = floatArrayOf((h - l) * 0.2126f, (h - l) * 0.7152f, (h - l) * 0.0722f, 0f, l * 255f)
        return ColorFilter.colorMatrix(
            ColorMatrix(row(low.red, high.red) + row(low.green, high.green) + row(low.blue, high.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)),
        )
    }
}

/**
 * Wariant tła. `id` idzie do DataStore i do kopii zapasowej razem z id motywu, więc istniejących nie zmieniamy;
 * jest unikalny w obrębie motywu. `stops` to kolory gradientu od góry do dołu, rozłożone równo (tak samo rysuje
 * je tło okna w `MainActivity`); pusta lista = jednolite `surface` motywu. `band` ma tylko zdjęcie dnia (D-51).
 * Pierwszy wariant motywu jest domyślny.
 */
enum class AppBackground(
    val theme: AppTheme,
    val id: String,
    val labelPl: String,
    val stops: List<Color>,
    val band: PhotoBand? = null,
) {
    FIRE_SOLID(AppTheme.FIRE, "jednolite", "Jednolite", emptyList()),
    FIRE_EMBER(AppTheme.FIRE, "zar", "Żar", listOf(FireEmberTop, FireEmberMid, FireEmberBottom)),
    FIRE_GLOW(AppTheme.FIRE, "luna", "Łuna", listOf(FireGlowTop, FireGlowMid, FireGlowBottom)),
    FIRE_WINE(AppTheme.FIRE, "wino", "Wino", listOf(FireWineTop, FireWineBottom)),
    FIRE_PHOTO(AppTheme.FIRE, "zdjecie", "Zdjęcie dnia", emptyList(), PhotoBand(FirePhotoLow, FirePhotoHigh, glass = 0.08f)),
    PAPER_SOLID(AppTheme.PAPER, "jednolite", "Jednolite", emptyList()),
    PAPER_DAWN(AppTheme.PAPER, "swit", "Świt", listOf(PaperDawnTop, PaperDawnBottom)),
    PAPER_ROSE(AppTheme.PAPER, "roza", "Róża", listOf(PaperRoseTop, PaperRoseBottom)),
    PAPER_PARCHMENT(AppTheme.PAPER, "pergamin", "Pergamin", listOf(PaperParchmentTop, PaperParchmentBottom)),
    PAPER_PHOTO(AppTheme.PAPER, "zdjecie", "Zdjęcie dnia", emptyList(), PhotoBand(PaperPhotoLow, PaperPhotoHigh, glass = 0.065f)),
    STEEL_SOLID(AppTheme.STEEL, "jednolite", "Jednolite", emptyList()),
    STEEL_NAVY(AppTheme.STEEL, "granat", "Granat", listOf(SteelNavyTop, SteelNavyBottom)),
    STEEL_DEEP(AppTheme.STEEL, "glebia", "Głębia", listOf(SteelDeepTop, SteelDeepBottom)),
    STEEL_DUSK(AppTheme.STEEL, "zmrok", "Zmrok", listOf(SteelDuskTop, SteelDuskBottom)),
    STEEL_PHOTO(AppTheme.STEEL, "zdjecie", "Zdjęcie dnia", emptyList(), PhotoBand(SteelPhotoLow, SteelPhotoHigh, glass = 0.075f)),
    ;

    val gradient: Boolean get() = stops.size >= 2

    /** Zdjęcie dnia (D-51): pod nim karty są szklane. */
    val photo: Boolean get() = band != null

    /**
     * Kolory tła od góry do dołu: dla jednolitego `surface` schematu, dla zdjęcia dolny kolor jego pasma —
     * to widać, zanim zdjęcie się wczyta, i wtedy, gdy baza nie ma zdjęć.
     */
    fun colors(scheme: ColorScheme): List<Color> = band?.let { listOf(it.low) } ?: stops.ifEmpty { listOf(scheme.surface) }

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
        // Zdjęcie dnia rysuje się nad tym kolorem osobno (`PhotoBackdrop`): pod nim jest dół pasma, nie `surface`.
        background(background.band?.low ?: surface)
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
