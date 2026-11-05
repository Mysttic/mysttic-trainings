package pl.mysttic.trainings.domain.plan

/** Stan kolejki planu: co zrobione, co następne. */
data class ProgramProgress(
    val doneDayIndexes: Set<Int>,
    val next: PlanDay?,
    val doneCount: Int,
    val totalCount: Int,
) {
    val isComplete: Boolean get() = totalCount > 0 && doneCount >= totalCount
    val remaining: Int get() = (totalCount - doneCount).coerceAtLeast(0)
}

/**
 * Rozliczanie planu jako kolejki (D-21), nie kalendarza: pominięty dzień nic nie psuje,
 * a niezrobiony trening czeka na swoją kolej.
 *
 * Sesje dopasowujemy po identyfikatorze treningu i po kolei: w rotacji ten sam trening
 * wraca kilka razy, więc N ukończonych sesji „Góra A” odhacza N pierwszych dni z „Górą A”.
 */
object PlanProgress {

    /**
     * Która ukończona sesja odhaczyła który dzień — ta sama zachłanna reguła co [resolve], ale z zapamiętaniem
     * pozycji sesji na liście (do raportu planu: data, czas, RPE dnia). Wynik: numer dnia → indeks w `completedWorkoutIds`.
     */
    fun assign(days: List<PlanDay>, completedWorkoutIds: List<String>): Map<Int, Int> {
        val queues = HashMap<String, ArrayDeque<Int>>()
        completedWorkoutIds.forEachIndexed { index, id -> queues.getOrPut(id) { ArrayDeque() }.addLast(index) }
        val result = LinkedHashMap<Int, Int>()
        for (day in days.filterNot { it.isRest }) {
            val queue = day.workoutId?.let { queues[it] } ?: continue
            queue.removeFirstOrNull()?.let { result[day.dayIndex] = it }
        }
        return result
    }

    /** `completedWorkoutIds` — identyfikatory treningów z ukończonych sesji po starcie cyklu, w kolejności wykonania. */
    fun resolve(days: List<PlanDay>, completedWorkoutIds: List<String>): ProgramProgress {
        val remaining = completedWorkoutIds.groupingBy { it }.eachCount().toMutableMap()
        val done = LinkedHashSet<Int>()
        val trainingDays = days.filterNot { it.isRest }
        for (day in trainingDays) {
            val workoutId = day.workoutId ?: continue
            val left = remaining[workoutId] ?: 0
            if (left > 0) {
                remaining[workoutId] = left - 1
                done += day.dayIndex
            }
        }
        return ProgramProgress(
            doneDayIndexes = done,
            next = trainingDays.firstOrNull { it.dayIndex !in done },
            doneCount = done.size,
            totalCount = trainingDays.size,
        )
    }
}
