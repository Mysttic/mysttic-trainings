package pl.mysttic.trainings.ui.share

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.data.share.PlanImportPreview
import pl.mysttic.trainings.data.share.PlanShareRepository
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.ui.navigation.PlanImportRoute
import pl.mysttic.trainings.ui.navigation.PlanShareRoute
import javax.inject.Inject

data class PlanShareUiState(
    val name: String = "",
    val author: String = "",
    val trainingDays: Int = 0,
    val days: Int = 0,
    val fileName: String = "plan.plan.json",
    /** Plik gotowy do okna udostępniania. */
    val shareUri: Uri? = null,
    val busy: Boolean = false,
    /** Informacja po udanej operacji. */
    val message: String? = null,
    /** Błąd eksportu: rysowany kolorem błędu, nie informacji. */
    val error: String? = null,
)

/** Wysłanie planu (D-42): plik do okna udostępniania albo do wybranego miejsca przez SAF. */
@HiltViewModel
class PlanShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programRepo: ProgramRepository,
    private val share: PlanShareRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val programId = savedStateHandle.toRoute<PlanShareRoute>().programId
    private val _ui = MutableStateFlow(PlanShareUiState())
    val ui: StateFlow<PlanShareUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val program = programRepo.byId(programId) ?: return@launch
            _ui.update {
                it.copy(
                    name = program.name,
                    author = program.author.ifBlank { settingsRepo.current().authorName },
                    trainingDays = program.days.count { d -> !d.isRest },
                    days = program.days.size,
                    fileName = share.suggestedName(programId),
                )
            }
        }
    }

    fun prepareShare() = launchOp {
        val (uri, _) = share.exportForShare(programId)
        _ui.update { it.copy(shareUri = uri) }
    }

    fun consumeShare() = _ui.update { it.copy(shareUri = null) }

    fun saveTo(uri: Uri) {
        // Pusty plik z okna zapisu już istnieje: przy trwającej operacji albo błędzie go usuwamy i mówimy dlaczego.
        if (_ui.value.busy) {
            viewModelScope.launch { share.discardDocument(uri) }
            _ui.update { it.copy(error = "Plik jeszcze się przygotowuje. Zapisz go za chwilę.") }
            return
        }
        launchOp(onFailure = { share.discardDocument(uri) }) {
            share.exportTo(uri, programId)
            _ui.update { it.copy(message = "Zapisano plik planu.") }
        }
    }

    private fun launchOp(onFailure: suspend () -> Unit = {}, block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (t: Throwable) {
                Log.e(TAG, "Eksport planu nie wyszedł", t)
                onFailure()
                _ui.update { it.copy(error = t.message ?: "Nie udało się przygotować pliku.") }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val TAG = "PlanShareViewModel"
    }
}

data class PlanImportUiState(
    /** Czekamy na wybór pliku (wejście z listy planów). */
    val picking: Boolean = false,
    val loading: Boolean = false,
    val preview: PlanImportPreview? = null,
    val exerciseNames: Map<String, String> = emptyMap(),
    val missingEquipment: Set<Equipment> = emptySet(),
    val error: String? = null,
    val importing: Boolean = false,
    /** Id planu po zapisie — ekran przechodzi do jego szczegółów. */
    val importedId: String? = null,
)

/** Podgląd planu z pliku i zapis (D-42). Nic nie trafia do bazy przed „Dodaj do planów”. */
@HiltViewModel
class PlanImportViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val share: PlanShareRepository,
    private val exerciseRepo: ExerciseRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val route = runCatching { savedStateHandle.toRoute<PlanImportRoute>() }.getOrNull()
    private var key: String? = savedStateHandle.get<String>(KEY_STAGED) ?: route?.stagedKey

    // Błąd odbioru z intencji: komunikat zamiast okna wyboru, dalej „Wybierz inny plik”.
    private val _ui = MutableStateFlow(
        PlanImportUiState(
            picking = key == null && route?.error == null,
            error = route?.error?.takeIf { key == null },
        ),
    )
    val ui: StateFlow<PlanImportUiState> = _ui.asStateFlow()

    init {
        key?.let { load(it) }
    }

    /**
     * Okno wyboru pliku otwiera się raz na żądanie: flaga schodzi przed otwarciem, więc obrót ekranu ani wynik
     * dostarczony po odtworzeniu Activity nie otworzą okna drugi raz. Zwraca, czy otworzyć.
     */
    fun startPicking(): Boolean {
        if (!_ui.value.picking) return false
        _ui.update { it.copy(picking = false) }
        return true
    }

    /** Plik wybrany ręcznie („Wczytaj plan z pliku”). */
    fun picked(uri: Uri?) {
        if (uri == null) {
            _ui.update { it.copy(picking = false, error = if (it.preview == null) "Nie wybrano pliku." else null) }
            return
        }
        _ui.update { it.copy(picking = false, loading = true, error = null) }
        viewModelScope.launch {
            try {
                val staged = share.stage(uri)
                key = staged
                savedStateHandle[KEY_STAGED] = staged
                load(staged)
            } catch (t: Throwable) {
                _ui.update { it.copy(loading = false, error = message(t)) }
            }
        }
    }

    fun pickAgain() = _ui.update { it.copy(picking = true, error = null) }

    private fun load(staged: String) {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val preview = share.preview(staged)
                val local = exerciseRepo.getMap()
                val names = preview.plan.newExercises.associate { it.id to it.namePl } +
                    preview.plan.exerciseIds.mapNotNull { id -> local[id]?.let { id to it.namePl } }
                val required = (preview.plan.newExercises + preview.plan.exerciseIds.mapNotNull { local[it] })
                    .flatMap { it.equipment }.toSet()
                _ui.update {
                    it.copy(
                        loading = false,
                        preview = preview,
                        exerciseNames = names,
                        missingEquipment = required - settingsRepo.current().ownedEquipment,
                    )
                }
            } catch (t: Throwable) {
                _ui.update { it.copy(loading = false, error = message(t)) }
            }
        }
    }

    fun import() {
        val staged = key ?: return
        if (_ui.value.importing) return
        _ui.update { it.copy(importing = true, error = null) }
        viewModelScope.launch {
            try {
                val id = share.commit(staged)
                savedStateHandle.remove<String>(KEY_STAGED)
                _ui.update { it.copy(importing = false, importedId = id) }
            } catch (t: Throwable) {
                Log.e(TAG, "Import planu nie wyszedł", t)
                _ui.update { it.copy(importing = false, error = message(t)) }
            }
        }
    }

    private fun message(t: Throwable): String = PlanShareRepository.errorMessage(t)

    private companion object {
        const val TAG = "PlanImportViewModel"
        const val KEY_STAGED = "staged_key"
    }
}
