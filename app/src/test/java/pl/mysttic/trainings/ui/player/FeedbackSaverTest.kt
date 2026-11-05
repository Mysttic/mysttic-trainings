package pl.mysttic.trainings.ui.player

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pl.mysttic.trainings.domain.coach.ExerciseFeedback

/**
 * Opinie o ćwiczeniach wpisuje się tylko w podsumowaniu treningu (D-35), a zapisuje dopiero „Zakończ” —
 * obrót ekranu w tym miejscu kasował je bez śladu. Trzyma je teraz `rememberSaveable` z tym saverem,
 * więc jego kontrakt (mapa → Bundle → mapa) jest wart testu.
 */
class FeedbackSaverTest {

    /** Wszystko, co saver oddaje, ma trafić do Bundle'a — w teście przepuszczamy każdą wartość. */
    private val scope = SaverScope { true }

    @Test
    fun `opinie przezywaja obrot ekranu`() {
        val opinie = mapOf(
            "pushups" to ExerciseFeedback.MORE,
            "plank" to ExerciseFeedback.LESS,
            "superman" to ExerciseFeedback.NEVER,
            "squats" to ExerciseFeedback.NONE,
        )

        val zapisane = with(FeedbackSaver) { scope.save(opinie) }
        assertNotNull(zapisane)

        assertEquals(opinie, FeedbackSaver.restore(zapisane!!))
    }

    @Test
    fun `pusta mapa nie ma czego zapisywac`() {
        // `listSaver` oddaje null dla pustej listy, a `rememberSaveable` wraca wtedy do wartości początkowej,
        // czyli do pustej mapy. Nic nie ginie — po prostu nie ma czego wkładać do Bundle'a.
        assertNull(with(FeedbackSaver) { scope.save(emptyMap<String, ExerciseFeedback>()) })
    }

    @Test
    fun `niepelny zapis nie wywraca odtwarzania`() {
        // Gdyby Bundle wrócił przycięty, podsumowanie ma się otworzyć bez opinii, a nie wysypać.
        assertEquals(emptyMap<String, ExerciseFeedback>(), FeedbackSaver.restore(listOf("pushups")))
    }
}
