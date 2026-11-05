package pl.mysttic.trainings.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout

class WorkoutEngineTest {

    private val planner = WorkoutPlanner()

    private fun initial(workout: pl.mysttic.trainings.domain.model.Workout = TestFixtures.setsWorkout(), difficulty: Difficulty = Difficulty.INTERMEDIATE) =
        EngineState(
            sessionId = "s1",
            difficulty = difficulty,
            steps = planner.expand(workout, TestFixtures.exercises, difficulty, emptySet()),
        )

    private fun EngineState.apply(event: EngineEvent): Pair<EngineState, List<EngineEffect>> = WorkoutEngine.reduce(this, event)

    /** EMOM po odliczaniu: stan stoi na pierwszym kroku pracy. */
    private fun emomRunning(windowSec: Int = 60): EngineState {
        var (s, _) = initial(TestFixtures.emomWorkout(windowSec)).apply(EngineEvent.Start(0L))
        var t = 0L
        while (s.currentStep is Step.Countdown) {
            t += 250L
            s = s.apply(EngineEvent.Tick(t)).first
        }
        return s
    }

    @Test
    fun `okno EMOM daje przerwe rowna oknu minus czas pracy`() {
        val s = emomRunning()
        val start = s.phaseStartedAt

        val (afterFast, _) = s.apply(EngineEvent.Done(start + 20_000L))
        assertEquals(40, (afterFast.currentStep as Step.Rest).seconds)

        val (afterSlow, _) = s.apply(EngineEvent.Done(start + 47_400L))
        assertEquals(13, (afterSlow.currentStep as Step.Rest).seconds)
    }

    @Test
    fun `pauza nie zjada okna EMOM`() {
        val s = emomRunning()
        val start = s.phaseStartedAt
        val paused = s.apply(EngineEvent.Pause(start + 10_000L)).first
        val resumed = paused.apply(EngineEvent.Resume(start + 40_000L)).first

        val (after, _) = resumed.apply(EngineEvent.Done(start + 50_000L))

        assertEquals(40, (after.currentStep as Step.Rest).seconds) // praca trwała 20 s, pauza się nie liczy
    }

    @Test
    fun `praca dluzsza niz okno pomija przerwe i zapowiada runde`() {
        val s = emomRunning()
        val (after, fx) = s.apply(EngineEvent.Done(s.phaseStartedAt + 70_000L))

        val work = after.currentStep as Step.Work
        assertEquals(2, work.round)
        assertTrue(fx.any { it is EngineEffect.Speak && it.text.contains("Runda 2") })
    }

    @Test
    fun `dodaj 15 s dziala na przerwie okna`() {
        val rest = emomRunning().apply(EngineEvent.Done(20_000L)).first
        val longer = rest.apply(EngineEvent.AddRest(20_000L, 15)).first
        assertEquals((rest.currentStep as Step.Rest).seconds + 15, (longer.currentStep as Step.Rest).seconds)
        assertEquals(60, (longer.currentStep as Step.Rest).windowSec)
    }

    @Test
    fun `start enters running on countdown`() {
        val (s, fx) = initial().apply(EngineEvent.Start(1_000L))
        assertEquals(Phase.RUNNING, s.phase)
        assertTrue(s.currentStep is Step.Countdown)
        assertEquals(5, s.remainingSec(1_000L))
        assertTrue(fx.contains(EngineEffect.Checkpoint))
    }

    @Test
    fun `countdown beeps at 3-2-1 once each and advances at boundary`() {
        var (s, _) = initial().apply(EngineEvent.Start(0L))
        val beeps = ArrayList<Long>()
        var t = 0L
        while (s.currentStep is Step.Countdown) {
            t += 250L
            val (ns, fx) = s.apply(EngineEvent.Tick(t))
            if (fx.contains(EngineEffect.BeepShort)) beeps += t
            s = ns
        }
        // beep w chwili, gdy na ekranie pojawia się 3, 2, 1
        assertEquals(listOf(2_000L, 3_000L, 4_000L), beeps)
        assertEquals(5_000L, t)
        assertTrue(s.currentStep is Step.Work)
        assertEquals(5_000L, s.phaseStartedAt)
    }

