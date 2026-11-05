package pl.mysttic.trainings.domain.plan

import kotlinx.serialization.Serializable

/**
 * Dzień w edytorze planu. `key` jest stały przez całą edycję (przesunięcia nie zmieniają tożsamości dnia),
 * dzięki czemu da się sprawdzić, czy zrobione dni zostały na swoim miejscu.
 */
@Serializable
data class DraftDay(val key: String, val workoutId: String?) {
    val isRest: Boolean get() = workoutId == null
}

/**
 * Operacje edytora planu (D-42). Czysty Kotlin na `List<DraftDay>`, jak `WorkoutEditing` na blokach:
 * każda funkcja zwraca nową listę, indeks spoza zakresu zostawia listę bez zmian. Numery dni powstają
 * dopiero przy zapisie (`toPlanDays`), gęsto od 1.
 */
object PlanEditing {

    const val MAX_DAYS = TrainingProfile.MAX_PLAN_DAYS

    /** Dni istniejącego planu jako szkic; klucz z numeru dnia, żeby porównać „przed” i „po”. */
    fun fromPlan(days: List<PlanDay>): List<DraftDay> =
        days.sortedBy { it.dayIndex }.map { DraftDay(keyOf(it.dayIndex), it.workoutId) }

    fun keyOf(dayIndex: Int): String = "d$dayIndex"

    /** Numer dnia sprzed edycji zapisany w kluczu z [keyOf]; null dla dni dodanych w edytorze. */
    fun dayIndexOf(key: String): Int? = if (key.startsWith("d")) key.drop(1).toIntOrNull() else null

    /** Czy któryś z zablokowanych dni stoi na innym numerze niż przed edycją. */
    fun lockedMoved(days: List<DraftDay>, lockedKeys: Set<String>): Boolean =
        lockedKeys.any { key ->
            val index = dayIndexOf(key) ?: return@any false
            days.getOrNull(index - 1)?.key != key
        }

    /** Nowy klucz, którego nie ma w szkicu. */
    fun newKey(days: List<DraftDay>): String {
        var n = days.size + 1
        while (days.any { it.key == "n$n" }) n++
        return "n$n"
    }

    fun addDay(days: List<DraftDay>, workoutId: String?): List<DraftDay> =
        if (days.size >= MAX_DAYS) days else days + DraftDay(newKey(days), workoutId)

    fun removeDay(days: List<DraftDay>, index: Int): List<DraftDay> =
        if (index !in days.indices) days else days.filterIndexed { i, _ -> i != index }

