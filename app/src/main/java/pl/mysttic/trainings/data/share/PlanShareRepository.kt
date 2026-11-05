package pl.mysttic.trainings.data.share

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.mysttic.trainings.BuildConfig
import pl.mysttic.trainings.data.db.AppDatabase
import pl.mysttic.trainings.data.db.dao.ProgramDao
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.plan.PlanCopy
import pl.mysttic.trainings.domain.plan.PlanSource
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.TrainingProfile
import pl.mysttic.trainings.domain.plan.share.ImportedPlan
import pl.mysttic.trainings.domain.plan.share.PlanFile
import pl.mysttic.trainings.domain.plan.share.PlanFileCodec
import pl.mysttic.trainings.domain.plan.share.PlanFileException
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import android.provider.DocumentsContract
import kotlinx.coroutines.NonCancellable

/** Podgląd pliku przed importem: plan po walidacji i czy ten plan już jest na telefonie. */
data class PlanImportPreview(
    val plan: ImportedPlan,
    /** Nazwa planu z tym samym `shareUid`, jeśli już go wczytano. */
    val alreadyHave: String?,
    /** Seed autora nowszy niż nasz: część ćwiczeń przyjdzie bez ilustracji. */
    val newerSeed: Boolean,
)

/**
 * Plik planu na zewnątrz i do środka (D-42). Plik wychodzi tylko przez systemowe okno udostępniania albo SAF,
 * przychodzi tylko jako `content://` od innej aplikacji. Import nigdy niczego nie nadpisuje: plan dostaje nowe id
 * (`PlanCopy`), ćwiczenia, które już są, zostają swoje, a nowy plan czeka na liście nieaktywny.
 */
