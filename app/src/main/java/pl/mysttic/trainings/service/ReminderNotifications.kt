package pl.mysttic.trainings.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.mysttic.trainings.MainActivity
import pl.mysttic.trainings.R
import pl.mysttic.trainings.domain.reminder.ReminderSchedule

/** Powiadomienie przypomnienia — osobny kanał niż player, bo to jedyne, które ma prawo zadzwonić. */
class ReminderNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun show(text: String) = show(ReminderSchedule.TITLE, text)

    /** [title] zależy od rodzaju cyklu (trening albo oddech, D-37); kanał zostaje ten sam. */
    fun show(title: String, text: String) {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Przypomnienie o treningu")
                .setDescription("Codzienne przypomnienie o godzinie z ustawień")
                .build(),
        )
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_player)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "reminder"
        private const val NOTIFICATION_ID = 2001
    }
}
