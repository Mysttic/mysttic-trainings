package pl.mysttic.trainings.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Rozpakowanie paczki bazy (D-47): tylko `exercises.json` i `images/<id>.jpg`, w limitach, bez wyjścia z katalogu. */
class DatabaseArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            for ((name, content) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(content)
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun target(): File = File(temp.root, "cel")

    /** Każda próba w świeżym katalogu: odrzucona paczka zostawia to, co zdążyła rozpakować (sprząta to repozytorium). */
    private fun rejected(archive: ByteArray, fragment: String, into: File = temp.newFolder()) {
        try {
            DatabaseArchive.extract(ByteArrayInputStream(archive), into)
            fail("paczka przeszła, a miała zostać odrzucona ($fragment)")
        } catch (e: IllegalArgumentException) {
            assertTrue("komunikat: ${e.message}", e.message.orEmpty().contains(fragment))
        }
    }

    @Test
    fun `poprawna paczka trafia do katalogu`() {
        val archive = zip("exercises.json" to "{}".toByteArray(), "images/" to ByteArray(0), "images/pushups.jpg" to byteArrayOf(1, 2, 3))

        DatabaseArchive.extract(ByteArrayInputStream(archive), target())

        assertEquals("{}", File(target(), "exercises.json").readText())
        assertEquals(3L, File(target(), "images/pushups.jpg").length())
    }

    @Test
    fun `sciezka poza katalog i obce pliki sa odrzucane`() {
        val into = temp.newFolder("zamkniety")
        rejected(zip("exercises.json" to "{}".toByteArray(), "../ucieczka.jpg" to byteArrayOf(1)), "nieoczekiwany plik", into)
        assertFalse(File(temp.root, "ucieczka.jpg").exists())
        rejected(zip("exercises.json" to "{}".toByteArray(), "images/../../x.jpg" to byteArrayOf(1)), "nieoczekiwany plik")
        rejected(zip("exercises.json" to "{}".toByteArray(), "skrypt.sh" to byteArrayOf(1)), "nieoczekiwany plik")
        rejected(zip("exercises.json" to "{}".toByteArray(), "images/Duze.jpg" to byteArrayOf(1)), "nieoczekiwany plik")
    }

    @Test
    fun `brak pliku bazy, powtorzony wpis i za duzy obraz`() {
        rejected(zip("images/pushups.jpg" to byteArrayOf(1)), "nie ma exercises.json")
        // ZipOutputStream nie zapisze dwa razy tej samej nazwy, więc druga dostaje nazwę tej samej długości,
        // a bajty nazwy podmieniamy w gotowym ZIP-ie: czytnik idzie po nagłówkach lokalnych i widzi powtórkę.
        val twice = zip("exercises.json" to "{}".toByteArray(), "exercises.jsoX" to "{}".toByteArray())
        val from = "exercises.jsoX".toByteArray()
        val to = "exercises.json".toByteArray()
        for (i in 0..twice.size - from.size) {
            if (from.indices.all { twice[i + it] == from[it] }) to.copyInto(twice, i)
        }
        rejected(twice, "powtórzony")
        val big = ByteArray((DatabaseArchive.MAX_IMAGE_BYTES + 1).toInt())
        rejected(zip("exercises.json" to "{}".toByteArray(), "images/pushups.jpg" to big), "za duży")
    }

    @Test
    fun `katalog docelowy musi byc pusty`() {
        target().mkdirs()
        File(target(), "stary.txt").writeText("x")
        rejected(zip("exercises.json" to "{}".toByteArray()), "nie jest pusty", into = target())
    }
}
