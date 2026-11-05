package pl.mysttic.trainings.domain.health

import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.Workout

/** Rodzaj wysiłku, którym opisujemy sesję w Health Connect. Mapowanie na stałe Androida robi warstwa serwisu. */
enum class HealthExerciseKind { HIIT, CALISTHENICS, BREATHING }

/**
 * Co i kiedy zapisujemy w Health Connect (M6). Czysty Kotlin, żeby reguła dała się przetestować bez Androida.
 * Zapis jest jednostronny: aplikacja nic stamtąd nie czyta, a historia w bazie zostaje źródłem prawdy.
 */
object HealthExport {

    /** Krótsze niż minuta i nieukończone treningi zostawiamy sobie — w dzienniku zdrowia byłyby szumem. */
    const val MIN_DURATION_SEC = 60

    fun exportable(status: SessionStatus, startedAt: Long, endedAt: Long?): Boolean {
        if (status != SessionStatus.DONE || endedAt == null) return false
        return (endedAt - startedAt) / 1000L >= MIN_DURATION_SEC
    }

    /**
     * Obwód to trening interwałowy, serie proste to kalistenika — tyle rozróżnienia wystarczy dzienniczkowi.
     * Praktyka oddechowa (D-37) wygrywa z oboma: rundy oddechu są obwodem, ale nie wysiłkiem.
     */
    fun kind(hasCircuit: Boolean, breathing: Boolean = false): HealthExerciseKind = when {
        breathing -> HealthExerciseKind.BREATHING
        hasCircuit -> HealthExerciseKind.HIIT
        else -> HealthExerciseKind.CALISTHENICS
    }

    /** Trening oddechowy: ma pozycje i każda wskazuje ćwiczenie z kategorii oddech. Ćwiczenie spoza biblioteki psuje warunek. */
    fun isBreathing(workout: Workout?, exercises: Map<String, Exercise>): Boolean {
        val ids = workout?.blocks?.flatMap { block -> block.items.map { it.exerciseId } }.orEmpty()
        return ids.isNotEmpty() && ids.all { exercises[it]?.category == ExerciseCategory.BREATHING }
    }

    fun title(workoutName: String?): String = workoutName?.takeIf { it.isNotBlank() } ?: "Trening w domu"
}
