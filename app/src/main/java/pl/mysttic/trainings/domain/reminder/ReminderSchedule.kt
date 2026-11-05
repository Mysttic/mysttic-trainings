package pl.mysttic.trainings.domain.reminder

import pl.mysttic.trainings.domain.plan.preset.ProgramKind
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Kiedy przypomnieć o treningu i co napisać (M6). Czysty Kotlin — warstwa Androida (alarm, powiadomienie)
 * tylko wykonuje decyzje podjęte tutaj.
 *
 * Przypomnienie jest dzienne, ale plan rozliczamy kolejką (D-21), więc nie pytamy „czy dziś jest dzień
 * treningowy”, tylko „czy dziś już trenowałeś i czy kolejka ma co podać”.
 */
object ReminderSchedule {

    /** Najbliższy termin przypomnienia: dziś o tej godzinie, jeśli jeszcze nie minęła, inaczej jutro. */
    fun next(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /**
     * Czy pokazać powiadomienie. Nie zawracamy głowy, gdy dziś była już ukończona sesja
     * albo gdy nie ma czego proponować (brak planu lub cykl domknięty).
     */
    fun shouldRemind(today: LocalDate, lastDoneDate: LocalDate?, hasNextWorkout: Boolean): Boolean =
        hasNextWorkout && lastDoneDate != today

    /** Treść powiadomienia: nazwa treningu z kolejki i szacowany czas, jeśli są znane. */
    fun message(workoutName: String?, minutes: Int): String = when {
        workoutName.isNullOrBlank() -> "Twój plan czeka"
        minutes > 0 -> "$workoutName · ok. $minutes min"
        else -> workoutName
    }

    /** Godzina w formacie HH:MM do pokazania w ustawieniach. */
    fun formatTime(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    /** Tytuł powiadomienia zależy od rodzaju cyklu (D-37): praktykę oddechową zapowiadamy inaczej niż trening. */
    fun title(kind: ProgramKind): String = when (kind) {
        ProgramKind.TRAINING -> TITLE
        ProgramKind.BREATHING -> "Czas na oddech"
    }

    const val TITLE = "Czas na trening"
}
