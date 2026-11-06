package pl.mysttic.trainings.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.mysttic.trainings.ui.theme.Dimens
import pl.mysttic.trainings.ui.theme.Pill

/*
 * Elementy struktury z nowego interfejsu (D-52): wszystko, co pozwala schować szczegół zamiast go usuwać.
 * Zwijany wiersz, wiersz kategorii z ikoną, pasek powrotu, znaczniki i przyciski ikonowe. Reguły „Ognia i Krwi”
 * (D-32) bez zmian: ogień tylko na akcji, żar tylko na informacji, warstwy z surfaceContainer i linii włosowej.
 */

/** Tytuł ekranu głównego: ta sama wielkość na każdej zakładce, opcjonalne akcje po prawej. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Pasek ekranu podrzędnego: strzałka wstecz i tytuł. Bez `onBack` (panel szczegółu na tablecie) zostaje sam tytuł. */
@Composable
fun BackTopBar(title: String?, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.touchMin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(AppIcons.Back, contentDescription = "Wstecz", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).offset(x = if (onBack != null) (-8).dp else 0.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

/**
 * Zwijany wiersz: tytuł, jedno zdanie streszczenia i strzałka. Szczegół (wyniki, warianty, trener) czeka pod spodem,
 * a zwinięty wiersz i tak mówi najważniejsze. Stan rozwinięcia trzyma wołający, żeby przetrwał obrót.
 */
@Composable
fun DisclosureRow(
    title: String,
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "strzałka")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = if (expanded) "Zwiń" else "Rozwiń", onClick = onToggle)
                .semantics { stateDescription = if (expanded) "rozwinięte" else "zwinięte" }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = AppIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
        Hairline()
    }
}

/** Grupa wierszy kategorii jako jedna karta: `cardColor()`, więc nad zdjęciem dnia szklana (D-51). */
@Composable
fun CategoryGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cardColor(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(content = content)
    }
}

/** Linia włosowa między wierszami kategorii, wcięta do tekstu (za kafelkiem ikony). */
@Composable
fun CategoryDivider() {
    Hairline(modifier = Modifier.padding(start = 70.dp))
}

/**
 * Wiersz kategorii: ikona na kafelku, tytuł i streszczenie tego, co jest w środku. [accent] to dopisek w żarze
 * („jest nowsza wersja”) — informacja, nie akcja. [selected] podświetla kategorię otwartą w panelu obok (tablet).
 */
@Composable
fun CategoryRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: String? = null,
    selected: Boolean = false,
    chevron: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Kartę maluje `CategoryGroup`; wiersz ma własne tło tylko jako zaznaczenie obok panelu (tablet).
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            // Na liście (telefon) wiersz otwiera podstronę; obok panelu (tablet) jest kartą z zaznaczeniem.
            .then(
                if (chevron) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (accent != null) {
                Text(accent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        if (chevron) {
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Znacznik bez akcji: partia mięśniowa, sprzęt. [strong] = główna partia (onSurface), reszta przygaszona;
 * [outlined] = sprzęt; [error] = sprzęt, którego nie masz.
 */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier, strong: Boolean = false, outlined: Boolean = false, error: Boolean = false) {
    val color = when {
        error -> MaterialTheme.colorScheme.error
        strong -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.height(28.dp),
        shape = Pill,
        // Jak `FilterPill` (D-50): obrysowany jest przezroczysty, pełny ma kolor karty, więc na gradiencie nie robi łaty.
        color = if (outlined) Color.Transparent else cardColor(),
        border = if (outlined) BorderStroke(Dimens.hairline, if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline) else null,
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
    }
}

/**
 * Cichy przycisk z ikoną nad podpisem: drugi rząd playera (Wstecz, Pauza, Pomiń). Bez wypełnienia, bo to nie akcja
 * główna; pole dotyku pełnej wysokości [height].
 */
@Composable
fun IconLabelButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
) {
    Column(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Przełącznik kilku słów w jednej pigułce (widok kafelki / lista w panelu filtrów). Wybrana opcja wypełniona ogniem
 * jak `FilterPill`, bo wybór to akcja; reszta na obwódce.
 */
@Composable
fun SegmentedText(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(34.dp),
        shape = Pill,
        color = Color.Transparent,
        border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Pozycja menu ⋮: etykieta, czerwona przy kasowaniu. */
class MenuAction(val label: String, val onClick: () -> Unit, val destructive: Boolean = false)

/**
 * Menu ⋮ (D-52, D-54): akcje, które nie zasługują na przycisk na ekranie (usuwanie, przesuwanie, duplikat).
 * Pusta lista nie rysuje przycisku. Opis „Więcej” czytają czytnik ekranu i skrypty przebiegów.
 */
@Composable
fun OverflowMenu(actions: List<MenuAction>, modifier: Modifier = Modifier, contentDescription: String = "Więcej") {
    if (actions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            Icon(AppIcons.More, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/**
 * Cichy odsyłacz (D-52): druga droga pod akcją główną, bez obwódki i wypełnienia, opcjonalnie z ikoną z przodu.
 * Ten sam wygląd co „Co jest w treningu” na karcie dnia.
 */
@Composable
fun QuietLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Pionowa linia włosowa: granica szyny i paneli na tablecie. */
@Composable
fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(Dimens.hairline)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    )
}
