package pl.mysttic.trainings.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RpeTest {

    @Test
    fun `skala ma piec stopni od 6 do 10, kazdy z innym podpisem`() {
        assertEquals(listOf(6, 7, 8, 9, 10), Rpe.RANGE.toList())
        val labels = Rpe.RANGE.map { Rpe.label(it) }
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `ocena spoza skali z dawnego suwaka nie wywraca podpisu`() {
        assertEquals("", Rpe.label(3))
        assertEquals("", Rpe.label(0))
        assertEquals("", Rpe.label(99))
    }
}
