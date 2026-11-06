package pl.mysttic.trainings.data.update

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Rozpakowanie ZIP-a bazy ćwiczeń (D-47) z limitami. Wpuszcza wyłącznie `exercises.json` i `images/<id>.jpg`,
 * a od D-51 także zdjęcia tła: `tla.json` i `tla/<id>.jpg`. Ścieżka wychodząca poza katalog (`../`), obcy plik,
 * powtórzony wpis i zip-bomba kończą się odmową, zanim cokolwiek trafi do Room. Bez Androida, więc sprawdza to
 * zwykły test jednostkowy.
 */
object DatabaseArchive {

    const val JSON = "exercises.json"
    const val PHOTOS_JSON = "tla.json"
    const val MAX_ENTRIES = 2_000
    const val MAX_JSON_BYTES = 5_000_000L
    const val MAX_IMAGE_BYTES = 1_000_000L
    const val MAX_TOTAL_BYTES = 60_000_000L
    private val IMAGE = Regex("images/[a-z0-9]+(-[a-z0-9]+)*\\.jpg")
    private val PHOTO = Regex("tla/[a-z0-9]+(-[a-z0-9]+)*\\.jpg")

    /** Rozpakowuje [input] do pustego albo nieistniejącego [target]. Odmowa = [IllegalArgumentException] z powodem. */
    fun extract(input: InputStream, target: File) {
        require(!target.exists() || target.listFiles().isNullOrEmpty()) { "katalog docelowy nie jest pusty" }
        target.mkdirs()
        val seen = HashSet<String>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory) {
                    require(name == "images/" || name == "tla/") { "nieoczekiwany katalog ${name.take(60)}" }
                    continue
                }
                val limit = when {
                    name == JSON || name == PHOTOS_JSON -> MAX_JSON_BYTES
                    IMAGE.matches(name) || PHOTO.matches(name) -> MAX_IMAGE_BYTES
                    else -> throw IllegalArgumentException("nieoczekiwany plik ${name.take(60)}")
                }
                require(seen.add(name)) { "plik $name powtórzony" }
                require(seen.size <= MAX_ENTRIES) { "za dużo plików w paczce" }
                val out = File(target, name)
                out.parentFile?.mkdirs()
                var written = 0L
                out.outputStream().use { sink ->
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        written += read
                        total += read
                        require(written <= limit) { "plik $name jest za duży" }
                        require(total <= MAX_TOTAL_BYTES) { "rozpakowana baza jest za duża" }
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }
        require(JSON in seen) { "w paczce nie ma $JSON" }
    }
}
