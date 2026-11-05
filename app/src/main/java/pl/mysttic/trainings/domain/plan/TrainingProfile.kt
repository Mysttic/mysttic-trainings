package pl.mysttic.trainings.domain.plan

import kotlinx.serialization.Serializable
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import kotlin.math.roundToInt

/** Po co użytkownik trenuje. Steruje doborem ćwiczeń, rodzajem bloków i długością przerw. */
enum class Goal(val labelPl: String, val descriptionPl: String) {
    STRENGTH("Siła", "Serie proste, dłuższe przerwy, nacisk na ćwiczenia oporowe."),
    CONDITION("Kondycja", "Obwody z krótkimi przerwami, więcej pracy w jednostce czasu."),
    FAT_LOSS("Redukcja", "Obwody z kardio i plyometrią, krótkie przerwy, wysoka objętość."),
    MOBILITY("Mobilność", "Spokojniejsze tempo, praca na czas, rozciąganie i tułów."),
}

/**
 * Dane z kreatora planu (M4). Snapshot: zapisywany razem z cyklem, żeby było wiadomo,
 * na jakich parametrach powstał, i żeby kolejny cykl dało się wygenerować od zmienionych.
 */
@Serializable
data class TrainingProfile(
    val heightCm: Int = 175,
    val weightKg: Double = 75.0,
    val goal: Goal = Goal.CONDITION,
    val level: Difficulty = Difficulty.INTERMEDIATE,
    /** Ile dni w tygodniu użytkownik chce trenować (2..6). */
    val daysPerWeek: Int = 3,
    /** Ile minut ma trwać pojedynczy trening; górna granica zależy od poziomu ([minutesRange]). */
    val sessionMinutes: Int = 30,
    /** Długość cyklu w dniach; po jego końcu kreator proponuje kolejny. */
    val cycleDays: Int = 30,
    val equipment: Set<Equipment> = emptySet(),
    /** Mnożnik celów wypracowany w poprzednich cyklach (D-22); 1.0 = wartości bazowe. */
    val targetScale: Double = 1.0,
    /** Gotowy program (D-37): nazwa z `PresetId`; null = plan z kreatora. Poziom i długość siedzą w `level` i `cycleDays`. */
    val presetId: String? = null,
    /** Parametr gotowego programu, jeśli o coś pyta (100 pompek: ile pompek z rzędu na start). */
    val presetParam: Int? = null,
) {
    val isPreset: Boolean get() = presetId != null

    val bmi: Double
        get() {
            if (heightCm <= 0) return 0.0
            val meters = heightCm / 100.0
            return weightKg / (meters * meters)
        }

    val bmiRounded: Double get() = (bmi * 10).roundToInt() / 10.0

    /**
     * Przy wysokim BMI skoki obciążają stawy nieproporcjonalnie do korzyści,
     * więc generator omija plyometrię i podmienia ją na warianty bez fazy lotu.
     */
    val avoidJumping: Boolean get() = bmi >= HIGH_BMI

    companion object {
        const val HIGH_BMI = 30.0
        val DAYS_RANGE = 2..6
        val MINUTES_RANGE = 15..75
        val CYCLE_DAYS_OPTIONS = listOf(14, 21, 28, 30, 42, 60)

        /**
         * Długość treningu, jaką da się sensownie wypełnić na danym poziomie (D-34). Rundy i serie
         * trzymają się widełek poziomu, więc początkujący na 75 minut dostałby albo absurdalną objętość,
         * albo pół godziny przerw. Zamiast tego kreator nie pozwala takiego czasu wybrać.
         */
        fun minutesRange(level: Difficulty): IntRange = when (level) {
            Difficulty.BEGINNER -> 15..45
            Difficulty.INTERMEDIATE -> 15..60
            Difficulty.ADVANCED -> MINUTES_RANGE
        }

        /** Najdłuższy plan układany ręcznie albo wczytany z pliku (D-42). */
        const val MAX_PLAN_DAYS = 120

        /**
         * Profil planu, który nie powstał w kreatorze (własny, od kogoś): bez sylwetki i celu z kreatora,
         * tylko poziom i długość. Nie przechodzi przez [sanitize], bo plan własny może mieć 1 dzień.
         */
        fun neutral(level: Difficulty, cycleDays: Int, daysPerWeek: Int = 3): TrainingProfile = TrainingProfile(
            level = level,
            cycleDays = cycleDays.coerceIn(1, MAX_PLAN_DAYS),
            daysPerWeek = daysPerWeek.coerceIn(1, 7),
        )

        fun sanitize(profile: TrainingProfile): TrainingProfile = profile.copy(
            heightCm = profile.heightCm.coerceIn(120, 230),
            weightKg = profile.weightKg.coerceIn(35.0, 250.0),
            daysPerWeek = profile.daysPerWeek.coerceIn(DAYS_RANGE.first, DAYS_RANGE.last),
            sessionMinutes = profile.sessionMinutes.coerceIn(minutesRange(profile.level)),
            cycleDays = profile.cycleDays.coerceIn(7, 120),
            targetScale = profile.targetScale.coerceIn(PlanProgression.MIN_SCALE, PlanProgression.MAX_SCALE),
        )
    }
}
