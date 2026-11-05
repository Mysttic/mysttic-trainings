package pl.mysttic.trainings.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.engine.TestFixtures
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.TargetKind

class WorkoutEditingTest {

    private fun item(id: String, sets: Int = 3, value: Int = 10, kind: TargetKind = TargetKind.REPS) =
        BlockItem(exerciseId = id, targetKind = kind, targetValue = value, sets = sets)

    private val setsBlock = Block(kind = BlockKind.SETS, items = listOf(item("pushups"), item("squats"), item("plank")))

    @Test
    fun `nowy element bierze domyslne wartosci cwiczenia`() {
        val reps = WorkoutEditing.newItem(TestFixtures.pushups, BlockKind.SETS)
        val time = WorkoutEditing.newItem(TestFixtures.plank, BlockKind.SETS)

        assertEquals(TargetKind.REPS, reps.targetKind)
        assertEquals(12, reps.targetValue)
        assertEquals(WorkoutEditing.DEFAULT_SETS, reps.sets)
        assertEquals(TargetKind.SECONDS, time.targetKind)
        assertEquals(30, time.targetValue)
    }

    @Test
    fun `element w obwodzie ma jedna serie`() {
        val item = WorkoutEditing.newItem(TestFixtures.pushups, BlockKind.CIRCUIT)

        assertEquals(1, item.sets)
        assertEquals(15, item.restAfterItemSec)
    }

    @Test
    fun `przesuwanie elementu zmienia kolejnosc, zakres poza lista nic nie robi`() {
        val blocks = listOf(setsBlock)

        val moved = WorkoutEditing.moveItem(blocks, 0, from = 2, to = 0)
        assertEquals(listOf("plank", "pushups", "squats"), moved[0].items.map { it.exerciseId })

        assertEquals(blocks, WorkoutEditing.moveItem(blocks, 0, from = 0, to = 9))
        assertEquals(blocks, WorkoutEditing.moveItem(blocks, 0, from = 1, to = 1))
        assertEquals(blocks, WorkoutEditing.moveItem(blocks, 5, from = 0, to = 1))
    }

    @Test
    fun `usuwanie elementu i bloku`() {
        val blocks = listOf(setsBlock, WorkoutEditing.emptyBlock(BlockKind.CIRCUIT))

        assertEquals(listOf("pushups", "plank"), WorkoutEditing.removeItem(blocks, 0, 1)[0].items.map { it.exerciseId })
        assertEquals(1, WorkoutEditing.removeBlock(blocks, 1).size)
        assertEquals(blocks, WorkoutEditing.removeBlock(blocks, 7))
        assertEquals(blocks, WorkoutEditing.removeItem(blocks, 0, 7))
    }

    @Test
    fun `zmiana rodzaju bloku zachowuje cwiczenia i zeruje serie w obwodzie`() {
        val toCircuit = WorkoutEditing.changeBlockKind(listOf(setsBlock), 0, BlockKind.CIRCUIT)

        assertEquals(BlockKind.CIRCUIT, toCircuit[0].kind)
        assertEquals(listOf("pushups", "squats", "plank"), toCircuit[0].items.map { it.exerciseId })
        assertTrue(toCircuit[0].items.all { it.sets == 1 })
        assertEquals(WorkoutEditing.DEFAULT_ROUNDS, toCircuit[0].rounds)

        val back = WorkoutEditing.changeBlockKind(toCircuit, 0, BlockKind.SETS)
        assertEquals(BlockKind.SETS, back[0].kind)
        assertEquals(3, back[0].items.size)
    }

    @Test
    fun `wartosci sa przycinane do zakresow`() {
        val wild = item("pushups", sets = 99, value = 5000).copy(restAfterSetSec = -5, restAfterItemSec = 9999)

        val clean = WorkoutEditing.sanitize(wild)

        assertEquals(WorkoutEditing.MAX_SETS, clean.sets)
        assertEquals(WorkoutEditing.MAX_TARGET_VALUE, clean.targetValue)
        assertEquals(0, clean.restAfterSetSec)
        assertEquals(WorkoutEditing.MAX_REST_SEC, clean.restAfterItemSec)
    }

