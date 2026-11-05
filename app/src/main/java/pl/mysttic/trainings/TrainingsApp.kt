package pl.mysttic.trainings

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.seed.SeedImporter
import pl.mysttic.trainings.service.ReminderScheduler
import javax.inject.Inject

@HiltAndroidApp
class TrainingsApp : Application() {

    @Inject
    lateinit var seedImporter: SeedImporter

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { seedImporter.ensureSeeded() }
                .onFailure { Log.e(TAG, "Seed import failed", it) }
            // Alarm jest jednorazowy i przestawiany po odpaleniu; przy starcie aplikacji upewniamy się, że stoi.
            runCatching { reminderScheduler.sync() }
                .onFailure { Log.e(TAG, "Reminder sync failed", it) }
        }
    }

    companion object {
        private const val TAG = "TrainingsApp"
    }
}
