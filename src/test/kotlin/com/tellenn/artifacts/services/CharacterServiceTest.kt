package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.CharacterClient
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.RestResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant

class CharacterServiceTest {

    private lateinit var characterClient: CharacterClient
    private lateinit var characterService: CharacterService

    @BeforeEach
    fun setUp() {
        characterClient = mock(CharacterClient::class.java)
        characterService = CharacterService(characterClient)
    }

    @Test
    fun `rest should return original character when HP is full`() {
        // Arrange
        val character = createCharacter(hp = 100, maxHp = 100)

        // Act
        val result = characterService.rest(character)

        // Assert
        assertSame(character, result)
        verify(characterClient, never()).rest(anyString())
    }

    @Test
    fun `rest should call client and return updated character when HP is not full`() {
        // Arrange
        val originalCharacter = createCharacter(hp = 50, maxHp = 100)
        val updatedCharacter = createCharacter(hp = 100, maxHp = 100)

        val now = Instant.now()
        val expiration = now.plusSeconds(60)
        val cooldown = Cooldown(
            totalSeconds = 60,
            remainingSeconds = 60,
            startedAt = now,
            expiration = expiration,
            reason = "rest"
        )
        val restResponseBody = RestResponseBody(cooldown, updatedCharacter, 50)
        val artifactsResponse = ArtifactsResponseBody(restResponseBody)

        `when`(characterClient.rest(originalCharacter.name)).thenReturn(artifactsResponse)

        // Act
        val result = characterService.rest(originalCharacter)

        // Assert
        assertEquals(updatedCharacter, result)
        verify(characterClient, times(1)).rest(originalCharacter.name)
    }

    private fun createCharacter(hp: Int, maxHp: Int): ArtifactsCharacter {
        return ArtifactsCharacter(
            name = "TestCharacter",
            account = "TestAccount",
            level = 1,
            gold = 100,
            hp = hp,
            maxHp = maxHp,
            x = 0,
            y = 0,
            inventory = null,
            cooldown = 0,
            skin = null,
            task = null,
            dmg = 10,
            wisdom = 10,
            prospecting = 10,
            criticalStrike = 10,
            speed = 10,
            haste = 10,
            xp = 0,
            maxXp = 100,
            taskType = null,
            taskTotal = 0,
            taskProgress = 0,
            miningXp = 0,
            miningMaxXp = 100,
            miningLevel = 1,
            woodcuttingXp = 0,
            woodcuttingMaxXp = 100,
            woodcuttingLevel = 1,
            fishingXp = 0,
            fishingMaxXp = 100,
            fishingLevel = 1,
            weaponcraftingXp = 0,
            weaponcraftingMaxXp = 100,
            weaponcraftingLevel = 1,
            gearcraftingXp = 0,
            gearcraftingMaxXp = 100,
            gearcraftingLevel = 1,
            jewelrycraftingXp = 0,
            jewelrycraftingMaxXp = 100,
            jewelrycraftingLevel = 1,
            cookingXp = 0,
            cookingMaxXp = 100,
            cookingLevel = 1,
            alchemyXp = 0,
            alchemyMaxXp = 100,
            alchemyLevel = 1,
            inventoryMaxItems = 20,
            attackFire = 0,
            attackEarth = 0,
            attackWater = 0,
            attackAir = 0,
            dmgFire = 0,
            dmgEarth = 0,
            dmgWater = 0,
            dmgAir = 0,
            resFire = 0,
            resEarth = 0,
            resWater = 0,
            resAir = 0,
            weaponSlot = null,
            runeSlot = null,
            shieldSlot = null,
            helmetSlot = null,
            bodyArmorSlot = null,
            legArmorSlot = null,
            bootsSlot = null,
            ring1Slot = null,
            ring2Slot = null,
            amuletSlot = null,
            artifact1Slot = null,
            artifact2Slot = null,
            artifact3Slot = null,
            utility1Slot = null,
            utility1SlotQuantity = 0,
            utility2Slot = null,
            utility2SlotQuantity = 0,
            bagSlot = null,
            cooldownExpiration = null
        )
    }
}
