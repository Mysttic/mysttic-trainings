package pl.mysttic.trainings.domain.plan

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rytm dni po przełączeniu planu (D-42). Wznowiony plan liczy odpoczynki od miejsca w kolejce, a nie od dnia,
 * w którym go kiedyś zaczęto: następny dzień kolejki wypada dziś.
 */
object PlanRhythm {

    fun anchorFor(progress: ProgramProgress, now: Long, zone: ZoneId): Long {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val nextIndex = progress.next?.dayIndex ?: 1
        return today.minusDays((nextIndex - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
