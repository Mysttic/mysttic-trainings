package pl.mysttic.trainings.ui.theme

import androidx.compose.foundation.background
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
 * Tło ekranów (issue #6). Od D-55 to zawsze zdjęcie dnia (D-51): rozmyte zdjęcie z bazy ćwiczeń sprowadzone filtrem
 * do pasma motywu, a karty leżą na nim jako szkło. Wybór w Ustawieniach to tylko kolor nad zdjęciem: „Samo zdjęcie”
 * albo jeden z gradientów motywu (D-50) położony na zdjęciu z kryciem [GRADIENT_OVER_PHOTO], więc zdjęcie dalej widać.
 * Tła jednolitego już nie ma; bez zdjęcia w bazie pod gradientem i szkłem leży dolny kolor pasma.
 *
 * Kontrast liczą razem `ThemeContractTest` i `python tools/check_contrast.py`, oba w 8 bitach na kanał, jak ekran:
 * w każdym punkcie pasma i każdym punkcie gradientu tekst na tle i na szklanej karcie trzyma progi, a karta odróżnia
 * się od tła. Gradienty są spokojne, bo mieszają się z pasmem: w ciemnych motywach nie jaśnieją ponad jasny koniec pasma,
 * w jasnych nie ciemnieją poniżej ciemnego.
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

// ---- Brzoza i Mech (D-56) ----
val ForestMistTop = Color(0xFFFFFFFF)         // Mgła: biel opada chłodną mgłą
val ForestMistBottom = Color(0xFFE4EDE8)
val ForestFernTop = Color(0xFFF7FAF1)         // Paproć: jasna zieleń gęstnieje ku dołowi
val ForestFernBottom = Color(0xFFDFEBD0)
val ForestHeatherTop = Color(0xFFFBF8FA)      // Wrzos: blady fiolet przy ziemi
val ForestHeatherBottom = Color(0xFFEDE2EC)

/*
 * Pasmo zdjęcia (D-51): najciemniejszy i najjaśniejszy kolor po filtrze oraz alfa szklanych kart. Pasmo i alfa są
 * policzone razem z gradientami nad zdjęciem. Najciaśniej jest w „Ogniu i Krwi”: obrys przycisku na szklanej karcie,
 * dlatego tam zdjęcie jest najciemniejsze.
 */
val FirePhotoLow = Color(0xFF050303)
val FirePhotoHigh = Color(0xFF1A1110)
val PaperPhotoLow = Color(0xFFDFD3CA)
val PaperPhotoHigh = Color(0xFFFFFFFF)
val SteelPhotoLow = Color(0xFF060809)
val SteelPhotoHigh = Color(0xFF2D363F)
val ForestPhotoLow = Color(0xFFD8DECF)
val ForestPhotoHigh = Color(0xFFFFFFFF)

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

val FireBand = PhotoBand(FirePhotoLow, FirePhotoHigh, glass = 0.08f)
val PaperBand = PhotoBand(PaperPhotoLow, PaperPhotoHigh, glass = 0.065f)
val SteelBand = PhotoBand(SteelPhotoLow, SteelPhotoHigh, glass = 0.075f)
val ForestBand = PhotoBand(ForestPhotoLow, ForestPhotoHigh, glass = 0.065f)

/** Krycie gradientu położonego na zdjęciu (D-55): 0,4, bo przy połowie ciemny gradient gasił zdjęcie jak zasłona. */
const val GRADIENT_OVER_PHOTO = 0.4f

/**
 * Wariant tła. `id` idzie do DataStore i do kopii zapasowej razem z id motywu, więc istniejących nie zmieniamy;
 * jest unikalny w obrębie motywu. `stops` to kolory gradientu od góry do dołu, rozłożone równo (tak samo rysuje je tło
 * okna w `MainActivity`); pusta lista = samo zdjęcie. `band` to pasmo zdjęcia motywu, wspólne dla jego wariantów.
 * Pierwszy wariant motywu („Samo zdjęcie”) jest domyślny.
 */
enum class AppBackground(
    val theme: AppTheme,
    val id: String,
    val labelPl: String,
    val stops: List<Color>,
    val band: PhotoBand,
) {
    FIRE_PHOTO(AppTheme.FIRE, "zdjecie", "Samo zdjęcie", emptyList(), FireBand),
    FIRE_EMBER(AppTheme.FIRE, "zar", "Żar", listOf(FireEmberTop, FireEmberMid, FireEmberBottom), FireBand),
    FIRE_GLOW(AppTheme.FIRE, "luna", "Łuna", listOf(FireGlowTop, FireGlowMid, FireGlowBottom), FireBand),
    FIRE_WINE(AppTheme.FIRE, "wino", "Wino", listOf(FireWineTop, FireWineBottom), FireBand),
    PAPER_PHOTO(AppTheme.PAPER, "zdjecie", "Samo zdjęcie", emptyList(), PaperBand),
    PAPER_DAWN(AppTheme.PAPER, "swit", "Świt", listOf(PaperDawnTop, PaperDawnBottom), PaperBand),
    PAPER_ROSE(AppTheme.PAPER, "roza", "Róża", listOf(PaperRoseTop, PaperRoseBottom), PaperBand),
    PAPER_PARCHMENT(AppTheme.PAPER, "pergamin", "Pergamin", listOf(PaperParchmentTop, PaperParchmentBottom), PaperBand),
    STEEL_PHOTO(AppTheme.STEEL, "zdjecie", "Samo zdjęcie", emptyList(), SteelBand),
    STEEL_NAVY(AppTheme.STEEL, "granat", "Granat", listOf(SteelNavyTop, SteelNavyBottom), SteelBand),
    STEEL_DEEP(AppTheme.STEEL, "glebia", "Głębia", listOf(SteelDeepTop, SteelDeepBottom), SteelBand),
    STEEL_DUSK(AppTheme.STEEL, "zmrok", "Zmrok", listOf(SteelDuskTop, SteelDuskBottom), SteelBand),
    FOREST_PHOTO(AppTheme.FOREST, "zdjecie", "Samo zdjęcie", emptyList(), ForestBand),
    FOREST_MIST(AppTheme.FOREST, "mgla", "Mgła", listOf(ForestMistTop, ForestMistBottom), ForestBand),
    FOREST_FERN(AppTheme.FOREST, "paproc", "Paproć", listOf(ForestFernTop, ForestFernBottom), ForestBand),
    FOREST_HEATHER(AppTheme.FOREST, "wrzos", "Wrzos", listOf(ForestHeatherTop, ForestHeatherBottom), ForestBand),
    ;

    /** Gradient na zdjęciu (D-55); bez niego „Samo zdjęcie”. */
    val gradient: Boolean get() = stops.size >= 2

    /**
     * Kolory tła od góry do dołu bez zdjęcia: dół pasma, a pod gradientem gradient zmieszany z dołem pasma. To widać,
     * zanim zdjęcie się wczyta, i wtedy, gdy baza nie ma zdjęć; tak samo maluje się tło okna w `MainActivity`.
     */
    fun colors(): List<Color> = if (gradient) stops.map { composite(band.low, it, GRADIENT_OVER_PHOTO) } else listOf(band.low)

    companion object {
        /** Warianty motywu w kolejności z Ustawień; pierwszy to samo zdjęcie i jest domyślny. */
        fun of(theme: AppTheme): List<AppBackground> = entries.filter { it.theme == theme }

        /**
         * Wybór z DataStore albo z kopii; nieznany id (także „jednolite” sprzed D-55) i tło innego motywu spadają
         * na samo zdjęcie tego motywu.
         */
        fun resolve(theme: AppTheme, id: String?): AppBackground = of(theme).let { own -> own.firstOrNull { it.id == id } ?: own.first() }
    }
}

/** Kolor [top] z kryciem [alpha] położony na [under], zmieszany w sRGB tak, jak składa warstwy Android. */
fun composite(under: Color, top: Color, alpha: Float): Color = Color(
    red = under.red + (top.red - under.red) * alpha,
    green = under.green + (top.green - under.green) * alpha,
    blue = under.blue + (top.blue - under.blue) * alpha,
)

/** Podkład tła: dół pasma zdjęcia. Na nim rysuje się zdjęcie (`PhotoBackdrop`), a nad zdjęciem [gradientOverPhoto]. */
fun Modifier.appBackground(background: AppBackground): Modifier = background(background.band.low)

/**
 * Gradient na zdjęciu (D-55) z kryciem [GRADIENT_OVER_PHOTO]. Idzie z ditheringiem, bo ciemne przejścia na kilku
 * poziomach jasności inaczej układają się w widoczne pasy; pędzel powstaje raz na rozmiar (`drawWithCache`), a nie
 * przy każdej klatce playera. Bez gradientu nic nie rysuje.
 */
fun Modifier.gradientOverPhoto(background: AppBackground): Modifier =
    if (!background.gradient) {
        this
    } else {
        drawWithCache {
            val paint = Paint().apply {
                shader = LinearGradientShader(
                    from = Offset.Zero,
                    to = Offset(0f, size.height),
                    colors = background.stops.map { it.copy(alpha = GRADIENT_OVER_PHOTO) },
                )
                nativePaint.isDither = true
            }
            onDrawBehind {
                drawIntoCanvas { it.drawRect(0f, 0f, size.width, size.height, paint) }
            }
        }
    }
