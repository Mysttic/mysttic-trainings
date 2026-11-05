package pl.mysttic.trainings.domain.stats

import pl.mysttic.trainings.domain.engine.EngineState
import pl.mysttic.trainings.domain.engine.Phase
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.TargetKind
import java.time.LocalDate

/** Podsumowanie okresu (tydzień, miesiąc): tylko sesje ukończone. */
data class PeriodStats(
    val sessions: Int = 0,
    val durationSec: Int = 0,
    val sets: Int = 0,
    val reps: Int = 0,
    val workSec: Int = 0,
) {
    val isEmpty: Boolean get() = sessions == 0
}

/** Wynik jednego ćwiczenia w sesji zestawiony z poprzednim wykonaniem tego treningu. */
data class ExerciseDelta(
    val exerciseId: String,
    val exerciseName: String,
    val targetKind: TargetKind,
    val sets: Int,
    val total: Int,
    /** null = ćwiczenia nie było w poprzednim wykonaniu. */
    val previousTotal: Int? = null,
) {
    val delta: Int? get() = previousTotal?.let { total - it }
}

/** Ćwiczenie pominięte w całości: w podsumowaniu bez wyniku, ale z opinią dla trenera (D-35). */
data class SkippedExercise(val exerciseId: String, val exerciseName: String, val sets: Int)

enum class HistoryPeriod(val labelPl: String, val days: Int?) {
    WEEK("7 dni", 7),
    MONTH("30 dni", 30),
    ALL("Wszystko", null),
}

/**
 * Statystyki historii treningów. Czysty Kotlin: bez Androida, bez zegara systemowego —
 * wszystkie granice czasu przychodzą z zewnątrz, żeby testy były deterministyczne.
 */
object SessionStats {

    private fun List<SetLog>.reps() =
        filter { it.targetKind == TargetKind.REPS || it.targetKind == TargetKind.AMRAP }.sumOf { it.actualValue }

    private fun List<SetLog>.workSec() =
        filter { it.targetKind == TargetKind.SECONDS }.sumOf { it.actualValue }

    /** Statystyki ukończonych sesji rozpoczętych w `[from, to)`. */
    fun period(sessions: List<Session>, logs: List<SetLog>, from: Long, to: Long = Long.MAX_VALUE): PeriodStats {
        val done = sessions.filter { it.status == SessionStatus.DONE && it.startedAt >= from && it.startedAt < to }
        if (done.isEmpty()) return PeriodStats()
        val ids = done.map { it.id }.toSet()
        val periodLogs = logs.filter { it.sessionId in ids }
        return PeriodStats(
            sessions = done.size,
            durationSec = done.sumOf { it.durationSec ?: 0 },
            sets = periodLogs.size,
            reps = periodLogs.reps(),
            workSec = periodLogs.workSec(),
        )
    }

    /** Filtr listy historii: status (null = wszystkie) i okres liczony wstecz od `now`. */
    fun filter(sessions: List<Session>, status: SessionStatus?, period: HistoryPeriod, now: Long): List<Session> {
        val since = period.days?.let { now - it * DAY_MS } ?: Long.MIN_VALUE
        return sessions.filter { (status == null || it.status == status) && it.startedAt >= since }
    }

    /**
     * Seria: ile dni kalendarzowych z rzędu, licząc wstecz, poszło zgodnie z planem (D-21).
     * Dzień liczy się, gdy był trening albo gdy plan przewidywał odpoczynek — zaplanowana przerwa
     * serii nie kasuje. Dzisiejszy brak treningu też jeszcze nie kasuje, bo dzień trwa do północy.
     * Bez planu (`isRestDay` zawsze false) wychodzi zwykła seria dni z treningiem.
     */
    fun streak(
        trainingDates: Collection<LocalDate>,
        today: LocalDate,
        isRestDay: (LocalDate) -> Boolean = { false },
    ): Int {
        val dates = trainingDates.toSet()
        // Dzień treningowy bez treningu jeszcze trwa: zaczynamy liczyć od wczoraj.
        var day = if (today in dates || isRestDay(today)) today else today.minusDays(1)
        var count = 0
        while (day in dates || isRestDay(day)) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Najdłuższa seria w historii — ta sama reguła co [streak], liczona od pierwszego treningu do dzisiaj. */
    fun bestStreak(
        trainingDates: Collection<LocalDate>,
        today: LocalDate,
        isRestDay: (LocalDate) -> Boolean = { false },
    ): Int {
        val dates = trainingDates.toSortedSet()
        val first = dates.firstOrNull() ?: return 0
        var best = 0
        var current = 0
        var day = first
        while (!day.isAfter(today)) {
            if (day in dates || isRestDay(day)) {
                current++
                if (current > best) best = current
            } else {
                current = 0
            }
            day = day.plusDays(1)
        }
        return best
    }

    /**
     * Wynik per ćwiczenie w kolejności pierwszego wystąpienia w sesji, zestawiony z poprzednim wykonaniem.
     * Ćwiczenia obecne tylko w poprzedniej sesji są pomijane — porównujemy to, co zrobiono dzisiaj.
     */
    fun perExercise(current: List<SetLog>, previous: List<SetLog>): List<ExerciseDelta> {
        val previousByExercise = previous.groupBy { it.exerciseId }
        return current.groupBy { it.exerciseId }.map { (exerciseId, logs) ->
            val kind = logs.first().targetKind
            val previousLogs = previousByExercise[exerciseId]
            ExerciseDelta(
                exerciseId = exerciseId,
                exerciseName = logs.first().exerciseName,
                targetKind = kind,
                sets = logs.size,
                total = logs.total(kind),
                previousTotal = previousLogs?.total(kind),
            )
        }
    }

    /**
     * Ćwiczenia, przez które trening przeszedł, a które nie mają ani jednej zapisanej serii, czyli pominięte
     * w całości (issue #22). Pominięcie nie tworzy wpisu (D-22), więc liczy się je z kroków stanu końcowego:
     * ukończony trening przeszedł przez wszystkie, przerwany tylko przez te przed krokiem, na którym stanął
     * (ten i dalsze są niezaczęte, nie pominięte). Kolejność jak w planie, każde ćwiczenie raz,
     * `sets` = liczba pominiętych serii.
     */
    fun skipped(ended: EngineState, logs: List<SetLog>): List<SkippedExercise> {
        val reached = if (ended.phase == Phase.FINISHED) ended.steps else ended.steps.take(ended.stepIndex)
        val logged = logs.map { it.exerciseId }.toSet()
        return reached.filterIsInstance<Step.Work>()
            .filterNot { it.exerciseId in logged }
            .groupBy { it.exerciseId }
            .map { (exerciseId, steps) -> SkippedExercise(exerciseId, steps.first().exerciseName, steps.size) }
    }

    /** Suma w jednostce ćwiczenia: powtórzenia albo sekundy pracy. */
    private fun List<SetLog>.total(kind: TargetKind): Int =
        if (kind == TargetKind.SECONDS) workSec() else reps()

    const val DAY_MS = 24 * 60 * 60 * 1000L
}
