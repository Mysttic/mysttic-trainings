package pl.mysttic.trainings.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.plan.preset.ProgramKind
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderScheduleTest {

    private val today = LocalDate.of(2026, 9, 6)

    @Test
    fun `godzina jeszcze przed nami wypada dzis`() {
        val now = LocalDateTime.of(2026, 9, 6, 9, 30)

        assertEquals(LocalDateTime.of(2026, 9, 6, 18, 0), ReminderSchedule.next(now, 18, 0))
    }

    @Test
    fun `godzina, ktora minela, wypada jutro`() {
        val now = LocalDateTime.of(2026, 9, 6, 18, 1)

        assertEquals(LocalDateTime.of(2026, 9, 7, 18, 0), ReminderSchedule.next(now, 18, 0))
    }

    @Test
    fun `termin rowny teraz przechodzi na jutro, zeby alarm nie odpalil sie w kolko`() {
        val now = LocalDateTime.of(2026, 9, 6, 18, 0)

        assertEquals(LocalDateTime.of(2026, 9, 7, 18, 0), ReminderSchedule.next(now, 18, 0))
    }

    @Test
    fun `bledna godzina jest przycinana zamiast wysadzac`() {
        val now = LocalDateTime.of(2026, 9, 6, 12, 0)

        assertEquals(LocalDateTime.of(2026, 9, 6, 23, 59), ReminderSchedule.next(now, 30, 90))
    }

    @Test
    fun `nie przypominamy po dzisiejszym treningu ani bez planu`() {
        assertTrue(ReminderSchedule.shouldRemind(today, lastDoneDate = today.minusDays(1), hasNextWorkout = true))
        assertTrue(ReminderSchedule.shouldRemind(today, lastDoneDate = null, hasNextWorkout = true))
        assertFalse(ReminderSchedule.shouldRemind(today, lastDoneDate = today, hasNextWorkout = true))
        assertFalse(ReminderSchedule.shouldRemind(today, lastDoneDate = null, hasNextWorkout = false))
    }

    @Test
    fun `tresc powiadomienia zalezy od tego, co wiemy o treningu`() {
        assertEquals("Góra A · ok. 45 min", ReminderSchedule.message("Góra A", 45))
        assertEquals("Góra A", ReminderSchedule.message("Góra A", 0))
        assertEquals("Twój plan czeka", ReminderSchedule.message(null, 45))
        assertEquals("Twój plan czeka", ReminderSchedule.message("  ", 45))
    }

    @Test
    fun `godzina formatuje sie z zerem wiodacym`() {
        assertEquals("07:05", ReminderSchedule.formatTime(7, 5))
        assertEquals("18:00", ReminderSchedule.formatTime(18, 0))
    }

    @Test
    fun `tytul przypomnienia zalezy od rodzaju cyklu`() {
        assertEquals(ReminderSchedule.TITLE, ReminderSchedule.title(ProgramKind.TRAINING))
        assertEquals("Czas na trening", ReminderSchedule.title(ProgramKind.TRAINING))
        assertEquals("Czas na oddech", ReminderSchedule.title(ProgramKind.BREATHING))
    }
}
