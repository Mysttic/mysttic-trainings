package pl.mysttic.trainings.domain.model

import kotlinx.serialization.Serializable

/** Szablon treningu. Bloki są przechowywane w bazie jako JSON (patrz docs/DECISIONS.md). */
data class Workout(
    val id: String,
    val name: String,
    val note: String = "",
    val blocks: List<Block>,
    val createdAt: Long,
    val updatedAt: Long,
    /** Trening z seedu — nie do usunięcia, ale do kopiowania. */
    val isBuiltIn: Boolean = false,
) {
    val exerciseIds: List<String> get() = blocks.flatMap { b -> b.items.map { it.exerciseId } }.distinct()

    /** Stały protokół (D-37): wszystkie bloki `fixed`, więc poziom, warianty i trener nie mają tu nic do roboty. */
    val isFixed: Boolean get() = blocks.isNotEmpty() && blocks.all { it.fixed }
}

@Serializable
data class Block(
    val kind: BlockKind,
    /** Tylko CIRCUIT: liczba rund. */
    val rounds: Int = 1,
    /** Tylko CIRCUIT: przerwa między rundami (s). */
    val restBetweenRoundsSec: Int = 60,
    val items: List<BlockItem>,
    /**
     * Stały protokół (D-37): cele i przerwy są takie, jak zapisano. `WorkoutPlanner` nie skaluje ich poziomem,
     * nie podmienia wariantu ćwiczenia i nie nakłada korekty trenera. Gotowe programy (tabata 20/10) tego wymagają.
     */
    val fixed: Boolean = false,
    /** Przerwa po tym bloku, przed następnym (s); null = domyślna minuta z plannera. 0 = bez przerwy. */
    val restAfterSec: Int? = null,
    /** Nazwa bloku od autora planu („Rozgrzewka”, „Superseria A”); pusta = „Blok N” (D-42). */
    val label: String = "",
)

@Serializable
data class BlockItem(
    val exerciseId: String,
    val targetKind: TargetKind,
    /** Powtórzenia (REPS) albo sekundy (SECONDS, AMRAP) — wartość bazowa dla poziomu INTERMEDIATE. */
    val targetValue: Int,
    /** Tylko SETS: liczba serii. */
    val sets: Int = 3,
    /** Tylko SETS: przerwa między seriami (s). */
    val restAfterSetSec: Int = 45,
    /** Przerwa po ostatniej serii tego elementu / między elementami obwodu (s). */
    val restAfterItemSec: Int = 60,
    val weightKg: Double? = null,
    /**
     * Krok bez limitu (SECONDS): czas leci w górę, użytkownik kończy go sam, a `targetValue` to tylko orientacyjny
     * cel (zatrzymanie oddechu „jak najdłużej”). Bezpiecznik: [pl.mysttic.trainings.domain.engine.WorkoutEngine.OPEN_ENDED_CAP].
     */
    val openEnded: Boolean = false,
    /** Sygnał co tyle sekund w trakcie kroku na czas (oddech w rytmie); null = bez rytmu. */
    val cadenceSec: Int? = null,
    /** Wskazówka od autora planu do tej pozycji („kolana na zewnątrz”, „tempo 3-1-1”); pokazuje ją player (D-42). */
    val note: String = "",
    /**
     * Okno EMOM (s): praca i następująca po niej przerwa mają razem trwać tyle sekund, więc długość przerwy
     * liczy silnik jako `windowSec` minus faktyczny czas pracy. null = przerwa z `restAfterSetSec`,
     * `restAfterItemSec` albo `restBetweenRoundsSec`, czyli jak wszędzie indziej.
     */
    val windowSec: Int? = null,
)
