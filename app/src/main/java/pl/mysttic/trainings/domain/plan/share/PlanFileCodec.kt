package pl.mysttic.trainings.domain.plan.share

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import pl.mysttic.trainings.domain.editor.WorkoutEditing
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
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
import pl.mysttic.trainings.domain.plan.PlanDay
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.TrainingProfile
import java.security.MessageDigest
import pl.mysttic.trainings.domain.plan.PlanNotes

/** Plan odczytany z pliku, po walidacji: gotowy do podglądu i zapisu pod nowymi id. */
data class ImportedPlan(
    val uid: String,
    val name: String,
    val author: String,
    val description: String,
    val level: Difficulty,
    /** Dni z kluczami treningów z pliku („w1”) w miejscu id. */
    val days: List<PlanDay>,
    /** Treningi pod kluczami z pliku; id = klucz, zapis nada id planu. */
    val workouts: List<Workout>,
    /** Ćwiczenia, których odbiorca nie ma — trafią do biblioteki jako własne. */
    val newExercises: List<Exercise>,
    val seedVersion: Int,
    val warnings: List<String>,
    /** Notatki do ćwiczeń (D-44) pod id ćwiczeń u odbiorcy. */
    val notes: Map<String, String> = emptyMap(),
) {
    val trainingDays: Int get() = days.count { !it.isRest }
    val exerciseIds: Set<String> get() = workouts.flatMap { it.exerciseIds }.toSet()
}

/** Plik, którego nie da się wczytać; komunikat idzie wprost na ekran. */
class PlanFileException(message: String) : IllegalArgumentException(message)

/**
 * Zapis i odczyt pliku planu (D-42). Czysty Kotlin: eksport jest deterministyczny (klucze treningów w kolejności
 * dni), a odczyt traktuje plik jak wrogi — limity rozmiaru i liczby, przycinanie wartości tym samym sitem co edytor,
 * spójność odwołań, teksty bez znaków sterujących.
 */
object PlanFileCodec {

    const val MAX_DAYS = TrainingProfile.MAX_PLAN_DAYS
    const val MAX_WORKOUTS = 120
    const val MAX_BLOCKS = 20
    const val MAX_ITEMS = 30
    const val MAX_WORK_PER_WORKOUT = 500
    const val MAX_EXERCISES = 300

    /** Wartości domyślne ćwiczenia z pliku: seed ma 5..20 powtórzeń i 10..900 s (ergometry). */
    const val MAX_DEFAULT_REPS = 30
    const val MIN_DEFAULT_SECONDS = 5
    const val MAX_DEFAULT_SECONDS = 900
    private val UID = Regex("[a-z0-9-]{1,64}")
    private val EXERCISE_ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    const val CUSTOM_PREFIX = "custom-"

    // ---------------------------------------------------------------- eksport

