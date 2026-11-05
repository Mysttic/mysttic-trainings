package pl.mysttic.trainings.data.settings

/**
 * Tło wybrane w każdym motywie (D-50) jako zbiór wpisów „motyw=tło” w DataStore. Zbiór, a nie jeden klucz
 * na motyw, bo warstwa danych nie zna listy motywów (ta mieszka w `ui/theme`); mapuje je `AppBackground.resolve`.
 * Wpis bez którejś połowy pomijamy: DataStore i kopia zapasowa mogą przynieść cokolwiek.
 */
object BackgroundChoices {
    private const val SEPARATOR = '='

    /** Najdłuższy identyfikator motywu albo tła, jaki przyjmujemy; dzisiejsze mają po kilka znaków. */
    const val MAX_ID_LENGTH = 32

    fun decode(entries: Set<String>): Map<String, String> =
        entries.mapNotNull { entry ->
            val at = entry.indexOf(SEPARATOR)
            if (at <= 0) return@mapNotNull null
            val theme = entry.substring(0, at)
            val background = entry.substring(at + 1)
            if (valid(theme, background)) theme to background else null
        }.toMap()

    fun encode(choices: Map<String, String>): Set<String> =
        choices.filter { (theme, background) -> valid(theme, background) }
            .map { (theme, background) -> "$theme$SEPARATOR$background" }
            .toSet()

    private fun valid(theme: String, background: String): Boolean =
        theme.isNotBlank() && background.isNotBlank() && SEPARATOR !in theme && SEPARATOR !in background &&
            theme.length <= MAX_ID_LENGTH && background.length <= MAX_ID_LENGTH
}
