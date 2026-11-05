package pl.mysttic.trainings.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.mysttic.trainings.domain.model.Rpe
import pl.mysttic.trainings.ui.theme.Dimens
import pl.mysttic.trainings.ui.theme.LocalAppBackground
import pl.mysttic.trainings.ui.theme.Pill
import pl.mysttic.trainings.ui.theme.StatValue
import pl.mysttic.trainings.ui.theme.StepperValue
import pl.mysttic.trainings.ui.theme.Overline as OverlineStyle

/*
 * Komponenty systemu „Ogień i Krew” (D-32). Trzy reguły, których trzymają się wszystkie ekrany:
 * wypełnienie kolorem primary znaczy akcję, tertiary niesie informację i nigdy nie trafia na przycisk,
 * a zrobione rysujemy ogniem, resztki kolorem outlineVariant.
 * Elewacje są zerowe: warstwy rozdziela stopień surfaceContainer i linia włosowa.
 */

/**
 * Scaffold ekranu na tle aplikacji (D-50): bez własnego wypełnienia, bo zasłoniłby tło, które maluje korzeń
 * nawigacji (jednolite albo gradient). Kolor treści idzie wprost: przezroczysty kontener nie ma pary w schemacie,
 * więc `contentColorFor` oddałby domyślną czerń zamiast `onSurface`.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}

/**
 * Wypełnienie karty leżącej na tle ekranu: `surfaceContainer`, a nad zdjęciem dnia (D-51) szkło, czyli `onSurface`
 * z alfą motywu, przez które zdjęcie prześwituje. Tylko karty: paski, menu, okna i miniatury zostają pełne, bo pod
 * nimi przewija się treść albo inny ekran. Kontrast tekstu na szkle pilnuje `ThemeContractTest` dla całego pasma zdjęcia.
 */
@Composable
fun cardColor(): Color {
    val band = LocalAppBackground.current.band ?: return MaterialTheme.colorScheme.surfaceContainer
    return MaterialTheme.colorScheme.onSurface.copy(alpha = band.glass)
}

/** Pasek tytułu nad tłem aplikacji (D-50): bez wypełnienia, jak `ScreenScaffold`, tytuł i ikony bez zmian. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun screenTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)

/** Nagłówek sekcji, etykieta stanu playera, data: wersaliki w kolorze żaru. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.tertiary) {
    Text(text = text.uppercase(), style = OverlineStyle, color = color, modifier = modifier)
}

/** Linia włosowa 1 dp: jedyny sposób na rozdzielenie planów obok stopnia surfaceContainer. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.hairline)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    )
}

/** Liczba z etykietą. `accent` = żar, zarezerwowany dla serii dni z rzędu. */
@Composable
fun StatCell(value: String, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            text = value,
            style = StatValue,
            color = if (accent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Trzy statystyki w rzędzie, między liniami włosowymi, rozdzielone pionową kreską. */
@Composable
fun StatRow(modifier: Modifier = Modifier, cells: List<StatCellData>) {
    Column(modifier = modifier.fillMaxWidth()) {
        Hairline()
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            cells.forEachIndexed { index, cell ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(Dimens.hairline)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
                    )
                }
                StatCell(
                    value = cell.value,
                    label = cell.label,
                    accent = cell.accent,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (index > 0) 16.dp else 0.dp),
                )
            }
        }
        Hairline()
    }
}

data class StatCellData(val value: String, val label: String, val accent: Boolean = false)

/**
 * Pasek segmentowy: zrobione ogniem, bieżący żarem, resztki kolorem outlineVariant.
 * Ta sama logika co w słupkach wykresu — „wypalone kontra jeszcze nie”.
 */
