package pl.mysttic.trainings.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import pl.mysttic.trainings.R
import java.util.Collections
import java.util.Locale

/**
 * Sygnały dla użytkownika: beepy, wibracja, komunikaty głosowe.
 * M2: SoundPool z własnymi próbkami (`res/raw`, generowane przez `tools/build_beeps.py`) i audio focus
 * `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, żeby muzyka z innej aplikacji ściszała się na czas sygnału.
 * Wołane z [WorkoutService] także przy zgaszonym ekranie.
 */
interface Cues {
    /** Inicjalizacja TTS i wczytanie próbek trwa chwilę; wołać przy starcie serwisu. */
    fun warmUp()
    fun beepShort()
    fun beepLong()
    /** Krótki sygnał rytmu (oddech co kilka sekund): bez audio focus, żeby metronom nie ściszał cudzej muzyki. */
    fun tick()
    fun vibrate()
    fun speak(text: String)
    fun release()
}

class AndroidCues(private val context: Context) : Cues {

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager: AudioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** USAGE_MEDIA: głośność sterowana suwakiem multimediów, tak jak wcześniej ToneGenerator na STREAM_MUSIC. */
    private val soundAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    /** Dla API < 33: to samo przeznaczenie, tyle że wyrażone atrybutami dźwięku. */
    private val vibrationAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var soundPool: SoundPool? = null
    private var shortId = 0
    private var longId = 0
    private val loaded: MutableSet<Int> = Collections.synchronizedSet(HashSet())

    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    private var focusRequest: AudioFocusRequest? = null
    private var focusHeld = false
    private val releaseFocus = Runnable { abandonFocus() }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @Synchronized
    override fun warmUp() {
        if (soundPool == null) {
            val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(soundAttributes).build()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) loaded += sampleId else Log.w(TAG, "Sample $sampleId load failed: $status")
            }
            shortId = pool.load(context, R.raw.beep_short, 1)
            longId = pool.load(context, R.raw.beep_long, 1)
            soundPool = pool
        }
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status -> onTtsInit(status) }
        }
    }

    private fun onTtsInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS init failed: $status")
            return
        }
        val engine = tts ?: return
        val result = engine.setLanguage(Locale.forLanguageTag("pl-PL"))
        ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ttsReady) {
            Log.w(TAG, "Polish TTS voice unavailable (result=$result)")
            return
        }
        engine.setAudioAttributes(speechAttributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = scheduleFocusRelease(SPEECH_TAIL_MS)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = scheduleFocusRelease(0L)
            override fun onError(utteranceId: String?, errorCode: Int) = scheduleFocusRelease(0L)
        })
    }

    override fun beepShort() = play(shortId)

    override fun beepLong() = play(longId)

    /**
     * Ta sama próbka co beep, ale bez [requestFocus] i bez odkładania zwolnienia: sygnał wraca co 3 s przez
     * półtorej minuty, więc ściszanie i przywracanie cudzej muzyki przy każdym byłoby gorsze niż sam sygnał.
     */
    override fun tick() {
        warmUp()
        val pool = soundPool ?: return
        if (shortId !in loaded) return
        handler.post { pool.play(shortId, 1f, 1f, 1, 0, 1f) }
    }

    private fun play(sampleId: Int) {
        warmUp()
        val pool = soundPool ?: return
        if (sampleId !in loaded) return
        handler.post {
            requestFocus()
            pool.play(sampleId, 1f, 1f, 1, 0, 1f)
            scheduleFocusRelease(BEEP_FOCUS_MS)
        }
    }

    /**
     * Sygnał w trakcie serii, nie haptyka dotyku. Bez podanego przeznaczenia system klasyfikuje wibrację
     * jako `USAGE_TOUCH` i pomija ją, gdy wyłączone jest dotykowe sprzężenie zwrotne — na telefonie
     * widać to w `dumpsys vibrator_manager` jako `ignored_for_settings`. `USAGE_ALARM` mówi wprost,
     * że to odmierzanie czasu w trwającym treningu, który użytkownik sam uruchomił.
     */
    override fun vibrate() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, vibrationAttributes)
        }
    }

    override fun speak(text: String) {
        if (!ttsReady) return
        val engine = tts ?: return
        handler.post {
            requestFocus()
            handler.removeCallbacks(releaseFocus)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue-${System.nanoTime()}")
        }
    }

    /** Główny wątek. */
    private fun requestFocus() {
        if (focusHeld) return
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttributes)
            .setWillPauseWhenDucked(false)
            .build()
            .also { focusRequest = it }
        focusHeld = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Z dowolnego wątku (callbacki TTS przychodzą z wątku silnika). */
    private fun scheduleFocusRelease(delayMs: Long) {
        handler.removeCallbacks(releaseFocus)
        handler.postDelayed(releaseFocus, delayMs)
    }

    /** Główny wątek. */
    private fun abandonFocus() {
        if (!focusHeld) return
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusHeld = false
    }

    @Synchronized
    override fun release() {
        handler.removeCallbacks(releaseFocus)
        handler.post { abandonFocus() }
        runCatching { soundPool?.release() }
        soundPool = null
        loaded.clear()
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    companion object {
        private const val TAG = "AndroidCues"
        private const val VIBRATION_MS = 200L
        /** Beep trwa do 450 ms; focus oddajemy chwilę później, żeby seria 3-2-1 nie migała ściszaniem. */
        private const val BEEP_FOCUS_MS = 1_200L
        private const val SPEECH_TAIL_MS = 300L
    }
}
