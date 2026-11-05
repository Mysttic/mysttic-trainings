package pl.mysttic.trainings.domain.plan.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.engine.TestFixtures
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.RestKind
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.TrainingProfile

class PresetCatalogTest {

    private val planner = WorkoutPlanner()

    /** Tabele programu bez biblioteki: nic nie wiadomo o sprzęcie, więc żadna pozycja nie dostaje zamiennika. */
    private fun build(id: PresetId, level: Difficulty, days: Int, param: Int?, programId: String, now: Long) =
        PresetCatalog.build(id, level, days, param, programId, now, exercises = emptyMap(), owned = emptySet())

    private val benchLibrary = listOf(
        TestFixtures.exercise("step-ups", "Wejścia na podwyższenie", equipment = setOf(Equipment.BENCH)),
        TestFixtures.exercise("bench-dips", "Dipy na krześle", equipment = setOf(Equipment.BENCH)),
        TestFixtures.exercise("curtsy-lunge", "Wykrok skrzyżny"),
        TestFixtures.exercise("close-pushups", "Pompki wąskie"),
        TestFixtures.exercise("kneeling-triceps-extension-floor", "Wyprost ramion z podłogi w klęku"),
        TestFixtures.exercise("pullups", equipment = setOf(Equipment.PULL_UP_BAR)),
        TestFixtures.exercise("negative-pullups", equipment = setOf(Equipment.PULL_UP_BAR)),
    ).associateBy { it.id }

    private fun estimate(blocks: List<pl.mysttic.trainings.domain.model.Block>): Int {
        val workout = pl.mysttic.trainings.domain.model.Workout("w", "w", blocks = blocks, createdAt = 0L, updatedAt = 0L)
        return planner.estimateSeconds(planner.expand(workout, emptyMap(), Difficulty.INTERMEDIATE, emptySet()))
    }

    @Test
    fun `kazdy program na kazdym poziomie i dlugosci daje gesty, spojny cykl`() {
        for (definition in PresetCatalog.all) {
            for (level in Difficulty.entries) {
                for (days in definition.lengthOptions) {
                    val plan = build(definition.id, level, days, param = null, programId = "p1", now = 7L)
                    val where = "${definition.id} $level $days"

                    assertEquals(where, days, plan.days.size)
                    assertEquals(where, (1..days).toList(), plan.days.map { it.dayIndex })
                    assertEquals(where, PresetCatalog.trainingDays(definition, days).size, plan.sessionCount)
                    assertTrue(where, plan.workouts.isNotEmpty())

                    val ids = plan.workouts.map { it.id }.toSet()
                    plan.days.mapNotNull { it.workoutId }.forEach { assertTrue("$where: $it", it in ids) }
                    plan.workouts.forEach { workout ->
                        assertTrue(where, workout.id.startsWith("p1-s"))
                        assertNotNull(where, PresetCatalog.stageOf("p1", workout.id))
                        assertFalse(where, workout.isBuiltIn)
                        assertTrue(where, workout.isFixed)
                        assertTrue(where, workout.blocks.all { it.items.isNotEmpty() })
                        assertTrue(where, workout.name.startsWith(definition.name))
                        assertTrue(where, workout.note.contains(PresetCatalog.FIXED_NOTE))
                    }
                    // etapy idą po kolei: pierwszy dzień treningowy to etap 1, ostatni to etap ostatni
                    val training = plan.days.filterNot { it.isRest }
                    assertEquals(where, 1, PresetCatalog.stageOf("p1", training.first().workoutId!!))
                    assertEquals(where, plan.workouts.size, PresetCatalog.stageOf("p1", training.last().workoutId!!))
                    val stagesInOrder = training.map { PresetCatalog.stageOf("p1", it.workoutId!!)!! }
                    assertEquals(where, stagesInOrder.sorted(), stagesInOrder)

                    // objętość nie maleje z etapu na etap
                    val estimates = plan.workouts.map { estimate(it.blocks) }
                    assertEquals(where, estimates.sorted(), estimates)
                }
            }
        }
    }

