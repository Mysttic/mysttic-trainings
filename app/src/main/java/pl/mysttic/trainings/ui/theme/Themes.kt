package pl.mysttic.trainings.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Motywy do wyboru w Ustawieniach (D-39). Każdy to jeden skończony wygląd, nie para jasny/ciemny:
 * „Ogień i Krew” (domyślny, wartości w Color.kt), „Papier i Ogień” i „Stal i Bursztyn” — tutaj.
 * Wszystkie trzymają reguły z D-32: wypełnienie `primary` znaczy akcję, `tertiary` niesie informację
 * i nigdy nie trafia na przycisk, `error` jest z innej rodziny odcieni niż akcja, a powierzchnie są
 * podbarwione, nie neutralnie szare. Komplet 35 ról na motyw, kontrast pilnuje `ThemeContractTest`
 * i `python tools/check_contrast.py`.
 */

/**
 * Wybrany wygląd; `id` idzie do DataStore i do kopii zapasowej, więc istniejących nie zmieniamy.
 * `dark` mówi tylko tyle, czy ikony paska stanu mają być jasne — nie jest trybem do przełączania.
 */
enum class AppTheme(val id: String, val labelPl: String, val opisPl: String, val dark: Boolean) {
    FIRE("fire", "Ogień i Krew", "Wypalona krew pod spodem, ogień na akcji. Domyślny.", dark = true),
    PAPER("paper", "Papier i Ogień", "Ten sam ogień na jasnym papierze — ekran nie świeci w dzień.", dark = false),
    STEEL("steel", "Stal i Bursztyn", "Chłodny grafit jak rama sprzętu, bursztyn tylko na akcji.", dark = true),
    ;

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: FIRE
    }
}

// ---- Papier i Ogień ----
val PaperPrimary = Color(0xFF932A08)
val PaperOnPrimary = Color(0xFFFFF3EC)
val PaperPrimaryContainer = Color(0xFFFFDCCE)
val PaperOnPrimaryContainer = Color(0xFF3F0E02)
val PaperSecondary = Color(0xFF7E4433)
val PaperOnSecondary = Color(0xFFFFF3EC)
val PaperSecondaryContainer = Color(0xFFFFDCD0)
val PaperOnSecondaryContainer = Color(0xFF3B140B)
val PaperTertiary = Color(0xFF744A06)
val PaperOnTertiary = Color(0xFFFFF3EC)
val PaperTertiaryContainer = Color(0xFFFFE1AC)
val PaperOnTertiaryContainer = Color(0xFF2E1D00)
val PaperError = Color(0xFF920728)
val PaperOnError = Color(0xFFFFFFFF)
val PaperErrorContainer = Color(0xFFFFDADE)
val PaperOnErrorContainer = Color(0xFF430011)
val PaperBackground = Color(0xFFFCF6F2)
val PaperOnBackground = Color(0xFF1F1310)
val PaperSurface = Color(0xFFFCF6F2)
val PaperOnSurface = Color(0xFF1F1310)
val PaperSurfaceVariant = Color(0xFFF2E7DF)
val PaperOnSurfaceVariant = Color(0xFF635049)
val PaperSurfaceContainerLowest = Color(0xFFFFFFFF)
val PaperSurfaceContainerLow = Color(0xFFF7EFE9)
val PaperSurfaceContainer = Color(0xFFF2E7DF)
val PaperSurfaceContainerHigh = Color(0xFFEADDD3)
val PaperSurfaceContainerHighest = Color(0xFFE2D1C4)
val PaperSurfaceBright = Color(0xFFFFFFFF)
val PaperSurfaceDim = Color(0xFFE2D1C4)
val PaperOutline = Color(0xFF785D55)
val PaperOutlineVariant = Color(0xFFE0CCBE)
val PaperInverseSurface = Color(0xFF392922)
val PaperInverseOnSurface = Color(0xFFFCF0EA)
val PaperInversePrimary = Color(0xFFFF7E36)
val PaperScrim = Color(0xFF2C1A14)

// ---- Stal i Bursztyn ----
val SteelPrimary = Color(0xFFF2A93C)
val SteelOnPrimary = Color(0xFF2B1B00)
val SteelPrimaryContainer = Color(0xFF6E4A08)
val SteelOnPrimaryContainer = Color(0xFFFFE0AE)
val SteelSecondary = Color(0xFFD8CBB0)
val SteelOnSecondary = Color(0xFF2E2718)
val SteelSecondaryContainer = Color(0xFF3F3A2B)
val SteelOnSecondaryContainer = Color(0xFFEDE3CC)
val SteelTertiary = Color(0xFF9CC3E0)
val SteelOnTertiary = Color(0xFF0E1C26)
val SteelTertiaryContainer = Color(0xFF274A61)
val SteelOnTertiaryContainer = Color(0xFFCDE5F6)
val SteelError = Color(0xFFFF8DA6)
val SteelOnError = Color(0xFF4A0A1E)
val SteelErrorContainer = Color(0xFF7E0F2E)
val SteelOnErrorContainer = Color(0xFFFFD6E0)
val SteelBackground = Color(0xFF10151A)
val SteelOnBackground = Color(0xFFE7EDF2)
val SteelSurface = Color(0xFF10151A)
val SteelOnSurface = Color(0xFFE7EDF2)
val SteelSurfaceVariant = Color(0xFF1B232A)
val SteelOnSurfaceVariant = Color(0xFFAAB8C2)
val SteelSurfaceContainerLowest = Color(0xFF090D11)
val SteelSurfaceContainerLow = Color(0xFF151B21)
val SteelSurfaceContainer = Color(0xFF1B232A)
val SteelSurfaceContainerHigh = Color(0xFF232D35)
val SteelSurfaceContainerHighest = Color(0xFF2B3740)
val SteelSurfaceBright = Color(0xFF2B3740)
val SteelSurfaceDim = Color(0xFF090D11)
val SteelOutline = Color(0xFF8796A3)
val SteelOutlineVariant = Color(0xFF2B4254)
val SteelInverseSurface = Color(0xFFE7EDF2)
val SteelInverseOnSurface = Color(0xFF1B232A)
val SteelInversePrimary = Color(0xFF7B5000)
val SteelScrim = Color(0xFF05080B)
