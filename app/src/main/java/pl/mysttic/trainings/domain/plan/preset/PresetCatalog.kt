package pl.mysttic.trainings.domain.plan.preset

import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.domain.plan.GeneratedPlan
import pl.mysttic.trainings.domain.plan.PlanDay
import pl.mysttic.trainings.domain.plan.TrainingProfile

/**
 * Katalog gotowych programów (D-37): druga, obok kreatora, droga do cyklu. Program to deterministyczna funkcja
 * (poziom, długość, parametr) → `GeneratedPlan`, zapisywana przez `ProgramRepository` jak plan z kreatora,
 * więc kolejka (D-21), „Dziś” (D-26) i historia nie wiedzą, skąd cykl pochodzi. Każdy etap progresji to osobny
 * szablon `Workout` o identyfikatorze `"$programId-s<n>"`; dzień cyklu wskazuje szablon etapu, a kolejka
 * odhacza je po kolei. Katalog żyje w kodzie, nie w seedzie ani w bazie.
 */
object PresetCatalog {

    /** Kolejność listy wyboru: od łagodnych do wymagających (D-46), w obrębie trudności od lżejszego. */
    val all: List<PresetDefinition> = listOf(
        Evening, Morning, WimHof, Squats, Abs, Pushups100, PlankAndBar, SevenMinutes, Tabata, BurpeeEmom, IronMan,
    )

    fun get(id: PresetId): PresetDefinition = all.first { it.id == id }

    fun get(presetId: String): PresetDefinition? =
        PresetId.entries.firstOrNull { it.name == presetId }?.let { get(it) }

    /** Dni treningowe cyklu o tej długości: numery dni od 1. */
    fun trainingDays(definition: PresetDefinition, cycleDays: Int): List<Int> =
        (1..cycleDays).filter { definition.pattern.isTrainingDay(it) }

    /**
     * Cykl z programu: jeden szablon na etap, gęsta lista dni (odpoczynek = `workoutId` null).
     * `now` trafia tylko do `createdAt`; poza tym wynik zależy wyłącznie od argumentów. Ćwiczenie, którego sprzętu
     * nie ma w [owned], dostaje zamiennik z [PresetDefinition.substitutes] (D-46), więc [owned] musi pochodzić
     * z ustawień, a [exercises] z biblioteki: bez ćwiczenia w mapie nie wiadomo, czego wymaga, i pozycja zostaje.
     */
    fun build(
        id: PresetId,
        level: Difficulty,
        cycleDays: Int,
        param: Int?,
        programId: String,
        now: Long,
        exercises: Map<String, Exercise>,
        owned: Set<Equipment>,
    ): GeneratedPlan {
        val definition = get(id)
        val trainingDays = trainingDays(definition, cycleDays)
        val sessions = trainingDays.size
        if (sessions == 0) return GeneratedPlan(emptyList(), emptyList())
        val stages = definition.stages(level, sessions, paramOrDefault(definition, param))
        if (stages.isEmpty()) return GeneratedPlan(emptyList(), emptyList())
        val count = stages.size
        val swaps = swaps(definition, level, exercises, owned)
        val workouts = stages.mapIndexed { index, stage ->
            val blocks = stage.blocks.map { block ->
                block.copy(fixed = true, items = block.items.map { item -> swaps[item.exerciseId]?.let { item.copy(exerciseId = it) } ?: item })
            }
            // Notatka mówi, co podmieniono, żeby opis protokołu nie rozjechał się z tym, co widać w treningu.
            val swapNote = swaps.filterKeys { from -> stage.blocks.any { block -> block.items.any { it.exerciseId == from } } }
                .entries.joinToString("; ") { (from, to) ->
                    "${exercises.getValue(from).namePl.lowercase()} → ${exercises.getValue(to).namePl.lowercase()}"
                }
            Workout(
                id = stageWorkoutId(programId, index + 1),
                name = "${definition.name} · etap ${index + 1} z $count",
                note = "Etap ${index + 1} z $count: ${stage.label}. " +
                    (if (swapNote.isEmpty()) "" else "Bez sprzętu: $swapNote. ") + FIXED_NOTE,
                blocks = blocks,
                createdAt = now,
                updatedAt = now,
                isBuiltIn = false,
            )
        }
        var session = 0
        val days = (1..cycleDays).map { day ->
            if (definition.pattern.isTrainingDay(day)) {
                session++
                PlanDay(day, workouts[Stages.stageIndex(session, sessions, count)].id)
            } else {
                PlanDay(day, null)
            }
        }
        val used = days.mapNotNull { it.workoutId }.toSet()
        return GeneratedPlan(workouts.filter { it.id in used }, days)
    }

