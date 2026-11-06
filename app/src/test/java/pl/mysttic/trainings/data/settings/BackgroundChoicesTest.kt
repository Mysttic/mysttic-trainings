package pl.mysttic.trainings.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tło w każdym motywie (D-50) leży w DataStore jako zbiór wpisów „motyw=tło”. Zbiór przychodzi też z kopii
 * zapasowej, więc odczyt nie może się wywrócić na śmieciu, a zapis nie może zrobić wpisu, którego potem nie odczyta.
 */
class BackgroundChoicesTest {

    @Test
    fun `wybor wraca z zapisu`() {
        val wybor = mapOf("fire" to "zar", "steel" to "granat", "paper" to "jednolite")
        assertEquals(wybor, BackgroundChoices.decode(BackgroundChoices.encode(wybor)))
    }

    @Test
    fun `wpis bez motywu albo bez tla pomijamy`() {
        val odczyt = BackgroundChoices.decode(setOf("fire=zar", "=luna", "steel=", "paper", "", "x".repeat(40) + "=zar"))
        assertEquals(mapOf("fire" to "zar"), odczyt)
    }

    @Test
    fun `tlo z drugim znakiem rownosci nie wchodzi`() {
        // Zapis zrobiłby z niego wpis, który odczyt podzieliłby inaczej; lepiej go nie zapisać wcale.
        assertTrue(BackgroundChoices.encode(mapOf("fire" to "a=b", "st=eel" to "granat")).isEmpty())
        assertTrue(BackgroundChoices.decode(setOf("fire=a=b")).isEmpty())
    }

    @Test
    fun `nowy wybor motywu zastepuje stary, inne motywy zostaja`() {
        val przed = BackgroundChoices.decode(setOf("fire=zar", "steel=granat"))
        val po = BackgroundChoices.decode(BackgroundChoices.encode(przed + ("fire" to "luna")))
        assertEquals(mapOf("fire" to "luna", "steel" to "granat"), po)
    }
}
