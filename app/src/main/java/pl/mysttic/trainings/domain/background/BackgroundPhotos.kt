package pl.mysttic.trainings.domain.background

import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.MuscleGroup

/*
 * Zdjęcie dnia w tle (D-51): rozmyte zdjęcie z bazy ćwiczeń, dobrane do dzisiejszego treningu i do tego, kogo
 * użytkownik chce widzieć na zdjęciach. Czysty Kotlin: skąd wziąć zdjęcia i trening wie repozytorium, jak je
 * pokazać — motyw. Tu jest tylko reguła wyboru, więc da się ją sprawdzić testem.
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
    }
}

/** Zdjęcie tła z bazy; `image` to ścieżka jak u ilustracji ćwiczeń: asset bazy wbudowanej albo plik pobranej (D-47). */
data class BackgroundPhoto(val id: String, val topic: PhotoTopic, val sex: PhotoSex, val image: String)

object PhotoPicker {

    private val CORE_MUSCLES = setOf(MuscleGroup.ABS, MuscleGroup.LOWER_BACK)
    private val LEG_MUSCLES = setOf(
        MuscleGroup.GLUTES, MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS,
        MuscleGroup.CALVES, MuscleGroup.ADDUCTORS, MuscleGroup.ABDUCTORS,
    )

    /**
     * Temat dnia: ten, do którego należy najwięcej ćwiczeń treningu. Remis rozstrzyga kolejność [PhotoTopic],
     * dzień bez treningu dostaje całe ciało. Joga nie jest kategorią bazy, więc stoi za nią rozciąganie i oddech.
     */
    fun topicOf(exercises: List<Exercise>): PhotoTopic {
        if (exercises.isEmpty()) return PhotoTopic.FULL_BODY
        val counts = exercises.groupingBy(::topicOf).eachCount()
        return PhotoTopic.entries.maxBy { counts[it] ?: 0 }
    }

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
     * Zdjęcie tematu dla wybranych osób. Losowe, ale to samo dla tego samego ziarna (dzień i trening), żeby tło
     * nie zmieniało się z ekranu na ekran. Brak zdjęć tematu = całe ciało, a gdy i tego brak, dowolne pasujące osobami.
     */
    fun pick(photos: List<BackgroundPhoto>, topic: PhotoTopic, people: PhotoPeople, seed: String): BackgroundPhoto? {
        val allowed = photos.filter { people.allows(it.sex) }
        val pool = allowed.filter { it.topic == topic }
            .ifEmpty { allowed.filter { it.topic == PhotoTopic.FULL_BODY } }
            .ifEmpty { allowed }
            .sortedBy { it.id }
        if (pool.isEmpty()) return null
        return pool[Math.floorMod(seed.hashCode(), pool.size)]
    }

    /** Ziarno losowania: nowy dzień albo kolejny trening z kolejki dają nowe zdjęcie. */
    fun seed(epochDay: Long, workoutId: String?): String = "$epochDay/${workoutId.orEmpty()}"
}
