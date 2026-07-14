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

    private val service = MovementService(
        movementClient, accountClient, mapService, bankService, teleportService,
        mock(AchievementCacheService::class.java),
    )

    private fun buildCharacter(mapId: Int = 1, name: String = "Cloud", x: Int = 0, y: Int = 0): ArtifactsCharacter =
        ArtifactsCharacter(
            name = name, account = "tellenn", level = 20, gold = 0,
            hp = 100, maxHp = 100, x = x, y = y, mapId = mapId, layer = "main",
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

    private fun mapAt(mapId: Int, x: Int, y: Int, region: Int = 1): MapData =
        MapData("map$mapId", "skin", x, y, mapId, "main", null,
            Interactions(MapContent("resource", "iron"), null), region = region)

    private fun stubMove(name: String, mapId: Int, moved: ArtifactsCharacter) {
        val movResp = mock(com.tellenn.artifacts.clients.responses.MovementResponseBody::class.java)
        `when`(movResp.character).thenReturn(moved)
        @Suppress("UNCHECKED_CAST")
        val resp = mock(com.tellenn.artifacts.clients.responses.ArtifactsResponseBody::class.java)
            as com.tellenn.artifacts.clients.responses.ArtifactsResponseBody<com.tellenn.artifacts.clients.responses.MovementResponseBody>
        `when`(resp.data).thenReturn(movResp)
        `when`(movementClient.move(name, mapId)).thenReturn(resp)
    }

    @Test
    fun `moveCharacterToCell uses teleport potion when destination is further than 3 cells`() {
        val character = buildCharacter(mapId = 1, x = 0, y = 0)
        val teleported = buildCharacter(mapId = 545)
        val potion = buildPotion("tp_potion")
        `when`(mapService.findByMapId(545)).thenReturn(mapAt(545, x = 10, y = 0))
        `when`(mapService.findByMapId(1)).thenReturn(mapAt(1, x = 0, y = 0))
        `when`(teleportService.findPotionForDestination(character, 545)).thenReturn(potion)
        `when`(teleportService.use(character, "tp_potion")).thenReturn(teleported)

        val result = service.moveCharacterToCell(545, character)

        assertEquals(545, result.mapId)
        verify(teleportService).use(character, "tp_potion")
        verifyNoInteractions(movementClient)
    }

    @Test
    fun `moveCharacterToCell walks when the teleport service offers no worthwhile potion`() {
        val character = buildCharacter(mapId = 1, x = 0, y = 0)
        val moved = buildCharacter(mapId = 5)
        `when`(mapService.findByMapId(5)).thenReturn(mapAt(5, x = 2, y = 0))
        `when`(mapService.findByMapId(1)).thenReturn(mapAt(1, x = 0, y = 0))
        // La décision « trop proche pour gâcher une potion » est désormais portée par TeleportService.
        `when`(teleportService.findPotionForDestination(character, 5)).thenReturn(null)
        stubMove("Cloud", 5, moved)

        val result = service.moveCharacterToCell(5, character)

        assertEquals(5, result.mapId)
        verify(teleportService).findPotionForDestination(character, 5)
        verify(movementClient).move("Cloud", 5)
    }

    @Test
    fun `moveCharacterToCell walks the remaining distance after teleporting to the region`() {
        val character = buildCharacter(mapId = 1, x = 0, y = 0)
        // La potion dépose le personnage dans la bonne région mais pas sur la case cible.
        val teleported = buildCharacter(mapId = 545, x = 9, y = 10)
        val moved = buildCharacter(mapId = 600)
        val potion = buildPotion("tp_potion")
        `when`(mapService.findByMapId(600)).thenReturn(mapAt(600, x = 10, y = 10))
        `when`(mapService.findByMapId(1)).thenReturn(mapAt(1, x = 0, y = 0))
        `when`(mapService.findByMapId(545)).thenReturn(mapAt(545, x = 9, y = 10))
        `when`(teleportService.findPotionForDestination(character, 600)).thenReturn(potion)
        `when`(teleportService.use(character, "tp_potion")).thenReturn(teleported)
        stubMove("Cloud", 600, moved)

        val result = service.moveCharacterToCell(600, character)

        assertEquals(600, result.mapId)
        verify(teleportService).use(character, "tp_potion")
        verify(movementClient).move("Cloud", 600)
    }

    @Test
    fun `moveCharacterToCell falls back to movement API when no potion available`() {
        val character = buildCharacter(mapId = 1, x = 0, y = 0)
        val moved = buildCharacter(mapId = 545)
        `when`(mapService.findByMapId(545)).thenReturn(mapAt(545, x = 10, y = 0))
        `when`(mapService.findByMapId(1)).thenReturn(mapAt(1, x = 0, y = 0))
        `when`(teleportService.findPotionForDestination(character, 545)).thenReturn(null)
        stubMove("Cloud", 545, moved)

        val result = service.moveCharacterToCell(545, character)

        assertEquals(545, result.mapId)
        verify(movementClient).move("Cloud", 545)
    }

    @Test
    fun `moveToBank does nothing and uses no potion when already at a bank`() {
        val character = buildCharacter(mapId = 545)
        `when`(mapService.findClosestMap(character, contentCode = "bank", checkAchievement = true))
            .thenReturn(bankMap())

        val result = service.moveToBank(character)

        assertEquals(545, result.mapId)
        // Pas de téléportation puisqu'on est déjà à la banque.
        verifyNoInteractions(teleportService)
        verifyNoInteractions(movementClient)
    }

    @Test
    fun `moveToBank delegates to movement which teleports only when it brings closer`() {
        val character = buildCharacter(mapId = 1, x = 0, y = 0)
        // Banque la plus proche : map 545, dans une autre région -> téléport pertinent.
        `when`(mapService.findClosestMap(character, contentCode = "bank", checkAchievement = true))
            .thenReturn(bankMap())
        `when`(mapService.findByMapId(545)).thenReturn(mapAt(545, x = 0, y = 0, region = 1))
        `when`(mapService.findByMapId(1)).thenReturn(mapAt(1, x = 0, y = 0, region = 2))
        val potion = buildPotion("recall_potion")
        `when`(teleportService.findPotionForDestination(character, 545)).thenReturn(potion)
        `when`(teleportService.use(character, "recall_potion")).thenReturn(buildCharacter(mapId = 545))

        val result = service.moveToBank(character)

        assertEquals(545, result.mapId)
        verify(teleportService).use(character, "recall_potion")
    }
}
