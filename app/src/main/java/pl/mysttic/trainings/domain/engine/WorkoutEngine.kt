package pl.mysttic.trainings.domain.engine

import kotlinx.serialization.Serializable
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.TargetKind

enum class Phase { IDLE, RUNNING, FINISHED, ABORTED }

/**
 * Stan silnika. Wszystkie czasy w milisekundach z jednego monotonicznego zegara
 * (na Androidzie `SystemClock.elapsedRealtime()`); zdarzenia niosą własne `now`, więc
 * silnik nie ma zależności od zegara i jest deterministyczny w testach.
 *
 * Czas fazy = (pauseStartedAt ?: now) − phaseStartedAt − pausedElapsedMs.
 */
@Serializable
data class EngineState(
    val sessionId: String,
    val difficulty: Difficulty,
    val steps: List<Step>,
    val stepIndex: Int = 0,
    val phase: Phase = Phase.IDLE,
    val phaseStartedAt: Long = 0L,
    val pausedElapsedMs: Long = 0L,
    val pauseStartedAt: Long? = null,
    val workoutStartedAt: Long = 0L,
    val totalPausedMs: Long = 0L,
    /** Ostatnia sekunda, dla której zagrał krótki beep (3, 2, 1) — chroni przed duplikatami. */
    val lastBeepSecond: Int = Int.MAX_VALUE,
    /** Numer ostatniego sygnału rytmu (oddechu), który zagrał w bieżącym kroku; 0 = jeszcze żaden. */
    val lastCueIndex: Int = 0,
    /** Licznik AMRAP wpisany na ekranie w trakcie kroku; null = nic nie wpisano. */
    val pendingCount: Int? = null,
) {
    val currentStep: Step? get() = steps.getOrNull(stepIndex)
    val paused: Boolean get() = pauseStartedAt != null
    val isRunning: Boolean get() = phase == Phase.RUNNING

    /**
     * Trening stoi na kroku, który kończy użytkownik (seria na powtórzenia albo krok bez limitu), i czeka
     * na potwierdzenie. Tylko wtedy przycisk głośności ma znaczyć „Zrobione” (M6): kroki na czas kończą się same,
     * a w pauzie klawisz ma zmieniać głośność.
     */
    val awaitingRepsDone: Boolean
        get() = isRunning && !paused && (currentStep as? Step.Work)?.let { it.durationSec == null } == true
    val isOver: Boolean get() = phase == Phase.FINISHED || phase == Phase.ABORTED

    fun elapsedInPhaseMs(now: Long): Long =
        ((pauseStartedAt ?: now) - phaseStartedAt - pausedElapsedMs).coerceAtLeast(0L)

    /** Pozostały czas kroku (zaokrąglony w górę), null dla kroków na powtórzenia. */
    fun remainingSec(now: Long): Int? {
        val dur = currentStep?.durationSec ?: return null
        val remainingMs = dur * 1000L - elapsedInPhaseMs(now)
        return ceilSec(remainingMs)
    }

    fun totalElapsedMs(now: Long): Long {
        if (phase == Phase.IDLE) return 0L
        val end = pauseStartedAt ?: now
        return (end - workoutStartedAt - totalPausedMs).coerceAtLeast(0L)
    }

    /** Następny krok pracy po bieżącym (do „Następnie: …”). */
    fun nextWork(): Step.Work? = steps.drop(stepIndex + 1).firstOrNull { it is Step.Work } as? Step.Work

    /**
     * Przesuwa wszystkie znaczniki czasu o `deltaMs` — używane przy odtwarzaniu checkpointu,
     * gdy zegar monotoniczny zaczął od nowa (reboot) albo minął realny czas.
     */
    fun shifted(deltaMs: Long): EngineState = copy(
        phaseStartedAt = phaseStartedAt + deltaMs,
        workoutStartedAt = workoutStartedAt + deltaMs,
        pauseStartedAt = pauseStartedAt?.plus(deltaMs),
    )

    companion object {
        fun ceilSec(ms: Long): Int = if (ms <= 0L) 0 else ((ms + 999L) / 1000L).toInt()
    }
}

