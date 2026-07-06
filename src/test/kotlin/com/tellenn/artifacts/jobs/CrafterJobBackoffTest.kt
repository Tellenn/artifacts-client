package com.tellenn.artifacts.jobs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Couvre le backoff appliqué quand le leveling du crafter échoue en boucle sur inventaire plein
 * (497). Sans ce garde-fou, le `do-while` de [CrafterJob.run] re-tente le cycle complet sans délai
 * et sature le quota d'API (2000 req/h) — c'est le pattern qui a mis le compte hors-jeu.
 */
class CrafterJobBackoffTest {

    @Test
    fun `aucun echec consecutif n'entraine aucune attente`() {
        assertEquals(0L, crafterInventoryFullBackoffMillis(0))
    }

    @Test
    fun `le premier echec declenche l'attente de base`() {
        assertEquals(1000L, crafterInventoryFullBackoffMillis(1))
    }

    @Test
    fun `l'attente double a chaque echec consecutif`() {
        assertEquals(2000L, crafterInventoryFullBackoffMillis(2))
        assertEquals(4000L, crafterInventoryFullBackoffMillis(3))
        assertEquals(8000L, crafterInventoryFullBackoffMillis(4))
    }

    @Test
    fun `l'attente est plafonnee pour ne pas geler le personnage indefiniment`() {
        // Au-delà du plafond, l'attente reste constante quel que soit le nombre d'échecs.
        assertEquals(60_000L, crafterInventoryFullBackoffMillis(100))
        assertTrue(crafterInventoryFullBackoffMillis(50) <= 60_000L)
    }
}
