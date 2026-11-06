package pl.mysttic.trainings.domain.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.Gender
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup
import pl.mysttic.trainings.domain.model.Session
import pl.mysttic.trainings.domain.model.SessionStatus
import pl.mysttic.trainings.domain.plan.PlanDay
import pl.mysttic.trainings.domain.plan.Program
import pl.mysttic.trainings.domain.plan.TrainingProfile
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Zdjęcie dnia (D-51): skład dnia to treningi ze wszystkich planów w użyciu, z niego trzy tematy, których jest
 * najwięcej, a z nich losowanie z wagą liczby ćwiczeń, stałe przez dzień. Tło ma nawiązywać do dnia, nie ogłaszać
 * partii, więc nawet temat, którego jest najwięcej, nie wygrywa zawsze.
 */
class PhotoPickerTest {

    private fun exercise(id: String, category: ExerciseCategory, vararg primary: MuscleGroup) = Exercise(
        id = id,
        nameEn = id,
        namePl = id,
        kind = ExerciseKind.REPS,
        level = Difficulty.INTERMEDIATE,
        category = category,
        equipment = emptySet(),
        primaryMuscles = primary.toList(),
        secondaryMuscles = emptyList(),
        instructionsPl = emptyList(),
        images = emptyList(),
    )

    private fun strength(id: String, vararg primary: MuscleGroup) = exercise(id, ExerciseCategory.STRENGTH, *primary)

    private fun photo(id: String, topic: PhotoTopic, sex: PhotoSex) = BackgroundPhoto(id, topic, sex, "tla/$id.jpg")

    /** Każdy temat dla obu płci, jak w bazie (`SeedIntegrityTest`). */
    private val baza = PhotoTopic.entries.flatMap { topic ->
        listOf(photo("${topic.name.lowercase()}-k", topic, PhotoSex.FEMALE), photo("${topic.name.lowercase()}-m", topic, PhotoSex.MALE))
    }

    // ---- tematy z ćwiczeń ----

    @Test
    fun `kardio i plyometria to kardio, rozciaganie i oddech to joga, sila idzie za partiami glownymi`() {
        assertEquals(PhotoTopic.CARDIO, PhotoPicker.topicOf(exercise("burpee", ExerciseCategory.PLYOMETRICS, MuscleGroup.FULL_BODY)))
        assertEquals(PhotoTopic.CARDIO, PhotoPicker.topicOf(exercise("bieg", ExerciseCategory.CARDIO, MuscleGroup.QUADS)))
        assertEquals(PhotoTopic.YOGA, PhotoPicker.topicOf(exercise("kobra", ExerciseCategory.STRETCHING, MuscleGroup.ABS)))
        assertEquals(PhotoTopic.YOGA, PhotoPicker.topicOf(exercise("oddech", ExerciseCategory.BREATHING)))
        assertEquals(PhotoTopic.LEGS, PhotoPicker.topicOf(strength("lunge", MuscleGroup.QUADS, MuscleGroup.GLUTES)))
        assertEquals(PhotoTopic.UPPER, PhotoPicker.topicOf(strength("pushup", MuscleGroup.CHEST, MuscleGroup.TRICEPS)))
        assertEquals(PhotoTopic.CORE, PhotoPicker.topicOf(strength("superman", MuscleGroup.LOWER_BACK)))
    }

    @Test
    fun `remis partii, cale cialo i brak partii to cale cialo`() {
        assertEquals(PhotoTopic.FULL_BODY, PhotoPicker.topicOf(strength("thruster", MuscleGroup.QUADS, MuscleGroup.SHOULDERS)))
        assertEquals(PhotoTopic.FULL_BODY, PhotoPicker.topicOf(strength("burpee", MuscleGroup.FULL_BODY, MuscleGroup.CHEST)))
        assertEquals(PhotoTopic.FULL_BODY, PhotoPicker.topicOf(strength("cos")))
    }