sealed interface EngineEvent {
    val now: Long

    data class Start(override val now: Long) : EngineEvent
    data class Tick(override val now: Long) : EngineEvent
    data class Pause(override val now: Long) : EngineEvent
    data class Resume(override val now: Long) : EngineEvent
    /** Przejście dalej bez logowania serii. */
    data class Skip(override val now: Long) : EngineEvent
    /** Powrót do poprzedniego kroku (bez cofania zapisanych serii). */
    data class Back(override val now: Long) : EngineEvent
    /** „Zrobione” — dla REPS z liczbą powtórzeń (null = cel), dla SECONDS/AMRAP z liczbą (AMRAP) albo null. */
    data class Done(override val now: Long, val actual: Int? = null) : EngineEvent
    data class AddRest(override val now: Long, val seconds: Int = 15) : EngineEvent
    data class Abort(override val now: Long) : EngineEvent
    /** Nowy plan (po zmianie poziomu) — już scalony przez [WorkoutPlanner.merge]. */
    data class Replan(override val now: Long, val difficulty: Difficulty, val steps: List<Step>) : EngineEvent
    /**
     * Licznik AMRAP z ekranu: ile już zrobiono w bieżącym kroku. Bez efektów (także bez checkpointu),
     * żeby nie pisać do bazy przy każdym tapnięciu; wartość trafia do logu, gdy czas minie.
     */
    data class Count(override val now: Long, val value: Int) : EngineEvent
}

data class SetResult(val step: Step.Work, val actualValue: Int, val completedAt: Long)

sealed interface EngineEffect {
    data object BeepShort : EngineEffect
    data object BeepLong : EngineEffect
    /** Sygnał rytmu (oddech w rytmie): krótki dźwięk co `cadenceSec`, bez ściszania cudzej muzyki. */
    data object Cadence : EngineEffect
    data object Vibrate : EngineEffect
    data class Speak(val text: String) : EngineEffect
    data class LogSet(val result: SetResult) : EngineEffect
    data object Checkpoint : EngineEffect
    data class Finished(val status: SessionStatus) : EngineEffect
}

/**
 * Maszyna stanów playera: czysta funkcja `reduce(state, event) -> (state, effects)`.
 * Przejścia faz liczone z timestampów, tick służy tylko do sprawdzenia, czy faza minęła.
 */
object WorkoutEngine {

    /** Jeśli tick spóźnił się mniej niż tyle, kolejna faza startuje dokładnie na granicy (brak dryfu). */
    const val LAG_TOLERANCE_MS = 2_000L

    /** Po odtworzeniu checkpointu: jeśli faza jest przeterminowana o więcej, wracamy w pauzie. */
    const val RESTORE_OVERDUE_PAUSE_MS = 30_000L

    /**
     * Seria na powtórzenia nie ma końca, więc po odtworzeniu checkpointu liczymy, ile trwa: dłużej niż tyle
     * znaczy, że nikt jej nie robił (ubity proces, trening odłożony), i trening wraca w pauzie z pytaniem.
     */
    const val RESTORE_REPS_PAUSE_MS = 5 * 60_000L

    /**
     * Bezpiecznik kroku bez limitu: trwa najwyżej tyle razy `targetValue` sekund. Potem trening staje w pauzie
     * (odłożony telefon nie liczy godzinami), a użytkownik decyduje, czy kontynuować.
     */
    const val OPEN_ENDED_CAP = 10

