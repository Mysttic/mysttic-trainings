package pl.mysttic.trainings.domain.plan

import pl.mysttic.trainings.domain.coach.ExerciseTuning
import pl.mysttic.trainings.domain.engine.DifficultyProfile
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.TargetKind

/**
 * Propozycja zmiany celu jednego ćwiczenia w szablonie treningu.
 * `swapToExerciseId != null` znaczy, że cel dobił do limitu i czas na trudniejszy wariant.
 *
 * `from` i `to` są w wartościach, które użytkownik widzi na ekranie (po skalowaniu poziomem i korekcie trenera),
 * a `rawTo` to liczba zapisywana w szablonie, który jest pisany dla INTERMEDIATE.
 */
data class TargetSuggestion(
    val blockIndex: Int,
    val itemIndex: Int,
    val exerciseId: String,
    val exerciseName: String,
    val targetKind: TargetKind,
    val from: Int,
    val to: Int,
    val rawTo: Int = to,
    val swapToExerciseId: String? = null,
    val swapToName: String? = null,
) {
    val isSwap: Boolean get() = swapToExerciseId != null

    /** „Pompki: 12 → 13 powtórzeń” albo „Deska: przejdź na Deska bokiem”. */
    fun describe(): String {
        val unit = if (targetKind == TargetKind.SECONDS || targetKind == TargetKind.AMRAP) "s" else "powt."
        return if (isSwap) {
            "$exerciseName → ${swapToName.orEmpty()} ($to $unit)"
        } else {
            "$exerciseName: $from → $to $unit"
        }
    }
}

/**
 * Progresja celów pojedynczych ćwiczeń (M5): jeśli w poprzednim wykonaniu wszystkie serie ćwiczenia
 * wyszły na cel, dokładamy powtórzenie albo pięć sekund. Po dobiciu do limitu proponujemy trudniejszy
 * wariant (`harderId`) zamiast rozciągania serii w nieskończoność.
 *
 * To progresja per ćwiczenie, w odróżnieniu od [PlanProgression], która skaluje cały kolejny cykl.
 */
object SetProgression {

    const val REPS_LIMIT = 20
    const val SECONDS_LIMIT = 90
    const val REPS_STEP = 1
    const val SECONDS_STEP = 5

    /**
     * `previousLogs` to serie z ostatniego ukończonego wykonania tego treningu, `difficulty` to obecny poziom,
     * `owned` posiadany sprzęt, `tuning` pamięć trenera (D-35).
     * Brak kompletu serii dla ćwiczenia (bo pominięte) blokuje propozycję — nie wiemy, czy było łatwo.
     *
     * Wszystko liczy się od celu pokazanego, czyli od tego samego, co wystawia player ([WorkoutPlanner.shownTarget]):
     * szablon po skalowaniu poziomem i po korekcie trenera. Dzięki temu karta mówi tę samą liczbę, którą widać
     * w wierszu ćwiczenia, a jeden tap „Zastosuj” przesuwa cel dokładnie o jeden krok (D-43).
     *
     * Efekt uboczny jest pożądany: sesja zrobiona na innym poziomie albo przy innej korekcie trenera nie daje
     * propozycji, bo nie ma sensu podbijać celu na podstawie wykonania, którego już nie da się porównać.
     *
     * `owned` pilnuje, żeby trudniejszy wariant nie był ćwiczeniem na sprzęt, którego użytkownik nie ma: to nie
     * byłaby propozycja, tylko wpisanie do szablonu ruchu, którego nie da się zrobić.
     */
    fun suggest(
        blocks: List<Block>,
        previousLogs: List<SetLog>,
        exercises: Map<String, Exercise>,
        difficulty: Difficulty = Difficulty.INTERMEDIATE,
        owned: Set<Equipment> = emptySet(),
        tuning: Map<String, ExerciseTuning> = emptyMap(),
        planner: WorkoutPlanner = WorkoutPlanner(),
    ): List<TargetSuggestion> {
        if (previousLogs.isEmpty()) return emptyList()
        val suggestions = ArrayList<TargetSuggestion>()
        blocks.forEachIndexed { blockIndex, block ->
            // Stały blok (D-37, D-42) ma cele wypalone przez autora: ani poziom, ani trener, ani progresja ich nie ruszają.
            // AMRAP liczy powtórzenia w czasie, więc nie ma celu do podbicia.
            if (block.fixed) return@forEachIndexed
            block.items.forEachIndexed { itemIndex, item ->
                if (item.targetKind == TargetKind.AMRAP) return@forEachIndexed
                val expected = if (block.kind == BlockKind.SETS) item.sets.coerceAtLeast(1) else block.rounds.coerceAtLeast(1)
                val itemLogs = previousLogs.filter { it.blockIndex == blockIndex && it.itemIndex == itemIndex }
                if (itemLogs.size < expected) return@forEachIndexed

                val exercise = planner.shownExercise(item, block, difficulty, exercises, owned)
                val shownId = exercise?.id ?: item.exerciseId
                val shown = planner.shownTarget(item, block, difficulty, tuning, exercises, owned)
                // Sesja musi opisywać to samo, co dziś wystawiłby player, inaczej podpowiadalibyśmy w kółko
                // to samo po zastosowaniu poprzedniej propozycji.
                if (itemLogs.any { it.exerciseId != shownId || it.targetValue != shown }) return@forEachIndexed
                if (itemLogs.any { !it.hitTarget }) return@forEachIndexed

                val onTime = item.targetKind != TargetKind.REPS
                val limit = if (onTime) SECONDS_LIMIT else REPS_LIMIT
                val name = exercise?.namePl ?: item.exerciseId

                if (shown < limit) {
                    val rawTo = nextRaw(item.targetValue, item.targetKind, difficulty)
                    // Cel po zastosowaniu propozycji liczony tak, jak policzy go player.
                    val to = planner.shownTarget(item.copy(targetValue = rawTo), block, difficulty, tuning, exercises, owned)
                    // Korekta trenera w dół potrafi trzymać cel na podłodze — wtedy nie ma czego proponować.
                    if (to <= shown) return@forEachIndexed
                    suggestions += TargetSuggestion(
                        blockIndex = blockIndex,
                        itemIndex = itemIndex,
                        exerciseId = shownId,
                        exerciseName = name,
                        targetKind = item.targetKind,
                        from = shown,
                        to = to,
                        rawTo = rawTo,
                    )
                    return@forEachIndexed
                }

                // Trudniejszy wariant tylko wtedy, gdy jest na czym go zrobić.
                val harder = exercise?.harderId?.let { exercises[it] }?.takeIf { it.canDoWith(owned) } ?: return@forEachIndexed
                val rawTo = if (onTime) harder.defaultSeconds else harder.defaultReps
                val swapped = item.copy(exerciseId = harder.id, targetValue = rawTo)
                // Podmiana musi zmienić ćwiczenie na ekranie. U początkującego trudniejsza wersja pompek na kolanach
                // to pompki z szablonu, więc player dalej pokazałby pompki na kolanach, tylko z niższym celem.
                val after = planner.shownExercise(swapped, block, difficulty, exercises, owned) ?: return@forEachIndexed
                if (after.id == shownId) return@forEachIndexed
                suggestions += TargetSuggestion(
                    blockIndex = blockIndex,
                    itemIndex = itemIndex,
                    exerciseId = shownId,
                    exerciseName = name,
                    targetKind = item.targetKind,
                    from = shown,
                    to = planner.shownTarget(swapped, block, difficulty, tuning, exercises, owned),
                    rawTo = rawTo,
                    swapToExerciseId = harder.id,
                    swapToName = after.namePl,
                )
            }
        }
        return suggestions
    }

