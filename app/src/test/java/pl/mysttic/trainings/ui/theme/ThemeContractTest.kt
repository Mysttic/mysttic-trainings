package pl.mysttic.trainings.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Kontrakt wyglądu (D-17, D-32, D-39, D-50) pilnowany testem, a nie tylko skryptem: `tools/check_contrast.py` trzeba
 * pamiętać uruchomić, a to leci na CI przy każdej zmianie. Lista par jest ta sama co w skrypcie i obie mają
 * pozostać zgodne — skrypt zostaje do ręcznego przeglądu z wypisanymi liczbami.
 *
 * Test nie ogląda ekranów; sprawdza to, co da się sprawdzić bez telefonu: że każdy motyw ma komplet ról,
 * że żadna rola nie została na domyślnym fiolecie Material 3, że pary tekst/tło trzymają progi WCAG
 * i że każde tło motywu (D-50) trzyma je w każdym punkcie gradientu.
 * Pierwsze uruchomienie na telefonie dalej jest potrzebne — kontrast to nie wszystko.
 */
class ThemeContractTest {

    /** (tekst, tło, próg, opis) — 4,5:1 dla tekstu, 3:1 dla elementów interfejsu, niżej progi separacji warstw. */
    private val pary = listOf(
        Para("onSurface", "surface", 4.5, "tekst na powierzchni"),
        Para("onSurfaceVariant", "surface", 4.5, "tekst pomocniczy"),
        Para("onSurfaceVariant", "surfaceVariant", 4.5, "tekst na surfaceVariant"),
        Para("onBackground", "background", 4.5, "tekst na tle"),
        Para("onSurface", "surfaceContainerHighest", 4.5, "tekst na karcie"),
        Para("onSurface", "surfaceContainer", 4.5, "tekst na pasku nawigacji"),
        Para("onSurface", "surfaceContainerHigh", 4.5, "tekst w dialogu"),
        Para("onPrimary", "primary", 4.5, "tekst przycisku"),
        Para("onPrimaryContainer", "primaryContainer", 4.5, "tekst na kontenerze primary"),
        Para("onSecondaryContainer", "secondaryContainer", 4.5, "zaznaczony chip, aktywna zakładka"),
        Para("onTertiaryContainer", "tertiaryContainer", 4.5, "tekst na kontenerze tertiary"),
        Para("onErrorContainer", "errorContainer", 4.5, "tekst na kontenerze błędu"),
        Para("onError", "error", 4.5, "tekst na kolorze błędu"),
        Para("onSecondary", "secondary", 4.5, "tekst na secondary"),
        Para("inverseOnSurface", "inverseSurface", 4.5, "tekst na inverseSurface"),
        Para("primary", "surface", 4.5, "primary jako tekst"),
        Para("primary", "surfaceContainerHighest", 4.5, "primary jako tekst na karcie"),
        Para("primary", "background", 4.5, "primary na tle"),
        Para("error", "surface", 4.5, "„Brakuje sprzętu”"),
        Para("error", "surfaceContainerHighest", 4.5, "błąd na karcie"),
        Para("secondary", "surfaceContainer", 4.5, "label aktywnej zakładki"),
        Para("outline", "surface", 3.0, "obrys kontrolki"),
        Para("outline", "surfaceContainerHighest", 3.0, "obrys na karcie"),
        Para("primary", "surfaceContainer", 3.0, "ikona na pasku"),
        Para("surfaceContainerHighest", "background", 1.12, "karta odróżniona od tła"),
        Para("surfaceContainer", "background", 1.04, "pasek odróżniony od tła"),
        Para("tertiary", "surface", 4.5, "nagłówek sekcji, seria dni"),
        Para("tertiary", "surfaceContainer", 4.5, "żar na karcie"),
        Para("onTertiary", "tertiary", 4.5, "tekst na żarze („Zastosuj”)"),
        Para("primary", "surfaceContainerHigh", 3.0, "wskaźnik nawigacji, pasek „to teraz”"),
    )

    @Test
    fun `kazdy motyw trzyma progi kontrastu`() {
        val bledy = mutableListOf<String>()
        forEachScheme { opis, scheme ->
            pary.forEach { para ->
                val ratio = kontrast(rola(scheme, para.fg), rola(scheme, para.bg))
                if (ratio < para.prog) {
                    bledy += "$opis: ${para.fg} na ${para.bg} = %.2f:1 (min %.2f) — %s"
                        .format(ratio, para.prog, para.opis)
                }
            }
        }
        assertTrue(bledy.joinToString("\n"), bledy.isEmpty())
    }