    @Test
    fun `walidacja lapie pusta nazwe, brak blokow i pusty blok`() {
        assertTrue(WorkoutEditing.validate("Full body A", listOf(setsBlock)).isEmpty())

        assertEquals(1, WorkoutEditing.validate("  ", listOf(setsBlock)).size)
        assertEquals(1, WorkoutEditing.validate("Trening", emptyList()).size)

        val withEmpty = WorkoutEditing.validate("Trening", listOf(setsBlock, WorkoutEditing.emptyBlock(BlockKind.SETS)))
        assertEquals(1, withEmpty.size)
        assertTrue(withEmpty.single().contains("Blok 2"))
    }

    @Test
    fun `liczba serii do wykonania liczy serie w SETS i rundy w obwodzie`() {
        val circuit = Block(kind = BlockKind.CIRCUIT, rounds = 4, items = listOf(item("a", sets = 1), item("b", sets = 1)))

        assertEquals(9, WorkoutEditing.workCount(listOf(setsBlock)))
        assertEquals(8, WorkoutEditing.workCount(listOf(circuit)))
        assertEquals(17, WorkoutEditing.workCount(listOf(setsBlock, circuit)))
    }

    @Test
    fun `opis celu zalezy od rodzaju bloku i celu`() {
        assertEquals("3 × 10 powt.", WorkoutEditing.targetLabel(item("a"), BlockKind.SETS))
        assertEquals("10 powt.", WorkoutEditing.targetLabel(item("a", sets = 1), BlockKind.CIRCUIT))
        assertEquals("40 s", WorkoutEditing.targetLabel(item("a", sets = 1, value = 40, kind = TargetKind.SECONDS), BlockKind.CIRCUIT))
        assertEquals("AMRAP 60 s", WorkoutEditing.targetLabel(item("a", sets = 1, value = 60, kind = TargetKind.AMRAP), BlockKind.CIRCUIT))
    }

    @Test
    fun `zapis nowego treningu przycina nazwe i zawsze daje trening uzytkownika`() {
        val workout = WorkoutEditing.toWorkout(null, id = "nowe-id", name = "  Mój trening  ", note = " luzno ", blocks = listOf(setsBlock), now = 123L)

        assertEquals("nowe-id", workout.id)
        assertEquals("Mój trening", workout.name)
        assertEquals("luzno", workout.note)
        assertEquals(123L, workout.createdAt)
        assertEquals(123L, workout.updatedAt)
        assertEquals(false, workout.isBuiltIn)
    }

    @Test
    fun `edycja istniejacego zachowuje id i date utworzenia`() {
        val existing = pl.mysttic.trainings.domain.model.Workout(
            id = "stare", name = "Stary", blocks = listOf(setsBlock), createdAt = 10L, updatedAt = 10L, isBuiltIn = false,
        )

        val saved = WorkoutEditing.toWorkout(existing, id = "ignorowane", name = "Nowa nazwa", note = "", blocks = listOf(setsBlock), now = 999L)

        assertEquals("stare", saved.id)
        assertEquals(10L, saved.createdAt)
        assertEquals(999L, saved.updatedAt)
        assertEquals("Nowa nazwa", saved.name)
    }

