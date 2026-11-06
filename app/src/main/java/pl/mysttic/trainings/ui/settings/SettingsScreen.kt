package pl.mysttic.trainings.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.BuildConfig
import pl.mysttic.trainings.data.settings.UserSettings
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.reminder.ReminderSchedule
import pl.mysttic.trainings.domain.update.DatabaseOffer
import pl.mysttic.trainings.domain.update.DatabaseUpdate
import pl.mysttic.trainings.domain.update.UpdateCheck
import pl.mysttic.trainings.ui.adaptive.LocalAppLayout
import pl.mysttic.trainings.ui.adaptive.TwoPane
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.ui.common.AppIcons
import pl.mysttic.trainings.ui.common.AppSwitch
import pl.mysttic.trainings.ui.common.BackTopBar
import pl.mysttic.trainings.ui.common.CategoryDivider
import pl.mysttic.trainings.ui.common.CategoryGroup
import pl.mysttic.trainings.ui.common.CategoryRow
import pl.mysttic.trainings.ui.common.DifficultySelector
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.domain.model.Gender
import pl.mysttic.trainings.domain.plan.TrainingProfile
import pl.mysttic.trainings.data.settings.SettingsRepository
import kotlin.math.roundToInt
import pl.mysttic.trainings.ui.common.InfoCard
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.ScreenTitle
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.SectionTitle
import pl.mysttic.trainings.ui.common.SettingRow
import pl.mysttic.trainings.ui.common.TextInput
import pl.mysttic.trainings.ui.common.ValueStepper
import pl.mysttic.trainings.ui.common.formatKg
import pl.mysttic.trainings.ui.theme.AppBackground
import pl.mysttic.trainings.ui.theme.AppTheme
import pl.mysttic.trainings.ui.theme.Dimens

/**
 * Ustawienia (D-52): sześć kategorii ze streszczeniem zamiast jednej strony z dwunastoma sekcjami. Na telefonie
 * kategoria otwiera się jako podstrona tej samej zakładki (wstecz wraca do listy), na tablecie w poziomie lista
 * i otwarta kategoria stoją obok siebie. Logika zostaje w `SettingsViewModel` — ekran tylko ją rozkłada.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val healthGranted by viewModel.healthGranted.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()

    // Kontrakt Health Connect sam obsługuje okno zgody; po powrocie odświeżamy stan.
    val healthPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refreshHealthPermission()
    }
    LaunchedEffect(Unit) { viewModel.refreshHealthPermission() }
    LaunchedEffect(Unit) { viewModel.checkUpdatesIfDue() }

    // Storage Access Framework: użytkownik sam wybiera miejsce pliku, aplikacja nie potrzebuje uprawnień do dysku.
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri) else viewModel.clearStatus()
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri) else viewModel.clearStatus()
    }
    // Build debug: baza ćwiczeń z ZIP-a (`tools/baza.py paczka`) bez publikowania wydania (D-47).
    val databaseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.installDatabaseFromFile(uri)
    }

    val actions = remember(viewModel, context) {
        SettingsActions(
            setDifficulty = viewModel::setDifficulty,
            toggleEquipment = viewModel::toggleEquipment,
            setCountdown = viewModel::setCountdown,
            setSound = viewModel::setSound,
            setTts = viewModel::setTts,
            setVibration = viewModel::setVibration,
            setKeepScreenOn = viewModel::setKeepScreenOn,
            setVolumeKeyDone = viewModel::setVolumeKeyDone,
            setTheme = viewModel::setTheme,
            setBackground = { themeId, backgroundId -> viewModel.setBackground(themeId, backgroundId) },
            setGender = { viewModel.setGender(it) },
            setHeightCm = { viewModel.setHeightCm(it) },
            setWeightKg = { viewModel.setWeightKg(it) },
            setReminderEnabled = viewModel::setReminderEnabled,
            setReminderTime = viewModel::setReminderTime,
            setHealthConnectEnabled = viewModel::setHealthConnectEnabled,
            requestHealthPermission = { healthPermission.launch(viewModel.healthPermissions) },
            setUpdateChecks = viewModel::setUpdateChecks,
            checkUpdates = { viewModel.checkUpdates() },
            downloadAndInstall = viewModel::downloadAndInstall,
            openReleasePage = { viewModel.openReleasePage(context) },
            downloadDatabase = viewModel::downloadDatabase,
            installDatabaseFromFile = { databaseFile.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            setAuthorName = viewModel::setAuthorName,
            exportBackup = { viewModel.clearStatus(); exportFile.launch(viewModel.suggestedFileName()) },
            importBackup = { viewModel.clearStatus(); importFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
        )
    }
    val signingKey by viewModel.signingKey.collectAsStateWithLifecycle()
    SettingsContent(
        state = SettingsState(
            settings = settings,
            update = update,
            backupStatus = backupStatus,
            healthAvailable = viewModel.healthAvailable,
            healthGranted = healthGranted,
            versionName = BuildConfig.VERSION_NAME,
            debug = BuildConfig.DEBUG,
            signingKey = signingKey,
        ),
        actions = actions,
    )
}

/** Wszystko, co Ustawienia pokazują; bez ViewModelu, żeby zrzut narysował ekran z fiksturą. */
data class SettingsState(
    val settings: UserSettings,
    val update: UpdateUiState = UpdateUiState(),
    val backupStatus: BackupStatus? = null,
    val healthAvailable: Boolean = false,
    val healthGranted: Boolean = false,
    val versionName: String = "",
    val debug: Boolean = false,
    /** Odcisk klucza, którym telefon podpisuje plany (D-58); null, dopóki się liczy. */
    val signingKey: String? = null,
)

