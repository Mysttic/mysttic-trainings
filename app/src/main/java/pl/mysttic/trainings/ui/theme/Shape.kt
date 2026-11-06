package pl.mysttic.trainings.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // checkbox, próbka
    small = RoundedCornerShape(12.dp),       // chipy, miniatura 44
    medium = RoundedCornerShape(16.dp),      // przyciski drugorzędne, miniatura 56
    large = RoundedCornerShape(22.dp),       // wielkie przyciski, zdjęcie w playerze
    extraLarge = RoundedCornerShape(26.dp),  // karta dnia, dialog
)

/** Chipy partii, znaczniki planu, przełącznik, tory pasków. */
val Pill = RoundedCornerShape(percent = 50)

/**
 * Odstępy i wymiary z systemu „Ogień i Krew” (D-32). Skala: 4 · 8 · 10 · 14 · 16 · 20 · 22 · 24 · 26.
 * Elewacje są wszędzie zerowe — warstwy rozdziela stopień surfaceContainer i linia włosowa.
 */
object Dimens {
    val screenPadding = 24.dp        // margines treści
    val cardPadding = 20.dp          // margines kart pełnej szerokości
    val cardInner = 22.dp
    val cardPreviewInner = 18.dp     // karta dnia z paskiem miniatur: miniatury bliżej krawędzi (D-52)
    val cardGap = 15.dp
    val sectionGap = 16.dp
    val rowGap = 14.dp
    val hairline = 1.dp
    val nowAccent = 3.dp             // pasek „to teraz” od lewej
    val touchMin = 48.dp
    val playerAction = 62.dp
    val playerStepper = 58.dp
    val primaryButton = 58.dp
    val secondaryButton = 44.dp
    val settingsRow = 48.dp
    val thumbSmall = 44.dp
    val thumbLarge = 56.dp
    val navIndicator = 34.dp
    val progressTrack = 6.dp
    val stepperThumb = 22.dp         // uchwyt na pasku wartości w kreatorze
    val progressTrackPlayer = 4.dp
}
