package pl.mysttic.trainings.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import pl.mysttic.trainings.domain.update.DatabaseUpdate
import java.io.File
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Equipment
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.ui.theme.Dimens
import pl.mysttic.trainings.ui.theme.PhotoBand

/*
 * Filtr zdjęć ćwiczeń (D-32). Biblioteka 81 pozycji jest niejednolita: różne siłownie, tła i światło.
 * Ten sam łańcuch co w prototypie (CSS `filter`), złożony w jedną macierz koloru:
 *   ciemny: grayscale .6 · sepia .25 · saturate 1.3 · brightness .82
 *   jasny:  grayscale .55 · sepia .18 · saturate 1.2
 * Macierze 4x5 w układzie wierszowym, mnożone w kolejności nakładania filtrów.
 */
private fun matrixTimes(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 5) {
            var sum = 0f
            for (k in 0 until 4) sum += a[row * 5 + k] * b[k * 5 + col]
            if (col == 4) sum += a[row * 5 + 4]
            out[row * 5 + col] = sum
        }
    }
    return out
}

private fun grayscaleMatrix(amount: Float): FloatArray {
    val s = 1f - amount
    return floatArrayOf(
        0.2126f + 0.7874f * s, 0.7152f - 0.7152f * s, 0.0722f - 0.0722f * s, 0f, 0f,
        0.2126f - 0.2126f * s, 0.7152f + 0.2848f * s, 0.0722f - 0.0722f * s, 0f, 0f,
        0.2126f - 0.2126f * s, 0.7152f - 0.7152f * s, 0.0722f + 0.9278f * s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun sepiaMatrix(amount: Float): FloatArray {
    val s = 1f - amount
    return floatArrayOf(
        0.393f + 0.607f * s, 0.769f - 0.769f * s, 0.189f - 0.189f * s, 0f, 0f,
        0.349f - 0.349f * s, 0.686f + 0.314f * s, 0.168f - 0.168f * s, 0f, 0f,
        0.272f - 0.272f * s, 0.534f - 0.534f * s, 0.131f + 0.869f * s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun saturateMatrix(s: Float): FloatArray = floatArrayOf(
    0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s, 0f, 0f,
    0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s, 0f, 0f,
    0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

private fun brightnessMatrix(k: Float): FloatArray = floatArrayOf(
    k, 0f, 0f, 0f, 0f,
    0f, k, 0f, 0f, 0f,
    0f, 0f, k, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

private fun photoMatrix(dark: Boolean): FloatArray {
    var m = grayscaleMatrix(if (dark) 0.6f else 0.55f)
    m = matrixTimes(sepiaMatrix(if (dark) 0.25f else 0.18f), m)
    m = matrixTimes(saturateMatrix(if (dark) 1.3f else 1.2f), m)
    if (dark) m = matrixTimes(brightnessMatrix(0.82f), m)
    return m
}

/**
 * Filtr idzie za jasnością motywu, nie za trybem systemu (D-39, poprawka z D-52): „Ogień i Krew” jest ciemny także
 * przy jasnym systemie, a filtr jasny zostawiał ilustracje białe jak kartka na krwistym tle.
 */
@Composable
fun exercisePhotoFilter(): ColorFilter {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(dark) { ColorFilter.colorMatrix(ColorMatrix(photoMatrix(dark))) }
}

/**
 * Model Coila dla ścieżki obrazu ćwiczenia (D-47): ścieżka z prefiksem `baza/` to plik pobranej bazy w `files/`,
 * każda inna to asset bazy wbudowanej w APK. Jedno miejsce, żeby kafelek, miniatura i szczegóły szły tą samą drogą.
 */
@Composable
fun exerciseImageModel(path: String): Any {
    val context = LocalContext.current
    return remember(path) {
        if (path.startsWith(DatabaseUpdate.IMAGE_PREFIX)) File(context.filesDir, path) else "file:///android_asset/$path"
    }
}

/**
 * Zdjęcie dnia pod całą aplikacją (D-51). Jest rozmyte już w bazie (`python tools/baza.py tlo`), bo `Modifier.blur`
 * działa dopiero od Androida 12, a filtr pasma motywu trzyma kontrast tekstu i szklanych kart nad każdym pikselem.
 * Ścieżka idzie tą samą drogą co ilustracje ćwiczeń: asset bazy wbudowanej albo plik pobranej.
 */
@Composable
fun PhotoBackdrop(path: String, band: PhotoBand, modifier: Modifier = Modifier) {
    AsyncImage(
        model = exerciseImageModel(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = remember(band) { band.colorFilter() },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Zdjęcie ćwiczenia z bazy. `assetPath` np. "images/pushups.jpg" (asset, korzeniem jest `baza/`) albo
 * "baza/11/images/pushups.jpg" (plik pobranej bazy, D-47); null = placeholder.
 * Miniatury kadrujemy (`Crop`), duże obrazy mieszczą się w całości (`Fit`): ilustracje są szersze niż
 * ramka i pokazują ruch w dwóch fazach, więc obcięte boki gubią połowę ćwiczenia.
 */
@Composable
fun ExerciseImage(
    assetPath: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (assetPath != null) {
            AsyncImage(
                model = exerciseImageModel(assetPath),
                contentDescription = contentDescription,
                contentScale = contentScale,
                colorFilter = exercisePhotoFilter(),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "brak zdjęcia",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Kafelek biblioteki: ilustracja w całości w ramce 16:9 (tyle ma większość ilustracji D-36, a kadrowanie gubiłoby drugą
 * fazę ruchu), pod nią nazwa i opis. Blok tekstu ma stałą wysokość czterech linii, żeby kafelki w rzędzie były równe,
 * a ostrzeżenie o sprzęcie wchodzi pod opis (przy długiej nazwie i opisie jako piąta linia). Bez clipu na całości:
 * zaokrąglony róg obcinałby pierwszą literę ostatniej linii.
 */
@Composable
fun ExerciseTile(
    name: String,
    secondary: String,
    imagePath: String?,
    modifier: Modifier = Modifier,
    warning: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val titleStyle = MaterialTheme.typography.titleMedium
    val bodyStyle = MaterialTheme.typography.bodySmall
    val textBlockMin = with(LocalDensity.current) { titleStyle.lineHeight.toDp() * 2 + bodyStyle.lineHeight.toDp() * 2 }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        TileImage(assetPath = imagePath, contentDescription = name)
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.heightIn(min = textBlockMin)) {
            Text(
                text = name,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = secondary,
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (warning != null) {
                Text(text = warning, style = bodyStyle, color = MaterialTheme.colorScheme.error, maxLines = 1)
            }
        }
    }
}

/**
 * Ilustracja kafelka: ramka 16:9 bez tła, obraz wyśrodkowany i przycięty do własnych proporcji z zaokrągleniem,
 * więc ilustracja 3:2 jest węższą kartą zamiast obrazu z ciemnymi pasami po bokach. Bez zdjęcia: placeholder z [ExerciseImage].
 */
@Composable
private fun TileImage(assetPath: String?, contentDescription: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
        if (assetPath == null) {
            ExerciseImage(assetPath = null, modifier = Modifier.fillMaxSize())
        } else {
            val painter = rememberAsyncImagePainter(model = exerciseImageModel(assetPath))
            val intrinsic = painter.intrinsicSize
            val ratio = if (intrinsic.isSpecified && intrinsic.height > 0f) intrinsic.width / intrinsic.height else 16f / 9f
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = exercisePhotoFilter(),
                modifier = Modifier.aspectRatio(ratio).clip(RoundedCornerShape(16.dp)),
            )
        }
    }
}

/**
 * Pasek miniatur ćwiczeń na karcie dnia (D-52): do czterech ilustracji w równych kafelkach, a przy dłuższym treningu
 * trzy i kafelek „+N”. Miniatury kadrujemy — to zapowiedź treningu, pełny ruch pokazuje szczegół ćwiczenia.
 * [total] = liczba różnych ćwiczeń w treningu; ilustracji może być mniej, gdy któreś jej nie ma.
 */
@Composable
fun ExercisePreviewStrip(
    images: List<String>,
    total: Int,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 64.dp,
    onClick: (() -> Unit)? = null,
) {
    val shown = if (total > PREVIEW_TILES) images.take(PREVIEW_TILES - 1) else images.take(PREVIEW_TILES)
    val more = (total - shown.size).coerceAtLeast(0)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Co jest w treningu", onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { path ->
            ExerciseImage(assetPath = path, modifier = Modifier.weight(1f).height(height), shape = RoundedCornerShape(14.dp))
        }
        if (more > 0) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text("+$more", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private const val PREVIEW_TILES = 4

/** Miniatura w liście: 44 dp (promień 12) w treningu, 56 dp (promień 16) w bibliotece. */
@Composable
fun ExerciseThumbnail(exercise: Exercise, modifier: Modifier = Modifier, large: Boolean = false) {
    ExerciseImage(
        assetPath = exercise.images.lastOrNull(),
        modifier = modifier.size(if (large) Dimens.thumbLarge else Dimens.thumbSmall),
        contentDescription = exercise.namePl,
        shape = RoundedCornerShape(if (large) 16.dp else 12.dp),
    )
}

/**
 * Ilustracja w swoich proporcjach (D-52): ramka ma kształt obrazu, więc nie ma pasów tła po bokach ani u góry.
 * Szerokość bierze dostępne miejsce, a [maxHeight] ją ogranicza (player); proporcje czyta painter po wczytaniu,
 * do tego czasu 2:1 — tyle ma większość ilustracji z generatora (D-36).
 */
@Composable
fun ExerciseIllustration(
    assetPath: String?,
    modifier: Modifier = Modifier,
    maxHeight: Dp = Dp.Unspecified,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(22.dp),
) {
    if (assetPath == null) {
        ExerciseImage(assetPath = null, modifier = modifier.fillMaxWidth().aspectRatio(2f), shape = shape)
        return
    }
    val painter = rememberAsyncImagePainter(model = exerciseImageModel(assetPath))
    val intrinsic = painter.intrinsicSize
    val ratio = if (intrinsic.isSpecified && intrinsic.height > 0f) intrinsic.width / intrinsic.height else 2f
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = if (maxHeight != Dp.Unspecified) minOf(maxWidth, maxHeight * ratio) else maxWidth
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            colorFilter = exercisePhotoFilter(),
            modifier = Modifier.width(width).aspectRatio(ratio).clip(shape),
        )
    }
}

/** Obraz ćwiczenia na całą szerokość. Od seedu v5 to zawsze jedna ilustracja; dwa kadry (Start, Koniec) obsługujemy dalej. */
@Composable
fun ExerciseImagePair(exercise: Exercise, modifier: Modifier = Modifier) {
    if (exercise.images.isEmpty()) {
        ExerciseImage(assetPath = null, modifier = modifier.fillMaxWidth().aspectRatio(3f / 2f))
        return
    }
    if (exercise.images.size == 1) {
        ExerciseIllustration(assetPath = exercise.images.single(), modifier = modifier, contentDescription = exercise.namePl)
        return
    }
    // Dwa zdjęcia to pozycja startowa i końcowa; jedna ilustracja (D-36) pokazuje cały ruch strzałkami, więc bez podpisów.
    val captions = if (exercise.images.size >= 2) listOf("Start", "Koniec") else emptyList()
    val single = exercise.images.size == 1
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        exercise.images.take(2).forEachIndexed { index, path ->
            Column(modifier = Modifier.weight(1f)) {
                ExerciseImage(
                    assetPath = path,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 2f),
                    contentDescription = exercise.namePl,
                    shape = RoundedCornerShape(22.dp),
                    contentScale = if (single) ContentScale.Fit else ContentScale.Crop,
                )
                captions.getOrNull(index)?.let {
                    Spacer(Modifier.height(6.dp))
                    Overline(it)
                }
            }
        }
    }
}

/**
 * Wybór poziomu: trzy równe segmenty z numerem, nie nazwą — „Średniozaawansowany” nie mieści się
 * w jednej linii na telefonie. Ten sam komponent w Ustawieniach, w szczegółach treningu i w playerze.
 */
@Composable
fun DifficultySelector(
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val height = if (compact) 34.dp else 38.dp
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Difficulty.entries.forEach { level ->
            val isSelected = level == selected
            val label = if (compact) level.level.toString() else "Poziom ${level.level}"
            if (isSelected) {
                Button(
                    onClick = { onSelect(level) },
                    modifier = Modifier.weight(1f).height(height),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(level) },
                    modifier = Modifier.weight(1f).height(height),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Kolory przycisku obrysowanego. W Material 3 1.4 domyślną treścią `OutlinedButton` jest `onSurfaceVariant`;
 * w tym systemie przyciski drugorzędne mają treść w `onSurface`, bo ogień jest zarezerwowany dla akcji głównej.
 */
@Composable
fun brandOutlinedColors() = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

/** Nagłówek sekcji: wersaliki w kolorze żaru (D-32). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Overline(text = text, modifier = modifier.padding(top = 20.dp, bottom = 10.dp))
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Odstęp pionowy o wysokości z siatki systemu. */
@Composable
fun VSpace(height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.height(height))
}

fun formatDuration(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s) else "%d:%02d".format(m, s)
}

/** Ciężar po polsku: „20 kg”, „22,5 kg” — bez „20.0 kg” z `Double.toString`. */
fun formatKg(kg: Double): String {
    val tenths = Math.round(kg * 10)
    val text = if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10},${kotlin.math.abs(tenths % 10)}"
    return "$text kg"
}
