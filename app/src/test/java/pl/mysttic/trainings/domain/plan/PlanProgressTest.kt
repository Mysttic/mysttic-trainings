package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanProgressTest {

    /** Rotacja A/B z dniami odpoczynku: 1=A, 2=B, 3=odpoczynek, 4=A, 5=B, 6=odpoczynek, 7=A. */
    private val days = listOf(
        PlanDay(1, "A"), PlanDay(2, "B"), PlanDay(3, null),
        PlanDay(4, "A"), PlanDay(5, "B"), PlanDay(6, null),
        PlanDay(7, "A"),
    )

    @Test
    fun `bez zadnego treningu nastepny jest pierwszy dzien planu`() {
        val progress = PlanProgress.resolve(days, emptyList())

        assertEquals(0, progress.doneCount)
        assertEquals(5, progress.totalCount)
        assertEquals(1, progress.next?.dayIndex)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `ukonczony trening odhacza pierwszy dzien z tym treningiem`() {
        val progress = PlanProgress.resolve(days, listOf("A"))

        assertEquals(setOf(1), progress.doneDayIndexes)
        assertEquals(2, progress.next?.dayIndex)
        assertEquals(4, progress.remaining)
    }

    @Test
    fun `powtorzony trening odhacza kolejne wystapienie w rotacji`() {
        val progress = PlanProgress.resolve(days, listOf("A", "A"))

        assertEquals(setOf(1, 4), progress.doneDayIndexes)
        assertEquals(2, progress.next?.dayIndex)
    }

    @Test
    fun `pominiety dzien nie przepada, tylko czeka w kolejce`() {
        // użytkownik zrobił B, choć w kolejce był A: A dalej czeka jako następny
        val progress = PlanProgress.resolve(days, listOf("B"))

        assertEquals(setOf(2), progress.doneDayIndexes)
        assertEquals(1, progress.next?.dayIndex)
        assertEquals(1, progress.doneCount)
    }

    @Test
    fun `dni odpoczynku nie licza sie do postepu`() {
        val progress = PlanProgress.resolve(days, listOf("A", "B", "A", "B", "A"))

        assertEquals(5, progress.doneCount)
        assertEquals(5, progress.totalCount)
        assertTrue(progress.isComplete)
        assertNull(progress.next)
        assertFalse(3 in progress.doneDayIndexes)
    }

    @Test
    fun `sesje treningu spoza planu nic nie odhaczaja`() {
        val progress = PlanProgress.resolve(days, listOf("full-body-a", "C"))

        assertEquals(0, progress.doneCount)
        assertEquals(1, progress.next?.dayIndex)
    }

    @Test
    fun `nadmiarowe sesje nie psuja licznika`() {
        val progress = PlanProgress.resolve(days, listOf("A", "A", "A", "A", "A"))

        assertEquals(setOf(1, 4, 7), progress.doneDayIndexes)
        assertEquals(3, progress.doneCount)
        assertEquals(2, progress.next?.dayIndex)
    }

    @Test
    fun `pusty plan jest neutralny`() {
        val progress = PlanProgress.resolve(emptyList(), listOf("A"))

        assertEquals(0, progress.totalCount)
        assertFalse(progress.isComplete)
        assertNull(progress.next)
    }
}