    @Test
    fun `ten sam wybor daje ten sam plan`() {
        val a = build(PresetId.ABS, Difficulty.INTERMEDIATE, 30, null, "p1", now = 1L)
        val b = build(PresetId.ABS, Difficulty.INTERMEDIATE, 30, null, "p1", now = 1L)
        assertEquals(a, b)
    }

    @Test
    fun `tabata dostaje blok wiecej co tydzien i trzyma 20 na 10`() {
        val plan = build(PresetId.TABATA, Difficulty.INTERMEDIATE, 21, null, "p1", 0L)
        assertEquals(9, plan.sessionCount)
        assertEquals(3, plan.workouts.size)
        // rozgrzewka + N bloków tabaty + schłodzenie
        assertEquals(listOf(3, 4, 5), plan.workouts.map { it.blocks.size })
        val tabata = plan.workouts[0].blocks[1]
        assertEquals(BlockKind.CIRCUIT, tabata.kind)
        assertEquals(2, tabata.rounds)
        assertEquals(10, tabata.restBetweenRoundsSec)
        assertEquals(4, tabata.items.size)
        assertTrue(tabata.items.all { it.targetValue == 20 && it.restAfterItemSec == 10 })
        assertTrue(tabata.items.all { it.targetKind == TargetKind.SECONDS || it.targetKind == TargetKind.AMRAP })
        assertEquals(setOf(0, 2, 4), plan.days.filterNot { it.isRest }.map { (it.dayIndex - 1) % 7 }.toSet())
    }

    @Test
    fun `brzuch rosnie co etap i odpoczywa co czwarty dzien`() {
        val plan = build(PresetId.ABS, Difficulty.INTERMEDIATE, 30, null, "p1", 0L)
        assertEquals(23, plan.sessionCount)
        assertEquals(8, plan.workouts.size)
        assertTrue(plan.days.filter { it.dayIndex % 4 == 0 }.all { it.isRest })
        assertEquals("p1-s1", plan.days[0].workoutId)
        assertEquals("p1-s8", plan.days[29].workoutId)
        val first = plan.workouts.first().blocks.single().items
        val last = plan.workouts.last().blocks.single().items
        assertEquals(10, first.first { it.exerciseId == "crunches" }.targetValue)
        assertEquals(45, last.first { it.exerciseId == "crunches" }.targetValue)
        assertEquals(20, first.first { it.exerciseId == "plank" }.targetValue)
        assertEquals(90, last.first { it.exerciseId == "plank" }.targetValue)
        assertTrue(plan.workouts.first().note.startsWith("Etap 1 z 8: 10 powt., deska 20 s."))
    }

    @Test
    fun `sto pompek liczy serie z podanej liczby i poziomu`() {
        val plan = build(PresetId.PUSHUPS_100, Difficulty.INTERMEDIATE, 42, param = 15, programId = "p1", now = 0L)
        assertEquals(18, plan.sessionCount)
        assertEquals(18, plan.workouts.size)
        val first = plan.workouts.first().blocks.single().items
        val last = plan.workouts.last().blocks.single().items
        assertEquals("pushups", first[0].exerciseId)
        assertEquals(7, first[0].targetValue) // 45 % z 15
        assertEquals(5, first[0].sets)
        assertEquals(60, first[0].restAfterSetSec)
        assertEquals(14, last[0].targetValue) // 90 % z 15
        assertEquals(120, last[0].restAfterSetSec)
        assertEquals(TargetKind.AMRAP, last[1].targetKind)

        val beginner = build(PresetId.PUSHUPS_100, Difficulty.BEGINNER, 21, param = 4, programId = "p1", now = 0L)
        val items = beginner.workouts.first().blocks.single().items
        assertEquals("knee-pushups", items.single().exerciseId)
        assertEquals(3, items.single().targetValue) // nigdy mniej niż 3
        assertEquals(4, items.single().sets)
    }

