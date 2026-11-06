package pl.mysttic.trainings.ui.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.Hairline
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.ProgressionCard
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.SectionTitle
import pl.mysttic.trainings.ui.common.StatCellData
import pl.mysttic.trainings.ui.common.StatRow
import pl.mysttic.trainings.ui.common.WorkoutCard
import pl.mysttic.trainings.ui.theme.AppBackground
import pl.mysttic.trainings.ui.theme.AppTheme
import pl.mysttic.trainings.ui.theme.LocalAppBackground
import pl.mysttic.trainings.ui.theme.TrainingsTheme
import pl.mysttic.trainings.ui.theme.appBackground
import pl.mysttic.trainings.ui.theme.gradientOverPhoto

/**
 * Zrzuty motywów bez emulatora (D-40): Robolectric rysuje natywnie, Roborazzi zapisuje PNG.
 * Nie zastępują telefonu — pokazują, jak każdy motyw i każde jego tło (D-50) trzyma komponenty systemu,
 * i sprawiają, że zmiana odcienia jest widoczna w diffie zamiast dopiero po instalacji.
 *
 * - `./gradlew testDebugUnitTest` — rysuje i nic nie porównuje (tyle robi CI),
 * - `./gradlew testDebugUnitTest -Proborazzi.record=true` — zapisuje wzorce do `src/test/screenshots`,
 * - `./gradlew testDebugUnitTest -Proborazzi.verify=true` — porównuje z wzorcami.
 *
 * Wzorce powstaną przy pierwszym uruchomieniu na maszynie z Android SDK; do tego czasu test jest wartą
 * tyle, ile kompilacja i samo rysowanie — czyli i tak łapie wywrotkę motywu.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemeScreenshotTest {

    @Test
    fun `kazdy motyw`() {
        AppTheme.entries.forEach { theme ->
            captureRoboImage(filePath = "src/test/screenshots/${theme.id}.png") {
                TrainingsTheme(theme = theme) {
                    Sampler(theme.labelPl, if (theme.dark) "ciemny" else "jasny")
                }
            }
        }
    }

    /**
     * Tła (D-50, D-51, D-55): te same komponenty na każdym wariancie. Zdjęcie przychodzi z bazy asynchronicznie, więc
     * zrzut pokazuje to, co widać przed jego wczytaniem: dół pasma, na nim gradient, a nad tym szklane karty.
     */
    @Test
    fun `kazde tlo`() {
        AppBackground.entries.forEach { tlo ->
            captureRoboImage(filePath = "src/test/screenshots/${tlo.theme.id}-${tlo.id}.png") {
                TrainingsTheme(theme = tlo.theme, background = tlo) {
                    Sampler(tlo.theme.labelPl, "tło ${tlo.labelPl}")
                }
            }
        }
    }
}

/**
 * Jeden ekran ze wszystkim, co niesie kolor: karta treningu z akcją, karta progresji (żar),
 * statystyki, chipy, linia włosowa i komunikat błędu. Celowo bez ViewModelu — zrzut ma pokazywać paletę,
 * nie stan aplikacji.
 */
@Composable
private fun Sampler(nazwa: String, motyw: String) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .appBackground(LocalAppBackground.current)
            .gradientOverPhoto(LocalAppBackground.current)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(nazwa, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Overline("$motyw · seria 6 dni")

        WorkoutCard(
            name = "Całe ciało A",
            counter = "Dzień 3 z 14",
            meta = "9 ćwiczeń · ok. 15 min · bez sprzętu",
            weekDone = 3,
            weekTotal = 4,
        ) {
            Spacer(Modifier.height(10.dp))
            PrimaryAction(text = "Zacznij trening", onClick = {}, modifier = Modifier.fillMaxWidth())
        }

        ProgressionCard(
            text = "Pompki szerokie: z 11 na 12 · Przysiady: z 15 na 16",
            actionLabel = "Zastosuj",
            onAction = {},
            boxed = true,
        )

        StatRow(
            cells = listOf(
                StatCellData("6 dni", "z rzędu", accent = true),
                StatCellData("3", "treningi"),
                StatCellData("42 min", "w ruchu"),
            ),
        )

        SectionTitle("Ćwiczenia")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill(text = "Filtry · 2", selected = true, onClick = {})
            FilterPill(text = "Mój sprzęt", selected = false, onClick = {})
            FilterPill(text = "Kardio", selected = true, onClick = {})
        }
        Hairline()
        Text(
            text = "Brakuje sprzętu: kettlebell",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        SecondaryAction(text = "Pokaż zamiennik", onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}
