package pl.mysttic.trainings.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.mysttic.trainings.data.repo.ExerciseRepository
import pl.mysttic.trainings.data.repo.ProgramRepository
import pl.mysttic.trainings.data.repo.SessionRepository
import pl.mysttic.trainings.data.repo.WorkoutRepository
import pl.mysttic.trainings.data.settings.SettingsRepository
import pl.mysttic.trainings.domain.engine.WorkoutPlanner
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.plan.PlanSessions
import pl.mysttic.trainings.domain.plan.preset.PresetCatalog
import pl.mysttic.trainings.domain.plan.preset.ProgramKind
import pl.mysttic.trainings.domain.reminder.ReminderSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Zależności odbiornika wyjmowane wprost z grafu — patrz komentarz przy [ReminderReceiver]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderDeps {
    fun scheduler(): ReminderScheduler
    fun settingsRepo(): SettingsRepository
    fun sessionRepo(): SessionRepository
    fun programRepo(): ProgramRepository
    fun workoutRepo(): WorkoutRepository
    fun exerciseRepo(): ExerciseRepository
}

/**
 * Odbiera alarm przypomnienia i restart systemu. Decyzję „czy w ogóle przypominać” podejmuje
 * [ReminderSchedule]; tutaj zostaje odpytanie bazy i pokazanie powiadomienia.
 *
 * Zależności bierzemy przez [EntryPointAccessors], a nie przez `@AndroidEntryPoint` z polami `@Inject`:
 * w odbiorniku Hilt wstrzykuje dopiero w `super.onReceive`, a tego w Kotlinie nie da się wywołać wprost
 * (`onReceive` w `BroadcastReceiver` jest abstrakcyjne). Wcześniej pola zostawały niezainicjowane,
 * pierwsze odwołanie leciało `UninitializedPropertyAccessException` prosto w `catch` niżej
 * i całe przypomnienie (D-29) milczało bez śladu poza logcatem.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val deps = EntryPointAccessors.fromApplication(app, ReminderDeps::class.java)
        val remind = intent.action == ACTION_REMIND
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (remind) notifyIfDue(app, deps)
                // Alarm jest jednorazowy, więc po każdym odpaleniu (i po restarcie telefonu) ustawiamy następny.
                deps.scheduler().sync()
            } catch (t: Throwable) {
                Log.e(TAG, "Przypomnienie nie doszło do skutku", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun notifyIfDue(context: Context, deps: ReminderDeps) {
        val settingsRepo = deps.settingsRepo()
        val sessionRepo = deps.sessionRepo()
        if (!settingsRepo.current().reminderEnabled) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val program = deps.programRepo().active()
        val next = program?.let { p ->
            PlanSessions.progress(sessionRepo.allSessions(), p).next?.workoutId
        }
        val lastDone = sessionRepo.allSessions()
            .filter { it.status == SessionStatus.DONE }
            .maxByOrNull { it.startedAt }
            ?.let { LocalDate.ofInstant(Instant.ofEpochMilli(it.startedAt), zone) }

        if (!ReminderSchedule.shouldRemind(today, lastDone, hasNextWorkout = next != null)) return

        val workout = next?.let { deps.workoutRepo().getById(it) }
        val minutes = workout?.let {
            val planner = WorkoutPlanner()
            val settings = settingsRepo.current()
            val steps = planner.expand(
                it, deps.exerciseRepo().getMap(), settings.difficulty, settings.ownedEquipment, settings.countdownSec,
            )
            (planner.estimateSeconds(steps) + 59) / 60
        } ?: 0
        // Tytuł zależy od rodzaju cyklu (D-37): praktykę oddechową zapowiadamy inaczej niż trening.
        val kind = program?.profile?.let { PresetCatalog.kindOf(it) } ?: ProgramKind.TRAINING
        ReminderNotifications(context).show(ReminderSchedule.title(kind), ReminderSchedule.message(workout?.name, minutes))
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_REMIND = "pl.mysttic.trainings.action.REMIND"
    }
}