    fun stageWorkoutId(programId: String, stage: Int): String = "$programId-s$stage"

    /** Numer etapu z identyfikatora treningu cyklu; null dla treningów spoza programu. */
    fun stageOf(programId: String, workoutId: String): Int? =
        workoutId.removePrefix("$programId-s").takeIf { it != workoutId }?.toIntOrNull()

    fun paramOrDefault(definition: PresetDefinition, param: Int?): Int? {
        val asks = definition.asks ?: return null
        return (param ?: asks.default).coerceIn(asks.range)
    }

    /**
     * Profil zapisywany z cyklem: poziom i długość z wyboru użytkownika, rytm z programu, reszta z ostatniego
     * profilu kreatora (sylwetka) albo domyślna. `sessionMinutes` to estymata pierwszego etapu.
     */
    fun profileFor(
        id: PresetId,
        level: Difficulty,
        cycleDays: Int,
        param: Int?,
        equipment: Set<Equipment>,
        firstStageMinutes: Int,
        base: TrainingProfile? = null,
    ): TrainingProfile {
        val definition = get(id)
        return TrainingProfile(
            heightCm = base?.heightCm ?: TrainingProfile().heightCm,
            weightKg = base?.weightKg ?: TrainingProfile().weightKg,
            goal = definition.goal,
            level = level,
            daysPerWeek = definition.pattern.daysPerWeek,
            sessionMinutes = firstStageMinutes.coerceAtLeast(1),
            cycleDays = cycleDays,
            equipment = equipment,
            targetScale = 1.0,
            presetId = id.name,
            presetParam = paramOrDefault(definition, param),
        )
    }

    /** Wszystkie ćwiczenia, jakich program może użyć na dowolnym poziomie, razem z zamiennikami bez sprzętu. */
    fun exerciseIds(definition: PresetDefinition): Set<String> =
        Difficulty.entries.flatMap { level -> idsAt(definition, level) + definition.substitutes(level).values }.toSet()

    /** Ćwiczenia z tabel etapów na jednym poziomie, bez zamienników. */
    private fun idsAt(definition: PresetDefinition, level: Difficulty): Set<String> =
        definition.stages(level, SAMPLE_SESSIONS, paramOrDefault(definition, null))
            .flatMap { stage -> stage.blocks.flatMap { block -> block.items.map { it.exerciseId } } }
            .toSet()

    /**
     * Podmiany na tym poziomie przy posiadanym sprzęcie: tylko dla ćwiczeń, których nie da się zrobić z [owned],
     * i tylko na zamiennik, który da się zrobić. Z pełnym sprzętem pusta, więc protokół zostaje nietknięty.
     */
    fun swaps(definition: PresetDefinition, level: Difficulty, exercises: Map<String, Exercise>, owned: Set<Equipment>): Map<String, String> =
        definition.substitutes(level).filter { (from, to) ->
            exercises[from]?.canDoWith(owned) == false && exercises[to]?.canDoWith(owned) == true
        }

    /**
     * Sprzęt, bez którego programu nie da się zrobić mimo zamienników (drążek w Desce i drążku), na dowolnym
     * poziomie. Niepusty = program niedostępny, dopóki sprzęt nie trafi do Ustawień (D-20: sprzęt to twardy filtr).
     */
    fun unmetEquipment(definition: PresetDefinition, exercises: Map<String, Exercise>, owned: Set<Equipment>): Set<Equipment> =
        Difficulty.entries.flatMap { level ->
            val swaps = swaps(definition, level, exercises, owned)
            idsAt(definition, level).filterNot { it in swaps }.mapNotNull { exercises[it] }.flatMap { it.missingEquipment(owned) }
        }.toSet()

    /** Ćwiczenia programu, których nie ma w bibliotece (np. przed importem seedu z oddechem). */
    fun missingExercises(definition: PresetDefinition, exercises: Map<String, Exercise>): Set<String> =
        exerciseIds(definition).filterNot { it in exercises }.toSet()

    /** Sprzęt wymagany przez ćwiczenia programu (suma po wszystkich poziomach). */
    fun equipmentFor(definition: PresetDefinition, exercises: Map<String, Exercise>): Set<Equipment> =
        exerciseIds(definition).mapNotNull { exercises[it] }.flatMap { it.equipment }.toSet()

