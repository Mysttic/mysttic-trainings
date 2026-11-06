package pl.mysttic.trainings.domain.plan

import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Dzień cyklu: trening albo odpoczynek. `dayIndex` liczony od 1. */
data class PlanDay(val dayIndex: Int, val workoutId: String?) {
    val isRest: Boolean get() = workoutId == null
}

/** Wynik kreatora: szablony treningów (A/B/C…) i rozkład na dni cyklu. */
data class GeneratedPlan(
    val workouts: List<Workout>,
    val days: List<PlanDay>,
) {
    val sessionCount: Int get() = days.count { !it.isRest }
}

/** Rola w treningu: jaką partię ma zaadresować kolejne ćwiczenie. */
private enum class Slot(val muscles: List<MuscleGroup>, val label: String) {
    LEGS_PUSH(listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), "nogi"),
    HINGE(listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK), "biodra"),
    PUSH(listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS), "push"),
    PULL(listOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK, MuscleGroup.BICEPS), "pull"),
    CORE(listOf(MuscleGroup.ABS, MuscleGroup.LOWER_BACK), "tułów"),
    CARDIO(listOf(MuscleGroup.FULL_BODY, MuscleGroup.QUADS, MuscleGroup.CALVES), "kardio"),
}

/**
 * Kreator planu (M4, D-20): z profilu użytkownika i biblioteki ćwiczeń buduje cykl treningowy.
 *
 * Czysty Kotlin i pełna determinacja: te same dane wejściowe dają ten sam plan, więc da się go
 * przetestować i odtworzyć. Sprzęt jest twardym filtrem (nigdy nie proponujemy czegoś, czego nie ma),
 * poziom i BMI wpływają na dobór wariantów, cel na rodzaj bloków i długość przerw.
 *
 * Objętość trzyma się poziomu (D-34): liczba rund albo serii siedzi w widełkach [repeatsRange],
 * a zadeklarowany czas wypełniają kolejne ćwiczenia (do [MAX_ITEMS], w częściach po [BLOCK_ITEMS])
 * i przerwy, nigdy kolejne rundy tego samego. Dzięki temu początkujący na 15 minut dostaje cztery
 * ćwiczenia po trzy rundy, a nie dwa po siedem.
 */
object PlanGenerator {

    fun generate(
        profile: TrainingProfile,
        exercises: List<Exercise>,
        now: Long,
        idPrefix: String = "plan",
        /** Ćwiczenia, których użytkownik nie chce (trener, D-35): ani jako pozycja, ani jako wariant poziomu. */
        excluded: Set<String> = emptySet(),
    ): GeneratedPlan {
        val clean = TrainingProfile.sanitize(profile)
        // Wycofane z bazy (D-47) zostają dla historii i zapisanych planów, ale nowy plan ich nie dostaje.
        val usable = exercises.filter { it.canDoWith(clean.equipment) && it.id !in excluded && !it.isRetired }
        if (usable.isEmpty()) return GeneratedPlan(emptyList(), emptyList())
        // Pełna mapa, bo warianty poziomu (`easierId`/`harderId`) mogą wskazywać ćwiczenia spoza filtra sprzętu.
        val byId = exercises.associateBy { it.id }

        val templates = templatesFor(clean.daysPerWeek)
        val workouts = templates.mapIndexed { index, template ->
            buildWorkout(
                template = template,
                variantIndex = index,
                profile = clean,
                exercises = usable,
                byId = byId,
                excluded = excluded,
                id = "$idPrefix-${index + 1}",
                now = now,
            )
        }.filter { it.blocks.any { block -> block.items.isNotEmpty() } }

        if (workouts.isEmpty()) return GeneratedPlan(emptyList(), emptyList())
        return GeneratedPlan(workouts, schedule(clean, workouts.map { it.id }))
    }

    /** Nazwy i zestawy partii dla kolejnych treningów w rotacji. */
    private data class Template(val name: String, val slots: List<Slot>)