    fun reduce(state: EngineState, event: EngineEvent): Pair<EngineState, List<EngineEffect>> {
        if (state.isOver) return state to emptyList()
        return when (event) {
            is EngineEvent.Start -> start(state, event.now)
            is EngineEvent.Tick -> tick(state, event.now)
            is EngineEvent.Pause -> pause(state, event.now)
            is EngineEvent.Resume -> resume(state, event.now)
            is EngineEvent.Skip -> if (!state.isRunning) state to emptyList() else advance(state, event.now, log = null)
            is EngineEvent.Back -> back(state, event.now)
            is EngineEvent.Done -> done(state, event.now, event.actual)
            is EngineEvent.AddRest -> addRest(state, event.seconds)
            is EngineEvent.Abort -> state.copy(phase = Phase.ABORTED, pauseStartedAt = null) to
                listOf(EngineEffect.Finished(SessionStatus.ABANDONED), EngineEffect.Checkpoint)
            is EngineEvent.Replan -> state.copy(steps = event.steps, difficulty = event.difficulty) to
                listOf(EngineEffect.Checkpoint)
            is EngineEvent.Count -> count(state, event.value)
        }
    }

    /**
     * Odtworzenie z checkpointu. `savedNow` — wartość zegara przy zapisie, `passedMs` — realny czas,
     * jaki minął od zapisu (z wall clock), `now` — bieżący zegar monotoniczny.
     * Jeśli w międzyczasie faza minęła o więcej niż [RESTORE_OVERDUE_PAUSE_MS], trening wraca w pauzie —
     * użytkownik decyduje, czy kontynuować od tego miejsca.
     */
    fun restore(saved: EngineState, savedNow: Long, passedMs: Long, now: Long): EngineState {
        if (!saved.isRunning) return saved
        val delta = (now - passedMs.coerceAtLeast(0L)) - savedNow
        val shifted = saved.shifted(delta)
        if (shifted.paused) return shifted
        val step = shifted.currentStep ?: return shifted
        val dur = step.durationSec
        if (dur == null) {
            val work = step as? Step.Work ?: return shifted
            if (!work.openEnded) {
                // Seria na powtórzenia: pauza zaczyna się w chwili zapisu, więc czas bez nikogo przy telefonie
                // nie wchodzi ani do czasu serii, ani do zegara treningu.
                if (shifted.elapsedInPhaseMs(now) <= RESTORE_REPS_PAUSE_MS) return shifted
                return shifted.copy(pauseStartedAt = (now - passedMs.coerceAtLeast(0L)).coerceAtLeast(shifted.phaseStartedAt))
            }
            // Krok bez limitu: sam bezpiecznik jest granicą, po której wracamy w pauzie.
            val cap = openEndedCapMs(work)
            return if (cap > 0L && shifted.elapsedInPhaseMs(now) > cap) shifted.copy(pauseStartedAt = now) else shifted
        }
        val overdueMs = shifted.elapsedInPhaseMs(now) - dur * 1000L
        return if (overdueMs > RESTORE_OVERDUE_PAUSE_MS) shifted.copy(pauseStartedAt = now) else shifted
    }

    /** Górna granica czasu kroku bez limitu w ms; 0 = brak celu orientacyjnego, więc i brak bezpiecznika. */
    private fun openEndedCapMs(work: Step.Work): Long = work.targetValue.coerceAtLeast(0) * OPEN_ENDED_CAP * 1000L

    private fun start(state: EngineState, now: Long): Pair<EngineState, List<EngineEffect>> {
        if (state.phase != Phase.IDLE) return state to emptyList()
        if (state.steps.isEmpty()) {
            return state.copy(phase = Phase.FINISHED) to listOf(EngineEffect.Finished(SessionStatus.DONE))
        }
        val started = state.copy(
            phase = Phase.RUNNING,
            stepIndex = 0,
            phaseStartedAt = now,
            workoutStartedAt = now,
            pausedElapsedMs = 0L,
            totalPausedMs = 0L,
            pauseStartedAt = null,
            lastBeepSecond = Int.MAX_VALUE,
            lastCueIndex = 0,
            pendingCount = null,
        )
        val effects = ArrayList<EngineEffect>()
        val first = started.currentStep
        if (first is Step.Work) effects += EngineEffect.Speak(Announcer.start(first))
        effects += EngineEffect.Checkpoint
        return started to effects
    }

