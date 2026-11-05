package pl.mysttic.trainings.domain.plan.preset

import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.plan.Goal
import kotlin.math.roundToInt

/** Rodzaj cyklu: trening albo praktyka oddechowa (inny tytuł przypomnienia, inny wpis w Health Connect). */
enum class ProgramKind { TRAINING, BREATHING }

/**
 * Gotowe programy (D-37). Nazwa w enumie jest kluczem zapisanym w `TrainingProfile.presetId`,
 * więc zmiana nazwy stałej psuje zapisane cykle; etykieta może się zmieniać.
 */
enum class PresetId(val labelPl: String) {
    TABATA("Tabata"),
    IRON_MAN("Iron Man"),
    ABS("Wyzwanie brzucha"),
    PUSHUPS_100("100 pompek"),
    SEVEN_MINUTES("Siedem minut"),
    PLANK_AND_BAR("Deska i drążek"),
    MORNING("Poranny rozruch"),
    EVENING("Wieczorne rozciąganie"),
    SQUATS("Wyzwanie przysiadów"),
    BURPEE_EMOM("Burpee co minutę"),
    WIM_HOF("Oddech Wima Hofa"),
}

/** Rytm dni cyklu: który dzień jest treningowy. `dayIndex` liczony od 1. */
sealed interface DayPattern {
    val labelPl: String
    /** Ile dni treningowych wypada w tygodniu; do profilu i podglądu. */
    val daysPerWeek: Int
    fun isTrainingDay(dayIndex: Int): Boolean

    /** Dni tygodnia liczone od dnia startu (0 = dzień startu), jak w `PlanGenerator`. */
    data class Weekly(val days: Set<Int>) : DayPattern {
        override val labelPl: String get() = "${days.size} razy w tygodniu"
        override val daysPerWeek: Int get() = days.size
        override fun isTrainingDay(dayIndex: Int): Boolean = ((dayIndex - 1) % 7) in days
    }

    /** Co n-ty dzień wolny (n = 4: trzy dni pracy, jeden odpoczynku). */
    data class RestEvery(val n: Int) : DayPattern {
        override val labelPl: String get() = "co ${n}. dzień wolny"
        override val daysPerWeek: Int get() = 7 - 7 / n
        override fun isTrainingDay(dayIndex: Int): Boolean = dayIndex % n != 0
    }

    data object Daily : DayPattern {
        override val labelPl: String get() = "codziennie"
        override val daysPerWeek: Int get() = 7
        override fun isTrainingDay(dayIndex: Int): Boolean = true
    }
}

/** O co program pyta przy starcie, poza długością i poziomem. */
enum class PresetParam(val labelPl: String, val range: IntRange, val default: Int, val unitPl: String) {
    START_PUSHUPS("Ile pompek robisz z rzędu", 3..60, 15, "pompek"),
}

/**
 * Trudność programu na liście (D-46): wrodzona intensywność protokołu przy tym samym wybranym poziomie.
 * To nie jest `Difficulty`: poziom 1/2/3 wybiera się przy starcie i ustawia tabele etapów (D-37).
 * Kolejność stałych = kolejność na liście, od łagodnych do wymagających.
 */
enum class PresetIntensity(val labelPl: String) {
    LIGHT("łagodna"),
    MODERATE("średnia"),
    HARD("wysoka"),
}

/** Jeden etap progresji: szablon treningu i opis tego, co w nim rośnie („20 powt., deska 40 s”). */
data class PresetStage(val label: String, val blocks: List<Block>)

/**
 * Definicja gotowego programu. Etapy są funkcją poziomu i liczby sesji w cyklu, więc te same wybory
 * dają ten sam plan (determinizm jak w `PlanGenerator`). Bloki etapów są stałym protokołem
 * (`Block.fixed`), player nie skaluje ich poziomem — poziom działa tylko tutaj, przy układaniu tabel.
 */
interface PresetDefinition {
    val id: PresetId
    val name: String get() = id.labelPl
    /** Jedno zdanie pod nazwą na liście. */
    val tagline: String
    /** Dwa, trzy zdania: skąd protokół i jak wygląda sesja. */
    val description: String
    /** Ostrzeżenie pokazywane przy programie (null = brak). */
    val warning: String? get() = null
    /** Trudność na kafelku i miejsce na liście (D-46); bez wartości domyślnej, żeby nowy program musiał ją podać. */
    val intensity: PresetIntensity
    val kind: ProgramKind get() = ProgramKind.TRAINING
    /** Cel wpisywany do profilu cyklu; kreator go nie czyta, ale podgląd planu tak. */
    val goal: Goal
    val pattern: DayPattern
    /** Długości cyklu do wyboru (dni). */
    val lengthOptions: List<Int>
    val defaultLength: Int
    val asks: PresetParam? get() = null

