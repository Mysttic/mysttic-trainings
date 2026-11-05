package pl.mysttic.trainings.domain.plan.preset

import pl.mysttic.trainings.domain.engine.Announcer.plural
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.plan.Goal
import pl.mysttic.trainings.domain.plan.preset.Stages.amrap
import pl.mysttic.trainings.domain.plan.preset.Stages.byLevel
import pl.mysttic.trainings.domain.plan.preset.Stages.circuit
import pl.mysttic.trainings.domain.plan.preset.Stages.emom
import pl.mysttic.trainings.domain.plan.preset.Stages.ramp
import pl.mysttic.trainings.domain.plan.preset.Stages.ramp5
import pl.mysttic.trainings.domain.plan.preset.Stages.reps
import pl.mysttic.trainings.domain.plan.preset.Stages.secs
import pl.mysttic.trainings.domain.plan.preset.Stages.sets
import pl.mysttic.trainings.domain.plan.preset.Stages.weeks
import kotlin.math.min

/*
 * Tabele gotowych programów (D-37). Każdy program to funkcja (poziom, liczba sesji, parametr) → etapy;
 * poziom wybiera się przy starcie i wchodzi wyłącznie tutaj, bo bloki są stałym protokołem (`Block.fixed`).
 * Identyfikatory ćwiczeń pochodzą z seedu; `PresetSeedConsistencyTest` pilnuje, że istnieją i mają zgodny rodzaj.
 */

/** Ćwiczenia na czas z seedu używane w oknie 20 s tabaty; reszta (na powtórzenia) idzie jako AMRAP. */
private val TIMED = setOf(
    "jumping-jacks", "high-knees", "mountain-climbers", "step-touch", "fast-feet", "skater-steps",
    "plank-jacks", "march-in-place", "standing-knee-to-elbow",
)

private const val PROTOCOL_MAX_HEART_RATE = "Interwały na maksimum tętna. Zawroty głowy, nudności albo ból w klatce znaczą stop."

object Tabata : PresetDefinition {
    override val id = PresetId.TABATA
    override val tagline = "8 razy 20 s pracy i 10 s przerwy. Z tygodnia na tydzień więcej bloków."
    override val description = "Protokół Izumiego Tabaty: cztery ćwiczenia po dwa przejścia, każde 20 s na maksimum i 10 s przerwy, " +
        "czyli cztery minuty na blok. Trzy minuty rozgrzewki i dwie schłodzenia w zestawie. Co tydzień dochodzi blok."
    override val warning = PROTOCOL_MAX_HEART_RATE
    override val intensity = PresetIntensity.HARD
    override val goal = Goal.CONDITION
    override val pattern = DayPattern.Weekly(setOf(0, 2, 4))
    override val lengthOptions = listOf(14, 21, 28, 42)
    override val defaultLength = 21

    private val POOLS_BEGINNER = listOf(
        listOf("jumping-jacks", "high-knees", "mountain-climbers", "step-touch"),
        listOf("fast-feet", "skater-steps", "plank-jacks", "march-in-place"),
    )
    private val POOLS_INTERMEDIATE = listOf(
        listOf("jump-squats", "mountain-climbers", "high-knees", "burpees"),
        listOf("split-jumps", "plank-jacks", "star-jumps", "pushups"),
        listOf("skater-steps", "fast-feet", "jump-squats", "mountain-climbers"),
    )
    private val POOLS_ADVANCED = POOLS_INTERMEDIATE + listOf(listOf("burpees", "plank-jacks", "split-jumps", "high-knees"))

