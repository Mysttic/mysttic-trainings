package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.coach.ExerciseTuning
import pl.mysttic.trainings.domain.engine.TestFixtures
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.BlockItem
import pl.mysttic.trainings.domain.model.BlockKind
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.SetLog
import pl.mysttic.trainings.domain.model.TargetKind

class SetProgressionTest {

    private val exercises = TestFixtures.exercises

    private fun item(id: String, target: Int, sets: Int = 3, kind: TargetKind = TargetKind.REPS) =
        BlockItem(exerciseId = id, targetKind = kind, targetValue = target, sets = sets)

    private fun log(
        exerciseId: String,
        target: Int,
        actual: Int,
        blockIndex: Int = 0,
        itemIndex: Int = 0,
        setIndex: Int = 1,
        round: Int = 1,
        kind: TargetKind = TargetKind.REPS,
    ) = SetLog(
        sessionId = "s", exerciseId = exerciseId, exerciseName = exerciseId,
        blockIndex = blockIndex, itemIndex = itemIndex, round = round, setIndex = setIndex,
        targetKind = kind, targetValue = target, actualValue = actual, completedAt = 0L,
    )

    private fun setsBlock(vararg items: BlockItem) = listOf(Block(kind = BlockKind.SETS, items = items.toList()))

    @Test
    fun `wszystkie serie na cel dokladaja powtorzenie`() {
        val blocks = setsBlock(item("pushups", 10))
        val logs = (1..3).map { log("pushups", 10, 10, setIndex = it) }

        val suggestions = SetProgression.suggest(blocks, logs, exercises)

        assertEquals(1, suggestions.size)
        val s = suggestions.single()
        assertEquals(10, s.from)
        assertEquals(11, s.to)
        assertEquals("Pompki", s.exerciseName)
        assertEquals(false, s.isSwap)
        assertEquals("Pompki: 10 → 11 powt.", s.describe())
    }

    @Test
    fun `staly blok i AMRAP nie dostaja propozycji`() {
        val fixed = listOf(Block(kind = BlockKind.SETS, fixed = true, items = listOf(item("pushups", 10))))
        val logs = (1..3).map { log("pushups", 10, 10, setIndex = it) }
        assertTrue(SetProgression.suggest(fixed, logs, exercises).isEmpty())

        val amrap = setsBlock(item("pushups", 60, kind = TargetKind.AMRAP))
        val amrapLogs = (1..3).map { log("pushups", 60, 80, setIndex = it, kind = TargetKind.AMRAP) }
        assertTrue(SetProgression.suggest(amrap, amrapLogs, exercises).isEmpty())
    }

    @Test
    fun `jedna seria ponizej celu blokuje propozycje`() {
        val blocks = setsBlock(item("pushups", 10))
        val logs = listOf(log("pushups", 10, 10), log("pushups", 10, 9, setIndex = 2), log("pushups", 10, 10, setIndex = 3))

        assertTrue(SetProgression.suggest(blocks, logs, exercises).isEmpty())
    }

    @Test
    fun `niepelny komplet serii blokuje propozycje`() {
        val blocks = setsBlock(item("pushups", 10, sets = 3))
        val logs = (1..2).map { log("pushups", 10, 10, setIndex = it) }

        assertTrue(SetProgression.suggest(blocks, logs, exercises).isEmpty())
    }

    @Test
    fun `cwiczenie na czas dostaje piec sekund`() {
        val blocks = setsBlock(item("plank", 40, sets = 2, kind = TargetKind.SECONDS))
        val logs = (1..2).map { log("plank", 40, 40, setIndex = it, kind = TargetKind.SECONDS) }

        val s = SetProgression.suggest(blocks, logs, exercises).single()

        assertEquals(40, s.from)
        assertEquals(45, s.to)
        assertEquals("Deska: 40 → 45 s", s.describe())
    }

    @Test
    fun `po dobiciu limitu proponujemy trudniejszy wariant`() {
        val blocks = setsBlock(item("pushups", SetProgression.REPS_LIMIT))
        val logs = (1..3).map { log("pushups", SetProgression.REPS_LIMIT, SetProgression.REPS_LIMIT, setIndex = it) }

        val s = SetProgression.suggest(blocks, logs, exercises).single()

        assertTrue(s.isSwap)
        assertEquals("decline-pushups", s.swapToExerciseId)
        assertEquals("Pompki z nogami wyżej", s.swapToName)
        assertEquals(12, s.to)
        assertTrue(s.describe().contains("→ Pompki z nogami wyżej"))
    }

