package com.tellenn.artifacts.clients

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Couvre l'étranglement proactif des simulations de combat (« 1 req / s »). Sans lui, une recette avec
 * plusieurs composants `mob` tire plusieurs `/simulation/fight` dans la même seconde et déclenche un
 * 429 (cf. [[hard-429-crashes-thread]]).
 */
class SimulationRateLimiterTest {

    private val interval = SimulationRateLimiter.MIN_INTERVAL_MS

    @Test
    fun `le premier appel part sans attendre et reserve la seconde suivante`() {
        val slot = reserveSimulationSlot(nowMs = 10_000, previousNextAllowedMs = 0, minIntervalMs = interval)
        assertEquals(0L, slot.waitMs)
        assertEquals(11_000L, slot.nextAllowedAtMs)
    }

    @Test
    fun `un appel dans la meme seconde attend le reste de la fenetre`() {
        // Créneau déjà réservé à 11_000 ; un appel à 10_400 doit attendre 600 ms.
        val slot = reserveSimulationSlot(nowMs = 10_400, previousNextAllowedMs = 11_000, minIntervalMs = interval)
        assertEquals(600L, slot.waitMs)
        assertEquals(12_000L, slot.nextAllowedAtMs)
    }

    @Test
    fun `des appels concurrents s'espacent d'une seconde chacun`() {
        // Trois appels au même instant : créneaux successifs à now, now+1s, now+2s.
        val first = reserveSimulationSlot(10_000, 0, interval)
        val second = reserveSimulationSlot(10_000, first.nextAllowedAtMs, interval)
        val third = reserveSimulationSlot(10_000, second.nextAllowedAtMs, interval)

        assertEquals(0L, first.waitMs)
        assertEquals(1_000L, second.waitMs)
        assertEquals(2_000L, third.waitMs)
    }

    @Test
    fun `un appel apres une longue inactivite ne rembourse pas de credit`() {
        // Le créneau réservé est dépassé depuis longtemps : pas d'attente, et on repart de maintenant.
        val slot = reserveSimulationSlot(nowMs = 50_000, previousNextAllowedMs = 11_000, minIntervalMs = interval)
        assertEquals(0L, slot.waitMs)
        assertEquals(51_000L, slot.nextAllowedAtMs)
    }
}
