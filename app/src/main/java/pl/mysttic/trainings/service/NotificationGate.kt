package pl.mysttic.trainings.service

/**
 * Zbocze zgody na powiadomienia: `true` tylko w tej chwili, w której wystawianie stało się możliwe.
 *
 * Bez tego zgoda nadana z systemowych Ustawień w trakcie trwającego treningu nie odzyskiwała powiadomienia:
 * rekord zdławiony przy starcie foreground service nie wraca sam, a serwis odświeża treść dopiero przy
 * zmianie kroku. Czysty Kotlin, więc regułę pilnuje test bez Androida.
 *
 * @param allowed stan zgody w chwili startu serwisu; z `false` przy zgodzie nadanej wcześniej pierwsze
 *   sprawdzenie udawałoby odzyskanie i wystawiało powiadomienie drugi raz.
 */
class NotificationGate(private var allowed: Boolean = false) {

    /** @return czy właśnie odzyskaliśmy możliwość wystawiania (a nie: czy wolno wystawiać). */
    fun update(canPost: Boolean): Boolean {
        val regained = canPost && !allowed
        allowed = canPost
        return regained
    }
}