    private val WARM_UP: Block = sets(
        listOf(secs("march-in-place", 60), secs("step-touch", 60), secs("standing-knee-to-elbow", 60)),
        restAfter = 15,
    )
    private val COOL_DOWN: Block = sets(
        listOf(
            secs("downward-dog", 30, restItem = 5),
            secs("kneeling-hip-flexor-stretch", 30, sets = 2, restSet = 5, restItem = 5),
            secs("childs-pose", 30),
        ),
    )

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val count = weeks(sessions, 3)
        val (from, to) = byLevel(level, 1 to 2, 1 to 3, 2 to 4)
        val pools = byLevel(level, POOLS_BEGINNER, POOLS_INTERMEDIATE, POOLS_ADVANCED)
        return List(count) { week ->
            val blocks = min(from + week, ramp(from, to, week, count))
            val tabata = List(blocks) { b -> tabataBlock(pools[b % pools.size], restAfter = if (b == blocks - 1) 30 else 60) }
            PresetStage(
                label = "$blocks ${plural(blocks, "blok", "bloki", "bloków")} po 8 rund",
                blocks = listOf(WARM_UP) + tabata + listOf(COOL_DOWN),
            )
        }
    }

    /** Cztery ćwiczenia po dwa przejścia = osiem okien 20/10; na powtórzenia liczy się „ile dasz radę”. */
    private fun tabataBlock(ids: List<String>, restAfter: Int): Block = circuit(
        rounds = 2,
        restRounds = 10,
        items = ids.map { if (it in TIMED) secs(it, 20, restItem = 10) else amrap(it, 20, restItem = 10) },
        restAfter = restAfter,
    )
}

object IronMan : PresetDefinition {
    override val id = PresetId.IRON_MAN
    override val tagline = "Rundy pompek, przysiadów, brzuszków i burpee. Co tydzień więcej rund."
    override val description = "Test siły i wytrzymałości bez sprzętu: runda to po dziesięć powtórzeń czterech ćwiczeń bez przerwy, " +
        "potem minuta oddechu. Rozgrzewka w zestawie; rośnie tylko liczba rund."
    override val warning = "Burpee na zimno obciążają nadgarstki i lędźwie: rozgrzewki nie pomijaj. Technika przed tempem."
    override val intensity = PresetIntensity.HARD
    override val goal = Goal.CONDITION
    override val pattern = DayPattern.Weekly(setOf(0, 2, 4))
    override val lengthOptions = listOf(14, 21, 28, 42)
    override val defaultLength = 28

    private val WARM_UP: Block = circuit(
        rounds = 1,
        restRounds = 0,
        items = listOf(secs("jumping-jacks", 45), reps("squats", 10), reps("knee-pushups", 8), reps("half-burpee", 5)),
        restAfter = 30,
    )

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val count = weeks(sessions, 3)
        val (from, to) = byLevel(level, 3 to 6, 5 to 10, 6 to 12)
        val items = byLevel(
            level,
            listOf(reps("knee-pushups", 8), reps("squats", 10), reps("crunches", 10), reps("half-burpee", 6)),
            listOf(reps("pushups", 10), reps("squats", 10), reps("sit-ups", 10), reps("burpees", 10)),
            listOf(reps("pushups", 12), reps("jump-squats", 12), reps("sit-ups", 12), reps("burpees", 12)),
        )
        return List(count) { week ->
            val rounds = ramp(from, to, week, count)
            PresetStage(
                label = "$rounds ${plural(rounds, "runda", "rundy", "rund")} po ${items.first().targetValue} powtórzeń",
                blocks = listOf(WARM_UP, circuit(rounds, restRounds = 60, items = items)),
            )
        }
    }
}

object Abs : PresetDefinition {
    override val id = PresetId.ABS
    override val tagline = "Te same pięć ćwiczeń, co etap więcej. Trzy dni pracy, czwarty wolny."
    override val description = "Klasyczne wyzwanie brzucha: spięcia, unoszenie nóg, rowerek, skręty i deska w jednym przebiegu. " +
        "Co etap dochodzi kilka powtórzeń i kilka sekund deski."
    override val warning = "Dolne plecy cały czas przy podłodze. Ból w lędźwiach znaczy skrócić ruch, nie forsować liczby."
    override val intensity = PresetIntensity.MODERATE
    override val goal = Goal.CONDITION
    override val pattern = DayPattern.RestEvery(4)
    override val lengthOptions = listOf(14, 21, 30, 42)
    override val defaultLength = 30

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val count = min(8, sessions)
        val (repsFrom, repsTo) = byLevel(level, 6 to 24, 10 to 45, 15 to 60)
        val (plankFrom, plankTo) = byLevel(level, 15 to 50, 20 to 90, 30 to 120)
        val rest = byLevel(level, 40, 30, 20)
        return List(count) { i ->
            val r = ramp(repsFrom, repsTo, i, count)
            val p = ramp5(plankFrom, plankTo, i, count)
            PresetStage(
                label = "$r powt., deska $p s",
                blocks = listOf(
                    circuit(
                        rounds = 1,
                        restRounds = 0,
                        items = listOf(
                            reps("crunches", r, restItem = rest),
                            reps("leg-raises", r, restItem = rest),
                            reps("bicycle-crunches", r, restItem = rest),
                            reps("russian-twist", r, restItem = rest),
                            secs("plank", p),
                        ),
                    ),
                ),
            )
        }
    }
}

