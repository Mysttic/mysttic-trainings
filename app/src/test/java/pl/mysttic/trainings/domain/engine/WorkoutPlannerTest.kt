package pl.mysttic.trainings.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.coach.ExerciseTuning
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.RestKind
import pl.mysttic.trainings.domain.model.TargetKind

class WorkoutPlannerTest {

    private val planner = WorkoutPlanner()

    @Test
    fun `sets block expands to countdown, works and rests in order`() {
        val steps = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        // 1 countdown + (3 works + 2 rests) + 1 rest between items + (3 works + 2 rests) = 12
        assertEquals(12, steps.size)
        assertTrue(steps.first() is Step.Countdown)
        assertEquals(6, steps.count { it is Step.Work })
        assertEquals(4, steps.count { it is Step.Rest && it.kind == RestKind.BETWEEN_SETS })
        assertEquals(1, steps.count { it is Step.Rest && it.kind == RestKind.BETWEEN_ITEMS })
        val works = steps.filterIsInstance<Step.Work>()
        assertEquals(listOf(1, 2, 3, 1, 2, 3), works.map { it.setIndex })
        assertEquals(listOf(3, 3, 3, 3, 3, 3), works.map { it.setsInItem })
        assertEquals("Pompki", works[0].exerciseName)
        assertEquals(10, works[0].targetValue)
        assertEquals(TargetKind.SECONDS, works[3].targetKind)
    }

