package pl.mysttic.trainings.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.mysttic.trainings.domain.engine.EngineState
import pl.mysttic.trainings.domain.engine.Phase
import pl.mysttic.trainings.domain.engine.Step
import javax.inject.Inject

/**
 * Foreground service playera (M2, D-16): tyka silnikiem w [PlayerController], trzyma częściowy wake lock w trakcie
 * pracy, wystawia powiadomienie z chronometrem i akcjami. Typ `mediaPlayback`, bo serwis gra sygnały i TTS.
 * `START_STICKY`: po ubiciu procesu system restartuje serwis z `intent == null`, wtedy wznawiamy z checkpointu.
 */
@AndroidEntryPoint
class WorkoutService : Service() {

    @Inject lateinit var controller: PlayerController
    @Inject lateinit var cues: Cues
    @Inject lateinit var clock: MonotonicClock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var observeJob: Job? = null
    private var permissionJob: Job? = null
    private lateinit var notificationGate: NotificationGate
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastStartId = 0
    private lateinit var notifications: PlayerNotifications

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = PlayerNotifications(this)
        // Zbocze od stanu faktycznego: przy zgodzie nadanej wcześniej pierwszy obieg nie udaje „odzyskania”.
        notificationGate = NotificationGate(notifications.canPost())
        notifications.ensureChannel()
        cues.warmUp()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "trainings:player")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Najpierw startForeground: po startForegroundService() system daje kilka sekund, a ładowanie treningu trwa.
        goForeground(controller.state.value)
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_WORKOUT_ID)?.let { controller.startAsync(it) }
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_SKIP -> controller.skip()
            ACTION_DONE -> controller.done()
            ACTION_ADD_REST -> controller.addRest()
            null -> scope.launch {
                // restart po ubiciu procesu (START_STICKY)
                if (!controller.restoreLatest()) {
                    Log.i(TAG, "Restart bez sesji do wznowienia, zatrzymuję serwis")
                    stopSelfSafely()
                }
            }
            else -> Log.w(TAG, "Nieznana akcja: ${intent.action}")
        }
        ensureRunning()
        return START_STICKY
    }

    private fun ensureRunning() {
        if (tickerJob?.isActive != true) {
            tickerJob = scope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    controller.tick(clock.now())
                }
            }
        }
        if (observeJob?.isActive != true) {
            observeJob = scope.launch {
                combine(controller.state, controller.error) { state, error -> state to error }
                    .map { (state, error) -> NotificationKey.of(state) to error }
                    .distinctUntilChanged()
                    .collect { (_, error) ->
                        val state = controller.state.value
                        when {
                            error != null && state == null -> stopSelfSafely()
                            state == null -> Unit // jeszcze się ładuje
                            state.isOver -> {
                                notifications.notify(buildNotification(state))
                                // Karencja: efekty końca treningu (beep, wibracja, zapis) idą osobną kolejką kontrolera,
                                // a onDestroy zwalnia Cues. Bez tego ostatni sygnał ginie razem z serwisem.
                                delay(FINISH_GRACE_MS)
                                // W międzyczasie mógł ruszyć nowy trening (isActive) albo jego ładowanie (isStarting).
                                if (!controller.isActive && !controller.isStarting) {
                                    releaseWakeLock()
                                    stopSelfSafely()
                                }
                            }
                            else -> {
                                if (state.isRunning && !state.paused) acquireWakeLock() else releaseWakeLock()
                                notifications.notify(buildNotification(state))
                            }
                        }
                    }
            }
        }
        if (permissionJob?.isActive != true) {
            permissionJob = scope.launch {
                while (isActive) {
                    delay(PERMISSION_POLL_MS)
                    if (!notificationGate.update(notifications.canPost())) continue
                    if (!controller.isActive && !controller.isStarting) continue
                    // Zgoda nadana z Ustawień w trakcie treningu: rekord zdławiony przy starcie FGS nie wraca
                    // sam po zwykłym notify, trzeba go wystawić tą samą drogą co przy starcie.
                    runCatching { goForeground(controller.state.value) }
                        .onFailure { Log.w(TAG, "ponowny startForeground po nadaniu zgody", it) }
                }
            }
        }
    }

    private fun goForeground(state: EngineState?) {
        val notification = buildNotification(state)
        ServiceCompat.startForeground(this, PlayerNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun buildNotification(state: EngineState?): android.app.Notification {
        val active = controller.active.value
        val content = state?.let { NotificationContent.from(it, clock.now(), System.currentTimeMillis()) }
            ?: NotificationContent("Przygotowanie treningu", "")
        return notifications.build(content, active?.workout?.id, active?.workout?.name.orEmpty())
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) lock.release()
    }

    /** `stopSelfResult(lastStartId)`: jeśli po drodze przyszedł nowszy start (nowy trening), system nie zatrzyma serwisu. */
    private fun stopSelfSafely() {
        if (stopSelfResult(lastStartId)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } else {
            Log.i(TAG, "Nowszy start w kolejce, serwis zostaje")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        cues.release()
        super.onDestroy()
    }

    /** Klucz zmian stanu, po których trzeba przebudować powiadomienie; odliczanie robi chronometr sam. */
    private data class NotificationKey(
        val stepIndex: Int,
        val phase: Phase,
        val paused: Boolean,
        val step: Step?,
        val phaseStartedAt: Long,
        val pausedElapsedMs: Long,
        val stepsCount: Int,
    ) {
        companion object {
            fun of(state: EngineState?): NotificationKey? = state?.let {
                NotificationKey(it.stepIndex, it.phase, it.paused, it.currentStep, it.phaseStartedAt, it.pausedElapsedMs, it.steps.size)
            }
        }
    }

    companion object {
        private const val TAG = "WorkoutService"
        private const val TICK_MS = 250L
        private const val WAKE_LOCK_TIMEOUT_MS = 3 * 60 * 60 * 1000L
        /** Tyle serwis czeka po końcu treningu, żeby końcowy beep/wibracja/zapis zdążyły przejść kolejką efektów. */
        private const val FINISH_GRACE_MS = 1_500L

        /** Co ile sprawdzamy, czy zgoda na powiadomienia wróciła (nadana z Ustawień w trakcie treningu). */
        private const val PERMISSION_POLL_MS = 2_000L

        const val ACTION_START = "pl.mysttic.trainings.action.START"
        const val ACTION_PAUSE = "pl.mysttic.trainings.action.PAUSE"
        const val ACTION_RESUME = "pl.mysttic.trainings.action.RESUME"
        const val ACTION_SKIP = "pl.mysttic.trainings.action.SKIP"
        const val ACTION_DONE = "pl.mysttic.trainings.action.DONE"
        const val ACTION_ADD_REST = "pl.mysttic.trainings.action.ADD_REST"
        /** Akcja intentu do [pl.mysttic.trainings.MainActivity] z powiadomienia: otwórz player tego treningu. */
        const val ACTION_OPEN_PLAYER = "pl.mysttic.trainings.action.OPEN_PLAYER"
        const val EXTRA_WORKOUT_ID = "workoutId"

        /** Idempotentne: jeśli ten trening już trwa, kontroler nic nie zmienia. */
        fun start(context: Context, workoutId: String) {
            val intent = Intent(context, WorkoutService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_WORKOUT_ID, workoutId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
