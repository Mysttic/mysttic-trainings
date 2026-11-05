package pl.mysttic.trainings.domain.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.mysttic.trainings.domain.model.RestKind
import pl.mysttic.trainings.domain.model.TargetKind

/**
 * Plan treningu rozwinięty do płaskiej listy kroków. Rozwijany raz, deterministycznie,
 * przy starcie (oraz ponownie przy zmianie poziomu trudności — patrz [WorkoutPlanner.merge]).
 */
@Serializable
sealed interface Step {
    /** Czas trwania kroku w sekundach, albo null gdy krok czeka na „Zrobione” (REPS). */
    val durationSec: Int?

    @Serializable
    @SerialName("countdown")
    data class Countdown(val seconds: Int) : Step {
        override val durationSec: Int get() = seconds
    }

    @Serializable
    @SerialName("work")
    data class Work(
        val blockIndex: Int,
        val itemIndex: Int,
        /** Numer rundy (CIRCUIT) — od 1. */
        val round: Int,
        /** Numer serii (SETS) — od 1. */
        val setIndex: Int,
        val setsInItem: Int,
        val exerciseId: String,
        val exerciseName: String,
        val targetKind: TargetKind,
        /** Cel po przeskalowaniu poziomem trudności i korekcie trenera. */
        val targetValue: Int,
        val weightKg: Double? = null,
        /** Liczba rund bloku (CIRCUIT) — „runda 2 z 8” w mowie i powiadomieniu; 1 dla serii prostych. */
        val roundsInBlock: Int = 1,
        /** Krok bez limitu: czas liczony w górę, koniec przez „Zrobione”; `targetValue` to cel orientacyjny. */
        val openEnded: Boolean = false,
        /** Sygnał co tyle sekund (oddech w rytmie); null = zwykły krok na czas. */
        val cadenceSec: Int? = null,
        /** Wskazówka autora planu do tej pozycji (D-42); pusta = brak. */
        val note: String = "",
        /** Nazwa bloku od autora planu; pusta = bez nazwy. */
        val blockLabel: String = "",
        /** Okno EMOM (s): tyle trwa cała runda, licząc pracę i przerwę po niej; null = zwykły krok. */
        val windowSec: Int? = null,
    ) : Step {
        override val durationSec: Int? get() = if (targetKind == TargetKind.REPS || openEnded) null else targetValue

        /** Ile sygnałów rytmu mieści się w kroku (30 oddechów w 90 s co 3 s); 0 bez rytmu. */
        val cadenceCount: Int get() = cadenceSec?.takeIf { it > 0 }?.let { targetValue / it } ?: 0

        /** Klucz pozycji w planie — niezależny od poziomu trudności; używany przy przeplanowaniu. */
        val positionKey: String get() = "$blockIndex/$itemIndex/$round/$setIndex"
    }

    @Serializable
    @SerialName("rest")
    data class Rest(
        val seconds: Int,
        val kind: RestKind,
        /**
         * Przerwa dopełniająca okno EMOM: `seconds` z plannera to tylko estymata (3 s na powtórzenie),
         * a silnik podmienia ją przy wejściu na to, co zostało z okna. null = zwykła przerwa.
         */
        val windowSec: Int? = null,
    ) : Step {
        override val durationSec: Int get() = seconds
    }
}