    @Test
    fun `trudniejszy wariant na brakujacy sprzet nie jest propozycja`() {
        // Wariant wymaga ławki: bez niej podmiana wpisałaby do szablonu ćwiczenie, którego nie da się zrobić.
        val benchPushups = TestFixtures.exercise(
            "bench-pushups", "Pompki na ławce",
            level = Difficulty.ADVANCED, equipment = setOf(Equipment.BENCH), easierId = "pushups",
        )
        val withBench = exercises + mapOf(
            "pushups" to TestFixtures.pushups.copy(harderId = "bench-pushups"),
            "bench-pushups" to benchPushups,
        )
        val blocks = setsBlock(item("pushups", SetProgression.REPS_LIMIT))
        val logs = (1..3).map { log("pushups", SetProgression.REPS_LIMIT, SetProgression.REPS_LIMIT, setIndex = it) }

        assertTrue(SetProgression.suggest(blocks, logs, withBench).isEmpty())

        val s = SetProgression.suggest(blocks, logs, withBench, owned = setOf(Equipment.BENCH)).single()
        assertEquals("bench-pushups", s.swapToExerciseId)
    }

    @Test
    fun `limit bez trudniejszego wariantu nie daje propozycji`() {
        // decline-pushups nie ma harderId w fixtures
        val blocks = setsBlock(item("decline-pushups", SetProgression.REPS_LIMIT))
        val logs = (1..3).map { log("decline-pushups", SetProgression.REPS_LIMIT, SetProgression.REPS_LIMIT, setIndex = it) }

        assertTrue(SetProgression.suggest(blocks, logs, exercises).isEmpty())
    }

    @Test
    fun `w obwodzie komplet to liczba rund`() {
        val blocks = listOf(
            Block(kind = BlockKind.CIRCUIT, rounds = 4, items = listOf(item("squats", 12, sets = 1))),
        )
        val tooFew = (1..3).map { log("squats", 12, 12, round = it) }
        val full = (1..4).map { log("squats", 12, 12, round = it) }

        assertTrue(SetProgression.suggest(blocks, tooFew, exercises).isEmpty())
        assertEquals(13, SetProgression.suggest(blocks, full, exercises).single().to)
    }

    @Test
    fun `propozycje sa liczone osobno dla kazdej pozycji`() {
        val blocks = setsBlock(item("pushups", 10), item("squats", 12))
        val logs = (1..3).map { log("pushups", 10, 10, itemIndex = 0, setIndex = it) } +
            (1..3).map { log("squats", 12, 11, itemIndex = 1, setIndex = it) }

        val suggestions = SetProgression.suggest(blocks, logs, exercises)

        assertEquals(1, suggestions.size)
        assertEquals("pushups", suggestions.single().exerciseId)
    }

    @Test
    fun `zastosowanie propozycji zmienia cel i podmienia cwiczenie`() {
        val blocks = setsBlock(item("pushups", 10), item("plank", 40, kind = TargetKind.SECONDS))
        val suggestions = listOf(
            TargetSuggestion(0, 0, "pushups", "Pompki", TargetKind.REPS, from = 10, to = 11),
            TargetSuggestion(
                0, 1, "plank", "Deska", TargetKind.SECONDS, from = 90, to = 20,
                swapToExerciseId = "climbers", swapToName = "Wspinaczka",
            ),
        )

        val updated = SetProgression.apply(blocks, suggestions)

        assertEquals(11, updated[0].items[0].targetValue)
        assertEquals("pushups", updated[0].items[0].exerciseId)
        assertEquals(20, updated[0].items[1].targetValue)
        assertEquals("climbers", updated[0].items[1].exerciseId)
    }

    @Test
    fun `brak historii i puste propozycje nic nie zmieniaja`() {
        val blocks = setsBlock(item("pushups", 10))

        assertTrue(SetProgression.suggest(blocks, emptyList(), exercises).isEmpty())
        assertEquals(blocks, SetProgression.apply(blocks, emptyList()))
        assertNull(SetProgression.suggest(blocks, emptyList(), exercises).firstOrNull())
    }

