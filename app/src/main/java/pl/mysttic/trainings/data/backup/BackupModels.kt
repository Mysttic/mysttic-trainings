package pl.mysttic.trainings.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pl.mysttic.trainings.domain.model.Block
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.model.TargetKind
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Format pliku kopii zapasowej (M3). Osobne DTO zamiast encji Room i modeli domenowych:
 * plik ma przeżyć zmiany w bazie, a nowe pola muszą mieć wartości domyślne (kompatybilność wstecz).
 * Wbudowane ćwiczenia i treningi nie są zapisywane — odtwarza je seed.
 */
@Serializable
data class BackupFile(
    val version: Int = VERSION,
    val app: String = APP,
    val exportedAt: Long = 0L,
    val settings: BackupSettings? = null,
    val exercises: List<BackupExercise> = emptyList(),
    val workouts: List<BackupWorkout> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
    val setLogs: List<BackupSetLog> = emptyList(),
    /** Pamięć trenera (D-35); starsze kopie jej nie mają, więc pole ma wartość domyślną. */
    val tuning: List<BackupTuning> = emptyList(),
    /** Cykle treningowe (D-20, D-37) razem z dniami; kopie w wersji 1 ich nie mają. */
    val programs: List<BackupProgram> = emptyList(),
    /** Notatki do ćwiczeń w planach (D-44); kopie do wersji 3 ich nie mają. */
    val planNotes: List<BackupPlanNote> = emptyList(),
) {
    companion object {
        /**
         * 2: doszły cykle treningowe i pełne ustawienia. 3 (D-42): źródło, autor i opis planu, plan w sesji,
         * imię autora w ustawieniach. 4 (D-44): notatki do ćwiczeń w planach. 5 (D-50, D-51): tło w każdym motywie
         * i osoby na zdjęciu dnia.
         * Starsze kopie wczytują się dalej (same pola domyślne).
         */
        const val VERSION = 5
        const val APP = "mysttic-trainings"

        /** Największa kopia, jaką wczytujemy; historia kilku lat treningów to ułamek tego. */
        const val MAX_BYTES = 20 * 1024 * 1024

        /** Imię autora (D-42) jest w kopii od wersji 3; starsza kopia go nie ma i nie może skasować ustawionego. */
        const val AUTHOR_NAME_SINCE = 3

        /**
         * Tła motywów (D-50) i osoby na zdjęciu dnia (D-51) są w kopii od wersji 5; starsza kopia ich nie ma i nie może
         * zgasić wybranego tła.
         */
        const val BACKGROUNDS_SINCE = 5

        /**
         * Tekst kopii z limitem [MAX_BYTES] w bajtach. Plik większy odrzucamy w trakcie czytania, zanim trafi
         * w całości do pamięci: wybór kopii przyjmuje każdy typ pliku, więc omyłkowo wskazany film nie może
         * skończyć się brakiem pamięci.
         */
        fun readText(input: InputStream, limit: Int = MAX_BYTES): String {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                require(out.size() <= limit) { "Plik jest za duży jak na kopię zapasową." }
            }
            return out.toByteArray().decodeToString()
        }

        /**
         * Odczyt kopii z tekstu. `app` ma w klasie wartość domyślną, więc bez tej kontroli wczytałby się każdy
         * obiekt JSON — także `{}` i plik planu (D-42), który ma własny znacznik `format`.
         */
        fun parse(text: String, json: Json): BackupFile {
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw IllegalArgumentException("To nie jest kopia zapasowa tej aplikacji.")
            if (root.containsKey("format")) {
                throw IllegalArgumentException("To plik planu, nie kopia zapasowa. Wczytaj go z listy planów.")
            }
            val app = (root["app"] as? JsonPrimitive)?.content
            require(app == APP) { "To nie jest kopia zapasowa tej aplikacji." }
            val file = json.decodeFromJsonElement(serializer(), JsonObject(root))
            require(file.version <= VERSION) { "Plik pochodzi z nowszej wersji aplikacji (v${file.version})." }
            return file
        }
    }
}

/**
 * Cykl treningowy z kreatora albo z gotowego programu. `profileJson` jedzie tak, jak leży w bazie —
 * to snapshot `TrainingProfile`, który ma przeżyć zmiany w tej klasie tak samo jak przeżywa je w Room.
 * Treningi cyklu są zwykłymi treningami użytkownika, więc siedzą w `workouts` tego samego pliku.
 */
