package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.Difficulty

class TrainingProfileTest {

    @Test
    fun `dlugosc treningu ma gorna granice zalezna od poziomu`() {
        assertEquals(15..45, TrainingProfile.minutesRange(Difficulty.BEGINNER))
        assertEquals(15..60, TrainingProfile.minutesRange(Difficulty.INTERMEDIATE))
        assertEquals(15..75, TrainingProfile.minutesRange(Difficulty.ADVANCED))

        val long = TrainingProfile(sessionMinutes = 75)
        assertEquals(45, TrainingProfile.sanitize(long.copy(level = Difficulty.BEGINNER)).sessionMinutes)
        assertEquals(60, TrainingProfile.sanitize(long.copy(level = Difficulty.INTERMEDIATE)).sessionMinutes)
        assertEquals(75, TrainingProfile.sanitize(long.copy(level = Difficulty.ADVANCED)).sessionMinutes)
        assertEquals(15, TrainingProfile.sanitize(long.copy(sessionMinutes = 5)).sessionMinutes)
    }

    @Test
    fun `zmiana poziomu na nizszy przycina wybrane wczesniej minuty`() {
        val advanced = TrainingProfile.sanitize(TrainingProfile(level = Difficulty.ADVANCED, sessionMinutes = 70))
        assertEquals(70, advanced.sessionMinutes)

        val beginner = TrainingProfile.sanitize(advanced.copy(level = Difficulty.BEGINNER))
        assertEquals(45, beginner.sessionMinutes)
    }

    @Test
    fun `sanitize przycina wzrost i wage do rozsadnych zakresow`() {
        val clean = TrainingProfile.sanitize(TrainingProfile(heightCm = 90, weightKg = 400.0))
        assertEquals(120, clean.heightCm)
        assertEquals(250.0, clean.weightKg, 0.0)
    }

    @Test
    fun `bmi decyduje o unikaniu skokow`() {
        assertFalse(TrainingProfile(heightCm = 180, weightKg = 78.0).avoidJumping)
        assertTrue(TrainingProfile(heightCm = 170, weightKg = 100.0).avoidJumping)
        assertEquals(24.1, TrainingProfile(heightCm = 180, weightKg = 78.0).bmiRounded, 0.0)
    }
}