    /**
     * Plan do pliku. Bloki, które u autora dopasowują się do poziomu, jadą tak, jak je widzi ([difficulty],
     * [owned] z jego ustawień, bez korekty trenera): odbiorca dostaje je dosłownie (bloki `fixed`), więc surowy
     * szablon INTERMEDIATE dałby mu inne ćwiczenia i liczby niż autorowi. Poziom w pliku mówi, dla kogo liczono.
     */
    fun export(
        program: Program,
        workouts: Map<String, Workout>,
        exercises: Map<String, Exercise>,
        uid: String,
        author: String,
        appVersion: String,
        seedVersion: Int,
        exportedAt: Long,
        difficulty: Difficulty,
        owned: Set<Equipment>,
        planner: WorkoutPlanner = WorkoutPlanner(),
        /** Notatki do ćwiczeń planu (D-44); jadą tylko te, których ćwiczenie jest w pliku. */
        notes: Map<String, String> = emptyMap(),
    ): PlanFile {
        val keys = LinkedHashMap<String, String>()
        program.days.sortedBy { it.dayIndex }.forEach { day ->
            val id = day.workoutId ?: return@forEach
            if (id !in keys && workouts.containsKey(id)) keys[id] = "w${keys.size + 1}"
        }
        val shown = keys.keys.associateWith { id -> planner.shownBlocks(workouts.getValue(id).blocks, difficulty, exercises, owned) }
        val adaptive = keys.keys.any { id -> workouts.getValue(id).blocks.any { !it.fixed } }
        val sharedWorkouts = keys.map { (id, key) ->
            val workout = workouts.getValue(id)
            SharedWorkout(key = key, name = workout.name, note = workout.note, blocks = shown.getValue(id).map { it.toShared() })
        }
        val used = shown.values.flatten().flatMap { block -> block.items.map { it.exerciseId } }.distinct().sorted()
        return PlanFile(
            format = PlanFile.FORMAT,
            formatVersion = PlanFile.VERSION,
            appVersion = appVersion,
            seedVersion = seedVersion,
            exportedAt = exportedAt,
            plan = SharedPlan(
                uid = uid,
                name = program.name,
                author = author,
                description = program.description,
                level = (if (adaptive) difficulty else program.profile.level).name,
                days = program.days.sortedBy { it.dayIndex }.map { SharedDay(it.dayIndex, it.workoutId?.let(keys::get)) },
                workouts = sharedWorkouts,
                exercises = used.mapNotNull { exercises[it]?.toShared() },
                notes = PlanNotes.forExercises(notes, used.toSet()).map { (id, note) -> SharedNote(id, note) },
            ),
        )
    }

    fun encode(file: PlanFile, json: Json): String = Json(json) { prettyPrint = true; encodeDefaults = true }
        .encodeToString(PlanFile.serializer(), file)

