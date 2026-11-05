package pl.mysttic.trainings.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lista wydań publicznego repozytorium strony (D-33). Leżą tam wydania wszystkich projektów Mysttic,
 * więc najtańszy sposób na pomyłkę to wziąć cudze wydanie za swoje — stąd te testy.
 */
class ReleaseParserTest {

    private val page = UpdateCheck.PAGE

    private fun release(
        tag: String = "mysttic-trainings-v0.3.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assetName: String = "mysttic-trainings-0.3.0-android.apk",
        body: String = "Treningi 0.3.0\n\nhttps://mysttic.github.io/Mysttic/mysttic-trainings/",
        state: String = "uploaded",
    ) = """
        {
          "html_url": "https://github.com/Mysttic/Mysttic/releases/tag/$tag",
          "tag_name": "$tag",
          "name": "Treningi 0.3.0",
          "draft": $draft,
          "prerelease": $prerelease,
          "published_at": "2026-09-30T10:00:00Z",
          "body": "${body.replace("\n", "\\n")}",
          "assets": [
            {
              "name": "$assetName",
              "state": "$state",
              "size": 5750986,
              "browser_download_url": "https://github.com/Mysttic/Mysttic/releases/download/$tag/$assetName"
            }
          ]
        }
    """.trimIndent()

    private fun list(vararg releases: String) = releases.joinToString(",", prefix = "[", postfix = "]")

    @Test
    fun `nowsze wydanie z plikiem dla Androida`() {
        val result = ReleaseParser.parse(list(release()), currentVersion = "0.2.0", fallbackPage = page)

        assertTrue(result.isNewer)
        val info = result.release!!
        assertEquals("0.3.0", info.version)
        assertEquals(5_750_986L, info.assetSize)
        assertTrue(info.assetUrl!!.endsWith("mysttic-trainings-0.3.0-android.apk"))
        assertTrue(info.notes.contains("mysttic.github.io"))
        assertEquals(page, info.pageUrl)
    }

    @Test
    fun `wydanie innego projektu nad naszym nic nie zmienia`() {
        // API oddaje wydania od najnowszego; nad naszym może stać cokolwiek z Mysttic/Mysttic.
        val body = list(release(tag = "chatt-v9.9.9", assetName = "chatt-9.9.9-android.apk"), release())
        val result = ReleaseParser.parse(body, currentVersion = "0.2.0", fallbackPage = page)

        assertTrue(result.isNewer)
        assertEquals("0.3.0", result.release!!.version)
    }

    @Test
    fun `lista bez naszego wydania to nic nowego, nie blad`() {
        val body = list(release(tag = "chatt-v9.9.9", assetName = "chatt-9.9.9-android.apk"))
        assertEquals(UpdateOutcome.UP_TO_DATE, ReleaseParser.parse(body, "0.2.0", page).outcome)
        assertEquals(UpdateOutcome.UP_TO_DATE, ReleaseParser.parse("[]", "0.2.0", page).outcome)
    }

    @Test
    fun `ta sama wersja to nic nowego, takze z buildu debugowego`() {
        val body = list(release(tag = "mysttic-trainings-v0.2.0", assetName = "mysttic-trainings-0.2.0-android.apk"))
        assertEquals(UpdateOutcome.UP_TO_DATE, ReleaseParser.parse(body, "0.2.0-debug", page).outcome)
    }

    @Test
    fun `szkic i wydanie kandydujace sa przezroczyste`() {
        // Pomijamy je i szukamy dalej: pod szkicem może leżeć nasze poprzednie, poprawne wydanie.
        val body = list(release(draft = true), release(tag = "mysttic-trainings-v0.2.5", assetName = "mysttic-trainings-0.2.5-android.apk"))
        val result = ReleaseParser.parse(body, "0.2.0", page)

        assertTrue(result.isNewer)
        assertEquals("0.2.5", result.release!!.version)
        assertEquals(
            UpdateOutcome.UP_TO_DATE,
            ReleaseParser.parse(list(release(prerelease = true)), "0.2.0", page).outcome,
        )
    }

    @Test
    fun `najwyzsza wersja wygrywa niezaleznie od kolejnosci na liscie`() {
        // Lista idzie za datą commita, na który wskazuje tag, a przerwane kopiowanie zostawia starsze wydanie.
        val older = release(tag = "mysttic-trainings-v0.9.0", assetName = "mysttic-trainings-0.9.0-android.apk")
        val newer = release(tag = "mysttic-trainings-v0.10.0", assetName = "mysttic-trainings-0.10.0-android.apk")

        assertEquals("0.10.0", ReleaseParser.parse(list(older, newer), "0.2.0", page).release!!.version)
        assertEquals("0.10.0", ReleaseParser.parse(list(newer, older), "0.2.0", page).release!!.version)
    }

    @Test
    fun `po prefiksie musi stac gole X Y Z`() {
        // Przyszły projekt o prefiksie zaczynającym się od naszego nie może uchodzić za nasze wydanie.
        val foreign = release(tag = "mysttic-trainings-viewer-v9.0.0", assetName = "mysttic-trainings-viewer-9.0.0-android.apk")

        assertEquals(UpdateOutcome.UP_TO_DATE, ReleaseParser.parse(list(foreign), "0.2.0", page).outcome)
        assertEquals("0.3.0", ReleaseParser.parse(list(foreign, release()), "0.2.0", page).release!!.version)
    }

    @Test
    fun `plik, ktory jeszcze sie wgrywa, to jeszcze nie aktualizacja`() {
        val result = ReleaseParser.parse(list(release(state = "open")), "0.2.0", page)

        assertEquals(UpdateOutcome.UP_TO_DATE, result.outcome)
        assertNull(result.release)
    }

    @Test
    fun `wydanie bez pliku dla Androida daje wersje bez adresu pobrania`() {
        val body = list(release(assetName = "mysttic-trainings-0.3.0-windows.zip"))
        val result = ReleaseParser.parse(body, "0.2.0", page)

        assertTrue(result.isNewer)
        assertNull(result.release!!.assetUrl)
        assertEquals(0L, result.release!!.assetSize)
        assertEquals(page, result.release!!.pageUrl)
    }

    @Test
    fun `smieci zamiast listy to blad, nie cisza`() {
        assertEquals(UpdateOutcome.FAILED, ReleaseParser.parse("nie jest jsonem", "0.2.0", page).outcome)
        assertEquals(UpdateOutcome.FAILED, ReleaseParser.parse("{}", "0.2.0", page).outcome)
        assertEquals(UpdateOutcome.FAILED, ReleaseParser.parse("", "0.2.0", page).outcome)
    }
}