    /**
     * Pominięta rola spada na paletę bazową M3 (fiolet), a tego na ekranie nie widać od razu — bywa, że tylko
     * w jednym dialogu. Biel i czerń pomijamy: `onError`, `surfaceContainerLowest` czy `scrim` mają prawo się
     * z domyślnymi zgadzać, bo neutralne są neutralne.
     */
    @Test
    fun `zadna rola nie zostaje na domyslnej palecie Material 3`() {
        val bledy = mutableListOf<String>()
        AppTheme.entries.forEach { theme ->
            val scheme = schemeFor(theme)
            val bazowy = if (theme.dark) darkColorScheme() else lightColorScheme()
            ROLE.keys.forEach { nazwa ->
                val nasza = rola(scheme, nazwa)
                if (nasza == rola(bazowy, nazwa) && nasycenie(nasza) > 0.05) {
                    bledy += "${theme.id}: rola $nazwa została na domyślnej M3"
                }
            }
        }
        assertTrue(bledy.joinToString("\n"), bledy.isEmpty())
    }

    @Test
    fun `motywy roznia sie akcja i tlem`() {
        val akcje = AppTheme.entries.map { schemeFor(it).primary }
        val tla = AppTheme.entries.map { schemeFor(it).surface }
        assertEquals("każdy motyw ma własną akcję", akcje.size, akcje.toSet().size)
        assertEquals("każdy motyw ma własne tło", tla.size, tla.toSet().size)
    }

    /** `dark` steruje ikonami paska stanu w `MainActivity`, więc nie może kłamać o jasności tła. */
    @Test
    fun `flaga ciemnosci zgadza sie z jasnoscia tla`() {
        AppTheme.entries.forEach { theme ->
            val jasnosc = luminancja(schemeFor(theme).surface)
            assertEquals("${theme.id}: flaga dark nie pasuje do tła", theme.dark, jasnosc < 0.2)
        }
    }

    /**
     * „Error z innej rodziny odcieni niż primary” (D-32) — inaczej ostrzeżenie zlewa się z akcją. Próg 18°
     * jest niżej niż dzisiejsze minimum (20,2° w „Ogniu i Krwi” w motywie jasnym): test ma zatrzymać nową paletę,
     * która postawi błąd obok akcji, a nie czerwienić repozytorium przy każdym drgnięciu odcienia.
     */
    @Test
    fun `blad jest z innej rodziny odcieni niz akcja`() {
        forEachScheme { opis, scheme ->
            val odleglosc = odlegloscOdcienia(odcien(scheme.primary), odcien(scheme.error))
            assertTrue(
                "$opis: błąd (${odcien(scheme.error).toInt()}°) za blisko akcji (${odcien(scheme.primary).toInt()}°): " +
                    "%.1f° różnicy".format(odleglosc),
                odleglosc >= 18.0,
            )
        }
    }

    /**
     * Tło ekranów (D-50) leży pod tymi samymi kartami i napisami co `surface`, więc w każdym swoim punkcie trzyma
     * wszystkie pary, w których tłem jest `surface` albo `background`: tekst, obrysy, karta i pasek odróżnione od tła.
     * Punkty liczymy tak, jak rysuje je Android: liniowo w sRGB między równo rozłożonymi kolorami. Sprawdzanie
     * samych końców nie wystarcza — w jasnym motywie środek przejścia bywa ciemniejszy od obu końców.
     */
    @Test
    fun `kazde tlo trzyma progi kontrastu swojego motywu`() {
        val doTla = pary.filter { it.bg == "surface" || it.bg == "background" }
        val bledy = mutableListOf<String>()
        AppBackground.entries.forEach { tlo ->
            val scheme = schemeFor(tlo.theme)
            probki(tlo).forEach { punkt ->
                val naTle = scheme.copy(surface = punkt, background = punkt)
                doTla.forEach { para ->
                    val ratio = kontrast(rola(naTle, para.fg), punkt)
                    if (ratio < para.prog) {
                        bledy += "${tlo.theme.id}/${tlo.id}: ${para.fg} na ${hex(punkt)} = %.3f:1 (min %.2f) — %s"
                            .format(ratio, para.prog, para.opis)
                    }
                }
            }
        }
        assertTrue(bledy.distinct().joinToString("\n"), bledy.isEmpty())
    }