/** Zmiany ustawień i akcje plikowe; domyślnie nic nie robią (zrzuty). */
class SettingsActions(
    val setDifficulty: (Difficulty) -> Unit = {},
    val toggleEquipment: (Equipment) -> Unit = {},
    val setCountdown: (Int) -> Unit = {},
    val setSound: (Boolean) -> Unit = {},
    val setTts: (Boolean) -> Unit = {},
    val setVibration: (Boolean) -> Unit = {},
    val setKeepScreenOn: (Boolean) -> Unit = {},
    val setVolumeKeyDone: (Boolean) -> Unit = {},
    val setTheme: (String) -> Unit = {},
    val setBackground: (themeId: String, backgroundId: String) -> Unit = { _, _ -> },
    val setGender: (String) -> Unit = {},
    val setHeightCm: (Int) -> Unit = {},
    val setWeightKg: (Double) -> Unit = {},
    val setReminderEnabled: (Boolean) -> Unit = {},
    val setReminderTime: (Int, Int) -> Unit = { _, _ -> },
    val setHealthConnectEnabled: (Boolean) -> Unit = {},
    val requestHealthPermission: () -> Unit = {},
    val setUpdateChecks: (Boolean) -> Unit = {},
    val checkUpdates: () -> Unit = {},
    val downloadAndInstall: () -> Unit = {},
    val openReleasePage: () -> Unit = {},
    val downloadDatabase: () -> Unit = {},
    val installDatabaseFromFile: () -> Unit = {},
    val setAuthorName: (String) -> Unit = {},
    val exportBackup: () -> Unit = {},
    val importBackup: () -> Unit = {},
)

/**
 * Lista kategorii i otwarta kategoria. [initialSection] otwiera od razu podstronę (zrzuty); w aplikacji null.
 * Wybór przetrwa obrót, a przejście z dwóch paneli do jednego zostawia otwartą kategorię jako podstronę.
 */