    @Test
    fun `po zastosowaniu propozycji nie podpowiadamy jej drugi raz`() {
        val blocks = setsBlock(item("pushups", 10))
        val logs = (1..3).map { log("pushups", 10, 10, setIndex = it) }

        val applied = SetProgression.apply(blocks, SetProgression.suggest(blocks, logs, exercises))

        assertEquals(11, applied[0].items[0].targetValue)
        // te same logi, ale szablon ma już wyższy cel: bez nowej sesji nie ma nowej propozycji
        assertTrue(SetProgression.suggest(applied, logs, exercises).isEmpty())
    }

    @Test
    fun `podmienione cwiczenie nie dziedziczy historii poprzedniego`() {
        val blocks = setsBlock(item("decline-pushups", 10))
        val logs = (1..3).map { log("pushups", 10, 10, setIndex = it) }

        assertTrue(SetProgression.suggest(blocks, logs, exercises).isEmpty())
    }

    @Test
    fun `u poczatkujacego krok liczy sie na wartosciach z ekranu`() {
        // szablon 40 s → 30 s po skalowaniu; samo +5 s w szablonie zaokrągliłoby się z powrotem do 30 s
        val blocks = setsBlock(item("plank", 40, sets = 2, kind = TargetKind.SECONDS))
        val logs = (1..2).map { log("plank", 30, 30, setIndex = it, kind = TargetKind.SECONDS) }

        val s = SetProgression.suggest(blocks, logs, exercises, Difficulty.BEGINNER).single()

        assertEquals(30, s.from)
        assertEquals(35, s.to)
        assertEquals(50, s.rawTo)
        assertEquals(50, SetProgression.apply(blocks, listOf(s))[0].items[0].targetValue)
    }

    @Test
    fun `powtorzenia u poczatkujacego tez rosna o widoczne jedno`() {
        val blocks = setsBlock(item("pushups", 10))
        // Na poziomie początkującym player pokazuje łatwiejszy wariant, więc i log jest z niego.
        val logs = (1..3).map { log("knee-pushups", 7, 7, setIndex = it) }

        val s = SetProgression.suggest(blocks, logs, exercises, Difficulty.BEGINNER).single()

        assertEquals("knee-pushups", s.exerciseId)
        assertEquals(7, s.from)
        assertEquals(8, s.to)
        assertEquals(11, s.rawTo)
    }

    @Test
    fun `trening zrobiony na innym poziomie nie daje propozycji`() {
        val blocks = setsBlock(item("pushups", 10))
        val logs = (1..3).map { log("pushups", 10, 10, setIndex = it) }

        assertTrue(SetProgression.suggest(blocks, logs, exercises, Difficulty.BEGINNER).isEmpty())
    }

    // --- progresja liczona od celu pokazanego: poziom + korekta trenera -------------------------------

    private fun tuning(id: String, reps: Int = 0, seconds: Int = 0) =
        mapOf(id to ExerciseTuning(id, repsDelta = reps, secondsDelta = seconds))

    @Test
    fun `korekta trenera w dol liczy progresje od celu pokazanego`() {
        val blocks = setsBlock(item("pushups", 10))
        val logs = (1..3).map { log("pushups", 8, 8, setIndex = it) }

        val s = SetProgression.suggest(blocks, logs, exercises, tuning = tuning("pushups", reps = -2)).single()

        assertEquals(8, s.from)
        assertEquals(9, s.to)
        assertEquals(11, s.rawTo)
        assertEquals("Pompki: 8 → 9 powt.", s.describe())
    }

    @Test
    fun `korekta trenera w gore nie blokuje karty`() {
        val blocks = setsBlock(item("pushups", 10))
        val logs = (1..3).map { log("pushups", 12, 12, setIndex = it) }

        val s = SetProgression.suggest(blocks, logs, exercises, tuning = tuning("pushups", reps = 2)).single()

        assertEquals(12, s.from)
        assertEquals(13, s.to)
    }

    @Test
    fun `sesja zrobiona przy innej korekcie nie daje propozycji`() {
        val blocks = setsBlock(item("pushups", 10))
        val logs = (1..3).map { log("pushups", 10, 10, setIndex = it) }

        assertTrue(SetProgression.suggest(blocks, logs, exercises, tuning = tuning("pushups", reps = -2)).isEmpty())
    }

    @Test
    fun `cel na podlodze korekty nie daje propozycji`() {
        val blocks = setsBlock(item("pushups", 10))
        val logs = (1..3).map { log("pushups", 1, 1, setIndex = it) }

        assertTrue(SetProgression.suggest(blocks, logs, exercises, tuning = tuning("pushups", reps = -20)).isEmpty())
    }

