package pl.mysttic.trainings.domain.engine

import pl.mysttic.trainings.domain.model.Difficulty
import kotlin.math.max
import kotlin.math.roundToInt

data class DifficultyFactors(val reps: Double, val seconds: Double, val rest: Double)

/**
 * Jak poziom trudności skaluje cele. Szablony treningów są pisane dla INTERMEDIATE (mnożnik 1.0).
 * Jedno miejsce do strojenia — patrz docs/DECISIONS.md.
 */
object DifficultyProfile {
    fun factors(difficulty: Difficulty): DifficultyFactors = when (difficulty) {
        Difficulty.BEGINNER -> DifficultyFactors(reps = 0.7, seconds = 0.7, rest = 1.3)
        Difficulty.INTERMEDIATE -> DifficultyFactors(reps = 1.0, seconds = 1.0, rest = 1.0)
        Difficulty.ADVANCED -> DifficultyFactors(reps = 1.3, seconds = 1.3, rest = 0.8)
    }

    fun scaleReps(base: Int, difficulty: Difficulty): Int =
        max(1, (base * factors(difficulty).reps).roundToInt())

    /** Sekundy pracy zaokrąglane do 5 s, minimum 5 s. */
    fun scaleSeconds(base: Int, difficulty: Difficulty): Int =
        max(5, roundTo5(base * factors(difficulty).seconds))

    /** Przerwa zaokrąglana do 5 s; 0 zostaje 0 (brak przerwy). */
    fun scaleRest(base: Int, difficulty: Difficulty): Int =
        if (base <= 0) 0 else max(5, roundTo5(base * factors(difficulty).rest))

    private fun roundTo5(value: Double): Int = ((value / 5.0).roundToInt() * 5)
}