    private fun tick(state: EngineState, now: Long): Pair<EngineState, List<EngineEffect>> {
        if (!state.isRunning || state.paused) return state to emptyList()
        val step = state.currentStep ?: return state to emptyList()
        val elapsed = state.elapsedInPhaseMs(now)
        val dur = step.durationSec
        if (dur == null) {
            // Krok bez limitu: czas leci w górę i kończy go użytkownik; po bezpieczniku trening staje w pauzie.
            val cap = (step as? Step.Work)?.takeIf { it.openEnded }?.let { openEndedCapMs(it) } ?: return state to emptyList()
            return if (cap > 0L && elapsed >= cap) pause(state, now) else state to emptyList()
        }
        val remainingMs = dur * 1000L - elapsed
        if (remainingMs <= 0L) {
            val log = (step as? Step.Work)?.let { work ->
                val actual = if (work.targetKind == TargetKind.AMRAP) state.pendingCount ?: 0 else work.targetValue
                SetResult(work, actual, now)
            }
            val boundary = state.phaseStartedAt + state.pausedElapsedMs + dur * 1000L
            val nextStart = if (now - boundary <= LAG_TOLERANCE_MS) boundary else now
            return advance(state, nextStart, log)
        }
        val cadenceSec = (step as? Step.Work)?.cadenceSec
        if (cadenceSec != null && cadenceSec > 0) {
            // Krok z rytmem: sygnał na każdej pełnej wielokrotności `cadenceSec`, raz na numer, bez odliczania 3-2-1.
            // Spóźniony tick daje jeden sygnał, nie serię zaległych.
            val idx = (elapsed / (cadenceSec * 1000L)).toInt()
            return if (idx > state.lastCueIndex) state.copy(lastCueIndex = idx) to listOf(EngineEffect.Cadence)
            else state to emptyList()
        }
        val remainingSec = EngineState.ceilSec(remainingMs)
        if (remainingSec in 1..3 && remainingSec < state.lastBeepSecond) {
            return state.copy(lastBeepSecond = remainingSec) to listOf(EngineEffect.BeepShort)
        }
        return state to emptyList()
    }

    private fun pause(state: EngineState, now: Long): Pair<EngineState, List<EngineEffect>> {
        if (!state.isRunning || state.paused) return state to emptyList()
        return state.copy(pauseStartedAt = now) to listOf(EngineEffect.Checkpoint)
    }

    private fun resume(state: EngineState, now: Long): Pair<EngineState, List<EngineEffect>> {
        val pausedAt = state.pauseStartedAt ?: return state to emptyList()
        val pausedFor = (now - pausedAt).coerceAtLeast(0L)
        return state.copy(
            pauseStartedAt = null,
            pausedElapsedMs = state.pausedElapsedMs + pausedFor,
            totalPausedMs = state.totalPausedMs + pausedFor,
        ) to listOf(EngineEffect.Checkpoint)
    }

    private fun done(state: EngineState, now: Long, actual: Int?): Pair<EngineState, List<EngineEffect>> {
        if (!state.isRunning) return state to emptyList()
        val step = state.currentStep ?: return state to emptyList()
        val log = (step as? Step.Work)?.let { work ->
            val value = when (work.targetKind) {
                TargetKind.REPS -> actual ?: work.targetValue
                TargetKind.SECONDS -> actual ?: run {
                    val elapsedSec = (state.elapsedInPhaseMs(now) / 1000L).toInt()
                    val limit = if (work.openEnded) work.targetValue * OPEN_ENDED_CAP else work.targetValue
                    elapsedSec.coerceIn(0, limit.coerceAtLeast(0))
                }
                TargetKind.AMRAP -> actual ?: state.pendingCount ?: 0
            }
            SetResult(work, value, now)
        }
        return advance(state, now, log)
    }

    private fun back(state: EngineState, now: Long): Pair<EngineState, List<EngineEffect>> {
        if (!state.isRunning) return state to emptyList()
        val target = (state.stepIndex - 1).coerceAtLeast(0)
        val moved = state.copy(
            stepIndex = target,
            phaseStartedAt = now,
            pausedElapsedMs = 0L,
            pauseStartedAt = null,
            lastBeepSecond = Int.MAX_VALUE,
            lastCueIndex = 0,
            pendingCount = null,
        )
        return moved to listOf(EngineEffect.Checkpoint)
    }

