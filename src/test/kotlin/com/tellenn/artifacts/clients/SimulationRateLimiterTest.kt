package com.tellenn.artifacts.clients

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Couvre l'étranglement proactif des simulations de combat (« 1 req / s »). Sans lui, une recette avec
 * plusieurs composants `mob` tire plusieurs `/simulation/fight` dans la même seconde et déclenche un
 * 429 (cf. [[hard-429-crashes-thread]]).
 */
class SimulationRateLimiterTest {

    private val interval = SimulationRateLimiter.MIN_INTERVAL_MS

    @Test
    fun `l'intervalle garde une marge au-dessus d'une seconde pleine`() {
        // La limite « 1 req/s » est glissante : espacer d'exactement 1000 ms tombe pile sur la borne et
        // déclenche encore un 429. On garde une marge de sécurité au-dessus de la seconde.
        assertTrue(interval > 1_000L, "L'intervalle doit dépasser 1000 ms pour absorber la fenêtre glissante")
    }

    @Test
    fun `le premier appel part sans attendre et reserve le creneau suivant`() {
        val slot = reserveSimulationSlot(nowMs = 10_000, previousNextAllowedMs = 0, minIntervalMs = interval)
        assertEquals(0L, slot.waitMs)
        assertEquals(10_000 + interval, slot.nextAllowedAtMs)
    }

    @Test
    fun `un appel avant la fin du creneau attend le reste de la fenetre`() {
        val reserved = 10_000 + interval
        val slot = reserveSimulationSlot(nowMs = reserved - 400, previousNextAllowedMs = reserved, minIntervalMs = interval)
        assertEquals(400L, slot.waitMs)
        assertEquals(reserved + interval, slot.nextAllowedAtMs)
    }

    @Test
    fun `des appels concurrents s'espacent d'un intervalle chacun`() {
        // Trois appels au même instant : créneaux successifs à now, now+interval, now+2*interval.
        val first = reserveSimulationSlot(10_000, 0, interval)
        val second = reserveSimulationSlot(10_000, first.nextAllowedAtMs, interval)
        val third = reserveSimulationSlot(10_000, second.nextAllowedAtMs, interval)

        assertEquals(0L, first.waitMs)
        assertEquals(interval, second.waitMs)
        assertEquals(2 * interval, third.waitMs)
    }

    @Test
    fun `un appel apres une longue inactivite ne rembourse pas de credit`() {
        // Le créneau réservé est dépassé depuis longtemps : pas d'attente, et on repart de maintenant.
        val slot = reserveSimulationSlot(nowMs = 50_000, previousNextAllowedMs = 11_000, minIntervalMs = interval)
        assertEquals(0L, slot.waitMs)
        assertEquals(50_000 + interval, slot.nextAllowedAtMs)
    }
}
