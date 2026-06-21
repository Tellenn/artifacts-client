package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.Effect
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ItemServiceTeleportTest {

    private val itemRepository = mock(ItemRepository::class.java)
    private val monsterService = mock(MonsterService::class.java)
    private val service = ItemService(itemRepository, monsterService)

    private fun item(
        code: String,
        level: Int = 5,
        subtype: String = "potion",
        craftSkill: String? = "alchemy",
        effectCode: String = "teleport",
    ): ItemDetails = ItemDetails(
        code = code, name = code, description = "",
        type = "consumable", subtype = subtype, level = level,
        tradeable = true, recyclable = false,
        craft = craftSkill?.let {
            ItemCraft(skill = it, level = level, items = listOf(RecipeIngredient("sunflower", 1)), quantity = 2)
        },
        effects = listOf(Effect(effectCode, 271, null)),
        conditions = emptyList(),
    )

    @Test
    fun `getCraftableTeleportPotions returns alchemy teleport potions at or below level`() {
        `when`(itemRepository.findByEffectsCode("teleport")).thenReturn(
            listOf(item("recall_potion", level = 5))
        )

        val result = service.getCraftableTeleportPotions(20)

        assertEquals(1, result.size)
        assertEquals("recall_potion", result[0].code)
    }

    @Test
    fun `getCraftableTeleportPotions excludes potions above max level`() {
        `when`(itemRepository.findByEffectsCode("teleport")).thenReturn(
            listOf(item("high_tp", level = 35))
        )

        val result = service.getCraftableTeleportPotions(20)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCraftableTeleportPotions excludes non-alchemy crafts`() {
        `when`(itemRepository.findByEffectsCode("teleport")).thenReturn(
            listOf(item("forged_tp", level = 5, craftSkill = "weaponcrafting"))
        )

        val result = service.getCraftableTeleportPotions(20)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCraftableTeleportPotions excludes non-craftable teleport items`() {
        `when`(itemRepository.findByEffectsCode("teleport")).thenReturn(
            listOf(item("drop_tp", level = 5, craftSkill = null))
        )

        val result = service.getCraftableTeleportPotions(20)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCraftableTeleportPotions sorts by level ascending`() {
        `when`(itemRepository.findByEffectsCode("teleport")).thenReturn(
            listOf(item("tp_high", level = 15), item("tp_low", level = 5))
        )

        val result = service.getCraftableTeleportPotions(20)

        assertEquals(listOf("tp_low", "tp_high"), result.map { it.code })
    }

    @Test
    fun `getPotions excludes teleport potions`() {
        `when`(itemRepository.findByCraftSkillAndLevelLessThanEqualOrderByLevelDesc("alchemy", 100)).thenReturn(
            listOf(
                item("recall_potion", level = 5, effectCode = "teleport"),
                item("small_health_potion", level = 5, effectCode = "restore"),
            )
        )

        val result = service.getPotions()

        assertEquals(listOf("small_health_potion"), result.map { it.code })
    }
}
