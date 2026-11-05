package pl.mysttic.trainings.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wydanie bazy ćwiczeń na liście wydań strony i decyzja, co z nim zrobić (D-47). */
class DatabaseUpdateTest {

    private val sha = "a".repeat(64)

    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false, assets: String = "[]") =
        """{"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease, "assets": $assets}"""

    private fun asset(name: String, size: Long = 100, state: String = "uploaded") =
        """{"name": "$name", "state": "$state", "size": $size, "browser_download_url": "https://example.invalid/$name"}"""

    private fun files(version: Int, state: String = "uploaded") =
        "[${asset("mysttic-trainings-baza-$version.json", 300, state)}, ${asset("mysttic-trainings-baza-$version.zip", 3_500_000)}]"

    private fun manifest(version: Int = 11, format: Int = 1, minApp: String? = "1.1.0", size: Long = 100) = DatabaseManifest(
        format = format, version = version, minApp = minApp, exercises = 285, added = 2, changed = 1, retired = 0,
        file = DatabaseUpdate.archiveName(version), size = size, sha256 = sha,
    )

    @Test
    fun `wydanie bazy ma wlasny prefiks i nie myli sie z wydaniem aplikacji`() {
        val body = "[" + listOf(
            release("chatt-v2.0.0"),
            release("mysttic-trainings-v1.1.0", assets = "[${asset("mysttic-trainings-1.1.0-android.apk")}]"),
            release("mysttic-trainings-baza-v12", draft = true),
            release("mysttic-trainings-baza-v11", assets = "[${asset("mysttic-trainings-baza-11.json", 300)}, ${asset("mysttic-trainings-baza-11.zip", 3_500_000)}]"),
        ).joinToString() + "]"

        val found = DatabaseUpdate.parseRelease(body)!!

        assertEquals(11, found.version)
        assertEquals("https://example.invalid/mysttic-trainings-baza-11.json", found.manifestUrl)
        assertEquals("https://example.invalid/mysttic-trainings-baza-11.zip", found.archiveUrl)
        assertEquals(3_500_000L, found.archiveSize)
        // Aplikacja i baza nie biorą sobie wydań: prefiks bazy nie zaczyna się od prefiksu aplikacji.
        assertNull(UpdateCheck.versionFromTag("mysttic-trainings-baza-v11"))
        assertNull(DatabaseUpdate.versionFromTag("mysttic-trainings-v1.1.0"))
    }

    @Test
    fun `najwyzszy numer wygrywa niezaleznie od kolejnosci na liscie`() {
        // Przerwany bieg baza.yml zostawia starsze wydanie obok nowego, a lista nie idzie za kolejnością wydań.
        val v9 = release("mysttic-trainings-baza-v9", assets = files(9))
        val v11 = release("mysttic-trainings-baza-v11", assets = files(11))

        assertEquals(11, DatabaseUpdate.parseRelease("[$v9, $v11]")?.version)
        assertEquals(11, DatabaseUpdate.parseRelease("[$v11, $v9]")?.version)
    }

    @Test
    fun `po prefiksie bazy musi stac gola liczba`() {
        assertEquals(10, DatabaseUpdate.versionFromTag("mysttic-trainings-baza-v10"))
        assertNull(DatabaseUpdate.versionFromTag("mysttic-trainings-baza-v+10"))
        assertNull(DatabaseUpdate.versionFromTag("mysttic-trainings-baza-v010"))
        assertNull(DatabaseUpdate.versionFromTag("mysttic-trainings-baza-v0"))
        assertNull(DatabaseUpdate.versionFromTag("mysttic-trainings-baza-v10a"))
    }

    @Test
    fun `pliki, ktore jeszcze sie wgrywaja, to jeszcze nie nowa baza`() {
        assertNull(DatabaseUpdate.parseRelease("[${release("mysttic-trainings-baza-v11", assets = files(11, state = "open"))}]"))
    }

    @Test
    fun `brak wydania bazy albo zly JSON to brak oferty, nie blad`() {
        assertNull(DatabaseUpdate.parseRelease("[${release("mysttic-trainings-v1.0.0")}]"))
        assertNull(DatabaseUpdate.parseRelease("nie json"))
        assertNull(DatabaseUpdate.parseRelease("{}"))
    }

    @Test
    fun `manifest bez sumy albo rozmiaru jest odrzucany`() {
        val dobry = """{"format": 1, "version": 11, "minApp": "1.1.0", "size": 100, "sha256": "$sha", "nowe": "pole"}"""
        assertEquals(11, DatabaseUpdate.parseManifest(dobry)?.version)
        assertNull(DatabaseUpdate.parseManifest("""{"version": 11, "size": 100, "sha256": "xyz"}"""))
        assertNull(DatabaseUpdate.parseManifest("""{"version": 11, "sha256": "$sha"}"""))
        assertNull(DatabaseUpdate.parseManifest("[]"))
    }

    @Test
    fun `decyzja zalezy od numeru, formatu i wersji aplikacji`() {
        val release = DatabaseRelease(11, "m", "z", 100)

        assertEquals(DatabaseOffer.UpToDate, DatabaseUpdate.decide(null, null, 10, "1.1.0"))
        assertEquals(DatabaseOffer.UpToDate, DatabaseUpdate.decide(release, manifest(), 11, "1.1.0"))
        assertEquals(DatabaseOffer.Available(manifest(), "z"), DatabaseUpdate.decide(release, manifest(), 10, "1.1.0"))
        // Build debug ma przyrostek, który nie zmienia porównania wersji.
        assertTrue(DatabaseUpdate.decide(release, manifest(), 10, "1.1.0-debug") is DatabaseOffer.Available)
        assertEquals(DatabaseOffer.NeedsApp(11, "1.2.0"), DatabaseUpdate.decide(release, manifest(minApp = "1.2.0"), 10, "1.1.0"))
        assertEquals(DatabaseOffer.NeedsApp(11, "1.1.0"), DatabaseUpdate.decide(release, manifest(format = 2), 10, "1.1.0"))
        assertEquals(DatabaseOffer.Unreadable(11), DatabaseUpdate.decide(release, null, 10, "1.1.0"))
        assertEquals(DatabaseOffer.Unreadable(11), DatabaseUpdate.decide(release, manifest(version = 12), 10, "1.1.0"))
        assertEquals(DatabaseOffer.Unreadable(11), DatabaseUpdate.decide(release.copy(archiveUrl = null), manifest(), 10, "1.1.0"))
        // Rozmiar w wydaniu i w manifeście muszą się zgadzać, inaczej to nie ten ZIP.
        assertEquals(DatabaseOffer.Unreadable(11), DatabaseUpdate.decide(release, manifest(size = 99), 10, "1.1.0"))
    }

    @Test
    fun `opis zmian odmienia liczby`() {
        assertEquals("2 nowe ćwiczenia, 1 zmienione, bez wycofanych", DatabaseUpdate.changesText(manifest()))
        assertEquals(
            "5 nowych ćwiczeń, 12 zmienionych, 1 wycofane",
            DatabaseUpdate.changesText(manifest().copy(added = 5, changed = 12, retired = 1)),
        )
        assertEquals("", DatabaseUpdate.changesText(manifest().copy(added = null)))
    }
}
