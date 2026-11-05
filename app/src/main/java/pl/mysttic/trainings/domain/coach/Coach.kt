package pl.mysttic.trainings.domain.coach

import pl.mysttic.trainings.domain.equipment.EquipmentMatcher
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.TargetKind
import kotlin.math.abs

/**
 * Trener (D-35): reguły, nie model. Opinia z podsumowania przesuwa cel ćwiczenia o stały krok we wszystkich
 * kolejnych seriach tego ćwiczenia, „nie chcę” wyklucza je z planów i podmienia w bieżącym cyklu.
 * Czysty Kotlin i pełna determinacja: te same opinie dają ten sam stan.
 */
object Coach {
    /** Jeden „więcej” albo „mniej” to tyle powtórzeń albo sekund; kolejne opinie się sumują. */
    const val REPS_STEP = 2
    const val SECONDS_STEP = 5

    /** Widełki korekty: dalej nie ma sensu, lepiej zmienić poziom albo ćwiczenie. */
    const val MAX_REPS_DELTA = 20
    const val MAX_SECONDS_DELTA = 60

    /** Poniżej tego cel nie schodzi, żeby seria nie zniknęła z treningu. */
    const val MIN_REPS = 1
    const val MIN_SECONDS = 5

    fun apply(current: ExerciseTuning, feedback: ExerciseFeedback, now: Long): ExerciseTuning = when (feedback) {
        ExerciseFeedback.NONE -> current
        ExerciseFeedback.MORE -> current.shifted(+1, now)
        ExerciseFeedback.LESS -> current.shifted(-1, now)
        ExerciseFeedback.NEVER -> current.copy(excluded = true, updatedAt = now)
    }

    private fun ExerciseTuning.shifted(direction: Int, now: Long): ExerciseTuning = copy(
        repsDelta = (repsDelta + direction * REPS_STEP).coerceIn(-MAX_REPS_DELTA, MAX_REPS_DELTA),
        secondsDelta = (secondsDelta + direction * SECONDS_STEP).coerceIn(-MAX_SECONDS_DELTA, MAX_SECONDS_DELTA),
        updatedAt = now,
    )

    /** Sama korekta z jednostką: „+2 powt.” albo „−5 s”; null, gdy nic nie zmienia. */
    fun deltaLabel(tuning: ExerciseTuning?, kind: TargetKind): String? {
        if (tuning == null) return null
        val delta = if (kind == TargetKind.REPS) tuning.repsDelta else tuning.secondsDelta
        if (delta == 0) return null
        val unit = if (kind == TargetKind.REPS) "powt." else "s"
        val sign = if (delta > 0) "+" else "−"
        return "$sign${abs(delta)} $unit"
    }

    /** Podpis korekty pod celem w szczegółach treningu: „trener +2 powt.”. */
    fun label(tuning: ExerciseTuning?, kind: TargetKind): String? = deltaLabel(tuning, kind)?.let { "trener $it" }

    /**
     * Zamiennik wykluczonego ćwiczenia: ta sama główna partia, posiadany sprzęt, nic z listy [taken]
     * (wykluczone i już obecne w treningu). Null, gdy nie ma czym zastąpić: wtedy pozycja znika.
     */
    fun replacement(exercise: Exercise, candidates: List<Exercise>, owned: Set<Equipment>, taken: Set<String>): Exercise? =
        EquipmentMatcher.substitutes(exercise, candidates.filter { it.id !in taken }, owned, limit = 1).firstOrNull()

    /**
     * Bloki bez wykluczonych ćwiczeń. Pozycję ocenia się tak, jak zobaczy ją player ([resolve] podmienia wariant
     * poziomu), bo opinia dotyczyła tego, co było na ekranie. Zamiennik dziedziczy serie i przerwy pozycji,
     * cel bierze z własnych wartości domyślnych. Blok bez pozycji znika. Gdy nic się nie zmienia, wraca ta sama lista.
     */
    fun withoutExcluded(
        blocks: List<Block>,
        exercises: Map<String, Exercise>,
        owned: Set<Equipment>,
        excluded: Set<String>,
        resolve: (Exercise) -> Exercise = { it },
    ): List<Block> {
        if (excluded.isEmpty()) return blocks
        val taken = HashSet<String>(excluded)
        blocks.forEach { block ->
            block.items.forEach { item ->
                taken += item.exerciseId
                exercises[item.exerciseId]?.let { taken += resolve(it).id }
            }
        }
        val candidates = exercises.values.filter { it.id !in excluded && resolve(it).id !in excluded }
        var changed = false
        val result = blocks.map { block ->
            // Stały protokół i plan od kogoś (D-37, D-42): ćwiczenia zostają takie, jak je zapisano.
            if (block.fixed) return@map block
            val items = block.items.mapNotNull { item ->
                val base = exercises[item.exerciseId] ?: return@mapNotNull item
                val shown = resolve(base)
                if (base.id !in excluded && shown.id !in excluded) return@mapNotNull item
                changed = true
                val substitute = replacement(shown, candidates, owned, taken) ?: return@mapNotNull null
                taken += substitute.id
                taken += resolve(substitute).id
                item.copy(
                    exerciseId = substitute.id,
                    targetKind = substitute.defaultTargetKind,
                    targetValue = substitute.defaultTargetValue,
                )
            }
            if (items == block.items) block else block.copy(items = items)
        }.filter { it.items.isNotEmpty() }
        return if (changed) result else blocks
    }
}