    @Test
    fun `sito przycina ciezar, rytm i wskazowke, a zero kg znaczy brak ciezaru`() {
        val dirty = item("pushups").copy(weightKg = 900.0, cadenceSec = 0, note = "  kolana\u202E na zewnątrz\u0007  ")
        val clean = WorkoutEditing.sanitize(dirty)
        assertEquals(500.0, clean.weightKg)
        assertEquals(null, clean.cadenceSec)
        assertEquals("kolana na zewnątrz", clean.note)
        assertEquals(null, WorkoutEditing.sanitize(item("pushups").copy(weightKg = 0.0)).weightKg)
        assertEquals(null, WorkoutEditing.sanitize(item("pushups").copy(weightKg = Double.NaN)).weightKg)
        assertEquals(280, WorkoutEditing.sanitize(item("pushups").copy(note = "x".repeat(400))).note.length)
    }

    @Test
    fun `sito przycina okno EMOM, a zero i ujemne znacza brak okna`() {
        assertEquals(null, WorkoutEditing.sanitize(item("pushups").copy(windowSec = 0)).windowSec)
        assertEquals(null, WorkoutEditing.sanitize(item("pushups").copy(windowSec = -5)).windowSec)
        assertEquals(WorkoutEditing.MAX_WINDOW_SEC, WorkoutEditing.sanitize(item("pushups").copy(windowSec = 9999)).windowSec)
        assertEquals(60, WorkoutEditing.sanitize(item("pushups").copy(windowSec = 60)).windowSec)
    }

    @Test
    fun `sito bloku przycina rundy, przerwy i etykiete`() {
        val block = Block(kind = BlockKind.CIRCUIT, rounds = 1_000_000, restBetweenRoundsSec = -5, restAfterSec = 9999, label = "y".repeat(90), items = listOf(item("pushups")))
        val clean = WorkoutEditing.sanitizeBlock(block)
        assertEquals(30, clean.rounds)
        assertEquals(0, clean.restBetweenRoundsSec)
        assertEquals(600, clean.restAfterSec)
        assertEquals(40, clean.label.length)
    }

    @Test
    fun `podmiana cwiczenia zostawia serie, przerwy, ciezar i wskazowke`() {
        val withExtras = setsBlock.copy(items = listOf(item("pushups", sets = 4).copy(weightKg = 10.0, note = "wolno", windowSec = 60)))
        val result = WorkoutEditing.replaceExercise(listOf(withExtras), 0, 0, TestFixtures.plank).single().items.single()
        assertEquals("plank", result.exerciseId)
        assertEquals(TargetKind.SECONDS, result.targetKind)
        assertEquals(TestFixtures.plank.defaultTargetValue, result.targetValue)
        assertEquals(4, result.sets)
        assertEquals(10.0, result.weightKg)
        assertEquals("wolno", result.note)
        assertEquals(60, result.windowSec)
        // Ten sam rodzaj celu: wartość autora zostaje.
        val same = WorkoutEditing.replaceExercise(listOf(setsBlock), 0, 0, TestFixtures.squats).single().items[0]
        assertEquals(10, same.targetValue)
    }

    @Test
    fun `plan doslowny ustawia fixed we wszystkich blokach, a wbudowany trening nie jest do edycji`() {
        val fixed = WorkoutEditing.setFixed(listOf(setsBlock, setsBlock), true)
        assertTrue(fixed.all { it.fixed })
        val workout = TestFixtures.setsWorkout()
        assertTrue(WorkoutEditing.canEdit(workout, ownerEditable = null, inProgress = false))
        assertEquals(false, WorkoutEditing.canEdit(workout.copy(isBuiltIn = true), null, false))
        assertEquals(false, WorkoutEditing.canEdit(workout, ownerEditable = false, inProgress = false))
        assertEquals(false, WorkoutEditing.canEdit(workout, ownerEditable = true, inProgress = true))
    }

