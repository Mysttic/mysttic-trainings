package pl.mysttic.trainings.domain.coach

import pl.mysttic.trainings.domain.model.TargetKind

/** Co użytkownik powiedział o ćwiczeniu w podsumowaniu treningu (D-35). */
enum class ExerciseFeedback(val labelPl: String) {
    NONE("Bez zmian"),
    MORE("Więcej"),
    LESS("Mniej"),
    NEVER("Nie chcę"),
}

/**
 * Pamięć trenera o jednym ćwiczeniu (D-35): korekta celu w jednostkach, które użytkownik widzi w playerze
 * (po skalowaniu poziomem), oraz wykluczenie z planów. Kluczem jest ćwiczenie pokazane na ekranie, więc
 * opinia o wariancie poziomu dotyczy wariantu, nie ćwiczenia bazowego z szablonu.
 */
data class ExerciseTuning(
    val exerciseId: String,
    val repsDelta: Int = 0,
    val secondsDelta: Int = 0,
    val excluded: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val isNeutral: Boolean get() = repsDelta == 0 && secondsDelta == 0 && !excluded

    /** Cel po korekcie: nigdy poniżej minimum, żeby seria nie znikła. */
    fun adjust(kind: TargetKind, value: Int): Int = when (kind) {
        TargetKind.REPS -> (value + repsDelta).coerceAtLeast(Coach.MIN_REPS)
        TargetKind.SECONDS, TargetKind.AMRAP -> (value + secondsDelta).coerceAtLeast(Coach.MIN_SECONDS)
    }
}