    /**
     * Etapy od pierwszego do ostatniego dla wybranego poziomu; [sessions] to liczba dni treningowych w cyklu.
     * Rozmiar listy nie może przekroczyć [sessions]; etap na sesję = lista o rozmiarze [sessions].
     */
    fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage>

    /**
     * Zamienniki bez sprzętu (D-46): ćwiczenie ze sprzętem → ćwiczenie bez sprzętu tej samej partii i rodzaju celu.
     * `PresetCatalog.build` wstawia je, gdy użytkownik nie ma sprzętu; cel i przerwy pozycji zostają. Kuratorowane,
     * a nie z `EquipmentMatcher`, bo protokół ma swoją kolejność partii. Sprzęt bez zamiennika blokuje start programu.
     */
    fun substitutes(level: Difficulty): Map<String, String> = emptyMap()
}

/** Wspólna arytmetyka etapów: liniowe narastanie i przydział sesji do etapów. */
object Stages {

    /** Liniowo od [from] do [to] w [count] etapach, indeks od 0; jeden etap = [to]. */
    fun ramp(from: Int, to: Int, index: Int, count: Int): Int {
        if (count <= 1) return to
        val i = index.coerceIn(0, count - 1)
        return (from + (to - from) * i.toDouble() / (count - 1)).roundToInt()
    }

    /** Jak [ramp], ale do pełnych pięciu sekund. */
    fun ramp5(from: Int, to: Int, index: Int, count: Int): Int = roundTo5(ramp(from, to, index, count))

    fun roundTo5(value: Int): Int = ((value + 2) / 5) * 5

    /** Indeks etapu (od 0) dla sesji [session] (od 1) z [sessions]: równe odcinki, ostatni etap kończy cykl. */
    fun stageIndex(session: Int, sessions: Int, stageCount: Int): Int {
        if (sessions <= 0 || stageCount <= 0) return 0
        return (((session - 1).coerceIn(0, sessions - 1)) * stageCount / sessions).coerceIn(0, stageCount - 1)
    }

    /** Liczba tygodni, gdy w tygodniu wypada [perWeek] sesji; co najmniej jeden. */
    fun weeks(sessions: Int, perWeek: Int): Int = ((sessions + perWeek - 1) / perWeek).coerceAtLeast(1)

    fun <T> byLevel(level: Difficulty, beginner: T, intermediate: T, advanced: T): T = when (level) {
        Difficulty.BEGINNER -> beginner
        Difficulty.INTERMEDIATE -> intermediate
        Difficulty.ADVANCED -> advanced
    }

    // --- składanie bloków stałego protokołu -------------------------------------------------------

    fun reps(exerciseId: String, reps: Int, sets: Int = 1, restSet: Int = 0, restItem: Int = 0) = BlockItem(
        exerciseId = exerciseId, targetKind = TargetKind.REPS, targetValue = reps.coerceAtLeast(1),
        sets = sets.coerceAtLeast(1), restAfterSetSec = restSet, restAfterItemSec = restItem,
    )

    fun secs(
        exerciseId: String, seconds: Int, sets: Int = 1, restSet: Int = 0, restItem: Int = 0,
        openEnded: Boolean = false, cadenceSec: Int? = null, note: String = "",
    ) = BlockItem(
        exerciseId = exerciseId, targetKind = TargetKind.SECONDS, targetValue = seconds.coerceAtLeast(5),
        sets = sets.coerceAtLeast(1), restAfterSetSec = restSet, restAfterItemSec = restItem,
        openEnded = openEnded, cadenceSec = cadenceSec, note = note,
    )

    /**
     * EMOM: runda trwa dokładnie [windowSec], w niej seria i odpoczynek do końca okna.
     * Długość przerwy liczy silnik z faktycznego czasu pracy; `restItem` zostaje jako wartość zapasowa.
     */
    fun emom(exerciseId: String, reps: Int, windowSec: Int = 60) =
        reps(exerciseId, reps).copy(windowSec = windowSec)

    /** Ile dasz radę w oknie czasu (tabata z burpee, seria max pompek). */
    fun amrap(exerciseId: String, seconds: Int, restItem: Int = 0) = BlockItem(
        exerciseId = exerciseId, targetKind = TargetKind.AMRAP, targetValue = seconds, sets = 1,
        restAfterSetSec = 0, restAfterItemSec = restItem,
    )

    fun sets(items: List<BlockItem>, restAfter: Int? = null) =
        Block(kind = BlockKind.SETS, items = items, fixed = true, restAfterSec = restAfter)

    fun circuit(rounds: Int, restRounds: Int, items: List<BlockItem>, restAfter: Int? = null) = Block(
        kind = BlockKind.CIRCUIT, rounds = rounds.coerceAtLeast(1), restBetweenRoundsSec = restRounds,
        items = items, fixed = true, restAfterSec = restAfter,
    )
}