object Pushups100 : PresetDefinition {
    override val id = PresetId.PUSHUPS_100
    override val tagline = "Serie pompek trzy razy w tygodniu, od tylu, ile robisz dziś, w stronę stu."
    override val description = "W duchu programu One Hundred Push-Ups: podajesz, ile pompek robisz z rzędu, a serie rosną " +
        "od 45 do 90 procent tej liczby, przerwy z minuty do dwóch. Na końcu sesji seria „ile dasz radę”."
    override val warning = "Przed każdą sesją rozgrzej barki i nadgarstki. Gdy pompki tracą pełen zakres, dokończ serię na kolanach."
    override val intensity = PresetIntensity.MODERATE
    override val goal = Goal.STRENGTH
    override val pattern = DayPattern.Weekly(setOf(0, 2, 4))
    override val lengthOptions = listOf(21, 28, 42)
    override val defaultLength = 42
    override val asks = PresetParam.START_PUSHUPS

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val max = (param ?: PresetParam.START_PUSHUPS.default).coerceIn(PresetParam.START_PUSHUPS.range)
        val exercise = byLevel(level, "knee-pushups", "pushups", "pushups")
        val setCount = byLevel(level, 4, 5, 6)
        val withMax = level != Difficulty.BEGINNER
        return List(sessions) { k ->
            val percent = ramp(45, 90, k, sessions)
            val perSet = maxOf(3, (max * percent + 50) / 100)
            val rest = 60 + 30 * (k * 3 / sessions).coerceIn(0, 2)
            val items = mutableListOf(reps(exercise, perSet, sets = setCount, restSet = rest, restItem = rest))
            if (withMax) items += amrap(exercise, 90)
            PresetStage(
                label = "$setCount × $perSet, przerwa $rest s" + if (withMax) " i seria max" else "",
                blocks = listOf(sets(items)),
            )
        }
    }
}

object SevenMinutes : PresetDefinition {
    override val id = PresetId.SEVEN_MINUTES
    override val tagline = "12 ćwiczeń po 30 s z 10 s przerwy, pięć dni w tygodniu. Co tydzień obwód więcej."
    override val description = "Protokół Kliki i Jordana (2013): pajacyki, ścianka, pompki, spięcia, wejścia, przysiady, dipy, deska, " +
        "bieg w miejscu, wykroki, pompka z obrotem i deska bokiem. Do wejść i dipów krzesło albo ławka; bez nich wchodzą " +
        "zamienniki bez sprzętu."
    override val warning = PROTOCOL_MAX_HEART_RATE
    override val intensity = PresetIntensity.HARD
    override val goal = Goal.CONDITION
    override val pattern = DayPattern.Weekly(setOf(0, 1, 2, 3, 4))
    override val lengthOptions = listOf(14, 21, 28)
    override val defaultLength = 21

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val count = weeks(sessions, 5)
        val (from, to) = byLevel(level, 1 to 2, 1 to 3, 2 to 4)
        val items = itemsFor(level)
        return List(count) { week ->
            val circuits = min(from + week, ramp(from, to, week, count))
            PresetStage(
                label = "$circuits ${plural(circuits, "obwód", "obwody", "obwodów")}",
                blocks = listOf(circuit(circuits, restRounds = 60, items = items)),
            )
        }
    }

    private fun itemsFor(level: Difficulty): List<BlockItem> {
        val r = byLevel(level, 8, 10, 14)
        val crunch = byLevel(level, 10, 15, 20)
        val rotation = byLevel(level, 6, 8, 10)
        val push = byLevel(level, "knee-pushups", "pushups", "pushups")
        val squat = byLevel(level, "squats", "squats", "jump-squats")
        val rotationId = byLevel(level, "pushups", "pushup-side-plank", "pushup-side-plank")
        return listOf(
            secs("jumping-jacks", 30, restItem = 10),
            secs("wall-sit", 30, restItem = 10),
            reps(push, r, restItem = 10),
            reps("crunches", crunch, restItem = 10),
            reps("step-ups", r, restItem = 10),
            reps(squat, r, restItem = 10),
            reps("bench-dips", r, restItem = 10),
            secs("plank", 30, restItem = 10),
            secs("high-knees", 30, restItem = 10),
            reps("walking-lunges", r, restItem = 10),
            reps(rotationId, rotation, restItem = 10),
            // Zmiana strony z krótką przerwą i podpisem: bez nich druga strona wyglądała jak przerwa (issue #22).
            secs("side-plank", 15, restItem = SIDE_SWITCH_SEC, note = "Pierwsza strona"),
            secs("side-plank", 15, note = "Druga strona"),
        )
    }

    /** Bez ławki (D-46): wejścia zastępuje wykrok skrzyżny (pośladki), dipy wyprost z klęku albo pompki wąskie (triceps). */
    override fun substitutes(level: Difficulty): Map<String, String> = mapOf(
        "step-ups" to "curtsy-lunge",
        "bench-dips" to byLevel(level, "kneeling-triceps-extension-floor", "close-pushups", "close-pushups"),
    )

    /** Tyle co przerwa na zmianę strony w Wieczornym rozciąganiu i schłodzeniu Tabaty. */
    const val SIDE_SWITCH_SEC = 5
}

