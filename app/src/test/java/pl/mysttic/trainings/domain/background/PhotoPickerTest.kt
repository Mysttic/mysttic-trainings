package pl.mysttic.trainings.domain.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.model.Difficulty
import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.model.ExerciseCategory
import pl.mysttic.trainings.domain.model.ExerciseKind
import pl.mysttic.trainings.domain.model.MuscleGroup

/**
 * Zdjęcie dnia (D-51): temat wynika z ćwiczeń dzisiejszego treningu, osoby z Ustawień, a losowanie jest stałe
 * przez dzień i trening. Brzuch ma dać brzuch, joga jogę — reszta to rozstrzyganie remisów i braków w bazie.
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

    private val baza = listOf(
        photo("brzuch-k-1", PhotoTopic.CORE, PhotoSex.FEMALE),
        photo("brzuch-k-2", PhotoTopic.CORE, PhotoSex.FEMALE),
        photo("brzuch-m-1", PhotoTopic.CORE, PhotoSex.MALE),
        photo("joga-k-1", PhotoTopic.YOGA, PhotoSex.FEMALE),
        photo("joga-m-1", PhotoTopic.YOGA, PhotoSex.MALE),
        photo("calosc-k-1", PhotoTopic.FULL_BODY, PhotoSex.FEMALE),
        photo("calosc-m-1", PhotoTopic.FULL_BODY, PhotoSex.MALE),
    )

    @Test
    fun `dzien na brzuch daje brzuch, a joga to rozciaganie i oddech`() {
        val brzuch = listOf(strength("crunch", MuscleGroup.ABS), strength("plank", MuscleGroup.ABS), strength("squat", MuscleGroup.QUADS))
        assertEquals(PhotoTopic.CORE, PhotoPicker.topicOf(brzuch))

        val joga = listOf(
            exercise("cobra", ExerciseCategory.STRETCHING, MuscleGroup.ABS),
            exercise("oddech", ExerciseCategory.BREATHING),
            strength("plank", MuscleGroup.ABS),
        )
        assertEquals(PhotoTopic.YOGA, PhotoPicker.topicOf(joga))
    }

    @Test
    fun `kardio i plyometria to kardio, a sila idzie za partiami glownymi`() {
        assertEquals(PhotoTopic.CARDIO, PhotoPicker.topicOf(exercise("burpee", ExerciseCategory.PLYOMETRICS, MuscleGroup.FULL_BODY)))
        assertEquals(PhotoTopic.CARDIO, PhotoPicker.topicOf(exercise("bieg", ExerciseCategory.CARDIO, MuscleGroup.QUADS)))
        assertEquals(PhotoTopic.LEGS, PhotoPicker.topicOf(strength("lunge", MuscleGroup.QUADS, MuscleGroup.GLUTES)))
        assertEquals(PhotoTopic.UPPER, PhotoPicker.topicOf(strength("pushup", MuscleGroup.CHEST, MuscleGroup.TRICEPS)))
        assertEquals(PhotoTopic.CORE, PhotoPicker.topicOf(strength("superman", MuscleGroup.LOWER_BACK)))
    }

    @Test
    fun `remis partii, cale cialo i brak partii to cale cialo`() {
        assertEquals(PhotoTopic.FULL_BODY, PhotoPicker.topicOf(strength("thruster", MuscleGroup.QUADS, MuscleGroup.SHOULDERS)))
        assertEquals(PhotoTopic.FULL_BODY, PhotoPicker.topicOf(strength("burpee", MuscleGroup.FULL_BODY, MuscleGroup.CHEST)))
        assertEquals(PhotoTopic.FULL_BODY, PhotoPicker.topicOf(strength("cos")))
        // Dzień bez treningu (brak planu, cykl zrobiony) też dostaje zdjęcie.
        assertEquals(PhotoTopic.FULL_BODY, PhotoPicker.topicOf(emptyList()))
    }

    @Test
    fun `remis tematow rozstrzyga kolejnosc tematow`() {
        val pol_na_pol = listOf(strength("crunch", MuscleGroup.ABS), strength("squat", MuscleGroup.QUADS))
        assertEquals(PhotoTopic.CORE, PhotoPicker.topicOf(pol_na_pol))
    }

    @Test
    fun `zdjecie pasuje do osob z ustawien`() {
        repeat(40) { dzien ->
            val seed = PhotoPicker.seed(dzien.toLong(), "plan-a-1")
            assertEquals(PhotoSex.FEMALE, PhotoPicker.pick(baza, PhotoTopic.CORE, PhotoPeople.WOMEN, seed)?.sex)
            assertEquals(PhotoSex.MALE, PhotoPicker.pick(baza, PhotoTopic.CORE, PhotoPeople.MEN, seed)?.sex)
            assertEquals(PhotoTopic.CORE, PhotoPicker.pick(baza, PhotoTopic.CORE, PhotoPeople.BOTH, seed)?.topic)
        }
    }

    @Test
    fun `to samo ziarno to to samo zdjecie, kolejne dni roznia sie`() {
        val seed = PhotoPicker.seed(20_400, "plan-a-1")
        val raz = PhotoPicker.pick(baza, PhotoTopic.CORE, PhotoPeople.BOTH, seed)
        assertEquals(raz, PhotoPicker.pick(baza.reversed(), PhotoTopic.CORE, PhotoPeople.BOTH, seed))
        val dni = (20_400L until 20_430L).map { PhotoPicker.pick(baza, PhotoTopic.CORE, PhotoPeople.BOTH, PhotoPicker.seed(it, "plan-a-1")) }
        assertTrue("przez miesiąc ciągle to samo zdjęcie", dni.toSet().size > 1)
    }

    @Test
    fun `brak zdjec tematu spada na cale cialo, potem na cokolwiek, a pusta baza na nic`() {
        val seed = PhotoPicker.seed(1, null)
        assertEquals(PhotoTopic.FULL_BODY, PhotoPicker.pick(baza, PhotoTopic.LEGS, PhotoPeople.MEN, seed)?.topic)
        val bezCalosci = baza.filter { it.topic != PhotoTopic.FULL_BODY }
        assertEquals(PhotoSex.MALE, PhotoPicker.pick(bezCalosci, PhotoTopic.CARDIO, PhotoPeople.MEN, seed)?.sex)
        assertNull(PhotoPicker.pick(emptyList(), PhotoTopic.CORE, PhotoPeople.BOTH, seed))
        assertNull(PhotoPicker.pick(baza.filter { it.sex == PhotoSex.FEMALE }, PhotoTopic.CORE, PhotoPeople.MEN, seed))
    }

    @Test
    fun `wybor osob wraca z zapisu, a smiec to oboje`() {
        PhotoPeople.entries.forEach { assertEquals(it, PhotoPeople.fromId(it.id)) }
        assertEquals(PhotoPeople.BOTH, PhotoPeople.fromId(null))
        assertEquals(PhotoPeople.BOTH, PhotoPeople.fromId("ktoś"))
    }
}