    @Test
    fun `do trzech tematow dnia od najliczniejszego, remis wedlug kolejnosci tematow`() {
        val dzien = listOf(
            strength("squat", MuscleGroup.QUADS), strength("lunge", MuscleGroup.GLUTES), strength("bridge", MuscleGroup.GLUTES),
            strength("crunch", MuscleGroup.ABS), strength("plank", MuscleGroup.ABS),
            strength("pushup", MuscleGroup.CHEST),
            exercise("kobra", ExerciseCategory.STRETCHING),
        )
        assertEquals(
            listOf(PhotoTopic.LEGS to 3, PhotoTopic.CORE to 2, PhotoTopic.UPPER to 1),
            PhotoPicker.topTopics(dzien),
        )
        assertEquals(listOf(PhotoTopic.CORE to 1, PhotoTopic.LEGS to 1), PhotoPicker.topTopics(listOf(strength("a", MuscleGroup.QUADS), strength("b", MuscleGroup.ABS))))
        assertEquals(emptyList<Pair<PhotoTopic, Int>>(), PhotoPicker.topTopics(emptyList()))
    }

    // ---- losowanie ----

    private val tematyDnia = listOf(PhotoTopic.LEGS to 4, PhotoTopic.CORE to 2, PhotoTopic.YOGA to 1)

    @Test
    fun `zdjecie jest z jednego z trzech tematow dnia i pasuje do osob z ustawien`() {
        (0L until 200L).forEach { dzien ->
            val k = PhotoPicker.pick(baza, tematyDnia, PhotoPeople.WOMEN, dzien)!!
            assertEquals(PhotoSex.FEMALE, k.sex)
            assertTrue("$dzien: ${k.topic} spoza tematów dnia", k.topic in tematyDnia.map { it.first })
            assertEquals(PhotoSex.MALE, PhotoPicker.pick(baza, tematyDnia, PhotoPeople.MEN, dzien)!!.sex)
        }
    }

    @Test
    fun `losowanie ma wage liczby cwiczen, ale wygrywa nie zawsze ten sam temat`() {
        val tematy = (0L until 700L).map { PhotoPicker.pick(baza, tematyDnia, PhotoPeople.BOTH, it)!!.topic }
        val ile = tematy.groupingBy { it }.eachCount()
        assertEquals("każdy z trzech tematów ma swoje dni", setOf(PhotoTopic.LEGS, PhotoTopic.CORE, PhotoTopic.YOGA), ile.keys)
        assertTrue("najliczniejszy temat wypada najczęściej: $ile", ile.getValue(PhotoTopic.LEGS) > ile.getValue(PhotoTopic.CORE))
        assertTrue("waga liczby ćwiczeń: $ile", ile.getValue(PhotoTopic.CORE) > ile.getValue(PhotoTopic.YOGA))
    }

    @Test
    fun `ten sam dzien to to samo zdjecie, niezaleznie od kolejnosci zdjec w bazie`() {
        (0L until 50L).forEach { dzien ->
            assertEquals(
                PhotoPicker.pick(baza, tematyDnia, PhotoPeople.BOTH, dzien),
                PhotoPicker.pick(baza.reversed(), tematyDnia, PhotoPeople.BOTH, dzien),
            )
        }
    }

    @Test
    fun `temat bez zdjec odpada, dzien bez treningow losuje z wszystkich, a brak zdjec to brak tla`() {
        val bezNog = baza.filter { it.topic != PhotoTopic.LEGS }
        (0L until 100L).forEach { dzien ->
            val zdjecie = PhotoPicker.pick(bezNog, tematyDnia, PhotoPeople.BOTH, dzien)!!
            assertTrue(zdjecie.topic in setOf(PhotoTopic.CORE, PhotoTopic.YOGA))
        }
        val bezTreningu = (0L until 300L).map { PhotoPicker.pick(baza, emptyList(), PhotoPeople.BOTH, it)!!.topic }.toSet()
        assertTrue("bez treningów losuje z różnych tematów: $bezTreningu", bezTreningu.size > 3)
        assertNull(PhotoPicker.pick(emptyList(), tematyDnia, PhotoPeople.BOTH, 1))
        assertNull(PhotoPicker.pick(baza.filter { it.sex == PhotoSex.FEMALE }, tematyDnia, PhotoPeople.MEN, 1))
    }

