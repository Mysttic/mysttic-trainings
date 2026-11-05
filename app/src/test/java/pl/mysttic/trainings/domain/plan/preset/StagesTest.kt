package pl.mysttic.trainings.domain.plan.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StagesTest {

    @Test
    fun `ramp idzie liniowo od poczatku do konca`() {
        assertEquals(listOf(1, 2, 3), (0 until 3).map { Stages.ramp(1, 3, it, 3) })
        assertEquals(listOf(1, 1, 2, 2, 3, 3), (0 until 6).map { Stages.ramp(1, 3, it, 6) })
        assertEquals(listOf(10, 15, 20, 25, 30, 35, 40, 45), (0 until 8).map { Stages.ramp(10, 45, it, 8) })
        assertEquals(3, Stages.ramp(1, 3, 0, 1)) // jeden etap = wartość końcowa
        assertEquals(50, Stages.ramp5(20, 90, 3, 8))
        assertEquals(45, Stages.roundTo5(43))
        assertEquals(40, Stages.roundTo5(42))
    }

    @Test
    fun `sesje dziela sie rowno miedzy etapy, ostatni etap konczy cykl`() {
        assertEquals(0, Stages.stageIndex(1, 9, 3))
        assertEquals(0, Stages.stageIndex(3, 9, 3))
        assertEquals(1, Stages.stageIndex(4, 9, 3))
        assertEquals(2, Stages.stageIndex(9, 9, 3))
        assertEquals(0, Stages.stageIndex(1, 11, 8))
        assertEquals(7, Stages.stageIndex(11, 11, 8))
        // etap na sesję
        assertEquals(listOf(0, 1, 2, 3), (1..4).map { Stages.stageIndex(it, 4, 4) })
        // poza zakresem nie wybucha
        assertEquals(0, Stages.stageIndex(0, 4, 4))
        assertEquals(3, Stages.stageIndex(99, 4, 4))
        assertEquals(0, Stages.stageIndex(1, 0, 4))
    }

    @Test
    fun `tygodnie licza sie z sesji na tydzien`() {
        assertEquals(3, Stages.weeks(9, 3))
        assertEquals(4, Stages.weeks(10, 3))
        assertEquals(1, Stages.weeks(0, 3))
    }

    @Test
    fun `rytm dni`() {
        val weekly = DayPattern.Weekly(setOf(0, 2, 4))
        assertTrue(weekly.isTrainingDay(1))
        assertFalse(weekly.isTrainingDay(2))
        assertTrue(weekly.isTrainingDay(8))
        assertEquals(3, weekly.daysPerWeek)

        val every4 = DayPattern.RestEvery(4)
        assertTrue(every4.isTrainingDay(3))
        assertFalse(every4.isTrainingDay(4))
        assertFalse(every4.isTrainingDay(8))
        assertEquals(6, every4.daysPerWeek)

        assertTrue((1..30).all { DayPattern.Daily.isTrainingDay(it) })
        assertEquals(7, DayPattern.Daily.daysPerWeek)
    }
}
