package pl.mysttic.trainings.domain.stats

import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.TargetKind
import java.time.DayOfWeek
import java.time.LocalDate

/** Tydzień w słupkach: od poniedziałku, z sumą pracy. */
data class WeekVolume(
    val weekStart: LocalDate,
    val sessions: Int,
    val reps: Int,
    val workSec: Int,
) {
    /** Wspólna miara do wysokości słupka: powtórzenia plus sekundy przeliczone po 3 s na powtórzenie. */
    val volume: Int get() = reps + workSec / 3
}

/** Najlepsza seria danego ćwiczenia w danym dniu. */
data class ExerciseRecord(
    val date: LocalDate,
    val best: Int,
    val targetKind: TargetKind,
)

/** Dzień w kalendarzu aktywności: ile ukończonych treningów. */
data class DayHeat(val date: LocalDate, val sessions: Int)

/**
 * Dane pod wykresy postępu (M5, D-23). Czysty Kotlin: wszystkie granice czasu wchodzą z zewnątrz,
 * więc wykresy da się przetestować bez Androida i bez zegara systemowego.
 */
object ProgressStats {

    /** Sekundy pracy przeliczane na powtórzenia, żeby jeden słupek obejmował trening siłowy i obwodowy. */
    const val SEC_PER_REP = 3

    /**
     * Wolumen tygodniowy: ostatnie `weeks` tygodni licząc wstecz od tygodnia, w którym jest `today`.
     * Tygodnie bez treningu zostają w wyniku z zerami, żeby wykres nie kłamał o ciągłości.
     */
    fun weeklyVolume(
        sessions: List<Session>,
        logs: List<SetLog>,
        today: LocalDate,
        weeks: Int = 8,
        toDate: (Long) -> LocalDate,
    ): List<WeekVolume> {
        if (weeks <= 0) return emptyList()
        val done = sessions.filter { it.status == SessionStatus.DONE }
        val logsBySession = logs.groupBy { it.sessionId }
        val currentWeekStart = today.with(DayOfWeek.MONDAY)
        val firstWeekStart = currentWeekStart.minusWeeks((weeks - 1).toLong())
        val byWeek = done.groupBy { toDate(it.startedAt).with(DayOfWeek.MONDAY) }
        return (0 until weeks).map { index ->
            val weekStart = firstWeekStart.plusWeeks(index.toLong())
            val weekSessions = byWeek[weekStart].orEmpty()
            val weekLogs = weekSessions.flatMap { logsBySession[it.id].orEmpty() }
            WeekVolume(
                weekStart = weekStart,
                sessions = weekSessions.size,
                reps = weekLogs.filter { it.targetKind != TargetKind.SECONDS }.sumOf { it.actualValue },
                workSec = weekLogs.filter { it.targetKind == TargetKind.SECONDS }.sumOf { it.actualValue },
            )
        }
    }

    /**
     * Najlepsza seria ćwiczenia dzień po dniu, rosnąco. Bierzemy maksimum z dnia, a nie sumę:
     * postęp w ćwiczeniu to „ile dałem radę w jednej serii”, nie „ile serii zrobiłem”.
     */
    fun exerciseHistory(logs: List<SetLog>, exerciseId: String, toDate: (Long) -> LocalDate): List<ExerciseRecord> {
        val forExercise = logs.filter { it.exerciseId == exerciseId && it.actualValue > 0 }
        if (forExercise.isEmpty()) return emptyList()
        return forExercise
            .groupBy { toDate(it.completedAt) }
            .map { (date, dayLogs) ->
                ExerciseRecord(
                    date = date,
                    best = dayLogs.maxOf { it.actualValue },
                    targetKind = dayLogs.first().targetKind,
                )
            }
            .sortedBy { it.date }
    }

    /** Rekord życiowy w jednej serii — liczba pokazywana obok wykresu. */
    fun personalBest(history: List<ExerciseRecord>): ExerciseRecord? = history.maxByOrNull { it.best }

    /**
     * Kalendarz aktywności: `days` dni wstecz od `today` włącznie, zawsze pełny zakres,
     * żeby siatka miała stałą długość niezależnie od tego, kiedy padły treningi.
     */
    fun heatmap(sessions: List<Session>, today: LocalDate, days: Int = 84, toDate: (Long) -> LocalDate): List<DayHeat> {
        if (days <= 0) return emptyList()
        val counts = sessions
            .filter { it.status == SessionStatus.DONE }
            .groupingBy { toDate(it.startedAt) }
            .eachCount()
        val first = today.minusDays((days - 1).toLong())
        return (0 until days).map { offset ->
            val date = first.plusDays(offset.toLong())
            DayHeat(date, counts[date] ?: 0)
        }
    }
}
