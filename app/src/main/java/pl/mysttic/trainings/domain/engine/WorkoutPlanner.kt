package pl.mysttic.trainings.domain.engine

import pl.mysttic.trainings.domain.coach.ExerciseTuning
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.RestKind
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout

/**
 * Rozwija szablon treningu w listę kroków dla danego poziomu trudności.
 * Czysty Kotlin — bez zależności od Androida, w całości testowalny.
 *
 * Blok ze `fixed = true` (gotowy program, D-37) idzie do playera dokładnie tak, jak zapisano: bez skalowania
 * poziomem, bez podmiany wariantu i bez korekty trenera. Funkcje [shownExercise], [shownTarget] i [restSec]
 * są jedynym źródłem tej reguły — szczegóły treningu liczą z nich to samo, co player. Wyjątkiem jest przerwa
 * dopełniająca okno EMOM ([BlockItem.windowSec]): planner wystawia estymatę, a ostateczną długość liczy silnik
 * z faktycznego czasu pracy.
 */
class WorkoutPlanner(
    private val restBetweenBlocksSec: Int = DEFAULT_BLOCK_REST_SEC,
) {

    /**
     * [owned] to sprzęt, który użytkownik ma — wariant poziomu wolno pokazać tylko wtedy, gdy ma się jego
     * komplet, inaczej zostaje ćwiczenie z szablonu.
     * [tuning] to pamięć trenera (D-35): korekta celu nakładana po skalowaniu poziomem, kluczem ćwiczenie pokazane na ekranie.
     */
    fun expand(
        workout: Workout,
        exercises: Map<String, Exercise>,
        difficulty: Difficulty,
        owned: Set<Equipment>,
        countdownSec: Int = DEFAULT_COUNTDOWN_SEC,
        tuning: Map<String, ExerciseTuning> = emptyMap(),
    ): List<Step> {
        val steps = ArrayList<Step>()
        if (countdownSec > 0) steps += Step.Countdown(countdownSec)
        workout.blocks.forEachIndexed { blockIndex, block ->
            if (blockIndex > 0) {
                val previous = workout.blocks[blockIndex - 1]
                addRest(steps, previous.restAfterSec ?: restBetweenBlocksSec, RestKind.BETWEEN_BLOCKS, difficulty, previous)
            }
            when (block.kind) {
                BlockKind.SETS -> expandSets(steps, blockIndex, block, exercises, difficulty, owned, tuning)
                BlockKind.CIRCUIT -> expandCircuit(steps, blockIndex, block, exercises, difficulty, owned, tuning)
            }
        }
        return steps
    }

    private fun expandSets(
        steps: MutableList<Step>, blockIndex: Int, block: Block,
        exercises: Map<String, Exercise>, difficulty: Difficulty, owned: Set<Equipment>, tuning: Map<String, ExerciseTuning>,
    ) {
        block.items.forEachIndexed { itemIndex, item ->
            val sets = item.sets.coerceAtLeast(1)
            for (set in 1..sets) {
                steps += workStep(blockIndex, itemIndex, round = 1, setIndex = set, setsInItem = sets, block, item, exercises, difficulty, owned, tuning)
                if (set < sets) addRest(steps, item.restAfterSetSec, RestKind.BETWEEN_SETS, difficulty, block, item.windowSec)
            }
            if (itemIndex < block.items.lastIndex) addRest(steps, item.restAfterItemSec, RestKind.BETWEEN_ITEMS, difficulty, block, item.windowSec)
        }
    }

    private fun expandCircuit(
        steps: MutableList<Step>, blockIndex: Int, block: Block,
        exercises: Map<String, Exercise>, difficulty: Difficulty, owned: Set<Equipment>, tuning: Map<String, ExerciseTuning>,
    ) {
        val rounds = block.rounds.coerceAtLeast(1)
        for (round in 1..rounds) {
            block.items.forEachIndexed { itemIndex, item ->
                steps += workStep(blockIndex, itemIndex, round = round, setIndex = 1, setsInItem = 1, block, item, exercises, difficulty, owned, tuning)
                if (itemIndex < block.items.lastIndex) addRest(steps, item.restAfterItemSec, RestKind.BETWEEN_ITEMS, difficulty, block, item.windowSec)
            }
            // Przerwa po ostatniej pozycji rundy dopełnia okno tej właśnie pozycji.
            if (round < rounds) {
                addRest(steps, block.restBetweenRoundsSec, RestKind.BETWEEN_ROUNDS, difficulty, block, block.items.lastOrNull()?.windowSec)
            }
        }
    }

    private fun workStep(
        blockIndex: Int, itemIndex: Int, round: Int, setIndex: Int, setsInItem: Int,
        block: Block, item: BlockItem, exercises: Map<String, Exercise>, difficulty: Difficulty,
        owned: Set<Equipment>, tuning: Map<String, ExerciseTuning>,
    ): Step.Work {
        val shown = shownExercise(item, block, difficulty, exercises, owned)
        return Step.Work(
            blockIndex = blockIndex,
            itemIndex = itemIndex,
            round = round,
            setIndex = setIndex,
            setsInItem = setsInItem,
            exerciseId = shown?.id ?: item.exerciseId,
            exerciseName = shown?.namePl ?: item.exerciseId,
            targetKind = item.targetKind,
            targetValue = shownTarget(item, block, difficulty, tuning, exercises, owned),
            weightKg = item.weightKg,
            roundsInBlock = if (block.kind == BlockKind.CIRCUIT) block.rounds.coerceAtLeast(1) else 1,
            openEnded = item.openEnded && item.targetKind == TargetKind.SECONDS,
            cadenceSec = item.cadenceSec?.takeIf { it > 0 && item.targetKind == TargetKind.SECONDS && !item.openEnded },
            note = item.note,
            blockLabel = block.label,
            windowSec = item.windowSec?.takeIf { it > 0 },
        )
    }

    /** Ćwiczenie, które użytkownik zobaczy: wariant poziomu, chyba że blok jest stałym protokołem. */
    fun shownExercise(
        item: BlockItem,
        block: Block,
        difficulty: Difficulty,
        exercises: Map<String, Exercise>,
        owned: Set<Equipment>,
    ): Exercise? {
        val base = exercises[item.exerciseId] ?: return null
        return if (block.fixed) base else resolveVariant(base, difficulty, exercises, owned)
    }

    /**
     * Cel, który użytkownik zobaczy: po skalowaniu poziomem i korekcie trenera (D-35), nakładanej po skalowaniu
     * z kluczem po ćwiczeniu pokazanym na ekranie. Stały protokół dostaje wartość z szablonu bez zmian.
     */
    fun shownTarget(
        item: BlockItem,
        block: Block,
        difficulty: Difficulty,
        tuning: Map<String, ExerciseTuning>,
        exercises: Map<String, Exercise>,
        owned: Set<Equipment>,
    ): Int {
        if (block.fixed) return item.targetValue
        val scaled = when (item.targetKind) {
            TargetKind.REPS -> DifficultyProfile.scaleReps(item.targetValue, difficulty)
            TargetKind.SECONDS, TargetKind.AMRAP -> DifficultyProfile.scaleSeconds(item.targetValue, difficulty)
        }
        val shownId = shownExercise(item, block, difficulty, exercises, owned)?.id ?: item.exerciseId
        return tuning[shownId]?.adjust(item.targetKind, scaled) ?: scaled
    }

    /**
     * Bloki tak, jak widzi je użytkownik, ale bez korekty trenera: wariant poziomu, cele i przerwy po skalowaniu,
     * z tych samych [shownExercise], [shownTarget] i [restSec] co player. Stały blok zostaje bez zmian.
     * Tak jedzie plan w pliku (D-42): u odbiorcy wchodzi dosłownie, więc musi nieść to, co widział autor.
     */
    fun shownBlocks(
        blocks: List<Block>,
        difficulty: Difficulty,
        exercises: Map<String, Exercise>,
        owned: Set<Equipment>,
    ): List<Block> = blocks.mapIndexed { index, block ->
        if (block.fixed) return@mapIndexed block
        block.copy(
            restBetweenRoundsSec = restSec(block.restBetweenRoundsSec, block, difficulty),
            // Bez własnej przerwy po bloku planner bierze domyślną minutę i ją skaluje; stały blok by jej nie skalował,
            // więc zapisujemy ją wprost. Po ostatnim bloku przerwy nie ma, tam nic nie zmieniamy.
            restAfterSec = if (index < blocks.lastIndex) {
                restSec(block.restAfterSec ?: restBetweenBlocksSec, block, difficulty)
            } else {
                block.restAfterSec
            },
            items = block.items.map { item ->
                item.copy(
                    exerciseId = shownExercise(item, block, difficulty, exercises, owned)?.id ?: item.exerciseId,
                    targetValue = shownTarget(item, block, difficulty, emptyMap(), exercises, owned),
                    restAfterSetSec = restSec(item.restAfterSetSec, block, difficulty),
                    restAfterItemSec = restSec(item.restAfterItemSec, block, difficulty),
                )
            },
        )
    }

    /** Przerwa po skalowaniu poziomem; stały protokół trzyma zapisane sekundy. */
    fun restSec(baseSec: Int, block: Block, difficulty: Difficulty): Int =
        if (block.fixed) baseSec.coerceAtLeast(0) else DifficultyProfile.scaleRest(baseSec, difficulty)

    /**
     * Przerwa po kroku pracy. Pozycja z oknem (EMOM) dostaje przerwę zawsze, nawet gdy estymata wyjdzie zerowa:
     * ostateczną długość policzy silnik z faktycznego czasu pracy, a poziom trudności okna nie rusza — minuta
     * to kontrakt programu, nie parametr trudności.
     */
    private fun addRest(
        steps: MutableList<Step>,
        baseSec: Int,
        kind: RestKind,
        difficulty: Difficulty,
        block: Block,
        windowSec: Int? = null,
    ) {
        if (windowSec != null && windowSec > 0) {
            val estimate = (windowSec - estimateWorkSec(steps.lastOrNull() as? Step.Work)).coerceAtLeast(0)
            steps += Step.Rest(estimate, kind, windowSec)
            return
        }
        val sec = restSec(baseSec, block, difficulty)
        if (sec > 0) steps += Step.Rest(sec, kind)
    }

    private fun estimateWorkSec(work: Step.Work?): Int = when {
        work == null -> 0
        else -> work.durationSec ?: if (work.openEnded) work.targetValue else work.targetValue * SEC_PER_REP
    }

    /**
     * Zamiana wariantu zależnie od poziomu: BEGINNER bierze `easierId` (jeśli ćwiczenie nie jest już
     * dla początkujących), ADVANCED bierze `harderId` (jeśli ćwiczenie nie jest już zaawansowane).
     * Jeden skok w łańcuchu — bez rekurencji, żeby zmiana była przewidywalna.
     *
     * Wariant, na który nie starcza sprzętu, nie wchodzi: zostaje ćwiczenie z szablonu, bo kreator już raz
     * uznał je za wykonalne. Bez tego zmiana poziomu albo odznaczenie sprzętu po założeniu cyklu wystawiały
     * w playerze ruch, którego nie ma na czym zrobić.
     */
    fun resolveVariant(
        exercise: Exercise,
        difficulty: Difficulty,
        exercises: Map<String, Exercise>,
        owned: Set<Equipment>,
    ): Exercise {
        val variantId = when (difficulty) {
            Difficulty.BEGINNER -> exercise.easierId?.takeIf { exercise.level != Difficulty.BEGINNER }
            Difficulty.INTERMEDIATE -> null
            Difficulty.ADVANCED -> exercise.harderId?.takeIf { exercise.level != Difficulty.ADVANCED }
        }
        return variantId?.let { exercises[it] }?.takeIf { it.canDoWith(owned) } ?: exercise
    }

    /**
     * Łączy plan w trakcie treningu z planem przeliczonym dla nowego poziomu:
     * wykonane kroki zostają, reszta pochodzi z nowego planu od tej samej pozycji (blok/element/runda/seria).
     */
    fun merge(current: List<Step>, stepIndex: Int, replanned: List<Step>): List<Step> {
        if (current.isEmpty() || stepIndex >= current.size) return current
        val currentStep = current[stepIndex]
        return when (currentStep) {
            is Step.Countdown -> replanned
            is Step.Work -> {
                val idx = replanned.indexOfFirst { it is Step.Work && it.positionKey == currentStep.positionKey }
                if (idx < 0) current else current.take(stepIndex) + replanned.drop(idx)
            }
            is Step.Rest -> {
                val nextWork = current.drop(stepIndex + 1).firstOrNull { it is Step.Work } as? Step.Work
                    ?: return current
                val idx = replanned.indexOfFirst { it is Step.Work && it.positionKey == nextWork.positionKey }
                if (idx < 0) current else current.take(stepIndex + 1) + replanned.drop(idx)
            }
        }
    }

    /**
     * Szacowany czas trwania planu; serie na powtórzenia liczone jako `secPerRep` na powtórzenie,
     * krok bez limitu jako jego orientacyjny cel.
     */
    fun estimateSeconds(steps: List<Step>, secPerRep: Int = SEC_PER_REP): Int = steps.sumOf { step ->
        when (step) {
            is Step.Countdown -> step.seconds
            is Step.Rest -> step.seconds
            is Step.Work -> step.durationSec ?: if (step.openEnded) step.targetValue else step.targetValue * secPerRep
        }
    }

    companion object {
        const val DEFAULT_COUNTDOWN_SEC = 5
        const val DEFAULT_BLOCK_REST_SEC = 60

        /** Orientacyjny czas jednego powtórzenia — do estymat, nigdy do rozliczania treningu. */
        const val SEC_PER_REP = 3
    }
}