    private fun scale(value: Int, kind: TargetKind, difficulty: Difficulty): Int =
        if (kind == TargetKind.REPS) {
            DifficultyProfile.scaleReps(value, difficulty)
        } else {
            DifficultyProfile.scaleSeconds(value, difficulty)
        }

    /**
     * Najmniejsza wartość szablonu, która na obecnym poziomie daje o krok wyższy cel na ekranie.
     * Bez tego „+5 s” u początkującego potrafi się zaokrąglić z powrotem do tej samej liczby (40 i 45 s → 30 s).
     */
    private fun nextRaw(rawFrom: Int, kind: TargetKind, difficulty: Difficulty): Int {
        val onTime = kind != TargetKind.REPS
        val step = if (onTime) SECONDS_STEP else REPS_STEP
        val wanted = scale(rawFrom, kind, difficulty) + step
        // Sekundy w szablonie trzymamy na wielokrotnościach piątki, powtórzenia idą co jeden.
        val candidates = if (onTime) {
            generateSequence(rawFrom / 5 * 5 + 5) { it + 5 }.take(5)
        } else {
            generateSequence(rawFrom + 1) { it + 1 }.take(4 * step + 1)
        }
        return candidates.firstOrNull { scale(it, kind, difficulty) >= wanted } ?: (rawFrom + step)
    }

    /** Nakłada propozycje na szablon; pozycje spoza zakresu są pomijane. */
    fun apply(blocks: List<Block>, suggestions: List<TargetSuggestion>): List<Block> {
        if (suggestions.isEmpty()) return blocks
        val byPosition = suggestions.associateBy { it.blockIndex to it.itemIndex }
        return blocks.mapIndexed { blockIndex, block ->
            block.copy(
                items = block.items.mapIndexed { itemIndex, item ->
                    val suggestion = byPosition[blockIndex to itemIndex] ?: return@mapIndexed item
                    item.copy(
                        exerciseId = suggestion.swapToExerciseId ?: item.exerciseId,
                        targetValue = suggestion.rawTo,
                    )
                },
            )
        }
    }

    /**
     * Nagłówek karty w UI: „3 ćwiczenia poszły na cel”. Liczymy ćwiczenia, nie pozycje: zduplikowany blok daje
     * te same ćwiczenia drugi raz, a to dalej dwa ćwiczenia.
     */
    fun headline(suggestions: List<TargetSuggestion>): String {
        val n = suggestions.distinctBy { it.exerciseId }.size
        val word = when {
            n == 1 -> "ćwiczenie poszło"
            n % 10 in 2..4 && n % 100 !in 12..14 -> "ćwiczenia poszły"
            else -> "ćwiczeń poszło"
        }
        return "$n $word na cel za każdym razem"
    }

    /**
     * Cały tekst karty „Podbij cele”: nagłówek, dwukropek i propozycje, każda raz (dwie pozycje z tym samym
     * ćwiczeniem i tym samym celem to jedna linijka), zakończone jedną kropką także po skrócie „powt.”.
     * `apply` dalej podbija każdą pozycję osobno.
     */
    fun cardText(suggestions: List<TargetSuggestion>): String {
        if (suggestions.isEmpty()) return ""
        val text = headline(suggestions) + ": " + suggestions.map { it.describe() }.distinct().joinToString(", ")
        return if (text.endsWith(".")) text else "$text."
    }
}
