package pl.mysttic.trainings.domain.plan.share

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.editor.WorkoutEditing
import pl.mysttic.trainings.domain.engine.TestFixtures
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.model.Workout
import pl.mysttic.trainings.domain.plan.PlanDay
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.TrainingProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class PlanFileCodecTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val a = Workout(
        id = "plan-x-1",
        name = "Góra",
        note = "Rozgrzej barki",
        blocks = listOf(
            Block(
                kind = BlockKind.SETS,
                label = "Siła",
                restAfterSec = 90,
                items = listOf(BlockItem("pushups", TargetKind.REPS, 8, sets = 4, weightKg = 12.5, note = "łokcie blisko")),
            ),
        ),
        createdAt = 1,
        updatedAt = 1,
    )
    private val b = Workout(
        id = "plan-x-2",
        name = "Dół",
        blocks = listOf(
            Block(
                kind = BlockKind.CIRCUIT, rounds = 3,
                items = listOf(
                    BlockItem("squats", TargetKind.REPS, 15, sets = 1, windowSec = 60),
                    BlockItem("plank", TargetKind.SECONDS, 40, sets = 1),
                ),
            ),
        ),
        createdAt = 1,
        updatedAt = 1,
    )
    private val program = Program(
        id = "plan-x", name = "Plan od Oli", profile = TrainingProfile(level = Difficulty.BEGINNER), startedAt = 0, cycleDays = 4,
        createdAt = 0, active = true, source = PlanSource.CUSTOM, description = "4 dni na próbę",
        days = listOf(PlanDay(1, "plan-x-1"), PlanDay(2, null), PlanDay(3, "plan-x-2"), PlanDay(4, "plan-x-1")),
    )
    private val workouts = listOf(a, b).associateBy { it.id }

    /** Autor na poziomie średnim ze wszystkim sprzętem: bloki jadą w pliku bez zmian (mnożnik 1, bez wariantu). */
    private fun exported(difficulty: Difficulty = Difficulty.INTERMEDIATE, owned: Set<Equipment> = Equipment.entries.toSet()) =
        PlanFileCodec.export(program, workouts, TestFixtures.exercises, "abc-123", "Ola", "0.10.0", 6, 99, difficulty, owned)

    @Test
    fun `eksport jest deterministyczny i nie niesie sylwetki ani id bazy`() {
        val file = exported()
        assertEquals(file, exported())
        assertEquals(listOf("w1", null, "w2", "w1"), file.plan.days.map { it.workout })
        assertEquals(listOf("plank", "pushups", "squats"), file.plan.exercises.map { it.id })
        val text = PlanFileCodec.encode(file, json)
        assertFalse(text.contains("plan-x"))
        assertFalse(text.contains("heightCm"))
        assertTrue(text.contains("\"format\": \"mysttic-trainings-plan\""))
    }

    @Test
    fun `plik wraca w calosci i wchodzi doslownie`() {
        val text = PlanFileCodec.encode(exported(), json)
        val imported = PlanFileCodec.validate(PlanFileCodec.decode(text, json), TestFixtures.exercises)

        assertEquals("Plan od Oli", imported.name)
        assertEquals("Ola", imported.author)
        // Bloki dopasowują się do poziomu, więc w pliku stoi poziom, dla którego je policzono, a nie poziom z kreatora.
        assertEquals(Difficulty.INTERMEDIATE, imported.level)
        assertEquals(listOf("w1", null, "w2", "w1"), imported.days.map { it.workoutId })
        val up = imported.workouts.first { it.id == "w1" }
        assertEquals(a.blocks.map { it.copy(fixed = true) }, up.blocks)
        // Okno EMOM jedzie w pliku (D-43): bez niego odbiorca dostałby przerwę zapasową zamiast minuty.
        assertEquals(b.blocks.map { it.copy(fixed = true) }, imported.workouts.first { it.id == "w2" }.blocks)
        assertEquals("Rozgrzej barki", up.note)
        assertTrue(imported.workouts.all { it.isFixed })
        assertTrue(imported.newExercises.isEmpty())
        assertTrue(imported.warnings.isEmpty())
    }

    @Test
    fun `plan dopasowany do poziomu jedzie tak, jak widzi go autor`() {
        // Autor zaawansowany: pompki 8 z szablonu to u niego „Pompki z nogami wyżej” 10 powt., przerwy krótsze.
        val file = exported(difficulty = Difficulty.ADVANCED)
        val item = file.plan.workouts.first { it.key == "w1" }.blocks.single().items.single()

        assertEquals("decline-pushups", item.exercise)
        assertEquals(10, item.value)
        assertEquals(35, item.restAfterSetSec)
        assertEquals(Difficulty.ADVANCED.name, file.plan.level)
        assertTrue("decline-pushups" in file.plan.exercises.map { it.id })
        assertFalse("pushups" in file.plan.exercises.map { it.id })

        // Po imporcie to są dokładnie te liczby, co u autora, i już się nie skalują.
        val imported = PlanFileCodec.validate(PlanFileCodec.decode(PlanFileCodec.encode(file, json), json), TestFixtures.exercises)
        val block = imported.workouts.first { it.id == "w1" }.blocks.single()
        assertTrue(block.fixed)
        assertEquals(10, block.items.single().targetValue)
    }

    @Test
    fun `wariant bez sprzetu autora i staly blok zostaja jak w szablonie`() {
        val fixedB = b.copy(blocks = b.blocks.map { it.copy(fixed = true) })
        val file = PlanFileCodec.export(
            program, mapOf(a.id to a, b.id to fixedB), TestFixtures.exercises, "abc-123", "Ola", "0.10.0", 6, 99,
            difficulty = Difficulty.BEGINNER, owned = emptySet(),
        )
        // Początkujący: „Pompki na kolanach” nie wymagają sprzętu, więc wariant wchodzi; cel 8 * 0,7 = 6.
        val up = file.plan.workouts.first { it.key == "w1" }.blocks.single().items.single()
        assertEquals("knee-pushups", up.exercise)
        assertEquals(6, up.value)
        // Stały blok nie zmienia się wcale.
        val down = file.plan.workouts.first { it.key == "w2" }.blocks.single()
        assertEquals(listOf(15, 40), down.items.map { it.value })
        assertEquals(listOf("squats", "plank"), down.items.map { it.exercise })
    }

    @Test
    fun `kopia zapasowa, byle JSON i nowsza wersja sa odrzucone z czytelnym komunikatem`() {
        fun message(text: String) = runCatching { PlanFileCodec.decode(text, json) }.exceptionOrNull()?.message.orEmpty()

        assertTrue(message("""{"app":"mysttic-trainings","version":3}""").contains("kopia zapasowa"))
        assertTrue(message("{}").contains("nie jest plik planu"))
        assertTrue(message("[]").contains("nie jest plik planu"))
        assertTrue(message("""{"format":"mysttic-trainings-plan","formatVersion":3,"plan":{}}""").contains("nowszej"))
        assertTrue(message("x".repeat(PlanFile.MAX_BYTES + 1)).contains("za duży"))
    }

    @Test
    fun `wrogie wartosci sa przyciete, a teksty oczyszczone`() {
        val hostile = exported().let { f ->
            f.copy(
                plan = f.plan.copy(
                    name = "‮plan\u0000",
                    workouts = f.plan.workouts.map { w ->
                        val window = if (w.key == "w1") -5 else 100_000
                        w.copy(
                            blocks = w.blocks.map { bl ->
                                bl.copy(
                                    rounds = 1_000_000,
                                    items = bl.items.map { it.copy(value = -5, sets = 900, weightKg = Double.NaN, windowSec = window) },
                                )
                            },
                        )
                    },
                ),
            )
        }
        val imported = PlanFileCodec.validate(hostile, TestFixtures.exercises)
        assertEquals("plan", imported.name)
        val block = imported.workouts.first().blocks.first()
        assertEquals(1, block.items.first().targetValue)
        assertEquals(20, block.items.first().sets)
        assertEquals(null, block.items.first().weightKg)
        assertEquals(null, block.items.first().windowSec)
        val circuit = imported.workouts.first { it.id == "w2" }.blocks.first()
        assertEquals(30, circuit.rounds)
        assertEquals(WorkoutEditing.MAX_WINDOW_SEC, circuit.items.first().windowSec)
    }

    @Test
    fun `dzien z nieznanym treningiem, powtorzony dzien i zly uid odrzucaja plik`() {
        val base = exported()
        fun rejected(f: PlanFile) = runCatching { PlanFileCodec.validate(f, TestFixtures.exercises) }.isFailure

        assertTrue(rejected(base.copy(plan = base.plan.copy(days = base.plan.days + SharedDay(9, "w7")))))
        assertTrue(rejected(base.copy(plan = base.plan.copy(days = base.plan.days + SharedDay(1, null)))))
        assertTrue(rejected(base.copy(plan = base.plan.copy(uid = "../../etc"))))
        assertTrue(rejected(base.copy(plan = base.plan.copy(days = listOf(SharedDay(500, "w1"))))))
    }

    @Test
    fun `nieznany cel i nieznany sprzet usuwaja pozycje, nie wysadzaja odczytu`() {
        val base = exported()
        val file = base.copy(
            plan = base.plan.copy(
                workouts = base.plan.workouts.map { w ->
                    if (w.key != "w2") w else w.copy(blocks = w.blocks.map { bl -> bl.copy(items = bl.items + SharedItem("rower", "WATTS", 200)) })
                },
                exercises = base.plan.exercises + SharedExercise("rower", "Rower", kind = "TIME", category = "CARDIO", equipment = listOf("BIKE")),
            ),
        )
        val imported = PlanFileCodec.validate(file, TestFixtures.exercises)
        assertEquals(2, imported.workouts.first { it.id == "w2" }.blocks.single().items.size)
        assertTrue(imported.warnings.isNotEmpty())
        assertTrue(imported.newExercises.isEmpty())
    }

    @Test
    fun `cwiczenie spoza biblioteki odbiorcy przychodzi z pliku, wlasne autora dostaje id ze skrotu`() {
        val base = exported()
        val custom = SharedExercise("custom-moje", "Wiosłowanie ręcznikiem", kind = "REPS", category = "STRENGTH")
        val newer = SharedExercise("farmer-walk", "Spacer farmera", kind = "TIME", category = "STRENGTH", equipment = listOf("DUMBBELLS"))
        val file = base.copy(
            plan = base.plan.copy(
                workouts = base.plan.workouts.map { w ->
                    if (w.key != "w1") w else w.copy(blocks = w.blocks + SharedBlock("SETS", items = listOf(SharedItem("custom-moje", "REPS", 10), SharedItem("farmer-walk", "SECONDS", 40))))
                },
                exercises = base.plan.exercises + custom + newer,
            ),
        )
        val imported = PlanFileCodec.validate(file, TestFixtures.exercises)
        val ids = imported.newExercises.map { it.id }
        assertTrue("farmer-walk" in ids)
        val customId = ids.single { it.startsWith("custom-") }
        assertEquals(customId, PlanFileCodec.validate(file, TestFixtures.exercises).newExercises.single { it.id.startsWith("custom-") }.id)
        assertTrue(customId != "custom-moje")
        assertTrue(imported.newExercises.all { it.isCustom && it.images.isEmpty() })
        assertTrue(customId in imported.workouts.first { it.id == "w1" }.exerciseIds)
    }

    @Test
    fun `dwa cwiczenia autora rozne tylko instrukcja i poziomem dostaja rozne id`() {
        val base = exported()
        val towel = SharedExercise(
            "custom-a", "Wiosłowanie", kind = "REPS", category = "STRENGTH", level = "INTERMEDIATE",
            instructionsPl = listOf("Ciągnij ręcznik."),
        )
        val band = towel.copy(id = "custom-b", level = "ADVANCED", instructionsPl = listOf("Ciągnij gumę."))
        val file = base.copy(
            plan = base.plan.copy(
                workouts = base.plan.workouts.map { w ->
                    if (w.key != "w1") w else w.copy(blocks = w.blocks + SharedBlock("SETS", items = listOf(SharedItem("custom-a", "REPS", 10), SharedItem("custom-b", "REPS", 10))))
                },
                exercises = base.plan.exercises + towel + band,
            ),
        )
        val imported = PlanFileCodec.validate(file, TestFixtures.exercises)
        val custom = imported.newExercises.filter { it.id.startsWith("custom-") }
        assertEquals(2, custom.size)
        assertEquals(setOf(listOf("Ciągnij ręcznik."), listOf("Ciągnij gumę.")), custom.map { it.instructionsPl }.toSet())
        val items = imported.workouts.first { it.id == "w1" }.blocks.last().items.map { it.exerciseId }
        assertEquals(custom.map { it.id }.toSet(), items.toSet())
        // Ta sama treść dalej daje to samo id.
        assertEquals(custom.map { it.id }, PlanFileCodec.validate(file, TestFixtures.exercises).newExercises.filter { it.id.startsWith("custom-") }.map { it.id })
    }

    @Test
    fun `wartosci domyslne cwiczenia z pliku trzymaja sie widelek seedu`() {
        val base = exported()
        val hostile = SharedExercise("custom-x", "Aaa pompki", kind = "REPS", category = "STRENGTH", defaultReps = 999, defaultSeconds = 100_000)
        val tiny = SharedExercise("custom-y", "Zzz deska", kind = "TIME", category = "STRETCHING", defaultReps = -3, defaultSeconds = 0)
        val file = base.copy(
            plan = base.plan.copy(
                workouts = base.plan.workouts.map { w ->
                    if (w.key != "w1") w else w.copy(blocks = w.blocks + SharedBlock("SETS", items = listOf(SharedItem("custom-x", "REPS", 10), SharedItem("custom-y", "SECONDS", 30))))
                },
                exercises = base.plan.exercises + hostile + tiny,
            ),
        )
        val added = PlanFileCodec.validate(file, TestFixtures.exercises).newExercises.associateBy { it.namePl }
        assertEquals(PlanFileCodec.MAX_DEFAULT_REPS, added.getValue("Aaa pompki").defaultReps)
        assertEquals(PlanFileCodec.MAX_DEFAULT_SECONDS, added.getValue("Aaa pompki").defaultSeconds)
        assertEquals(1, added.getValue("Zzz deska").defaultReps)
        assertEquals(PlanFileCodec.MIN_DEFAULT_SECONDS, added.getValue("Zzz deska").defaultSeconds)
    }

    @Test
    fun `plik wzorcowy z repozytorium dalej sie wczytuje`() {
        // Zamrożone nazwy pól (umowa na lata): jeśli ten test padnie, stare pliki od ludzi przestaną się wczytywać.
        val text = javaClass.classLoader!!.getResourceAsStream("plan-wzorcowy.plan.json")!!.readBytes().decodeToString()
        val imported = PlanFileCodec.validate(PlanFileCodec.decode(text, json), TestFixtures.exercises)
        assertEquals("Plan wzorcowy", imported.name)
        assertEquals(3, imported.days.size)
        assertEquals(2, imported.trainingDays)
        assertEquals(12.5, imported.workouts.single().blocks.single().items.first().weightKg)
        // Pozycja z samymi polami wymaganymi: pola dopisane później (np. windowSec z D-43) biorą wartości domyślne.
        assertEquals(null, imported.workouts.single().blocks.single().items[1].windowSec)
    }

    @Test
    fun `nazwa pliku bez polskich znakow i spacji`() {
        assertEquals("plan-od-oli-zolw.plan.json", PlanFileCodec.fileName("Plan od Oli: żółw!"))
        assertEquals("plan.plan.json", PlanFileCodec.fileName("???"))
    }

    @Test
    fun `notatki do cwiczen jada w pliku tylko dla cwiczen z planu`() {
        val file = PlanFileCodec.export(
            program, workouts, TestFixtures.exercises, "abc-123", "Ola", "0.10.0", 6, 99,
            Difficulty.INTERMEDIATE, Equipment.entries.toSet(),
            notes = mapOf("pushups" to "  12 kg w plecaku ", "burpees" to "nie ma go w planie", "squats" to "   "),
        )
        assertEquals(listOf(SharedNote("pushups", "12 kg w plecaku")), file.plan.notes)

        val imported = PlanFileCodec.validate(PlanFileCodec.decode(PlanFileCodec.encode(file, json), json), TestFixtures.exercises)
        assertEquals(mapOf("pushups" to "12 kg w plecaku"), imported.notes)
    }

    @Test
    fun `plik bez notatek wczytuje sie z pusta mapa, a notatka do obcego cwiczenia wypada`() {
        val file = exported().let { f -> f.copy(plan = f.plan.copy(notes = listOf(SharedNote("nieznane-cwiczenie", "x")))) }
        val imported = PlanFileCodec.validate(PlanFileCodec.decode(PlanFileCodec.encode(file, json), json), TestFixtures.exercises)
        assertTrue(imported.notes.isEmpty())

        // Plik sprzed D-44: bez klucza „notes”.
        val tree = json.parseToJsonElement(PlanFileCodec.encode(exported(), json)).jsonObject
        val bare = JsonObject(tree + ("plan" to JsonObject(tree.getValue("plan").jsonObject - "notes"))).toString()
        assertFalse(bare.contains("\"notes\""))
        assertTrue(PlanFileCodec.validate(PlanFileCodec.decode(bare, json), TestFixtures.exercises).notes.isEmpty())
    }
}