object PlankAndBar : PresetDefinition {
    override val id = PresetId.PLANK_AND_BAR
    override val tagline = "Codziennie deska dłużej i kilka lekkich serii na drążku. Co siódmy dzień wolny."
    override val description = "Deska rośnie od kilkunastu sekund do kilku minut, drążek w stylu grease the groove: serie daleko " +
        "od upadku z długimi przerwami. Potrzebny drążek."
    override val warning = "Na drążku nigdy do upadku: każda seria ma zostawić dwa, trzy powtórzenia zapasu."
    override val intensity = PresetIntensity.MODERATE
    override val goal = Goal.STRENGTH
    override val pattern = DayPattern.RestEvery(7)
    override val lengthOptions = listOf(14, 21, 30)
    override val defaultLength = 30

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val (plankFrom, plankTo) = byLevel(level, 15 to 120, 20 to 180, 30 to 300)
        val bar = byLevel(level, "negative-pullups", "pullups", "pullups")
        val (setsFrom, setsTo) = byLevel(level, 3 to 4, 4 to 6, 5 to 6)
        val (repsFrom, repsTo) = byLevel(level, 3 to 5, 4 to 5, 5 to 8)
        val rest = byLevel(level, 120, 180, 150)
        return List(sessions) { k ->
            val p = ramp5(plankFrom, plankTo, k, sessions)
            val s = ramp(setsFrom, setsTo, k, sessions)
            val r = ramp(repsFrom, repsTo, k, sessions)
            PresetStage(
                label = "drążek $s × $r, deska $p s",
                blocks = listOf(
                    sets(listOf(reps(bar, r, sets = s, restSet = rest)), restAfter = 60),
                    sets(listOf(secs("plank", p))),
                ),
            )
        }
    }
}

object Morning : PresetDefinition {
    override val id = PresetId.MORNING
    override val tagline = "Dziesięć minut rozruchu codziennie, bez dni wolnych. Trzymania rosną co tydzień."
    override val description = "Powitanie słońca skrzyżowane z 5BX: leżenie, kobra, klęk podparty, pies z głową w dół, deska, " +
        "pompki, wykrok ze skrętem, zawias, przysiady, pajacyki i pozycja dziecka na koniec."
    override val intensity = PresetIntensity.LIGHT
    override val goal = Goal.MOBILITY
    override val pattern = DayPattern.Daily
    override val lengthOptions = listOf(14, 21, 28, 42)
    override val defaultLength = 28

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val count = weeks(sessions, 7)
        val base = byLevel(level, 20, 30, 40)
        val r = byLevel(level, 8, 12, 15)
        val (push, pushReps) = byLevel(level, "knee-pushups" to 6, "pushups" to 10, "pushups" to 15)
        return List(count) { week ->
            val hold = base + min(week, 4) * 5
            PresetStage(
                label = "trzymania $hold s",
                blocks = listOf(
                    sets(
                        listOf(
                            reps("dead-bug", r, restItem = 5),
                            reps("glute-bridge", r + 3, restItem = 5),
                            secs("cobra-stretch", hold - 10, restItem = 5),
                            reps("bird-dog", r, restItem = 5),
                            secs("cat-cow-flow", hold + 10, restItem = 5),
                            secs("downward-dog", hold, restItem = 5),
                            secs("plank", hold, restItem = 5),
                            reps(push, pushReps, restItem = 5),
                            reps("worlds-greatest-stretch", 6, restItem = 5),
                            reps("hip-hinge-wall-tap", r, restItem = 5),
                            reps("squats", r, restItem = 5),
                            secs("jumping-jacks", hold + 10, restItem = 5),
                            secs("childs-pose", hold),
                        ),
                    ),
                ),
            )
        }
    }
}