@Composable
fun SegmentBar(done: Int, total: Int, modifier: Modifier = Modifier, current: Boolean = true) {
    if (total <= 0) return
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            val color = when {
                index < done -> MaterialTheme.colorScheme.primary
                index == done && current -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.progressTrack)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

/** Ciągły pasek postępu; ten sam podział kolorów co [SegmentBar]. */
@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier, height: Dp = Dimens.progressTrack) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Wielki przycisk akcji: Start, „Zrobione”, „Ułóż plan”. Wypełnienie ogniem = akcja. */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.primaryButton,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(22.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Przycisk drugorzędny: obwódka, treść w onSurface. Nie dostaje ognia, bo to nie jest główna akcja. */
@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.secondaryButton,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Karta dnia: jedyna karta na „Dziś” i jedyne wyróżnienie w kolejce planu.
 * `weekDone`/`weekTotal` rysują pasek postępu tygodnia nad przyciskiem Start, a [top] (D-52) to miejsce na pasek
 * miniatur ćwiczeń nad nazwą — karta przestaje być samym tekstem.
 */
@Composable
fun WorkoutCard(
    name: String,
    counter: String?,
    meta: String,
    modifier: Modifier = Modifier,
    weekDone: Int = 0,
    weekTotal: Int = 0,
    top: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cardColor(),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(if (top != null) Dimens.cardPreviewInner else Dimens.cardInner),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            top?.invoke()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (counter != null) {
                    Text(
                        text = counter,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (weekTotal > 0) SegmentBar(done = weekDone, total = weekTotal)
            content()
        }
    }
}

/**
 * Propozycja podbicia celów. `boxed = false` na „Dziś” (sam pasek żaru od lewej),
 * `boxed = true` w szczegółach treningu (karta z przyciskiem „Zastosuj”).
 */
@Composable
fun ProgressionCard(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    boxed: Boolean = false,
) {
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(Dimens.nowAccent)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (boxed) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    if (boxed) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = cardColor(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Box(Modifier.padding(14.dp)) { body() }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            body()
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .padding(start = Dimens.nowAccent + 14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(vertical = 6.dp),
            )
        }
    }
}

/**
 * Wiersz ćwiczenia: miniatura, nazwa i jedna linia szczegółów. Ten sam komponent w treningu i w bibliotece,
 * bez separatorów — listę rozdzielają odstępy, nie kreski.
 */