    @Test
    fun `osoby na zdjeciu wynikaja z plci w O mnie`() {
        assertEquals(PhotoPeople.WOMEN, PhotoPeople.of(Gender.FEMALE))
        assertEquals(PhotoPeople.MEN, PhotoPeople.of(Gender.MALE))
        assertEquals(PhotoPeople.BOTH, PhotoPeople.of(Gender.UNSPECIFIED))
        Gender.entries.forEach { assertEquals(it, Gender.fromId(it.id)) }
        assertEquals(Gender.UNSPECIFIED, Gender.fromId(null))
        // „Oboje” z dawnego „Na zdjęciach” (D-51) to nie płeć: bez wyboru.
        assertEquals(Gender.UNSPECIFIED, Gender.fromId("oboje"))
        assertEquals(Gender.FEMALE, Gender.fromId("kobieta"))
    }

    @Test
    fun `wybor osob wraca z zapisu, a smiec to oboje`() {
        PhotoPeople.entries.forEach { assertEquals(it, PhotoPeople.fromId(it.id)) }
        assertEquals(PhotoPeople.BOTH, PhotoPeople.fromId(null))
        assertEquals(PhotoPeople.BOTH, PhotoPeople.fromId("ktoś"))
    }

    // ---- skład dnia z kilku planów ----

    private val dzis = LocalDate.of(2026, 11, 5)
    private fun godzina(dzien: LocalDate, h: Int) = dzien.atTime(h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun plan(id: String, active: Boolean, vararg workouts: String) = Program(
        id = id,
        name = id,
        profile = TrainingProfile(),
        startedAt = 0,
        cycleDays = workouts.size,
        createdAt = 0,
        active = active,
        days = workouts.mapIndexed { i, w -> PlanDay(i + 1, w) },
    )

    private fun sesja(id: String, workoutId: String, programId: String?, kiedy: Long, status: SessionStatus = SessionStatus.DONE) =
        Session(id, workoutId, workoutId, Difficulty.INTERMEDIATE, kiedy, kiedy + 1, status, programId = programId)

    private val sila = plan("plan-sila", active = true, "plan-sila-a", "plan-sila-b", "plan-sila-c")
    private val joga = plan("plan-joga", active = false, "plan-joga-a", "plan-joga-b")
    private val stary = plan("plan-stary", active = false, "plan-stary-a", "plan-stary-b")

    @Test
    fun `aktywny plan bez dzisiejszego treningu daje nastepny trening, a plan uzywany w tygodniu tez`() {
        val sesje = listOf(
            sesja("s1", "plan-joga-a", "plan-joga", godzina(dzis.minusDays(3), 18)),
            sesja("s2", "plan-stary-a", "plan-stary", godzina(dzis.minusDays(20), 18)),
        )
        val ids = PhotoPicker.todaysWorkoutIds(listOf(sila, joga, stary), sesje, dzis, ZoneOffset.UTC)
        assertEquals(listOf("plan-sila-a", "plan-joga-b"), ids)
    }

    @Test
    fun `plan z dzisiejszym treningiem daje to, co dzis zrobione, wiec sklad dnia nie zmienia sie po treningu`() {
        val rano = listOf(sesja("s1", "plan-sila-a", "plan-sila", godzina(dzis, 7)))
        assertEquals(listOf("plan-sila-a"), PhotoPicker.todaysWorkoutIds(listOf(sila), rano, dzis, ZoneOffset.UTC))
        val dwaRazy = rano + sesja("s2", "plan-sila-b", "plan-sila", godzina(dzis, 19))
        assertEquals(listOf("plan-sila-a", "plan-sila-b"), PhotoPicker.todaysWorkoutIds(listOf(sila), dwaRazy, dzis, ZoneOffset.UTC))
    }

    @Test
    fun `trening spoza planow zrobiony dzis sie liczy, przerwany nie`() {
        val sesje = listOf(
            sesja("s1", "wlasny-1", null, godzina(dzis, 9)),
            sesja("s2", "wlasny-2", null, godzina(dzis, 10), SessionStatus.ABANDONED),
            sesja("s3", "wlasny-3", null, godzina(dzis.minusDays(1), 10)),
        )
        assertEquals(listOf("plan-sila-a", "wlasny-1"), PhotoPicker.todaysWorkoutIds(listOf(sila), sesje, dzis, ZoneOffset.UTC))
    }
}
