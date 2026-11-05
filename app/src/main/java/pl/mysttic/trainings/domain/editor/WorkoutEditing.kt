package pl.mysttic.trainings.domain.editor

import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout

/**
 * Operacje edytora treningu (M4). Czysty Kotlin na `List<Block>`: każda funkcja zwraca nową listę,
 * indeks spoza zakresu zostawia listę bez zmian. Walidacja zwraca komunikaty po polsku,
 * pusta lista = trening da się zapisać.
 */
object WorkoutEditing {

    const val DEFAULT_SETS = 3
    const val DEFAULT_REST_AFTER_SET_SEC = 45
    const val DEFAULT_REST_AFTER_ITEM_SEC = 60
    const val DEFAULT_ROUNDS = 4
    const val DEFAULT_REST_BETWEEN_ROUNDS_SEC = 60
    const val MAX_TARGET_VALUE = 999
    /** Cel na czas do godziny: trening na ergometrze (D-44) trwa 20 do 60 minut. */
    const val MAX_TARGET_SECONDS = 3600
    const val MAX_SETS = 20
    const val MAX_ROUNDS = 30
    const val MAX_REST_SEC = 600
    const val MAX_WEIGHT_KG = 500.0
    const val MAX_NOTE = 280
    const val MAX_LABEL = 40
    const val MAX_CADENCE_SEC = 60
    /** Okno EMOM (D-43): dłuższa „minuta” niż 10 minut to już nie EMOM, tylko błąd albo wrogi plik. */
    const val MAX_WINDOW_SEC = 600

    fun emptyBlock(kind: BlockKind): Block = when (kind) {
        BlockKind.SETS -> Block(kind = kind, items = emptyList())
        BlockKind.CIRCUIT -> Block(
            kind = kind,
            rounds = DEFAULT_ROUNDS,
            restBetweenRoundsSec = DEFAULT_REST_BETWEEN_ROUNDS_SEC,
            items = emptyList(),
        )
    }

    /** Nowy element z domyślnych wartości ćwiczenia: powtórzenia albo czas, zależnie od jego rodzaju. */
    fun newItem(exercise: Exercise, blockKind: BlockKind): BlockItem = BlockItem(
        exerciseId = exercise.id,
        targetKind = exercise.defaultTargetKind,
        targetValue = exercise.defaultTargetValue,
        sets = if (blockKind == BlockKind.SETS) DEFAULT_SETS else 1,
        restAfterSetSec = DEFAULT_REST_AFTER_SET_SEC,
        restAfterItemSec = if (blockKind == BlockKind.SETS) DEFAULT_REST_AFTER_ITEM_SEC else 15,
    )

    fun addBlock(blocks: List<Block>, kind: BlockKind): List<Block> = blocks + emptyBlock(kind)

    fun removeBlock(blocks: List<Block>, index: Int): List<Block> =
        if (index !in blocks.indices) blocks else blocks.filterIndexed { i, _ -> i != index }

    fun moveBlock(blocks: List<Block>, from: Int, to: Int): List<Block> = move(blocks, from, to)

    /** Zmiana rodzaju bloku zachowuje elementy; serie mają sens tylko w SETS, więc w obwodzie schodzą do 1. */
    fun changeBlockKind(blocks: List<Block>, index: Int, kind: BlockKind): List<Block> =
        mapBlock(blocks, index) { block ->
            if (block.kind == kind) {
                block
            } else {
                val template = emptyBlock(kind)
                block.copy(
                    kind = kind,
                    rounds = template.rounds,
                    restBetweenRoundsSec = template.restBetweenRoundsSec,
                    items = block.items.map { it.copy(sets = if (kind == BlockKind.SETS) it.sets.coerceAtLeast(1) else 1) },
                )
            }
        }

    fun setRounds(blocks: List<Block>, index: Int, rounds: Int): List<Block> =
        mapBlock(blocks, index) { it.copy(rounds = rounds.coerceIn(1, MAX_ROUNDS)) }

