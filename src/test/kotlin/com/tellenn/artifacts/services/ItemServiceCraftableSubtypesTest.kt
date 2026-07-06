package com.tellenn.artifacts.services

import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ItemServiceCraftableSubtypesTest {

    private val itemRepository = mock(ItemRepository::class.java)
    private val monsterService = mock(MonsterService::class.java)
    private val service = ItemService(itemRepository, monsterService)

    private fun bar(code: String, level: Int, subtype: String, craftable: Boolean = true): ItemDetails = ItemDetails(
        code = code, name = code, description = "",
        type = "resource", subtype = subtype, level = level,
        tradeable = true, recyclable = false,
        craft = if (craftable) {
            ItemCraft(skill = "mining", level = level, items = listOf(RecipeIngredient("iron_ore", 10)), quantity = 1)
        } else null,
        effects = emptyList(),
        conditions = emptyList(),
    )

    @Test
    fun `should rank steel_bar alloy above iron_bar when both subtypes are allowed`() {
        // given — état de la banque d'items pour un mineur niveau 20 : steel_bar est un "alloy", pas une "bar"
        `when`(itemRepository.findByCraftSkillAndSubtypeAndLevelLessThanEqualOrderByLevelDesc("mining", "bar", 20))
            .thenReturn(listOf(bar("iron_bar", 10, "bar"), bar("copper_bar", 1, "bar")))
        `when`(itemRepository.findByCraftSkillAndSubtypeAndLevelLessThanEqualOrderByLevelDesc("mining", "alloy", 20))
            .thenReturn(listOf(bar("steel_bar", 20, "alloy")))

        // when
        val items = service.getAllCraftableItemsBySkillAndSubtypesAndMaxLevel("mining", listOf("bar", "alloy"), 20)

        // then — trié par niveau décroissant à travers les deux subtypes
        assertEquals(listOf("steel_bar", "iron_bar", "copper_bar"), items.map { it.code })
    }

    @Test
    fun `should exclude items without craft recipe`() {
        // given
        `when`(itemRepository.findByCraftSkillAndSubtypeAndLevelLessThanEqualOrderByLevelDesc("mining", "bar", 20))
            .thenReturn(listOf(bar("iron_bar", 10, "bar"), bar("raw_drop_bar", 5, "bar", craftable = false)))
        `when`(itemRepository.findByCraftSkillAndSubtypeAndLevelLessThanEqualOrderByLevelDesc("mining", "alloy", 20))
            .thenReturn(emptyList())

        // when
        val items = service.getAllCraftableItemsBySkillAndSubtypesAndMaxLevel("mining", listOf("bar", "alloy"), 20)

        // then
        assertEquals(listOf("iron_bar"), items.map { it.code })
    }
}