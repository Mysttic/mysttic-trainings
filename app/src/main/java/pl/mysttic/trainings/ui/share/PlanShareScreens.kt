package pl.mysttic.trainings.ui.share

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.MainActivity
import pl.mysttic.trainings.domain.engine.Announcer
import pl.mysttic.trainings.domain.plan.share.PlanFile
import pl.mysttic.trainings.ui.adaptive.ReadableBox
import pl.mysttic.trainings.ui.common.InfoCard
import pl.mysttic.trainings.ui.common.Overline
import pl.mysttic.trainings.ui.common.PrimaryAction
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.theme.Dimens

/**
 * Wysłanie planu (D-42). Plik idzie przez systemowe okno udostępniania (Messenger, Gmail, Pliki) albo do miejsca
 * wybranego w SAF. Ekran mówi wprost, co jest w pliku, a czego nie ma — plan idzie do innej osoby.
 */
@Composable
fun PlanShareScreen(onBack: () -> Unit, viewModel: PlanShareViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PlanFile.MIME)) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }
    LaunchedEffect(state.shareUri) {
        val uri = state.shareUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PlanFile.MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Plan treningowy: ${state.name}")
            putExtra(Intent.EXTRA_TEXT, "Plan „${state.name}”. Otwórz plik w aplikacji Mysttic Trainings.")
            clipData = ClipData.newRawUri(state.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Wyślij plan").apply {
            // Aplikacja sama łapie pliki planów (SEND application/json), ale wysłanie planu do siebie nic nie da.
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
        }
        context.startActivity(chooser)
        viewModel.consumeShare()
    }

    ReadableBox {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Wyślij plan", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "${state.name} · ${daysLabel(state.days)}, ${trainingsLabel(state.trainingDays)}" +
                    if (state.author.isNotBlank()) "\nPodpis: ${state.author}" else "\nBez podpisu — imię ustawisz w Ustawieniach.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoCard {
                Overline("Co jest w pliku")
                Text(
                    "Nazwa, opis, dni, treningi z seriami, powtórzeniami, czasem, przerwami, ciężarem i wskazówkami " +
                        "oraz opisy użytych ćwiczeń.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Overline("Czego nie ma")
                Text(
                    "Twojego wzrostu i wagi, historii treningów, notatek z sesji ani pamięci trenera.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "Odbiorca dostanie plan dokładnie taki, jak go ułożyłeś — bez skalowania do swojego poziomu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryAction("Udostępnij", onClick = viewModel::prepareShare, enabled = !state.busy)
            SecondaryAction(
                "Zapisz do pliku",
                onClick = { saveFile.launch(state.fileName) },
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp,
            )
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Podgląd planu z pliku (D-42): najpierw widać, co przyszło i od kogo, braki sprzętu i ostrzeżenia; do bazy
 * trafia dopiero po „Dodaj do planów”, jako plan nieaktywny.
 */
@Composable
fun PlanImportScreen(
    onBack: () -> Unit,
    onImported: (String) -> Unit,
    viewModel: PlanImportViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    // Pliki z komunikatorów bywają opisane jako octet-stream albo text/plain, więc wybór nie filtruje po typie —
    // treść i tak sprawdza walidator.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> viewModel.picked(uri) }
    // Flaga z ViewModelu, nie z `state`: po odtworzeniu Activity wynik wyboru przychodzi, zanim `state` go zobaczy.
    LaunchedEffect(state.picking) {
        if (state.picking && viewModel.startPicking()) {
            pickFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
    }
    LaunchedEffect(state.importedId) { state.importedId?.let(onImported) }

    ReadableBox {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Plan z pliku", style = MaterialTheme.typography.headlineMedium)

            state.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                SecondaryAction("Wybierz inny plik", onClick = viewModel::pickAgain, modifier = Modifier.fillMaxWidth(), height = 48.dp)
            }
            if (state.loading) {
                Text("Czytam plik…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val preview = state.preview ?: return@Column
            val plan = preview.plan
            Text(plan.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = listOfNotNull(
                    plan.author.takeIf { it.isNotBlank() }?.let { "Od: $it" },
                    "${daysLabel(plan.days.size)} · ${trainingsLabel(plan.trainingDays)} · pisany dla poziomu: ${plan.level.labelPl.lowercase()}",
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (plan.description.isNotBlank()) Text(plan.description, style = MaterialTheme.typography.bodyLarge)

            InfoCard {
                Overline("Treningi")
                plan.workouts.forEach { workout ->
                    Text(
                        text = workout.name + ": " + workout.exerciseIds.joinToString { state.exerciseNames[it] ?: it },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            preview.alreadyHave?.let {
                Text(
                    "Masz już ten plan („$it”). Dodanie zrobi drugą, osobną kopię.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (state.missingEquipment.isNotEmpty()) {
                Text(
                    "Plan wymaga sprzętu, którego nie zaznaczyłeś: " + state.missingEquipment.joinToString { it.labelPl.lowercase() } + ".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (plan.newExercises.isNotEmpty()) {
                Text(
                    "Nowe ćwiczenia w bibliotece: " + plan.newExercises.joinToString { it.namePl } +
                        if (preview.newerSeed) ". Ilustracje dojdą z aktualizacją aplikacji." else ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            plan.warnings.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Plan wejdzie dokładnie taki, jak go ułożył autor: bez skalowania poziomem i korekt trenera. " +
                    "Po dodaniu czeka na liście planów — aktywujesz go, kiedy zechcesz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryAction("Dodaj do planów", onClick = viewModel::import, enabled = !state.importing)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun daysLabel(n: Int): String = "$n ${Announcer.plural(n, "dzień", "dni", "dni")}"

private fun trainingsLabel(n: Int): String = "$n ${Announcer.plural(n, "trening", "treningi", "treningów")}"
