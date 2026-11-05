package pl.mysttic.trainings.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Progi układu (D-42) dla telefonu i tabletu w obu orientacjach — te same, które obejrzy przebieg na emulatorze. */
class AppLayoutTest {

    @Test
    fun `telefon w pionie to dolny pasek, jeden panel, dwie kolumny kafelkow`() {
        val phone = AppLayout(411, 891)
        assertFalse(phone.navRail)
        assertFalse(phone.twoPane)
        assertFalse(phone.playerSideBySide)
        assertEquals(2, phone.tileColumns)
    }

    @Test
    fun `telefon w poziomie ma szyne i player obok siebie, ale jeden panel`() {
        val landscape = AppLayout(891, 411)
        assertTrue(landscape.navRail)
        assertFalse(landscape.twoPane)
        assertTrue(landscape.playerSideBySide)
    }

    @Test
    fun `tablet w pionie i w poziomie`() {
        val portrait = AppLayout(800, 1280)
        assertTrue(portrait.navRail)
        assertFalse(portrait.twoPane)
        assertFalse(portrait.playerSideBySide)
        assertEquals(3, portrait.tileColumns)

        val landscape = AppLayout(1280, 800)
        assertTrue(landscape.navRail)
        assertTrue(landscape.twoPane)
        assertTrue(landscape.playerSideBySide)
        assertEquals(4, landscape.tileColumns)
    }

    @Test
    fun `male okno w podzielonym ekranie`() {
        val split = AppLayout(700, 1000)
        assertTrue(split.navRail)
        assertFalse(split.twoPane)
        assertEquals(3, split.tileColumns)
    }
}