    /** Ikony pasków systemu idą za `AppTheme.dark`, więc gradient nie może przejść na drugą stronę jasności. */
    @Test
    fun `tlo nie przechodzi na druga strone jasnosci motywu`() {
        AppBackground.entries.forEach { tlo ->
            probki(tlo).forEach { punkt ->
                assertEquals("${tlo.theme.id}/${tlo.id}: ${hex(punkt)} kłamie o jasności", tlo.theme.dark, luminancja(punkt) < 0.2)
            }
        }
    }

    /** Issue #6: każdy motyw ma kilka wariantów tła; pierwszy jest jednolity, bo to wygląd sprzed D-50. */
    @Test
    fun `kazdy motyw ma tlo jednolite i kilka gradientow`() {
        AppTheme.entries.forEach { theme ->
            val tla = AppBackground.of(theme)
            assertTrue("${theme.id}: pierwsze tło ma być jednolite", !tla.first().gradient)
            assertEquals("${theme.id}: jedno tło jednolite", 1, tla.count { !it.gradient })
            assertTrue("${theme.id}: za mało gradientów (${tla.count { it.gradient }})", tla.count { it.gradient } >= 3)
            assertEquals("${theme.id}: identyfikatory tła powtarzają się", tla.size, tla.map { it.id }.toSet().size)
            assertEquals("${theme.id}: dwa gradienty o tych samych kolorach", tla.size, tla.map { it.stops }.toSet().size)
            tla.filter { it.gradient }.forEach { tlo ->
                assertTrue("${theme.id}/${tlo.id}: gradient bez przejścia", tlo.stops.toSet().size > 1)
            }
        }
    }

    @Test
    fun `tlo wraca z zapisu, a obce i smiec spadaja na jednolite`() {
        AppBackground.entries.forEach { assertEquals(it, AppBackground.resolve(it.theme, it.id)) }
        AppTheme.entries.forEach { theme ->
            val jednolite = AppBackground.of(theme).first()
            assertEquals(jednolite, AppBackground.resolve(theme, null))
            assertEquals(jednolite, AppBackground.resolve(theme, "tęcza"))
        }
        // Tło innego motywu nie trzyma kontrastu z tym schematem: „Żar” z „Ognia i Krwi” nie wchodzi do „Stali”.
        assertEquals(AppBackground.STEEL_SOLID, AppBackground.resolve(AppTheme.STEEL, AppBackground.FIRE_EMBER.id))
        // Tło jednolite to dalej `surface` motywu, więc wygląd sprzed D-50 się nie zmienia.
        AppTheme.entries.forEach { theme ->
            assertEquals(listOf(schemeFor(theme).surface), AppBackground.of(theme).first().colors(schemeFor(theme)))
        }
    }

    @Test
    fun `identyfikatory wracaja z zapisu, a smiec spada na domyslny`() {
        AppTheme.entries.forEach { assertEquals(it, AppTheme.fromId(it.id)) }
        // Identyfikator z kopii zapasowej albo z DataStore po zmianie nazw — nie wolno się na tym wywrócić.
        assertEquals(AppTheme.FIRE, AppTheme.fromId("nokturn"))
        assertEquals(AppTheme.FIRE, AppTheme.fromId(null))
        assertEquals("motyw domyślny to „Ogień i Krew”", AppTheme.FIRE, AppTheme.entries.first())
    }

    // ---- narzędzia ----

    private data class Para(val fg: String, val bg: String, val prog: Double, val opis: String)

    private fun forEachScheme(action: (String, ColorScheme) -> Unit) {
        AppTheme.entries.forEach { theme -> action(theme.id, schemeFor(theme)) }
    }

    private fun rola(scheme: ColorScheme, nazwa: String): Color =
        ROLE.getValue(nazwa)(scheme)

    /** Punkty tła tak, jak rysuje je Android: liniowo w sRGB między równo rozłożonymi kolorami gradientu. */
    private fun probki(tlo: AppBackground, naOdcinek: Int = 64): List<Color> {
        val kolory = tlo.colors(schemeFor(tlo.theme))
        if (kolory.size == 1) return kolory
        return kolory.zipWithNext().flatMap { (a, b) ->
            (0..naOdcinek).map { i ->
                val t = i / naOdcinek.toFloat()
                Color(
                    red = a.red + (b.red - a.red) * t,
                    green = a.green + (b.green - a.green) * t,
                    blue = a.blue + (b.blue - a.blue) * t,
                )
            }
        }
    }

