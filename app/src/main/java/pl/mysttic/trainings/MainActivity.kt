package pl.mysttic.trainings

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.background.BackgroundPhotoRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.data.share.PlanShareRepository
import pl.mysttic.trainings.data.settings.UserSettings
import pl.mysttic.trainings.service.PlayerController
import pl.mysttic.trainings.service.WorkoutService
import pl.mysttic.trainings.ui.navigation.TrainingsRoot
import pl.mysttic.trainings.ui.theme.AppBackground
import pl.mysttic.trainings.ui.theme.AppTheme
import pl.mysttic.trainings.ui.theme.TrainingsTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var player: PlayerController

    @Inject lateinit var settingsRepo: SettingsRepository

    @Inject lateinit var planShare: PlanShareRepository

    @Inject lateinit var backgroundPhotos: BackgroundPhotoRepository

    /** Id treningu z powiadomienia (zimny start i onNewIntent przy singleTask); CONFLATED = ostatnie żądanie czeka na NavHost. */
    private val openPlayerRequests = Channel<String>(Channel.CONFLATED)

    /** Kopia ustawienia, bo `onKeyDown` nie może czekać na DataStore. */
    private var volumeKeyDone = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Tylko świeży start: po obrocie ekranu ten sam intent wróciłby i otworzył player drugi raz.
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch {
            settingsRepo.settings.collect { volumeKeyDone = it.volumeKeyDone }
        }
        setContent {
            // Motyw jest ustawieniem (D-39), więc zmiana w Ustawieniach przemalowuje aplikację od razu.
            val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
            val theme = AppTheme.fromId(settings.themeId)
            // Tło tego motywu (D-50): każdy motyw pamięta swoje, więc zmiana motywu przywraca jego wybór.
            val background = AppBackground.resolve(theme, settings.backgrounds[theme.id])
            // Zdjęcie dnia (D-51) leży od D-55 pod każdym tłem. Dobór czyta plan, treningi i bazę ćwiczeń, więc flow jest jeden.
            val photos = remember { backgroundPhotos.today() }
            val photo by photos.collectAsStateWithLifecycle(initialValue = null)
            val view = LocalView.current
            LaunchedEffect(theme, background) {
                // Wygląd nie idzie za systemem, więc ikony pasków i tło okna muszą iść za wyborem.
                window.setBackgroundDrawable(windowBackground(background))
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !theme.dark
                    isAppearanceLightNavigationBars = !theme.dark
                }
            }
            TrainingsTheme(theme = theme, background = background, photo = photo) {
                TrainingsRoot(openPlayer = openPlayerRequests.receiveAsFlow(), openPlanFile = planShare.incoming)
            }
        }
    }

    /**
     * Przycisk głośności potwierdza serię na powtórzenia (M6): w trakcie ćwiczenia telefon leży na podłodze
     * i łatwiej trafić w krawędź niż w ekran. Kontroler oddaje klawisz, gdy trening go akurat nie potrzebuje.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val volumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (volumeKey && volumeKeyDone && player.doneFromVolumeKey()) return true
        return super.onKeyDown(keyCode, event)
    }

    /** Bez zjedzenia „up” system i tak pokaże suwak głośności po puszczeniu klawisza. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val volumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (volumeKey && volumeKeyDone && player.isActive) return true
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Tło okna takie jak tło ekranów przed wczytaniem zdjęcia: widać je, zanim Compose narysuje klatkę. Dół pasma,
     * a z gradientem jego kolory zmieszane z dołem pasma, rozłożone równo z góry na dół jak w `gradientOverPhoto`.
     */
    private fun windowBackground(background: AppBackground): Drawable {
        val colors = background.colors().map { it.toArgb() }
        return if (colors.size < 2) {
            ColorDrawable(colors.first())
        } else {
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors.toIntArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            WorkoutService.ACTION_OPEN_PLAYER ->
                intent.getStringExtra(WorkoutService.EXTRA_WORKOUT_ID)?.let { openPlayerRequests.trySend(it) }
            // Plik planu od kogoś (D-42): repozytorium czyta go od razu i odkłada do cache, podgląd otwiera nawigacja.
            Intent.ACTION_VIEW -> intent.data?.let(planShare::receive)
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(planShare::receive)
        }
    }
}