object Evening : PresetDefinition {
    override val id = PresetId.EVENING
    override val tagline = "Osiem minut rozciągania na wieczór, codziennie to samo."
    override val description = "Od ściany przez klęk do leżenia: łydki, zginacze bioder, koci grzbiet, pozycja dziecka, tył uda, " +
        "szczęśliwe dziecko i skręt w leżeniu. Kończysz na podłodze."
    override val intensity = PresetIntensity.LIGHT
    override val goal = Goal.MOBILITY
    override val pattern = DayPattern.Daily
    override val lengthOptions = listOf(14, 21, 28, 42)
    override val defaultLength = 28

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val count = weeks(sessions, 7)
        val base = byLevel(level, 30, 40, 50)
        return List(count) { week ->
            val hold = base + min(week, 3) * 5
            PresetStage(
                label = "trzymania $hold s",
                blocks = listOf(
                    sets(
                        listOf(
                            secs("calf-stretch-wall", hold, sets = 2, restSet = 5, restItem = 5),
                            secs("kneeling-hip-flexor-stretch", hold, sets = 2, restSet = 5, restItem = 5),
                            secs("cat-cow-flow", hold, restItem = 5),
                            secs("childs-pose", hold, restItem = 5),
                            secs("hamstring-stretch", hold, sets = 2, restSet = 5, restItem = 5),
                            secs("happy-baby", hold, restItem = 5),
                            secs("supine-twist", hold, sets = 2, restSet = 5),
                        ),
                    ),
                ),
            )
        }
    }
}

object Squats : PresetDefinition {
    override val id = PresetId.SQUATS
    override val tagline = "Przysiady w seriach, suma rośnie z dnia na dzień. Trzy dni pracy, czwarty wolny."
    override val description = "Wyzwanie przysiadów: od kilkudziesięciu do kilkuset dziennie, w seriach z krótką przerwą. " +
        "Pięty na podłodze, kolana w linii stóp, uda do równoległości."
    override val warning = "Ból w kolanie albo plecach znaczy zostać na poprzedniej sesji, nie dokładać."
    override val intensity = PresetIntensity.MODERATE
    override val goal = Goal.CONDITION
    override val pattern = DayPattern.RestEvery(4)
    override val lengthOptions = listOf(14, 21, 30, 42)
    override val defaultLength = 30

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val (from, to) = byLevel(level, 30 to 150, 50 to 250, 60 to 350)
        val maxPerSet = byLevel(level, 30, 50, 50)
        val rest = byLevel(level, 45, 30, 30)
        return List(sessions) { k ->
            val total = ramp(from, to, k, sessions)
            val n = ((total + maxPerSet - 1) / maxPerSet).coerceAtLeast(1)
            val r = (total + n - 1) / n
            PresetStage(
                label = "$n × $r ($total ${plural(total, "przysiad", "przysiady", "przysiadów")})",
                blocks = listOf(sets(listOf(reps("squats", r, sets = n, restSet = rest)))),
            )
        }
    }
}

object BurpeeEmom : PresetDefinition {
    /** Okno EMOM: runda trwa równo minutę, niezależnie od tempa serii. */
    const val EMOM_WINDOW_SEC = 60

