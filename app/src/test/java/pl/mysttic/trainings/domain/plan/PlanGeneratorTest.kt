package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.engine.Step
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout
import kotlin.math.abs
import kotlin.math.min
import pl.mysttic.trainings.domain.engine.TestFixtures

class PlanGeneratorTest {

    private fun exercise(
        id: String,
        primary: List<MuscleGroup>,
        level: Difficulty = Difficulty.INTERMEDIATE,
        category: ExerciseCategory = ExerciseCategory.STRENGTH,
        equipment: Set<Equipment> = emptySet(),
        kind: ExerciseKind = ExerciseKind.REPS,
        easierId: String? = null,
        harderId: String? = null,
        defaultSeconds: Int = 30,
    ) = Exercise(
        id = id, nameEn = id, namePl = id, kind = kind, level = level, category = category,
        equipment = equipment, primaryMuscles = primary, secondaryMuscles = emptyList(),
        instructionsPl = listOf("krok"), images = emptyList(),
        easierId = easierId, harderId = harderId, defaultSeconds = defaultSeconds,
    )

    /** Mała biblioteka pokrywająca wszystkie role, z kilkoma wariantami poziomu i sprzętu. */
    private val library = listOf(
        exercise("squats", listOf(MuscleGroup.QUADS), Difficulty.BEGINNER, harderId = "jump-squats"),
        exercise("lunges", listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)),
        exercise("pistol", listOf(MuscleGroup.QUADS), Difficulty.ADVANCED),
        exercise("jump-squats", listOf(MuscleGroup.QUADS), category = ExerciseCategory.PLYOMETRICS, easierId = "squats"),
        exercise("bridge", listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS), Difficulty.BEGINNER, harderId = "single-bridge"),
        exercise("single-bridge", listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS)),
        exercise("goodmorning", listOf(MuscleGroup.HAMSTRINGS)),
        exercise("pushups", listOf(MuscleGroup.CHEST), Difficulty.BEGINNER, harderId = "decline-pushups"),
        exercise("decline-pushups", listOf(MuscleGroup.CHEST), equipment = setOf(Equipment.BENCH)),
        exercise("dips", listOf(MuscleGroup.TRICEPS, MuscleGroup.CHEST), Difficulty.ADVANCED),
        exercise("pike-pushups", listOf(MuscleGroup.SHOULDERS)),
        exercise("rows", listOf(MuscleGroup.UPPER_BACK), equipment = setOf(Equipment.DUMBBELLS)),
        exercise("pullups", listOf(MuscleGroup.LATS), Difficulty.ADVANCED, equipment = setOf(Equipment.PULL_UP_BAR)),
        exercise("band-rows", listOf(MuscleGroup.LATS), equipment = setOf(Equipment.RESISTANCE_BAND)),
        exercise("superman", listOf(MuscleGroup.LOWER_BACK), Difficulty.BEGINNER),
        exercise("plank", listOf(MuscleGroup.ABS), Difficulty.BEGINNER, kind = ExerciseKind.TIME, defaultSeconds = 40),
        exercise("hollow", listOf(MuscleGroup.ABS), kind = ExerciseKind.TIME, defaultSeconds = 20),
        exercise("deadbug", listOf(MuscleGroup.ABS), Difficulty.BEGINNER),
        exercise("climbers", listOf(MuscleGroup.FULL_BODY), category = ExerciseCategory.CARDIO, kind = ExerciseKind.TIME),
        exercise("high-knees", listOf(MuscleGroup.QUADS), Difficulty.BEGINNER, category = ExerciseCategory.CARDIO, kind = ExerciseKind.TIME),
        exercise("burpee", listOf(MuscleGroup.FULL_BODY), category = ExerciseCategory.PLYOMETRICS),
        exercise("stretch-hams", listOf(MuscleGroup.HAMSTRINGS), Difficulty.BEGINNER, category = ExerciseCategory.STRETCHING, kind = ExerciseKind.TIME),
    )

    private val byId = library.associateBy { it.id }

    private val profile = TrainingProfile(
        heightCm = 180, weightKg = 78.0, goal = Goal.CONDITION, level = Difficulty.INTERMEDIATE,
        daysPerWeek = 3, sessionMinutes = 30, cycleDays = 30, equipment = emptySet(),
    )

    private fun idsIn(plan: GeneratedPlan) = plan.workouts.flatMap { idsOf(it) }

    @Test
    fun `licznik kreatora bierze te same cwiczenia co generator`() {
        val ergometr = exercise(
            "rower", listOf(MuscleGroup.QUADS), category = ExerciseCategory.CARDIO, kind = ExerciseKind.TIME,
            equipment = setOf(Equipment.STATIONARY_BIKE),
        )
        val oddech = exercise("oddech", listOf(MuscleGroup.FULL_BODY), category = ExerciseCategory.BREATHING, kind = ExerciseKind.TIME)
        val wycofane = exercise("stare", listOf(MuscleGroup.CHEST)).copy(retiredAt = "2026-10-01")
        val wszystko = library + ergometr + oddech + wycofane

        val bezSprzetu = PlanGenerator.candidates(wszystko, emptySet()).map { it.id }
        assertTrue(bezSprzetu.none { id -> wszystko.first { it.id == id }.equipment.isNotEmpty() })
        assertTrue(listOf("rower", "oddech", "stare").none { it in bezSprzetu })

        val zHantlamiIRowerem = PlanGenerator.candidates(wszystko, setOf(Equipment.DUMBBELLS, Equipment.STATIONARY_BIKE)).map { it.id }
        assertTrue("rows" in zHantlamiIRowerem)
        assertTrue("ergometr nigdy nie wchodzi do kreatora (D-44)", "rower" !in zHantlamiIRowerem)

        // Wszystko, co generator wstawia do planu, jest wśród kandydatów tego sprzętu.
        val sprzet = setOf(Equipment.DUMBBELLS, Equipment.BENCH)
        val plan = PlanGenerator.generate(profile.copy(equipment = sprzet), wszystko, now = 0L)
        val kandydaci = PlanGenerator.candidates(wszystko, sprzet).map { it.id }.toSet()
        assertTrue(idsIn(plan).isNotEmpty())
        assertTrue(idsIn(plan).all { it in kandydaci })
    }
    private fun idsOf(workout: Workout) = workout.blocks.flatMap { b -> b.items.map { it.exerciseId } }
    private fun itemsOf(workout: Workout) = workout.blocks.flatMap { it.items }

    /** Kroki pracy tak, jak zobaczy je player na danym poziomie (po podmianie wariantów i skalowaniu). */
    private fun workSteps(workout: Workout, level: Difficulty, owned: Set<Equipment> = emptySet()): List<Step.Work> =
        WorkoutPlanner().expand(workout, byId, level, owned).filterIsInstance<Step.Work>()

    private fun estimatedSeconds(workout: Workout, level: Difficulty = Difficulty.INTERMEDIATE): Int {
        val planner = WorkoutPlanner()
        return planner.estimateSeconds(planner.expand(workout, byId, level, emptySet()))
    }

    private fun repeatsOf(workout: Workout): List<Int> = workout.blocks.flatMap { block ->
        if (block.kind == BlockKind.CIRCUIT) listOf(block.rounds) else block.items.map { it.sets }
    }

    /** Skrajne i typowe długości sesji dozwolone na danym poziomie. */
    private fun minutesFor(level: Difficulty): List<Int> {
        val range = TrainingProfile.minutesRange(level)
        return listOf(range.first, 30, range.last).distinct()
    }

    @Test
    fun `plan nie proponuje cwiczen na sprzet, ktorego nie ma`() {
        val plan = PlanGenerator.generate(profile, library, now = 0L)

        val used = idsIn(plan).toSet()
        assertTrue(used.isNotEmpty())
        assertFalse(used.contains("rows"))
        assertFalse(used.contains("pullups"))
        assertFalse(used.contains("band-rows"))
        assertFalse(used.contains("decline-pushups"))
    }

    @Test
    fun `dolozony sprzet wchodzi do planu`() {
        val withBand = PlanGenerator.generate(profile.copy(equipment = setOf(Equipment.RESISTANCE_BAND)), library, now = 0L)

        assertTrue(idsIn(withBand).contains("band-rows"))
    }

    @Test
    fun `wysokie BMI wyklucza plyometrie`() {
        val heavy = profile.copy(heightCm = 170, weightKg = 100.0, goal = Goal.FAT_LOSS)
        assertTrue(heavy.avoidJumping)

        val plan = PlanGenerator.generate(heavy, library, now = 0L)
        val used = idsIn(plan).toSet()

        assertFalse(used.contains("jump-squats"))
        assertFalse(used.contains("burpee"))
    }

    @Test
    fun `wysokie BMI wyklucza tez wariant plyometryczny, ktory wszedlby po podmianie poziomu`() {
        // squats → jump-squats u zaawansowanego; przy wysokim BMI ani jedno, ani drugie.
        val heavy = profile.copy(heightCm = 170, weightKg = 100.0, level = Difficulty.ADVANCED, sessionMinutes = 45)

        val plan = PlanGenerator.generate(heavy, library, now = 0L)
        val shown = plan.workouts.flatMap { workSteps(it, Difficulty.ADVANCED) }.map { it.exerciseId }.toSet()

        assertFalse(shown.contains("jump-squats"))
        assertFalse(shown.contains("burpee"))
    }

    @Test
    fun `cel sila daje bloki serii, kondycja obwod`() {
        val strength = PlanGenerator.generate(profile.copy(goal = Goal.STRENGTH), library, now = 0L)
        val condition = PlanGenerator.generate(profile.copy(goal = Goal.CONDITION), library, now = 0L)

        assertTrue(strength.workouts.all { w -> w.blocks.all { it.kind == BlockKind.SETS } })
        assertTrue(condition.workouts.all { w -> w.blocks.all { it.kind == BlockKind.CIRCUIT } })
    }

    @Test
    fun `rozklad dni zgadza sie z liczba treningow w tygodniu`() {
        val plan = PlanGenerator.generate(profile.copy(daysPerWeek = 3, cycleDays = 28), library, now = 0L)

        assertEquals(28, plan.days.size)
        assertEquals(12, plan.sessionCount)
        assertEquals(12, PlanGenerator.sessionsInCycle(profile.copy(daysPerWeek = 3, cycleDays = 28)))
        // pierwszy tydzień: trening, przerwa, trening, przerwa, trening, przerwa, przerwa
        assertEquals(listOf(false, true, false, true, false, true, true), plan.days.take(7).map { it.isRest })
    }

    @Test
    fun `treningi z rotacji ida po kolei i wracaja od poczatku`() {
        val plan = PlanGenerator.generate(profile.copy(daysPerWeek = 3, cycleDays = 30), library, now = 0L)

        val order = plan.days.filterNot { it.isRest }.map { it.workoutId }
        val ids = plan.workouts.map { it.id }
        assertEquals(3, ids.size)
        assertEquals(ids, order.take(3))
        assertEquals(ids.first(), order[3])
    }

    @Test
    fun `kazdy trening trafia w zadeklarowany czas z dokladnoscia do minuty`() {
        for (level in Difficulty.entries) {
            for (minutes in minutesFor(level)) {
                for (goal in Goal.entries) {
                    val p = profile.copy(level = level, sessionMinutes = minutes, goal = goal)
                    val plan = PlanGenerator.generate(p, library, now = 0L)
                    assertTrue("poziom=$level minuty=$minutes cel=$goal: brak treningów", plan.workouts.isNotEmpty())
                    plan.workouts.forEach { workout ->
                        val miss = estimatedSeconds(workout, p.level) - minutes * 60
                        assertTrue(
                            "poziom=$level cel=$goal minuty=$minutes trening=${workout.name} odchylenie=${miss}s",
                            abs(miss) <= PlanGenerator.TIME_TOLERANCE_SEC,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `rundy i serie trzymaja sie widelek poziomu`() {
        for (level in Difficulty.entries) {
            for (minutes in minutesFor(level)) {
                for (goal in Goal.entries) {
                    val range = PlanGenerator.repeatsRange(level, goal)
                    val plan = PlanGenerator.generate(profile.copy(level = level, sessionMinutes = minutes, goal = goal), library, now = 0L)
                    plan.workouts.forEach { workout ->
                        repeatsOf(workout).forEach { repeats ->
                            assertTrue(
                                "poziom=$level cel=$goal minuty=$minutes trening=${workout.name}: $repeats poza $range",
                                repeats in range,
                            )
                        }
                    }
                }
            }
        }
        assertEquals(2..3, PlanGenerator.repeatsRange(Difficulty.BEGINNER, Goal.CONDITION))
        assertEquals(3..5, PlanGenerator.repeatsRange(Difficulty.ADVANCED, Goal.STRENGTH))
        assertEquals(2..4, PlanGenerator.repeatsRange(Difficulty.ADVANCED, Goal.MOBILITY))
    }

    @Test
    fun `poczatkujacy bez sprzetu na kwadrans dostaje kilka cwiczen po kilka rund, nie dwa po siedem`() {
        val starter = profile.copy(level = Difficulty.BEGINNER, sessionMinutes = 15, goal = Goal.CONDITION)

        val plan = PlanGenerator.generate(starter, library, now = 0L)

        plan.workouts.forEach { workout ->
            // Rola „pull” nie ma kandydata bez sprzętu, ale trening nie ma przez to dziury.
            assertTrue("${workout.name}: za mało ćwiczeń: ${idsOf(workout)}", itemsOf(workout).size >= PlanGenerator.MIN_ITEMS)
            assertTrue(repeatsOf(workout).all { it <= 3 })
        }
        // Pompki (12 powtórzeń w szablonie) u początkującego to 8 na serię, najwyżej trzy serie.
        val pushups = workSteps(plan.workouts.first(), Difficulty.BEGINNER).filter { it.exerciseId == "pushups" }
        assertTrue("brak pompek w pierwszym treningu", pushups.isNotEmpty())
        assertTrue("za dużo pompek: ${pushups.sumOf { it.targetValue }}", pushups.sumOf { it.targetValue } <= 3 * 8)
    }

    @Test
    fun `czas pracy na czas bierze sie z cwiczenia, nie z celu planu`() {
        // 60 minut, żeby lista ćwiczeń była długa i weszły zarówno hollow (20 s), jak i deska (40 s).
        val plan = PlanGenerator.generate(profile.copy(sessionMinutes = 60), library, now = 0L)
        val timed = plan.workouts.flatMap { itemsOf(it) }.filter { it.targetKind == TargetKind.SECONDS }

        assertTrue(timed.isNotEmpty())
        timed.forEach { item ->
            val base = byId.getValue(item.exerciseId).defaultSeconds
            assertTrue(
                "${item.exerciseId}: ${item.targetValue} s przy bazie $base s",
                item.targetValue in (base - 10).coerceAtLeast(20)..(base + 10),
            )
        }
        val hollow = timed.filter { it.exerciseId == "hollow" }
        val plank = timed.filter { it.exerciseId == "plank" }
        assertTrue("hollow powinien wejść do planu", hollow.isNotEmpty())
        assertTrue("deska powinna wejść do planu", plank.isNotEmpty())
        assertTrue(hollow.all { it.targetValue <= 30 })
        assertTrue(plank.all { it.targetValue >= 30 })
    }

    @Test
    fun `dwie pozycje nie staja sie tym samym cwiczeniem po podmianie wariantu poziomu`() {
        // bridge → single-bridge u zaawansowanego, squats → jump-squats: każde z nich może wejść tylko raz.
        val advanced = profile.copy(level = Difficulty.ADVANCED, sessionMinutes = 45)

        val plan = PlanGenerator.generate(advanced, library, now = 0L)

        plan.workouts.forEach { workout ->
            val shown = workSteps(workout, Difficulty.ADVANCED).map { it.exerciseId }.distinct()
            assertEquals("${workout.name}: $shown", shown.size, idsOf(workout).size)
        }
    }

    @Test
    fun `wariant poziomu wymagajacy sprzetu, ktorego nie ma, nie wchodzi do krokow`() {
        // pushups → decline-pushups (ławka) u zaawansowanego. Bez ławki kandydat zostaje w wersji bazowej,
        // zamiast wypadać z planu; z ławką player pokazuje wariant.
        val advanced = profile.copy(level = Difficulty.ADVANCED, sessionMinutes = 45)
        val withoutBench = PlanGenerator.generate(advanced, library, now = 0L).workouts
            .flatMap { workSteps(it, Difficulty.ADVANCED) }.map { it.exerciseId }.toSet()
        assertFalse(withoutBench.contains("decline-pushups"))

        val withBench = advanced.copy(equipment = setOf(Equipment.BENCH))
        val shown = PlanGenerator.generate(withBench, library, now = 0L).workouts
            .flatMap { workSteps(it, Difficulty.ADVANCED, setOf(Equipment.BENCH)) }.map { it.exerciseId }.toSet()
        assertTrue(shown.contains("decline-pushups"))
    }

    @Test
    fun `kazdy krok planu da sie wykonac posiadanym sprzetem`() {
        // Jedno zdanie zamykające całą klasę błędu: cokolwiek player pokaże, ma być wykonalne.
        for (level in Difficulty.entries) {
            for (equipment in listOf(emptySet(), setOf(Equipment.DUMBBELLS, Equipment.BENCH))) {
                val p = profile.copy(level = level, equipment = equipment, sessionMinutes = 45)
                PlanGenerator.generate(p, library, now = 0L).workouts.forEach { workout ->
                    workSteps(workout, level, equipment).forEach { step ->
                        val exercise = byId.getValue(step.exerciseId)
                        assertTrue("$level/$equipment: ${exercise.id}", exercise.canDoWith(equipment))
                    }
                }
            }
        }
    }

    @Test
    fun `zmiana poziomu po zalozeniu cyklu nie wystawia cwiczenia na brakujacy sprzet`() {
        // Plan ułożony na średniozaawansowanym, potem użytkownik przełącza poziom w ustawieniach.
        val plan = PlanGenerator.generate(profile, library, now = 0L)
        plan.workouts.forEach { workout ->
            workSteps(workout, Difficulty.ADVANCED).forEach { step ->
                assertTrue(step.exerciseId, byId.getValue(step.exerciseId).canDoWith(emptySet()))
            }
        }
    }

    @Test
    fun `poczatkujacy nie dostaje cwiczen o dwa poziomy za trudnych`() {
        val beginner = profile.copy(level = Difficulty.BEGINNER, sessionMinutes = 45)

        val used = idsIn(PlanGenerator.generate(beginner, library, now = 0L)).toSet()

        assertFalse(used.contains("pistol"))
        assertFalse(used.contains("dips"))
        assertTrue(used.contains("squats"))
    }

    @Test
    fun `dluga lista cwiczen dzieli sie na rowne czesci po najwyzej szesc`() {
        val plan = PlanGenerator.generate(profile.copy(sessionMinutes = 60), library, now = 0L)

        plan.workouts.forEach { workout ->
            val sizes = workout.blocks.map { it.items.size }
            assertTrue("${workout.name}: $sizes", sizes.size >= 2)
            assertTrue("${workout.name}: $sizes", sizes.all { it <= PlanGenerator.BLOCK_ITEMS })
            assertTrue("${workout.name}: $sizes", sizes.max() - sizes.min() <= 1)
            assertTrue(workout.blocks.all { it.kind == BlockKind.CIRCUIT })
        }
    }

    @Test
    fun `za mala biblioteka daje krotszy trening zamiast dokladania rund i mowi to w notatce`() {
        val tiny = listOf(
            exercise("squats", listOf(MuscleGroup.QUADS), Difficulty.BEGINNER),
            exercise("pushups", listOf(MuscleGroup.CHEST), Difficulty.BEGINNER),
            exercise("plank", listOf(MuscleGroup.ABS), Difficulty.BEGINNER, kind = ExerciseKind.TIME),
            exercise("climbers", listOf(MuscleGroup.FULL_BODY), category = ExerciseCategory.CARDIO, kind = ExerciseKind.TIME),
        )
        val plan = PlanGenerator.generate(profile.copy(sessionMinutes = 60), tiny, now = 0L)
        val workout = plan.workouts.first()

        assertEquals(4, itemsOf(workout).size)
        assertTrue(repeatsOf(workout).all { it in PlanGenerator.repeatsRange(profile.level, profile.goal) })
        assertTrue(estimatedSeconds(workout) < 60 * 60 - PlanGenerator.TIME_TOLERANCE_SEC)
        assertTrue(workout.note, workout.note.contains("wyszło ok."))

        val fits = PlanGenerator.generate(profile, library, now = 0L).workouts.first()
        assertFalse(fits.note, fits.note.contains("wyszło"))
    }

    @Test
    fun `cwiczenie ze zdjeciem ma pierwszenstwo, ale brak zdjecia nie wyklucza`() {
        val withPhoto = exercise("core-photo", listOf(MuscleGroup.ABS)).copy(images = listOf("seed/images/core-photo/0.jpg"))
        val withoutPhoto = exercise("core-nophoto", listOf(MuscleGroup.ABS))
        val onlyOption = exercise("cardio-nophoto", listOf(MuscleGroup.FULL_BODY), category = ExerciseCategory.CARDIO)
        val lib = listOf(
            exercise("squats", listOf(MuscleGroup.QUADS)).copy(images = listOf("seed/images/squats/0.jpg")),
            exercise("bridge", listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS)).copy(images = listOf("seed/images/bridge/0.jpg")),
            exercise("pushups", listOf(MuscleGroup.CHEST)).copy(images = listOf("seed/images/pushups/0.jpg")),
            exercise("rows", listOf(MuscleGroup.UPPER_BACK)).copy(images = listOf("seed/images/rows/0.jpg")),
            withoutPhoto, withPhoto, onlyOption,
        )

        val ids = idsOf(PlanGenerator.generate(profile, lib, now = 0L).workouts.first())

        assertTrue("brzuch powinien wziąć najpierw wersję ze zdjęciem", ids.indexOf("core-photo") >= 0)
        val nophoto = ids.indexOf("core-nophoto")
        assertTrue(nophoto < 0 || ids.indexOf("core-photo") < nophoto)
        // kardio ma tylko jednego kandydata bez zdjęcia — i tak wchodzi, bo to preferencja, nie filtr
        assertTrue(ids.contains("cardio-nophoto"))
    }

    @Test
    fun `krotszy i dluzszy trening roznia sie dlugoscia`() {
        val short = PlanGenerator.generate(profile.copy(sessionMinutes = 15), library, now = 0L).workouts.first()
        val long = PlanGenerator.generate(profile.copy(sessionMinutes = 45), library, now = 0L).workouts.first()

        assertTrue(estimatedSeconds(short) < estimatedSeconds(long))
    }

    @Test
    fun `cwiczenia na powtorzenia dostaja powtorzenia takze w obwodzie`() {
        val plan = PlanGenerator.generate(profile.copy(goal = Goal.FAT_LOSS), library, now = 0L)
        val items = plan.workouts.flatMap { itemsOf(it) }

        val reps = items.filter { it.targetKind == TargetKind.REPS }.map { it.exerciseId }.toSet()
        val onTime = items.filter { it.targetKind == TargetKind.SECONDS }.map { it.exerciseId }.toSet()

        assertTrue("obwód powinien mieć pozycje na powtórzenia", reps.isNotEmpty())
        // deska i wspinaczka to izometria i cardio — te zostają na czas
        assertTrue(onTime.none { id -> byId.getValue(id).kind == ExerciseKind.REPS })
        assertTrue(reps.none { id -> byId.getValue(id).kind == ExerciseKind.TIME })
    }

    @Test
    fun `ten sam profil daje ten sam plan`() {
        val a = PlanGenerator.generate(profile, library, now = 0L)
        val b = PlanGenerator.generate(profile, library, now = 0L)

        assertEquals(idsIn(a), idsIn(b))
        assertEquals(a.workouts.map { it.blocks }, b.workouts.map { it.blocks })
        assertEquals(a.days, b.days)
    }

    @Test
    fun `treningi w rotacji roznia sie doborem cwiczen`() {
        val plan = PlanGenerator.generate(profile.copy(daysPerWeek = 3), library, now = 0L)

        val first = idsOf(plan.workouts[0])
        val second = idsOf(plan.workouts[1])
        assertTrue(first.isNotEmpty() && second.isNotEmpty())
        assertTrue("rotacja powtarza ten sam trening", first != second)
    }

    @Test
    fun `podzial na gore i dol przy czterech dniach`() {
        val plan = PlanGenerator.generate(profile.copy(daysPerWeek = 4), library, now = 0L)

        assertEquals(listOf("Góra A", "Dół A", "Góra B", "Dół B"), plan.workouts.map { it.name })
    }

    @Test
    fun `pusta biblioteka albo brak pasujacych cwiczen daje pusty plan zamiast wyjatku`() {
        assertEquals(0, PlanGenerator.generate(profile, emptyList(), now = 0L).workouts.size)

        val onlyEquipment = library.filter { it.requiresEquipment }
        assertEquals(0, PlanGenerator.generate(profile, onlyEquipment, now = 0L).workouts.size)
    }

    @Test
    fun `rozciaganie nie trafia do planu poza celem mobilnosc`() {
        val condition = PlanGenerator.generate(profile.copy(goal = Goal.CONDITION), library, now = 0L)
        assertFalse(idsIn(condition).contains("stretch-hams"))

        val mobility = PlanGenerator.generate(profile.copy(goal = Goal.MOBILITY), library, now = 0L)
        assertTrue(idsIn(mobility).contains("stretch-hams"))
    }

    @Test
    fun `mobilnosc bierze rozciaganie przed cwiczeniami silowymi na te sama partie`() {
        val ids = idsOf(PlanGenerator.generate(profile.copy(goal = Goal.MOBILITY), library, now = 0L).workouts.first())

        val stretch = ids.indexOf("stretch-hams")
        val hinge = ids.indexOf("goodmorning")
        assertTrue(ids.toString(), stretch >= 0)
        assertTrue(ids.toString(), hinge < 0 || stretch < hinge)
    }

    @Test
    fun `trening nie powtarza tej samej partii, dopoki sa inne do wziecia`() {
        val plan = PlanGenerator.generate(profile.copy(daysPerWeek = 4, sessionMinutes = 20), library, now = 0L)
        val upper = plan.workouts.first { it.name == "Góra A" }

        val primary = idsOf(upper).mapNotNull { byId[it]?.primaryMuscles?.first() }
        val upperMuscles = setOf(
            MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS,
            MuscleGroup.LATS, MuscleGroup.UPPER_BACK, MuscleGroup.BICEPS, MuscleGroup.ABS, MuscleGroup.LOWER_BACK,
        )
        val available = library
            .filter { !it.requiresEquipment && it.category != ExerciseCategory.STRETCHING }
            .map { it.primaryMuscles.first() }
            .filter { it in upperMuscles }
            .toSet()
        assertEquals("ta sama partia za wcześnie: $primary", min(primary.size, available.size), primary.toSet().size)
    }

    @Test
    fun `wygenerowane treningi sa wlasne, nie wbudowane`() {
        val plan = PlanGenerator.generate(profile, library, now = 123L)

        assertTrue(plan.workouts.all { !it.isBuiltIn })
        assertTrue(plan.workouts.all { it.createdAt == 123L })
        assertNotNull(plan.workouts.first().note)
    }

    @Test
    fun `wykluczone cwiczenia nie wchodza do planu ani jako pozycja, ani jako wariant poziomu`() {
        val plan = PlanGenerator.generate(profile, library, now = 0L, excluded = setOf("pushups", "squats"))
        assertTrue(plan.workouts.isNotEmpty())
        assertFalse(idsIn(plan).contains("pushups"))
        assertFalse(idsIn(plan).contains("squats"))

        // u zaawansowanego przysiady pokazują się jako „jump-squats”; wykluczenie wariantu blokuje pozycję bazową
        val advanced = PlanGenerator.generate(
            profile.copy(level = Difficulty.ADVANCED, sessionMinutes = 45), library, now = 0L, excluded = setOf("jump-squats"),
        )
        assertTrue(advanced.workouts.isNotEmpty())
        val shown = advanced.workouts.flatMap { workSteps(it, Difficulty.ADVANCED) }.map { it.exerciseId }
        assertFalse(shown.contains("jump-squats"))
        assertFalse(idsIn(advanced).contains("squats"))
    }

    @Test
    fun `ergometr nie wchodzi do planu z kreatora, nawet gdy go masz`() {
        // Kardio z maszyną (D-44) pasowałoby do slotu kardio, ale kreator zostaje przy ćwiczeniach bez maszyn.
        val rower = TestFixtures.exercise(
            "rower-sprint", category = ExerciseCategory.CARDIO, kind = ExerciseKind.TIME,
            equipment = setOf(Equipment.STATIONARY_BIKE), primary = listOf(MuscleGroup.FULL_BODY, MuscleGroup.QUADS),
        )
        val plan = PlanGenerator.generate(profile.copy(equipment = setOf(Equipment.STATIONARY_BIKE)), library + rower, now = 0L)

        assertFalse(idsIn(plan).contains("rower-sprint"))
    }

    @Test
    fun `wycofane z bazy cwiczenie nie trafia do nowego planu`() {
        // Wycofanie (D-47) zostawia ćwiczenie dla historii i zapisanych planów, ale kreator już go nie proponuje.
        val wycofane = setOf("lunges", "pushups", "plank", "climbers")
        val biblioteka = library.map { if (it.id in wycofane) it.copy(retiredAt = "2026-10-28") else it }

        val plan = PlanGenerator.generate(profile, biblioteka, now = 0L)

        assertTrue(idsIn(plan).isNotEmpty())
        assertTrue("wycofane w planie: ${idsIn(plan)}", idsIn(plan).none { it in wycofane })
        // Bez wycofania ta sama biblioteka je wybiera, więc test sprawdza filtr, a nie przypadek.
        assertTrue(idsIn(PlanGenerator.generate(profile, library, now = 0L)).any { it in wycofane })
    }
}
