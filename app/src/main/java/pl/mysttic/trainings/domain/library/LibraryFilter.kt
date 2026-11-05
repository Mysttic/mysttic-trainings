package pl.mysttic.trainings.domain.library

import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.MuscleGroup

/**
 * Filtr biblioteki ćwiczeń. Wymiary łączy „i”, wartości w jednym wymiarze „lub”: „Kardio” z „Hantle” daje kardio
 * z hantlami, a „Bez sprzętu” z „Hantle” wszystko, co da się zrobić samymi hantlami. `onlyOwned` to osobny wymiar
 * wykonalności: ćwiczenie musi w całości mieścić się w posiadanym sprzęcie, jak `Exercise.canDoWith`.
 * Szukanie po nazwie (PL albo EN, bez wielkości liter) też jest wymiarem, ale nie wchodzi do [selectedCount].
 */
data class LibraryFilter(
    val query: String = "",
    val onlyOwned: Boolean = false,
    val noEquipment: Boolean = false,
    val equipment: Set<Equipment> = emptySet(),
    val categories: Set<ExerciseCategory> = emptySet(),
    val levels: Set<Difficulty> = emptySet(),
    val muscles: Set<MuscleGroup> = emptySet(),
) {
    /** Ile wartości jest zaznaczonych w panelu: bez „Mój sprzęt” i szukania, bo te widać poza panelem. */
    val selectedCount: Int
        get() = (if (noEquipment) 1 else 0) + equipment.size + categories.size + levels.size + muscles.size

    /** Czy jest co czyścić (szukanie nie wchodzi: pole je pokazuje i ma własne kasowanie). */
    val hasSelection: Boolean get() = onlyOwned || selectedCount > 0

    fun matches(exercise: Exercise, owned: Set<Equipment>): Boolean =
        (!onlyOwned || exercise.canDoWith(owned)) &&
            matchesEquipment(exercise) &&
            (categories.isEmpty() || exercise.category in categories) &&
            (levels.isEmpty() || exercise.level in levels) &&
            (muscles.isEmpty() || exercise.primaryMuscles.any { it in muscles }) &&
            matchesQuery(exercise)

    fun apply(exercises: List<Exercise>, owned: Set<Equipment>): List<Exercise> = exercises.filter { matches(it, owned) }

    /** Zeruje wszystkie wymiary, zostawia tekst szukania. */
    fun cleared(): LibraryFilter = LibraryFilter(query = query)

    fun toggleEquipment(item: Equipment): LibraryFilter = copy(equipment = equipment.toggled(item))

    fun toggleCategory(category: ExerciseCategory): LibraryFilter = copy(categories = categories.toggled(category))

    fun toggleLevel(level: Difficulty): LibraryFilter = copy(levels = levels.toggled(level))

    fun toggleMuscle(muscle: MuscleGroup): LibraryFilter = copy(muscles = muscles.toggled(muscle))

    private fun matchesEquipment(exercise: Exercise): Boolean {
        if (!noEquipment && equipment.isEmpty()) return true
        return (noEquipment && !exercise.requiresEquipment) || exercise.equipment.any { it in equipment }
    }

    private fun matchesQuery(exercise: Exercise): Boolean {
        val needle = query.trim().lowercase()
        return needle.isEmpty() || exercise.namePl.lowercase().contains(needle) || exercise.nameEn.lowercase().contains(needle)
    }

    companion object {
        /**
         * Ćwiczenia, które biblioteka w ogóle pokazuje: bez wycofanych z bazy (D-47). Wycofane dalej otwierają się
         * z historii i planów, ale nie ma ich na półce, w chipach ani w liczniku.
         */
        fun visible(exercises: List<Exercise>): List<Exercise> = exercises.filterNot { it.isRetired }
    }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/**
 * Wartości, które mają sens jako chipy: tylko te, które występują w bibliotece (sztanga bez ćwiczeń chipa nie dostaje).
 * Kolejność z definicji enumów, żeby panel wyglądał tak samo niezależnie od kolejności ćwiczeń.
 */
data class LibraryFacets(
    val equipment: List<Equipment> = emptyList(),
    val categories: List<ExerciseCategory> = emptyList(),
    val levels: List<Difficulty> = emptyList(),
    val muscles: List<MuscleGroup> = emptyList(),
) {
    companion object {
        fun of(exercises: List<Exercise>): LibraryFacets {
            val equipment = exercises.flatMapTo(mutableSetOf()) { it.equipment }
            val categories = exercises.mapTo(mutableSetOf()) { it.category }
            val levels = exercises.mapTo(mutableSetOf()) { it.level }
            val muscles = exercises.flatMapTo(mutableSetOf()) { it.primaryMuscles }
            return LibraryFacets(
                equipment = Equipment.entries.filter { it in equipment },
                categories = ExerciseCategory.entries.filter { it in categories },
                levels = Difficulty.entries.filter { it in levels },
                muscles = MuscleGroup.entries.filter { it in muscles },
            )
        }
    }
}
