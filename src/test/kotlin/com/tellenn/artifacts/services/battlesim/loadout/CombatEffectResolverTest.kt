package com.tellenn.artifacts.services.battlesim.loadout

import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Effect
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.services.battlesim.TestCharacters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class CombatEffectResolverTest {
    private lateinit var itemRepository: ItemRepository
    private lateinit var resolver: CombatEffectResolver

    @BeforeEach
    fun setUp() {
        itemRepository = mock(ItemRepository::class.java)
        resolver = CombatEffectResolver(itemRepository)
    }

    private fun item(code: String, type: String, effects: List<Effect>) =
        ItemDetails(code, code, "", type, "", 1, true, false, null, effects, null)

    @Test
    fun `collects combat effects from weapon and heal potion from utility`() {
        `when`(itemRepository.findByCode("sword"))
            .thenReturn(item("sword", "weapon", listOf(Effect("lifesteal", 10, null))))
        `when`(itemRepository.findByCode("hp_potion"))
            .thenReturn(item("hp_potion", "utility", listOf(Effect("heal", 60, null))))

        val character = TestCharacters.blank().apply {
            weaponSlot = "sword"; utility1Slot = "hp_potion"; utility1SlotQuantity = 3
        }
        val loadout = resolver.resolve(character)

        assertEquals(listOf("lifesteal"), loadout.effects.map { it.code })
        assertEquals(60, loadout.healPotion1?.healPerUse)
        assertEquals(3, loadout.healPotion1?.remaining)
    }
}
