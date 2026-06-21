package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.models.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class MovementServiceTeleportTest {

    private val movementClient = mock(MovementClient::class.java)
    private val accountClient = mock(AccountClient::class.java)
    private val mapService = mock(MapService::class.java)
    private val bankService = mock(BankService::class.java)
    private val teleportService = mock(TeleportService::class.java)

    private val service = MovementService(movementClient, accountClient, mapService, bankService, teleportService)

    private fun buildCharacter(mapId: Int = 1, name: String = "Cloud"): ArtifactsCharacter =
        ArtifactsCharacter(
            name = name, account = "tellenn", level = 20, gold = 0,
            hp = 100, maxHp = 100, x = 0, y = 0, mapId = mapId, layer = "main",
            inventory = emptyArray(), cooldown = 0, skin = null, task = null,
            initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0,
            criticalStrike = 0, speed = 0, haste = 0, xp = 0, maxXp = 0,
            taskType = null, taskTotal = 0, taskProgress = 0,
            miningXp = 0, miningMaxXp = 0, miningLevel = 0,
            woodcuttingXp = 0, woodcuttingMaxXp = 0, woodcuttingLevel = 0,
            fishingXp = 0, fishingMaxXp = 0, fishingLevel = 0,
            weaponcraftingXp = 0, weaponcraftingMaxXp = 0, weaponcraftingLevel = 0,
            gearcraftingXp = 0, gearcraftingMaxXp = 0, gearcraftingLevel = 0,
            jewelrycraftingXp = 0, jewelrycraftingMaxXp = 0, jewelrycraftingLevel = 0,
            cookingXp = 0, cookingMaxXp = 0, cookingLevel = 0,
            alchemyXp = 0, alchemyMaxXp = 0, alchemyLevel = 0,
            inventoryMaxItems = 100,
            attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
            dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0,
            resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
            weaponSlot = null, runeSlot = null, shieldSlot = null, helmetSlot = null,
            bodyArmorSlot = null, legArmorSlot = null, bootsSlot = null,
            ring1Slot = null, ring2Slot = null, amuletSlot = null,
            artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
            utility1Slot = "", utility1SlotQuantity = 0,
            utility2Slot = "", utility2SlotQuantity = 0,
            bagSlot = null, cooldownExpiration = null,
        )

    private fun buildPotion(code: String): ItemDetails =
        ItemDetails(code = code, name = code, description = "", type = "consumable",
            subtype = "potion", level = 1, tradeable = true, recyclable = false,
            craft = null, effects = listOf(Effect("teleport", 545, null)), conditions = null)

    private fun bankMap(): MapData = MapData("Bank", "bank", 0, 0, 545, "main", null,
        Interactions(MapContent("bank", "bank"), null), region = 1)

    @Test
    fun `moveCharacterToCell uses teleport potion when available for exact destination`() {
        val character = buildCharacter(mapId = 1)
        val teleported = buildCharacter(mapId = 545)
        val potion = buildPotion("tp_potion")
        `when`(teleportService.findPotionForDestination(character, 545)).thenReturn(potion)
        `when`(teleportService.use(character, "tp_potion")).thenReturn(teleported)

        val result = service.moveCharacterToCell(545, character)

        assertEquals(545, result.mapId)
        verify(teleportService).use(character, "tp_potion")
        verifyNoInteractions(movementClient)
    }

    @Test
    fun `moveCharacterToCell falls back to movement API when no potion available`() {
        val character = buildCharacter(mapId = 1)
        val moved = buildCharacter(mapId = 545)
        val destMap = bankMap()
        `when`(teleportService.findPotionForDestination(character, 545)).thenReturn(null)
        `when`(mapService.findByMapId(545)).thenReturn(destMap)
        `when`(mapService.findByMapId(1)).thenReturn(
            MapData("Start", "skin", 0, 0, 1, "main", null,
                Interactions(MapContent("resource", "iron"), null), region = 1))
        val movResp = mock(com.tellenn.artifacts.clients.responses.MovementResponseBody::class.java)
        `when`(movResp.character).thenReturn(moved)
        @Suppress("UNCHECKED_CAST")
        val resp = mock(com.tellenn.artifacts.clients.responses.ArtifactsResponseBody::class.java)
            as com.tellenn.artifacts.clients.responses.ArtifactsResponseBody<com.tellenn.artifacts.clients.responses.MovementResponseBody>
        `when`(resp.data).thenReturn(movResp)
        `when`(movementClient.move("Cloud", 545)).thenReturn(resp)

        val result = service.moveCharacterToCell(545, character)

        assertEquals(545, result.mapId)
        verify(movementClient).move("Cloud", 545)
    }

    @Test
    fun `moveToBank uses bank teleport potion when available in inventory`() {
        val character = buildCharacter(mapId = 1)
        val teleported = buildCharacter(mapId = 545)
        val potion = buildPotion("tp_bank")
        `when`(teleportService.findBankPotionInInventory(character)).thenReturn(potion)
        `when`(teleportService.use(character, "tp_bank")).thenReturn(teleported)

        val result = service.moveToBank(character)

        assertEquals(545, result.mapId)
        verify(teleportService).use(character, "tp_bank")
        verifyNoInteractions(movementClient)
    }

    @Test
    fun `moveToBank falls back to normal movement when no bank potion in inventory`() {
        val character = buildCharacter(mapId = 545)
        `when`(teleportService.findBankPotionInInventory(character)).thenReturn(null)
        `when`(mapService.findClosestMap(character, contentCode = "bank", checkAchievement = true))
            .thenReturn(bankMap())

        val result = service.moveToBank(character)

        assertEquals(545, result.mapId)
        verifyNoInteractions(movementClient)
    }
}