@Singleton
class PlanShareRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val programRepo: ProgramRepository,
    private val programDao: ProgramDao,
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val settingsRepo: SettingsRepository,
    private val json: Json,
) {
    /** Odbiór pliku żyje dłużej niż Activity: obrót ekranu w trakcie czytania nie gubi pliku. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _incoming = Channel<IncomingPlanFile>(Channel.BUFFERED)

    /** Pliki odłożone do `cacheDir/incoming` albo błędy odbioru; nawigacja otwiera podgląd importu. */
    val incoming: Flow<IncomingPlanFile> = _incoming.receiveAsFlow()

    // ---------------------------------------------------------------- wysyłka

    /** Plik planu w `cacheDir/shared` i jego `content://` do okna udostępniania. */
    suspend fun exportForShare(programId: String): Pair<Uri, String> = withContext(Dispatchers.IO) {
        cleanup()
        val (text, name) = exportText(programId)
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(text)
        FileProvider.getUriForFile(context, authority, file) to name
    }

    /** Zapis przez SAF (`CreateDocument`) — do Plików, na dysk, na kartę. */
    suspend fun exportTo(uri: Uri, programId: String) = withContext(Dispatchers.IO) {
        val (text, _) = exportText(programId)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Nie udało się otworzyć pliku do zapisu.")
    }

    /**
     * Plik z okna zapisu (`CreateDocument`) istnieje, pusty, zanim cokolwiek do niego trafi. Gdy zapis się nie uda,
     * sprzątamy go, żeby w Plikach nie został plik planu, którego nie da się wczytać (C3-11).
     */
    suspend fun discardDocument(uri: Uri) = withContext(NonCancellable + Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            .onFailure { Log.w("PlanShareRepository", "Nie udało się usunąć pustego pliku planu", it) }
    }

    suspend fun suggestedName(programId: String): String =
        PlanFileCodec.fileName(programRepo.byId(programId)?.name ?: "plan")

    private suspend fun exportText(programId: String): Pair<String, String> {
        val program = programRepo.byId(programId) ?: error("Nie ma takiego planu.")
        val workouts = program.days.mapNotNull { it.workoutId }.distinct()
            .mapNotNull { workoutRepo.getById(it) }.associateBy { it.id }
        val settings = settingsRepo.current()
        // Tożsamość planu nadajemy przy pierwszym eksporcie: kolejne wysyłki tego samego planu rozpozna odbiorca.
        val uid = program.shareUid ?: UUID.randomUUID().toString().also { programRepo.setShareUid(programId, it) }
        val author = program.author.ifBlank { settings.authorName }
        val file = PlanFileCodec.export(
            program = program,
            workouts = workouts,
            exercises = exerciseRepo.getMap(),
            uid = uid,
            author = author,
            appVersion = BuildConfig.VERSION_NAME,
            seedVersion = settings.seedVersion,
            exportedAt = System.currentTimeMillis(),
            difficulty = settings.difficulty,
            owned = settings.ownedEquipment,
            notes = programRepo.notes(programId),
        )
        return PlanFileCodec.encode(file, json) to PlanFileCodec.fileName(program.name)
    }

    // ---------------------------------------------------------------- odbiór

    /**
     * Plik z intencji (Otwórz za pomocą, Udostępnij). Czytamy od razu, bo uprawnienie do `content://` wygasa
     * razem z Activity; przyjmujemy tylko cudze `content://`, żeby nikt nie podsunął nam naszych własnych plików.
     * Każdy wynik idzie do nawigacji: odłożony plik otwiera podgląd, a błąd ten sam ekran z komunikatem,
     * tak jak przy „Wczytaj plan z pliku”.
     */
    fun receive(uri: Uri) {
        when {
            uri.scheme != ContentResolver.SCHEME_CONTENT -> _incoming.trySend(IncomingPlanFile.Failed(CANNOT_OPEN))
            uri.authority == authority -> _incoming.trySend(IncomingPlanFile.Failed(OWN_FILE))
            else -> scope.launch {
                val result = try {
                    IncomingPlanFile.Staged(stage(uri))
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "Odbiór pliku planu nie wyszedł", t)
                    IncomingPlanFile.Failed(errorMessage(t))
                }
                _incoming.send(result)
            }
        }
    }

    /** Odkłada plik do `cacheDir/incoming` i zwraca klucz dla podglądu. Rzuca [PlanFileException] przy złym pliku. */
    suspend fun stage(uri: Uri): String = withContext(Dispatchers.IO) {
        cleanup()
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = input.readNBytesCompat(PlanFile.MAX_BYTES + 1)
            if (buffer.size > PlanFile.MAX_BYTES) throw PlanFileException("Plik jest za duży jak na plan treningowy.")
            buffer
        } ?: throw PlanFileException(CANNOT_OPEN)
        val key = UUID.randomUUID().toString()
        File(File(context.cacheDir, INCOMING_DIR).apply { mkdirs() }, "$key.json").writeBytes(bytes)
        key
    }

    suspend fun preview(key: String): PlanImportPreview = withContext(Dispatchers.IO) {
        val plan = read(key)
        val settings = settingsRepo.current()
        PlanImportPreview(
            plan = plan,
            alreadyHave = programDao.byShareUid(plan.uid).firstOrNull()?.name,
            newerSeed = plan.seedVersion > settings.seedVersion,
        )
    }

    /** Zapis planu z pliku: nowe id, dosłowne bloki, nieaktywny. Zwraca id nowego planu. */
    suspend fun commit(key: String, now: Long = System.currentTimeMillis()): String = withContext(Dispatchers.IO) {
        val plan = read(key)
        val programId = programRepo.newProgramId()
        val copied = PlanCopy.copy(plan.days, plan.workouts.associateBy { it.id }, programId, now)
        val program = Program(
            id = programId,
            name = plan.name,
            profile = TrainingProfile.neutral(plan.level, plan.days.size),
            startedAt = now,
            cycleDays = plan.days.size,
            createdAt = now,
            active = false,
            days = copied.days,
            source = PlanSource.IMPORTED,
            description = plan.description,
            author = plan.author,
            shareUid = plan.uid,
            updatedAt = now,
        )
        db.withTransaction {
            // Ćwiczenie, które już jest (z seedu albo wcześniejszego importu), zostaje swoje — plik go nie nadpisze.
            val known = exerciseRepo.getMap().keys
            plan.newExercises.filter { it.id !in known }.forEach { exerciseRepo.save(it) }
            programRepo.insert(program, copied.workouts, plan.notes)
        }
        File(File(context.cacheDir, INCOMING_DIR), "$key.json").delete()
        programId
    }

    private suspend fun read(key: String): ImportedPlan {
        require(KEY.matches(key)) { "Zły klucz pliku." }
        val file = File(File(context.cacheDir, INCOMING_DIR), "$key.json")
        if (!file.exists()) throw PlanFileException("Plik zniknął — otwórz go jeszcze raz.")
        val text = file.readText()
        return PlanFileCodec.validate(PlanFileCodec.decode(text, json), exerciseRepo.getMap())
    }

    /** Pliki starsze niż doba z `shared`, `reports` i `incoming`: cache nie puchnie od wysłanych planów. */
    fun cleanup(now: Long = System.currentTimeMillis()) {
        listOf(SHARED_DIR, REPORTS_DIR, INCOMING_DIR).forEach { name ->
            File(context.cacheDir, name).listFiles()?.forEach { file ->
                if (now - file.lastModified() > DAY_MS) file.delete()
            }
        }
    }

    private val authority: String get() = "${context.packageName}.updates"

    companion object {
        const val SHARED_DIR = "shared"
        const val REPORTS_DIR = "reports"
        const val INCOMING_DIR = "incoming"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private val KEY = Regex("[0-9a-f-]{36}")
        private const val TAG = "PlanShareRepository"
        private const val CANNOT_OPEN = "Nie udało się otworzyć pliku."
        private const val OWN_FILE = "Ten plik wyszedł z tej aplikacji. Wyślij go innej osobie albo zapisz do pliku."

        /** Komunikat na ekran dla błędu odczytu pliku planu; ten sam dla intencji i dla wyboru pliku. */
        fun errorMessage(t: Throwable): String = when (t) {
            is PlanFileException -> t.message ?: "To nie jest plik planu."
            is IllegalArgumentException -> t.message ?: "Plik planu jest uszkodzony."
            else -> "Nie udało się wczytać pliku."
        }
    }
}

/** Plik planu z intencji: klucz pliku odłożonego do podglądu albo komunikat, czemu się nie dało. */
sealed interface IncomingPlanFile {
    data class Staged(val key: String) : IncomingPlanFile
    data class Failed(val message: String) : IncomingPlanFile
}

/** `InputStream.readNBytes` jest od API 33; minSdk to 26. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
