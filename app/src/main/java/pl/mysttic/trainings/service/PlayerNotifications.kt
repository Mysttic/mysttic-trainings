package pl.mysttic.trainings.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.mysttic.trainings.MainActivity
import pl.mysttic.trainings.R

/** Kanał, budowanie i aktualizacja powiadomienia foreground service playera. */
class PlayerNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Kanał ma `IMPORTANCE_DEFAULT`, nie `LOW`: telefon z ustawieniem „ukryj ciche powiadomienia”
     * nie pokazuje kanałów o niskiej wadze na ekranie blokady, a to jedyne miejsce, w które zerka się
     * w trakcie treningu. Dźwięku i tak nie ma — kanał nie ma sygnału ani wibracji, a samo powiadomienie
     * jest budowane z `setSilent(true)`.
     *
     * Ustawień istniejącego kanału nie da się zmienić z kodu, więc podniesienie wagi wymagało nowego
     * identyfikatora; stary kasujemy, żeby nie został w ustawieniach jako martwy wpis.
     */
    fun ensureChannel() {
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Trening w toku")
                .setDescription("Bieżący krok treningu, odliczanie i sterowanie")
                .setSound(null, null)
                .setVibrationEnabled(false)
                .setShowBadge(false)
                .build(),
        )
    }

    fun build(content: NotificationContent, workoutId: String?, workoutName: String): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_player)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(workoutName.ifBlank { null })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPlayer(workoutId))
        when {
            content.countDownTo != null -> builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(content.countDownTo).setShowWhen(true)
            content.countUpFrom != null -> builder.setUsesChronometer(true).setWhen(content.countUpFrom).setShowWhen(true)
            else -> builder.setShowWhen(false)
        }
        if (content.showPause) builder.addAction(0, "Pauza", serviceAction(WorkoutService.ACTION_PAUSE, 1))
        if (content.showResume) builder.addAction(0, "Wznów", serviceAction(WorkoutService.ACTION_RESUME, 2))
        if (content.showDone) builder.addAction(0, "Zrobione", serviceAction(WorkoutService.ACTION_DONE, 3))
        if (content.showAddRest) builder.addAction(0, "+15 s", serviceAction(WorkoutService.ACTION_ADD_REST, 4))
        if (content.showSkip) builder.addAction(0, "Pomiń", serviceAction(WorkoutService.ACTION_SKIP, 5))
        return builder.build()
    }

    /**
     * Aktualizacja bez ponownego `startForeground`; bez zgody na powiadomienia (Android 13+) nic nie pokazuje.
     * Sama zgoda nadana później tą drogą powiadomienia nie odzyska — od tego jest [canPost] i [NotificationGate]
     * w serwisie, który wystawia je wtedy od nowa.
     */
    fun notify(notification: Notification) {
        if (!canPost()) return
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /** Czy system wpuści teraz powiadomienie: zgoda POST_NOTIFICATIONS (13+) i włączone powiadomienia aplikacji. */
    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WorkoutService::class.java).setAction(action)
        return PendingIntent.getForegroundService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openPlayer(workoutId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(WorkoutService.ACTION_OPEN_PLAYER)
            .putExtra(WorkoutService.EXTRA_WORKOUT_ID, workoutId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_ID = "player_v2"

        /** Kanał z wersji do 0.3.1 włącznie: cichy, więc niewidoczny na ekranie blokady. */
        private const val LEGACY_CHANNEL_ID = "player"
        const val NOTIFICATION_ID = 1
    }
}
