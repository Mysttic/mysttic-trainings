package pl.mysttic.trainings.domain.model

data class Exercise(
    /** Stabilny identyfikator (kebab-case), np. "pushups". */
    val id: String,
    val nameEn: String,
    val namePl: String,
    val kind: ExerciseKind,
    val level: Difficulty,
    val category: ExerciseCategory,
    /** Wymagany sprzęt — wszystkie elementy. Pusty zbiór = bez sprzętu. */
    val equipment: Set<Equipment>,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val instructionsPl: List<String>,
    /**
     * Ścieżki względem korzenia assetów, czyli katalogu `baza/` (D-47), np. "images/pushups.jpg". Pusta lista = brak
     * zdjęcia. Baza daje jedną ilustrację na ćwiczenie; dawne seedy dawały dwie klatki (start, pozycja docelowa),
     * więc pojedyncze zdjęcie w UI dalej bierzemy z końca listy.
     */
    val images: List<String>,
    /** Łatwiejszy / trudniejszy wariant — używane przy zmianie poziomu trudności. */
    val easierId: String? = null,
    val harderId: String? = null,
    /** Domyślny cel przy dodawaniu do treningu. */
    val defaultReps: Int = 12,
    val defaultSeconds: Int = 30,
    val isCustom: Boolean = false,
    /**
     * Data wycofania z bazy (D-47), `RRRR-MM-DD`; null = ćwiczenie w użyciu. Wycofane zostaje dla historii,
     * zapisanych planów, notatek i trenera, ale nie trafia do biblioteki, kreatora, zamienników ani wyszukiwarki edytora.
     */
    val retiredAt: String? = null,
) {
    val isRetired: Boolean get() = retiredAt != null

    val requiresEquipment: Boolean get() = equipment.isNotEmpty()

    fun canDoWith(owned: Set<Equipment>): Boolean = owned.containsAll(equipment)

    /** Ćwiczenie na ergometrze (D-44): tylko biblioteka i edytory, nigdy kreator ani zamiennik zwykłego ćwiczenia. */
    val usesErgometer: Boolean get() = equipment.any { it.ergometer }

    fun missingEquipment(owned: Set<Equipment>): Set<Equipment> = equipment - owned

    val defaultTargetKind: TargetKind get() = if (kind == ExerciseKind.TIME) TargetKind.SECONDS else TargetKind.REPS

    val defaultTargetValue: Int get() = if (kind == ExerciseKind.TIME) defaultSeconds else defaultReps
}
