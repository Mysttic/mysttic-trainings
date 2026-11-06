package pl.mysttic.trainings.domain.model

/**
 * Płeć z „O mnie” (D-57). Wybiera osoby na zdjęciu dnia w tle (D-51); plan, cele ani dobór ćwiczeń od niej nie zależą.
 * `id` idzie do DataStore i do kopii zapasowej, więc istniejących nie zmieniamy. Bez wyboru aplikacja nie zgaduje.
 */
enum class Gender(val id: String, val labelPl: String) {
    FEMALE("kobieta", "Kobieta"),
    MALE("mezczyzna", "Mężczyzna"),
    UNSPECIFIED("", "Nie podaję"),
    ;

    companion object {
        /** Nieznany, pusty albo brakujący id = bez wyboru. */
        fun fromId(id: String?): Gender = entries.firstOrNull { it.id.isNotEmpty() && it.id == id } ?: UNSPECIFIED
    }
}
