package com.tellenn.artifacts.services

import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.RecipeIngredient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MaterialResponsibilityTest {

    private lateinit var itemService: ItemService
    private lateinit var materialResponsibility: MaterialResponsibility

    @BeforeEach
    fun setUp() {
        itemService = mock(ItemService::class.java)
        materialResponsibility = MaterialResponsibility(itemService)
    }

    private fun crafted(code: String, skill: String, level: Int = 10, subtype: String = "bar"): ItemDetails =
        ItemDetails(
            code = code, name = code, description = "", type = "resource", subtype = subtype,
            level = level, tradeable = true,
            craft = ItemCraft(skill, level, listOf(RecipeIngredient("ore", 5)), 1),
            effects = emptyList(), conditions = emptyList()
        )

    private fun raw(code: String, subtype: String, level: Int = 1): ItemDetails =
        ItemDetails(
            code = code, name = code, description = "", type = "resource", subtype = subtype,
            level = level, tradeable = true, craft = null,
            effects = emptyList(), conditions = emptyList()
        )

    @Test
    fun `a bar maps to the mining skill and the miner Kepo`() {
        // given
        `when`(itemService.getItem("iron_bar")).thenReturn(crafted("iron_bar", "mining"))

        // when - then
        assertEquals("mining", materialResponsibility.skillFor("iron_bar"))
        assertEquals("Kepo", materialResponsibility.characterFor("iron_bar"))
    }

    @Test
    fun `a plank maps to the woodcutting skill and the woodworker Gustave`() {
        // given
        `when`(itemService.getItem("ash_plank")).thenReturn(crafted("ash_plank", "woodcutting", subtype = "plank"))

        // when - then
        assertEquals("woodcutting", materialResponsibility.skillFor("ash_plank"))
        assertEquals("Gustave", materialResponsibility.characterFor("ash_plank"))
    }

    @Test
    fun `a crafter-assembled equipment is not posted as a task`() {
        // given
        `when`(itemService.getItem("iron_sword")).thenReturn(crafted("iron_sword", "weaponcrafting", subtype = "sword"))

        // when - then
        assertNull(materialResponsibility.skillFor("iron_sword"))
        assertNull(materialResponsibility.characterFor("iron_sword"))
    }

    @Test
    fun `a raw mob drop maps to mob and the fighter Cloud`() {
        // given
        `when`(itemService.getItem("red_slimeball")).thenReturn(raw("red_slimeball", "mob"))

        // when - then
        assertEquals("mob", materialResponsibility.skillFor("red_slimeball"))
        assertEquals("Cloud", materialResponsibility.characterFor("red_slimeball"))
    }

    @Test
    fun `a raw mining resource maps to mining and the miner Kepo`() {
        // given
        `when`(itemService.getItem("iron_ore")).thenReturn(raw("iron_ore", "mining"))

        // when - then
        assertEquals("mining", materialResponsibility.skillFor("iron_ore"))
        assertEquals("Kepo", materialResponsibility.characterFor("iron_ore"))
    }

    @Test
    fun `an unmappable subtype returns null`() {
        // given
        `when`(itemService.getItem("mysterious_token")).thenReturn(raw("mysterious_token", "currency"))

        // when - then
        assertNull(materialResponsibility.skillFor("mysterious_token"))
        assertNull(materialResponsibility.characterFor("mysterious_token"))
    }
}
