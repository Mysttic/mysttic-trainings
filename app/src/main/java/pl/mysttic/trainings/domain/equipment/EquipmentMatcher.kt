package pl.mysttic.trainings.domain.equipment

import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.Workout
import kotlin.math.abs

/**
 * Dopasowanie ćwiczeń do sprzętu, który użytkownik ma w domu.
 * Kluczowa funkcja aplikacji: użytkownik zaznacza sprzęt w ustawieniach,
 * biblioteka (`domain/library/LibraryFilter`) i treningi są filtrowane, a brakujący sprzęt dostaje propozycję zamiennika.
 */
object EquipmentMatcher {

    /** Brakujący sprzęt dla każdego ćwiczenia w treningu (tylko te, którym czegoś brakuje). */
    fun missingInWorkout(workout: Workout, exercises: Map<String, Exercise>, owned: Set<Equipment>): Map<String, Set<Equipment>> =
        workout.exerciseIds
            .mapNotNull { id -> exercises[id]?.let { ex -> id to ex.missingEquipment(owned) } }
            .filter { it.second.isNotEmpty() }
            .toMap()

    /**
     * Zamienniki dla ćwiczenia, które wymaga sprzętu spoza posiadanego:
     * ta sama główna grupa mięśniowa, wykonalne z posiadanym sprzętem, posortowane od najbliższego
     * (ten sam rodzaj REPS/TIME, ta sama kategoria, najbliższy poziom, bez sprzętu przed sprzętowymi).
     */
    fun substitutes(exercise: Exercise, candidates: List<Exercise>, owned: Set<Equipment>, limit: Int = 3): List<Exercise> {
        val primary = exercise.primaryMuscles.firstOrNull() ?: return emptyList()
        return candidates
            .asSequence()
            .filter { it.id != exercise.id && it.canDoWith(owned) }
            // Wycofane z bazy (D-47) nie wraca jako zamiennik: ani przy brakującym sprzęcie, ani przy „Nie chcę”.
            .filter { !it.isRetired }
            // Ćwiczenie oddechowe nie zastąpi ruchu (D-37).
            .filter { it.category != ExerciseCategory.BREATHING }
            // Ergometr zastąpi tylko inny ergometr (D-44): plan z kreatora nie dostanie maszyny przez „Nie chcę”.
            .filter { !it.usesErgometer || exercise.usesErgometer }
            .filter { primary in it.primaryMuscles }
            .sortedWith(
                compareBy<Exercise> { if (it.kind == exercise.kind) 0 else 1 }
                    .thenBy { if (it.category == exercise.category) 0 else 1 }
                    // Ćwiczenie z biblioteki (z ilustracją i sprawdzonymi celami) przed własnym albo z pliku planu (C2-5).
                    .thenBy { if (it.isCustom) 1 else 0 }
                    .thenBy { abs(it.level.level - exercise.level.level) }
                    .thenBy { it.equipment.size }
                    .thenBy { it.namePl },
            )
            .take(limit)
            .toList()
    }
}