    @Test
    fun `stary blocksJson bez nowych pol wczytuje sie z domyslnymi`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = """[{"kind":"SETS","items":[{"exerciseId":"pushups","targetKind":"REPS","targetValue":10}]}]"""
        val blocks = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Block.serializer()), old)
        assertEquals("", blocks.single().label)
        assertEquals("", blocks.single().items.single().note)
        assertEquals(null, blocks.single().items.single().windowSec)
    }

    @Test
    fun `czas do godziny, powtorzenia do 999`() {
        assertEquals(WorkoutEditing.MAX_TARGET_SECONDS, WorkoutEditing.sanitize(item("row", value = 99_999, kind = TargetKind.SECONDS)).targetValue)
        assertEquals(2400, WorkoutEditing.sanitize(item("row", value = 2400, kind = TargetKind.SECONDS)).targetValue)
        assertEquals(WorkoutEditing.MAX_TARGET_VALUE, WorkoutEditing.sanitize(item("row", value = 2400)).targetValue)
    }

    @Test
    fun `czas w edytorze idzie po siatce 5 s, 15 s i minut`() {
        fun up(v: Int) = WorkoutEditing.nextTarget(TargetKind.SECONDS, v)
        fun down(v: Int) = WorkoutEditing.previousTarget(TargetKind.SECONDS, v)

        assertEquals(listOf(35, 120, 135, 600, 660), listOf(up(30), up(115), up(120), up(585), up(600)))
        assertEquals(listOf(25, 115, 120, 585, 600), listOf(down(30), down(120), down(135), down(600), down(660)))
        // Wartość spoza siatki (np. z pliku planu) wskakuje na najbliższy punkt w danym kierunku.
        assertEquals(Pair(45, 40), Pair(up(42), down(42)))
        assertEquals(WorkoutEditing.MAX_TARGET_SECONDS, up(WorkoutEditing.MAX_TARGET_SECONDS))
        assertEquals(1, down(5))
        assertEquals(Pair(11, 9), Pair(WorkoutEditing.nextTarget(TargetKind.REPS, 10), WorkoutEditing.previousTarget(TargetKind.REPS, 10)))
    }

    @Test
    fun `ciezar idzie co pol kilo do 10 kg, co kilo do 40 kg, dalej co 2,5 kg`() {
        fun upFrom(start: Double?, times: Int): Double? = (1..times).fold(start) { kg, _ -> WorkoutEditing.nextWeight(kg) }

        // Domowe hantle 1, 3, 4, 6 i 8 kg są osiągalne od zera.
        assertEquals(1.0, upFrom(null, 2)!!, 0.0)
        assertEquals(3.0, upFrom(null, 6)!!, 0.0)
        assertEquals(8.0, upFrom(null, 16)!!, 0.0)
        assertEquals(10.0, WorkoutEditing.nextWeight(9.5), 0.0)
        assertEquals(11.0, WorkoutEditing.nextWeight(10.0), 0.0)
        assertEquals(40.0, WorkoutEditing.nextWeight(39.0), 0.0)
        assertEquals(42.5, WorkoutEditing.nextWeight(40.0), 0.0)
        assertEquals(WorkoutEditing.MAX_WEIGHT_KG, WorkoutEditing.nextWeight(WorkoutEditing.MAX_WEIGHT_KG), 0.0)
    }

    @Test
    fun `ciezar w dol wraca po tej samej siatce, a spod pol kilo schodzi do braku ciezaru`() {
        assertEquals(40.0, WorkoutEditing.previousWeight(42.5)!!, 0.0)
        assertEquals(39.0, WorkoutEditing.previousWeight(40.0)!!, 0.0)
        assertEquals(9.5, WorkoutEditing.previousWeight(10.0)!!, 0.0)
        assertNull(WorkoutEditing.previousWeight(0.5))
        assertNull(WorkoutEditing.previousWeight(null))
        // Wartość spoza siatki (z pliku planu) wskakuje na najbliższy punkt w danym kierunku.
        assertEquals(Pair(23.0, 22.0), Pair(WorkoutEditing.nextWeight(22.5), WorkoutEditing.previousWeight(22.5)))
        assertEquals(Pair(42.5, 40.0), Pair(WorkoutEditing.nextWeight(40.5), WorkoutEditing.previousWeight(40.5)))
    }
}
