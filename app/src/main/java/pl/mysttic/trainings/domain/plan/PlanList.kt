package pl.mysttic.trainings.domain.plan

import pl.mysttic.trainings.domain.model.Session

/** Stan planu na liście (D-42); liczony, nie zapisywany. */
enum class PlanStatus(val labelPl: String) {
    ACTIVE("Aktywny"),
    NOT_STARTED("Nierozpoczęty"),
    PAUSED("Wstrzymany"),
    COMPLETE("Zakończony"),
    EMPTY("Bez treningów"),
}

data class PlanListRow(
    val id: String,
    val name: String,
    val source: PlanSource,
    val author: String,
    val status: PlanStatus,
    val done: Int,
    val total: Int,
    val lastDoneAt: Long?,
)

object PlanList {

    fun row(program: Program, sessions: List<Session>): PlanListRow {
        val done = PlanSessions.done(sessions, program)
        val progress = PlanProgress.resolve(program.days, done.mapNotNull { it.workoutId })
        val status = when {
            progress.totalCount == 0 -> PlanStatus.EMPTY
            progress.isComplete -> PlanStatus.COMPLETE
            program.active -> PlanStatus.ACTIVE
            done.isEmpty() -> PlanStatus.NOT_STARTED
            else -> PlanStatus.PAUSED
        }
        return PlanListRow(
            id = program.id,
            name = program.name,
            source = program.source,
            author = program.author,
            status = status,
            done = progress.doneCount,
            total = progress.totalCount,
            lastDoneAt = done.lastOrNull()?.startedAt,
        )
    }

    /** Aktywny na górze, potem czekające (ostatnio używane wyżej), na końcu zakończone. */
    fun rows(programs: List<Program>, sessions: List<Session>): List<PlanListRow> {
        val byProgram = programs.associate { it.id to row(it, sessions) }
        return programs.sortedWith(
            compareBy<Program> { order(byProgram.getValue(it.id).status) }
                .thenByDescending { byProgram.getValue(it.id).lastDoneAt ?: it.updatedAt }
                .thenBy { it.id },
        ).map { byProgram.getValue(it.id) }
    }

    private fun order(status: PlanStatus): Int = when (status) {
        PlanStatus.ACTIVE -> 0
        PlanStatus.PAUSED -> 1
        PlanStatus.NOT_STARTED -> 2
        PlanStatus.EMPTY -> 3
        PlanStatus.COMPLETE -> 4
    }
}