    @Test
    fun `wim hof to program oddechowy z rytmem i krokiem bez limitu`() {
        val definition = PresetCatalog.get(PresetId.WIM_HOF)
        assertEquals(ProgramKind.BREATHING, definition.kind)
        assertNotNull(definition.warning)
        val plan = build(PresetId.WIM_HOF, Difficulty.INTERMEDIATE, 21, null, "p1", 0L)
        assertEquals(18, plan.sessionCount)
        assertEquals(listOf(3, 4, 4), plan.workouts.map { it.blocks.first().rounds })
        val round = plan.workouts.first().blocks.first()
        assertEquals(0, round.restBetweenRoundsSec)
        assertEquals(0, round.restAfterSec)
        val (breathing, hold, recovery) = round.items
        assertEquals("power-breathing", breathing.exerciseId)
        assertEquals(3, breathing.cadenceSec)
        assertEquals(90, breathing.targetValue)
        assertEquals("breath-hold", hold.exerciseId)
        assertTrue(hold.openEnded)
        assertEquals("recovery-breath", recovery.exerciseId)
        assertEquals(15, recovery.targetValue)
        assertEquals("calm-breathing", plan.workouts.first().blocks[1].items.single().exerciseId)
        assertTrue(plan.days.filter { it.dayIndex % 7 == 0 }.all { it.isRest })
        assertTrue("power-breathing" in PresetCatalog.exerciseIds(definition))
        assertEquals(PresetCatalog.exerciseIds(definition), PresetCatalog.missingExercises(definition, emptyMap()))
    }

    @Test
    fun `burpee co minute niesie okno minuty`() {
        val definition = PresetCatalog.get(PresetId.BURPEE_EMOM)
        for (level in Difficulty.entries) {
            for (days in definition.lengthOptions) {
                val plan = build(PresetId.BURPEE_EMOM, level, days, null, "p1", 0L)
                plan.workouts.forEach { workout ->
                    val where = "$level $days ${workout.id}"
                    val emom = workout.blocks.single { it.kind == BlockKind.CIRCUIT }
                    assertTrue(where, emom.items.all { it.windowSec == BurpeeEmom.EMOM_WINDOW_SEC })

                    // Przerwa między rundami dopełnia minutę: planner wystawia estymatę z oknem, silnik liczy resztę.
                    val steps = planner.expand(workout, emptyMap(), Difficulty.INTERMEDIATE, emptySet())
                    val roundRests = steps.filterIsInstance<Step.Rest>().filter { it.kind == RestKind.BETWEEN_ROUNDS }
                    assertEquals(where, emom.rounds - 1, roundRests.size)
                    assertTrue(where, roundRests.all { it.windowSec == BurpeeEmom.EMOM_WINDOW_SEC })
                }
            }
        }
    }

    @Test
    fun `deska i drazek maja etap na kazda sesje`() {
        val plan = build(PresetId.PLANK_AND_BAR, Difficulty.INTERMEDIATE, 30, null, "p1", 0L)
        assertEquals(26, plan.sessionCount)
        assertEquals(26, plan.workouts.size)
        assertEquals(20, plan.workouts.first().blocks[1].items.single().targetValue)
        assertEquals(180, plan.workouts.last().blocks[1].items.single().targetValue)
        assertEquals("pullups", plan.workouts.first().blocks[0].items.single().exerciseId)
        assertEquals(60, plan.workouts.first().blocks[0].restAfterSec)
    }

    @Test
    fun `profil cyklu niesie program, poziom i dlugosc, a nazwa cyklu to nazwa programu`() {
        val profile = PresetCatalog.profileFor(
            id = PresetId.TABATA, level = Difficulty.ADVANCED, cycleDays = 28, param = null,
            equipment = emptySet(), firstStageMinutes = 12, base = TrainingProfile(heightCm = 190, weightKg = 90.0),
        )
        assertEquals("TABATA", profile.presetId)
        assertTrue(profile.isPreset)
        assertEquals(Difficulty.ADVANCED, profile.level)
        assertEquals(28, profile.cycleDays)
        assertEquals(3, profile.daysPerWeek)
        assertEquals(12, profile.sessionMinutes)
        assertEquals(190, profile.heightCm)
        assertNull(profile.presetParam)
        assertEquals("Tabata", Program.nameFor(profile))
        assertEquals("Plan na 30 dni · kondycja", Program.nameFor(TrainingProfile()))

        val pushups = PresetCatalog.profileFor(PresetId.PUSHUPS_100, Difficulty.INTERMEDIATE, 42, param = 200, equipment = emptySet(), firstStageMinutes = 8)
        assertEquals(60, pushups.presetParam) // przycięte do zakresu
    }

