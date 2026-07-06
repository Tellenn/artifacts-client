package com.tellenn.artifacts.jobs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Couvre le dimensionnement des chunks de recyclage du nettoyage de banque. Sans borne, `cleanUpBank`
 * retirait tout le stock d'un coup et débordait l'inventaire (497 en boucle : « prend puis repose »).
 * Le chunk doit tenir dans l'inventaire une fois les matériaux de recyclage récupérés.
 */
class CrafterJobRecycleChunkTest {

    @Test
    fun `le chunk laisse la place aux materiaux recuperes par le recyclage`() {
        // 100 slots - marge 5 = 95 ; une recette de 6 ingrédients rend au plus 6 matériaux par pièce
        assertEquals(15, recycleChunkSize(recipeIngredientCount = 6, inventoryMaxItems = 100, safeMargin = 5))
    }

    @Test
    fun `une recette a un seul ingredient recycle jusqu'a la capacite`() {
        assertEquals(95, recycleChunkSize(recipeIngredientCount = 1, inventoryMaxItems = 100, safeMargin = 5))
    }

    @Test
    fun `une recette plus lourde que l'inventaire recycle au moins une piece a la fois`() {
        assertEquals(1, recycleChunkSize(recipeIngredientCount = 200, inventoryMaxItems = 100, safeMargin = 5))
    }

    @Test
    fun `le chunk ne depasse jamais la capacite disponible`() {
        for (ingredientCount in 1..30) {
            val chunk = recycleChunkSize(ingredientCount, inventoryMaxItems = 100, safeMargin = 5)
            assertTrue(chunk * ingredientCount <= 95, "chunk trop grand pour $ingredientCount ingrédients")
            assertTrue(chunk >= 1)
        }
    }
}
