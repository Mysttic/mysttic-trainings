package pl.mysttic.trainings.domain.engine

import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout

object TestFixtures {

    fun exercise(
        id: String,
        namePl: String = id,
        kind: ExerciseKind = ExerciseKind.REPS,
        level: Difficulty = Difficulty.INTERMEDIATE,
        category: ExerciseCategory = ExerciseCategory.STRENGTH,
        equipment: Set<Equipment> = emptySet(),
        primary: List<MuscleGroup> = listOf(MuscleGroup.CHEST),
        easierId: String? = null,
        harderId: String? = null,
    ) = Exercise(
        id = id, nameEn = id, namePl = namePl, kind = kind, level = level, category = category,
        equipment = equipment, primaryMuscles = primary, secondaryMuscles = emptyList(),
        instructionsPl = listOf("krok 1"), images = emptyList(), easierId = easierId, harderId = harderId,
    )

    val kneePushups = exercise("knee-pushups", "Pompki na kolanach", level = Difficulty.BEGINNER, harderId = "pushups")
    val pushups = exercise("pushups", "Pompki", easierId = "knee-pushups", harderId = "decline-pushups")
    val declinePushups = exercise("decline-pushups", "Pompki z nogami wyżej", level = Difficulty.ADVANCED, easierId = "pushups")
    val squats = exercise("squats", "Przysiady", level = Difficulty.BEGINNER, primary = listOf(MuscleGroup.QUADS))
    val plank = exercise("plank", "Deska", kind = ExerciseKind.TIME, level = Difficulty.BEGINNER, primary = listOf(MuscleGroup.ABS))
    val climbers = exercise("climbers", "Wspinaczka", kind = ExerciseKind.TIME, category = ExerciseCategory.CARDIO, primary = listOf(MuscleGroup.QUADS))

    /** Para „bez sprzętu → ze sprzętem”: trudniejszy wariant wymaga drążka, łatwiejszy niczego. */
    val legRaises = exercise("leg-raises", "Wznosy nóg", level = Difficulty.BEGINNER, primary = listOf(MuscleGroup.ABS), harderId = "hanging-leg-raises")
    val hangingLegRaises = exercise(
        "hanging-leg-raises", "Wznosy nóg w zwisie", level = Difficulty.ADVANCED,
        equipment = setOf(Equipment.PULL_UP_BAR), primary = listOf(MuscleGroup.ABS), easierId = "leg-raises",
    )

    /** Lustrzana para w drugą stronę: łatwiejszy wariant wymaga sprzętu. */
    val benchPushups = exercise("bench-pushups", "Pompki o ławkę", level = Difficulty.BEGINNER, equipment = setOf(Equipment.BENCH), harderId = "wall-pushups")
    val wallPushups = exercise("wall-pushups", "Pompki o ścianę", easierId = "bench-pushups")

    val exercises: Map<String, Exercise> = listOf(
        kneePushups, pushups, declinePushups, squats, plank, climbers,
        legRaises, hangingLegRaises, benchPushups, wallPushups,
    ).associateBy { it.id }

    /** EMOM: 4 rundy po 5 powtórzeń w oknie minuty; przerwę dopełnia silnik. */
    fun emomWorkout(windowSec: Int = 60, rounds: Int = 4) = Workout(
        id = "w-emom", name = "EMOM", blocks = listOf(
            Block(
                kind = BlockKind.CIRCUIT, rounds = rounds, restBetweenRoundsSec = 45, fixed = true,
                items = listOf(BlockItem("pushups", TargetKind.REPS, 5, restAfterItemSec = 0, windowSec = windowSec)),
            ),
        ), createdAt = 0L, updatedAt = 0L,
    )

    /** 2 ćwiczenia × 3 serie, przerwy 30 s między seriami, 60 s między ćwiczeniami. */
    fun setsWorkout() = Workout(
        id = "w-sets", name = "Serie", blocks = listOf(
            Block(
                kind = BlockKind.SETS, items = listOf(
                    BlockItem("pushups", TargetKind.REPS, 10, sets = 3, restAfterSetSec = 30, restAfterItemSec = 60),
                    BlockItem("plank", TargetKind.SECONDS, 30, sets = 3, restAfterSetSec = 30, restAfterItemSec = 60),
                ),
            ),
        ), createdAt = 0L, updatedAt = 0L,
    )

    /** Tabata: 4 rundy × 2 ćwiczenia po 20 s, 10 s przerwy między ćwiczeniami i rundami. */
    fun tabataWorkout() = Workout(
        id = "w-tabata", name = "Tabata", blocks = listOf(
            Block(
                kind = BlockKind.CIRCUIT, rounds = 4, restBetweenRoundsSec = 10, items = listOf(
                    BlockItem("climbers", TargetKind.SECONDS, 20, restAfterItemSec = 10),
                    BlockItem("squats", TargetKind.SECONDS, 20, restAfterItemSec = 10),
                ),
            ),
        ), createdAt = 0L, updatedAt = 0L,
    )

    fun twoBlockWorkout() = Workout(
        id = "w-two", name = "Dwa bloki", blocks = setsWorkout().blocks + tabataWorkout().blocks,
        createdAt = 0L, updatedAt = 0L,
    )
}