    @Test
    fun `reps work waits for done and logs actual value`() {
        var (s, _) = initial().apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first
        val work = s.currentStep as Step.Work
        assertEquals(TargetKind.REPS, work.targetKind)
        assertNull(s.remainingSec(60_000L))
        // tick nie przesuwa kroku na powtórzenia
        s = s.apply(EngineEvent.Tick(60_000L)).first
        assertEquals(work, s.currentStep)
        val (afterDone, fx) = s.apply(EngineEvent.Done(61_000L, actual = 8))
        val log = fx.filterIsInstance<EngineEffect.LogSet>().single().result
        assertEquals(8, log.actualValue)
        assertEquals(10, log.step.targetValue)
        assertTrue(afterDone.currentStep is Step.Rest)
        assertTrue(fx.contains(EngineEffect.BeepLong))
        assertTrue(fx.filterIsInstance<EngineEffect.Speak>().single().text.startsWith("Następnie: Pompki"))
    }

    @Test
    fun `done without value logs target`() {
        var (s, _) = initial().apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first
        val (_, fx) = s.apply(EngineEvent.Done(10_000L))
        assertEquals(10, fx.filterIsInstance<EngineEffect.LogSet>().single().result.actualValue)
    }

    @Test
    fun `rest can be extended and pause does not consume rest time`() {
        var (s, _) = initial().apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first
        s = s.apply(EngineEvent.Done(10_000L)).first
        assertEquals(30, (s.currentStep as Step.Rest).seconds)
        s = s.apply(EngineEvent.AddRest(11_000L, 15)).first
        assertEquals(45, (s.currentStep as Step.Rest).seconds)
        assertEquals(44, s.remainingSec(11_000L))
        s = s.apply(EngineEvent.Pause(15_000L)).first
        assertTrue(s.paused)
        assertEquals(40, s.remainingSec(25_000L)) // w pauzie czas stoi
        s = s.apply(EngineEvent.Tick(25_000L)).first // tick w pauzie nic nie robi
        assertTrue(s.paused)
        s = s.apply(EngineEvent.Resume(25_000L)).first
        assertFalse(s.paused)
        assertEquals(40, s.remainingSec(25_000L))
        assertEquals(10_000L, s.totalPausedMs)
        // przerwa kończy się 10 s później niż bez pauzy: start 10 000 + 45 000 + 10 000 = 65 000
        s = s.apply(EngineEvent.Tick(64_900L)).first
        assertTrue(s.currentStep is Step.Rest)
        s = s.apply(EngineEvent.Tick(65_100L)).first
        assertTrue(s.currentStep is Step.Work)
        assertEquals(65_000L, s.phaseStartedAt)
    }

    @Test
    fun `timed work auto completes and logs target`() {
        var (s, _) = initial(TestFixtures.tabataWorkout()).apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first
        val work = s.currentStep as Step.Work
        assertEquals(20, work.durationSec)
        val (ns, fx) = s.apply(EngineEvent.Tick(25_300L))
        assertEquals(20, fx.filterIsInstance<EngineEffect.LogSet>().single().result.actualValue)
        assertTrue(ns.currentStep is Step.Rest)
        assertEquals(25_000L, ns.phaseStartedAt)
    }

    @Test
    fun `skip advances without logging and back returns`() {
        var (s, _) = initial().apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first
        val (skipped, fx) = s.apply(EngineEvent.Skip(6_000L))
        assertTrue(fx.none { it is EngineEffect.LogSet })
        assertEquals(2, skipped.stepIndex)
        val (back, _) = skipped.apply(EngineEvent.Back(7_000L))
        assertEquals(1, back.stepIndex)
        assertEquals(7_000L, back.phaseStartedAt)
    }