    private fun templatesFor(daysPerWeek: Int): List<Template> {
        val full = listOf(Slot.LEGS_PUSH, Slot.PUSH, Slot.PULL, Slot.HINGE, Slot.CORE, Slot.CARDIO)
        val upper = listOf(Slot.PUSH, Slot.PULL, Slot.PUSH, Slot.PULL, Slot.CORE)
        val lower = listOf(Slot.LEGS_PUSH, Slot.HINGE, Slot.LEGS_PUSH, Slot.CORE, Slot.CARDIO)
        return when (daysPerWeek) {
            2 -> listOf(Template("Całe ciało A", full), Template("Całe ciało B", full))
            3 -> listOf(Template("Całe ciało A", full), Template("Całe ciało B", full), Template("Całe ciało C", full))
            4 -> listOf(Template("Góra A", upper), Template("Dół A", lower), Template("Góra B", upper), Template("Dół B", lower))
            5 -> listOf(
                Template("Góra A", upper), Template("Dół A", lower), Template("Całe ciało", full),
                Template("Góra B", upper), Template("Dół B", lower),
            )
            else -> listOf(
                Template("Góra A", upper), Template("Dół A", lower), Template("Całe ciało A", full),
                Template("Góra B", upper), Template("Dół B", lower), Template("Całe ciało B", full),
            )
        }
    }

    private fun buildWorkout(
        template: Template,
        variantIndex: Int,
        profile: TrainingProfile,
        exercises: List<Exercise>,
        byId: Map<String, Exercise>,
        excluded: Set<String>,
        id: String,
        now: Long,
    ): Workout {
        val pool = poolFor(template.slots, variantIndex, profile, exercises, byId, excluded)
        val blocks = fitToTime(pool, profile, byId)
        return Workout(
            id = id,
            name = template.name,
            note = noteFor(profile, blocks, byId),
            blocks = blocks,
            createdAt = now,
            updatedAt = now,
            isBuiltIn = false,
        )
    }

    /**
     * Uporządkowana lista ćwiczeń do wzięcia: role idą w kółko (nogi, push, pull, …), aż zbierze się
     * [MAX_ITEMS] albo żadna rola nie ma już kandydata. Rola bez kandydata (pull bez sprzętu) jest
     * pomijana, a nie zostawia dziury — o liczbie ćwiczeń decyduje potem czas, nie liczba ról.
     */
    private fun poolFor(
        slots: List<Slot>,
        variantIndex: Int,
        profile: TrainingProfile,
        exercises: List<Exercise>,
        byId: Map<String, Exercise>,
        excluded: Set<String>,
    ): List<Exercise> {
        val picked = ArrayList<Exercise>()
        val used = HashSet<String>()
        val usedMuscles = LinkedHashSet<MuscleGroup>()
        var progress = true
        while (progress && picked.size < MAX_ITEMS) {
            progress = false
            for (slot in slots) {
                if (picked.size >= MAX_ITEMS) break
                val exercise = pick(slot, profile, exercises, byId, used, usedMuscles, variantIndex, excluded) ?: continue
                val shown = PLANNER.resolveVariant(exercise, profile.level, byId, profile.equipment)
                picked += exercise
                used += exercise.id
                used += shown.id
                usedMuscles += shown.primaryMuscles
                progress = true
            }
        }
        return picked
    }

