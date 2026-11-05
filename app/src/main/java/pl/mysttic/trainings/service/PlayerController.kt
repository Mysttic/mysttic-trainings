package pl.mysttic.trainings.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.mysttic.trainings.data.repo.CoachRepository
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.data.settings.UserSettings
import pl.mysttic.trainings.domain.engine.EngineEffect
import pl.mysttic.trainings.domain.engine.EngineEvent
import pl.mysttic.trainings.domain.engine.EngineState
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.engine.WorkoutEngine
import pl.mysttic.trainings.domain.coach.ExerciseTuning
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.domain.stats.ExerciseDelta
import pl.mysttic.trainings.domain.stats.SessionStats
import pl.mysttic.trainings.domain.stats.SkippedExercise
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checkpoint starszy niż tyle (zegar ścienny) to trening odłożony, a nie przerwany na chwilę: wznowienie
 * doliczyłoby do sesji całą przerwę, więc Start zamyka taką sesję i zaczyna trening od nowa.
 */
internal const val STALE_CHECKPOINT_MS = 6 * 60 * 60 * 1000L

/** Czy z checkpointu zapisanego o [savedAtWall] wolno jeszcze wznowić trening o [nowWall]. */
internal fun checkpointIsStale(savedAtWall: Long, nowWall: Long): Boolean = nowWall - savedAtWall > STALE_CHECKPOINT_MS

/** Trening załadowany do playera: szablon, ćwiczenia, ustawienia z chwili startu i sesja w bazie. */
data class ActiveWorkout(
    val workout: Workout,
    val exercises: Map<String, Exercise>,
    val settings: UserSettings,
    val session: Session,
    val restoredFromCheckpoint: Boolean,
    /** Pamięć trenera z chwili startu (D-35); zmiana poziomu w trakcie przelicza plan z tą samą korektą. */
    val tuning: Map<String, ExerciseTuning> = emptyMap(),
)

data class SessionSummary(
    /** Potrzebne, żeby ekran podsumowania mógł dopisać RPE i notatkę do właśnie zamkniętej sesji. */
    val sessionId: String,
    val status: SessionStatus,
    val durationSec: Int,
    val setsDone: Int,
    val totalReps: Int,
    val totalSeconds: Int,
    /** Porównanie z poprzednim ukończonym wykonaniem tego treningu (null = pierwsze wykonanie). */
    val previousSetsDone: Int? = null,
    val deltaReps: Int? = null,
    val deltaSeconds: Int? = null,
    /** Wynik per ćwiczenie zestawiony z poprzednim wykonaniem tego treningu (M3). */
    val perExercise: List<ExerciseDelta> = emptyList(),
    /** Ćwiczenia pominięte w całości: bez wyniku, ale z opinią dla trenera (issue #22). */
    val skipped: List<SkippedExercise> = emptyList(),
)

/**
 * Jedyne miejsce, które trzyma stan silnika w procesie (D-16). Tyka nim [WorkoutService], UI tylko obserwuje
 * i wysyła zdarzenia. Wszystkie metody poza `suspend` wołać z głównego wątku; efekty (dźwięk, zapis, checkpoint)
 * są przetwarzane sekwencyjnie, więc kolejność zapisów w bazie = kolejność zdarzeń.
 */