    @Test
    fun `circuit expands rounds with rests between items and rounds`() {
        val steps = planner.expand(TestFixtures.tabataWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        // 1 countdown + 4 rounds × (2 works + 1 rest between items) + 3 rests between rounds = 16
        assertEquals(16, steps.size)
        assertEquals(8, steps.count { it is Step.Work })
        assertEquals(4, steps.count { it is Step.Rest && it.kind == RestKind.BETWEEN_ITEMS })
        assertEquals(3, steps.count { it is Step.Rest && it.kind == RestKind.BETWEEN_ROUNDS })
        val works = steps.filterIsInstance<Step.Work>()
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4), works.map { it.round })
        assertTrue(steps.last() is Step.Work)
    }

    @Test
    fun `blocks are separated by a rest`() {
        val steps = planner.expand(TestFixtures.twoBlockWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        assertEquals(1, steps.count { it is Step.Rest && it.kind == RestKind.BETWEEN_BLOCKS })
        assertEquals(12 + 1 + 15, steps.size)
    }

    @Test
    fun `beginner scales targets down and rests up`() {
        val steps = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.BEGINNER, emptySet())
        val works = steps.filterIsInstance<Step.Work>()
        assertEquals(7, works[0].targetValue) // 10 × 0.7
        assertEquals(20, works[3].targetValue) // 30 s × 0.7 = 21 → 20
        val rests = steps.filterIsInstance<Step.Rest>()
        assertEquals(40, rests[0].seconds) // 30 × 1.3 = 39 → 40
        assertEquals(80, rests[2].seconds) // 60 × 1.3 = 78 → 80
    }

    @Test
    fun `advanced scales targets up and rests down`() {
        val steps = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.ADVANCED, emptySet())
        val works = steps.filterIsInstance<Step.Work>()
        assertEquals(13, works[0].targetValue) // 10 × 1.3
        assertEquals(40, works[3].targetValue) // 30 × 1.3 = 39 → 40
        val rests = steps.filterIsInstance<Step.Rest>()
        assertEquals(25, rests[0].seconds) // 30 × 0.8 = 24 → 25
        assertEquals(50, rests[2].seconds) // 60 × 0.8 = 48 → 50
    }

    @Test
    fun `beginner swaps to easier variant, advanced to harder, intermediate keeps base`() {
        val beginner = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.BEGINNER, emptySet())
        assertEquals("knee-pushups", beginner.filterIsInstance<Step.Work>()[0].exerciseId)
        val advanced = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.ADVANCED, emptySet())
        assertEquals("decline-pushups", advanced.filterIsInstance<Step.Work>()[0].exerciseId)
        val intermediate = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        assertEquals("pushups", intermediate.filterIsInstance<Step.Work>()[0].exerciseId)
        // Deska jest już BEGINNER — bez zamiany
        assertEquals("plank", beginner.filterIsInstance<Step.Work>()[3].exerciseId)
    }

    @Test
    fun `wariant na sprzet, ktorego nie ma, nie wchodzi do krokow`() {
        val workout = TestFixtures.setsWorkout().let { w ->
            w.copy(blocks = listOf(w.blocks[0].copy(items = listOf(w.blocks[0].items[0].copy(exerciseId = "leg-raises")))))
        }
        val withoutBar = planner.expand(workout, TestFixtures.exercises, Difficulty.ADVANCED, emptySet())
        assertEquals("leg-raises", withoutBar.filterIsInstance<Step.Work>()[0].exerciseId)

        val withBar = planner.expand(workout, TestFixtures.exercises, Difficulty.ADVANCED, setOf(Equipment.PULL_UP_BAR))
        assertEquals("hanging-leg-raises", withBar.filterIsInstance<Step.Work>()[0].exerciseId)
    }

    @Test
    fun `latwiejszy wariant tez wymaga sprzetu`() {
        val workout = TestFixtures.setsWorkout().let { w ->
            w.copy(blocks = listOf(w.blocks[0].copy(items = listOf(w.blocks[0].items[0].copy(exerciseId = "wall-pushups")))))
        }
        val withoutBench = planner.expand(workout, TestFixtures.exercises, Difficulty.BEGINNER, emptySet())
        assertEquals("wall-pushups", withoutBench.filterIsInstance<Step.Work>()[0].exerciseId)

        val withBench = planner.expand(workout, TestFixtures.exercises, Difficulty.BEGINNER, setOf(Equipment.BENCH))
        assertEquals("bench-pushups", withBench.filterIsInstance<Step.Work>()[0].exerciseId)
    }

    @Test
    fun `korekta trenera idzie za cwiczeniem pokazanym, nie za wariantem bez sprzetu`() {
        val workout = TestFixtures.setsWorkout().let { w ->
            w.copy(blocks = listOf(w.blocks[0].copy(items = listOf(w.blocks[0].items[0].copy(exerciseId = "leg-raises")))))
        }
        val tuning = mapOf("hanging-leg-raises" to ExerciseTuning("hanging-leg-raises", repsDelta = 4))

        val withoutBar = planner.expand(workout, TestFixtures.exercises, Difficulty.ADVANCED, emptySet(), tuning = tuning)
        assertEquals(13, withoutBar.filterIsInstance<Step.Work>()[0].targetValue) // 10 × 1,3, bez korekty

        val withBar = planner.expand(workout, TestFixtures.exercises, Difficulty.ADVANCED, setOf(Equipment.PULL_UP_BAR), tuning = tuning)
        assertEquals(17, withBar.filterIsInstance<Step.Work>()[0].targetValue) // 13 + 4
    }

    @Test
    fun `obwod z oknem emituje przerwe okna zamiast przerwy miedzy rundami`() {
        val steps = planner.expand(TestFixtures.emomWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        val rests = steps.filterIsInstance<Step.Rest>()
        assertEquals(3, rests.size) // 4 rundy, przerwa tylko między nimi
        assertTrue(rests.all { it.windowSec == 60 })
        // Estymata plannera: okno minus 5 powtórzeń po 3 s.
        assertEquals(45, rests[0].seconds)
    }

    @Test
    fun `okno nie zmienia sie z poziomem`() {
        val beginner = planner.expand(TestFixtures.emomWorkout(), TestFixtures.exercises, Difficulty.BEGINNER, emptySet())
        val advanced = planner.expand(TestFixtures.emomWorkout(), TestFixtures.exercises, Difficulty.ADVANCED, emptySet())
        assertEquals(
            beginner.filterIsInstance<Step.Rest>().map { it.seconds },
            advanced.filterIsInstance<Step.Rest>().map { it.seconds },
        )
        // Blok skalowany poziomem: cel pracy rośnie i maleje, a okno każdej przerwy zostaje minutą.
        val scaled = TestFixtures.emomWorkout().let { w -> w.copy(blocks = w.blocks.map { it.copy(fixed = false) }) }
        for (difficulty in Difficulty.entries) {
            val rests = planner.expand(scaled, TestFixtures.exercises, difficulty, emptySet()).filterIsInstance<Step.Rest>()
            assertEquals(difficulty.name, 3, rests.size)
            assertTrue(difficulty.name, rests.all { it.windowSec == 60 })
        }
    }

    @Test
    fun `krok pracy niesie wskazowke, nazwe bloku i okno naraz`() {
        val workout = TestFixtures.emomWorkout().let { w ->
            w.copy(
                blocks = w.blocks.map { block ->
                    block.copy(label = "Minutówka", items = block.items.map { it.copy(note = "klatka do ziemi") })
                },
            )
        }
        val work = planner.expand(workout, TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
            .filterIsInstance<Step.Work>().first()
        assertEquals("klatka do ziemi", work.note)
        assertEquals("Minutówka", work.blockLabel)
        assertEquals(60, work.windowSec)
    }

    @Test
    fun `zero rest produces no rest step`() {
        val workout = TestFixtures.tabataWorkout().let { w ->
            w.copy(blocks = listOf(w.blocks[0].copy(restBetweenRoundsSec = 0, items = w.blocks[0].items.map { it.copy(restAfterItemSec = 0) })))
        }
        val steps = planner.expand(workout, TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        assertEquals(0, steps.count { it is Step.Rest })
    }

    @Test
    fun `merge keeps completed steps and continues from same position in new plan`() {
        val current = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        val replanned = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.ADVANCED, emptySet())
        // bieżący krok: 2. seria pompek (index: countdown=0, work1=1, rest=2, work2=3)
        val merged = planner.merge(current, stepIndex = 3, replanned = replanned)
        assertEquals(current.size, merged.size)
        assertEquals(current.take(3), merged.take(3))
        val work2 = merged[3] as Step.Work
        assertEquals(2, work2.setIndex)
        assertEquals(13, work2.targetValue)
        assertEquals("decline-pushups", work2.exerciseId)
        // wcześniejsze kroki bez zmian
        assertEquals(10, (merged[1] as Step.Work).targetValue)
    }

    @Test
    fun `merge during rest keeps the rest and replans following steps`() {
        val current = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        val replanned = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.BEGINNER, emptySet())
        val merged = planner.merge(current, stepIndex = 2, replanned = replanned)
        assertEquals(current[2], merged[2])
        assertEquals(7, (merged[3] as Step.Work).targetValue)
        assertEquals(40, (merged[4] as Step.Rest).seconds)
    }

    @Test
    fun `merge on countdown replaces whole plan`() {
        val current = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        val replanned = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.BEGINNER, emptySet())
        assertEquals(replanned, planner.merge(current, 0, replanned))
    }

    @Test
    fun `estimate counts reps at seconds per rep`() {
        val steps = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        // countdown 5 + pompki 3×10×3 s = 90 + przerwy 30+30 + 60 + deska 3×30 = 90 + przerwy 30+30 = 365
        assertEquals(365, planner.estimateSeconds(steps))
    }

    @Test
    fun `korekta trenera dziala po skalowaniu poziomem i dotyczy wariantu pokazanego na ekranie`() {
        val tuning = mapOf(
            "knee-pushups" to ExerciseTuning("knee-pushups", repsDelta = -2, secondsDelta = -5),
            "plank" to ExerciseTuning("plank", repsDelta = 2, secondsDelta = 5),
        )
        val works = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.BEGINNER, emptySet(), tuning = tuning)
            .filterIsInstance<Step.Work>()

        // pompki u początkującego to pompki na kolanach: 10 × 0.7 = 7, potem −2
        assertEquals("knee-pushups", works[0].exerciseId)
        assertEquals(5, works[0].targetValue)
        // deska: 30 s × 0.7 = 21 → 20, potem +5
        assertEquals(25, works[3].targetValue)

        // opinia o ćwiczeniu bazowym nie dotyczy wariantu, który player pokazuje
        val basePushups = mapOf("pushups" to ExerciseTuning("pushups", repsDelta = -2))
        val untouched = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.BEGINNER, emptySet(), tuning = basePushups)
        assertEquals(7, untouched.filterIsInstance<Step.Work>()[0].targetValue)

        assertEquals(
            planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.BEGINNER, emptySet()),
            planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.BEGINNER, emptySet(), tuning = emptyMap()),
        )
    }

    @Test
    fun `korekta trenera nie schodzi ponizej minimum`() {
        val tuning = mapOf(
            "pushups" to ExerciseTuning("pushups", repsDelta = -20, secondsDelta = -60),
            "plank" to ExerciseTuning("plank", secondsDelta = -60),
        )
        val works = planner.expand(TestFixtures.setsWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet(), tuning = tuning)
            .filterIsInstance<Step.Work>()

        assertEquals(1, works[0].targetValue)
        assertEquals(5, works[3].targetValue)
    }

    // --- stały protokół (D-37) ---------------------------------------------------------------------

    private fun fixedWorkout(restAfter: Int? = null) = pl.mysttic.trainings.domain.model.Workout(
        id = "w-fixed", name = "Stały", blocks = listOf(
            pl.mysttic.trainings.domain.model.Block(
                kind = pl.mysttic.trainings.domain.model.BlockKind.CIRCUIT, rounds = 8, restBetweenRoundsSec = 10, fixed = true,
                restAfterSec = restAfter,
                items = listOf(BlockItem("pushups", TargetKind.AMRAP, 20, restAfterItemSec = 10), BlockItem("climbers", TargetKind.SECONDS, 20)),
            ),
            pl.mysttic.trainings.domain.model.Block(
                kind = pl.mysttic.trainings.domain.model.BlockKind.SETS, fixed = true,
                items = listOf(BlockItem("plank", TargetKind.SECONDS, 90, sets = 1, restAfterItemSec = 0, openEnded = true), BlockItem("pushups", TargetKind.REPS, 10, sets = 2, restAfterSetSec = 30)),
            ),
        ), createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun `staly blok ignoruje poziom, wariant i korekte trenera`() {
        val tuning = mapOf("pushups" to ExerciseTuning("pushups", repsDelta = 4, secondsDelta = 10), "knee-pushups" to ExerciseTuning("knee-pushups", repsDelta = 4))
        val base = planner.expand(fixedWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        val beginner = planner.expand(fixedWorkout(), TestFixtures.exercises, Difficulty.BEGINNER, emptySet(), tuning = tuning)
        val advanced = planner.expand(fixedWorkout(), TestFixtures.exercises, Difficulty.ADVANCED, emptySet(), tuning = tuning)
        assertEquals(base, beginner)
        assertEquals(base, advanced)
        val works = base.filterIsInstance<Step.Work>()
        // bez podmiany wariantu: pompki zostają pompkami także dla początkującego
        assertTrue(works.all { it.exerciseId != "knee-pushups" && it.exerciseId != "decline-pushups" })
        assertEquals(20, works.first().targetValue)
        assertEquals(8, works.first().roundsInBlock)
        // przerwy bez skalowania: 10 s między pozycjami i rundami, domyślna minuta między blokami
        val rests = base.filterIsInstance<Step.Rest>()
        assertTrue(rests.filter { it.kind == RestKind.BETWEEN_ITEMS || it.kind == RestKind.BETWEEN_ROUNDS }.all { it.seconds == 10 })
        assertEquals(60, rests.single { it.kind == RestKind.BETWEEN_BLOCKS }.seconds)
        assertEquals(30, rests.single { it.kind == RestKind.BETWEEN_SETS }.seconds)
    }

    @Test
    fun `restAfterSec bloku zastepuje domyslna przerwe miedzy blokami, zero ja usuwa`() {
        val custom = planner.expand(fixedWorkout(restAfter = 15), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        assertEquals(15, custom.filterIsInstance<Step.Rest>().single { it.kind == RestKind.BETWEEN_BLOCKS }.seconds)
        val none = planner.expand(fixedWorkout(restAfter = 0), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet())
        assertTrue(none.filterIsInstance<Step.Rest>().none { it.kind == RestKind.BETWEEN_BLOCKS })
    }

    @Test
    fun `krok bez limitu nie ma czasu trwania, a estymata bierze jego cel`() {
        val steps = planner.expand(fixedWorkout(), TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet(), countdownSec = 0)
        val hold = steps.filterIsInstance<Step.Work>().single { it.openEnded }
        assertEquals("plank", hold.exerciseId)
        assertNull(hold.durationSec)
        assertEquals(90, hold.targetValue)
        // 16 okien × 20 s + 8 × 10 (między pozycjami) + 7 × 10 (między rundami) + 60 (między blokami) + 90 + 2 × 30 + 30
        assertEquals(320 + 80 + 70 + 60 + 90 + 60 + 30, planner.estimateSeconds(steps))
    }

    @Test
    fun `rytm trafia tylko do kroku na czas bez limitu`() {
        val workout = pl.mysttic.trainings.domain.model.Workout(
            id = "w-breath", name = "Oddech", blocks = listOf(
                pl.mysttic.trainings.domain.model.Block(
                    kind = pl.mysttic.trainings.domain.model.BlockKind.SETS, fixed = true,
                    items = listOf(
                        BlockItem("plank", TargetKind.SECONDS, 90, sets = 1, cadenceSec = 3),
                        BlockItem("plank", TargetKind.SECONDS, 90, sets = 1, cadenceSec = 3, openEnded = true),
                        BlockItem("pushups", TargetKind.REPS, 30, sets = 1, cadenceSec = 3),
                    ),
                ),
            ), createdAt = 0L, updatedAt = 0L,
        )
        val works = planner.expand(workout, TestFixtures.exercises, Difficulty.INTERMEDIATE, emptySet()).filterIsInstance<Step.Work>()
        assertEquals(3, works[0].cadenceSec)
        assertEquals(30, works[0].cadenceCount)
        assertNull(works[1].cadenceSec)
        assertNull(works[2].cadenceSec)
        assertEquals(0, works[2].cadenceCount)
    }

    @Test
    fun `shownTarget i shownExercise licza to samo co expand`() {
        val tuning = mapOf("pushups" to ExerciseTuning("pushups", repsDelta = 2))
        for (workout in listOf(TestFixtures.setsWorkout(), TestFixtures.tabataWorkout(), fixedWorkout())) {
            for (difficulty in Difficulty.entries) {
                for (owned in listOf(emptySet(), setOf(Equipment.BENCH, Equipment.PULL_UP_BAR))) {
                    val works = planner.expand(workout, TestFixtures.exercises, difficulty, owned, countdownSec = 0, tuning = tuning)
                        .filterIsInstance<Step.Work>()
                    for (work in works) {
                        val block = workout.blocks[work.blockIndex]
                        val item = block.items[work.itemIndex]
                        assertEquals(work.targetValue, planner.shownTarget(item, block, difficulty, tuning, TestFixtures.exercises, owned))
                        assertEquals(work.exerciseId, planner.shownExercise(item, block, difficulty, TestFixtures.exercises, owned)?.id)
                    }
                }
            }
        }
    }
}
