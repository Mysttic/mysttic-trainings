package pl.mysttic.trainings.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.mysttic.trainings.domain.model.TargetKind

/** Komunikaty TTS dla gotowych programów (D-37): krok bez limitu, rytm oddechu, numer rundy. */
class AnnouncerTest {

    private fun work(
        kind: TargetKind = TargetKind.SECONDS,
        value: Int = 20,
        name: String = "Pajacyki",
        openEnded: Boolean = false,
        cadenceSec: Int? = null,
        round: Int = 1,
        roundsInBlock: Int = 1,
    ) = Step.Work(
        blockIndex = 0, itemIndex = 0, round = round, setIndex = 1, setsInItem = 1,
        exerciseId = name.lowercase(), exerciseName = name, targetKind = kind, targetValue = value,
        roundsInBlock = roundsInBlock, openEnded = openEnded, cadenceSec = cadenceSec,
    )

    @Test
    fun `krok bez limitu nie ma liczby`() {
        val step = work(value = 90, name = "Zatrzymanie oddechu", openEnded = true)
        assertEquals("jak najdłużej", Announcer.target(step))
        assertEquals("Zatrzymanie oddechu, jak najdłużej", Announcer.start(step))
        assertEquals("Następnie: Zatrzymanie oddechu, jak najdłużej", Announcer.next(step))
    }

    @Test
    fun `krok z rytmem liczy oddechy z polska odmiana`() {
        assertEquals("30 oddechów w rytmie", Announcer.target(work(value = 90, cadenceSec = 3)))
        assertEquals("22 oddechy w rytmie", Announcer.target(work(value = 66, cadenceSec = 3)))
        assertEquals("2 oddechy w rytmie", Announcer.target(work(value = 6, cadenceSec = 3)))
        assertEquals("1 oddech w rytmie", Announcer.target(work(value = 3, cadenceSec = 3)))
        // rytm dłuższy niż krok: ani jednego pełnego oddechu, więc zostają sekundy
        assertEquals("2 sekundy", Announcer.target(work(value = 2, cadenceSec = 3)))
    }

    @Test
    fun `start w obwodzie dodaje numer rundy, serie proste nie`() {
        assertEquals("Pajacyki, 20 sekund, runda 3 z 8", Announcer.start(work(round = 3, roundsInBlock = 8)))
        assertEquals("Pajacyki, 20 sekund", Announcer.start(work()))
        assertEquals("Pompki, 12 powtórzeń", Announcer.start(work(kind = TargetKind.REPS, value = 12, name = "Pompki")))
        assertEquals(
            "Oddech mocy, 30 oddechów w rytmie, runda 2 z 3",
            Announcer.start(work(value = 90, name = "Oddech mocy", cadenceSec = 3, round = 2, roundsInBlock = 3)),
        )
    }

    @Test
    fun `zapowiedz na przerwie nie powtarza numeru rundy`() {
        assertEquals("Następnie: Pajacyki, 20 sekund", Announcer.next(work(round = 3, roundsInBlock = 8)))
    }

    @Test
    fun `runda na przerwie miedzy rundami`() {
        assertEquals("Runda 2 z 6", Announcer.round(work(round = 2, roundsInBlock = 6)))
    }
}
