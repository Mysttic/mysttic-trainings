package pl.mysttic.trainings.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.TargetKind
import pl.mysttic.trainings.ui.adaptive.ReadableBox
import pl.mysttic.trainings.ui.common.ChartLegendRow
import pl.mysttic.trainings.ui.common.RecordLineChart
import pl.mysttic.trainings.ui.common.EmptyState
import pl.mysttic.trainings.ui.common.SecondaryAction
import pl.mysttic.trainings.ui.theme.Dimens
import pl.mysttic.trainings.ui.common.EquipmentTags
import pl.mysttic.trainings.ui.common.ExerciseImagePair
import pl.mysttic.trainings.ui.common.SectionTitle
import pl.mysttic.trainings.ui.common.SettingRow
import pl.mysttic.trainings.ui.common.AppSwitch
import pl.mysttic.trainings.domain.coach.Coach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exercise = state.exercise

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        if (exercise == null) {
            if (!state.loading) EmptyState("Nie znaleziono ćwiczenia.", Modifier.padding(padding))
            return@Scaffold
        }
        // Na tablecie kolumna 640 dp: ilustracja na pełną szerokość zajmowała cały pierwszy ekran i była rozmyta.
        ReadableBox(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
                }
                ExerciseImagePair(exercise)
                Spacer(Modifier.height(Dimens.sectionGap))
                Text(exercise.namePl, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${exercise.level.labelPl} · ${exercise.category.labelPl} · " +
                        (if (exercise.kind == ExerciseKind.TIME) "na czas" else "na powtórzenia"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Wycofane z bazy (D-47) otwiera się już tylko z historii i planów: mówimy wprost, czemu nie ma go w bibliotece.
                exercise.retiredAt?.let { date ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Wycofane z bazy ${plDate(date)}: zostaje w historii i planach, ale nie ma go " +
                            "w bibliotece, kreatorze ani zamiennikach.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                EquipmentTags(exercise.equipment, state.ownedEquipment)

                SectionTitle("Mięśnie")
                Text("Główne: " + exercise.primaryMuscles.joinToString { it.labelPl })
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    Text(
                        "Pomocnicze: " + exercise.secondaryMuscles.joinToString { it.labelPl },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionTitle("Jak wykonać")
                exercise.instructionsPl.forEachIndexed { i, step ->
                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = "${i + 1}",
                            modifier = Modifier.padding(end = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(step, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                SectionTitle("Twoje wyniki")
                RecordLineChart(state.history)
                state.personalBest?.let { best ->
                    val unit = if (best.targetKind == TargetKind.SECONDS) "s" else "powt."
                    ChartLegendRow("Rekord w serii", "${best.best} $unit")
                    state.history.lastOrNull()?.let { last ->
                        ChartLegendRow("Ostatnio", "${last.best} $unit")
                    }
                    ChartLegendRow("Wykonań", "${state.history.size}")
                }

                SectionTitle("Trener")
                val tuning = state.tuning
                val excluded = tuning?.excluded == true
                SettingRow(
                    title = "Nie proponuj w planach",
                    description = if (excluded) {
                        "Wykluczone: w aktywnym cyklu dostało zamiennik, kolejne plany je pominą."
                    } else {
                        "Wyklucza z kreatora i od razu podmienia w aktywnym cyklu."
                    },
                    onClick = { viewModel.setExcluded(!excluded) },
                    trailing = { AppSwitch(checked = excluded, onCheckedChange = viewModel::setExcluded) },
                )
                val correction = Coach.deltaLabel(tuning, exercise.defaultTargetKind)
                if (correction != null) {
                    SettingRow(
                        title = "Korekta celu: $correction",
                        description = "Z opinii w podsumowaniach; działa na każdą kolejną serię tego ćwiczenia.",
                        value = "Wyzeruj",
                        onClick = viewModel::resetTuning,
                    )
                } else {
                    Text(
                        text = "Bez korekty. Po treningu powiedz „Więcej” albo „Mniej”, a cel przesunie się w kolejnych seriach.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.easier != null || state.harder != null) {
                    SectionTitle("Warianty")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        state.easier?.let { e ->
                            SecondaryAction("Łatwiej: ${e.namePl}", onClick = { onOpenExercise(e.id) }, modifier = Modifier.weight(1f))
                        }
                        state.harder?.let { h ->
                            SecondaryAction("Trudniej: ${h.namePl}", onClick = { onOpenExercise(h.id) }, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (state.missingEquipment.isNotEmpty()) {
                    SectionTitle("Brakuje: " + state.missingEquipment.joinToString { it.labelPl })
                    if (state.substitutes.isEmpty()) {
                        Text("Brak zamiennika na tę grupę mięśni z Twoim sprzętem.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Zamienniki z Twoim sprzętem:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.substitutes.forEach { s ->
                                SecondaryAction(s.namePl, onClick = { onOpenExercise(s.id) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Data z bazy (`RRRR-MM-DD`) tak, jak czyta się ją po polsku: 28.10.2026. */
private fun plDate(iso: String): String = iso.split("-").reversed().joinToString(".")
