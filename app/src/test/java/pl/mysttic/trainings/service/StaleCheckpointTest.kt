package pl.mysttic.trainings.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleCheckpointTest {

    private val saved = 1_790_191_274_868L

    @Test
    fun `checkpoint sprzed chwili i sprzed godziny wznawia trening`() {
        assertFalse(checkpointIsStale(saved, saved + 30_000L))
        assertFalse(checkpointIsStale(saved, saved + 60 * 60 * 1000L))
        assertFalse(checkpointIsStale(saved, saved + STALE_CHECKPOINT_MS))
    }

    @Test
    fun `checkpoint sprzed doby jest odlozonym treningiem`() {
        // Przypadek z testów na telefonie: zapis wieczorem, Start następnego dnia po 22,9 h.
        assertTrue(checkpointIsStale(saved, saved + 82_496_887L))
        assertTrue(checkpointIsStale(saved, saved + STALE_CHECKPOINT_MS + 1L))
    }

    @Test
    fun `zegar cofniety w ustawieniach nie robi checkpointu starym`() {
        assertFalse(checkpointIsStale(saved, saved - 3 * 60 * 60 * 1000L))
    }
}