@Composable
fun SettingsContent(state: SettingsState, actions: SettingsActions, initialSection: SettingsSection? = null) {
    var section by rememberSaveable { mutableStateOf(initialSection) }
    if (LocalAppLayout.current.twoPane) {
        val current = section ?: SettingsSection.TRAINING
        TwoPane(
            listWidth = CATEGORIES_PANE_WIDTH,
            list = { SettingsCategories(state, selected = current, onSelect = { section = it }) },
            detail = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 48.dp, vertical = 36.dp),
                ) {
                    // Czytelna szerokość wewnątrz panelu: przełączniki nie odjeżdżają na drugi koniec tabletu.
                    Column(modifier = Modifier.widthIn(max = 600.dp)) {
                        Text(current.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                        SectionBody(current, state, actions)
                    }
                }
            },
        )
        return
    }
    val current = section
    BackHandler(enabled = current != null) { section = null }
    if (current == null) {
        SettingsCategories(state, selected = null, onSelect = { section = it })
    } else {
        Column(
            modifier = Modifier
                .centeredReadable()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
        ) {
            BackTopBar(title = current.title, onBack = { section = null })
            SectionBody(current, state, actions)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** „O mnie” osobno, potem kategorie w dwóch grupach i stopka o wersji i źródłach danych. */
@Composable
private fun SettingsCategories(state: SettingsState, selected: SettingsSection?, onSelect: (SettingsSection) -> Unit) {
    val settings = state.settings
    val twoPane = selected != null
    Column(
        modifier = Modifier
            .then(if (twoPane) Modifier.fillMaxSize() else Modifier.centeredReadable())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.sectionGap + 4.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
    ) {
        ScreenTitle("Ustawienia", modifier = Modifier.padding(bottom = 6.dp))
        SettingsSection.groups.forEach { group ->
            CategoryGroup {
                group.forEachIndexed { index, section ->
                    if (index > 0) CategoryDivider()
                    CategoryRow(
                        icon = section.icon,
                        title = section.title,
                        summary = summaryOf(section, state),
                        accent = if (section == SettingsSection.UPDATES) updatesAccent(state.update) else null,
                        onClick = { onSelect(section) },
                        selected = section == selected,
                        chevron = !twoPane,
                    )
                }
            }
        }
        Text(
            text = "mysttic-trainings ${state.versionName} · baza ćwiczeń v${settings.seedVersion}. Część danych pochodzi " +
                "z free-exercise-db (domena publiczna), ilustracje są własne, generowane.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val SettingsSection.icon: ImageVector
    get() = when (this) {
        SettingsSection.ABOUT_ME -> AppIcons.Person
        SettingsSection.TRAINING -> AppIcons.Timer
        SettingsSection.EQUIPMENT -> AppIcons.Exercises
        SettingsSection.REMINDER -> AppIcons.Bell
        SettingsSection.APPEARANCE -> AppIcons.Palette
        SettingsSection.DATA -> AppIcons.Data
        SettingsSection.UPDATES -> AppIcons.Update
    }

private fun summaryOf(section: SettingsSection, state: SettingsState): String = when (section) {
    SettingsSection.ABOUT_ME -> SettingsSummary.aboutMe(state.settings)
    SettingsSection.TRAINING -> SettingsSummary.training(state.settings)
    SettingsSection.EQUIPMENT -> SettingsSummary.equipment(state.settings.ownedEquipment)
    SettingsSection.REMINDER -> SettingsSummary.reminder(state.settings)
    SettingsSection.APPEARANCE -> SettingsSummary.appearance(state.settings)
    SettingsSection.DATA -> SettingsSummary.data(state.healthAvailable)
    SettingsSection.UPDATES -> SettingsSummary.updates(state.versionName, state.settings.seedVersion)
}

private fun updatesAccent(update: UpdateUiState): String? = SettingsSummary.updatesAccent(
    newVersion = update.release?.version,
    newDatabase = (update.database as? DatabaseOffer.Available)?.manifest?.version,
)

@Composable
private fun SectionBody(section: SettingsSection, state: SettingsState, actions: SettingsActions) {
    when (section) {
        SettingsSection.ABOUT_ME -> AboutMeSection(state.settings, state.signingKey, actions)
        SettingsSection.TRAINING -> TrainingSection(state.settings, actions)
        SettingsSection.EQUIPMENT -> EquipmentSection(state.settings, actions)
        SettingsSection.REMINDER -> ReminderSection(state.settings, actions)
        SettingsSection.APPEARANCE -> AppearanceSection(state.settings, actions)
        SettingsSection.DATA -> DataSection(state, actions)
        SettingsSection.UPDATES -> UpdatesSection(state, actions)
    }
}

/**
 * „O mnie” (D-57): imię na planach i raportach, płeć do zdjęcia dnia oraz wzrost i waga, od których startuje kreator.
 * Imię mieszkało w „Dane i kopia” (D-42); płeć zastępuje „Na zdjęciach” z „Wyglądu” (D-51), bo to cecha użytkownika,
 * a nie ustawienie tła.
 */
@Composable
private fun AboutMeSection(settings: UserSettings, signingKey: String?, actions: SettingsActions) {
    // Lokalny tekst, a nie klucz z ustawień: zapis przycina spacje, więc spacja między imieniem a nazwiskiem
    // znikałaby w trakcie pisania.
    var author by rememberSaveable { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(8.dp))
    TextInput(
        label = "Imię",
        value = author ?: settings.authorName,
        onValueChange = { text ->
            author = text.take(60)
            actions.setAuthorName(text.take(60))
        },
    )
    Hint("Podpis na planach, które wyślesz innym, i na raportach z przebiegu planu.")
    if (signingKey != null) {
        SectionTitle("Klucz podpisu")
        Text(signingKey, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Hint(
            "Telefon podpisuje nim plany, które wysyłasz. Odbiorca widzi ten sam ciąg przy imporcie: " +
                "jeśli się zgadza, plan na pewno jest od Ciebie. Nowy telefon to nowy klucz.",
        )
    }

    SectionTitle("Płeć")
    val gender = Gender.fromId(settings.gender)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Gender.entries.forEach { option ->
            FilterPill(text = option.labelPl, selected = option == gender, onClick = { actions.setGender(option.id) })
        }
    }
    Hint("Na zdjęciu dnia w tle są osoby twojej płci, a bez wyboru obie. Plan i cele od tego nie zależą.")

    Spacer(Modifier.height(Dimens.sectionGap))
    val height = settings.heightCm ?: TrainingProfile().heightCm
    val weight = settings.weightKg ?: TrainingProfile().weightKg
    val heightRange = SettingsRepository.HEIGHT_RANGE
    ValueStepper(
        label = "Wzrost",
        value = "$height cm",
        fraction = (height - heightRange.first).toFloat() / (heightRange.last - heightRange.first),
        onLess = { actions.setHeightCm(height - 1) },
        onMore = { actions.setHeightCm(height + 1) },
        onFraction = { actions.setHeightCm((heightRange.first + (heightRange.last - heightRange.first) * it).roundToInt()) },
    )
    Spacer(Modifier.height(Dimens.sectionGap))
    ValueStepper(
        label = "Waga",
        value = formatKg(weight),
        fraction = ((weight - SettingsRepository.WEIGHT_MIN) / (SettingsRepository.WEIGHT_MAX - SettingsRepository.WEIGHT_MIN)).toFloat(),
        onLess = { actions.setWeightKg(weight - 1) },
        onMore = { actions.setWeightKg(weight + 1) },
        onFraction = {
            actions.setWeightKg((SettingsRepository.WEIGHT_MIN + (SettingsRepository.WEIGHT_MAX - SettingsRepository.WEIGHT_MIN) * it).roundToInt().toDouble())
        },
        hint = if (settings.heightCm == null || settings.weightKg == null) {
            "Jeszcze nie podane. Kreator planu zacznie od tych liczb i zapisze tu to, co w nim poprawisz."
        } else {
            "Kreator planu zaczyna od tych liczb i zapisuje tu to, co w nim poprawisz."
        },
    )
}

@Composable
private fun TrainingSection(settings: UserSettings, actions: SettingsActions) {
    SectionTitle("Poziom trudności")
    DifficultySelector(selected = settings.difficulty, onSelect = actions.setDifficulty)
    Hint("Skaluje powtórzenia, czas i przerwy we wszystkich treningach; początkujący dostaje łatwiejsze warianty, " +
        "zaawansowany trudniejsze. Zmienisz go też w trakcie treningu.")

    SectionTitle("Odliczanie przed startem")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 3, 5, 10).forEach { sec ->
            FilterPill(
                text = if (sec == 0) "brak" else "$sec s",
                selected = settings.countdownSec == sec,
                onClick = { actions.setCountdown(sec) },
            )
        }
    }

    SectionTitle("Sygnały")
    SwitchRow("Dźwięk", settings.soundEnabled, actions.setSound, "beep 3-2-1 przed każdą serią")
    SwitchRow("Zapowiedź głosem", settings.ttsEnabled, actions.setTts, "nazwa następnego ćwiczenia")
    SwitchRow("Wibracja", settings.vibrationEnabled, actions.setVibration)
    SwitchRow("Nie gaś ekranu", settings.keepScreenOn, actions.setKeepScreenOn, "tylko w trakcie treningu")
    SwitchRow("Przycisk głośności", settings.volumeKeyDone, actions.setVolumeKeyDone, "potwierdza serię tak jak Zrobione")
}

/** Sprzęt domowy, a pod nim ergometry (D-44) osobno: tych kreator nie używa, są dla biblioteki i edytorów. */
@Composable
private fun EquipmentSection(settings: UserSettings, actions: SettingsActions) {
    Hint("Zaznacz, co masz w domu. Biblioteka i treningi pokażą, które ćwiczenia są wykonalne, a dla reszty zaproponują zamienniki.")
    val (ergometers, home) = Equipment.entries.partition { it.ergometer }
    Spacer(Modifier.height(8.dp))
    home.forEach { EquipmentRow(it, it in settings.ownedEquipment, actions.toggleEquipment) }
    SectionTitle("Ergometry")
    ergometers.forEach { EquipmentRow(it, it in settings.ownedEquipment, actions.toggleEquipment) }
}

@Composable
private fun EquipmentRow(equipment: Equipment, checked: Boolean, onToggle: (Equipment) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(equipment) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle(equipment) })
        Text(equipment.labelPl, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ReminderSection(settings: UserSettings, actions: SettingsActions) {
    var showTimePicker by remember { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        "Codzienne przypomnienie",
        settings.reminderEnabled,
        actions.setReminderEnabled,
        "tylko gdy tego dnia nie było jeszcze treningu",
    )
    if (settings.reminderEnabled) {
        SettingRow(
            title = "Godzina",
            value = ReminderSchedule.formatTime(settings.reminderHour, settings.reminderMinute),
            onClick = { showTimePicker = true },
        )
    }
    if (showTimePicker) {
        TimePickerDialog(
            hour = settings.reminderHour,
            minute = settings.reminderMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                actions.setReminderTime(h, m)
                showTimePicker = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(settings: UserSettings, actions: SettingsActions) {
    SectionTitle("Motyw")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTheme.entries.forEach { theme ->
            FilterPill(text = theme.labelPl, selected = settings.themeId == theme.id, onClick = { actions.setTheme(theme.id) })
        }
    }
    val theme = AppTheme.fromId(settings.themeId)
    Hint(theme.opisPl)

    // Tło motywu (D-50, D-55): zawsze zdjęcie dnia, a na nim do wyboru gradient tego motywu, bo każdy gradient
    // liczony jest pod jego karty i napisy. Bez miniatur, jak przy motywach (D-39): wybór przemalowuje od razu cały ekran.
    SectionTitle("Tło")
    val background = AppBackground.resolve(theme, settings.backgrounds[theme.id])
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppBackground.of(theme).forEach { option ->
            FilterPill(text = option.labelPl, selected = option == background, onClick = { actions.setBackground(theme.id, option.id) })
        }
    }
    Hint(
        "Pod ekranami zawsze leży zdjęcie, losowane raz dziennie w klimacie dzisiejszych treningów ze wszystkich planów; " +
            "karty są szklane, żeby prześwitywało. Gradient zabarwia zdjęcie kolorem motywu, a każdy motyw pamięta swój wybór.",
    )
}

@Composable
private fun DataSection(state: SettingsState, actions: SettingsActions) {
    val settings = state.settings
    SectionTitle("Kopia zapasowa")
    Hint(
        "Plik JSON z historią, wszystkimi planami (z autorami i notatkami do ćwiczeń), ćwiczeniami i treningami " +
            "własnymi, pamięcią trenera oraz ustawieniami. Import dokłada brakujące sesje i plany; plany, które już masz, " +
            "zostają nietknięte razem z treningami, a ustawienia, sprzęt i pamięć trenera wracają z pliku.",
        top = 0.dp,
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PrimaryAction(text = "Eksportuj", onClick = actions.exportBackup, modifier = Modifier.weight(1f), height = 48.dp)
        SecondaryAction(text = "Importuj", onClick = actions.importBackup, modifier = Modifier.weight(1f), height = 48.dp)
    }
    state.backupStatus?.let { status -> StatusText(status.message, status.error) }

    if (state.healthAvailable) {
        SectionTitle("Health Connect")
        SwitchRow("Zapisuj ukończone treningi", settings.healthConnectEnabled, actions.setHealthConnectEnabled)
        if (settings.healthConnectEnabled && !state.healthGranted) {
            Hint("Brakuje zgody na zapis treningów. Bez niej aplikacja po prostu nic nie wysyła.", top = 0.dp)
            Spacer(Modifier.height(8.dp))
            SecondaryAction(text = "Udziel zgody", onClick = actions.requestHealthPermission, modifier = Modifier.fillMaxWidth(), height = 48.dp)
        }
    }

}

@Composable
private fun UpdatesSection(state: SettingsState, actions: SettingsActions) {
    val settings = state.settings
    val update = state.update
    Hint("Zainstalowana wersja: ${state.versionName} · baza ćwiczeń v${settings.seedVersion}")
    SwitchRow(
        "Pytaj o nowe wydanie",
        settings.updateChecks,
        actions.setUpdateChecks,
        "raz dziennie: pytanie o numer wersji aplikacji i bazy ćwiczeń na stronie projektu, bez tokenu i bez niczego o tobie",
    )
    Spacer(Modifier.height(8.dp))
    SecondaryAction(
        text = if (update.checking) "Sprawdzam…" else "Sprawdź teraz",
        onClick = actions.checkUpdates,
        modifier = Modifier.fillMaxWidth(),
        height = 48.dp,
    )
    update.release?.let { release ->
        Spacer(Modifier.height(8.dp))
        InfoCard {
            Column {
                Text("Jest wersja ${release.version}", style = MaterialTheme.typography.titleLarge)
                val size = UpdateCheck.formatSize(release.assetSize)
                if (size.isNotEmpty()) {
                    Text(
                        text = "Plik do pobrania: $size",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(release.notes, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                if (release.assetUrl == null) {
                    // Wydanie bez pliku dla Androida i tak niesie numer i adres strony (D-33).
                    Text(
                        text = "To wydanie nie ma pliku dla Androida. Pobierzesz je ze strony projektu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryAction(
                        text = "Otwórz stronę projektu",
                        onClick = actions.openReleasePage,
                        modifier = Modifier.fillMaxWidth(),
                        height = 48.dp,
                    )
                } else {
                    PrimaryAction(
                        text = if (update.downloading) "Pobieram…" else "Pobierz i zainstaluj",
                        onClick = actions.downloadAndInstall,
                        enabled = !update.downloading,
                        height = 48.dp,
                    )
                }
            }
        }
    }
    update.message?.let { StatusText(it, update.error) }
    DatabaseUpdateCard(offer = update.database, working = update.databaseWorking, onDownload = actions.downloadDatabase)
    update.databaseMessage?.let { StatusText(it, update.databaseError) }
    if (state.debug) {
        Spacer(Modifier.height(8.dp))
        SecondaryAction(
            text = if (update.databaseWorking) "Wczytuję bazę…" else "Wczytaj bazę ćwiczeń z pliku (debug)",
            onClick = actions.installDatabaseFromFile,
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp,
        )
    }
}

/** Nowsza baza ćwiczeń (D-47): liczby zmian i rozmiar z manifestu albo powód, dla którego ta wersja jej nie wczyta. */
@Composable
private fun DatabaseUpdateCard(offer: DatabaseOffer?, working: Boolean, onDownload: () -> Unit) {
    when (offer) {
        is DatabaseOffer.Available -> {
            Spacer(Modifier.height(8.dp))
            InfoCard {
                Column {
                    Text("Jest baza ćwiczeń v${offer.manifest.version}", style = MaterialTheme.typography.titleLarge)
                    val details = listOf(DatabaseUpdate.changesText(offer.manifest), UpdateCheck.formatSize(offer.manifest.size))
                        .filter { it.isNotEmpty() }
                        .joinToString(" · ")
                    if (details.isNotEmpty()) {
                        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "Wchodzi bez nowej wersji aplikacji; historia i plany zostają.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryAction(
                        text = if (working) "Pobieram…" else "Pobierz bazę",
                        onClick = onDownload,
                        enabled = !working,
                        height = 48.dp,
                    )
                }
            }
        }
        is DatabaseOffer.NeedsApp -> Hint(
            "Nowa baza ćwiczeń v${offer.version} wymaga aplikacji ${offer.minApp ?: "w nowszej wersji"}: najpierw zaktualizuj aplikację.",
        )
        is DatabaseOffer.Unreadable -> Hint("Wydania bazy ćwiczeń v${offer.version} nie da się teraz odczytać. Spróbuj później.")
        DatabaseOffer.UpToDate, null -> Unit
    }
}

/** Jedno zdanie wyjaśnienia pod ustawieniem: mały tekst w onSurfaceVariant. */
@Composable
private fun Hint(text: String, top: androidx.compose.ui.unit.Dp = 8.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top),
    )
}

/** Wynik akcji (eksport, sprawdzenie wydania): błąd w kolorze błędu, sukces w ogniu. */
@Composable
private fun StatusText(text: String, error: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null) {
    SettingRow(
        title = label,
        description = description,
        onClick = { onChange(!checked) },
        trailing = { AppSwitch(checked = checked, onCheckedChange = onChange) },
    )
}

/** Wybór godziny przypomnienia — zegar z Material 3 w zwykłym dialogu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("O której przypomnieć?", style = MaterialTheme.typography.titleLarge) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Ustaw") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

/** Szerokość listy kategorii obok otwartej kategorii (tablet). */
private val CATEGORIES_PANE_WIDTH = 400.dp
