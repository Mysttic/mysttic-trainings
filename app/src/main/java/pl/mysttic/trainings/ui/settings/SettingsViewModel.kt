package pl.mysttic.trainings.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.backup.BackupRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.data.settings.UserSettings
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.BuildConfig
import pl.mysttic.trainings.data.update.DatabaseInstall
import pl.mysttic.trainings.data.update.ExerciseDatabaseRepository
import pl.mysttic.trainings.data.update.UpdateRepository
import pl.mysttic.trainings.domain.engine.Announcer.plural
import pl.mysttic.trainings.domain.update.DatabaseOffer
import pl.mysttic.trainings.domain.update.DatabaseUpdate
import pl.mysttic.trainings.domain.update.ReleaseInfo
import pl.mysttic.trainings.domain.update.UpdateCheck
import pl.mysttic.trainings.domain.update.UpdateOutcome
import pl.mysttic.trainings.service.HealthConnectExporter
import pl.mysttic.trainings.service.PlayerController
import pl.mysttic.trainings.service.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import pl.mysttic.trainings.data.share.PlanSigningKey

/** Stan sekcji „Aktualizacje": wynik ostatniego pytania i to, co akurat trwa, osobno dla aplikacji i bazy ćwiczeń. */
data class UpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val release: ReleaseInfo? = null,
    val message: String? = null,
    val error: Boolean = false,
    /** Nowsze wydanie bazy ćwiczeń (D-47); null = nie pytano albo nie ma nowszej. */
    val database: DatabaseOffer? = null,
    val databaseWorking: Boolean = false,
    val databaseMessage: String? = null,
    val databaseError: Boolean = false,
)

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Komunikat po eksporcie albo imporcie; `error` steruje kolorem w UI. */
data class BackupStatus(val message: String, val error: Boolean = false, val working: Boolean = false)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val backup: BackupRepository,
    private val reminders: ReminderScheduler,
    private val health: HealthConnectExporter,
    private val updates: UpdateRepository,
    private val databases: ExerciseDatabaseRepository,
    private val player: PlayerController,
    private val planSigningKey: PlanSigningKey,
) : ViewModel() {

    /** Odcisk klucza podpisu planów (D-58) do „O mnie”; liczony raz, w tle, bo za pierwszym razem tworzy klucz. */
    private val _signingKey = MutableStateFlow<String?>(null)
    val signingKey: StateFlow<String?> = _signingKey.asStateFlow()

    init {
        viewModelScope.launch {
            _signingKey.value = runCatching { withContext(Dispatchers.Default) { planSigningKey.fingerprint() } }.getOrNull()
        }
    }

    private val _update = MutableStateFlow(UpdateUiState())
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

    /**
     * Sprawdzenie przy wejściu do Ustawień, ale najwyżej raz na dobę i tylko z włączonym przełącznikiem.
     * Ręczny przycisk pyta zawsze — od tego jest.
     */
    fun checkUpdatesIfDue(now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val settings = repo.current()
            if (!settings.updateChecks) return@launch
            if (now - settings.lastUpdateCheck < DAY_MS) return@launch
            checkUpdates(now)
        }
    }

    fun checkUpdates(now: Long = System.currentTimeMillis()) {
        if (_update.value.checking) return
        _update.value = _update.value.copy(checking = true, message = null, release = null, database = null, databaseMessage = null)
        viewModelScope.launch {
            val result = updates.check()
            repo.setLastUpdateCheck(now)
            // Manifest bazy to drugie, małe pytanie: tylko gdy na stronie jest numer wyższy od wczytanego.
            val installed = repo.current().seedVersion
            val database = result.database?.takeIf { it.version > installed }?.let { release ->
                DatabaseUpdate.decide(release, databases.manifest(release), installed, BuildConfig.VERSION_NAME)
            }
            val app = when (result.app.outcome) {
                UpdateOutcome.NEWER -> UpdateUiState(release = result.app.release)
                UpdateOutcome.UP_TO_DATE -> UpdateUiState(message = "Masz najnowszą wersję aplikacji.")
                UpdateOutcome.FAILED -> UpdateUiState(
                    message = "Nie udało się sprawdzić wydań. Jeśli repozytorium jest jeszcze prywatne, to normalne.",
                    error = true,
                )
            }
            _update.value = app.copy(database = database)
        }
    }

    /** Pobiera, sprawdza i wczytuje nowszą bazę ćwiczeń (D-47). W trakcie treningu tylko zapisuje: wczyta ją następny start. */
    fun downloadDatabase() {
        val offer = _update.value.database as? DatabaseOffer.Available ?: return
        if (_update.value.databaseWorking) return
        _update.value = _update.value.copy(databaseWorking = true, databaseMessage = null, databaseError = false)
        viewModelScope.launch { showDatabaseResult(databases.download(offer, importNow = !player.isActive)) }
    }

    /** Baza ćwiczeń z pliku ZIP (tylko build debug): ta sama kontrola i ten sam import co z wydania. */
    fun installDatabaseFromFile(uri: Uri) {
        if (_update.value.databaseWorking) return
        _update.value = _update.value.copy(databaseWorking = true, databaseMessage = null, databaseError = false)
        viewModelScope.launch { showDatabaseResult(databases.installFrom(uri, importNow = !player.isActive)) }
    }

    private fun showDatabaseResult(result: DatabaseInstall) {
        val (message, error) = when (result) {
            is DatabaseInstall.Imported -> "Baza ćwiczeń v${result.version} wczytana." to false
            is DatabaseInstall.Pending ->
                "Baza ćwiczeń v${result.version} pobrana. Trwa trening, więc wczyta się przy następnym uruchomieniu." to false
            is DatabaseInstall.Rejected -> {
                val more = result.reasons.size - 1
                val rest = if (more > 0) " (i $more ${plural(more, "inny powód", "inne powody", "innych powodów")})" else ""
                "Baza odrzucona: ${result.reasons.first()}$rest. Nic w telefonie się nie zmieniło." to true
            }
            is DatabaseInstall.Failed -> result.message to true
        }
        val done = result is DatabaseInstall.Imported || result is DatabaseInstall.Pending
        _update.value = _update.value.copy(
            databaseWorking = false,
            database = if (done) null else _update.value.database,
            databaseMessage = message,
            databaseError = error,
        )
    }

    /** Pobiera APK i oddaje go systemowemu instalatorowi; zgodę na podmianę pakietu wydaje użytkownik. */
    fun downloadAndInstall() {
        val release = _update.value.release ?: return
        if (release.assetUrl == null) {
            _update.value = _update.value.copy(message = "To wydanie nie ma pliku APK.", error = true)
            return
        }
        _update.value = _update.value.copy(downloading = true, message = null)
        viewModelScope.launch {
            val file = updates.download(release)
            _update.value = if (file != null && updates.install(file)) {
                _update.value.copy(downloading = false, message = "Instalator systemu przejmuje resztę.")
            } else {
                _update.value.copy(downloading = false, message = "Pobieranie nie powiodło się.", error = true)
            }
        }
    }

    /** Wyjście awaryjne: wydanie bez pliku dla Androida da się pobrać ze strony projektu. */
    fun openReleasePage(context: Context) {
        val url = _update.value.release?.pageUrl ?: UpdateCheck.PAGE
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    fun setUpdateChecks(on: Boolean) = viewModelScope.launch {
        repo.setUpdateChecks(on)
        if (on) checkUpdates()
    }

    /** Health Connect bywa nieobecny (starsze telefony, brak modułu) — wtedy nie pokazujemy przełącznika. */
    val healthAvailable: Boolean get() = health.available

    /** Uprawnienia, o które prosi kontrakt Health Connect. */
    val healthPermissions: Set<String> get() = health.permissions

    private val _healthGranted = MutableStateFlow(false)
    val healthGranted: StateFlow<Boolean> = _healthGranted.asStateFlow()

    fun refreshHealthPermission() {
        viewModelScope.launch { _healthGranted.value = runCatching { health.hasPermission() }.getOrDefault(false) }
    }

    fun setHealthConnectEnabled(on: Boolean) = viewModelScope.launch {
        repo.setHealthConnectEnabled(on)
        if (on) _healthGranted.value = runCatching { health.hasPermission() }.getOrDefault(false)
    }

    val settings: StateFlow<UserSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
    val backupStatus: StateFlow<BackupStatus?> = _backupStatus.asStateFlow()

    fun setDifficulty(d: Difficulty) = viewModelScope.launch { repo.setDifficulty(d) }
    fun toggleEquipment(e: Equipment) = viewModelScope.launch { repo.toggleEquipment(e) }
    fun setCountdown(sec: Int) = viewModelScope.launch { repo.setCountdownSec(sec) }
    fun setSound(on: Boolean) = viewModelScope.launch { repo.setSoundEnabled(on) }
    fun setTts(on: Boolean) = viewModelScope.launch { repo.setTtsEnabled(on) }
    fun setVibration(on: Boolean) = viewModelScope.launch { repo.setVibrationEnabled(on) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { repo.setKeepScreenOn(on) }
    fun setVolumeKeyDone(on: Boolean) = viewModelScope.launch { repo.setVolumeKeyDone(on) }
    fun setTheme(id: String) = viewModelScope.launch { repo.setThemeId(id) }
    fun setBackground(themeId: String, backgroundId: String) = viewModelScope.launch { repo.setBackground(themeId, backgroundId) }
    fun setGender(id: String) = viewModelScope.launch { repo.setGender(id) }
    fun setHeightCm(cm: Int) = viewModelScope.launch { repo.setHeightCm(cm) }
    fun setWeightKg(kg: Double) = viewModelScope.launch { repo.setWeightKg(kg) }
    fun setAuthorName(name: String) = viewModelScope.launch { repo.setAuthorName(name) }

    fun setReminderEnabled(on: Boolean) = viewModelScope.launch {
        repo.setReminderEnabled(on)
        reminders.sync()
    }

    fun setReminderTime(hour: Int, minute: Int) = viewModelScope.launch {
        repo.setReminderTime(hour, minute)
        reminders.sync()
    }

    /** Nazwa proponowana w oknie zapisu: `treningi-2026-09-05.json`. */
    fun suggestedFileName(now: Long = System.currentTimeMillis()): String =
        "treningi-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))}.json"

    fun export(uri: Uri) {
        _backupStatus.value = BackupStatus("Zapisywanie…", working = true)
        viewModelScope.launch {
            runCatching { backup.export(uri) }
                .onSuccess { file ->
                    _backupStatus.value = BackupStatus(
                        "Zapisano ${file.sessions.size} ${sessionsWord(file.sessions.size)}, " +
                            "${file.setLogs.size} ${setsWord(file.setLogs.size)}" +
                            (if (file.exercises.isNotEmpty()) ", ${file.exercises.size} własnych ćwiczeń" else "") + ".",
                    )
                }
                .onFailure { fail(it, "Eksport nie powiódł się") }
        }
    }

    fun import(uri: Uri) {
        _backupStatus.value = BackupStatus("Wczytywanie…", working = true)
        viewModelScope.launch {
            val result = runCatching { backup.import(uri) }
                .getOrElse { fail(it, "Import nie powiódł się"); return@launch }
            // Ustawienia wróciły z pliku, więc alarm przypomnienia trzeba przestawić pod odtworzoną godzinę.
            if (result.settingsRestored) reminders.sync()
            _backupStatus.value = BackupStatus(
                "Dodano ${result.sessions} ${sessionsWord(result.sessions)}, ${result.setLogs} ${setsWord(result.setLogs)}" +
                    (if (result.skippedSessions > 0) "; pominięto ${result.skippedSessions} już istniejących" else "") +
                    (if (result.exercises > 0) "; ćwiczenia własne: ${result.exercises}" else "") +
                    (if (result.workouts > 0) "; treningi własne: ${result.workouts}" else "") +
                    (if (result.programs > 0) "; cykle: ${result.programs}" else "") +
                    (if (result.planRestored) "; plan wrócił" else "") +
                    (if (result.settingsRestored) "; ustawienia odtworzone" else "") + ".",
            )
        }
    }

    fun clearStatus() {
        _backupStatus.value = null
    }

    private fun fail(t: Throwable, prefix: String) {
        Log.e(TAG, prefix, t)
        _backupStatus.value = BackupStatus("$prefix: ${t.message ?: "nieznany błąd"}", error = true)
    }

    private fun sessionsWord(n: Int) = when {
        n == 1 -> "sesję"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "sesje"
        else -> "sesji"
    }

    private fun setsWord(n: Int) = when {
        n == 1 -> "serię"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "serie"
        else -> "serii"
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
