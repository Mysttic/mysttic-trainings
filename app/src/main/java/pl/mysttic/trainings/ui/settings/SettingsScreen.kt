package pl.mysttic.trainings.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.BuildConfig
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.reminder.ReminderSchedule
import pl.mysttic.trainings.domain.update.DatabaseOffer
import pl.mysttic.trainings.domain.update.DatabaseUpdate
import pl.mysttic.trainings.domain.update.UpdateCheck
import pl.mysttic.trainings.ui.adaptive.centeredReadable
import pl.mysttic.trainings.ui.common.AppSwitch
import pl.mysttic.trainings.ui.common.DifficultySelector
import pl.mysttic.trainings.ui.common.FilterPill
import pl.mysttic.trainings.ui.common.InfoCard
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.common.SectionTitle
import pl.mysttic.trainings.ui.common.SettingRow
import pl.mysttic.trainings.ui.common.TextInput
import androidx.compose.runtime.saveable.rememberSaveable
import pl.mysttic.trainings.ui.theme.AppBackground
import pl.mysttic.trainings.ui.theme.AppTheme
import pl.mysttic.trainings.ui.theme.Dimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }
    val healthGranted by viewModel.healthGranted.collectAsStateWithLifecycle()

    // Kontrakt Health Connect sam obsługuje okno zgody; po powrocie odświeżamy stan.
    val healthPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refreshHealthPermission()
    }
    LaunchedEffect(Unit) { viewModel.refreshHealthPermission() }

    val update by viewModel.update.collectAsStateWithLifecycle()
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

    Column(
        modifier = Modifier
            .centeredReadable()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.sectionGap),
    ) {
        Text("Ustawienia", style = MaterialTheme.typography.headlineMedium)

        SectionTitle("Poziom trudności")
        Text(
            text = "Skaluje powtórzenia, czas i przerwy we wszystkich treningach; początkujący dostaje łatwiejsze warianty, zaawansowany trudniejsze. Zmienisz go też w trakcie treningu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        DifficultySelector(selected = settings.difficulty, onSelect = viewModel::setDifficulty)

        SectionTitle("Mój sprzęt")
        Text(
            text = "Zaznacz, co masz w domu. Biblioteka i treningi pokażą, które ćwiczenia są wykonalne, a dla reszty zaproponują zamienniki.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Equipment.entries.forEach { eq ->
            val checked = eq in settings.ownedEquipment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleEquipment(eq) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { viewModel.toggleEquipment(eq) })
                Text(eq.labelPl)
            }
        }

        SectionTitle("Wygląd")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTheme.entries.forEach { theme ->
                FilterPill(
                    text = theme.labelPl,
                    selected = settings.themeId == theme.id,
                    onClick = { viewModel.setTheme(theme.id) },
                )
            }
        }
        val theme = AppTheme.fromId(settings.themeId)
        Text(
            text = theme.opisPl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        // Tło motywu (D-50): warianty tylko tego motywu, bo każdy gradient liczony jest pod jego karty i napisy.
        // Bez miniatur, jak przy motywach (D-39): wybór przemalowuje od razu cały ekran, także ten.
        Text(
            text = "Tło",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        val background = AppBackground.resolve(theme, settings.backgrounds[theme.id])
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppBackground.of(theme).forEach { option ->
                FilterPill(
                    text = option.labelPl,
                    selected = option == background,
                    onClick = { viewModel.setBackground(theme.id, option.id) },
                )
            }
        }
        Text(
            text = "Gradient leży pod wszystkimi ekranami, a karty i napisy zostają takie same. Każdy motyw pamięta swoje tło.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        SectionTitle("Odliczanie przed startem")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 3, 5, 10).forEach { sec ->
                FilterPill(
                    text = if (sec == 0) "brak" else "$sec s",
                    selected = settings.countdownSec == sec,
                    onClick = { viewModel.setCountdown(sec) },
                )
            }
        }

        SectionTitle("Sygnały")
        SwitchRow("Dźwięk", settings.soundEnabled, viewModel::setSound, "beep 3-2-1 przed każdą serią")
        SwitchRow("Zapowiedź głosem", settings.ttsEnabled, viewModel::setTts, "nazwa następnego ćwiczenia")
        SwitchRow("Wibracja", settings.vibrationEnabled, viewModel::setVibration)
        SwitchRow("Nie gaś ekranu", settings.keepScreenOn, viewModel::setKeepScreenOn, "tylko w trakcie treningu")
        SwitchRow("Przycisk głośności", settings.volumeKeyDone, viewModel::setVolumeKeyDone, "potwierdza serię tak jak Zrobione")

        SectionTitle("Przypomnienie")
        SwitchRow(
            "Codzienne przypomnienie",
            settings.reminderEnabled,
            viewModel::setReminderEnabled,
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
                    viewModel.setReminderTime(h, m)
                    showTimePicker = false
                },
            )
        }

        if (viewModel.healthAvailable) {
            SectionTitle("Health Connect")
            SwitchRow("Zapisuj ukończone treningi", settings.healthConnectEnabled, viewModel::setHealthConnectEnabled)
            if (settings.healthConnectEnabled && !healthGranted) {
                Text(
                    text = "Brakuje zgody na zapis treningów. Bez niej aplikacja po prostu nic nie wysyła.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecondaryAction(
                    text = "Udziel zgody",
                    onClick = { healthPermission.launch(viewModel.healthPermissions) },
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp,
                )
            }
        }

        SectionTitle("Aktualizacje")
        Text(
            text = "Zainstalowana wersja: ${BuildConfig.VERSION_NAME} · baza ćwiczeń v${settings.seedVersion}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            "Pytaj o nowe wydanie",
            settings.updateChecks,
            viewModel::setUpdateChecks,
            "raz dziennie: pytanie o numer wersji aplikacji i bazy ćwiczeń na stronie projektu, bez tokenu i bez niczego o tobie",
        )
        Spacer(Modifier.height(8.dp))
        SecondaryAction(
            text = if (update.checking) "Sprawdzam…" else "Sprawdź teraz",
            onClick = { viewModel.checkUpdates() },
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
                            onClick = { viewModel.openReleasePage(context) },
                            modifier = Modifier.fillMaxWidth(),
                            height = 48.dp,
                        )
                    } else {
                        PrimaryAction(
                            text = if (update.downloading) "Pobieram…" else "Pobierz i zainstaluj",
                            onClick = viewModel::downloadAndInstall,
                            enabled = !update.downloading,
                            height = 48.dp,
                        )
                    }
                }
            }
        }
        update.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (update.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        DatabaseUpdateCard(
            offer = update.database,
            working = update.databaseWorking,
            onDownload = viewModel::downloadDatabase,
        )
        update.databaseMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (update.databaseError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(8.dp))
            SecondaryAction(
                text = if (update.databaseWorking) "Wczytuję bazę…" else "Wczytaj bazę ćwiczeń z pliku (debug)",
                onClick = { databaseFile.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp,
            )
        }

        SectionTitle("Plany")
        Text(
            text = "Imię trafia na plany, które wyślesz innym, i na raporty z przebiegu planu. Puste = bez podpisu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Lokalny tekst, a nie klucz z ustawień: zapis przycina spacje, więc spacja między imieniem a nazwiskiem
        // znikałaby w trakcie pisania.
        var author by rememberSaveable { mutableStateOf<String?>(null) }
        TextInput(
            label = "Twoje imię",
            value = author ?: settings.authorName,
            onValueChange = { text ->
                author = text.take(60)
                viewModel.setAuthorName(text.take(60))
            },
        )

        SectionTitle("Kopia zapasowa")
        Text(
            text = "Plik JSON z historią, wszystkimi planami (z autorami i notatkami do ćwiczeń), ćwiczeniami " +
                "i treningami własnymi, pamięcią trenera oraz ustawieniami. Import dokłada brakujące sesje i plany; " +
                "plany, które już masz, zostają nietknięte razem z treningami, a ustawienia, sprzęt i pamięć trenera " +
                "wracają z pliku.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryAction(
                text = "Eksportuj",
                onClick = { viewModel.clearStatus(); exportFile.launch(viewModel.suggestedFileName()) },
                modifier = Modifier.weight(1f),
                height = 48.dp,
            )
            SecondaryAction(
                text = "Importuj",
                onClick = { viewModel.clearStatus(); importFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.weight(1f),
                height = 48.dp,
            )
        }
        backupStatus?.let { status ->
            Text(
                text = status.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Baza ćwiczeń v${settings.seedVersion}. Część danych pochodzi z free-exercise-db (domena publiczna), " +
                "ilustracje są własne, generowane.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Nowsza baza ćwiczeń (D-47): liczby zmian i rozmiar z manifestu albo powód, dla którego ta wersja jej nie wczyta. */
@Composable
private fun DatabaseUpdateCard(offer: DatabaseOffer?, working: Boolean, onDownload: () -> Unit) {
    val note: @Composable (String) -> Unit = { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
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
        is DatabaseOffer.NeedsApp -> note(
            "Nowa baza ćwiczeń v${offer.version} wymaga aplikacji ${offer.minApp ?: "w nowszej wersji"}: najpierw zaktualizuj aplikację.",
        )
        is DatabaseOffer.Unreadable -> note("Wydania bazy ćwiczeń v${offer.version} nie da się teraz odczytać. Spróbuj później.")
        DatabaseOffer.UpToDate, null -> Unit
    }
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
