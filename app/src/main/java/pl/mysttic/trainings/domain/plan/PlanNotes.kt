package pl.mysttic.trainings.domain.plan

import pl.mysttic.trainings.domain.editor.WorkoutEditing

/**
 * Notatki do ćwiczeń w obrębie jednego planu (D-44): „20 kg, drugi otwór” przy wyciskaniu wraca w każdym tygodniu
 * tego planu, a w innym planie go nie ma. Kluczem jest ćwiczenie pokazane na ekranie, jak u trenera (D-35).
 * Czysty Kotlin: to samo sito tekstu co wskazówka w edytorze treningu, limity dla pliku planu.
 */
object PlanNotes {

    const val MAX_NOTE = WorkoutEditing.MAX_NOTE

    /** Więcej notatek niż ćwiczeń w pliku nie ma sensu; limit chroni przed wrogim plikiem. */
    const val MAX_NOTES = 300

    fun clean(note: String): String = WorkoutEditing.cleanText(note, MAX_NOTE)

    /**
     * Notatki do zapisu: oczyszczone, bez pustych i tylko dla ćwiczeń z [exerciseIds] (plik planu niesie notatki
     * wyłącznie do ćwiczeń, które sam opisuje). Kolejność po id ćwiczenia, żeby eksport był deterministyczny.
     */
    fun forExercises(notes: Map<String, String>, exerciseIds: Set<String>): Map<String, String> =
        notes.asSequence()
            .filter { (id, _) -> id in exerciseIds }
            .map { (id, note) -> id to clean(note) }
            .filter { (_, note) -> note.isNotEmpty() }
            .sortedBy { it.first }
            .take(MAX_NOTES)
            .toMap(LinkedHashMap())
}
