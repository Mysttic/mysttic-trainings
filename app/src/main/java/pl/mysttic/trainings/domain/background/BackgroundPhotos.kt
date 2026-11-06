package pl.mysttic.trainings.domain.background

import pl.mysttic.trainings.domain.model.Gender

import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.plan.PlanSessions
import pl.mysttic.trainings.domain.plan.Program
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/*
 * Zdjęcie dnia w tle (D-51): rozmyte zdjęcie z bazy ćwiczeń, losowane raz dziennie z tematów, których najwięcej
 * w dzisiejszych treningach ze wszystkich planów, i z osobami, które użytkownik chce widzieć. Tło tylko nawiązuje
 * do dnia: nic w aplikacji nie mówi, jaką partię dziś ćwiczysz. Czysty Kotlin: skąd wziąć zdjęcia i treningi wie
 * repozytorium, jak je pokazać — motyw. Tu jest tylko reguła wyboru, więc da się ją sprawdzić testem.
 */

/** Temat zdjęcia. Nazwy idą do `baza/tla.json` (pole `topic`), więc istniejących nie zmieniamy. */
enum class PhotoTopic(val labelPl: String) {
    CORE("brzuch"),
    LEGS("nogi"),
    UPPER("góra ciała"),
    FULL_BODY("całe ciało"),
    CARDIO("kardio"),
    YOGA("joga"),
}

/** Kto jest na zdjęciu w bazie (pole `sex` w `baza/tla.json`). */
enum class PhotoSex { FEMALE, MALE }

/** Kogo pokazywać na zdjęciach: wybór z Ustawień. `id` idzie do DataStore i do kopii zapasowej. */
enum class PhotoPeople(val id: String, val labelPl: String) {
    WOMEN("kobieta", "Kobieta"),
    MEN("mezczyzna", "Mężczyzna"),
    BOTH("oboje", "Oboje"),
    ;

    fun allows(sex: PhotoSex): Boolean = when (this) {
        WOMEN -> sex == PhotoSex.FEMALE
        MEN -> sex == PhotoSex.MALE
        BOTH -> true
    }

    companion object {
        /** Bez wyboru i przy nieznanym id pokazujemy oboje: aplikacja nie zna płci użytkownika, dopóki jej nie poda. */
        fun fromId(id: String?): PhotoPeople = entries.firstOrNull { it.id == id } ?: BOTH

        /** Osoby na zdjęciu dnia z płci w „O mnie” (D-57): bez wyboru oboje, bo aplikacja nie zgaduje. */
        fun of(gender: Gender): PhotoPeople = when (gender) {
            Gender.FEMALE -> WOMEN
            Gender.MALE -> MEN
            Gender.UNSPECIFIED -> BOTH
        }
    }
}

/** Zdjęcie tła z bazy; `image` to ścieżka jak u ilustracji ćwiczeń: asset bazy wbudowanej albo plik pobranej (D-47). */
data class BackgroundPhoto(val id: String, val topic: PhotoTopic, val sex: PhotoSex, val image: String)

object PhotoPicker {

    /** Ile tematów dnia bierze udział w losowaniu: właściciel chce zdjęcia z trzech, których jest najwięcej. */
    const val TOP_TOPICS = 3

    /** Plan, z którego był trening w ostatnim tygodniu, jest w użyciu, nawet gdy nie jest aktywny. */
    const val RECENT_DAYS = 7L

    private val CORE_MUSCLES = setOf(MuscleGroup.ABS, MuscleGroup.LOWER_BACK)
    private val LEG_MUSCLES = setOf(
        MuscleGroup.GLUTES, MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS,
        MuscleGroup.CALVES, MuscleGroup.ADDUCTORS, MuscleGroup.ABDUCTORS,
    )

