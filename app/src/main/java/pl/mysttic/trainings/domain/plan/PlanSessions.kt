package pl.mysttic.trainings.domain.plan

import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus

/**
 * Przynależność sesji do planu (D-42) w jednym miejscu. Od bazy v4 sesja zapisuje `programId` przy starcie;
 * starsze sesje (i te, których plan wtedy nie znano) rozliczamy dawną regułą: ukończona po starcie planu
 * sesja treningu, który stoi w dniach planu.
 */
object PlanSessions {

    fun belongsTo(session: Session, program: Program): Boolean {
        session.programId?.let { return it == program.id }
        val workoutId = session.workoutId ?: return false
        return session.startedAt >= program.startedAt && program.days.any { it.workoutId == workoutId }
    }

    /** Ukończone sesje planu w kolejności wykonania. */
    fun done(sessions: List<Session>, program: Program): List<Session> =
        sessions.filter { it.status == SessionStatus.DONE && belongsTo(it, program) }.sortedBy { it.startedAt }

    fun completedWorkoutIds(sessions: List<Session>, program: Program): List<String> =
        done(sessions, program).mapNotNull { it.workoutId }

    /** Stan kolejki (D-21) liczony z sesji tego planu. */
    fun progress(sessions: List<Session>, program: Program): ProgramProgress =
        PlanProgress.resolve(program.days, completedWorkoutIds(sessions, program))

    /**
     * Właściciel treningu po prefiksie id: treningi planu mają id `"$programId-…"` od pierwszego kreatora,
     * a każda kopia planu nadaje nowe. Najdłuższy pasujący prefiks wygrywa, więc wynik nie zależy od kolejności.
     */
    fun ownerOf(workoutId: String, programIds: Collection<String>): String? =
        programIds.filter { workoutId.startsWith("$it-") }.maxByOrNull { it.length }
}
