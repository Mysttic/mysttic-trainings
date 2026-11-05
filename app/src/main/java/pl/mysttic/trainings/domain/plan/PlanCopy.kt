package pl.mysttic.trainings.domain.plan

import pl.mysttic.trainings.domain.model.Workout

/** Wynik kopii planu: dni i treningi pod nowym id oraz mapa stare id treningu → nowe. */
data class CopiedPlan(
    val days: List<PlanDay>,
    val workouts: List<Workout>,
    val idMap: Map<String, String>,
)

/**
 * Kopia planu (D-42): duplikat, powtórka, „Duplikuj i zmień” i import idą tędy. Każdy trening dostaje nowe id
 * z prefiksem nowego planu, więc dwa plany nigdy nie dzielą treningu ani postępu kolejki. Deterministyczna:
 * id nadawane w kolejności pierwszego wystąpienia w dniach, a po nich treningi z [copy] bez dnia (edytor planu
 * pozwala je trzymać) w kolejności (createdAt, id). Kopia nie gubi więc żadnego treningu planu.
 */
object PlanCopy {

    fun copy(
        days: List<PlanDay>,
        workouts: Map<String, Workout>,
        newProgramId: String,
        now: Long,
        transform: (Workout) -> Workout = { it },
    ): CopiedPlan {
        val idMap = LinkedHashMap<String, String>()
        days.sortedBy { it.dayIndex }.forEach { day ->
            val id = day.workoutId ?: return@forEach
            if (id !in idMap && workouts.containsKey(id)) idMap[id] = "$newProgramId-${idMap.size + 1}"
        }
        workouts.values.filter { it.id !in idMap }
            .sortedWith(compareBy<Workout>({ it.createdAt }, { it.id }))
            .forEach { idMap[it.id] = "$newProgramId-${idMap.size + 1}" }
        val copied = idMap.map { (oldId, newId) ->
            transform(workouts.getValue(oldId)).copy(id = newId, createdAt = now, updatedAt = now, isBuiltIn = false)
        }
        // Dzień wskazujący trening, którego nie ma, staje się odpoczynkiem: kolejka nie stanie na nim na zawsze.
        val newDays = days.sortedBy { it.dayIndex }.map { PlanDay(it.dayIndex, it.workoutId?.let(idMap::get)) }
        return CopiedPlan(newDays, copied, idMap)
    }

    /** Profil kopii: poza gotowym programem kopia nie jest już presetem (inaczej „Dziś” brałoby opis z katalogu). */
    fun profileFor(profile: TrainingProfile, target: PlanSource): TrainingProfile =
        if (target == PlanSource.PRESET) profile else profile.copy(presetId = null, presetParam = null)
}
