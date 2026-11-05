package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEditingTest {

    private val plan = listOf(PlanDay(1, "p-a"), PlanDay(2, null), PlanDay(3, "p-b"), PlanDay(4, "p-a"))
    private val ids = setOf("p-a", "p-b")

    @Test
    fun `szkielet rozklada treningi po tygodniu i ciagnie rotacje przez caly plan`() {
        val days = PlanEditing.skeleton(weeks = 2, perWeek = 3, rotation = listOf("A", "B"))

        assertEquals(14, days.size)
        assertEquals(listOf("A", null, "B", null, "A", null, null), days.take(7).map { it.workoutId })
        assertEquals(listOf("B", null, "A", null, "B", null, null), days.drop(7).map { it.workoutId })
        assertEquals(14, days.map { it.key }.toSet().size)
    }

    @Test
    fun `numeracja dni gesta od jedynki po przesunieciach`() {
        var days = PlanEditing.fromPlan(plan)
        days = PlanEditing.moveDay(days, 3, 0)
        days = PlanEditing.addDay(days, null)

        assertEquals(listOf(1, 2, 3, 4, 5), PlanEditing.toPlanDays(days).map { it.dayIndex })
        assertEquals(listOf("p-a", "p-a", null, "p-b", null), PlanEditing.toPlanDays(days).map { it.workoutId })
    }

    @Test
    fun `zrobione dni z dziura sa zbiorem, nie prefiksem`() {
        // Zrobione: p-a (dzień 1) i p-b (dzień 3), dzień 2 to odpoczynek, dzień 4 czeka.
        val days = PlanEditing.fromPlan(plan)
        val done = PlanEditing.doneKeys(days, listOf("p-a", "p-b"))
        assertEquals(setOf("d1", "d3"), done)

        // Zmiana przyszłego dnia jest w porządku.
        val changed = PlanEditing.setWorkout(days, 3, "p-b")
        assertTrue(PlanEditing.validate("Plan", changed, ids, done, listOf("p-a", "p-b")).isEmpty())
    }

    @Test
    fun `dzien A wstawiony przed zrobiony dzien A kradnie odhaczenie i zapis jest odrzucony`() {
        val days = PlanEditing.fromPlan(plan)
        val done = PlanEditing.doneKeys(days, listOf("p-a"))
        val stolen = listOf(DraftDay("n9", "p-a")) + days

        val errors = PlanEditing.validate("Plan", stolen, ids, done, listOf("p-a"))
        assertTrue(errors.any { it.startsWith("Zrobione dni") })
    }

    @Test
    fun `odpoczynek przestawiony przed zrobiony dzien przesuwa go i zapis jest odrzucony`() {
        // Dzień 1 (p-a) zrobiony; odpoczynek z dnia 2 idzie w górę, więc p-a ląduje na dniu 2.
        val days = PlanEditing.fromPlan(plan)
        val done = PlanEditing.doneKeys(days, listOf("p-a"))
        val moved = PlanEditing.moveDay(days, 1, 0)

        assertEquals(done, PlanEditing.doneKeys(moved, listOf("p-a")))
        val errors = PlanEditing.validate("Plan", moved, ids, done, listOf("p-a"))
        assertTrue(errors.any { it.startsWith("Zrobione dni") })
    }

    @Test
    fun `usuniecie dnia przed zrobionym dniem jest odrzucone, a po nim przechodzi`() {
        // Zrobione dni 1 i 3; usunięcie odpoczynku z dnia 2 przesuwa dzień 3 na 2.
        val days = PlanEditing.fromPlan(plan)
        val completed = listOf("p-a", "p-b")
        val done = PlanEditing.doneKeys(days, completed)

        val before = PlanEditing.removeDay(days, 1)
        assertTrue(PlanEditing.validate("Plan", before, ids, done, completed).any { it.startsWith("Zrobione dni") })

        val after = PlanEditing.removeDay(days, 3)
        assertTrue(PlanEditing.validate("Plan", after, ids, done, completed).isEmpty())
    }

    @Test
    fun `sesja ponad plan odhacza nowy dzien i nie blokuje wydluzenia planu`() {
        // Dzień 1 = A, dzień 2 odpoczynek, dzień 3 = B; A ukończone dwa razy (drugi raz ze szczegółów treningu).
        val days = PlanEditing.fromPlan(listOf(PlanDay(1, "p-a"), PlanDay(2, null), PlanDay(3, "p-b")))
        val completed = listOf("p-a", "p-a")
        val locked = PlanEditing.doneKeys(days, completed)
        assertEquals(setOf("d1"), locked)

        val week = PlanEditing.appendWeekCopy(days)
        assertTrue(PlanEditing.validate("Plan", week, ids, locked, completed).isEmpty())
        assertEquals(setOf(week[3].key), PlanEditing.surplusKeys(week, locked, completed))

        val added = PlanEditing.addDay(days, "p-a")
        assertTrue(PlanEditing.validate("Plan", added, ids, locked, completed).isEmpty())
    }

    @Test
    fun `regula zrobionych dni dziala tak samo bez zablokowanych dni`() {
        // Sesja treningu planu, którego nie ma w dniach: nowy dzień z nim jest zrobiony od razu, jak przy blokadzie.
        val days = PlanEditing.fromPlan(listOf(PlanDay(1, "p-b")))
        val completed = listOf("p-a")
        val locked = PlanEditing.doneKeys(days, completed)
        assertTrue(locked.isEmpty())

        val added = PlanEditing.addDay(days, "p-a")
        assertTrue(PlanEditing.validate("Plan", added, ids, locked, completed).isEmpty())
        assertEquals(setOf(added[1].key), PlanEditing.surplusKeys(added, locked, completed))
    }

    @Test
    fun `sesja ponad plan nie pozwala wstawic dnia przed zrobiony dzien`() {
        // Nowy dzień A przed zrobionym dniem 1 przesuwa go na dzień 2, choć zostałby odhaczony nadwyżką.
        val days = PlanEditing.fromPlan(listOf(PlanDay(1, "p-a"), PlanDay(2, null)))
        val completed = listOf("p-a", "p-a")
        val locked = PlanEditing.doneKeys(days, completed)
        val before = listOf(DraftDay("n9", "p-a")) + days

        assertTrue(PlanEditing.validate("Plan", before, ids, locked, completed).any { it.startsWith("Zrobione dni") })
    }

    @Test
    fun `trening spoza planu, pusta nazwa i sam odpoczynek blokuja zapis`() {
        val errors = PlanEditing.validate("", listOf(DraftDay("n1", null)), ids, emptySet(), emptyList())
        assertEquals(2, errors.size)
        val foreign = PlanEditing.validate("P", listOf(DraftDay("n1", "full-body-a")), ids, emptySet(), emptyList())
        assertTrue(foreign.single().contains("spoza planu"))
    }

    @Test
    fun `tydzien jak ostatni kopiuje ostatnie 7 dni z nowymi kluczami`() {
        val days = PlanEditing.skeleton(1, 2, listOf("A"))
        val two = PlanEditing.appendWeekCopy(days)
        assertEquals(14, two.size)
        assertEquals(days.map { it.workoutId }, two.drop(7).map { it.workoutId })
        assertEquals(14, two.map { it.key }.toSet().size)
    }

    @Test
    fun `duplikat dnia wstawia kopie tuz za oryginalem`() {
        val days = PlanEditing.duplicateDay(PlanEditing.fromPlan(plan), 0)
        assertEquals(listOf("p-a", "p-a", null, "p-b", "p-a"), days.map { it.workoutId })
    }

    @Test
    fun `kopia treningu z planu dostaje wolna nazwe z dopiskiem kopia`() {
        assertEquals("A: siła (kopia)", PlanEditing.copyName("A: siła", listOf("A: siła", "B: obwód")))
        assertEquals("A: siła (kopia 2)", PlanEditing.copyName("A: siła", listOf("A: siła", "A: siła (kopia)")))
        assertEquals(
            "A: siła (kopia 3)",
            PlanEditing.copyName("A: siła", listOf("A: siła", "A: siła (kopia)", "A: siła (kopia 2)")),
        )
    }

    @Test
    fun `kopia kopii nie doklada drugiego nawiasu`() {
        assertEquals("A (kopia 2)", PlanEditing.copyName("A (kopia)", listOf("A", "A (kopia)")))
        assertEquals("A (kopia 3)", PlanEditing.copyName("A (kopia 2)", listOf("A", "A (kopia)", "A (kopia 2)")))
        // Oryginał mógł zniknąć z planu: kopia kopii bierze pierwszą wolną nazwę od „(kopia)”.
        assertEquals("A (kopia)", PlanEditing.copyName("A (kopia 2)", listOf("A (kopia 2)")))
    }

    @Test
    fun `wolna nazwa zostaje, a wielkosc liter i spacje nie odrozniaja nazw`() {
        assertEquals("Full body A", PlanEditing.copyName("Full body A", listOf("Full body B")))
        assertEquals("Full body A", PlanEditing.copyName("  Full body A ", emptyList()))
        assertEquals("full body a (kopia)", PlanEditing.copyName("full body a", listOf(" Full Body A")))
        assertEquals("Trening (kopia)", PlanEditing.copyName(" ", listOf("Trening")))
    }
}
