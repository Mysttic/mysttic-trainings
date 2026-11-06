package pl.mysttic.trainings.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rozkład planu tygodniami w podglądzie (D-54): podział po 7 dni, sklejanie odpoczynków, tydzień rozwinięty na start. */
class PlanWeeksTest {

    private fun training(day: Int, done: Boolean = false, next: Boolean = false) =
        PlanDayRow(dayIndex = day, workoutId = "w$day", workoutName = "Trening $day", minutes = 20, isNext = next, done = done)

    private fun rest(day: Int) = PlanDayRow(dayIndex = day, workoutId = null, workoutName = null, minutes = 0, isNext = false, done = false)

    @Test
    fun `dni dziela sie po siedem jak tygodnie edytora`() {
        val days = (1..15).map { if (it % 2 == 1) training(it) else rest(it) }
        val weeks = PlanWeeks.of(days)
        assertEquals(listOf(1, 2, 3), weeks.map { it.number })
        assertEquals(4, weeks[0].trainings)
        assertEquals(1, weeks[2].trainings)
    }

    @Test
    fun `kolejne odpoczynki skleja w jeden wiersz, a trening je rozdziela`() {
        val days = listOf(training(1), rest(2), rest(3), training(4), rest(5), rest(6), rest(7))
        val entries = PlanWeeks.of(days).single().entries
        assertEquals(4, entries.size)
        assertEquals(2, (entries[1] as PlanWeekEntry.Rest).days.size)
        assertEquals(3, (entries[3] as PlanWeekEntry.Rest).days.size)
        assertTrue(entries[2] is PlanWeekEntry.Training)
    }

    @Test
    fun `kolejnosc z numerow dni, nie z listy`() {
        val weeks = PlanWeeks.of(listOf(training(9), training(1), rest(2)))
        assertEquals(listOf(1, 2), weeks.map { it.number })
        assertEquals(1, (weeks[0].entries[0] as PlanWeekEntry.Training).day.dayIndex)
    }

    @Test
    fun `na start rozwiniety jest tydzien z nastepnym treningiem`() {
        val days = listOf(training(1, done = true), rest(2)) + (3..9).map { training(it, next = it == 8) }
        val weeks = PlanWeeks.of(days)
        assertEquals(2, PlanWeeks.defaultExpanded(weeks))
        assertEquals(1, weeks[0].done)
    }

    @Test
    fun `plan zrobiony nie rozwija zadnego tygodnia`() {
        val weeks = PlanWeeks.of((1..10).map { training(it, done = true) })
        assertNull(PlanWeeks.defaultExpanded(weeks))
        assertEquals(weeks[1].trainings, weeks[1].done)
    }
}
