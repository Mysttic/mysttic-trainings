package pl.mysttic.trainings.domain.update

import pl.mysttic.trainings.domain.model.Exercise
import pl.mysttic.trainings.domain.plan.preset.PresetCatalog
import pl.mysttic.trainings.domain.plan.share.PlanFileCodec
import java.time.LocalDate

/**
 * Wroga kontrola bazy ćwiczeń pobranej z sieci, zanim trafi do Room (D-47). Te same reguły co `tools/baza.py sprawdz`
 * i `SeedIntegrityTest`, plus to, czego tamte nie wiedzą: co już jest w telefonie i jakich ćwiczeń potrzebują gotowe
 * programy tej wersji aplikacji. Wartości spoza enumów odrzuca wcześniej sam odczyt JSON-a. Pusta lista = baza
 * do wczytania; każda pozycja to powód odrzucenia całej bazy, bo niepełna baza zgubiłaby ćwiczenie historii albo programu.
 */
object ExerciseDatabaseCheck {

    private val ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    const val ID_MAX = 64
    const val NAME_MAX = 80
    const val STEPS_MAX = 10
    const val STEP_MAX = 500
    val REPS = 1..PlanFileCodec.MAX_DEFAULT_REPS
    val SECONDS = PlanFileCodec.MIN_DEFAULT_SECONDS..PlanFileCodec.MAX_DEFAULT_SECONDS

    fun problems(
        version: Int,
        format: Int,
        minApp: String?,
        exercises: List<Exercise>,
        /** Nazwy plików w `images/` pobranej bazy, np. `pushups.jpg`. */
        imageFiles: Set<String>,
        /** Ćwiczenia, które są teraz w telefonie. */
        installed: List<Exercise>,
        installedVersion: Int,
        appVersion: String,
        /** Zdjęcia tła z `tla.json` (D-51); baza bez zdjęć ma obie listy puste. */
        photoIds: List<String> = emptyList(),
        /** Nazwy plików w `tla/` pobranej bazy, np. `brzuch-k-1.jpg`. */
        photoFiles: Set<String> = emptySet(),
    ): List<String> {
        val problems = LinkedHashSet<String>()
        if (version <= installedVersion) problems += "baza v$version nie jest nowsza od wczytanej v$installedVersion"
        if (format > DatabaseUpdate.SUPPORTED_FORMAT) problems += "format $format jest nowszy niż ten, który zna aplikacja"
        if (minApp != null && UpdateCheck.compareVersions(appVersion, minApp) < 0) problems += "baza wymaga aplikacji $minApp"
        if (exercises.isEmpty()) return (problems + "baza nie ma ćwiczeń").toList()

        val byId = LinkedHashMap<String, Exercise>()
        for (e in exercises) {
            if (!ID.matches(e.id) || e.id.length > ID_MAX) {
                problems += "${e.id.take(ID_MAX)}: id poza formatem"
                continue
            }
            if (e.id.startsWith(PlanFileCodec.CUSTOM_PREFIX)) problems += "${e.id}: prefiks ${PlanFileCodec.CUSTOM_PREFIX} należy do plików planów"
            if (byId.put(e.id, e) != null) problems += "${e.id}: id powtórzone"
            if (e.namePl.isBlank() || e.namePl.length > NAME_MAX || e.nameEn.isBlank() || e.nameEn.length > NAME_MAX) {
                problems += "${e.id}: nazwa pusta albo dłuższa niż $NAME_MAX znaków"
            }
            if (e.instructionsPl.size !in 1..STEPS_MAX || e.instructionsPl.any { it.isBlank() || it.length > STEP_MAX }) {
                problems += "${e.id}: kroki poza zakresem"
            }
            if (e.defaultReps !in REPS || e.defaultSeconds !in SECONDS) problems += "${e.id}: cel domyślny poza zakresem"
            if (e.images != listOf("images/${e.id}.jpg") || "${e.id}.jpg" !in imageFiles) problems += "${e.id}: brak ilustracji"
            e.retiredAt?.let { date ->
                val valid = DATE.matches(date) && runCatching { LocalDate.parse(date) }.isSuccess
                if (!valid) problems += "${e.id}: data wycofania $date"
            }
        }
        for (e in byId.values) {
            for (target in listOfNotNull(e.easierId, e.harderId)) {
                val variant = byId[target]
                when {
                    target == e.id -> problems += "${e.id}: wariant wskazuje samo siebie"
                    variant == null -> problems += "${e.id}: brak wariantu $target"
                    variant.kind != e.kind -> problems += "${e.id}: wariant $target ma inny rodzaj celu"
                    variant.isRetired && !e.isRetired -> problems += "${e.id}: wariant $target jest wycofany"
                }
            }
        }
        // Id raz wydanego ćwiczenia nie znika i nie zmienia rodzaju celu: wskazują po nim historia, plany i notatki.
        for (old in installed.filter { !it.isCustom }) {
            val now = byId[old.id]
            if (now == null) problems += "${old.id}: ćwiczenie z telefonu zniknęło"
            else if (now.kind != old.kind) problems += "${old.id}: zmienił się rodzaj celu"
        }
        val orphans = imageFiles - byId.keys.map { "$it.jpg" }.toSet()
        if (orphans.isNotEmpty()) problems += "obrazy bez ćwiczeń: ${orphans.sorted().take(3).joinToString()}"
        problems += photoProblems(photoIds, photoFiles)
        problems += PresetCatalog.libraryProblems(byId)
        return problems.toList()
    }

    /** Zdjęcia tła (D-51): id jak u ćwiczeń, każde z plikiem i każdy plik ze zdjęciem. Temat i płeć sprawdza odczyt JSON-a. */
    private fun photoProblems(ids: List<String>, files: Set<String>): List<String> {
        val problems = mutableListOf<String>()
        val seen = HashSet<String>()
        for (id in ids) {
            if (!ID.matches(id) || id.length > ID_MAX) {
                problems += "zdjęcie ${id.take(ID_MAX)}: id poza formatem"
                continue
            }
            if (!seen.add(id)) problems += "zdjęcie $id: id powtórzone"
            if ("$id.jpg" !in files) problems += "zdjęcie $id: brak pliku"
        }
        val orphans = files - seen.map { "$it.jpg" }.toSet()
        if (orphans.isNotEmpty()) problems += "pliki zdjęć bez wpisu: ${orphans.sorted().take(3).joinToString()}"
        return problems
    }
}