    fun setRestBetweenRounds(blocks: List<Block>, index: Int, seconds: Int): List<Block> =
        mapBlock(blocks, index) { it.copy(restBetweenRoundsSec = seconds.coerceIn(0, MAX_REST_SEC)) }

    fun addItem(blocks: List<Block>, blockIndex: Int, item: BlockItem): List<Block> =
        mapBlock(blocks, blockIndex) { it.copy(items = it.items + item) }

    fun removeItem(blocks: List<Block>, blockIndex: Int, itemIndex: Int): List<Block> =
        mapBlock(blocks, blockIndex) { block ->
            if (itemIndex !in block.items.indices) block
            else block.copy(items = block.items.filterIndexed { i, _ -> i != itemIndex })
        }

    fun moveItem(blocks: List<Block>, blockIndex: Int, from: Int, to: Int): List<Block> =
        mapBlock(blocks, blockIndex) { it.copy(items = move(it.items, from, to)) }

    /** Podmiana elementu z przycięciem wartości do sensownych zakresów. */
    fun updateItem(blocks: List<Block>, blockIndex: Int, itemIndex: Int, item: BlockItem): List<Block> =
        mapBlock(blocks, blockIndex) { block ->
            if (itemIndex !in block.items.indices) {
                block
            } else {
                block.copy(items = block.items.mapIndexed { i, old -> if (i == itemIndex) sanitize(item) else old })
            }
        }

    /** Górna granica celu: powtórzenia do 999, czas (także okno AMRAP) do godziny. */
    fun maxTarget(kind: TargetKind): Int = if (kind == TargetKind.REPS) MAX_TARGET_VALUE else MAX_TARGET_SECONDS

    /**
     * Następna wartość celu w edytorze. Czas idzie po siatce, która gęstnieje przy krótkich celach: co 5 s do 2 minut,
     * co 15 s do 10 minut, dalej co minutę, żeby 40 minut na ergometrze nie wymagało 480 dotknięć.
     */
    fun nextTarget(kind: TargetKind, value: Int): Int {
        if (kind == TargetKind.REPS) return (value + 1).coerceIn(1, MAX_TARGET_VALUE)
        val step = when {
            value < 120 -> 5
            value < 600 -> 15
            else -> 60
        }
        return ((value / step + 1) * step).coerceIn(1, MAX_TARGET_SECONDS)
    }

    /** Poprzednia wartość celu po tej samej siatce co [nextTarget]. */
    fun previousTarget(kind: TargetKind, value: Int): Int {
        if (kind == TargetKind.REPS) return (value - 1).coerceIn(1, MAX_TARGET_VALUE)
        val step = when {
            value <= 120 -> 5
            value <= 600 -> 15
            else -> 60
        }
        return ((value - 1) / step * step).coerceIn(1, MAX_TARGET_SECONDS)
    }

    /**
     * Następny ciężar w edytorze. Siatka gęstnieje przy lekkich ciężarach: co 0,5 kg do 10 kg (domowe hantle 1, 3, 4, 6,
     * 8 kg), co 1 kg do 40 kg, dalej co 2,5 kg (najmniejsza para talerzy), żeby sztanga nie wymagała setek dotknięć.
     * Wartość spoza siatki (z pliku planu) idzie do najbliższego wyższego punktu.
     */
    fun nextWeight(kg: Double?): Double {
        val halves = toHalves(kg)
        val step = when {
            halves < 20 -> 1
            halves < 80 -> 2
            else -> 5
        }
        return (((halves / step + 1) * step) / 2.0).coerceAtMost(MAX_WEIGHT_KG)
    }

    /** Poprzedni ciężar po tej samej siatce co [nextWeight]; zejście do zera daje null, czyli „bez ciężaru”. */
    fun previousWeight(kg: Double?): Double? {
        val halves = toHalves(kg)
        if (halves <= 0) return null
        val step = when {
            halves <= 20 -> 1
            halves <= 80 -> 2
            else -> 5
        }
        return (((halves - 1) / step * step) / 2.0).takeIf { it > 0.0 }
    }

