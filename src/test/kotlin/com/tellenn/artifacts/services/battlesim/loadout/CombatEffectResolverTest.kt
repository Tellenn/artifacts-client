package com.tellenn.artifacts.services.battlesim.loadout

import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Effect
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.services.battlesim.TestCharacters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `utility boost effect goes to effects list and second utility becomes heal potion 2`() {
        `when`(itemRepository.findByCode("boost_pot"))
            .thenReturn(item("boost_pot", "utility", listOf(Effect("boost_dmg_fire", 15, null))))
        `when`(itemRepository.findByCode("hp_potion"))
            .thenReturn(item("hp_potion", "utility", listOf(Effect("heal", 50, null))))

        val character = TestCharacters.blank().apply {
            utility1Slot = "boost_pot"; utility1SlotQuantity = 1
            utility2Slot = "hp_potion"; utility2SlotQuantity = 2
        }
        val loadout = resolver.resolve(character)

        assertEquals(listOf("boost_dmg_fire"), loadout.effects.map { it.code })
        assertNull(loadout.healPotion1)
        assertEquals(50, loadout.healPotion2?.healPerUse)
        assertEquals(2, loadout.healPotion2?.remaining)
    }

    @Test
    fun `gathers effects from multiple equipment slots and drops zero-value effects`() {
        `when`(itemRepository.findByCode("sword"))
            .thenReturn(item("sword", "weapon", listOf(Effect("lifesteal", 10, null), Effect("burn", 0, null))))
        `when`(itemRepository.findByCode("ring"))
            .thenReturn(item("ring", "ring", listOf(Effect("poison", 5, null))))

        val character = TestCharacters.blank().apply {
            weaponSlot = "sword"; ring1Slot = "ring"
        }
        val loadout = resolver.resolve(character)

        assertEquals(listOf("lifesteal", "poison"), loadout.effects.map { it.code })
    }

    @Test
    fun `lookup failure is logged and skipped without throwing`() {
        `when`(itemRepository.findByCode("broken"))
            .thenThrow(RuntimeException("db down"))

        val character = TestCharacters.blank().apply { weaponSlot = "broken" }
        val loadout = resolver.resolve(character)

        assertEquals(emptyList<String>(), loadout.effects.map { it.code })
        assertNull(loadout.healPotion1)
    }

    @Test
    fun `blank codes and non-positive quantities are skipped`() {
        val character = TestCharacters.blank().apply {
            weaponSlot = ""; utility1Slot = "hp_potion"; utility1SlotQuantity = 0
        }
        val loadout = resolver.resolve(character)

        assertEquals(emptyList<String>(), loadout.effects.map { it.code })
        assertNull(loadout.healPotion1)
    }
}
