package pl.mysttic.trainings.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/*
 * Komplet 35 ról Material 3 na motyw (D-17, D-32). Pominięta rola spada na paletę bazową M3 (fiolet),
 * dlatego wszystkie są podane jawnie. Bez dynamicColor: wygląd to tożsamość produktu, nie tapeta systemu —
 * ale od D-39 użytkownik wybiera jeden z trzech motywów, a każdy z nich jest skończony: „Papier i Ogień”
 * jest jasny zawsze, pozostałe ciemne zawsze, niezależnie od ustawienia systemu.
 * Wartości „Ognia i Krwi” stoją w Color.kt, pozostałych w Themes.kt.
 */

private val FireScheme = darkColorScheme(
    primary = FireDark,
    onPrimary = OnFireDark,
    primaryContainer = FireContainerDark,
    onPrimaryContainer = OnFireContainerDark,
    secondary = ClayDark,
    onSecondary = OnClayDark,
    secondaryContainer = ClayContainerDark,
    onSecondaryContainer = OnClayContainerDark,
    tertiary = EmberDark,
    onTertiary = OnEmberDark,
    tertiaryContainer = EmberContainerDark,
    onTertiaryContainer = OnEmberContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceLowestDark,
    surfaceContainerLow = SurfaceLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceHighDark,
    surfaceContainerHighest = SurfaceHighestDark,
    surfaceBright = SurfaceBrightDark,
    surfaceDim = SurfaceDimDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
    scrim = ScrimDark,
)


private val PaperScheme = lightColorScheme(
    primary = PaperPrimary,
    onPrimary = PaperOnPrimary,
    primaryContainer = PaperPrimaryContainer,
    onPrimaryContainer = PaperOnPrimaryContainer,
    secondary = PaperSecondary,
    onSecondary = PaperOnSecondary,
    secondaryContainer = PaperSecondaryContainer,
    onSecondaryContainer = PaperOnSecondaryContainer,
    tertiary = PaperTertiary,
    onTertiary = PaperOnTertiary,
    tertiaryContainer = PaperTertiaryContainer,
    onTertiaryContainer = PaperOnTertiaryContainer,
    error = PaperError,
    onError = PaperOnError,
    errorContainer = PaperErrorContainer,
    onErrorContainer = PaperOnErrorContainer,
    background = PaperBackground,
    onBackground = PaperOnBackground,
    surface = PaperSurface,
    onSurface = PaperOnSurface,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperOnSurfaceVariant,
    surfaceContainerLowest = PaperSurfaceContainerLowest,
    surfaceContainerLow = PaperSurfaceContainerLow,
    surfaceContainer = PaperSurfaceContainer,
    surfaceContainerHigh = PaperSurfaceContainerHigh,
    surfaceContainerHighest = PaperSurfaceContainerHighest,
    surfaceBright = PaperSurfaceBright,
    surfaceDim = PaperSurfaceDim,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    inverseSurface = PaperInverseSurface,
    inverseOnSurface = PaperInverseOnSurface,
    inversePrimary = PaperInversePrimary,
    scrim = PaperScrim,
)

private val SteelScheme = darkColorScheme(
    primary = SteelPrimary,
    onPrimary = SteelOnPrimary,
    primaryContainer = SteelPrimaryContainer,
    onPrimaryContainer = SteelOnPrimaryContainer,
    secondary = SteelSecondary,
    onSecondary = SteelOnSecondary,
    secondaryContainer = SteelSecondaryContainer,
    onSecondaryContainer = SteelOnSecondaryContainer,
    tertiary = SteelTertiary,
    onTertiary = SteelOnTertiary,
    tertiaryContainer = SteelTertiaryContainer,
    onTertiaryContainer = SteelOnTertiaryContainer,
    error = SteelError,
    onError = SteelOnError,
    errorContainer = SteelErrorContainer,
    onErrorContainer = SteelOnErrorContainer,
    background = SteelBackground,
    onBackground = SteelOnBackground,
    surface = SteelSurface,
    onSurface = SteelOnSurface,
    surfaceVariant = SteelSurfaceVariant,
    onSurfaceVariant = SteelOnSurfaceVariant,
    surfaceContainerLowest = SteelSurfaceContainerLowest,
    surfaceContainerLow = SteelSurfaceContainerLow,
    surfaceContainer = SteelSurfaceContainer,
    surfaceContainerHigh = SteelSurfaceContainerHigh,
    surfaceContainerHighest = SteelSurfaceContainerHighest,
    surfaceBright = SteelSurfaceBright,
    surfaceDim = SteelSurfaceDim,
    outline = SteelOutline,
    outlineVariant = SteelOutlineVariant,
    inverseSurface = SteelInverseSurface,
    inverseOnSurface = SteelInverseOnSurface,
    inversePrimary = SteelInversePrimary,
    scrim = SteelScrim,
)

/** Schemat wybranego motywu; jedyne miejsce, które kojarzy wybór z kolorami. */
fun schemeFor(theme: AppTheme): ColorScheme = when (theme) {
    AppTheme.FIRE -> FireScheme
    AppTheme.PAPER -> PaperScheme
    AppTheme.STEEL -> SteelScheme
}

@Composable
fun TrainingsTheme(
    theme: AppTheme = AppTheme.FIRE,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = schemeFor(theme),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
