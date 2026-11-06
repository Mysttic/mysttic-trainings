package pl.mysttic.trainings.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment

/**
 * Kopia zapasowa ma przeżyć zmiany w aplikacji (D-18): plik zapisany starszą wersją musi się wczytać,
 * a nowe pola mają mieć wartości domyślne. Ten test pilnuje obu kierunków — czyta plik w wersji 1
 * (bez cykli i bez połowy ustawień) i sprawdza, że nic nie wybucha, oraz że dzisiejszy plik wraca w całości.
 */
class BackupFormatTest {

    /** Ta sama konfiguracja co w aplikacji (`AppModule.provideJson`). */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun `plik w wersji 1 wczytuje sie z domyslnymi wartosciami nowych pol`() {
        val stary = """
            {
              "version": 1,
              "app": "mysttic-trainings",
              "exportedAt": 1757030400000,
              "settings": {
                "difficulty": "BEGINNER",
                "ownedEquipment": ["DUMBBELLS"],
                "countdownSec": 3,
                "soundEnabled": true,
                "ttsEnabled": false,
                "vibrationEnabled": true,
                "keepScreenOn": true
              },
              "exercises": [],
              "workouts": [],
              "sessions": [],
              "setLogs": []
            }
        """.trimIndent()

        val plik = json.decodeFromString(BackupFile.serializer(), stary)
        val ustawienia = requireNotNull(plik.settings)

        assertEquals(Difficulty.BEGINNER, ustawienia.difficulty)
        assertEquals(listOf(Equipment.DUMBBELLS), ustawienia.ownedEquipment)
        assertEquals(3, ustawienia.countdownSec)
        // Pola dodane po wersji 1: przypomnienie, klawisz głośności, Health Connect, wydania, biblioteka, wygląd.
        assertEquals(false, ustawienia.reminderEnabled)
        assertEquals(18, ustawienia.reminderHour)
        assertEquals(true, ustawienia.volumeKeyDone)
        assertEquals(false, ustawienia.healthConnectEnabled)
        assertEquals(false, ustawienia.updateChecks)
        assertEquals(true, ustawienia.libraryTiles)
        assertEquals("fire", ustawienia.themeId)
        assertTrue(ustawienia.backgrounds.isEmpty())
        // Cykli w wersji 1 nie było — pusta lista, nie wyjątek.
        assertTrue(plik.programs.isEmpty())
        assertTrue(plik.tuning.isEmpty())
    }

    @Test
    fun `dzisiejszy plik wraca w calosci`() {
        val plik = BackupFile(
            exportedAt = 1_757_030_400_000,
            settings = BackupSettings(
                difficulty = Difficulty.ADVANCED,
                ownedEquipment = listOf(Equipment.KETTLEBELL, Equipment.PULL_UP_BAR),
                reminderEnabled = true,
                reminderHour = 7,
                reminderMinute = 30,
                volumeKeyDone = false,
                healthConnectEnabled = true,
                updateChecks = true,
                libraryTiles = false,
                themeId = "steel",
                backgrounds = mapOf("steel" to "granat", "fire" to "zar"),
            ),
            programs = listOf(
                BackupProgram(
                    id = "plan-abc123",
                    name = "Kondycja, 14 dni",
                    profileJson = """{"heightCm":180,"weightKg":78.0}""",
                    startedAt = 1_757_000_000_000,
                    cycleDays = 14,
                    createdAt = 1_757_000_000_000,
                    active = true,
                    days = listOf(
                        BackupProgramDay(dayIndex = 0, workoutId = "plan-abc123-a"),
                        BackupProgramDay(dayIndex = 1, workoutId = null),
                    ),
                ),
            ),
        )

        val odczytany = json.decodeFromString(BackupFile.serializer(), json.encodeToString(BackupFile.serializer(), plik))

        assertEquals(BackupFile.VERSION, odczytany.version)
        assertEquals(plik.settings, odczytany.settings)
        assertEquals(plik.programs, odczytany.programs)
        assertEquals("steel", odczytany.settings?.themeId)
        assertEquals(mapOf("steel" to "granat", "fire" to "zar"), odczytany.settings?.backgrounds)
        assertEquals(1, odczytany.programs.single().days.count { it.workoutId != null })
    }

    @Test
    fun `wersja pliku rosnie razem z tym, co do niego wkladamy`() {
        // Zmiana formatu bez podbicia VERSION sprawia, że starsza aplikacja wczyta plik i po cichu zgubi dane.
        // 4: notatki do ćwiczeń w planach (D-44). 5: tło w każdym motywie (D-50).
        assertEquals(5, BackupFile.VERSION)
    }

    @Test
    fun `odczyt odrzuca pusty obiekt, plik planu i nowsza wersje`() {
        fun rejected(text: String) = runCatching { BackupFile.parse(text, json) }.isFailure

        assertTrue(rejected("{}"))
        assertTrue(rejected("[1,2]"))
        assertTrue(rejected("nie json"))
        assertTrue(rejected("""{"format":"mysttic-trainings-plan","formatVersion":1,"app":"mysttic-trainings"}"""))
        assertTrue(rejected("""{"app":"inna-apka","version":1}"""))
        assertTrue(rejected("""{"app":"mysttic-trainings","version":99}"""))
        assertEquals(1, BackupFile.parse("""{"app":"mysttic-trainings","version":1}""", json).version)
    }