    @Test
    fun `podmiana wariantu pokazuje cel nowego cwiczenia, bez korekty starego`() {
        val blocks = setsBlock(item("pushups", 18))
        val logs = (1..3).map { log("pushups", 20, 20, setIndex = it) }

        val s = SetProgression.suggest(blocks, logs, exercises, tuning = tuning("pushups", reps = 2)).single()

        assertEquals("decline-pushups", s.swapToExerciseId)
        assertEquals(12, s.to) // wartość domyślna nowego ćwiczenia, korekta pompek na nie nie przechodzi
    }

    @Test
    fun `trudniejszy wariant na brakujacy sprzet nie jest proponowany`() {
        val blocks = setsBlock(item("leg-raises", 20))
        val logs = (1..3).map { log("leg-raises", 20, 20, setIndex = it) }

        assertTrue(SetProgression.suggest(blocks, logs, exercises).isEmpty())

        val s = SetProgression.suggest(blocks, logs, exercises, owned = setOf(Equipment.PULL_UP_BAR)).single()
        assertEquals("hanging-leg-raises", s.swapToExerciseId)
    }

    @Test
    fun `u poczatkujacego podmiana na cwiczenie z szablonu nie jest propozycja`() {
        // 29 powtórzeń pompek u początkującego to pompki na kolanach × 20 (limit). Trudniejsza wersja pompek na kolanach
        // to pompki z szablonu, więc po „Zastosuj” player dalej pokazałby pompki na kolanach, tylko z niższym celem.
        val blocks = setsBlock(item("pushups", 29))
        val logs = (1..3).map { log("knee-pushups", SetProgression.REPS_LIMIT, SetProgression.REPS_LIMIT, setIndex = it) }

        assertTrue(SetProgression.suggest(blocks, logs, exercises, Difficulty.BEGINNER).isEmpty())
    }

    @Test
    fun `staly protokol nie dostaje propozycji na zadnym poziomie`() {
        val blocks = listOf(Block(kind = BlockKind.SETS, fixed = true, items = listOf(item("pushups", 10))))
        val logs = (1..3).map { log("pushups", 10, 10, setIndex = it) }

        for (level in Difficulty.entries) {
            assertTrue(level.name, SetProgression.suggest(blocks, logs, exercises, level).isEmpty())
        }
    }

    @Test
    fun `naglowek odmienia sie po polsku`() {
        fun headline(n: Int) = SetProgression.headline(List(n) { TargetSuggestion(0, it, "x$it", "X", TargetKind.REPS, 1, 2) })

        assertTrue(headline(1).startsWith("1 ćwiczenie poszło"))
        assertTrue(headline(3).startsWith("3 ćwiczenia poszły"))
        assertTrue(headline(5).startsWith("5 ćwiczeń poszło"))
    }

    @Test
    fun `karta liczy cwiczenia, nie pozycje zduplikowanego bloku, i konczy sie jedna kropka`() {
        val item = listOf(item("pushups", 10), item("squats", 10))
        val blocks = listOf(Block(kind = BlockKind.SETS, items = item), Block(kind = BlockKind.SETS, items = item))
        val logs = (0..1).flatMap { block ->
            listOf("pushups", "squats").flatMapIndexed { index, id ->
                (1..3).map { set -> log(id, 10, 10, blockIndex = block, itemIndex = index, setIndex = set) }
            }
        }

        val suggestions = SetProgression.suggest(blocks, logs, exercises)

        // Obie kopie bloku dostają propozycję, bo „Zastosuj” podbija każdą pozycję szablonu.
        assertEquals(4, suggestions.size)
        assertEquals(
            "2 ćwiczenia poszły na cel za każdym razem: Pompki: 10 → 11 powt., Przysiady: 10 → 11 powt.",
            SetProgression.cardText(suggestions),
        )
    }

    @Test
    fun `karta dopisuje kropke po sekundach i jest pusta bez propozycji`() {
        val plank = TargetSuggestion(0, 0, "plank", "Deska", TargetKind.SECONDS, 40, 45)

        assertEquals("1 ćwiczenie poszło na cel za każdym razem: Deska: 40 → 45 s.", SetProgression.cardText(listOf(plank)))
        assertEquals("", SetProgression.cardText(emptyList()))
    }
}