@Singleton
class PlayerController @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val sessionRepo: SessionRepository,
    private val settingsRepo: SettingsRepository,
    private val coachRepo: CoachRepository,
    private val programRepo: ProgramRepository,
    private val cues: Cues,
    private val clock: MonotonicClock,
    private val health: HealthConnectExporter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val startMutex = Mutex()
    private val planner = WorkoutPlanner()

    private val _state = MutableStateFlow<EngineState?>(null)
    val state: StateFlow<EngineState?> = _state.asStateFlow()

    private val _active = MutableStateFlow<ActiveWorkout?>(null)
    val active: StateFlow<ActiveWorkout?> = _active.asStateFlow()

    private val _summary = MutableStateFlow<SessionSummary?>(null)
    val summary: StateFlow<SessionSummary?> = _summary.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val effects = Channel<Pair<EngineState, List<EngineEffect>>>(Channel.UNLIMITED)

    init {
        scope.launch { for ((s, fx) in effects) handle(s, fx) }
    }

    /** Trening trwa (także w pauzie). */
    val isActive: Boolean get() = _state.value?.isOver == false

    /** Czy ekran playera jest na wierzchu — poza nim klawisz głośności ma robić to, co zwykle. */
    private val _playerVisible = MutableStateFlow(false)

    fun setPlayerVisible(visible: Boolean) {
        _playerVisible.value = visible
    }

    /**
     * Potwierdzenie serii sprzętowym klawiszem głośności (M6). Zwraca `true`, jeśli zdarzenie zostało zużyte;
     * poza serią na powtórzenia oddajemy klawisz systemowi, żeby dało się ustawić głośność sygnałów.
     */
    fun doneFromVolumeKey(): Boolean {
        if (!_playerVisible.value) return false
        if (_state.value?.awaitingRepsDone != true) return false
        done()
        return true
    }

    private var pendingStarts = 0

    /** Trwa ładowanie treningu (stan jeszcze null) — serwis nie może się wtedy zatrzymać. */
    val isStarting: Boolean get() = pendingStarts > 0

    /** Wersja dla wywołań spoza korutyny (serwis); praca dzieje się w scope kontrolera, więc nie ginie z serwisem. */
    fun startAsync(workoutId: String) {
        scope.launch { start(workoutId) }
    }

    /**
     * Ładuje trening: wznawia sesję w toku z checkpointu, jeśli dotyczy tego treningu, inaczej zamyka ją jako porzuconą
     * i zaczyna nową. Jeśli ten sam trening już trwa, nic nie robi. Inny trwający trening zostaje porzucony bez podsumowania.
     */
    suspend fun start(workoutId: String) {
        pendingStarts++
        try {
            startMutex.withLock {
                val current = _state.value
                if (current != null && !current.isOver) {
                    if (_active.value?.workout?.id == workoutId) return@withLock
                    runCatching {
                        sessionRepo.finish(
                            current.sessionId, SessionStatus.ABANDONED,
                            plannedSets = current.plannedSets(), difficulty = current.difficulty,
                        )
                    }
                        .onFailure { Log.e(TAG, "finish previous failed", it) }
                }
                // Stan zerujemy przed ładowaniem (które suspenduje): inaczej porzucony trening tyka dalej i loguje serie
                // do zamkniętej sesji, a serwis widzący stary stan `isOver` zatrzymałby się tuż po starcie nowego treningu.
                _state.value = null
                _active.value = null
                _summary.value = null
                _error.value = null
                try {
                    load(workoutId)
                } catch (t: Throwable) {
                    Log.e(TAG, "Player load failed", t)
                    _state.value = null
                    _active.value = null
                    _error.value = t.message ?: "Błąd ładowania treningu"
                }
            }
        } finally {
            pendingStarts--
        }
    }

    private suspend fun load(workoutId: String) {
        val settings = settingsRepo.current()
        val workout = workoutRepo.getById(workoutId) ?: run {
            _state.value = null
            _active.value = null
            _error.value = "Nie znaleziono treningu."
            return
        }
        val exercises = exerciseRepo.getMap()
        val tuning = runCatching { coachRepo.all() }.getOrElse { Log.e(TAG, "coach tuning failed", it); emptyMap() }

        val inProgress = sessionRepo.inProgress()
        val checkpoint = inProgress?.let { sessionRepo.loadCheckpoint(it.id) }
        // Trening odłożony na wiele godzin nie wraca: Start zaczyna go od nowa, zamiast doliczać dobę do sesji.
        val stale = checkpoint?.takeIf { checkpointIsStale(it.savedAtWall, System.currentTimeMillis()) }
        if (inProgress != null && checkpoint != null && stale == null && inProgress.workoutId == workout.id) {
            val restored = WorkoutEngine.restore(
                saved = checkpoint.state,
                savedNow = checkpoint.savedAtElapsed,
                passedMs = System.currentTimeMillis() - checkpoint.savedAtWall,
                now = clock.now(),
            )
            _active.value = ActiveWorkout(workout, exercises, settings, inProgress, restoredFromCheckpoint = true, tuning = tuning)
            _state.value = restored
            return
        }
        if (inProgress != null) {
            // porzucona sesja innego treningu (albo bez checkpointu, albo odłożona): zamykamy, żeby nie wisiała jako „w toku”.
            // Odłożoną kończymy na chwili ostatniego zapisu, żeby w Historii nie trwała wiele godzin.
            sessionRepo.finish(
                inProgress.id, SessionStatus.ABANDONED,
                plannedSets = checkpoint?.state?.plannedSets(), difficulty = checkpoint?.state?.difficulty,
                endedAt = stale?.let { maxOf(it.savedAtWall, inProgress.startedAt) } ?: System.currentTimeMillis(),
            )
        }

        // Plan zapisujemy przy starcie (D-42): późniejsza edycja albo usunięcie planu nie przepisze historii.
        val programId = runCatching { programRepo.ownerOf(workout.id) }
            .onFailure { Log.e(TAG, "program owner failed", it) }.getOrNull()
        // Stały protokół (D-37, plan dosłowny D-42) nie skaluje się poziomem: sesję opisuje poziom, na którym
        // zbudowano plan-właściciel, a nie globalne ustawienie, które mogło się w międzyczasie zmienić (D-43).
        // Właściciel, nie aktywny plan: trening da się odpalić z planu wstrzymanego.
        val difficulty = if (workout.isFixed) {
            programId?.let { id -> runCatching { programRepo.byId(id) }.getOrNull()?.profile?.level }
                ?: settings.difficulty
        } else {
            settings.difficulty
        }
        val steps = planner.expand(workout, exercises, difficulty, settings.ownedEquipment, settings.countdownSec, tuning)
        val session = sessionRepo.startSession(workout, difficulty, programId)
        _active.value = ActiveWorkout(workout, exercises, settings, session, restoredFromCheckpoint = false, tuning = tuning)
        _state.value = EngineState(sessionId = session.id, difficulty = difficulty, steps = steps)
        dispatch(EngineEvent.Start(clock.now()))
    }

    /**
     * Po restarcie serwisu (START_STICKY): wznawia ostatnią sesję w toku, jeśli ma świeży checkpoint. Odłożonej
     * nie wznawia ani nie zaczyna od nowa sam z siebie: zamknie ją dopiero Start wybrany przez użytkownika.
     */
    suspend fun restoreLatest(): Boolean {
        if (isActive) return true
        val inProgress = sessionRepo.inProgress() ?: return false
        val workoutId = inProgress.workoutId ?: return false
        val checkpoint = sessionRepo.loadCheckpoint(inProgress.id) ?: return false
        if (checkpointIsStale(checkpoint.savedAtWall, System.currentTimeMillis())) return false
        start(workoutId)
        return isActive
    }

    fun tick(now: Long = clock.now()) {
        val s = _state.value ?: return
        if (s.isRunning && !s.paused) dispatch(EngineEvent.Tick(now))
    }

    fun done(actual: Int? = null) = dispatch(EngineEvent.Done(clock.now(), actual))
    /** Licznik AMRAP z ekranu: silnik zapamiętuje go i loguje, gdy czas minie. */
    fun count(value: Int) = dispatch(EngineEvent.Count(clock.now(), value))
    fun skip() = dispatch(EngineEvent.Skip(clock.now()))
    fun back() = dispatch(EngineEvent.Back(clock.now()))
    fun pause() = dispatch(EngineEvent.Pause(clock.now()))
    fun resume() = dispatch(EngineEvent.Resume(clock.now()))
    fun addRest(seconds: Int = 15) = dispatch(EngineEvent.AddRest(clock.now(), seconds))
    fun abort() = dispatch(EngineEvent.Abort(clock.now()))

    /** Zmiana poziomu w trakcie: pozostałe kroki przeliczone, wykonane zostają. Zapisuje też globalne ustawienie. */
    fun changeDifficulty(difficulty: Difficulty) {
        val a = _active.value ?: return
        val s = _state.value ?: return
        if (s.difficulty == difficulty) return
        val replanned =
            planner.expand(a.workout, a.exercises, difficulty, a.settings.ownedEquipment, a.settings.countdownSec, a.tuning)
        val merged = planner.merge(s.steps, s.stepIndex, replanned)
        dispatch(EngineEvent.Replan(clock.now(), difficulty, merged))
        scope.launch { settingsRepo.setDifficulty(difficulty) }
    }

    /** Po zamknięciu podsumowania: zwalnia stan, ale nigdy trwającego treningu. */
    fun clear() {
        if (isActive) return
        _state.value = null
        _active.value = null
        _summary.value = null
        _error.value = null
    }

    private fun dispatch(event: EngineEvent) {
        val current = _state.value ?: return
        // Pierwsza reakcja użytkownika kasuje flagę wznowienia: dalsze pauzy to zwykłe pauzy, nie „kontynuować od…?”.
        if (event !is EngineEvent.Tick) {
            _active.value?.takeIf { it.restoredFromCheckpoint }?.let { _active.value = it.copy(restoredFromCheckpoint = false) }
        }
        val (next, fx) = WorkoutEngine.reduce(current, event)
        _state.value = next
        if (fx.isNotEmpty()) effects.trySend(next to fx)
    }

    private suspend fun handle(newState: EngineState, effects: List<EngineEffect>) {
        val sessionId = newState.sessionId
        val settings = _active.value?.settings ?: UserSettings()
        for (effect in effects) {
            when (effect) {
                EngineEffect.BeepShort -> if (settings.soundEnabled) cues.beepShort()
                EngineEffect.BeepLong -> if (settings.soundEnabled) cues.beepLong()
                // Rytm oddechu: dźwięk, a bez dźwięku wibracja; nigdy oba naraz, bo to metronom, nie alarm.
                EngineEffect.Cadence -> if (settings.soundEnabled) cues.tick() else if (settings.vibrationEnabled) cues.vibrate()
                EngineEffect.Vibrate -> if (settings.vibrationEnabled) cues.vibrate()
                is EngineEffect.Speak -> if (settings.ttsEnabled) cues.speak(effect.text)
                is EngineEffect.LogSet -> runCatching { sessionRepo.logSet(sessionId, effect.result) }
                    .onFailure { Log.e(TAG, "logSet failed", it) }
                EngineEffect.Checkpoint -> runCatching {
                    if (newState.isOver) sessionRepo.clearCheckpoint(sessionId)
                    else sessionRepo.saveCheckpoint(newState, System.currentTimeMillis(), clock.now())
                }.onFailure { Log.e(TAG, "checkpoint failed", it) }
                // Poziom i liczba serii z migawki, która niosła efekt: zmiana poziomu w trakcie trafia do historii
                // i do raportu planu (D-42), a trening startujący w międzyczasie nie podmieni stanu (D-43).
                is EngineEffect.Finished -> finish(newState, effect.status)
            }
        }
    }

    private suspend fun finish(ended: EngineState, status: SessionStatus) {
        val sessionId = ended.sessionId
        // Liczba serii i poziom z końca treningu: zmiana poziomu w trakcie przelicza kroki, raport planu liczy od nich.
        runCatching { sessionRepo.finish(sessionId, status, plannedSets = ended.plannedSets(), difficulty = ended.difficulty) }
            .onFailure { Log.e(TAG, "finish failed", it) }
        // Trener (D-35, C1-5): podmiana „Nie chcę” pomija trening w toku, więc po jego końcu dogania go tutaj.
        runCatching { coachRepo.replaceExcludedInActiveProgram() }.onFailure { Log.e(TAG, "coach catch-up failed", it) }
        val session = _active.value?.session?.takeIf { it.id == sessionId } ?: return
        val logs = runCatching { sessionRepo.setLogs(sessionId) }.getOrDefault(emptyList())
        val previous = session.workoutId?.let { sessionRepo.previousDone(it, session.startedAt) }
        val previousLogs = previous?.let { sessionRepo.setLogs(it.id) }.orEmpty()
        val durationSec = ((System.currentTimeMillis() - session.startedAt) / 1000L).toInt()
        val summary = SessionSummary(
            sessionId = sessionId,
            status = status,
            durationSec = durationSec,
            setsDone = logs.size,
            totalReps = logs.reps(),
            totalSeconds = logs.seconds(),
            previousSetsDone = previous?.let { previousLogs.size },
            deltaReps = previous?.let { logs.reps() - previousLogs.reps() },
            deltaSeconds = previous?.let { logs.seconds() - previousLogs.seconds() },
            perExercise = SessionStats.perExercise(logs, previousLogs),
            skipped = SessionStats.skipped(ended, logs),
        )
        // W międzyczasie mógł wystartować inny trening: podsumowanie tylko dla wciąż aktualnej sesji.
        if (_active.value?.session?.id == sessionId) _summary.value = summary

        // Health Connect (M6): opcjonalny, jednostronny zapis ukończonego treningu; błąd nie rusza podsumowania.
        if (status == SessionStatus.DONE && runCatching { settingsRepo.current().healthConnectEnabled }.getOrDefault(false)) {
            val saved = runCatching { sessionRepo.getSession(sessionId) }.getOrNull()
            if (saved != null) runCatching { health.export(saved, _active.value?.workout) }
                .onFailure { Log.e(TAG, "health export failed", it) }
        }
    }

    /** Kroki pracy po ostatnim przeliczeniu: liczba serii planowanych w raporcie planu (D-42). */
    private fun EngineState.plannedSets(): Int = steps.count { it is Step.Work }

    private fun List<SetLog>.reps() = filter { it.targetKind == TargetKind.REPS || it.targetKind == TargetKind.AMRAP }.sumOf { it.actualValue }
    private fun List<SetLog>.seconds() = filter { it.targetKind == TargetKind.SECONDS }.sumOf { it.actualValue }

    companion object {
        private const val TAG = "PlayerController"
    }
}
