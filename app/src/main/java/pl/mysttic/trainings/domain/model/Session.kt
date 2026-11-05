package pl.mysttic.trainings.domain.model

/** Wykonanie treningu. `workoutName` i `difficulty` są snapshotem — edycja szablonu nie zmienia historii. */
data class Session(
    val id: String,
    val workoutId: String?,
    val workoutName: String,
    val difficulty: Difficulty,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    /** Odczuwany wysiłek 1–10, wpisywany w podsumowaniu. */
    val rpe: Int? = null,
    val note: String = "",
    /** Plan, z którego trening był uruchomiony (D-42); snapshot z chwili startu. null = poza planem albo sesja sprzed v4. */
    val programId: String? = null,
    /** Ile serii przewidywał trening przy zakończeniu (po zmianie poziomu w trakcie); null w starszych sesjach. */
    val plannedSets: Int? = null,
) {
    val durationSec: Int? get() = endedAt?.let { ((it - startedAt) / 1000).toInt() }
}

/** Jedna wykonana seria — źródło wszystkich statystyk. */
data class SetLog(
    val id: Long = 0,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val blockIndex: Int,
    val itemIndex: Int,
    val round: Int,
    val setIndex: Int,
    val targetKind: TargetKind,
    val targetValue: Int,
    val actualValue: Int,
    val weightKg: Double? = null,
    val completedAt: Long,
) {
    val hitTarget: Boolean get() = actualValue >= targetValue
}