    override val id = PresetId.BURPEE_EMOM
    override val tagline = "Co minutę kilka burpee, reszta minuty to przerwa. Z tygodnia na tydzień dłużej."
    override val description = "EMOM (every minute on the minute): runda to minuta, w niej seria burpee i odpoczynek do końca minuty. " +
        "Przerwa trwa tyle, ile zostało do końca minuty, więc szybsza seria znaczy dłuższy odpoczynek."
    override val warning = PROTOCOL_MAX_HEART_RATE
    override val intensity = PresetIntensity.HARD
    override val goal = Goal.FAT_LOSS
    override val pattern = DayPattern.Weekly(setOf(0, 2, 4))
    override val lengthOptions = listOf(14, 21, 28, 42)
    override val defaultLength = 28

    private val WARM_UP: Block = sets(
        listOf(secs("jumping-jacks", 40, restItem = 15), reps("squats", 10, restItem = 15), reps("half-burpee", 5)),
        restAfter = 30,
    )

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val count = weeks(sessions, 3)
        val (minutesFrom, minutesTo) = byLevel(level, 6 to 12, 10 to 20, 12 to 24)
        val (perMinuteFrom, perMinuteTo) = byLevel(level, 4 to 6, 5 to 7, 6 to 8)
        val exercise = byLevel(level, "half-burpee", "burpees", "burpees")
        return List(count) { week ->
            val minutes = ramp(minutesFrom, minutesTo, week, count)
            val n = ramp(perMinuteFrom, perMinuteTo, week, count)
            // Okno minuty niesie pozycja; `restRounds` zostaje jako wartość zapasowa dla kodu, który okna nie zna.
            val restRound = Stages.roundTo5(EMOM_WINDOW_SEC - 3 * n)
            PresetStage(
                label = "$minutes min × $n",
                blocks = listOf(
                    WARM_UP,
                    circuit(minutes, restRounds = restRound, items = listOf(emom(exercise, n, EMOM_WINDOW_SEC))),
                ),
            )
        }
    }
}

object WimHof : PresetDefinition {
    override val id = PresetId.WIM_HOF
    override val tagline = "Rundy: 30 oddechów w rytmie, zatrzymanie jak najdłużej, 15 s po wdechu."
    override val description = "Podstawowa runda metody Wima Hofa na siedząco lub leżąco: głębokie oddechy w tempie sygnału, " +
        "wydech i zatrzymanie tak długo, jak jest wygodnie (kończysz sam), głęboki wdech i 15 s zatrzymania. " +
        "Na koniec minuta spokojnego oddechu."
    override val warning = "Tylko na siedząco lub leżąco. Nigdy w wodzie, na stojąco, za kierownicą ani po alkoholu. " +
        "Nie w ciąży, przy padaczce i chorobach serca. Mrowienie i zawroty głowy są normalne, omdlenie znaczy zwolnij."
    override val kind = ProgramKind.BREATHING
    override val intensity = PresetIntensity.LIGHT
    override val goal = Goal.MOBILITY
    override val pattern = DayPattern.RestEvery(7)
    override val lengthOptions = listOf(14, 21, 28)
    override val defaultLength = 21

    override fun stages(level: Difficulty, sessions: Int, param: Int?): List<PresetStage> {
        val count = weeks(sessions, 6)
        val (from, to) = byLevel(level, 2 to 3, 3 to 4, 3 to 4)
        val breathing = byLevel(level, 90, 90, 105)
        val hold = byLevel(level, 60, 90, 120)
        return List(count) { week ->
            val rounds = min(from + week, ramp(from, to, week, count))
            PresetStage(
                label = "$rounds ${plural(rounds, "runda", "rundy", "rund")} po ${breathing / BREATH_CADENCE_SEC} oddechów",
                blocks = listOf(
                    circuit(
                        rounds = rounds,
                        restRounds = 0,
                        items = listOf(
                            secs("power-breathing", breathing, cadenceSec = BREATH_CADENCE_SEC),
                            secs("breath-hold", hold, openEnded = true),
                            secs("recovery-breath", 15),
                        ),
                        restAfter = 0,
                    ),
                    sets(listOf(secs("calm-breathing", 60))),
                ),
            )
        }
    }

    /** Jeden pełny oddech (wdech i wydech) na trzy sekundy: 30 oddechów w półtorej minuty. */
    const val BREATH_CADENCE_SEC = 3
}
