package pl.mysttic.trainings.domain.engine

/** Widok stanu silnika dla UI, policzony dla konkretnego `now`. */
data class PlayerSnapshot(
    val phase: Phase,
    val step: Step?,
    val stepIndex: Int,
    val totalSteps: Int,
    /** Numer bieżącego (albo najbliższego) kroku pracy i liczba wszystkich kroków pracy. */
    val workNumber: Int,
    val workTotal: Int,
    val remainingSec: Int?,
    val elapsedInPhaseSec: Int,
    val totalElapsedSec: Int,
    val paused: Boolean,
    val nextWork: Step.Work?,
    /** Licznik AMRAP zapamiętany przez silnik; ekran startuje od niego po powrocie do playera. */
    val pendingCount: Int? = null,
) {
    val progress: Float get() = if (totalSteps == 0) 0f else stepIndex.toFloat() / totalSteps.toFloat()
}

fun EngineState.snapshot(now: Long): PlayerSnapshot {
    val works = steps.count { it is Step.Work }
    val doneWorks = steps.take(stepIndex).count { it is Step.Work }
    val current = currentStep
    val workNumber = when {
        current is Step.Work -> doneWorks + 1
        else -> (doneWorks + 1).coerceAtMost(works)
    }
    return PlayerSnapshot(
        phase = phase,
        step = current,
        stepIndex = stepIndex,
        totalSteps = steps.size,
        workNumber = workNumber,
        workTotal = works,
        remainingSec = if (isRunning) remainingSec(now) else null,
        elapsedInPhaseSec = if (isRunning) (elapsedInPhaseMs(now) / 1000L).toInt() else 0,
        totalElapsedSec = (totalElapsedMs(now) / 1000L).toInt(),
        paused = paused,
        nextWork = nextWork(),
        pendingCount = pendingCount,
    )
}
