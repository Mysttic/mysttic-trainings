package pl.mysttic.trainings.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.mysttic.trainings.data.settings.UserSettings
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment

/** Streszczenia kategorii Ustawień (D-52): to, co widać w wierszu, zanim kategoria się otworzy. */
class SettingsSummaryTest {

    @Test
    fun `trening mowi poziom, odliczanie i sygnaly`() {
        val settings = UserSettings(difficulty = Difficulty.ADVANCED, countdownSec = 5, vibrationEnabled = false)
        assertEquals("Poziom 3 · odliczanie 5 s · dźwięk i głos", SettingsSummary.training(settings))
        assertEquals(
            "Poziom 1 · bez odliczania · bez sygnałów",
            SettingsSummary.training(
                UserSettings(difficulty = Difficulty.BEGINNER, countdownSec = 0, soundEnabled = false, ttsEnabled = false, vibrationEnabled = false),
            ),
        )
    }

    @Test
    fun `sygnaly lacza sie po polsku`() {
        assertEquals("dźwięk, głos i wibracja", SettingsSummary.signals(UserSettings()))
        assertEquals("głos", SettingsSummary.signals(UserSettings(soundEnabled = false, vibrationEnabled = false)))
        assertEquals("dźwięk i wibracja", SettingsSummary.signals(UserSettings(ttsEnabled = false)))
    }

    @Test
    fun `sprzet w kolejnosci listy, najwyzej trzy pozycje`() {
        assertEquals("Bez sprzętu", SettingsSummary.equipment(emptySet()))
        assertEquals("Hantle, drążek", SettingsSummary.equipment(setOf(Equipment.PULL_UP_BAR, Equipment.DUMBBELLS)))
        assertEquals(
            "Hantle, kettlebell, niski drążek i jeszcze 2",
            SettingsSummary.equipment(
                setOf(Equipment.TREADMILL, Equipment.LOW_BAR, Equipment.DUMBBELLS, Equipment.KETTLEBELL, Equipment.JUMP_ROPE),
            ),
        )
    }

    @Test
    fun `przypomnienie z godzina albo wylaczone`() {
        assertEquals("Wyłączone", SettingsSummary.reminder(UserSettings(reminderEnabled = false)))
        assertEquals(
            "Codziennie o 07:05",
            SettingsSummary.reminder(UserSettings(reminderEnabled = true, reminderHour = 7, reminderMinute = 5)),
        )
    }

    @Test
    fun `dane z podpisem autora i bez Health Connect, gdy go nie ma`() {
        assertEquals("Kopia zapasowa, Health Connect, podpis planów", SettingsSummary.data(UserSettings(), healthAvailable = true))
        assertEquals("Kopia zapasowa, podpis: Ola", SettingsSummary.data(UserSettings(authorName = " Ola "), healthAvailable = false))
    }

    @Test
    fun `aktualizacje z dopiskiem o nowosci`() {
        assertEquals("1.1.0 · baza ćwiczeń v10", SettingsSummary.updates("1.1.0", 10))
        assertNull(SettingsSummary.updatesAccent(newVersion = null, newDatabase = null))
        assertEquals("Jest wersja 1.2.0", SettingsSummary.updatesAccent(newVersion = "1.2.0", newDatabase = 11))
        assertEquals("Jest baza ćwiczeń v11", SettingsSummary.updatesAccent(newVersion = null, newDatabase = 11))
    }

    @Test
    fun `wyglad to nazwa motywu, a nieznany id wraca do domyslnego`() {
        assertEquals("Papier i Ogień", SettingsSummary.appearance(UserSettings(themeId = "paper")))
        assertEquals("Ogień i Krew", SettingsSummary.appearance(UserSettings(themeId = "nieznany")))
    }

    @Test
    fun `wyglad dopisuje tlo, gdy nie jest jednolite`() {
        assertEquals("Ogień i Krew · Żar", SettingsSummary.appearance(UserSettings(themeId = "fire", backgrounds = mapOf("fire" to "zar"))))
        assertEquals("Ogień i Krew", SettingsSummary.appearance(UserSettings(themeId = "fire", backgrounds = mapOf("fire" to "jednolite"))))
        // Tło zapisane dla innego motywu nie zmienia streszczenia bieżącego.
        assertEquals("Stal i Bursztyn", SettingsSummary.appearance(UserSettings(themeId = "steel", backgrounds = mapOf("fire" to "zar"))))
        assertEquals(
            "Stal i Bursztyn · Zdjęcie dnia",
            SettingsSummary.appearance(UserSettings(themeId = "steel", backgrounds = mapOf("steel" to "zdjecie"))),
        )
    }

    @Test
    fun `kategorie w dwoch grupach obejmuja wszystkie sekcje raz`() {
        assertEquals(SettingsSection.entries.toList(), SettingsSection.groups.flatten())
    }
}
