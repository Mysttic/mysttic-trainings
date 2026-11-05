package pl.mysttic.trainings.domain.stats

import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.plan.PlanProgress
import pl.mysttic.trainings.domain.plan.PlanSessions
import pl.mysttic.trainings.domain.plan.Program

/** Dzień planu w raporcie: zrobiony przez którą sesję i jak poszło. */
data class PlanDayResult(
    val dayIndex: Int,
    val workoutName: String?,
    val sessionId: String? = null,
    val doneAt: Long? = null,
    val durationSec: Int? = null,
    val rpe: Int? = null,
    val loggedSets: Int = 0,
    /** Serie przewidziane; null = sesja sprzed zapisu `plannedSets` i trening już nie istnieje. */
    val plannedSets: Int? = null,
    /** `plannedSets` policzone z dzisiejszego szablonu, bo sesja jest sprzed v4. */
    val plannedEstimated: Boolean = false,
) {
    val isRest: Boolean get() = workoutName == null
    val done: Boolean get() = sessionId != null
}

/** Ćwiczenie w przebiegu planu: ile serii, jak często w celu i jak zmienił się wynik. */
data class PlanExerciseResult(
    val exerciseId: String,
    val name: String,
    val targetKind: TargetKind,
    val sets: Int,
    /** Serie w celu; AMRAP i kroki bez limitu nie mają celu do trafienia, więc się nie liczą. */
    val hitSets: Int,
    val scoredSets: Int,
    val first: Int,
    val last: Int,
    val best: Int,
    /** Największy ciężar zaplanowany przy tym ćwiczeniu (D-42: w raporcie ciężar z planu, nie wpisywany). */
    val plannedWeightKg: Double?,
) {
    val hitRate: Int? get() = if (scoredSets == 0) null else hitSets * 100 / scoredSets
}

/** Tydzień planu liczony z kolejki (D-21): dni 1–7 to tydzień 1, niezależnie od kalendarza. */
data class PlanWeek(val week: Int, val done: Int, val planned: Int, val reps: Int, val workSec: Int)

data class PlanStats(
    val doneCount: Int,
    val totalCount: Int,
    val abandoned: Int,
    val durationSec: Int,
    val loggedSets: Int,
    val plannedSets: Int,
    val averageRpe: Double?,
    val firstDoneAt: Long?,
    val lastDoneAt: Long?,
    val days: List<PlanDayResult>,
    val exercises: List<PlanExerciseResult>,
    val weeks: List<PlanWeek>,
    /** Notatki z podsumowań sesji planu, od najstarszej. */
    val notes: List<Pair<Long, String>>,
    /**
     * Ukończone sesje planu bez dnia w kolejce: ten sam trening zrobiony częściej, niż stoi w dniach. Nie wchodzą
     * do pozostałych liczb, żeby czas, serie i tabele miały jedną podstawę (dni planu).
     */
    val extraCount: Int = 0,
) {
    /** Kompletność serii w zrobionych treningach, 0..100. Osobna miara, nie `CycleReview` (D-22 zostaje po staremu). */
    val completionPct: Int? get() = if (plannedSets == 0) null else (loggedSets * 100 / plannedSets).coerceAtMost(100)
    val attendancePct: Int get() = if (totalCount == 0) 0 else doneCount * 100 / totalCount
}

/**
 * Statystyki jednego planu (D-42). Sesje przypisuje [PlanSessions], dni — ta sama kolejka co na „Dziś”.
 * Wszystkie liczby (czas, RPE, okres, ćwiczenia, notatki) biorą tylko sesje przypisane do dni planu, jak frekwencja
 * i serie; sesje ponad plan liczy osobno [PlanStats.extraCount].
 * Serie są deduplikowane po pozycji (sesja, blok, pozycja, runda, seria): „Wstecz” w playerze zapisuje serię
 * drugi raz, a liczy się ostatni zapis.
 */
object PlanStatsCalculator {