    /** Licznik AMRAP z ekranu; poza biegnącym treningiem nie ma czego liczyć. */
    private fun count(state: EngineState, value: Int): Pair<EngineState, List<EngineEffect>> {
        if (!state.isRunning) return state to emptyList()
        return state.copy(pendingCount = value.coerceAtLeast(0)) to emptyList()
    }

    private fun addRest(state: EngineState, seconds: Int): Pair<EngineState, List<EngineEffect>> {
        val step = state.currentStep as? Step.Rest ?: return state to emptyList()
        val updated = state.steps.toMutableList()
        updated[state.stepIndex] = step.copy(seconds = step.seconds + seconds)
        return state.copy(steps = updated, lastBeepSecond = Int.MAX_VALUE) to listOf(EngineEffect.Checkpoint)
    }

    /** Przejście do następnego kroku (albo koniec). `nextStart` to moment startu nowej fazy. */
    private fun advance(state: EngineState, nextStart: Long, log: SetResult?): Pair<EngineState, List<EngineEffect>> {
        val effects = ArrayList<EngineEffect>()
        if (log != null) effects += EngineEffect.LogSet(log)
        var steps = state.steps
        var nextIndex = state.stepIndex + 1
        val pendingRest = steps.getOrNull(nextIndex) as? Step.Rest
        val window = pendingRest?.windowSec
        if (window != null) {
            // Okno EMOM: praca i przerwa mają razem trwać `windowSec`, więc przerwa to reszta minuty.
            // Czas pracy bez pauz — `elapsedInPhaseMs` odejmuje `pausedElapsedMs`.
            val fillMs = window * 1000L - state.elapsedInPhaseMs(nextStart)
            val fill = ((fillMs + 500L) / 1000L).toInt()
            if (fill <= 0) {
                nextIndex++ // praca zjadła całe okno: kolejna runda rusza od razu, bez pustej przerwy
            } else if (fill != pendingRest.seconds) {
                steps = steps.toMutableList().also { it[nextIndex] = pendingRest.copy(seconds = fill) }
            }
        }
        if (nextIndex >= steps.size) {
            effects += EngineEffect.BeepLong
            effects += EngineEffect.Vibrate
            effects += EngineEffect.Finished(SessionStatus.DONE)
            effects += EngineEffect.Checkpoint
            return state.copy(phase = Phase.FINISHED, pauseStartedAt = null) to effects
        }
        val next = state.copy(
            steps = steps,
            stepIndex = nextIndex,
            phaseStartedAt = nextStart,
            pausedElapsedMs = 0L,
            pauseStartedAt = null,
            lastBeepSecond = Int.MAX_VALUE,
            lastCueIndex = 0,
            pendingCount = null,
        )
        effects += EngineEffect.BeepLong
        effects += EngineEffect.Vibrate
        val previousWork = state.currentStep as? Step.Work
        when (val step = next.currentStep) {
            is Step.Rest -> next.nextWork()?.let { nw ->
                effects += EngineEffect.Speak(
                    if (sameItemNextRound(previousWork, nw)) Announcer.round(nw) else Announcer.next(nw),
                )
            }
            // Bez przerwy (pominięte okno EMOM) i tak należy się numer rundy, a nie pełna zapowiedź ćwiczenia.
            is Step.Work -> effects += EngineEffect.Speak(
                if (sameItemNextRound(previousWork, step)) Announcer.round(step) else Announcer.start(step),
            )
            else -> Unit
        }
        effects += EngineEffect.Checkpoint
        return next to effects
    }

    /** Ta sama pozycja obwodu w kolejnej rundzie: zamiast powtarzać nazwę ćwiczenia mówimy numer rundy. */
    private fun sameItemNextRound(previous: Step.Work?, next: Step.Work): Boolean =
        previous != null && next.roundsInBlock > 1 &&
            next.blockIndex == previous.blockIndex && next.itemIndex == previous.itemIndex &&
            next.round == previous.round + 1
}