    @Test
    fun `identyfikator etapu z identyfikatora treningu`() {
        assertEquals(3, PresetCatalog.stageOf("plan-abc", "plan-abc-s3"))
        assertNull(PresetCatalog.stageOf("plan-abc", "plan-xyz-s3"))
        assertNull(PresetCatalog.stageOf("plan-abc", "full-body-a"))
        assertEquals("plan-abc-s12", PresetCatalog.stageWorkoutId("plan-abc", 12))
    }

    @Test
    fun `sprzet programu wynika z jego cwiczen`() {
        val bench = pl.mysttic.trainings.domain.engine.TestFixtures.exercise("bench-dips", equipment = setOf(pl.mysttic.trainings.domain.model.Equipment.BENCH))
        val stepUps = pl.mysttic.trainings.domain.engine.TestFixtures.exercise("step-ups", equipment = setOf(pl.mysttic.trainings.domain.model.Equipment.BENCH))
        val map = mapOf(bench.id to bench, stepUps.id to stepUps)
        assertEquals(setOf(pl.mysttic.trainings.domain.model.Equipment.BENCH), PresetCatalog.equipmentFor(PresetCatalog.get(PresetId.SEVEN_MINUTES), map))
        assertEquals(emptySet<pl.mysttic.trainings.domain.model.Equipment>(), PresetCatalog.equipmentFor(PresetCatalog.get(PresetId.ABS), map))
    }

    @Test
    fun `zerowa dlugosc albo brak dni treningowych daje pusty plan`() {
        val plan = build(PresetId.TABATA, Difficulty.INTERMEDIATE, 0, null, "p1", 0L)
        assertTrue(plan.workouts.isEmpty())
        assertTrue(plan.days.isEmpty())
    }

    @Test
    fun `lista idzie od lagodnych do wymagajacych, a kazdy program ma swoja trudnosc`() {
        val intensities = PresetCatalog.all.map { it.intensity }
        assertEquals(intensities.sorted(), intensities)
        assertEquals(PresetId.entries.toSet(), PresetCatalog.all.map { it.id }.toSet())
        // Przypięte: zmiana trudności programu ma być świadoma, bo przestawia listę wyboru.
        assertEquals(
            mapOf(
                PresetId.EVENING to PresetIntensity.LIGHT, PresetId.MORNING to PresetIntensity.LIGHT,
                PresetId.WIM_HOF to PresetIntensity.LIGHT, PresetId.SQUATS to PresetIntensity.MODERATE,
                PresetId.ABS to PresetIntensity.MODERATE, PresetId.PUSHUPS_100 to PresetIntensity.MODERATE,
                PresetId.PLANK_AND_BAR to PresetIntensity.MODERATE, PresetId.SEVEN_MINUTES to PresetIntensity.HARD,
                PresetId.TABATA to PresetIntensity.HARD, PresetId.BURPEE_EMOM to PresetIntensity.HARD,
                PresetId.IRON_MAN to PresetIntensity.HARD,
            ),
            PresetCatalog.all.associate { it.id to it.intensity },
        )
    }

