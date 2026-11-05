package pl.mysttic.trainings.domain.plan

import pl.mysttic.trainings.domain.plan.preset.PresetId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Skąd wziął się plan (D-42). Źródło decyduje o tym, co wolno z planem zrobić w miejscu. */
enum class PlanSource(val labelPl: String) {
    /** Z kreatora (D-20). */
    WIZARD("Z kreatora"),
    /** Gotowy program z katalogu (D-37). */
    PRESET("Gotowy program"),
    /** Ułożony albo skopiowany przez użytkownika. */
    CUSTOM("Twój plan"),
    /** Wczytany z pliku od kogoś. */
    IMPORTED("Od kogoś"),
    ;

    companion object {
        fun parse(value: String?): PlanSource? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Zapisany cykl treningowy: profil, z którego powstał, rozkład dni i szablony treningów.
 * Numeracja dni liczona kalendarzowo (dzień 1 = data startu), nie po 24 godzinach,
 * żeby trening o 6 rano i o 22 tego samego dnia trafiały w ten sam dzień planu.
 */
data class Program(
    val id: String,
    val name: String,
    val profile: TrainingProfile,
    val startedAt: Long,
    val cycleDays: Int,
    val createdAt: Long,
    val active: Boolean,
    val days: List<PlanDay>,
    val source: PlanSource = if (profile.isPreset) PlanSource.PRESET else PlanSource.WIZARD,
    val description: String = "",
    /** Kto ułożył plan; pusty, gdy plan jest z kreatora albo z katalogu. */
    val author: String = "",
    /** Plan, z którego ten powstał (duplikat, powtórka). */
    val originId: String? = null,
    /** Tożsamość planu między telefonami, nadawana przy pierwszym eksporcie do pliku. */
    val shareUid: String? = null,
    val updatedAt: Long = createdAt,
    /**
     * Kotwica rytmu dni (odpoczynki, seria): przesuwa się przy wznowieniu wstrzymanego planu, żeby dzień odpoczynku
     * wypadał tak, jak w rozkładzie, licząc od miejsca w kolejce. 0 = od [startedAt].
     */
    val rhythmStart: Long = 0,
) {
    private val rhythmAnchor: Long get() = if (rhythmStart > 0) rhythmStart else startedAt

    /** Numer dnia cyklu dla podanej chwili; 1 w dniu startu, może przekroczyć [cycleDays] po zakończeniu. */
    fun dayIndexAt(now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val start = LocalDate.ofInstant(Instant.ofEpochMilli(rhythmAnchor), zone)
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        return ChronoUnit.DAYS.between(start, today).toInt() + 1
    }

    /** Data dnia planu według rytmu (po wznowieniu liczona od kotwicy). */
    fun dateOf(dayIndex: Int, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        LocalDate.ofInstant(Instant.ofEpochMilli(rhythmAnchor), zone).plusDays((dayIndex - 1).toLong())

    fun isFinished(now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean = dayIndexAt(now, zone) > cycleDays

    /** Ile dni cyklu zostało (0 gdy skończony). */
    fun daysLeft(now: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        (cycleDays - dayIndexAt(now, zone) + 1).coerceAtLeast(0)

    /**
     * Czy plan przewidywał na ten dzień odpoczynek. Rozkład dni zostaje rytmem tygodnia,
     * mimo że treningi rozliczamy kolejką (D-21) — dzięki temu seria nie kasuje się w dni wolne.
     * Data spoza cyklu nie jest dniem odpoczynku, więc seria nie przechodzi w nieskończoność wstecz.
     */
    fun isRestDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val start = LocalDate.ofInstant(Instant.ofEpochMilli(rhythmAnchor), zone)
        val index = ChronoUnit.DAYS.between(start, date).toInt() + 1
        if (index < 1 || index > cycleDays) return false
        return days.firstOrNull { it.dayIndex == index }?.isRest == true
    }

    /** Cykl z gotowego programu (D-37): stały protokół, bez kreatora, bez progresji celów. */
    val presetId: String? get() = if (source == PlanSource.PRESET) profile.presetId else null
    val isPreset: Boolean get() = source == PlanSource.PRESET
    val isWizard: Boolean get() = source == PlanSource.WIZARD

    /** Własny plan i plan z kreatora zmienia się w miejscu; gotowy program i plan od kogoś — przez kopię (D-42). */
    val editableInPlace: Boolean get() = source == PlanSource.WIZARD || source == PlanSource.CUSTOM

    companion object {
        fun nameFor(profile: TrainingProfile): String =
            profile.presetId?.let { id -> PresetId.entries.firstOrNull { it.name == id }?.labelPl }
                ?: "Plan na ${profile.cycleDays} dni · ${profile.goal.labelPl.lowercase()}"
    }
}