    @Test
    fun `full tabata runs to finish without drift with jittery ticks`() {
        var (s, _) = initial(TestFixtures.tabataWorkout()).apply(EngineEvent.Start(0L))
        val expectedTotal = planner.estimateSeconds(s.steps) * 1000L // wszystkie kroki czasowe
        var t = 0L
        var logs = 0
        var finished: EngineEffect.Finished? = null
        val jitter = listOf(250L, 400L, 700L, 300L, 1_100L, 250L)
        var i = 0
        while (!s.isOver) {
            t += jitter[i % jitter.size]
            i++
            val (ns, fx) = s.apply(EngineEvent.Tick(t))
            logs += fx.count { it is EngineEffect.LogSet }
            fx.filterIsInstance<EngineEffect.Finished>().firstOrNull()?.let { finished = it }
            if (ns.stepIndex != s.stepIndex) {
                // każda faza startuje dokładnie na granicy poprzedniej
                assertEquals(0L, ns.phaseStartedAt % 1_000L)
            }
            s = ns
            if (t > 10 * expectedTotal) error("engine did not finish")
        }
        assertEquals(8, logs)
        assertEquals(SessionStatus.DONE, finished?.status)
        assertEquals(Phase.FINISHED, s.phase)
        // koniec nastąpił przy pierwszym ticku po granicy — granica = suma czasów
        assertTrue(t >= expectedTotal && t - expectedTotal < 1_200L)
    }

    @Test
    fun `long sleep does not cascade phases`() {
        var (s, _) = initial(TestFixtures.tabataWorkout()).apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first
        // telefon „zasnął” na 5 minut w trakcie 20 s pracy
        val (ns, _) = s.apply(EngineEvent.Tick(305_000L))
        assertTrue(ns.currentStep is Step.Rest)
        assertEquals(305_000L, ns.phaseStartedAt) // nowa faza startuje od teraz, nie od granicy
        assertEquals(10, ns.remainingSec(305_000L))
    }

    @Test
    fun `abort finishes as abandoned and ignores further events`() {
        var (s, _) = initial().apply(EngineEvent.Start(0L))
        val (aborted, fx) = s.apply(EngineEvent.Abort(1_000L))
        assertEquals(Phase.ABORTED, aborted.phase)
        assertEquals(SessionStatus.ABANDONED, fx.filterIsInstance<EngineEffect.Finished>().single().status)
        val (same, none) = aborted.apply(EngineEvent.Tick(2_000L))
        assertEquals(aborted, same)
        assertTrue(none.isEmpty())
    }

