package pl.mysttic.trainings.domain.model

/**
 * Ocena wysiłku po treningu (D-46): RPE liczone zapasem powtórzeń, stąd zakres od 6 do 10, a nie od 1.
 * Niżej niż 6 to rozgrzewka, której nie ma sensu oceniać. Wartość trafia do `sessions.rpe`; oceny spoza zakresu
 * (dawny suwak Historii od 1 do 10) zostają w bazie i w średniej planu, ekran po prostu nie zaznacza żadnej pigułki.
 */
object Rpe {
    val RANGE: IntRange = 6..10

    /** Podpis słowny pod pigułką; pusty poza zakresem. */
    fun label(value: Int): String = when (value) {
        6 -> "lekko"
        7 -> "średnio"
        8 -> "ciężko"
        9 -> "bardzo ciężko"
        10 -> "do oporu"
        else -> ""
    }

    const val HINT = "Ile powtórzeń zostało w zapasie: przy 6 cztery i więcej, przy 8 dwa, przy 10 żadne."
}
