package pl.mysttic.trainings.service

import pl.mysttic.trainings.domain.engine.Announcer
import pl.mysttic.trainings.domain.engine.EngineState
import pl.mysttic.trainings.domain.engine.Phase
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.model.TargetKind

/**
 * Treść powiadomienia playera policzona ze stanu silnika. Czysty Kotlin (testowalny bez Androida):
 * `countDownTo` / `countUpFrom` to chwile na zegarze ściennym dla chronometru powiadomienia.
 */
data class NotificationContent(
    val title: String,
    val text: String,
    /** Chronometr odlicza w dół do tej chwili (ms, wall clock); null = bez odliczania. */
    val countDownTo: Long? = null,
    /** Chronometr liczy w górę od tej chwili (ms, wall clock); null = bez licznika. */
    val countUpFrom: Long? = null,
    val showPause: Boolean = false,
    val showResume: Boolean = false,
    val showDone: Boolean = false,
    val showAddRest: Boolean = false,
    val showSkip: Boolean = false,
) {
    companion object {
        /** `nowMono` = zegar silnika, `nowWall` = `System.currentTimeMillis()` z tej samej chwili. */
        fun from(state: EngineState, nowMono: Long, nowWall: Long): NotificationContent {
            when (state.phase) {
                Phase.IDLE -> return NotificationContent("Przygotowanie treningu", "")
                Phase.FINISHED -> return NotificationContent("Trening ukończony", "")
                Phase.ABORTED -> return NotificationContent("Trening przerwany", "")
                Phase.RUNNING -> Unit
            }
            val step = state.currentStep ?: return NotificationContent("Trening", "")
            val paused = state.paused
            val next = state.nextWork()?.let { Announcer.next(it) } ?: "Ostatni krok"
            val (title, text) = when (step) {
                is Step.Countdown -> "Przygotuj się" to next
                is Step.Rest -> "Przerwa" to next
                is Step.Work -> workTitle(step) to Announcer.target(step)
            }
            val elapsed = state.elapsedInPhaseMs(nowMono)
            val duration = step.durationSec
            val countDownTo = if (!paused && duration != null) nowWall + (duration * 1000L - elapsed).coerceAtLeast(0L) else null
            val countUpFrom = if (!paused && duration == null) nowWall - elapsed else null
            return NotificationContent(
                title = if (paused) "$title · pauza" else title,
                text = text,
                countDownTo = countDownTo,
                countUpFrom = countUpFrom,
                showPause = !paused,
                showResume = paused,
                // „Zrobione” kończy serię na powtórzenia i krok bez limitu; kroki na czas kończą się same.
                showDone = step is Step.Work && (step.targetKind == TargetKind.REPS || step.openEnded),
                showAddRest = step is Step.Rest,
                showSkip = true,
            )
        }

        /** Obwód liczy rundy („runda 2 z 8”), serie proste numer serii. */
        private fun workTitle(step: Step.Work): String =
            if (step.roundsInBlock > 1) "${step.exerciseName}, runda ${step.round} z ${step.roundsInBlock}"
            else "${step.exerciseName}, seria ${step.setIndex} z ${step.setsInItem}"
    }
}
