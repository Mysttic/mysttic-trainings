package pl.mysttic.trainings.ui.settings

import pl.mysttic.trainings.domain.model.Gender
import pl.mysttic.trainings.ui.common.formatKg

import pl.mysttic.trainings.data.settings.UserSettings
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.reminder.ReminderSchedule
import pl.mysttic.trainings.ui.theme.AppBackground
import pl.mysttic.trainings.ui.theme.AppTheme

/**
 * Kategorie Ustawień (D-52): zamiast jednej długiej strony wiersze, a w każdym streszczenie tego, co jest w środku.
 * Od D-57 pierwsza jest „O mnie”. Kolejność to kolejność na ekranie, podział na grupy: [groups].
 */
enum class SettingsSection(val title: String) {
    ABOUT_ME("O mnie"),
    TRAINING("Trening"),
    EQUIPMENT("Mój sprzęt"),
    REMINDER("Przypomnienie"),
    APPEARANCE("Wygląd"),
    DATA("Dane i kopia"),
    UPDATES("Aktualizacje"),
    ;

    companion object {
        /** Najpierw ty, potem to, jak aplikacja prowadzi trening, na końcu to, jak działa sama aplikacja. */
        val groups: List<List<SettingsSection>> = listOf(
            listOf(ABOUT_ME),
            listOf(TRAINING, EQUIPMENT, REMINDER),
            listOf(APPEARANCE, DATA, UPDATES),
        )
    }
}

/**
 * Streszczenia kategorii — czysty Kotlin, żeby test pilnował polszczyzny i skrótów bez Androida.
 * Streszczenie mówi, co jest ustawione, nie co da się ustawić.
 */
object SettingsSummary {

    /** „Marta · kobieta · 168 cm · 62 kg”; bez niczego wpisanego mówi, co da się tu podać. */
    fun aboutMe(settings: UserSettings): String {
        val parts = listOfNotNull(
            settings.authorName.trim().takeIf { it.isNotEmpty() },
            Gender.fromId(settings.gender).takeIf { it != Gender.UNSPECIFIED }?.labelPl?.lowercase(),
            settings.heightCm?.let { "$it cm" },
            settings.weightKg?.let { formatKg(it) },
        )
        return if (parts.isEmpty()) "Imię, płeć, wzrost i waga" else parts.joinToString(" · ")
    }

    fun training(settings: UserSettings): String = listOf(
        "Poziom ${settings.difficulty.level}",
        if (settings.countdownSec == 0) "bez odliczania" else "odliczanie ${settings.countdownSec} s",
        signals(settings),
    ).joinToString(" · ")

    /** „dźwięk i głos”, „dźwięk, głos i wibracja”, „bez sygnałów”. */
    fun signals(settings: UserSettings): String {
        val on = listOfNotNull(
            "dźwięk".takeIf { settings.soundEnabled },
            "głos".takeIf { settings.ttsEnabled },
            "wibracja".takeIf { settings.vibrationEnabled },
        )
        return when (on.size) {
            0 -> "bez sygnałów"
            1 -> on.single()
            else -> on.dropLast(1).joinToString(", ") + " i " + on.last()
        }
    }

    /** Do trzech pozycji w kolejności z listy sprzętu, reszta jako „i jeszcze n”. */
    fun equipment(owned: Set<Equipment>): String {
        if (owned.isEmpty()) return "Bez sprzętu"
        val names = owned.sortedBy { it.ordinal }.map { it.shortPl.lowercase() }
        val shown = names.take(EQUIPMENT_SHOWN).joinToString(", ")
        val rest = names.size - EQUIPMENT_SHOWN
        return (if (rest > 0) "$shown i jeszcze $rest" else shown).replaceFirstChar { it.uppercase() }
    }

    fun reminder(settings: UserSettings): String =
        if (settings.reminderEnabled) {
            "Codziennie o ${ReminderSchedule.formatTime(settings.reminderHour, settings.reminderMinute)}"
        } else {
            "Wyłączone"
        }

    /** Motyw, a gdy tło nie jest jednolite (D-50, D-51) — także tło: „Ogień i Krew · Żar”. */
    fun appearance(settings: UserSettings): String {
        val theme = AppTheme.fromId(settings.themeId)
        val background = AppBackground.resolve(theme, settings.backgrounds[theme.id])
        return if (background == AppBackground.of(theme).first()) theme.labelPl else "${theme.labelPl} · ${background.labelPl}"
    }

    fun data(healthAvailable: Boolean): String = listOfNotNull(
        "Kopia zapasowa",
        "Health Connect".takeIf { healthAvailable },
    ).joinToString(", ")

    fun updates(versionName: String, seedVersion: Int): String = "$versionName · baza ćwiczeń v$seedVersion"

    /** Dopisek w żarze, gdy jest coś nowego do pobrania; null = nic. */
    fun updatesAccent(newVersion: String?, newDatabase: Int?): String? = when {
        newVersion != null -> "Jest wersja $newVersion"
        newDatabase != null -> "Jest baza ćwiczeń v$newDatabase"
        else -> null
    }

    private const val EQUIPMENT_SHOWN = 3
}
