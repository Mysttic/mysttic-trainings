package pl.mysttic.trainings.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Paleta „Ogień i Krew” (D-32). Trzy zasady, z których wynika reszta:
 * 1. Krew to podłoże — powierzchnie są wypaloną krwią, nie neutralnym grafitem. Czerwień nigdy nie jest tekstem.
 * 2. Ogień to akcja — wypełnienie kolorem primary znaczy „kliknij” albo „zrobione”, nic dekoracyjnego go nie dostaje.
 * 3. Żar to informacja — tertiary niesie nagłówki sekcji, serię dni i podpowiedzi progresji, nigdy nie trafia na przycisk.
 * Stąd wykresy i paski: zrobione = primary, resztki = outlineVariant (krwiste, nie szare).
 * Kontrast par sprawdzany skryptem tools/check_contrast.py i testem ThemeContractTest — zmieniając odcień, przelicz.
 * Pozostałe motywy (D-39) mieszkają w Themes.kt.
 */

// ---- „Ogień i Krew”: jeden skończony wygląd, bez wariantu jasnego (D-39) ----
val FireDark = Color(0xFFFF6A21)              // primary: Start, Zrobione, +15 s, wypełnione paski
val OnFireDark = Color(0xFF2A0B00)            // tekst na ogniu — zawsze ciemny tusz, nigdy biały
val FireContainerDark = Color(0xFF7A2A0A)
val OnFireContainerDark = Color(0xFFFFD9C2)
val ClayDark = Color(0xFFE3A796)              // secondary: rzadko, gdy ogień byłby za mocny
val OnClayDark = Color(0xFF46170F)
val ClayContainerDark = Color(0xFF5C231A)     // wyłączony przełącznik, znacznik odpoczynku
val OnClayContainerDark = Color(0xFFFFD8CC)
val EmberDark = Color(0xFFFFC24A)             // tertiary: żar — nagłówki, seria dni, „Podbij cele”, RPE
val OnEmberDark = Color(0xFF2A1A00)
val EmberContainerDark = Color(0xFF6B4A00)
val OnEmberContainerDark = Color(0xFFFFE3AE)
val ErrorDark = Color(0xFFFF9AA2)             // róż, nie pomarańcz — inaczej błąd zlewa się z akcją
val OnErrorDark = Color(0xFF5A0011)
val ErrorContainerDark = Color(0xFF8C0021)
val OnErrorContainerDark = Color(0xFFFFD9DC)
val SurfaceDark = Color(0xFF120809)           // krew wypalona: tło wszystkich ekranów
val OnSurfaceDark = Color(0xFFFBEDE8)         // nagłówki, wielkie liczby, nazwy ćwiczeń
val OnSurfaceVariantDark = Color(0xFFBAADAA)  // podpisy, metadane, jednostki
val SurfaceLowestDark = Color(0xFF0C0505)     // ekran blokady pod powiadomieniami
val SurfaceLowDark = Color(0xFF1A0C0B)        // pola wejściowe, tło notatki
val SurfaceContainerDark = Color(0xFF23100F)  // karta dnia, przyciski drugorzędne, miniatury
val SurfaceHighDark = Color(0xFF2E1614)       // karta na karcie (dialog nad playerem)
val SurfaceHighestDark = Color(0xFF391B18)    // stan wciśnięcia
val SurfaceBrightDark = Color(0xFF391B18)
val SurfaceDimDark = Color(0xFF0C0505)
val OutlineDark = Color(0xFF8A6660)           // obwódka przycisków i chipów, 1 dp
val OutlineVariantDark = Color(0xFF4A1A18)    // puste tory pasków, słupki wykresu, siatka kalendarza
val InverseSurfaceDark = Color(0xFFFBEDE8)
val InverseOnSurfaceDark = Color(0xFF23100F)
val InversePrimaryDark = Color(0xFFB4230F)    // akcja w snackbarze: ogień z motywu jasnego
val ScrimDark = Color(0xFF090404)
