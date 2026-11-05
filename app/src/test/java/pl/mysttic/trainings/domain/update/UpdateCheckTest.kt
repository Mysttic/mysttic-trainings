package pl.mysttic.trainings.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun `porownanie numerow idzie po trzech czlonach`() {
        assertTrue(UpdateCheck.compareVersions("0.1.0", "0.2.0") < 0)
        assertTrue(UpdateCheck.compareVersions("0.10.0", "0.9.0") > 0)
        assertTrue(UpdateCheck.compareVersions("1.0.0", "0.99.99") > 0)
        assertEquals(0, UpdateCheck.compareVersions("1.2.3", "1.2.3"))
    }

    @Test
    fun `przyrostki buildu i litera v nie zmieniaja wyniku`() {
        // versionName debugowego builda to np. „0.1.0-debug”
        assertEquals(0, UpdateCheck.compareVersions("0.1.0-debug", "v0.1.0"))
        assertEquals(0, UpdateCheck.compareVersions("0.1.0+7", "0.1.0"))
        assertTrue(UpdateCheck.compareVersions("0.1.0-debug", "0.2.0") < 0)
    }

    @Test
    fun `krotszy numer domyka sie zerami`() {
        assertEquals(0, UpdateCheck.compareVersions("1.2", "1.2.0"))
        assertTrue(UpdateCheck.compareVersions("1", "1.0.1") < 0)
    }

    @Test
    fun `nowsze wydanie to jedyny powod, zeby cos proponowac`() {
        assertEquals(UpdateOutcome.NEWER, UpdateCheck.decide("0.1.0", "mysttic-trainings-v0.2.0", draft = false, prerelease = false))
        assertEquals(UpdateOutcome.UP_TO_DATE, UpdateCheck.decide("0.2.0", "mysttic-trainings-v0.2.0", draft = false, prerelease = false))
        assertEquals(UpdateOutcome.UP_TO_DATE, UpdateCheck.decide("0.1.0", "mysttic-trainings-v0.9.0", draft = true, prerelease = false))
        assertEquals(UpdateOutcome.UP_TO_DATE, UpdateCheck.decide("0.1.0", "mysttic-trainings-v0.9.0", draft = false, prerelease = true))
    }

    @Test
    fun `brak lub bezsensowny tag to blad, nie cisza`() {
        assertEquals(UpdateOutcome.FAILED, UpdateCheck.decide("0.1.0", null, draft = false, prerelease = false))
        assertEquals(UpdateOutcome.FAILED, UpdateCheck.decide("0.1.0", "  ", draft = false, prerelease = false))
        assertEquals(UpdateOutcome.FAILED, UpdateCheck.decide("0.1.0", "wersja-testowa", draft = false, prerelease = false))
    }

    @Test
    fun `tag bez naszego prefiksu nie jest naszym wydaniem`() {
        // Na stronie leżą wydania wszystkich projektów; „chatt-v9.9.9” nie ma nas obchodzić.
        assertNull(UpdateCheck.versionFromTag("chatt-v9.9.9"))
        assertNull(UpdateCheck.versionFromTag("v0.3.0"))
        assertNull(UpdateCheck.versionFromTag("mysttic-trainings-v"))
        assertEquals("0.3.0", UpdateCheck.versionFromTag("mysttic-trainings-v0.3.0"))
        // Po prefiksie tylko gołe X.Y.Z (umowa strony), inaczej cudzy prefiks zaczynający się od naszego przejdzie.
        assertNull(UpdateCheck.versionFromTag("mysttic-trainings-viewer-v1.0.0"))
        assertNull(UpdateCheck.versionFromTag("mysttic-trainings-v1.0"))
        assertNull(UpdateCheck.versionFromTag("mysttic-trainings-v1.0.0-rc1"))
        assertEquals(UpdateOutcome.FAILED, UpdateCheck.decide("0.1.0", "chatt-v9.9.9", draft = false, prerelease = false))
    }

    @Test
    fun `nazwa pliku zgadza sie z ta, ktora tworzy workflow wydania`() {
        assertEquals("mysttic-trainings-0.2.0-android.apk", UpdateCheck.assetNameFor("0.2.0"))
        assertEquals("mysttic-trainings-0.2.0-android.apk", UpdateCheck.assetNameFor("v0.2.0"))
    }

    @Test
    fun `rozmiar pliku po ludzku`() {
        assertEquals("12,4 MB", UpdateCheck.formatSize(12_400_000))
        assertEquals("640 kB", UpdateCheck.formatSize(640_000))
        assertEquals("", UpdateCheck.formatSize(0))
    }
}
