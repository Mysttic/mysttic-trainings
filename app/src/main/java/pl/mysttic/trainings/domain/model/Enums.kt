package pl.mysttic.trainings.domain.model

/**
 * Sprzęt, który użytkownik może zaznaczyć jako posiadany.
 * Ćwiczenie wymaga WSZYSTKICH elementów ze swojego zbioru `equipment`;
 * pusty zbiór = ćwiczenie bez sprzętu.
 * `labelPl` z podpowiedzią zamiennika czyta się w Ustawieniach, `shortPl` mieści się w chipie i wierszu listy.
 * Ergometr (D-44) to maszyna do kardio na czas: jego ćwiczenia są w bibliotece i edytorach, ale nie w kreatorze
 * i nie jako zamiennik zwykłego ćwiczenia.
 */
enum class Equipment(val labelPl: String, val shortPl: String = labelPl, val ergometer: Boolean = false) {
    DUMBBELLS("Hantle"),
    KETTLEBELL("Kettlebell"),
    RESISTANCE_BAND("Guma oporowa"),
    PULL_UP_BAR("Drążek"),
    LOW_BAR("Niski drążek lub stabilny stół", "Niski drążek"),
    BENCH("Ławka lub stabilne krzesło", "Ławka"),
    JUMP_ROPE("Skakanka"),
    MEDICINE_BALL("Piłka lekarska"),
    EXERCISE_BALL("Piłka gimnastyczna"),
    BARBELL("Sztanga"),
    ROWING_MACHINE("Ergometr wioślarski", "Wioślarz", ergometer = true),
    STATIONARY_BIKE("Rower stacjonarny", "Rower", ergometer = true),
    ELLIPTICAL("Orbitrek", ergometer = true),
    TREADMILL("Bieżnia", ergometer = true),
}

/** Poziom trudności — globalne ustawienie użytkownika, zmienne w każdej chwili (także w trakcie treningu). */
enum class Difficulty(val level: Int, val labelPl: String) {
    BEGINNER(1, "Początkujący"),
    INTERMEDIATE(2, "Średniozaawansowany"),
    ADVANCED(3, "Zaawansowany");

    companion object {
        fun fromLevel(level: Int): Difficulty = entries.firstOrNull { it.level == level } ?: INTERMEDIATE
    }
}

/** Jak naturalnie mierzy się ćwiczenie: powtórzeniami czy czasem (izometria, kardio). */
enum class ExerciseKind { REPS, TIME }

/** Cel pojedynczej serii w planie treningu. */
enum class TargetKind { REPS, SECONDS, AMRAP }

/** SETS: N serii jednego ćwiczenia z przerwami. CIRCUIT: R rund przez listę ćwiczeń (tabata = CIRCUIT z celami czasowymi). */
enum class BlockKind { SETS, CIRCUIT }

enum class RestKind { BETWEEN_SETS, BETWEEN_ITEMS, BETWEEN_ROUNDS, BETWEEN_BLOCKS }

enum class SessionStatus { IN_PROGRESS, DONE, ABANDONED }

enum class ExerciseCategory(val labelPl: String) {
    STRENGTH("Siła"),
    CARDIO("Kardio"),
    PLYOMETRICS("Plyometria"),
    STRETCHING("Rozciąganie"),
    /** Ćwiczenia oddechowe (D-37): tylko w gotowych programach, nigdy w planie z kreatora ani jako zamiennik. */
    BREATHING("Oddech"),
}

enum class MuscleGroup(val labelPl: String) {
    CHEST("Klatka piersiowa"),
    UPPER_BACK("Górna część pleców"),
    LATS("Najszersze grzbietu"),
    TRAPS("Kaptury"),
    LOWER_BACK("Dolna część pleców"),
    SHOULDERS("Barki"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Przedramiona"),
    ABS("Brzuch"),
    GLUTES("Pośladki"),
    QUADS("Czworogłowe uda"),
    HAMSTRINGS("Dwugłowe uda"),
    CALVES("Łydki"),
    ADDUCTORS("Przywodziciele"),
    ABDUCTORS("Odwodziciele"),
    NECK("Szyja"),
    FULL_BODY("Całe ciało"),
}