    /** Ciężar w połówkach kilograma, żeby siatka nie zależała od błędów zaokrągleń Double. */
    private fun toHalves(kg: Double?): Int =
        kg?.takeIf { it.isFinite() && it > 0.0 }?.let { Math.round(it.coerceAtMost(MAX_WEIGHT_KG) * 2).toInt() } ?: 0

    fun sanitize(item: BlockItem): BlockItem = item.copy(
        targetValue = item.targetValue.coerceIn(1, maxTarget(item.targetKind)),
        sets = item.sets.coerceIn(1, MAX_SETS),
        restAfterSetSec = item.restAfterSetSec.coerceIn(0, MAX_REST_SEC),
        restAfterItemSec = item.restAfterItemSec.coerceIn(0, MAX_REST_SEC),
        weightKg = item.weightKg?.takeIf { it.isFinite() && it > 0.0 }?.coerceAtMost(MAX_WEIGHT_KG),
        cadenceSec = item.cadenceSec?.takeIf { it > 0 }?.coerceAtMost(MAX_CADENCE_SEC),
        note = cleanText(item.note, MAX_NOTE),
        windowSec = item.windowSec?.takeIf { it > 0 }?.coerceAtMost(MAX_WINDOW_SEC),
    )

    /** Blok z przyciętymi wartościami: to samo sito dla edytora i dla planu z pliku (D-42). */
    fun sanitizeBlock(block: Block): Block = block.copy(
        rounds = block.rounds.coerceIn(1, MAX_ROUNDS),
        restBetweenRoundsSec = block.restBetweenRoundsSec.coerceIn(0, MAX_REST_SEC),
        restAfterSec = block.restAfterSec?.coerceIn(0, MAX_REST_SEC),
        label = cleanText(block.label, MAX_LABEL),
        items = block.items.map { sanitize(it) },
    )

    /**
     * Tekst od człowieka, który trafi na ekran: bez znaków sterujących i znaków zmiany kierunku pisma
     * (tekst z pliku od kogoś mógłby nimi udawać inną treść), przycięty do limitu.
     */
    fun cleanText(text: String, max: Int): String = text
        .filter { c -> (c == '\n' || !c.isISOControl()) && c !in BIDI_CONTROLS }
        .trim()
        .take(max)

    private val BIDI_CONTROLS = setOf(
        '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
        '\u2066', '\u2067', '\u2068', '\u2069',
    )

    fun setBlockLabel(blocks: List<Block>, index: Int, label: String): List<Block> =
        mapBlock(blocks, index) { it.copy(label = label.take(MAX_LABEL)) }

    /** Przerwa po bloku; null = domyślna minuta plannera. */
    fun setBlockRestAfter(blocks: List<Block>, index: Int, seconds: Int?): List<Block> =
        mapBlock(blocks, index) { it.copy(restAfterSec = seconds?.coerceIn(0, MAX_REST_SEC)) }

    fun duplicateBlock(blocks: List<Block>, index: Int): List<Block> {
        if (index !in blocks.indices) return blocks
        return blocks.toMutableList().apply { add(index + 1, blocks[index]) }
    }

    fun duplicateItem(blocks: List<Block>, blockIndex: Int, itemIndex: Int): List<Block> =
        mapBlock(blocks, blockIndex) { block ->
            if (itemIndex !in block.items.indices) block
            else block.copy(items = block.items.toMutableList().apply { add(itemIndex + 1, block.items[itemIndex]) })
        }

