package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.models.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class MovementServiceTransitionTest {

    private val movementClient = mock(MovementClient::class.java)
    private val accountClient = mock(AccountClient::class.java)
    private val mapService = mock(MapService::class.java)
    private val bankService = mock(BankService::class.java)
    private val teleportService = mock(TeleportService::class.java)

    private val service = MovementService(
        movementClient, accountClient, mapService, bankService, teleportService,
        mock(AchievementCacheService::class.java),
    )

    private fun buildCharacter(mapId: Int, name: String = "Kepo"): ArtifactsCharacter =
        ArtifactsCharacter(
            name = name, account = "tellenn", level = 20, gold = 0,
            hp = 100, maxHp = 100, x = 0, y = 0, mapId = mapId, layer = "underground",
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

    private fun mapAt(mapId: Int, region: Int): MapData =
        MapData("map$mapId", "skin", 0, 0, mapId, "underground", null,
            Interactions(null, null), region = region)

    private fun goldCostTransition(sourceMapId: Int): TransitionMapper =
        TransitionMapper(
            id = null,
            sourceMapData = mapAt(sourceMapId, region = 1),
            destinationMapData = mapAt(1178, region = 20),
            conditions = listOf(Conditions(code = "gold", operator = "cost", value = 1000)),
        )

    private fun stubTransition(name: String, transitioned: ArtifactsCharacter) {
        val transResp = mock(com.tellenn.artifacts.clients.responses.TransitionResponseBody::class.java)
        `when`(transResp.character).thenReturn(transitioned)
        @Suppress("UNCHECKED_CAST")
        val resp = mock(com.tellenn.artifacts.clients.responses.ArtifactsResponseBody::class.java)
            as com.tellenn.artifacts.clients.responses.ArtifactsResponseBody<com.tellenn.artifacts.clients.responses.TransitionResponseBody>
        `when`(resp.data).thenReturn(transResp)
        `when`(movementClient.transition(name)).thenReturn(resp)
    }

    @Test
    fun `transition with a gold cost passes when the bank holds enough gold`() {
        // given : Kepo est déjà sur la case du transit (mapId 500) pour court-circuiter les déplacements,
        // et la banque possède largement les 1000 or exigés par le ferry vers Sandwhisper Isle.
        val character = buildCharacter(mapId = 500)
        val destinationMap = mapAt(1229, region = 20)
        val originMap = mapAt(500, region = 1)
        val transitioned = buildCharacter(mapId = 1229)

        `when`(mapService.findTransitionPath(1, 20)).thenReturn(listOf(goldCostTransition(sourceMapId = 500)))
        `when`(bankService.getBankDetails()).thenReturn(BankDetails(gold = 5000, nextExpansionCost = 0, expansions = 0, slots = 0))
        `when`(mapService.findClosestMap(character, contentCode = "bank", checkAchievement = true))
            .thenReturn(mapAt(500, region = 1))
        `when`(bankService.withdrawMoney(character, 1000)).thenReturn(character)
        stubTransition("Kepo", transitioned)

        // when
        val result = service.transitionsFromRegions(character, originMap, destinationMap)

        // then : l'or en banque n'est pas un item -> l'ancien isInBank("gold",…) échouait toujours.
        assertEquals(1229, result.mapId)
        verify(bankService).getBankDetails()
    }
}