    /**
     * Czego brakuje bazie ćwiczeń, żeby programy tej wersji działały: ćwiczenie istnieje i nie jest wycofane (D-47),
     * rodzaj celu pasuje do pozycji (D-27), oddech tylko w programie oddechowym (D-37), zamiennik bez sprzętu tej
     * samej partii i rodzaju (D-46). Pusta lista = baza pasuje. Woła ją `PresetSeedConsistencyTest` na `baza/`
     * i aplikacja na bazie pobranej z sieci, zanim ją wczyta.
     */
    fun libraryProblems(exercises: Map<String, Exercise>): List<String> {
        val problems = LinkedHashSet<String>()
        for (definition in all) {
            for (level in Difficulty.entries) {
                definition.stages(level, SAMPLE_SESSIONS, paramOrDefault(definition, null)).forEach { stage ->
                    stage.blocks.flatMap { it.items }.forEach { item ->
                        val where = "${definition.id}: ${item.exerciseId}"
                        val exercise = exercises[item.exerciseId]
                        if (exercise == null) {
                            problems += "$where nie ma w bazie"
                            return@forEach
                        }
                        val kindFits = when (exercise.kind) {
                            ExerciseKind.TIME -> item.targetKind == TargetKind.SECONDS
                            ExerciseKind.REPS -> item.targetKind == TargetKind.REPS || item.targetKind == TargetKind.AMRAP
                        }
                        if (!kindFits) problems += "$where: cel ${item.targetKind} nie pasuje do ${exercise.kind}"
                        if ((item.cadenceSec != null || item.openEnded) && item.targetKind != TargetKind.SECONDS) {
                            problems += "$where: rytm i krok bez limitu liczą sekundy"
                        }
                    }
                }
                definition.substitutes(level).forEach { (from, to) ->
                    val where = "${definition.id}: zamiennik $from → $to"
                    val original = exercises[from]
                    val substitute = exercises[to]
                    when {
                        original == null -> problems += "$where: $from nie ma w bazie"
                        substitute == null -> problems += "$where: $to nie ma w bazie"
                        else -> {
                            if (original.equipment.isEmpty()) problems += "$where: oryginał nie wymaga sprzętu"
                            if (substitute.equipment.isNotEmpty()) problems += "$where: zamiennik wymaga sprzętu"
                            if (original.kind != substitute.kind) problems += "$where: inny rodzaj celu"
                            if (original.primaryMuscles != substitute.primaryMuscles) problems += "$where: inne partie główne"
                            if (substitute.category == ExerciseCategory.BREATHING) problems += "$where: zamiennik to oddech"
                        }
                    }
                }
            }
            val breathingProgram = definition.kind == ProgramKind.BREATHING
            for (id in exerciseIds(definition)) {
                val exercise = exercises[id] ?: continue
                if (exercise.isRetired) problems += "${definition.id}: $id jest wycofane z bazy"
                if (breathingProgram != (exercise.category == ExerciseCategory.BREATHING)) {
                    problems += if (breathingProgram) "${definition.id}: $id to nie oddech" else "${definition.id}: $id to oddech poza programem oddechowym"
                }
            }
        }
        return problems.toList()
    }

    /** Czy trening oddechowy: wszystkie pozycje to ćwiczenia z kategorii oddech. */
    fun kindOf(profile: TrainingProfile): ProgramKind = get(profile.presetId.orEmpty())?.kind ?: ProgramKind.TRAINING

    /**
     * Estymata bloków w minutach tak, jak liczy ją player (stały protokół, więc poziom nie ma znaczenia).
     * Sprzęt nie jest potrzebny: przy INTERMEDIATE podmiana wariantu i tak nie zachodzi.
     */
    fun minutes(blocks: List<Block>, exercises: Map<String, Exercise>, countdownSec: Int = WorkoutPlanner.DEFAULT_COUNTDOWN_SEC): Int {
        val workout = Workout("tmp", "tmp", blocks = blocks, createdAt = 0L, updatedAt = 0L)
        val steps = PLANNER.expand(workout, exercises, Difficulty.INTERMEDIATE, emptySet(), countdownSec)
        return (PLANNER.estimateSeconds(steps) + 59) / 60
    }

    const val FIXED_NOTE = "Stały protokół programu: poziom i trener nie zmieniają celów"

    /** Liczba sesji do policzenia zbioru ćwiczeń; wystarczy, żeby każdy etap istniał. */
    private const val SAMPLE_SESSIONS = 12
    private val PLANNER = WorkoutPlanner()
}
