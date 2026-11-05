package pl.mysttic.trainings.ui.stats

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.data.share.PlanShareRepository
import pl.mysttic.trainings.domain.editor.WorkoutEditing
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.report.PlanReport
import pl.mysttic.trainings.domain.report.PlanReportBuilder
import pl.mysttic.trainings.domain.stats.PlanStats
import pl.mysttic.trainings.domain.stats.PlanStatsCalculator
import pl.mysttic.trainings.ui.navigation.PlanStatsRoute
import java.io.File
import java.time.ZoneId
import javax.inject.Inject

data class PlanStatsUiState(
    val loading: Boolean = true,
    val program: Program? = null,
    val stats: PlanStats? = null,
    val includeNotes: Boolean = false,
    val busy: Boolean = false,
    /** Raport gotowy do okna udostępniania. */
    val shareUri: Uri? = null,
    val fileName: String = "raport.pdf",
    val message: String? = null,
    /** Komunikat to błąd: rysujemy go kolorem błędu, a nie jak potwierdzenie (C3-16). */
    val messageIsError: Boolean = false,
    /** Podgląd raportu na ekranie; null = schowany. */
    val preview: ReportPreview? = null,
)

/**
 * Statystyki jednego planu i raport PDF (D-42). Dane liczy czysty [PlanStatsCalculator], treść raportu
 * [PlanReportBuilder], a PDF rysuje [PdfReportRenderer]. Raport powstaje tylko na żądanie.
 */
@HiltViewModel
class PlanStatsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    programRepo: ProgramRepository,
    workoutRepo: WorkoutRepository,
    sessionRepo: SessionRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val programId = savedStateHandle.toRoute<PlanStatsRoute>().programId
    private val local = MutableStateFlow(PlanStatsUiState())
    private val zone = ZoneId.systemDefault()

    val ui: StateFlow<PlanStatsUiState> = combine(
        programRepo.observeById(programId),
        workoutRepo.observeAll(),
        sessionRepo.observeSessions(),
        sessionRepo.observeAllSetLogs(),
        local,
    ) { program, workouts, sessions, logs, l ->
        if (program == null) return@combine l.copy(loading = false)
        val byId = workouts.associateBy { it.id }
        val planWorkouts = program.days.mapNotNull { it.workoutId }.distinct().mapNotNull { byId[it] }
        val unscored = planWorkouts.flatMap { w ->
            w.blocks.flatMapIndexed { b, block ->
                block.items.mapIndexedNotNull { i, item ->
                    "${w.id}/$b/$i".takeIf { item.openEnded || item.targetKind == TargetKind.AMRAP }
                }
            }
        }.toSet()
        val stats = PlanStatsCalculator.compute(
            program = program,
            workoutNames = planWorkouts.associate { it.id to it.name },
            sessions = sessions,
            logs = logs,
            plannedFallback = planWorkouts.associate { it.id to WorkoutEditing.workCount(it.blocks) },
            unscored = unscored,
        )
        l.copy(loading = false, program = program, stats = stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanStatsUiState())

    /** Zmiana notatek chowa podgląd: pokazywałby raport bez tej zmiany. */
    fun setIncludeNotes(value: Boolean) = local.update { it.copy(includeNotes = value, preview = null) }

    fun shareReport() = launchOp {
        val (file, report) = renderToCache()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        local.update { it.copy(shareUri = uri, fileName = report.fileName) }
    }

    fun consumeShare() = local.update { it.copy(shareUri = null) }

    /** Podgląd pierwszych stron tego samego PDF, który idzie do udostępnienia; drugi raz chowa podgląd. */
    fun togglePreview() {
        if (local.value.preview != null) {
            local.update { it.copy(preview = null) }
            return
        }
        launchOp {
            val (file, _) = renderToCache(PREVIEW_FILE)
            val preview = withContext(Dispatchers.IO) { ReportPreviewRenderer.render(file) }
            local.update { it.copy(preview = preview) }
        }
    }

    /**
     * Zapis do pliku wybranego w oknie systemowym. Ten plik już istnieje (pusty), zanim raport powstanie,
     * więc gdy zapis się nie uda albo trwa inna operacja, usuwamy go i mówimy dlaczego.
     */
    fun saveReport(uri: Uri) {
        if (local.value.busy) {
            viewModelScope.launch { discard(uri) }
            local.update { it.copy(message = "Raport jeszcze się przygotowuje. Zapisz go za chwilę.", messageIsError = true) }
            return
        }
        launchOp(onFailure = { discard(uri) }) {
            val report = report() ?: error("Brak danych planu.")
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { PdfReportRenderer(context).render(report, it) }
                    ?: error("Nie udało się otworzyć pliku do zapisu.")
            }
            local.update { it.copy(message = "Zapisano raport.", messageIsError = false) }
        }
    }

    suspend fun suggestedName(): String = report()?.fileName ?: "raport.pdf"

    private suspend fun discard(uri: Uri) = withContext(NonCancellable + Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            .onFailure { Log.w(TAG, "Nie udało się usunąć pustego pliku raportu", it) }
    }

    private suspend fun renderToCache(fileName: String? = null): Pair<File, PlanReport> {
        val report = report() ?: error("Brak danych planu.")
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, PlanShareRepository.REPORTS_DIR).apply { mkdirs() }
            val file = File(dir, fileName ?: report.fileName)
            file.outputStream().use { PdfReportRenderer(context).render(report, it) }
            file to report
        }
    }

    private suspend fun report(): PlanReport? {
        val state = ui.value
        val program = state.program ?: return null
        val stats = state.stats ?: return null
        val settings = settingsRepo.current()
        // Autor planu to ten, kto go ułożył; osoba ćwicząca to właściciel telefonu (imię z Ustawień).
        val person = settings.authorName.takeIf { it != program.author }.orEmpty()
        return PlanReportBuilder.build(
            planName = program.name,
            author = program.author,
            person = person,
            stats = stats,
            generatedAt = System.currentTimeMillis(),
            zone = zone,
            includeNotes = state.includeNotes,
        )
    }

    private fun launchOp(onFailure: suspend () -> Unit = {}, block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (t: Throwable) {
                Log.e(TAG, "Raport nie wyszedł", t)
                onFailure()
                local.update { it.copy(message = t.message ?: "Nie udało się przygotować raportu.", messageIsError = true) }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val TAG = "PlanStatsViewModel"
        const val PREVIEW_FILE = "podglad.pdf"
    }
}
