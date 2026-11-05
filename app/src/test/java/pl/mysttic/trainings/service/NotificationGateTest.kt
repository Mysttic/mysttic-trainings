package pl.mysttic.trainings.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGateTest {

    @Test
    fun `start bez zgody niczego nie wystawia`() {
        assertFalse(NotificationGate().update(false))
    }

    @Test
    fun `zgoda od poczatku nie daje zbocza`() {
        // WorkoutService tworzy bramkę ze stanem z onCreate: trening ze zgodą nie wystawia FGS drugi raz.
        val gate = NotificationGate(allowed = true)

        assertFalse(gate.update(true))
        assertFalse(gate.update(true))
    }

    @Test
    fun `zgoda nadana w trakcie daje jedno zbocze`() {
        val gate = NotificationGate()
        gate.update(false)

        assertTrue(gate.update(true))
    }

    @Test
    fun `trwajaca zgoda nie wystawia powiadomienia w kolko`() {
        val gate = NotificationGate()
        gate.update(false)
        gate.update(true)

        assertFalse(gate.update(true))
        assertFalse(gate.update(true))
    }

    @Test
    fun `odebranie i ponowne nadanie zgody znowu wystawia`() {
        val gate = NotificationGate()
        gate.update(true)
        gate.update(false)

        assertTrue(gate.update(true))
    }
}