    /** Nazwa pliku: ASCII, bez znaków, które system plików albo komunikator by pokiereszował. */
    fun fileName(name: String): String {
        val ascii = java.text.Normalizer.normalize(name.replace('ł', 'l').replace('Ł', 'L'), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
            .ifEmpty { "plan" }
        return ascii + PlanFile.EXTENSION
    }

    // ---------------------------------------------------------------- odczyt

    /** Odczyt z tekstu z kontrolą formatu; walidację treści robi [validate]. */
    fun decode(text: String, json: Json): PlanFile {
        if (text.length > PlanFile.MAX_BYTES) throw PlanFileException("Plik jest za duży jak na plan treningowy.")
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw PlanFileException("To nie jest plik planu.")
        val format = (root["format"] as? JsonPrimitive)?.content
        if (format == null) {
            if (root.containsKey("app")) throw PlanFileException("To kopia zapasowa, nie plan. Wczytaj ją w Ustawieniach.")
            throw PlanFileException("To nie jest plik planu.")
        }
        if (format != PlanFile.FORMAT) throw PlanFileException("To nie jest plik planu tej aplikacji.")
        val version = (root["formatVersion"] as? JsonPrimitive)?.intOrNull
            ?: throw PlanFileException("Plik planu nie ma numeru wersji.")
        if (version > PlanFile.VERSION) {
            throw PlanFileException("Plan zapisano w nowszej wersji aplikacji. Zaktualizuj aplikację i spróbuj jeszcze raz.")
        }
        return runCatching { json.decodeFromJsonElement(PlanFile.serializer(), JsonObject(root)) }
            .getOrElse { throw PlanFileException("Plik planu jest uszkodzony.") }
    }

    /**
     * Treść pliku po sicie. `local` — ćwiczenia odbiorcy. Wynik ma klucze treningów z pliku zamiast id;
     * nowe id nada zapis (`PlanCopy`). Plan wchodzi dosłownie (D-42): wszystkie bloki `fixed`.
     */
    fun validate(file: PlanFile, local: Map<String, Exercise>): ImportedPlan {
        val plan = file.plan
        val warnings = ArrayList<String>()
        if (!UID.matches(plan.uid)) throw PlanFileException("Plik planu ma zły identyfikator.")
        val name = WorkoutEditing.cleanText(plan.name, 80).ifEmpty { throw PlanFileException("Plan nie ma nazwy.") }
        if (plan.days.isEmpty()) throw PlanFileException("Plan nie ma ani jednego dnia.")
        if (plan.workouts.size > MAX_WORKOUTS) throw PlanFileException("Plan ma za dużo treningów (najwyżej $MAX_WORKOUTS).")
        if (plan.exercises.size > MAX_EXERCISES) throw PlanFileException("Plan ma za dużo ćwiczeń (najwyżej $MAX_EXERCISES).")

        // Dni: numery 1..120 bez powtórzeń; luki to odpoczynek.
        val dayNumbers = plan.days.map { it.day }
        if (dayNumbers.any { it < 1 || it > MAX_DAYS }) throw PlanFileException("Plan może mieć od 1 do $MAX_DAYS dni.")
        if (dayNumbers.toSet().size != dayNumbers.size) throw PlanFileException("Plik planu powtarza ten sam dzień.")
        val keys = plan.workouts.map { it.key }
        if (keys.toSet().size != keys.size) throw PlanFileException("Plik planu powtarza ten sam trening.")
        val unknown = plan.days.mapNotNull { it.workout }.filter { it !in keys }
        if (unknown.isNotEmpty()) throw PlanFileException("Dzień planu wskazuje trening, którego nie ma w pliku.")

        // Ćwiczenia: lokalne mają pierwszeństwo, brakujące powstają z migawek, nieznane pozycje wypadają.
        val resolved = resolveExercises(plan.exercises, local, warnings)
        val available = local.keys + resolved.added.map { it.id }

        val usedKeys = plan.days.mapNotNull { it.workout }.toSet()
        val workouts = ArrayList<Workout>()
        plan.workouts.forEach { shared ->
            if (shared.key !in usedKeys) {
                warnings += "Trening „${shared.name.take(40)}” nie stoi w żadnym dniu — pominięty."
                return@forEach
            }
            val blocks = shared.blocks.take(MAX_BLOCKS).mapNotNull { block ->
                block.toBlock(resolved.idMap, available, warnings)
            }
            if (shared.blocks.size > MAX_BLOCKS) warnings += "Trening „${shared.name.take(40)}” miał za dużo bloków — przycięty."
            val workout = Workout(
                id = shared.key,
                name = WorkoutEditing.cleanText(shared.name, 80).ifEmpty { "Trening" },
                note = WorkoutEditing.cleanText(shared.note, 2000),
                blocks = blocks,
                createdAt = 0,
                updatedAt = 0,
            )
            when {
                blocks.isEmpty() -> warnings += "Trening „${workout.name}” nie ma ćwiczeń, które da się wczytać — jego dni będą odpoczynkiem."
                WorkoutEditing.workCount(blocks) > MAX_WORK_PER_WORKOUT ->
                    throw PlanFileException("Trening „${workout.name}” ma za dużo serii (najwyżej $MAX_WORK_PER_WORKOUT).")
                else -> workouts += workout
            }
        }
        val kept = workouts.map { it.id }.toSet()
        val keptExercises = workouts.flatMap { it.exerciseIds }.toSet()
        // Notatki (D-44) pod id ćwiczeń u odbiorcy, tylko do ćwiczeń, które naprawdę weszły do planu.
        val notes = PlanNotes.forExercises(
            plan.notes.take(PlanNotes.MAX_NOTES).associate { (resolved.idMap[it.exercise] ?: it.exercise) to it.note },
            keptExercises,
        )
        val maxDay = dayNumbers.max()
        val byNumber = plan.days.associateBy { it.day }
        val days = (1..maxDay).map { n -> PlanDay(n, byNumber[n]?.workout?.takeIf { it in kept }) }
        if (days.none { !it.isRest }) throw PlanFileException("W planie nie został ani jeden trening, który da się wczytać.")

        return ImportedPlan(
            uid = plan.uid,
            name = name,
            author = WorkoutEditing.cleanText(plan.author, 60),
            description = WorkoutEditing.cleanText(plan.description, 2000),
            level = Difficulty.entries.firstOrNull { it.name == plan.level } ?: Difficulty.INTERMEDIATE,
            days = days,
            workouts = workouts,
            newExercises = resolved.added.filter { added -> workouts.any { added.id in it.exerciseIds } },
            seedVersion = file.seedVersion,
            warnings = warnings.distinct().take(20),
            notes = notes,
        )
    }

    // ---------------------------------------------------------------- ćwiczenia

    private class Resolved(val idMap: Map<String, String>, val added: List<Exercise>)

    /**
     * Ćwiczenie z pliku: id z seedu, które odbiorca ma — bierzemy jego wersję (z ilustracją). Id z seedu, którego
     * nie ma (nowszy seed u autora) — wiersz własny pod tym samym id, który przejmie przyszła aktualizacja seedu.
     * Ćwiczenie własne autora (`custom-…`) — id ze skrótu treści: ta sama treść daje to samo id, bez zderzeń
     * z seedem i z ćwiczeniami innych autorów.
     */
    private fun resolveExercises(shared: List<SharedExercise>, local: Map<String, Exercise>, warnings: MutableList<String>): Resolved {
        val idMap = HashMap<String, String>()
        val added = LinkedHashMap<String, Exercise>()
        shared.forEach { s ->
            if (!EXERCISE_ID.matches(s.id) || s.id.length > 64) {
                warnings += "Ćwiczenie o złym identyfikatorze pominięte."
                return@forEach
            }
            val exercise = s.toExercise()
            if (exercise == null) {
                warnings += "Ćwiczenie „${s.namePl.take(40)}” ma nieznany rodzaj albo sprzęt — pominięte."
                return@forEach
            }
            val id = if (s.id.startsWith(CUSTOM_PREFIX)) customId(exercise) else s.id
            idMap[s.id] = id
            if (id !in local) added.putIfAbsent(id, exercise.copy(id = id))
        }
        return Resolved(idMap, added.values.toList())
    }

    /**
     * Skrót całej migawki: nazwy, rodzaj, kategoria, poziom, sprzęt, partie, wartości domyślne i instrukcje.
     * Różna treść daje różne id, więc dwa ćwiczenia autora nie sklejają się w jedno. Każde pole idzie z długością,
     * żeby znak rozdzielający w tekście nie zrobił z dwóch migawek jednej.
     */
    fun customId(exercise: Exercise): String {
        val fields = listOf(
            exercise.namePl, exercise.nameEn, exercise.kind.name, exercise.category.name, exercise.level.name,
            exercise.equipment.map { it.name }.sorted().joinToString(","),
            exercise.primaryMuscles.joinToString(",") { it.name },
            exercise.secondaryMuscles.joinToString(",") { it.name },
            exercise.defaultReps.toString(), exercise.defaultSeconds.toString(),
            exercise.instructionsPl.size.toString(),
        ) + exercise.instructionsPl
        val canonical = fields.joinToString("") { "${it.length}:$it" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return CUSTOM_PREFIX + digest.take(4).joinToString("") { "%02x".format(it) }
    }

    private fun SharedExercise.toExercise(): Exercise? {
        val kind = ExerciseKind.entries.firstOrNull { it.name == kind } ?: return null
        val category = ExerciseCategory.entries.firstOrNull { it.name == category } ?: return null
        // Sprzęt to zbiór wymagany w całości: nieznany element nie może zamienić ćwiczenia w „bez sprzętu”.
        val equipment = equipment.map { name -> Equipment.entries.firstOrNull { it.name == name } ?: return null }.toSet()
        return Exercise(
            id = id,
            nameEn = WorkoutEditing.cleanText(nameEn, 80),
            namePl = WorkoutEditing.cleanText(namePl, 80).ifEmpty { return null },
            kind = kind,
            level = Difficulty.entries.firstOrNull { it.name == level } ?: Difficulty.INTERMEDIATE,
            category = category,
            equipment = equipment,
            primaryMuscles = primaryMuscles.mapNotNull { m -> MuscleGroup.entries.firstOrNull { it.name == m } },
            secondaryMuscles = secondaryMuscles.mapNotNull { m -> MuscleGroup.entries.firstOrNull { it.name == m } },
            instructionsPl = instructionsPl.take(10).map { WorkoutEditing.cleanText(it, 500) }.filter { it.isNotEmpty() },
            images = emptyList(),
            // Widełki seedu, nie edytora: ćwiczenie z pliku może zostać zamiennikiem trenera albo sprzętu,
            // a wtedy cel pozycji bierze się z tych wartości.
            defaultReps = defaultReps.coerceIn(1, MAX_DEFAULT_REPS),
            defaultSeconds = defaultSeconds.coerceIn(MIN_DEFAULT_SECONDS, MAX_DEFAULT_SECONDS),
            isCustom = true,
        )
    }

    private fun Exercise.toShared() = SharedExercise(
        id = id,
        namePl = namePl,
        nameEn = nameEn,
        kind = kind.name,
        level = level.name,
        category = category.name,
        equipment = equipment.map { it.name }.sorted(),
        primaryMuscles = primaryMuscles.map { it.name },
        secondaryMuscles = secondaryMuscles.map { it.name },
        instructionsPl = instructionsPl,
        defaultReps = defaultReps,
        defaultSeconds = defaultSeconds,
    )

    // ---------------------------------------------------------------- bloki

    private fun Block.toShared() = SharedBlock(
        kind = kind.name,
        rounds = rounds,
        restBetweenRoundsSec = restBetweenRoundsSec,
        restAfterSec = restAfterSec,
        label = label,
        items = items.map { item ->
            SharedItem(
                exercise = item.exerciseId,
                target = item.targetKind.name,
                value = item.targetValue,
                sets = item.sets,
                restAfterSetSec = item.restAfterSetSec,
                restAfterItemSec = item.restAfterItemSec,
                weightKg = item.weightKg,
                openEnded = item.openEnded,
                cadenceSec = item.cadenceSec,
                note = item.note,
                windowSec = item.windowSec,
            )
        },
    )

    private fun SharedBlock.toBlock(idMap: Map<String, String>, available: Set<String>, warnings: MutableList<String>): Block? {
        val kind = BlockKind.entries.firstOrNull { it.name == kind } ?: run {
            warnings += "Blok nieznanego rodzaju pominięty."
            return null
        }
        if (items.size > MAX_ITEMS) warnings += "Blok miał za dużo ćwiczeń — przycięty do $MAX_ITEMS."
        val parsed = items.take(MAX_ITEMS).mapNotNull { item ->
            val target = TargetKind.entries.firstOrNull { it.name == item.target } ?: run {
                warnings += "Pozycja z nieznanym celem pominięta."
                return@mapNotNull null
            }
            val exerciseId = idMap[item.exercise] ?: item.exercise
            if (exerciseId !in available) {
                warnings += "Ćwiczenie „${item.exercise.take(40)}” nie jest znane — pozycja pominięta."
                return@mapNotNull null
            }
            WorkoutEditing.sanitize(
                BlockItem(
                    exerciseId = exerciseId,
                    targetKind = target,
                    targetValue = item.value,
                    sets = if (kind == BlockKind.SETS) item.sets else 1,
                    restAfterSetSec = item.restAfterSetSec,
                    restAfterItemSec = item.restAfterItemSec,
                    weightKg = item.weightKg,
                    openEnded = item.openEnded,
                    cadenceSec = item.cadenceSec,
                    note = item.note,
                    windowSec = item.windowSec,
                ),
            )
        }
        if (parsed.isEmpty()) return null
        // Plan od kogoś wchodzi dosłownie (D-42): wartości autora, bez poziomu, trenera i „Podbij cele”.
        return WorkoutEditing.sanitizeBlock(
            Block(
                kind = kind,
                rounds = rounds,
                restBetweenRoundsSec = restBetweenRoundsSec,
                items = parsed,
                fixed = true,
                restAfterSec = restAfterSec,
                label = label,
            ),
        )
    }
}
