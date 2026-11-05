package pl.mysttic.trainings.service

import android.os.SystemClock

/** Zegar monotoniczny dla silnika — w testach podmieniany na sztuczny. */
fun interface MonotonicClock {
    fun now(): Long
}

/** `elapsedRealtime()` liczy też czas w uśpieniu i nie reaguje na zmianę godziny w systemie. */
object AndroidMonotonicClock : MonotonicClock {
    override fun now(): Long = SystemClock.elapsedRealtime()
}