    @Test
    fun `replan mid workout changes remaining targets only`() {
        var (s, _) = initial().apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first
        s = s.apply(EngineEvent.Done(10_000L, 10)).first // seria 1 → przerwa
        s = s.apply(EngineEvent.Tick(40_000L)).first // przerwa 30 s minęła → seria 2
        assertEquals(2, (s.currentStep as Step.Work).setIndex)
        val replanned = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.ADVANCED, emptySet())
        val merged = planner.merge(s.steps, s.stepIndex, replanned)
        val (rs, _) = s.apply(EngineEvent.Replan(41_000L, Difficulty.ADVANCED, merged))
        assertEquals(Difficulty.ADVANCED, rs.difficulty)
        assertEquals(13, (rs.currentStep as Step.Work).targetValue)
        assertEquals(10, (rs.steps[1] as Step.Work).targetValue)
        assertEquals(s.stepIndex, rs.stepIndex)
    }

    @Test
    fun `restore shifts timestamps by real time passed and pauses when overdue`() {
        var (s, _) = initial(TestFixtures.tabataWorkout()).apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first // praca 20 s od 5 000
        s = s.apply(EngineEvent.Tick(9_000L)).first // 4 s pracy za nami
        // zapis przy now=9 000; po reboocie zegar startuje od 1 000, minęło realnie 2 s
        val restored = WorkoutEngine.restore(s, savedNow = 9_000L, passedMs = 2_000L, now = 1_000L)
        assertFalse(restored.paused)
        assertEquals(14, restored.remainingSec(1_000L)) // 20 − 4 − 2
        // minęło realnie 10 minut → faza dawno minęła → wraca w pauzie
        val late = WorkoutEngine.restore(s, savedNow = 9_000L, passedMs = 600_000L, now = 1_000L)
        assertTrue(late.paused)
        assertEquals(0, late.remainingSec(1_000L))
    }

    @Test
    fun `restore serii na powtorzenia po dlugiej przerwie wraca w pauzie bez martwego czasu`() {
        var (s, _) = initial().apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first // seria pompek (powtórzenia) od 5 000
        assertEquals(TargetKind.REPS, (s.currentStep as Step.Work).targetKind)
        // zapis przy now = 9 000 (4 s serii); po reboocie zegar startuje od 1 000, minęła realnie minuta
        val soon = WorkoutEngine.restore(s, savedNow = 9_000L, passedMs = 60_000L, now = 1_000L)
        assertFalse(soon.paused)
        assertEquals(64, (soon.elapsedInPhaseMs(1_000L) / 1000L).toInt())
        // prawie doba później: pauza od chwili zapisu, więc seria i zegar treningu stoją tam, gdzie były
        val late = WorkoutEngine.restore(s, savedNow = 9_000L, passedMs = 82_000_000L, now = 1_000L)
        assertTrue(late.paused)
        assertEquals(4, (late.elapsedInPhaseMs(1_000L) / 1000L).toInt())
        assertEquals(9, (late.totalElapsedMs(1_000L) / 1000L).toInt())
        // „Kontynuuj” liczy serię dalej od 4 s, bez doby w środku
        val resumed = late.apply(EngineEvent.Resume(1_000L)).first
        assertFalse(resumed.paused)
        assertEquals(6, (resumed.elapsedInPhaseMs(3_000L) / 1000L).toInt())
        assertEquals(11, (resumed.totalElapsedMs(3_000L) / 1000L).toInt())
    }

    @Test
    fun `restore of paused state keeps it paused with same remaining`() {
        var (s, _) = initial(TestFixtures.tabataWorkout()).apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first
        s = s.apply(EngineEvent.Pause(12_000L)).first
        val restored = WorkoutEngine.restore(s, savedNow = 12_000L, passedMs = 100_000L, now = 500L)
        assertTrue(restored.paused)
        assertEquals(13, restored.remainingSec(500L))
    }

    @Test
    fun `snapshot reports work numbering and next work`() {
        var (s, _) = initial().apply(EngineEvent.Start(0L))
        val onCountdown = s.snapshot(1_000L)
        assertEquals(1, onCountdown.workNumber)
        assertEquals(6, onCountdown.workTotal)
        assertEquals("Pompki", onCountdown.nextWork?.exerciseName)
        s = s.apply(EngineEvent.Tick(5_000L)).first
        s = s.apply(EngineEvent.Done(10_000L)).first
        val onRest = s.snapshot(12_000L)
        assertEquals(2, onRest.workNumber)
        assertEquals(28, onRest.remainingSec)
        assertEquals(12, onRest.totalElapsedSec)
    }

    /**
     * Na emulatorze zdarzyło się raz, że trening zaliczył serię 1 i 3, a serii 2 nie było w logu.
     * Nie udało się tego powtórzyć, więc pilnujemy niezmiennika: przy dowolnie nierównych tickach
     * (lag ponad tolerancję i poniżej) każda seria trafia do logu dokładnie raz i w kolejności.
     */
    @Test
    fun `przy nierownych tickach kazda seria trafia do logu dokladnie raz`() {
        var (s, _) = initial().apply(EngineEvent.Start(0L))
        val expected = s.steps.filterIsInstance<Step.Work>().map { it.positionKey }
        val logged = ArrayList<String>()
        val lags = listOf(700L, 1_500L, 11_000L, 37_000L, 250L, 3_100L)
        var now = 0L
        var lag = 0
        var guard = 0
        while (!s.isOver && guard++ < 10_000) {
            val step = s.currentStep
            val (next, fx) = if (step is Step.Work && step.durationSec == null) {
                s.apply(EngineEvent.Done(now, null))
            } else {
                now += lags[lag++ % lags.size]
                s.apply(EngineEvent.Tick(now))
            }
            fx.filterIsInstance<EngineEffect.LogSet>().forEach { logged += it.result.step.positionKey }
            s = next
        }

        assertTrue(s.isOver)
        assertEquals(expected, logged)
    }

    @Test
    fun `announcer handles polish plurals`() {
        assertEquals("1 powtórzenie", Announcer.reps(1))
        assertEquals("3 powtórzenia", Announcer.reps(3))
        assertEquals("5 powtórzeń", Announcer.reps(5))
        assertEquals("12 powtórzeń", Announcer.reps(12))
        assertEquals("22 powtórzenia", Announcer.reps(22))
        assertEquals("1 sekunda", Announcer.seconds(1))
        assertEquals("30 sekund", Announcer.seconds(30))
        assertEquals("1:05", Announcer.formatClock(65))
    }

    // Gotowe programy (D-37): obwód z jednym ćwiczeniem, krok bez limitu, rytm oddechu, licznik AMRAP.

    private fun fixedWorkout(block: Block) =
        Workout(id = "w-fixed", name = "Program", blocks = listOf(block), createdAt = 0L, updatedAt = 0L)

    /** Tabata z jednym ćwiczeniem: 6 rund po 20 s, 10 s przerwy między rundami. */
    private fun singleItemTabata() = fixedWorkout(
        Block(
            kind = BlockKind.CIRCUIT, rounds = 6, restBetweenRoundsSec = 10, fixed = true,
            items = listOf(BlockItem("climbers", TargetKind.SECONDS, 20)),
        ),
    )

    private fun openEndedWorkout() = fixedWorkout(
        Block(kind = BlockKind.SETS, fixed = true, items = listOf(BlockItem("plank", TargetKind.SECONDS, 90, sets = 1, openEnded = true))),
    )

    private fun cadenceWorkout() = fixedWorkout(
        Block(
            kind = BlockKind.SETS, fixed = true,
            items = listOf(BlockItem("plank", TargetKind.SECONDS, 90, sets = 2, restAfterSetSec = 10, cadenceSec = 3)),
        ),
    )

    private fun amrapWorkout() = fixedWorkout(
        Block(kind = BlockKind.SETS, fixed = true, items = listOf(BlockItem("pushups", TargetKind.AMRAP, 30, sets = 2, restAfterSetSec = 10))),
    )

    /** Start i odliczanie 5 s: stan na pierwszym kroku pracy, faza od 5 000. */
    private fun onFirstWork(workout: Workout): EngineState {
        var (s, _) = initial(workout).apply(EngineEvent.Start(0L))
        s = s.apply(EngineEvent.Tick(5_000L)).first
        assertTrue(s.currentStep is Step.Work)
        return s
    }

    /** Tyka co 250 ms do końca treningu; zwraca czas końca, logi i komunikaty. */
    private fun runToEnd(start: EngineState): Triple<Long, List<SetResult>, List<String>> {
        var s = start
        val logs = ArrayList<SetResult>()
        val speeches = ArrayList<String>()
        var t = 0L
        while (!s.isOver) {
            t += 250L
            val (ns, fx) = s.apply(EngineEvent.Tick(t))
            fx.filterIsInstance<EngineEffect.LogSet>().forEach { logs += it.result }
            fx.filterIsInstance<EngineEffect.Speak>().forEach { speeches += it.text }
            s = ns
            if (t > 600_000L) error("engine did not finish")
        }
        return Triple(t, logs, speeches)
    }

    @Test
    fun `obwod z jednym cwiczeniem loguje kazda runde i mowi numer rundy na przerwie`() {
        val (t, logs, speeches) = runToEnd(initial(singleItemTabata()).apply(EngineEvent.Start(0L)).first)
        assertEquals(6, logs.size)
        assertTrue(logs.all { it.actualValue == 20 })
        assertEquals((1..6).toList(), logs.map { it.step.round })
        assertEquals("Wspinaczka, 20 sekund, runda 1 z 6", speeches.first())
        assertEquals(
            listOf("Runda 2 z 6", "Runda 3 z 6", "Runda 4 z 6", "Runda 5 z 6", "Runda 6 z 6"),
            speeches.filter { it.startsWith("Runda") },
        )
        assertTrue(speeches.none { it.startsWith("Następnie") })
        // 5 s odliczania + 6 × 20 s pracy + 5 × 10 s przerwy
        assertEquals(175_000L, t)
    }

    @Test
    fun `obwod z dwoma cwiczeniami zapowiada nastepne cwiczenie, nie runde`() {
        val (_, logs, speeches) = runToEnd(initial(TestFixtures.tabataWorkout()).apply(EngineEvent.Start(0L)).first)
        assertEquals(8, logs.size)
        val onRest = speeches.filter { it.startsWith("Następnie") }
        assertEquals(7, onRest.size)
        assertTrue(speeches.none { it.startsWith("Runda") })
        assertEquals("Następnie: Przysiady, 20 sekund", onRest[0])
        // przerwa między rundami prowadzi do innej pozycji, więc zwykła zapowiedź
        assertEquals("Następnie: Wspinaczka, 20 sekund", onRest[1])
        assertEquals("Wspinaczka, 20 sekund, runda 1 z 4", speeches.first())
    }

    @Test
    fun `krok bez limitu nie konczy sie sam i loguje czas, ktory minal`() {
        val s = onFirstWork(openEndedWorkout())
        val work = s.currentStep as Step.Work
        assertTrue(work.openEnded)
        assertNull(work.durationSec)
        assertNull(s.remainingSec(6_000L))
        assertTrue(s.awaitingRepsDone) // klawisz głośności kończy krok bez limitu

        val (after600, fx600) = s.apply(EngineEvent.Tick(5_000L + 600_000L))
        assertEquals(work, after600.currentStep)
        assertFalse(after600.paused)
        assertTrue(fx600.isEmpty())

        val (_, fxDone) = s.apply(EngineEvent.Done(5_000L + 60_000L))
        assertEquals(60, fxDone.filterIsInstance<EngineEffect.LogSet>().single().result.actualValue)

        // bezpiecznik: 90 s × OPEN_ENDED_CAP
        val (_, fxLate) = s.apply(EngineEvent.Done(5_000L + 2_000_000L))
        assertEquals(900, fxLate.filterIsInstance<EngineEffect.LogSet>().single().result.actualValue)
    }

    @Test
    fun `krok bez limitu po bezpieczniku staje w pauzie`() {
        val s = onFirstWork(openEndedWorkout())
        val (before, fxBefore) = s.apply(EngineEvent.Tick(5_000L + 899_750L))
        assertFalse(before.paused)
        assertTrue(fxBefore.isEmpty())

        val (paused, fx) = s.apply(EngineEvent.Tick(5_000L + 900_000L))
        assertTrue(paused.paused)
        assertTrue(fx.contains(EngineEffect.Checkpoint))
        assertTrue(fx.none { it is EngineEffect.LogSet })
        // w pauzie klawisz wraca do systemu, a „Zakończ” dalej loguje czas do bezpiecznika
        assertFalse(paused.awaitingRepsDone)
        val (_, fxDone) = paused.apply(EngineEvent.Done(5_000L + 1_000_000L))
        assertEquals(900, fxDone.filterIsInstance<EngineEffect.LogSet>().single().result.actualValue)
    }

    @Test
    fun `restore kroku bez limitu pauzuje dopiero po bezpieczniku`() {
        var s = onFirstWork(openEndedWorkout())
        s = s.apply(EngineEvent.Tick(9_000L)).first // 4 s kroku za nami, zapis przy now = 9 000
        val soon = WorkoutEngine.restore(s, savedNow = 9_000L, passedMs = 300_000L, now = 1_000L) // 5 min
        assertFalse(soon.paused)
        assertEquals(304, (soon.elapsedInPhaseMs(1_000L) / 1000L).toInt())
        val late = WorkoutEngine.restore(s, savedNow = 9_000L, passedMs = 1_200_000L, now = 1_000L) // 20 min
        assertTrue(late.paused)
    }

    @Test
    fun `krok z rytmem daje sygnal co cadenceSec zamiast odliczania`() {
        var s = onFirstWork(cadenceWorkout())
        val work = s.currentStep as Step.Work
        assertEquals(3, work.cadenceSec)
        assertEquals(30, work.cadenceCount)
        assertEquals(90, work.durationSec)
        val cues = ArrayList<Long>()
        var beeps = 0
        var t = 5_000L
        while (s.currentStep == work) {
            t += 250L
            val (ns, fx) = s.apply(EngineEvent.Tick(t))
            if (fx.contains(EngineEffect.Cadence)) cues += t - 5_000L
            beeps += fx.count { it == EngineEffect.BeepShort }
            s = ns
        }
        assertEquals((3..87 step 3).map { it * 1_000L }, cues)
        assertEquals(0, beeps)
        assertEquals(95_000L, t)
        assertTrue(s.currentStep is Step.Rest)
        assertEquals(0, s.lastCueIndex)
    }

    @Test
    fun `spozniony tick daje jeden sygnal rytmu, a cofniecie zeruje licznik`() {
        var s = onFirstWork(cadenceWorkout())
        val (late, fx) = s.apply(EngineEvent.Tick(5_000L + 10_000L)) // trzy oddechy naraz
        assertEquals(1, fx.count { it == EngineEffect.Cadence })
        assertEquals(3, late.lastCueIndex)
        s = late.apply(EngineEvent.Back(16_000L)).first
        assertEquals(0, s.lastCueIndex)
        assertTrue(s.currentStep is Step.Countdown)
    }

    @Test
    fun `licznik AMRAP z ekranu trafia do logu po uplywie czasu`() {
        val s = onFirstWork(amrapWorkout())
        assertEquals(TargetKind.AMRAP, (s.currentStep as Step.Work).targetKind)
        val (counted, fxCount) = s.apply(EngineEvent.Count(10_000L, 7))
        assertTrue(fxCount.isEmpty()) // bez checkpointu przy każdym tapnięciu
        assertEquals(7, counted.pendingCount)
        val (after, fx) = counted.apply(EngineEvent.Tick(35_000L))
        assertEquals(7, fx.filterIsInstance<EngineEffect.LogSet>().single().result.actualValue)
        assertTrue(after.currentStep is Step.Rest)
        assertNull(after.pendingCount)
    }

    @Test
    fun `Zrobione bierze licznik AMRAP, a jawna wartosc ma pierwszenstwo`() {
        val s = onFirstWork(amrapWorkout()).apply(EngineEvent.Count(10_000L, 5)).first
        val (_, fxDefault) = s.apply(EngineEvent.Done(20_000L))
        assertEquals(5, fxDefault.filterIsInstance<EngineEffect.LogSet>().single().result.actualValue)
        val (_, fxExplicit) = s.apply(EngineEvent.Done(20_000L, actual = 9))
        assertEquals(9, fxExplicit.filterIsInstance<EngineEffect.LogSet>().single().result.actualValue)
        // bez licznika i bez wartości zostaje zero, jak dotąd
        val (_, fxNone) = onFirstWork(amrapWorkout()).apply(EngineEvent.Done(20_000L))
        assertEquals(0, fxNone.filterIsInstance<EngineEffect.LogSet>().single().result.actualValue)
    }

    @Test
    fun `licznik AMRAP przed startem jest ignorowany`() {
        val (s, fx) = initial(amrapWorkout()).apply(EngineEvent.Count(0L, 3))
        assertNull(s.pendingCount)
        assertTrue(fx.isEmpty())
    }
}