    /**
     * Wybór ćwiczenia do roli: filtr sprzętu jest już zrobiony, tu decyduje partia, poziom,
     * cel i BMI. `variantIndex` przesuwa wybór, żeby treningi w rotacji nie były identyczne.
     *
     * Kandydata ocenia się po tym, co użytkownik zobaczy na tym poziomie ([WorkoutPlanner.resolveVariant]):
     * dwa ćwiczenia, które po podmianie wariantu są tym samym, nie wchodzą oba. Wariant wymagający sprzętu,
     * którego nie ma, nie wchodzi w ogóle — kandydat zostaje w wersji bazowej z szablonu.
     * Początkujący nie dostaje ćwiczeń o dwa poziomy za trudnych.
     */
    private fun pick(
        slot: Slot,
        profile: TrainingProfile,
        exercises: List<Exercise>,
        byId: Map<String, Exercise>,
        used: Set<String>,
        usedMuscles: Set<MuscleGroup>,
        variantIndex: Int,
        excluded: Set<String>,
    ): Exercise? {
        val candidates = exercises
            .asSequence()
            .filter { it.id !in used }
            // Oddech (D-37) żyje tylko w gotowych programach: kreator nigdy go nie proponuje.
            .filter { it.category != ExerciseCategory.BREATHING }
            // Ergometry (D-44) są do treningów układanych w edytorze; kreator zostaje przy ćwiczeniach bez maszyn.
            .filter { !it.usesErgometer }
            .filter { exercise -> exercise.primaryMuscles.any { it in slot.muscles } }
            .filter { it.level.level - profile.level.level <= 1 }
            .filter { slot != Slot.CARDIO || it.category == ExerciseCategory.CARDIO || it.category == ExerciseCategory.PLYOMETRICS }
            .filter { profile.goal != Goal.MOBILITY || it.category != ExerciseCategory.PLYOMETRICS }
            // Rozciąganie to ćwiczenie uzupełniające; jako główna pozycja ma sens tylko przy celu „mobilność”.
            .filter { profile.goal == Goal.MOBILITY || it.category != ExerciseCategory.STRETCHING }
            .map { it to PLANNER.resolveVariant(it, profile.level, byId, profile.equipment) }
            .filter { (_, shown) -> shown.id !in used && shown.id !in excluded && !shown.isRetired }
            .filter { (base, shown) ->
                !profile.avoidJumping || (base.category != ExerciseCategory.PLYOMETRICS && shown.category != ExerciseCategory.PLYOMETRICS)
            }
            .sortedWith(
                // Najpierw partia, której jeszcze w tym treningu nie było — inaczej dwa razy z rzędu wypada biceps.
                compareBy<Pair<Exercise, Exercise>> { (_, shown) -> if (shown.primaryMuscles.any { it !in usedMuscles }) 0 else 1 }
                    // Mobilność to przede wszystkim rozciąganie, więc tu cel wygrywa z dopasowaniem poziomu.
                    .thenBy { (base, _) -> if (profile.goal == Goal.MOBILITY && goalFavours(base, profile.goal)) 0 else 1 }
                    .thenBy { (base, _) -> abs(base.level.level - profile.level.level) }
                    .thenBy { (base, _) -> if (goalFavours(base, profile.goal)) 0 else 1 }
                    .thenBy { (base, _) -> base.equipment.size }
                    .thenBy { (base, _) -> base.id },
            )
            .toList()
        if (candidates.isEmpty()) return null
        // Rotacja wariantów chodzi po ćwiczeniach ze zdjęciem; bez zdjęcia sięgamy dopiero, gdy nie ma czym
        // wypełnić roli. W playerze zdjęcie niesie instrukcję nie gorzej niż opis.
        val pool = candidates.filter { (base, _) -> base.images.isNotEmpty() }.ifEmpty { candidates }
        return pool[variantIndex % pool.size].first
    }

    private fun goalFavours(exercise: Exercise, goal: Goal): Boolean = when (goal) {
        Goal.STRENGTH -> exercise.category == ExerciseCategory.STRENGTH
        Goal.CONDITION -> exercise.category == ExerciseCategory.CARDIO || exercise.category == ExerciseCategory.STRENGTH
        Goal.FAT_LOSS -> exercise.category == ExerciseCategory.CARDIO || exercise.category == ExerciseCategory.PLYOMETRICS
        Goal.MOBILITY -> exercise.category == ExerciseCategory.STRETCHING
    }

    /**
     * Ile rund obwodu albo serii każdego ćwiczenia mieści się w jednym treningu na danym poziomie.
     * To twarde widełki: czas dociąga się ćwiczeniami i przerwami, nie kolejnymi rundami.
     */
    /**
     * Ćwiczenia, z których kreator może układać plan przy danym sprzęcie (D-57: licznik w kroku sprzętu). Te same
     * reguły co w [generate] i doborze ćwiczeń: wykonalne z posiadanym sprzętem, niewycofane, bez ćwiczeń oddechowych
     * i bez ergometrów (D-44). Ćwiczenia z „nie chcę” trenera nie są tu odejmowane: licznik mówi o sprzęcie.
     */
    fun candidates(exercises: List<Exercise>, equipment: Set<Equipment>): List<Exercise> =
        exercises.filter {
            it.canDoWith(equipment) && !it.isRetired && it.category != ExerciseCategory.BREATHING && !it.usesErgometer
        }