@Serializable
data class BackupProgram(
    val id: String,
    val name: String,
    val profileJson: String,
    val startedAt: Long = 0L,
    val cycleDays: Int = 0,
    val createdAt: Long = 0L,
    val active: Boolean = false,
    val days: List<BackupProgramDay> = emptyList(),
    /** Od wersji 3; null w starszym pliku = źródło liczone z `profileJson` (preset albo kreator). */
    val source: String? = null,
    val description: String = "",
    val author: String = "",
    val originId: String? = null,
    val shareUid: String? = null,
    val updatedAt: Long = 0L,
    val rhythmStart: Long = 0L,
)

/** Dzień cyklu; `workoutId == null` to dzień odpoczynku. */
@Serializable
data class BackupProgramDay(
    val dayIndex: Int,
    val workoutId: String? = null,
)

@Serializable
data class BackupTuning(
    val exerciseId: String,
    val repsDelta: Int = 0,
    val secondsDelta: Int = 0,
    val excluded: Boolean = false,
    val updatedAt: Long = 0L,
)

/** Notatka do ćwiczenia w planie (D-44). */
@Serializable
data class BackupPlanNote(
    val programId: String,
    val exerciseId: String,
    val note: String,
    val updatedAt: Long = 0L,
)

/**
 * Bez `seedVersion` i `lastUpdateCheck` — jedno i drugie to lokalna sprawa instalacji (który seed jest wczytany,
 * kiedy ostatnio pytaliśmy GitHuba o wydanie), nie dane użytkownika. Reszta ustawień jedzie w komplecie;
 * pola dodane po wersji 1 pliku mają wartości domyślne, więc starsza kopia wczytuje się bez zmian.
 */
@Serializable
data class BackupSettings(
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val ownedEquipment: List<Equipment> = emptyList(),
    val countdownSec: Int = 5,
    val soundEnabled: Boolean = true,
    val ttsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    val volumeKeyDone: Boolean = true,
    val healthConnectEnabled: Boolean = false,
    val updateChecks: Boolean = false,
    val libraryTiles: Boolean = true,
    val themeId: String = "fire",
    /** Imię na planach i raportach (D-42); od wersji 3. */
    val authorName: String = "",
    /** Tło w każdym motywie, id motywu → id tła (D-50); od wersji 5. */
    val backgrounds: Map<String, String> = emptyMap(),
    /** Kogo pokazywać na zdjęciu dnia (D-51); od wersji 5. */
    val photoPeople: String = "oboje",
)

@Serializable
data class BackupExercise(
    val id: String,
    val nameEn: String = "",
    val namePl: String,
    val kind: ExerciseKind,
    val level: Difficulty,
    val category: ExerciseCategory,
    val equipment: List<Equipment> = emptyList(),
    val primaryMuscles: List<MuscleGroup> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val instructionsPl: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val easierId: String? = null,
    val harderId: String? = null,
    val defaultReps: Int = 12,
    val defaultSeconds: Int = 30,
)

@Serializable
data class BackupWorkout(
    val id: String,
    val name: String,
    val note: String = "",
    val blocks: List<Block> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class BackupSession(
    val id: String,
    val workoutId: String? = null,
    val workoutName: String,
    val difficulty: Difficulty,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: SessionStatus,
    val rpe: Int? = null,
    val note: String = "",
    /** Od wersji 3 (D-42); starsze sesje rozlicza reguła zapasowa `PlanSessions`. */
    val programId: String? = null,
    val plannedSets: Int? = null,
)

@Serializable
data class BackupSetLog(
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val blockIndex: Int = 0,
    val itemIndex: Int = 0,
    val round: Int = 1,
    val setIndex: Int = 1,
    val targetKind: TargetKind,
    val targetValue: Int,
    val actualValue: Int,
    val weightKg: Double? = null,
    val completedAt: Long,
)

/** Co się stało przy imporcie — pokazywane wprost w Ustawieniach. */
data class ImportResult(
    val sessions: Int = 0,
    val setLogs: Int = 0,
    val exercises: Int = 0,
    val workouts: Int = 0,
    val programs: Int = 0,
    val skippedSessions: Int = 0,
    val settingsRestored: Boolean = false,
    /** Czy z pliku wrócił aktywny cykl — wtedy „Dziś” znowu ma co pokazać. */
    val planRestored: Boolean = false,
)
