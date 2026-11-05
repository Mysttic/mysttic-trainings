package pl.mysttic.trainings.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.reminder.ReminderSchedule
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Codzienne przypomnienie o treningu (M6). Alarm jest jednorazowy i przestawiany po każdym odpaleniu:
 * `…AndAllowWhileIdle` przebija się przez tryb czuwania, a `setRepeating` w nim nie odpala.
 *
 * Wersja niedokładna dostaje od systemu godzinne okno, a „przypomnij o 18:00” z odpaleniem o 18:47 nie ma sensu,
 * więc prosimy o alarm dokładny (`USE_EXACT_ALARM`, przyznawane przy instalacji; aplikacja jest sideloadowana, D-12).
 * Gdy uprawnienia zabraknie, schodzimy na wariant niedokładny zamiast rzucać wyjątkiem.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Ustawia albo kasuje alarm zgodnie z ustawieniami. Wołane po zmianie ustawień, po odpaleniu i po starcie systemu. */
    suspend fun sync() {
        val settings = settingsRepo.current()
        if (!settings.reminderEnabled) {
            cancel()
            return
        }
        schedule(settings.reminderHour, settings.reminderMinute)
    }

    private fun schedule(hour: Int, minute: Int) {
        val at = ReminderSchedule.next(LocalDateTime.now(), hour, minute)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent()
        runCatching {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        }.onFailure { Log.e(TAG, "Nie udało się ustawić przypomnienia", it) }
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "ReminderScheduler"
        private const val REQUEST_CODE = 100
    }
}