    fun repeatsRange(level: Difficulty, goal: Goal): IntRange {
        val byLevel = when (level) {
            Difficulty.BEGINNER -> 2..3
            Difficulty.INTERMEDIATE -> 3..4
            Difficulty.ADVANCED -> 3..5
        }
        // Mobilność to spokojne tempo: od dwóch rund, ale nie więcej niż cztery.
        return if (goal == Goal.MOBILITY) 2..min(byLevel.last, MOBILITY_MAX_REPEATS) else byLevel
    }

    /**
     * Dobiera liczbę ćwiczeń, rund i przerw tak, żeby trafić w zadeklarowany czas ([TIME_TOLERANCE_SEC]).
     * Przegląd jest wyczerpujący i w stałej kolejności (rundy rosnąco, potem liczba ćwiczeń rosnąco,
     * remis wygrywa pierwszy), więc generator zostaje deterministyczny, a przy równym trafieniu wybiera
     * mniejszą objętość. Gdy biblioteka nie ma dość ćwiczeń, żeby wypełnić czas, trening wychodzi krótszy —
     * to lepsze niż dokładanie rund ponad widełki poziomu.
     *
     * Estymata liczy powtórzenia po 3 s (`WorkoutPlanner.estimateSeconds`), więc trening na powtórzenia
     * trafia w czas planu, a nie w stoper: realny czas zależy od tempa ćwiczącego.
     */
    private fun fitToTime(pool: List<Exercise>, profile: TrainingProfile, byId: Map<String, Exercise>): List<Block> {
        if (pool.isEmpty()) return emptyList()
        val target = profile.sessionMinutes * 60
        var best: List<Block> = emptyList()
        var bestMiss = Int.MAX_VALUE
        for (repeats in repeatsRange(profile.level, profile.goal)) {
            for (count in min(MIN_ITEMS, pool.size)..min(MAX_ITEMS, pool.size)) {
                val tuned = tuneRests(blocksFor(pool.take(count), repeats, profile), profile, byId, target)
                val miss = abs(estimateSeconds(tuned, profile, byId) - target)
                if (miss < bestMiss) {
                    best = tuned
                    bestMiss = miss
                }
            }
        }
        return best
    }

    /** Lista ćwiczeń pocięta na części po najwyżej [BLOCK_ITEMS], możliwie równe; każda część to osobny blok. */
    private fun blocksFor(exercises: List<Exercise>, repeats: Int, profile: TrainingProfile): List<Block> {
        val parts = (exercises.size + BLOCK_ITEMS - 1) / BLOCK_ITEMS
        val base = exercises.size / parts
        val extra = exercises.size % parts
        var from = 0
        return List(parts) { part ->
            val size = base + if (part < extra) 1 else 0
            val slice = exercises.subList(from, from + size)
            from += size
            blockFor(slice, repeats, profile)
        }
    }

    /** Siła to serie proste, reszta celów to obwód. */
    private fun blockFor(exercises: List<Exercise>, repeats: Int, profile: TrainingProfile): Block {
        val items = exercises.map { itemFor(it, profile, repeats) }
        return when (profile.goal) {
            Goal.STRENGTH -> Block(kind = BlockKind.SETS, items = items)
            else -> Block(
                kind = BlockKind.CIRCUIT,
                rounds = repeats,
                restBetweenRoundsSec = if (profile.goal == Goal.FAT_LOSS) 45 else 60,
                items = items,
            )
        }
    }

    /**
     * Cel ćwiczenia: powtórzenia wszędzie, gdzie ćwiczenie ma je w naturze (przysiady, pompki),
     * odliczanie czasu tylko dla izometrii i cardio (deska, wspinaczka, skakanka). Dotyczy każdego celu,
     * bo „15 przysiadów” czyta się lepiej niż „40 sekund przysiadów”, także w obwodzie.
     * Obie wartości biorą się z ćwiczenia (`defaultReps`, `defaultSeconds`): hollow body ma 20 s, deska 40 s.
     */
    private fun itemFor(exercise: Exercise, profile: TrainingProfile, repeats: Int): BlockItem {
        val onTime = exercise.kind == ExerciseKind.TIME
        val strength = profile.goal == Goal.STRENGTH
        // Punkt startowy przerw; [tuneRests] przesuwa je w widełkach, żeby trafić w czas.
        val restAfterItem = when (profile.goal) {
            Goal.STRENGTH -> 60
            Goal.CONDITION -> 20
            Goal.FAT_LOSS -> 15
            Goal.MOBILITY -> 15
        }
        return BlockItem(
            exerciseId = exercise.id,
            targetKind = if (onTime) TargetKind.SECONDS else TargetKind.REPS,
            targetValue = scaled(if (onTime) secondsFor(exercise, profile.goal) else exercise.defaultReps, profile.targetScale, onTime),
            sets = if (strength) repeats else 1,
            restAfterSetSec = 75,
            restAfterItemSec = restAfterItem,
        )
    }