    @Test
    fun `siedem minut bez lawki dostaje zamienniki bez sprzetu, a z lawka zostaje protokol`() {
        for (level in Difficulty.entries) {
            val bare = PresetCatalog.build(PresetId.SEVEN_MINUTES, level, 21, null, "p1", 0L, benchLibrary, owned = emptySet())
            val bench = PresetCatalog.build(PresetId.SEVEN_MINUTES, level, 21, null, "p1", 0L, benchLibrary, owned = setOf(Equipment.BENCH))
            val bareItems = bare.workouts.flatMap { w -> w.blocks.flatMap { it.items } }
            val benchItems = bench.workouts.flatMap { w -> w.blocks.flatMap { it.items } }

            assertTrue("$level", bareItems.none { it.exerciseId == "step-ups" || it.exerciseId == "bench-dips" })
            assertTrue("$level", bareItems.any { it.exerciseId == "curtsy-lunge" })
            assertTrue("$level", benchItems.any { it.exerciseId == "step-ups" } && benchItems.any { it.exerciseId == "bench-dips" })
            // Podmiana zmienia tylko ćwiczenie: cele, przerwy i liczba pozycji zostają.
            assertEquals("$level", benchItems.map { it.copy(exerciseId = "") }, bareItems.map { it.copy(exerciseId = "") })
            assertTrue("$level", bare.workouts.all { it.note.contains("Bez sprzętu: wejścia na podwyższenie → wykrok skrzyżny") })
            assertTrue("$level", bench.workouts.none { it.note.contains("Bez sprzętu") })
            assertEquals(bare, PresetCatalog.build(PresetId.SEVEN_MINUTES, level, 21, null, "p1", 0L, benchLibrary, owned = emptySet()))
        }
        val beginner = PresetCatalog.build(PresetId.SEVEN_MINUTES, Difficulty.BEGINNER, 14, null, "p1", 0L, benchLibrary, emptySet())
        assertTrue(beginner.workouts.first().blocks.single().items.any { it.exerciseId == "kneeling-triceps-extension-floor" })
    }

    @Test
    fun `sprzet bez zamiennika blokuje program, zastapiony juz nie`() {
        val plankAndBar = PresetCatalog.get(PresetId.PLANK_AND_BAR)
        val sevenMinutes = PresetCatalog.get(PresetId.SEVEN_MINUTES)
        assertEquals(setOf(Equipment.PULL_UP_BAR), PresetCatalog.unmetEquipment(plankAndBar, benchLibrary, emptySet()))
        assertTrue(PresetCatalog.unmetEquipment(plankAndBar, benchLibrary, setOf(Equipment.PULL_UP_BAR)).isEmpty())
        assertTrue(PresetCatalog.unmetEquipment(sevenMinutes, benchLibrary, emptySet()).isEmpty())
        assertTrue(PresetCatalog.unmetEquipment(PresetCatalog.get(PresetId.ABS), benchLibrary, emptySet()).isEmpty())
        assertTrue("curtsy-lunge" in PresetCatalog.exerciseIds(sevenMinutes))
    }

    @Test
    fun `siedem minut robi deske boczna na dwie strony z przerwa na zmiane strony`() {
        for (level in Difficulty.entries) {
            val plan = build(PresetId.SEVEN_MINUTES, level, 21, null, "p1", 0L)
            plan.workouts.forEach { workout ->
                val (first, second) = workout.blocks.single().items.takeLast(2)
                assertEquals("side-plank", first.exerciseId)
                assertEquals("side-plank", second.exerciseId)
                assertEquals(SevenMinutes.SIDE_SWITCH_SEC, first.restAfterItemSec)
                assertEquals("Pierwsza strona", first.note)
                assertEquals("Druga strona", second.note)
            }
        }
        // Na ekranie: praca, przerwa na zmianę strony, praca z podpisem drugiej strony.
        val workout = build(PresetId.SEVEN_MINUTES, Difficulty.INTERMEDIATE, 21, null, "p1", 0L).workouts.first()
        val steps = planner.expand(workout, emptyMap(), Difficulty.INTERMEDIATE, emptySet())
        val tail = steps.takeLast(3)
        assertEquals("Pierwsza strona", (tail[0] as Step.Work).note)
        assertEquals(SevenMinutes.SIDE_SWITCH_SEC, (tail[1] as Step.Rest).durationSec)
        assertEquals("Druga strona", (tail[2] as Step.Work).note)
    }
}