    fun compute(
        program: Program,
        workoutNames: Map<String, String>,
        sessions: List<Session>,
        logs: List<SetLog>,
        /** Liczba serii dzisiejszego szablonu — zapas dla sesji bez `plannedSets`. */
        plannedFallback: Map<String, Int> = emptyMap(),
        /** Pozycje bez celu do trafienia (AMRAP, krok bez limitu) jako klucz „workoutId/blok/pozycja”. */
        unscored: Set<String> = emptySet(),
    ): PlanStats {
        val allDone = PlanSessions.done(sessions, program)
        val abandoned = sessions.count { it.status == SessionStatus.ABANDONED && PlanSessions.belongsTo(it, program) }

        val withWorkout = allDone.filter { it.workoutId != null }
        val assignment = PlanProgress.assign(program.days, withWorkout.mapNotNull { it.workoutId })
        // Jedna podstawa raportu: sesje przypisane do dni. Nadwyżka (ten sam trening zrobiony drugi raz) liczy się osobno.
        val assignedIds = assignment.values.mapNotNull { withWorkout.getOrNull(it)?.id }.toSet()
        val done = allDone.filter { it.id in assignedIds }
        val planLogs = dedupe(logs.filter { it.sessionId in assignedIds })
        val logsBySession = planLogs.groupBy { it.sessionId }

        val days = program.days.sortedBy { it.dayIndex }.map { day ->
            val session = assignment[day.dayIndex]?.let { withWorkout.getOrNull(it) }
            val fallback = day.workoutId?.let { plannedFallback[it] }
            PlanDayResult(
                dayIndex = day.dayIndex,
                workoutName = day.workoutId?.let { workoutNames[it] ?: "Trening" },
                sessionId = session?.id,
                doneAt = session?.startedAt,
                durationSec = session?.durationSec,
                rpe = session?.rpe,
                loggedSets = session?.let { logsBySession[it.id]?.size } ?: 0,
                plannedSets = session?.let { it.plannedSets ?: fallback },
                plannedEstimated = session != null && session.plannedSets == null && fallback != null,
            )
        }
        val doneDays = days.filter { it.done }

        val sessionWorkout = done.associate { it.id to it.workoutId }
        val exercises = planLogs.groupBy { it.exerciseId }.map { (id, list) ->
            val ordered = list.sortedBy { it.completedAt }
            val scored = ordered.filter { log ->
                log.targetKind != TargetKind.AMRAP &&
                    "${sessionWorkout[log.sessionId]}/${log.blockIndex}/${log.itemIndex}" !in unscored
            }
            PlanExerciseResult(
                exerciseId = id,
                name = ordered.last().exerciseName,
                targetKind = ordered.last().targetKind,
                sets = ordered.size,
                hitSets = scored.count { it.actualValue >= it.targetValue },
                scoredSets = scored.size,
                first = ordered.first().actualValue,
                last = ordered.last().actualValue,
                best = ordered.maxOf { it.actualValue },
                plannedWeightKg = ordered.mapNotNull { it.weightKg }.maxOrNull(),
            )
        }.sortedByDescending { it.sets }

        val weekCount = ((program.days.maxOfOrNull { it.dayIndex } ?: 0) + 6) / 7
        val weeks = (1..weekCount).map { week ->
            val range = ((week - 1) * 7 + 1)..(week * 7)
            val inWeek = days.filter { it.dayIndex in range && !it.isRest }
            val weekLogs = inWeek.mapNotNull { it.sessionId }.flatMap { logsBySession[it].orEmpty() }
            PlanWeek(
                week = week,
                done = inWeek.count { it.done },
                planned = inWeek.size,
                reps = weekLogs.filter { it.targetKind != TargetKind.SECONDS }.sumOf { it.actualValue },
                workSec = weekLogs.filter { it.targetKind == TargetKind.SECONDS }.sumOf { it.actualValue },
            )
        }

        val rpes = done.mapNotNull { it.rpe }
        return PlanStats(
            doneCount = doneDays.size,
            totalCount = days.count { !it.isRest },
            abandoned = abandoned,
            durationSec = done.sumOf { it.durationSec ?: 0 },
            loggedSets = doneDays.filter { it.plannedSets != null }.sumOf { it.loggedSets },
            plannedSets = doneDays.sumOf { it.plannedSets ?: 0 },
            averageRpe = if (rpes.isEmpty()) null else rpes.average(),
            firstDoneAt = done.firstOrNull()?.startedAt,
            lastDoneAt = done.lastOrNull()?.startedAt,
            days = days,
            exercises = exercises,
            weeks = weeks,
            notes = done.filter { it.note.isNotBlank() }.map { it.startedAt to it.note.trim() },
            extraCount = allDone.size - done.size,
        )
    }

    /** Ostatni zapis serii na tej samej pozycji wygrywa. */
    fun dedupe(logs: List<SetLog>): List<SetLog> =
        logs.groupBy { listOf(it.sessionId, it.blockIndex, it.itemIndex, it.round, it.setIndex) }
            .map { (_, same) -> same.maxBy { it.completedAt } }
            .sortedBy { it.completedAt }
}
