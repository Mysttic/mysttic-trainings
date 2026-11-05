package pl.mysttic.trainings.domain.plan

import kotlin.math.roundToInt

/** Jak potraktować kolejny cykl względem poprzedniego. */
enum class ProgressionStep(val labelPl: String) {
    DOWN("Łatwiej"),
    HOLD("Tak samo"),
    UP("Trudniej"),
}

/**
 * Rozliczenie zamkniętego cyklu: ile z zaplanowanych treningów zrobiono i ile serii faktycznie zapisano.
 * `loggedSets` liczy tylko serie zapisane (pominięte nie tworzą wpisu), więc pokazuje, ile z treningu
 * realnie wykonano, a nie ile razy ktoś wszedł do playera.
 */
data class CycleReview(
    val plannedSessions: Int = 0,
    val doneSessions: Int = 0,
    val plannedSets: Int = 0,
    val loggedSets: Int = 0,
) {
    val adherence: Double get() = if (plannedSessions == 0) 0.0 else doneSessions.toDouble() / plannedSessions
    val setCompletion: Double get() = if (plannedSets == 0) 0.0 else (loggedSets.toDouble() / plannedSets).coerceAtMost(1.0)
    val isEmpty: Boolean get() = doneSessions == 0
    val adherencePercent: Int get() = (adherence * 100).roundToInt()
    val setCompletionPercent: Int get() = (setCompletion * 100).roundToInt()
}

/**
 * Progresja między cyklami (D-22). Świadomie prosta i oparta na frekwencji, nie na rekordach:
 * aplikacja nie wie, ile powtórzeń „mogłeś” zrobić, wie tylko, czy trzymałeś się planu.
 *
 * Podbicie celów nie wydłuża treningu — deklarowany czas zostaje, a `PlanGenerator` dopasowuje
 * liczbę ćwiczeń i przerwy (rundy trzymają się widełek poziomu, D-34). Trudniej znaczy więc
 * „więcej pracy w tej samej jednostce czasu”.
 */
object PlanProgression {

    const val MIN_SCALE = 0.7
    const val MAX_SCALE = 1.6
    private const val UP_FACTOR = 1.10
    private const val DOWN_FACTOR = 0.90

    /** Progi dobrane tak, żeby jeden opuszczony trening w cyklu nie blokował progresji. */
    const val UP_ADHERENCE = 0.8
    const val UP_SET_COMPLETION = 0.8
    const val HOLD_ADHERENCE = 0.5

    fun suggest(review: CycleReview): ProgressionStep = when {
        review.isEmpty -> ProgressionStep.HOLD
        review.adherence >= UP_ADHERENCE && review.setCompletion >= UP_SET_COMPLETION -> ProgressionStep.UP
        review.adherence >= HOLD_ADHERENCE -> ProgressionStep.HOLD
        else -> ProgressionStep.DOWN
    }

    /** Nowy mnożnik celów; kumuluje się między cyklami, ale nie wychodzi poza rozsądny zakres. */
    fun nextScale(currentScale: Double, step: ProgressionStep): Double {
        val factor = when (step) {
            ProgressionStep.UP -> UP_FACTOR
            ProgressionStep.HOLD -> 1.0
            ProgressionStep.DOWN -> DOWN_FACTOR
        }
        return ((currentScale * factor * 100).roundToInt() / 100.0).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /** Opis dla kreatora: co widać w danych i co z tego wynika. */
    fun explain(review: CycleReview, step: ProgressionStep): String {
        if (review.isEmpty) return "Poprzedni cykl bez ukończonego treningu — zostawiam cele bez zmian."
        val base = "Poprzedni cykl: ${review.doneSessions} z ${review.plannedSessions} treningów" +
            (if (review.plannedSets > 0) ", ${review.setCompletionPercent}% serii zapisanych" else "")
        val verdict = when (step) {
            ProgressionStep.UP -> "trzymałeś plan, więc podbijam cele"
            ProgressionStep.HOLD -> "zostawiam cele bez zmian"
            ProgressionStep.DOWN -> "plan był za ciężki, więc odpuszczam cele"
        }
        return "$base — $verdict."
    }

    /** Etykieta mnożnika: „cele bez zmian”, „cele +10%”, „cele −10%”. */
    fun scaleLabel(scale: Double): String {
        val percent = ((scale - 1.0) * 100).roundToInt()
        return when {
            percent == 0 -> "cele bez zmian"
            percent > 0 -> "cele +$percent%"
            else -> "cele −${-percent}%"
        }
    }
}
