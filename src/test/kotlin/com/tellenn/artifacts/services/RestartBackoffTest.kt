package com.tellenn.artifacts.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Backoff exponentiel des redémarrages de thread : un job qui crashe en boucle
 * (ex. CharacterSkillTooLow sur une recette hors de portée) ne doit pas marteler
 * l'API à raison d'un cycle par seconde jusqu'à cramer le quota horaire.
 */
class RestartBackoffTest {

    private val backoff = RestartBackoff(
        baseDelayMs = 1_000,
        maxDelayMs = 60_000,
        healthyUptimeMs = 600_000,
    )

    @Test
    fun `le delai double a chaque crash rapide consecutif`() {
        // given / when — trois crashs successifs après 5 s de vie chacun
        val delays = listOf(
            backoff.nextDelayMs("Aerith", uptimeMs = 5_000),
            backoff.nextDelayMs("Aerith", uptimeMs = 5_000),
            backoff.nextDelayMs("Aerith", uptimeMs = 5_000),
        )

        // then
        assertEquals(listOf(1_000L, 2_000L, 4_000L), delays)
    }

    @Test
    fun `le delai est plafonne au maximum`() {
        // given — assez de crashs rapides pour dépasser le plafond
        repeat(10) { backoff.nextDelayMs("Aerith", uptimeMs = 5_000) }

        // when / then
        assertEquals(60_000L, backoff.nextDelayMs("Aerith", uptimeMs = 5_000))
    }

    @Test
    fun `un thread reste longtemps en vie remet le backoff a zero`() {
        // given — escalade en cours
        repeat(5) { backoff.nextDelayMs("Aerith", uptimeMs = 5_000) }

        // when — le thread a tourné sainement avant ce crash
        val delay = backoff.nextDelayMs("Aerith", uptimeMs = 700_000)

        // then
        assertEquals(1_000L, delay)
    }

    @Test
    fun `les personnages ont des compteurs independants`() {
        // given — Aerith escalade
        repeat(3) { backoff.nextDelayMs("Aerith", uptimeMs = 5_000) }

        // when / then — Cloud repart du délai de base
        assertEquals(1_000L, backoff.nextDelayMs("Cloud", uptimeMs = 5_000))
    }
}
