package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanNotesTest {

    @Test
    fun `notatki do pliku sa oczyszczone, bez pustych, tylko dla podanych cwiczen, po kolei`() {
        val notes = mapOf(
            "squats" to " 40 kg ",
            "pushups" to "kolana na macie\u202E",
            "plank" to "   ",
            "burpees" to "spoza planu",
        )

        val out = PlanNotes.forExercises(notes, setOf("squats", "pushups", "plank"))

        assertEquals(listOf("pushups" to "kolana na macie", "squats" to "40 kg"), out.toList())
    }

    @Test
    fun `notatka jest przycieta do limitu`() {
        assertEquals(PlanNotes.MAX_NOTE, PlanNotes.clean("x".repeat(PlanNotes.MAX_NOTE + 50)).length)
    }
}
