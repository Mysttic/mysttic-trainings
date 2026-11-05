package pl.mysttic.trainings.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProgramTest {

    private val zone: ZoneId = ZoneId.of("Europe/Warsaw")
    private val start = LocalDate.of(2026, 9, 1)
    private val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 1 = trening, 2 = odpoczynek, 3 = trening, 4 = odpoczynek, 5 = trening. */
    private val program = Program(
        id = "p1",
        name = "Plan",
        profile = TrainingProfile(),
        startedAt = startMillis,
        cycleDays = 5,
        createdAt = startMillis,
        active = true,
        days = listOf(
            PlanDay(1, "A"), PlanDay(2, null), PlanDay(3, "B"), PlanDay(4, null), PlanDay(5, "A"),
        ),
    )

    @Test
    fun `dzien odpoczynku rozpoznany po dacie`() {
        assertFalse(program.isRestDate(start, zone))
        assertTrue(program.isRestDate(start.plusDays(1), zone))
        assertFalse(program.isRestDate(start.plusDays(2), zone))
        assertTrue(program.isRestDate(start.plusDays(3), zone))
    }

    @Test
    fun `data spoza cyklu nie jest odpoczynkiem`() {
        assertFalse(program.isRestDate(start.minusDays(1), zone))
        assertFalse(program.isRestDate(start.plusDays(5), zone))
        assertFalse(program.isRestDate(start.plusDays(100), zone))
    }

    @Test
    fun `zrodlo planu decyduje o presecie i edycji w miejscu`() {
        val preset = program.copy(profile = TrainingProfile(presetId = "TABATA"), source = PlanSource.PRESET)
        assertTrue(preset.isPreset)
        assertEquals("TABATA", preset.presetId)
        assertFalse(preset.editableInPlace)

        // Kopia presetu jako plan własny: profil może jeszcze nieść presetId, ale plan presetem już nie jest.
        val copy = preset.copy(source = PlanSource.CUSTOM)
        assertFalse(copy.isPreset)
        assertEquals(null, copy.presetId)
        assertTrue(copy.editableInPlace)
        assertFalse(program.copy(source = PlanSource.IMPORTED).editableInPlace)
        // Domyślne źródło z profilu: stare wywołania konstruktora dalej działają.
        assertEquals(PlanSource.WIZARD, program.source)
    }

    @Test
    fun `kotwica rytmu przesuwa numer dnia i odpoczynki`() {
        val resumed = program.copy(rhythmStart = start.plusDays(10).atStartOfDay(zone).toInstant().toEpochMilli())
        val now = start.plusDays(10).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        assertEquals(1, resumed.dayIndexAt(now, zone))
        assertTrue(resumed.isRestDate(start.plusDays(11), zone))
        assertFalse(resumed.isRestDate(start.plusDays(1), zone))
    }

    @Test
    fun `numer dnia liczony kalendarzowo od daty startu`() {
        val now = start.plusDays(3).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()

        assertEquals(4, program.dayIndexAt(now, zone))
        assertFalse(program.isFinished(now, zone))
        assertEquals(2, program.daysLeft(now, zone))
    }
}