    @Test
    fun `plan i sesja z wersji 3 wracaja z nowymi polami`() {
        val plik = BackupFile(
            programs = listOf(
                BackupProgram(
                    id = "plan-x", name = "Od trenera", profileJson = "{}", source = "IMPORTED", author = "Ola",
                    description = "4 tygodnie", originId = "plan-y", shareUid = "abc", updatedAt = 5, rhythmStart = 7,
                ),
            ),
            sessions = listOf(
                BackupSession(
                    id = "s", workoutName = "A", difficulty = Difficulty.BEGINNER, startedAt = 1,
                    status = pl.mysttic.trainings.domain.model.SessionStatus.DONE, programId = "plan-x", plannedSets = 12,
                ),
            ),
            settings = BackupSettings(authorName = "Ola"),
        )
        val odczytany = BackupFile.parse(json.encodeToString(BackupFile.serializer(), plik), json)

        assertEquals(plik.programs, odczytany.programs)
        assertEquals(plik.sessions, odczytany.sessions)
        assertEquals("Ola", odczytany.settings?.authorName)
    }

    @Test
    fun `import pomija treningi planow, ktore juz sa w bazie`() {
        val plik = listOf(
            BackupWorkout(id = "plan-aa11-w1", name = "A z planu, który już jest"),
            BackupWorkout(id = "plan-aa11-copy-w1", name = "A z kopii planu, której nie ma"),
            BackupWorkout(id = "plan-bb22-w1", name = "B z nowego planu"),
            BackupWorkout(id = "moj-trening", name = "Własny trening"),
        )

        val doZapisu = BackupRepository.workoutsToRestore(
            plik,
            knownProgramIds = setOf("plan-aa11"),
            fileProgramIds = listOf("plan-aa11", "plan-aa11-copy", "plan-bb22"),
        )

        // Najdłuższy prefiks wygrywa: trening plan-aa11-copy-w1 należy do nowego planu z pliku, nie do plan-aa11.
        assertEquals(listOf("plan-aa11-copy-w1", "plan-bb22-w1", "moj-trening"), doZapisu.map { it.id })
    }

    @Test
    fun `odczyt kopii przerywa plik ponad limit, zanim wczyta calosc`() {
        val tekst = """{"app":"mysttic-trainings","version":4}"""
        assertEquals(tekst, BackupFile.readText(tekst.byteInputStream()))

        var przeczytane = 0
        val nieskonczony = object : java.io.InputStream() {
            override fun read(): Int {
                przeczytane++
                return 'a'.code
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                java.util.Arrays.fill(b, off, off + len, 'a'.code.toByte())
                przeczytane += len
                return len
            }
        }
        val blad = runCatching { BackupFile.readText(nieskonczony, limit = 100_000) }.exceptionOrNull()
        assertTrue(blad is IllegalArgumentException)
        // Czyta najwyżej jeden bufor ponad limit, nie cały strumień.
        assertTrue(przeczytane <= 100_000 + 8192)
    }

    @Test
    fun `limit liczy bajty, nie znaki`() {
        // „ł” to dwa bajty w UTF-8: 60 znaków = 120 bajtów.
        val tekst = "ł".repeat(60)
        assertTrue(runCatching { BackupFile.readText(tekst.byteInputStream(), limit = 100) }.isFailure)
        assertEquals(tekst, BackupFile.readText(tekst.byteInputStream(), limit = 120))
    }

    @Test
    fun `imie autora wraca tylko z kopii, ktora je zna`() {
        val v2 = BackupFile.parse("""{"app":"mysttic-trainings","version":2,"settings":{}}""", json)
        assertEquals("", v2.settings?.authorName)
        assertTrue(v2.version < BackupFile.AUTHOR_NAME_SINCE)
        assertTrue(BackupFile.VERSION >= BackupFile.AUTHOR_NAME_SINCE)
    }

    /** Kopia sprzed wersji 5 nie niesie teł motywów (D-50), więc import nie może nimi zgasić wybranego gradientu. */
    @Test
    fun `tla motywow wracaja tylko z kopii, ktora je zna`() {
        val v4 = BackupFile.parse("""{"app":"mysttic-trainings","version":4,"settings":{"themeId":"steel"}}""", json)
        assertEquals("steel", v4.settings?.themeId)
        assertTrue(v4.settings?.backgrounds.orEmpty().isEmpty())
        assertTrue(v4.version < BackupFile.BACKGROUNDS_SINCE)
        assertEquals(5, BackupFile.BACKGROUNDS_SINCE)
        assertTrue(BackupFile.VERSION >= BackupFile.BACKGROUNDS_SINCE)
    }

    @Test
    fun `notatki do cwiczen w planach wracaja z kopii, starsza kopia ich nie ma`() {
        val plik = BackupFile(
            exportedAt = 1,
            planNotes = listOf(BackupPlanNote("plan-aa11", "barbell-bench-press", "60 kg", 5)),
        )
        val odczytany = BackupFile.parse(json.encodeToString(BackupFile.serializer(), plik), json)
        assertEquals(plik.planNotes, odczytany.planNotes)
        assertEquals(BackupFile.VERSION, odczytany.version)

        val v3 = BackupFile.parse("""{"version":3,"app":"mysttic-trainings","exportedAt":1}""", json)
        assertTrue(v3.planNotes.isEmpty())
    }
}
