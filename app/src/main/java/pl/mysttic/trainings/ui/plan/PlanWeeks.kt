package pl.mysttic.trainings.ui.plan

/**
 * Tydzień planu w podglądzie (D-54): dni 1 do 7, 8 do 14 i tak dalej, tak jak „Rozpisz tygodnie” w edytorze
 * i rytm planu. Zwinięty tydzień mówi, ile ma treningów i ile z nich jest zrobionych.
 */
data class PlanWeek(val number: Int, val entries: List<PlanWeekEntry>) {
    val trainings: Int get() = entries.count { it is PlanWeekEntry.Training }
    val done: Int get() = entries.count { it is PlanWeekEntry.Training && it.day.done }
    val hasNext: Boolean get() = entries.any { it is PlanWeekEntry.Training && it.day.isNext }
}

sealed interface PlanWeekEntry {
    data class Training(val day: PlanDayRow) : PlanWeekEntry

    /** Kolejne dni odpoczynku jednym wierszem: w tygodniu z trzema treningami cztery wiersze „Odpoczynek” to szum. */
    data class Rest(val days: List<PlanDayRow>) : PlanWeekEntry
}

object PlanWeeks {
    const val DAYS = 7

    fun of(days: List<PlanDayRow>): List<PlanWeek> =
        days.sortedBy { it.dayIndex }
            .groupBy { (it.dayIndex - 1).coerceAtLeast(0) / DAYS }
            .map { (week, inWeek) -> PlanWeek(number = week + 1, entries = entries(inWeek)) }

    /** Tydzień rozwinięty na start: ten z następnym treningiem; bez niego (plan zrobiony) żaden. */
    fun defaultExpanded(weeks: List<PlanWeek>): Int? = weeks.firstOrNull { it.hasNext }?.number

    private fun entries(days: List<PlanDayRow>): List<PlanWeekEntry> {
        val out = mutableListOf<PlanWeekEntry>()
        var rest = mutableListOf<PlanDayRow>()
        days.forEach { day ->
            if (day.isRest) {
                rest += day
            } else {
                if (rest.isNotEmpty()) {
                    out += PlanWeekEntry.Rest(rest)
                    rest = mutableListOf()
                }
                out += PlanWeekEntry.Training(day)
            }
        }
        if (rest.isNotEmpty()) out += PlanWeekEntry.Rest(rest)
        return out
    }
}