    /**
     * Bazowy czas pracy pozycji na czas: z ćwiczenia; kardio przy redukcji trochę dłużej,
     * rozciąganie przy celu „mobilność” trzymane dłużej (45 s zamiast 30 s).
     */
    private fun secondsFor(exercise: Exercise, goal: Goal): Int {
        val bonus = when {
            goal == Goal.FAT_LOSS && exercise.category == ExerciseCategory.CARDIO -> 10
            goal == Goal.MOBILITY && exercise.category == ExerciseCategory.STRETCHING -> 15
            else -> 0
        }
        return (exercise.defaultSeconds + bonus).coerceIn(MIN_WORK_SEC, MAX_WORK_SEC)
    }

    /** Cele po progresji: powtórzenia do pełnych sztuk, sekundy do pięciu, zawsze co najmniej minimum. */
    private fun scaled(base: Int, scale: Double, onTime: Boolean): Int {
        val value = base * scale
        return if (onTime) (value / 5).roundToInt().times(5).coerceAtLeast(5) else value.roundToInt().coerceAtLeast(1)
    }

    /**
     * Dostraja przerwy i czas pracy przy ustalonej liście ćwiczeń i rund. Kolejność jest stała, żeby
     * generator został deterministyczny: czas pracy, przerwa między ćwiczeniami, przerwa między rundami
     * (albo seriami) — i drugi przebieg, bo parametry wpływają na siebie. Wszystkie bloki dostają te same wartości.
     */
    private fun tuneRests(blocks: List<Block>, profile: TrainingProfile, byId: Map<String, Exercise>, target: Int): List<Block> {
        fun miss(candidate: List<Block>) = abs(estimateSeconds(candidate, profile, byId) - target)
        // Pierwszy z listy wygrywa remis, a listy są budowane w stałej kolejności — stąd determinizm.
        fun bestOf(current: List<Block>, options: List<List<Block>>): List<Block> =
            (listOf(current) + options).minByOrNull { miss(it) } ?: current

        var current = blocks
        repeat(2) {
            current = bestOf(current, workOptions(current, profile, byId))
            current = bestOf(current, itemRestOptions(current))
            current = bestOf(current, repeatRestOptions(current))
        }
        return current
    }

    /** Długość pracy na czas, kilka kroków wokół bazy z ćwiczenia; pozycje na powtórzenia zostają nietknięte. */
    private fun workOptions(blocks: List<Block>, profile: TrainingProfile, byId: Map<String, Exercise>): List<List<Block>> {
        if (blocks.none { block -> block.items.any { it.targetKind == TargetKind.SECONDS } }) return emptyList()
        return WORK_DELTAS.map { delta ->
            blocks.map { block ->
                block.copy(
                    items = block.items.map { item ->
                        val exercise = byId[item.exerciseId]
                        if (item.targetKind != TargetKind.SECONDS || exercise == null) {
                            item
                        } else {
                            val seconds = (secondsFor(exercise, profile.goal) + delta).coerceIn(MIN_WORK_SEC, MAX_WORK_SEC)
                            item.copy(targetValue = scaled(seconds, profile.targetScale, onTime = true))
                        }
                    },
                )
            }
        }
    }

    private fun itemRestOptions(blocks: List<Block>): List<List<Block>> {
        val range = if (blocks.first().kind == BlockKind.CIRCUIT) CIRCUIT_ITEM_REST else SETS_REST
        return (range.first..range.last step 5).map { rest ->
            blocks.map { block -> block.copy(items = block.items.map { it.copy(restAfterItemSec = rest) }) }
        }
    }

    private fun repeatRestOptions(blocks: List<Block>): List<List<Block>> = when (blocks.first().kind) {
        BlockKind.CIRCUIT -> (CIRCUIT_ROUND_REST.first..CIRCUIT_ROUND_REST.last step 5).map { rest ->
            blocks.map { it.copy(restBetweenRoundsSec = rest) }
        }
        BlockKind.SETS -> (SETS_REST.first..SETS_REST.last step 5).map { rest ->
            blocks.map { block -> block.copy(items = block.items.map { it.copy(restAfterSetSec = rest) }) }
        }
    }

