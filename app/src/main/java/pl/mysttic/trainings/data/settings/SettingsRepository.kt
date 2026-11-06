package pl.mysttic.trainings.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Gender
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Ustawienia użytkownika. Poziom trudności i posiadany sprzęt są globalne i zmienne w każdej chwili. */
data class UserSettings(
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val ownedEquipment: Set<Equipment> = emptySet(),
    val countdownSec: Int = 5,
    val soundEnabled: Boolean = true,
    val ttsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val seedVersion: Int = 0,
    /** Codzienne przypomnienie o treningu (M6); godzina lokalna. */
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    /** Przycisk głośności potwierdza serię na powtórzenia (M6). */
    val volumeKeyDone: Boolean = true,
    /** Zapis ukończonych treningów do Health Connect (M6); domyślnie wyłączone. */
    val healthConnectEnabled: Boolean = false,
    /** Pytanie do GitHuba o nowsze wydanie raz dziennie (M7); domyślnie wyłączone, bo to jedyne wyjście do sieci. */
    val updateChecks: Boolean = false,
    val lastUpdateCheck: Long = 0L,
    /** Biblioteka jako kafelki z ilustracjami (domyślnie) albo lista wierszy; wybór z nagłówka biblioteki. */
    val libraryTiles: Boolean = true,
    /**
     * Wybrany motyw (D-39). Trzymamy identyfikator, a nie enum z `ui/theme`, żeby warstwa danych
     * nie zaglądała do interfejsu; mapuje go `AppTheme.fromId`.
     */
    val themeId: String = "fire",
    /**
     * Tło wybrane w każdym motywie (D-50): id motywu → id wariantu tła, więc powrót do motywu przywraca jego tło.
     * Brak wpisu = samo zdjęcie (D-55). Też identyfikatory, z tego samego powodu co `themeId`; mapuje je `AppBackground.resolve`.
     */
    val backgrounds: Map<String, String> = emptyMap(),
    /** Imię na planach wysyłanych innym i na raportach (D-42); od D-57 w „O mnie”. Puste = trzeba podać przed wysyłką. */
    val authorName: String = "",
    /**
     * Płeć z „O mnie” (D-57): id jak w `Gender`, puste = nie podano. Wybiera osoby na zdjęciu dnia (`PhotoPeople.of`),
     * nic więcej. Zastępuje „Na zdjęciach” z D-51.
     */
    val gender: String = "",
    /** Wzrost i waga z „O mnie” (D-57); null = jeszcze nie podane. Kreator startuje od nich i zapisuje je z powrotem. */
    val heightCm: Int? = null,
    val weightKg: Double? = null,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val difficulty = intPreferencesKey("difficulty_level")
        val ownedEquipment = stringSetPreferencesKey("owned_equipment")
        val countdownSec = intPreferencesKey("countdown_sec")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val ttsEnabled = booleanPreferencesKey("tts_enabled")
        val vibrationEnabled = booleanPreferencesKey("vibration_enabled")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val seedVersion = intPreferencesKey("seed_version")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val volumeKeyDone = booleanPreferencesKey("volume_key_done")
        val healthConnectEnabled = booleanPreferencesKey("health_connect_enabled")
        val updateChecks = booleanPreferencesKey("update_checks")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val libraryTiles = booleanPreferencesKey("library_tiles")
        val themeId = stringPreferencesKey("theme_id")
        val backgrounds = stringSetPreferencesKey("backgrounds")
        /** Klucz z D-51 („Na zdjęciach”); czytany tylko jako zapas dla płci z kompilacji rozwojowych sprzed D-57. */
        val legacyPhotoPeople = stringPreferencesKey("photo_people")
        val authorName = stringPreferencesKey("author_name")
        val gender = stringPreferencesKey("gender")
        val heightCm = intPreferencesKey("height_cm")
        val weightKg = doublePreferencesKey("weight_kg")
    }

    private val store: DataStore<Preferences> get() = context.settingsDataStore

    val settings: Flow<UserSettings> = store.data.map { p ->
        UserSettings(
            difficulty = p[Keys.difficulty]?.let { Difficulty.fromLevel(it) } ?: Difficulty.INTERMEDIATE,
            ownedEquipment = p[Keys.ownedEquipment].orEmpty()
                .mapNotNull { name -> Equipment.entries.firstOrNull { it.name == name } }.toSet(),
            countdownSec = p[Keys.countdownSec] ?: 5,
            soundEnabled = p[Keys.soundEnabled] ?: true,
            ttsEnabled = p[Keys.ttsEnabled] ?: true,
            vibrationEnabled = p[Keys.vibrationEnabled] ?: true,
            keepScreenOn = p[Keys.keepScreenOn] ?: true,
            seedVersion = p[Keys.seedVersion] ?: 0,
            reminderEnabled = p[Keys.reminderEnabled] ?: false,
            reminderHour = p[Keys.reminderHour] ?: 18,
            reminderMinute = p[Keys.reminderMinute] ?: 0,
            volumeKeyDone = p[Keys.volumeKeyDone] ?: true,
            healthConnectEnabled = p[Keys.healthConnectEnabled] ?: false,
            updateChecks = p[Keys.updateChecks] ?: false,
            lastUpdateCheck = p[Keys.lastUpdateCheck] ?: 0L,
            libraryTiles = p[Keys.libraryTiles] ?: true,
            themeId = p[Keys.themeId] ?: "fire",
            backgrounds = BackgroundChoices.decode(p[Keys.backgrounds].orEmpty()),
            authorName = p[Keys.authorName] ?: "",
            gender = p[Keys.gender] ?: Gender.fromId(p[Keys.legacyPhotoPeople]).id,
            heightCm = p[Keys.heightCm],
            weightKg = p[Keys.weightKg],
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setDifficulty(difficulty: Difficulty) = store.edit { it[Keys.difficulty] = difficulty.level }

    suspend fun setOwnedEquipment(owned: Set<Equipment>) = store.edit { it[Keys.ownedEquipment] = owned.map { e -> e.name }.toSet() }

    suspend fun toggleEquipment(equipment: Equipment) {
        val owned = current().ownedEquipment
        setOwnedEquipment(if (equipment in owned) owned - equipment else owned + equipment)
    }

    suspend fun setCountdownSec(sec: Int) = store.edit { it[Keys.countdownSec] = sec.coerceIn(0, 30) }

    suspend fun setSoundEnabled(enabled: Boolean) = store.edit { it[Keys.soundEnabled] = enabled }

    suspend fun setTtsEnabled(enabled: Boolean) = store.edit { it[Keys.ttsEnabled] = enabled }

    suspend fun setVibrationEnabled(enabled: Boolean) = store.edit { it[Keys.vibrationEnabled] = enabled }

    suspend fun setKeepScreenOn(enabled: Boolean) = store.edit { it[Keys.keepScreenOn] = enabled }

    suspend fun setSeedVersion(version: Int) = store.edit { it[Keys.seedVersion] = version }

    suspend fun setVolumeKeyDone(enabled: Boolean) = store.edit { it[Keys.volumeKeyDone] = enabled }

    suspend fun setHealthConnectEnabled(enabled: Boolean) = store.edit { it[Keys.healthConnectEnabled] = enabled }

    suspend fun setUpdateChecks(enabled: Boolean) = store.edit { it[Keys.updateChecks] = enabled }

    suspend fun setLastUpdateCheck(millis: Long) = store.edit { it[Keys.lastUpdateCheck] = millis }

    suspend fun setLibraryTiles(tiles: Boolean) = store.edit { it[Keys.libraryTiles] = tiles }

    suspend fun setThemeId(id: String) = store.edit { it[Keys.themeId] = id }

    /** Tło jednego motywu (D-50); tła pozostałych motywów zostają, jakie były. */
    suspend fun setBackground(themeId: String, backgroundId: String) = store.edit {
        val choices = BackgroundChoices.decode(it[Keys.backgrounds].orEmpty())
        it[Keys.backgrounds] = BackgroundChoices.encode(choices + (themeId to backgroundId))
    }

    /** Tła wszystkich motywów naraz, z kopii zapasowej. */
    suspend fun setBackgrounds(choices: Map<String, String>) = store.edit { it[Keys.backgrounds] = BackgroundChoices.encode(choices) }

    /** Płeć z „O mnie” (D-57); nieznany id (też z kopii) zapisuje się jako „nie podano”. */
    suspend fun setGender(id: String) = store.edit {
        it[Keys.gender] = Gender.fromId(id).id
        it.remove(Keys.legacyPhotoPeople)
    }

    /** Wzrost w „O mnie” (D-57), w granicach kreatora. */
    suspend fun setHeightCm(cm: Int) = store.edit { it[Keys.heightCm] = cm.coerceIn(HEIGHT_RANGE) }

    /** Waga w „O mnie” (D-57), do dziesiątej części kilograma. */
    suspend fun setWeightKg(kg: Double) = store.edit {
        it[Keys.weightKg] = (kg.coerceIn(WEIGHT_MIN, WEIGHT_MAX) * 10).roundToInt() / 10.0
    }

    /** Wzrost i waga naraz: kopia zapasowa i kreator; null zostawia to, co jest. */
    suspend fun setBody(heightCm: Int?, weightKg: Double?) {
        heightCm?.let { setHeightCm(it) }
        weightKg?.let { setWeightKg(it) }
    }

    suspend fun setAuthorName(name: String) = store.edit { it[Keys.authorName] = name.trim().take(60) }

    suspend fun setReminderEnabled(enabled: Boolean) = store.edit { it[Keys.reminderEnabled] = enabled }

    suspend fun setReminderTime(hour: Int, minute: Int) = store.edit {
        it[Keys.reminderHour] = hour.coerceIn(0, 23)
        it[Keys.reminderMinute] = minute.coerceIn(0, 59)
    }

    companion object {
        /** Granice wzrostu i wagi: te same co suwaki kreatora i „O mnie” (D-57). */
        val HEIGHT_RANGE = 140..210
        const val WEIGHT_MIN = 40.0
        const val WEIGHT_MAX = 150.0
    }
}
