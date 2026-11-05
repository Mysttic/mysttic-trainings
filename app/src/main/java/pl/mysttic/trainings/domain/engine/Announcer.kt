package pl.mysttic.trainings.domain.engine

import pl.mysttic.trainings.domain.model.TargetKind

/** Polskie komunikaty dla TTS i UI. Czysty Kotlin. */
object Announcer {

    fun plural(n: Int, one: String, few: String, many: String): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            n == 1 -> one
            mod10 in 2..4 && mod100 !in 12..14 -> few
            else -> many
        }
    }

    fun reps(n: Int): String = "$n ${plural(n, "powtórzenie", "powtórzenia", "powtórzeń")}"

    fun seconds(n: Int): String = "$n ${plural(n, "sekunda", "sekundy", "sekund")}"

    fun breaths(n: Int): String = "$n ${plural(n, "oddech", "oddechy", "oddechów")}"

    /** Cel kroku: krok bez limitu nie ma liczby, krok z rytmem liczy oddechy zamiast sekund (D-37). */
    fun target(step: Step.Work): String = when (step.targetKind) {
        TargetKind.REPS -> reps(step.targetValue)
        TargetKind.SECONDS -> when {
            step.openEnded -> "jak najdłużej"
            step.cadenceCount > 0 -> "${breaths(step.cadenceCount)} w rytmie"
            else -> seconds(step.targetValue)
        }
        TargetKind.AMRAP -> "ile dasz radę w ${seconds(step.targetValue)}"
    }

    /** Czytane na starcie przerwy: „Następnie: pompki, 12 powtórzeń”. */
    fun next(step: Step.Work): String = "Następnie: ${step.exerciseName}, ${target(step)}"

    /** Czytane na starcie pracy; obwód dostaje numer rundy: „Pajacyki, 20 sekund, runda 1 z 8”. */
    fun start(step: Step.Work): String = buildString {
        append(step.exerciseName).append(", ").append(target(step))
        if (step.roundsInBlock > 1) append(", runda ${step.round} z ${step.roundsInBlock}")
    }

    /** Czytane na przerwie między rundami, gdy następna pozycja to to samo ćwiczenie w kolejnej rundzie. */
    fun round(step: Step.Work): String = "Runda ${step.round} z ${step.roundsInBlock}"

    fun rest(step: Step.Rest): String = "Przerwa, ${seconds(step.seconds)}"

    fun formatClock(totalSec: Int): String {
        val s = totalSec.coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return "%d:%02d".format(m, r)
    }
}