    /**
     * Podmiana ćwiczenia w pozycji: serie, przerwy, okno EMOM, ciężar i wskazówka zostają, cel dostaje rodzaj nowego
     * ćwiczenia (D-27), a gdy rodzaj się nie zmienia — zostaje wartość autora.
     */
    fun replaceExercise(blocks: List<Block>, blockIndex: Int, itemIndex: Int, exercise: Exercise): List<Block> =
        mapBlock(blocks, blockIndex) { block ->
            val old = block.items.getOrNull(itemIndex) ?: return@mapBlock block
            val kind = exercise.defaultTargetKind
            val replaced = old.copy(
                exerciseId = exercise.id,
                targetKind = if (old.targetKind == TargetKind.AMRAP) old.targetKind else kind,
                targetValue = if (old.targetKind == kind || old.targetKind == TargetKind.AMRAP) old.targetValue else exercise.defaultTargetValue,
            )
            block.copy(items = block.items.mapIndexed { i, item -> if (i == itemIndex) replaced else item })
        }

    /**
     * Plan dosłowny (D-42): `fixed = true` wyłącza poziom, warianty, trenera i „Podbij cele” — wartości zostają
     * takie, jak je wpisał autor. `fixed = false` oddaje trening pod poziom i trenera, jak plan z kreatora.
     */
    fun setFixed(blocks: List<Block>, fixed: Boolean): List<Block> = blocks.map { it.copy(fixed = fixed) }

    /**
     * Czy trening wolno edytować w miejscu. Wbudowany nigdy (D-19): zmienia się go przez kopię do planu; trening planu tylko wtedy,
     * gdy plan zmienia się w miejscu (własny, z kreatora) i nic z niego właśnie nie trwa.
     */
    fun canEdit(workout: Workout, ownerEditable: Boolean?, inProgress: Boolean): Boolean =
        !workout.isBuiltIn && ownerEditable != false && !inProgress

    /** Komunikaty blokujące zapis; pusta lista = można zapisać. */
    fun validate(name: String, blocks: List<Block>): List<String> {
        val errors = ArrayList<String>()
        if (name.isBlank()) errors += "Nazwa treningu nie może być pusta."
        if (blocks.isEmpty()) errors += "Dodaj przynajmniej jeden blok."
        blocks.forEachIndexed { index, block ->
            if (block.items.isEmpty()) errors += "Blok ${index + 1} jest pusty — dodaj ćwiczenie albo usuń blok."
        }
        return errors
    }

    /** Szacowana liczba serii do wykonania: SETS mnoży serie, CIRCUIT mnoży rundy. */
    fun workCount(blocks: List<Block>): Int = blocks.sumOf { block ->
        when (block.kind) {
            BlockKind.SETS -> block.items.sumOf { it.sets.coerceAtLeast(1) }
            BlockKind.CIRCUIT -> block.items.size * block.rounds.coerceAtLeast(1)
        }
    }

    /** Opis celu do listy w edytorze: „3 × 12 powt.”, „40 s”, „AMRAP 60 s”. */
    fun targetLabel(item: BlockItem, blockKind: BlockKind): String {
        val target = when (item.targetKind) {
            TargetKind.REPS -> "${item.targetValue} powt."
            TargetKind.SECONDS -> "${item.targetValue} s"
            TargetKind.AMRAP -> "AMRAP ${item.targetValue} s"
        }
        return if (blockKind == BlockKind.SETS && item.sets > 1) "${item.sets} × $target" else target
    }

    /** Zapisywany trening: nowy dostaje `id` z zewnątrz (UUID), edytowany zachowuje swoje. */
    fun toWorkout(existing: Workout?, id: String, name: String, note: String, blocks: List<Block>, now: Long): Workout =
        Workout(
            id = existing?.id ?: id,
            name = name.trim(),
            note = note.trim(),
            blocks = blocks.map { sanitizeBlock(it) },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isBuiltIn = false,
        )

    private fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val mutable = list.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        return mutable
    }

    private fun mapBlock(blocks: List<Block>, index: Int, transform: (Block) -> Block): List<Block> =
        if (index !in blocks.indices) blocks else blocks.mapIndexed { i, block -> if (i == index) transform(block) else block }
}