@Composable
fun ExerciseRow(
    name: String,
    secondary: String,
    imagePath: String?,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    warning: String? = null,
    large: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseImage(
            assetPath = imagePath,
            modifier = Modifier.size(if (large) Dimens.thumbLarge else Dimens.thumbSmall),
            contentDescription = name,
            shape = RoundedCornerShape(if (large) 16.dp else 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (warning != null) {
                Text(text = warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Nagłówek bloku: nazwa w żarze po lewej, parametr bloku po prawej, nad tym linia i odstęp. */
@Composable
fun BlockHeader(title: String, param: String, modifier: Modifier = Modifier, first: Boolean = false) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!first) {
            Spacer(Modifier.height(Dimens.sectionGap))
            Hairline()
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.sectionGap, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Overline(title, modifier = Modifier.weight(1f))
            if (param.isNotBlank()) {
                Text(param, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Pigułka filtra: wybrana wypełniona ogniem, reszta na obwódce. `trailingIcon` (np. strzałka) dla pigułki, która coś rozwija.
 * Niewybrana jest przezroczysta, jak `SecondaryAction`: na gradiencie tła (D-50) wypełnienie `surface` byłoby łatą.
 */
@Composable
fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = Pill,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (selected) null else BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = if (trailingIcon == null) 14.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1)
            if (trailingIcon != null) {
                Icon(imageVector = trailingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Pole szukania: bez obwódki, na stopniu surfaceContainerLow. [leadingIcon] i [trailingIcon] (D-52): lupa i przycisk
 * filtrów w samym polu, zamiast osobnego rzędu pigułek nad listą.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** Przełącznik z palety systemu: włączony ogniem, wyłączony na glinie. */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSecondaryContainer,
            uncheckedTrackColor = MaterialTheme.colorScheme.secondaryContainer,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * Wiersz ustawienia: tytuł, opcjonalny opis pod nim i element po prawej (przełącznik albo wartość).
 * Wartość tekstowa idzie w żarze, bo to informacja, a nie akcja.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        trailing?.invoke()
    }
}

/** Karta informacyjna: stopień surfaceContainer, promień 20, bez cienia. */
@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cardColor(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/**
 * Ocena wysiłku po treningu (D-46): pigułki od 6 do 10 z podpisem słownym i podpowiedzią o zapasie powtórzeń.
 * Ta sama w podsumowaniu playera i w szczegółach sesji; ponowne tapnięcie wybranej czyści ocenę.
 */
@Composable
fun RpePicker(value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rpe.RANGE.forEach { option ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    FilterPill(
                        text = option.toString(),
                        selected = value == option,
                        onClick = { onChange(if (value == option) null else option) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = Rpe.label(option),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (value == option) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(Rpe.HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Karta wyboru w kreatorze: wybrana wypełniona ogniem, reszta na stopniu surfaceContainer.
 * [meta] to krótka informacja pod opisem (trudność gotowego programu, D-46): tertiary, bo to informacja.
 */
@Composable
fun ChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else cardColor(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            meta?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/**
 * Suwak z dużymi „−” i „+”: na telefonie w kuchni dosuwanie jest lepsze niż wpisywanie liczby.
 * Tor pokazuje położenie w dozwolonym zakresie, sam nie przyjmuje przeciągnięć.
 */
@Composable
fun ValueStepper(
    label: String,
    value: String,
    fraction: Float,
    onLess: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    /** Przeciąganie po pasku ustawia wartość wprost (0..1 zakresu); bez tego pasek tylko pokazuje. */
    onFraction: ((Float) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Overline(label)
        Spacer(Modifier.height(8.dp))
        Text(text = value, style = StepperValue, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StepperKey("−", onLess)
            if (onFraction != null) {
                DragTrack(fraction = fraction, onFraction = onFraction, modifier = Modifier.weight(1f))
            } else {
                ProgressTrack(fraction = fraction, modifier = Modifier.weight(1f))
            }
            StepperKey("+", onMore)
        }
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Pasek wartości z uchwytem: tapnięcie albo przeciągnięcie ustawia wartość z pozycji palca.
 * Pole dotyku ma pełną wysokość [Dimens.touchMin], choć sam pasek jest cienki.
 */
@Composable
private fun DragTrack(fraction: Float, onFraction: (Float) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(onFraction)
    BoxWithConstraints(
        modifier = modifier
            .height(Dimens.touchMin)
            .pointerInput(Unit) {
                detectTapGestures { offset -> current((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    current((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        ProgressTrack(fraction = fraction)
        Box(
            modifier = Modifier
                .offset(x = (maxWidth - Dimens.stepperThumb) * fraction.coerceIn(0f, 1f))
                .size(Dimens.stepperThumb)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * Klawisz „−” / „+”: tapnięcie to jeden krok, przytrzymanie powtarza krok co [STEPPER_REPEAT_MS]
 * po zwłoce [STEPPER_HOLD_DELAY_MS] — bez tego 30 kg różnicy to 30 tapnięć.
 */
@Composable
private fun StepperKey(text: String, onStep: () -> Unit, enabled: Boolean = true) {
    val current by rememberUpdatedState(onStep)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .indication(interaction, LocalIndication.current)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick {
                    if (enabled) current()
                    enabled
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    detectTapGestures(
                        onPress = { offset ->
                            val press = PressInteraction.Press(offset)
                            interaction.emit(press)
                            current()
                            val repeater = launch {
                                delay(STEPPER_HOLD_DELAY_MS)
                                while (isActive) {
                                    current()
                                    delay(STEPPER_REPEAT_MS)
                                }
                            }
                            val released = tryAwaitRelease()
                            repeater.cancel()
                            interaction.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

private const val STEPPER_HOLD_DELAY_MS = 400L
private const val STEPPER_REPEAT_MS = 70L

/** Pasek kroków kreatora: zrobione ogniem, resztki w outlineVariant. */
@Composable
fun StepProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(Pill)
                    .background(
                        if (index < step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

/** Cztery statystyki w siatce 2 x 2, rozdzielone liniami włosowymi. Podsumowanie zamkniętego cyklu. */
@Composable
fun StatGrid(cells: List<StatCellData>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        cells.chunked(2).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Hairline()
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { index, cell ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(Dimens.hairline)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
                        )
                    }
                    StatCell(
                        value = cell.value,
                        label = cell.label,
                        accent = cell.accent,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (index > 0) 16.dp else 0.dp),
                    )
                }
            }
        }
    }
}

/** Pole tekstowe z etykietą nad nim: etykieta w żarze, pole bez obwódki na stopniu surfaceContainerLow. */
@Composable
fun TextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Overline(label)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/** Liczba z kwadratowymi „−” i „+”: ten sam gest co w kreatorze, tylko w jednym wierszu. */
@Composable
fun InlineStepper(
    label: String,
    value: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    lessEnabled: Boolean = true,
    moreEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        StepperKey("−", onLess, enabled = lessEnabled)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.widthIn(min = 56.dp),
            textAlign = TextAlign.Center,
        )
        StepperKey("+", onMore, enabled = moreEnabled)
    }
}

/**
 * Wielka liczba playera. Rozmiar z projektu jest górną granicą: przy powiększonej czcionce systemowej
 * albo dwóch cyfrach tekst schodzi w dół tak, żeby zmieścić się w jednej linii, zamiast dać się przyciąć.
 */
@Composable
fun PlayerNumber(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 72.sp, maxFontSize = style.fontSize, stepSize = 4.sp),
    )
}