    fun moveDay(days: List<DraftDay>, from: Int, to: Int): List<DraftDay> {
        if (from !in days.indices || to !in days.indices || from == to) return days
        return days.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun duplicateDay(days: List<DraftDay>, index: Int): List<DraftDay> {
        if (index !in days.indices || days.size >= MAX_DAYS) return days
        return days.toMutableList().apply { add(index + 1, DraftDay(newKey(days), days[index].workoutId)) }
    }

    fun setWorkout(days: List<DraftDay>, index: Int, workoutId: String?): List<DraftDay> =
        if (index !in days.indices) days else days.mapIndexed { i, d -> if (i == index) d.copy(workoutId = workoutId) else d }

    /**
     * Kolejny tydzień jak ostatni: kopia ostatnich 7 dni (albo wszystkich, gdy jest ich mniej) na koniec.
     * Tak trener rozpisuje mezocykl — ten sam tydzień, potem zmiany w liczbach.
     */
    fun appendWeekCopy(days: List<DraftDay>): List<DraftDay> {
        if (days.isEmpty()) return days
        val week = days.takeLast(7)
        var result = days
        week.forEach { day -> result = addDay(result, day.workoutId) }
        return result
    }

    /**
     * Szkielet planu: `weeks` tygodni, w każdym `perWeek` treningów z rotacji rozłożonych równo po tygodniu,
     * reszta dni to odpoczynek. Rotacja idzie przez cały plan (A, B, A, B…), nie od nowa co tydzień.
     */
    fun skeleton(weeks: Int, perWeek: Int, rotation: List<String>): List<DraftDay> {
        if (rotation.isEmpty()) return emptyList()
        val w = weeks.coerceIn(1, MAX_DAYS / 7)
        val p = perWeek.coerceIn(1, 7)
        val trainingSlots = (0 until p).map { i -> (i * 7) / p }.toSet()
        val result = ArrayList<DraftDay>()
        var next = 0
        repeat(w) {
            for (day in 0 until 7) {
                val workout = if (day in trainingSlots) rotation[next++ % rotation.size] else null
                result += DraftDay("n${result.size + 1}", workout)
            }
        }
        return result
    }

    fun toPlanDays(days: List<DraftDay>): List<PlanDay> = days.mapIndexed { i, d -> PlanDay(i + 1, d.workoutId) }

    /**
     * Klucze dni odhaczonych przez sesje planu. Kolejka odhacza zachłannie po treningu (D-21), więc zbiór
     * może mieć dziury (dzień 1 i 3 zrobione, 2 nie) — dlatego blokujemy zbiór, a nie prefiks.
     */
    fun doneKeys(days: List<DraftDay>, completedWorkoutIds: List<String>): Set<String> {
        val progress = PlanProgress.resolve(toPlanDays(days), completedWorkoutIds)
        return days.filterIndexed { i, _ -> (i + 1) in progress.doneDayIndexes }.map { it.key }.toSet()
    }

    /**
     * Dni, które w szkicu są zrobione, choć przed edycją nie były. Odhacza je sesja ponad plan: trening ukończony
     * więcej razy, niż stoi w dniach (np. drugi raz ze szczegółów treningu). Kolejka (D-21) liczy każdą ukończoną
     * sesję, więc nowy dzień z takim treningiem jest zrobiony od razu; edytor to pokazuje, a zapis przepuszcza.
     */
    fun surplusKeys(days: List<DraftDay>, lockedKeys: Set<String>, completedWorkoutIds: List<String>): Set<String> =
        doneKeys(days, completedWorkoutIds) - lockedKeys

    /**
     * Komunikaty blokujące zapis; pusta lista = można zapisać.
     * `lockedKeys` to dni zrobione przed edycją. Każdy z nich musi zostać zrobiony, bo inaczej sesje z historii
     * przeskoczyłyby na inne dni, i zostać na swoim numerze: przestawienie sąsiada albo usunięcie dnia przed nim
     * zmieniłoby jego datę. Reguła jest ta sama przy pustym `lockedKeys`. Dzień odhaczony przez sesję ponad plan
     * ([surplusKeys]) nie jest błędem: bez tego plan z takim treningiem nie dałby się już wydłużyć.
     */
    fun validate(
        name: String,
        days: List<DraftDay>,
        planWorkoutIds: Set<String>,
        lockedKeys: Set<String>,
        completedWorkoutIds: List<String>,
    ): List<String> {
        val errors = ArrayList<String>()
        if (name.isBlank()) errors += "Nazwa planu nie może być pusta."
        if (days.none { !it.isRest }) errors += "Dodaj przynajmniej jeden dzień z treningiem."
        if (days.size > MAX_DAYS) errors += "Plan może mieć najwyżej $MAX_DAYS dni."
        if (days.any { it.workoutId != null && it.workoutId !in planWorkoutIds }) {
            errors += "Dzień wskazuje trening spoza planu — wybierz trening z listy planu."
        }
        if (!doneKeys(days, completedWorkoutIds).containsAll(lockedKeys) || lockedMoved(days, lockedKeys)) {
            errors += "Zrobione dni muszą zostać zrobione i na swoich numerach. Zmieniaj tylko dni, których jeszcze nie ma w historii."
        }
        return errors
    }
}
