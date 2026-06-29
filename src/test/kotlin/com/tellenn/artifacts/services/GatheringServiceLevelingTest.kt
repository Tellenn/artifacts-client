package com.tellenn.artifacts.services

import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Couvre le calcul des tailles de chunk du leveling par batch ([craftAndRecycleForLeveling]) :
 * combien d'exemplaires d'un ingrédient on peut collecter / assembler par passage sans déborder
 * de l'inventaire.
 */
class GatheringServiceLevelingTest {

    // --- Phase 1 : collecte d'un ingrédient ---

    @Test
    fun `un ingredient brut (footprint 0) se collecte en un seul passage, quelle que soit la quantite`() {
        // footprint 0 = matériau récolté brut : gather() gère lui-même les allers-retours.
        assertEquals(250, levelingGatherChunkSize(unitSize = 0, totalNeeded = 250, inventoryMaxItems = 100))
    }

    @Test
    fun `un ingredient craftable se collecte par chunks bornes par l'inventaire`() {
        // 100 / 6 = 16 exemplaires par passage.
        assertEquals(16, levelingGatherChunkSize(unitSize = 6, totalNeeded = 60, inventoryMaxItems = 100))
    }

    @Test
    fun `un ingredient craftable plus gros que l'inventaire se collecte un par un`() {
        assertEquals(1, levelingGatherChunkSize(unitSize = 150, totalNeeded = 10, inventoryMaxItems = 100))
    }

    // --- Phase 2 : assemblage de l'item final ---

    @Test
    fun `l'assemblage se fait par chunks bornes par le footprint direct des ingredients`() {
        // 100 / 5 = 20 exemplaires assemblés par passage.
        assertEquals(20, levelingAssembleChunkSize(directSize = 5, inventoryMaxItems = 100))
    }

    @Test
    fun `un item dont les ingredients directs depassent l'inventaire s'assemble un par un`() {
        assertEquals(1, levelingAssembleChunkSize(directSize = 120, inventoryMaxItems = 100))
    }

    // --- Phase 0 : calcul des manques de matériaux à publier ---

    private fun craftable(code: String, ingredients: List<RecipeIngredient>): ItemDetails =
        ItemDetails(
            code = code, name = code, description = "", type = "resource", subtype = "bar",
            level = 10, tradeable = true,
            craft = ItemCraft("weaponcrafting", 10, ingredients, 1),
            effects = emptyList(), conditions = emptyList()
        )

    @Test
    fun `levelingShortfalls multiplie le besoin par la taille du batch`() {
        val item = craftable("iron_dagger", listOf(RecipeIngredient("iron_bar", 3)))

        val shortfalls = levelingShortfalls(item, batchSize = 4, bankQuantities = emptyMap())

        assertEquals(mapOf("iron_bar" to 12), shortfalls)
    }

    @Test
    fun `levelingShortfalls soustrait le stock deja en banque`() {
        val item = craftable("iron_dagger", listOf(RecipeIngredient("iron_bar", 3)))

        val shortfalls = levelingShortfalls(item, batchSize = 4, bankQuantities = mapOf("iron_bar" to 5))

        assertEquals(mapOf("iron_bar" to 7), shortfalls)
    }

    @Test
    fun `levelingShortfalls ecarte un ingredient entierement couvert par la banque`() {
        val item = craftable("iron_dagger", listOf(RecipeIngredient("iron_bar", 3)))

        val shortfalls = levelingShortfalls(item, batchSize = 4, bankQuantities = mapOf("iron_bar" to 12))

        assertTrue(shortfalls.isEmpty())
    }

    @Test
    fun `levelingShortfalls ne garde que les ingredients manquants`() {
        val item = craftable(
            "iron_dagger",
            listOf(RecipeIngredient("iron_bar", 3), RecipeIngredient("ash_plank", 2))
        )

        val shortfalls = levelingShortfalls(
            item, batchSize = 4,
            bankQuantities = mapOf("iron_bar" to 0, "ash_plank" to 100)
        )

        assertEquals(mapOf("iron_bar" to 12), shortfalls)
    }

    @Test
    fun `levelingShortfalls renvoie vide pour un item non craftable`() {
        val raw = ItemDetails(
            code = "iron_ore", name = "iron_ore", description = "", type = "resource", subtype = "mining",
            level = 1, tradeable = true, craft = null, effects = emptyList(), conditions = emptyList()
        )

        assertTrue(levelingShortfalls(raw, batchSize = 10, bankQuantities = emptyMap()).isEmpty())
    }
}