    private fun hex(c: Color): String =
        "#%02X%02X%02X".format((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

    private fun luminancja(c: Color): Double {
        fun kanal(v: Float): Double {
            val x = v.toDouble()
            return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * kanal(c.red) + 0.7152 * kanal(c.green) + 0.0722 * kanal(c.blue)
    }

    private fun kontrast(a: Color, b: Color): Double {
        val la = luminancja(a)
        val lb = luminancja(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Odcień w stopniach (HSL); dla szarości zwraca -1, czego test odcieni nie dotyczy. */
    private fun odcien(c: Color): Double {
        val r = c.red
        val g = c.green
        val b = c.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max == min) return -1.0
        val d = (max - min).toDouble()
        val h = when (max) {
            r -> ((g - b) / d + if (g < b) 6 else 0)
            g -> ((b - r) / d + 2)
            else -> ((r - g) / d + 4)
        }
        return h * 60.0
    }

    /** Nasycenie HSL; biel, czerń i szarości mają zero. */
    private fun nasycenie(c: Color): Double {
        val max = maxOf(c.red, c.green, c.blue).toDouble()
        val min = minOf(c.red, c.green, c.blue).toDouble()
        if (max == min) return 0.0
        val l = (max + min) / 2
        return (max - min) / (1 - kotlin.math.abs(2 * l - 1)).coerceAtLeast(1e-6)
    }

    private fun odlegloscOdcienia(a: Double, b: Double): Double {
        if (a < 0 || b < 0) return 360.0
        val d = kotlin.math.abs(a - b) % 360.0
        return minOf(d, 360.0 - d)
    }

    private companion object {
        /** Komplet 35 ról schematu — ta sama lista, co jawnie wypisana w Theme.kt. */
        val ROLE: Map<String, (ColorScheme) -> Color> = mapOf(
            "primary" to { s: ColorScheme -> s.primary },
            "onPrimary" to { s: ColorScheme -> s.onPrimary },
            "primaryContainer" to { s: ColorScheme -> s.primaryContainer },
            "onPrimaryContainer" to { s: ColorScheme -> s.onPrimaryContainer },
            "secondary" to { s: ColorScheme -> s.secondary },
            "onSecondary" to { s: ColorScheme -> s.onSecondary },
            "secondaryContainer" to { s: ColorScheme -> s.secondaryContainer },
            "onSecondaryContainer" to { s: ColorScheme -> s.onSecondaryContainer },
            "tertiary" to { s: ColorScheme -> s.tertiary },
            "onTertiary" to { s: ColorScheme -> s.onTertiary },
            "tertiaryContainer" to { s: ColorScheme -> s.tertiaryContainer },
            "onTertiaryContainer" to { s: ColorScheme -> s.onTertiaryContainer },
            "error" to { s: ColorScheme -> s.error },
            "onError" to { s: ColorScheme -> s.onError },
            "errorContainer" to { s: ColorScheme -> s.errorContainer },
            "onErrorContainer" to { s: ColorScheme -> s.onErrorContainer },
            "background" to { s: ColorScheme -> s.background },
            "onBackground" to { s: ColorScheme -> s.onBackground },
            "surface" to { s: ColorScheme -> s.surface },
            "onSurface" to { s: ColorScheme -> s.onSurface },
            "surfaceVariant" to { s: ColorScheme -> s.surfaceVariant },
            "onSurfaceVariant" to { s: ColorScheme -> s.onSurfaceVariant },
            "surfaceContainerLowest" to { s: ColorScheme -> s.surfaceContainerLowest },
            "surfaceContainerLow" to { s: ColorScheme -> s.surfaceContainerLow },
            "surfaceContainer" to { s: ColorScheme -> s.surfaceContainer },
            "surfaceContainerHigh" to { s: ColorScheme -> s.surfaceContainerHigh },
            "surfaceContainerHighest" to { s: ColorScheme -> s.surfaceContainerHighest },
            "surfaceBright" to { s: ColorScheme -> s.surfaceBright },
            "surfaceDim" to { s: ColorScheme -> s.surfaceDim },
            "outline" to { s: ColorScheme -> s.outline },
            "outlineVariant" to { s: ColorScheme -> s.outlineVariant },
            "inverseSurface" to { s: ColorScheme -> s.inverseSurface },
            "inverseOnSurface" to { s: ColorScheme -> s.inverseOnSurface },
            "inversePrimary" to { s: ColorScheme -> s.inversePrimary },
            "scrim" to { s: ColorScheme -> s.scrim },
        )
    }
}