    private fun estimateSeconds(blocks: List<Block>, profile: TrainingProfile, byId: Map<String, Exercise>): Int {
        val workout = Workout("tmp", "tmp", blocks = blocks, createdAt = 0, updatedAt = 0)
        return PLANNER.estimateSeconds(PLANNER.expand(workout, byId, profile.level, profile.equipment))
    }

    private fun noteFor(profile: TrainingProfile, blocks: List<Block>, byId: Map<String, Exercise>): String {
        val base = "${profile.goal.labelPl}, ok. ${profile.sessionMinutes} min, poziom ${profile.level.labelPl.lowercase()}"
        val scale = if (profile.targetScale == 1.0) "" else ", ${PlanProgression.scaleLabel(profile.targetScale)}"
        val estimate = estimateSeconds(blocks, profile, byId)
        val shortfall = if (abs(estimate - profile.sessionMinutes * 60) > TIME_TOLERANCE_SEC) {
            ", wyszło ok. ${(estimate + 30) / 60} min, bo na ten sprzęt nie ma więcej ćwiczeń"
        } else {
            ""
        }
        return "$base$scale$shortfall."
    }

    /**
     * Rozkład treningów na dni cyklu: dni treningowe rozłożone równomiernie w każdym tygodniu,
     * treningi z rotacji idą po kolei. Reszta dni to odpoczynek.
     */
    private fun schedule(profile: TrainingProfile, workoutIds: List<String>): List<PlanDay> {
        val trainingDaysOfWeek = weekPattern(profile.daysPerWeek)
        var next = 0
        return (1..profile.cycleDays).map { day ->
            val dayOfWeek = (day - 1) % 7
            if (dayOfWeek in trainingDaysOfWeek) {
                val id = workoutIds[next % workoutIds.size]
                next++
                PlanDay(day, id)
            } else {
                PlanDay(day, null)
            }
        }
    }

    /** Indeksy dni tygodnia (0 = pierwszy dzień cyklu) rozłożone możliwie równo. */
    private fun weekPattern(daysPerWeek: Int): Set<Int> = when (daysPerWeek) {
        2 -> setOf(0, 3)
        3 -> setOf(0, 2, 4)
        4 -> setOf(0, 1, 3, 5)
        5 -> setOf(0, 1, 2, 4, 5)
        else -> setOf(0, 1, 2, 3, 4, 5)
    }

    /** Ile treningów wypadnie w cyklu przy tych ustawieniach — do podglądu w kreatorze. */
    fun sessionsInCycle(profile: TrainingProfile): Int {
        val clean = TrainingProfile.sanitize(profile)
        val pattern = weekPattern(clean.daysPerWeek)
        return (1..clean.cycleDays).count { (it - 1) % 7 in pattern }
    }

    /** Szacowany tygodniowy czas pracy w minutach — pokazywany w kreatorze. */
    fun weeklyMinutes(profile: TrainingProfile): Int {
        val clean = TrainingProfile.sanitize(profile)
        return (clean.daysPerWeek * clean.sessionMinutes.toDouble()).roundToInt()
    }

    /** Trafiamy w zadeklarowany czas z dokładnością do minuty. */
    const val TIME_TOLERANCE_SEC = 60

    /** Najmniej ćwiczeń w treningu (gdy biblioteka ma tyle) i najwięcej — dalej czas dociągają przerwy. */
    const val MIN_ITEMS = 4
    const val MAX_ITEMS = 16

    /** Najdłuższa część treningu; dłuższa lista dzieli się na bloki z minutą przerwy między nimi. */
    const val BLOCK_ITEMS = 6

    private val PLANNER = WorkoutPlanner()
    private const val MOBILITY_MAX_REPEATS = 4
    private const val MIN_WORK_SEC = 20
    private const val MAX_WORK_SEC = 60
    private val WORK_DELTAS = listOf(-10, -5, 5, 10)
    private val CIRCUIT_ITEM_REST = 10..30
    private val CIRCUIT_ROUND_REST = 30..90
    private val SETS_REST = 30..90
}