    /**
     * Treningi dnia, z których liczy się temat zdjęcia. W ciągu dnia można trenować z kilku planów (D-42 pozwala
     * przełączać), więc: z planu, z którego dziś był trening, bierzemy to, co z niego dziś zrobione; z planu aktywnego
     * i z planów używanych w ostatnim tygodniu ([RECENT_DAYS]), z których dziś jeszcze nic nie było, następny trening
     * z kolejki; do tego treningi spoza planów zrobione dziś. Zrobiony trening zostaje w składzie dnia, więc zdjęcie
     * nie zmienia się po treningu. Id mogą się powtarzać: trening zrobiony dwa razy liczy się dwa razy.
     */
    fun todaysWorkoutIds(programs: List<Program>, sessions: List<Session>, today: LocalDate, zone: ZoneId): List<String> {
        fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val ids = mutableListOf<String>()
        for (program in programs) {
            val done = PlanSessions.done(sessions, program)
            val doneToday = done.filter { dayOf(it.startedAt) == today }
            if (doneToday.isNotEmpty()) {
                ids += doneToday.mapNotNull { it.workoutId }
                continue
            }
            val recent = done.any { !dayOf(it.startedAt).isBefore(today.minusDays(RECENT_DAYS)) }
            if (program.active || recent) PlanSessions.progress(sessions, program).next?.workoutId?.let { ids += it }
        }
        ids += sessions
            .filter { s -> s.status == SessionStatus.DONE && dayOf(s.startedAt) == today && programs.none { PlanSessions.belongsTo(s, it) } }
            .mapNotNull { it.workoutId }
        return ids
    }

    /**
     * Do [TOP_TOPICS] tematów dnia z liczbą ćwiczeń, od największej; remis rozstrzyga kolejność [PhotoTopic].
     * Joga nie jest kategorią bazy, więc stoi za nią rozciąganie i oddech. Dzień bez treningów = pusta lista.
     */
    fun topTopics(exercises: List<Exercise>): List<Pair<PhotoTopic, Int>> =
        exercises.groupingBy(::topicOf).eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<PhotoTopic, Int>> { it.value }.thenBy { it.key.ordinal })
            .take(TOP_TOPICS)
            .map { it.key to it.value }

    /** Kardio i plyometria to kardio, rozciąganie i oddech to joga, siła idzie za partiami głównymi. */
    fun topicOf(exercise: Exercise): PhotoTopic = when (exercise.category) {
        ExerciseCategory.STRETCHING, ExerciseCategory.BREATHING -> PhotoTopic.YOGA
        ExerciseCategory.CARDIO, ExerciseCategory.PLYOMETRICS -> PhotoTopic.CARDIO
        ExerciseCategory.STRENGTH -> regionOf(exercise.primaryMuscles)
    }

    /** Partia, której jest najwięcej; remis albo `FULL_BODY` to całe ciało (przysiad z wyciskaniem to nie same nogi). */
    private fun regionOf(muscles: List<MuscleGroup>): PhotoTopic {
        if (muscles.isEmpty() || MuscleGroup.FULL_BODY in muscles) return PhotoTopic.FULL_BODY
        val regions = muscles.groupingBy { muscle ->
            when (muscle) {
                in CORE_MUSCLES -> PhotoTopic.CORE
                in LEG_MUSCLES -> PhotoTopic.LEGS
                else -> PhotoTopic.UPPER
            }
        }.eachCount()
        val top = regions.values.max()
        val leaders = regions.filterValues { it == top }.keys
        return leaders.singleOrNull() ?: PhotoTopic.FULL_BODY
    }

    /**
     * Zdjęcie dnia dla wybranych osób: temat losowany spośród [topics] z wagą liczby ćwiczeń, potem zdjęcie tego tematu.
     * Tło ma tylko nawiązywać do dnia, nie ogłaszać partii, więc nawet temat, którego jest najwięcej, nie wygrywa zawsze.
     * Losowanie zależy tylko od [seed] (numer dnia), więc przez dzień zdjęcie się nie zmienia. Temat bez zdjęć dla tych
     * osób odpada; dzień bez treningów albo bez pasujących tematów losuje z wszystkich zdjęć tych osób.
     */
    fun pick(photos: List<BackgroundPhoto>, topics: List<Pair<PhotoTopic, Int>>, people: PhotoPeople, seed: Long): BackgroundPhoto? {
        val allowed = photos.filter { people.allows(it.sex) }.sortedBy { it.id }
        if (allowed.isEmpty()) return null
        val random = Random(seed)
        val candidates = topics.filter { (topic, count) -> count > 0 && allowed.any { it.topic == topic } }
        val pool = if (candidates.isEmpty()) {
            allowed
        } else {
            val topic = weighted(candidates, random)
            allowed.filter { it.topic == topic }
        }
        return pool[random.nextInt(pool.size)]
    }

    private fun weighted(topics: List<Pair<PhotoTopic, Int>>, random: Random): PhotoTopic {
        var draw = random.nextInt(topics.sumOf { it.second })
        for ((topic, count) in topics) {
            if (draw < count) return topic
            draw -= count
        }
        return topics.last().first
    }
}
