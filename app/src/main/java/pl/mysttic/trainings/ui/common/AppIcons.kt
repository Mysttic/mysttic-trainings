package pl.mysttic.trainings.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Własne ikony kreskowe (D-52): siatka 24 x 24, kreska 1,8 z zaokrąglonymi końcami. `material-icons-core` (D-13) nie ma
 * hantla, pauzy ani „pomiń”, a mieszanie wypełnionych glifów Materiala z kreską wyglądało jak dwa systemy naraz.
 * Ścieżki są te same co w projekcie z designera, więc ikona na makiecie i w aplikacji to jedna ikona.
 * Kolor nadaje `Icon` (tint), więc czerń poniżej jest tylko nośnikiem, a nie kolorem ekranu.
 */
object AppIcons {
    val Today: ImageVector by lazy {
        icon("today", "M12 3c.9 3 4.8 5.2 4.8 9.6a4.8 4.8 0 0 1-9.6 0c0-2.1 1-3.6 2.2-4.6.1 1.5.8 2.6 1.9 3.1-.4-2.9.2-5.5.7-8.1z")
    }
    val Exercises: ImageVector by lazy { icon("exercises", "M2.75 10v4M5.75 7v10M18.25 7v10M21.25 10v4M5.75 12h12.5", 2f) }
    val History: ImageVector by lazy { icon("history", "M5 20v-6M10 20V9M15 20V4M20 20v-9", 2.2f) }
    val Settings: ImageVector by lazy {
        icon(
            "settings",
            "M4 7.5h8.5M17.5 7.5H20M4 16.5h2.5M11.5 16.5H20M17.5 7.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z" +
                "M11.5 16.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z",
        )
    }
    val Search: ImageVector by lazy { icon("search", "M16.5 10.5a6 6 0 1 1-12 0a6 6 0 1 1 12 0zM15 15l5 5") }
    val Filter: ImageVector by lazy { icon("filter", "M4 5h16l-6.25 7.5V18l-3.5 1.75V12.5z") }
    val ChevronRight: ImageVector by lazy { icon("chevron-right", "M9.5 5.5L16 12l-6.5 6.5") }
    val ChevronDown: ImageVector by lazy { icon("chevron-down", "M5.5 9.5L12 16l6.5-6.5") }
    val Back: ImageVector by lazy { icon("back", "M19.5 12h-15M10.5 6l-6 6 6 6") }
    val More: ImageVector by lazy { icon("more", "M12 5.5v.01M12 12v.01M12 18.5v.01", 3f) }
    val Pause: ImageVector by lazy { icon("pause", "M8.5 5.5v13M15.5 5.5v13", 2.4f) }
    val Next: ImageVector by lazy { icon("next", "M6 5.5l8.5 6.5L6 18.5zM18 5.5v13") }
    val Previous: ImageVector by lazy { icon("previous", "M18 5.5L9.5 12l8.5 6.5zM6 5.5v13") }
    val Play: ImageVector by lazy { icon("play", "M8 5.5v13l10.5-6.5z", 2f) }
    val Plus: ImageVector by lazy { icon("plus", "M12 5v14M5 12h14", 2f) }
    val Minus: ImageVector by lazy { icon("minus", "M5 12h14", 2f) }
    val Close: ImageVector by lazy { icon("close", "M6 6l12 12M18 6L6 18") }
    val Timer: ImageVector by lazy { icon("timer", "M18.5 13.5a6.5 6.5 0 1 1-13 0a6.5 6.5 0 1 1 13 0zM12 13.5V10M10 3.5h4M12 3.5V7") }
    val Bell: ImageVector by lazy { icon("bell", "M6.5 16.5v-5a5.5 5.5 0 0 1 11 0v5l1.5 2h-14zM10 20.5a2 2 0 0 0 4 0") }
    val Palette: ImageVector by lazy {
        icon("palette", "M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0z", fillPath = "M12 3.5a8.5 8.5 0 0 0 0 17z")
    }
    val Data: ImageVector by lazy {
        icon(
            "data",
            "M5 6.5c0-1.4 3.1-2.5 7-2.5s7 1.1 7 2.5-3.1 2.5-7 2.5-7-1.1-7-2.5zM5 6.5v11c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5v-11" +
                "M5 12c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5",
        )
    }
    val Update: ImageVector by lazy { icon("update", "M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.5v4h-4") }
    val Plans: ImageVector by lazy {
        icon("plans", "M8.5 6.5H20M8.5 12H20M8.5 17.5H20M4.5 6.5v.01M4.5 12v.01M4.5 17.5v.01", 2f)
    }
    val ArrowRight: ImageVector by lazy { icon("arrow-right", "M4.5 12h15M13.5 6l6 6-6 6") }
    /** Kreator planu: iskra — plan „wyczarowany” z odpowiedzi. */
    val Spark: ImageVector by lazy {
        icon("spark", "M11 4.5l1.6 4.4 4.4 1.6-4.4 1.6L11 16.5l-1.6-4.4L5 10.5l4.4-1.6zM18 15v4M16 17h4")
    }
    /** Plik planu od kogoś (D-42). */
    val File: ImageVector by lazy { icon("file", "M7 3.5h6.5l4 4v13H7zM13.5 3.5v4h4M9.5 13h5M9.5 16.5h5") }

    /** Wszystkie ikony z nazwami — dla testu, który pilnuje, że każda ścieżka się parsuje. */
    val all: Map<String, ImageVector>
        get() = mapOf(
            "Today" to Today, "Exercises" to Exercises, "History" to History, "Settings" to Settings,
            "Search" to Search, "Filter" to Filter, "ChevronRight" to ChevronRight, "ChevronDown" to ChevronDown,
            "Back" to Back, "More" to More, "Pause" to Pause, "Next" to Next, "Previous" to Previous, "Play" to Play,
            "Plus" to Plus, "Minus" to Minus, "Close" to Close, "Timer" to Timer, "Bell" to Bell, "Palette" to Palette,
            "Data" to Data, "Update" to Update, "Plans" to Plans, "ArrowRight" to ArrowRight, "Spark" to Spark, "File" to File,
        )
}

private fun icon(name: String, strokePath: String, strokeWidth: Float = 1.8f, fillPath: String? = null): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        if (fillPath != null) addPath(pathData = addPathNodes(fillPath), fill = SolidColor(Color.Black))
        addPath(
            pathData = addPathNodes(strokePath),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
