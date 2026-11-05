package pl.mysttic.trainings.ui.adaptive

/**
 * Układ zależny od rozmiaru okna (D-42), liczony z samych wymiarów w dp — bez `material3-adaptive`, bo jej
 * `NavigationSuiteScaffold` wymaga ikon, a nawigacja w „Ogniu i Krwi” ich nie ma (D-32). Czysta klasa, żeby
 * progi dało się sprawdzić testem bez Androida.
 */
data class AppLayout(val widthDp: Int, val heightDp: Int) {
    /** Szyna z boku zamiast dolnego paska: tablet albo telefon w poziomie, gdzie pasek zjada wysokość. */
    val navRail: Boolean get() = widthDp >= COMPACT_MAX || heightDp < LOW_HEIGHT

    /** Lista i szczegół obok siebie. Warunek wysokości trzyma telefon w poziomie w jednym panelu. */
    val twoPane: Boolean get() = widthDp >= EXPANDED_MIN && heightDp >= LOW_HEIGHT

    /** Player w poziomie: liczba po lewej, akcje po prawej. */
    val playerSideBySide: Boolean get() = widthDp > heightDp && widthDp >= COMPACT_MAX

    /** Kolumny kafelków biblioteki. */
    val tileColumns: Int get() = when {
        widthDp >= EXPANDED_MIN + 400 -> 4
        widthDp >= COMPACT_MAX -> 3
        else -> 2
    }

    companion object {
        const val COMPACT_MAX = 600
        const val EXPANDED_MIN = 840
        const val LOW_HEIGHT = 480

        /** Najszersza kolumna tekstu; szersza przestaje się dobrze czytać. */
        const val CONTENT_MAX_WIDTH = 640

        /** Szerokość listy w układzie dwupanelowym. */
        const val LIST_PANE_WIDTH = 400

        val PHONE = AppLayout(411, 891)
    }
}
