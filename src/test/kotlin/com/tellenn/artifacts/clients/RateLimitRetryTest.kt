package com.tellenn.artifacts.clients

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Couvre le back-off appliqué quand une requête est rejetée en 429 (rate limit). Sans ce garde-fou,
 * un 429 sur /simulation/fight (« 1 req / s ») remonte non capturé jusqu'à [ThreadService] et tue le
 * thread du personnage au lieu d'attendre une seconde et de re-tenter.
 */
class RateLimitRetryTest {

    @Test
    fun `le premier essai attend au moins une fenetre pleine d'une seconde`() {
        // L'endpoint impose 1 req/s : la première re-tentative doit laisser passer une seconde entière.
        assertEquals(1_000L, rateLimitRetryDelayMillis(0))
    }

    @Test
    fun `l'attente croit avec les essais pour absorber la contention entre personnages`() {
        assertEquals(2_000L, rateLimitRetryDelayMillis(1))
        assertEquals(3_000L, rateLimitRetryDelayMillis(2))
    }

    @Test
    fun `l'attente est plafonnee pour ne pas figer le thread`() {
        assertEquals(5_000L, rateLimitRetryDelayMillis(100))
        assertTrue(rateLimitRetryDelayMillis(50) <= 5_000L)
    }

    @Test
    fun `un numero d'essai negatif est traite comme le premier essai`() {
        assertEquals(1_000L, rateLimitRetryDelayMillis(-1))
    }
}
