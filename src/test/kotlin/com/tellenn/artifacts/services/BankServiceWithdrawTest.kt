package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.BankItemTransaction
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.exceptions.CharacterAlreadyMapException
import com.tellenn.artifacts.exceptions.MapContentNotFoundException
import com.tellenn.artifacts.exceptions.NotFoundException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.MapData
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class BankServiceWithdrawTest {

    private val bankClient = mock(BankClient::class.java)
    private val bankRepository = mock(BankItemRepository::class.java)
    private val itemRepository = mock(ItemRepository::class.java)
    private val characterService = mock(CharacterService::class.java)
    private val bankItemSyncService = mock(BankItemSyncService::class.java)
    private val accountClient = mock(AccountClient::class.java)
    private val movementClient = mock(MovementClient::class.java)
    private val mapService = mock(MapService::class.java)
    private val teleportService = mock(TeleportService::class.java)

    private val service = BankService(
        bankClient, bankRepository, itemRepository, characterService,
        bankItemSyncService, accountClient, movementClient, mapService, teleportService
    )

    @Test
    fun `withdrawMany resynchronise le cache banque quand l'API renvoie 404`() {
        // given — le cache local croit l'item présent mais la vraie banque renvoie "Item not found"
        val character = buildCharacter()
        val items = ArrayList(listOf(SimpleItem("cooked_chicken", 50)))
        `when`(bankClient.withdrawItems("Renoir", items)).thenThrow(NotFoundException())

        // when
        assertThrows(NotFoundException::class.java) { service.withdrawMany(items, character) }

        // then — le cache est resynchronisé pour ne pas répéter le retrait voué à l'échec
        verify(bankItemSyncService).syncAllItems()
    }

    @Test
    fun `withdrawMany resynchronise la position et re-tente apres un 598`() {
        // given — 598 à la 1re tentative (état mémoire périmé) ; l'état serveur montre le perso
        // déjà sur la banque (545) : on re-tente sur place, sans déplacement.
        val character = buildCharacter()
        val items = ArrayList(listOf(SimpleItem("cooked_chicken", 50)))
        val successResponse = withdrawResponse(character)
        val accountResponse = characterResponse(character)
        val closestBank = bankMap(mapId = 545)
        `when`(bankClient.withdrawItems("Renoir", items))
            .thenThrow(MapContentNotFoundException())
            .thenReturn(successResponse)
        `when`(accountClient.getCharacter("Renoir")).thenReturn(accountResponse)
        `when`(mapService.findClosestMap(character, "bank")).thenReturn(closestBank)

        // when
        val result = service.withdrawMany(items, character)

        // then
        assertEquals(character, result)
        verify(bankClient, times(2)).withdrawItems("Renoir", items)
        verify(movementClient, never()).move(anyString(), anyInt())
    }

    @Test
    fun `withdrawMany tolere un 490 pendant la relocalisation apres un 598`() {
        // given — le déplacement de récupération répond 490 « déjà sur place » (course avec un
        // autre déplacement) : traité comme un succès, le retrait est re-tenté.
        val character = buildCharacter()
        val items = ArrayList(listOf(SimpleItem("cooked_chicken", 50)))
        val successResponse = withdrawResponse(character)
        val accountResponse = characterResponse(character)
        val closestBank = bankMap(mapId = 955)
        `when`(bankClient.withdrawItems("Renoir", items))
            .thenThrow(MapContentNotFoundException())
            .thenReturn(successResponse)
        `when`(accountClient.getCharacter("Renoir")).thenReturn(accountResponse)
        `when`(mapService.findClosestMap(character, "bank")).thenReturn(closestBank)
        `when`(movementClient.move("Renoir", 955)).thenThrow(CharacterAlreadyMapException())

        // when
        val result = service.withdrawMany(items, character)

        // then
        assertEquals(character, result)
        verify(bankClient, times(2)).withdrawItems("Renoir", items)
    }

    private fun bankMap(mapId: Int): MapData {
        val map = mock(MapData::class.java)
        `when`(map.mapId).thenReturn(mapId)
        return map
    }

    private fun characterResponse(character: ArtifactsCharacter): ArtifactsResponseBody<ArtifactsCharacter> {
        @Suppress("UNCHECKED_CAST")
        val response = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<ArtifactsCharacter>
        `when`(response.data).thenReturn(character)
        return response
    }

    private fun withdrawResponse(character: ArtifactsCharacter): ArtifactsResponseBody<BankItemTransaction> {
        @Suppress("UNCHECKED_CAST")
        val response = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<BankItemTransaction>
        val transaction = mock(BankItemTransaction::class.java)
        `when`(transaction.character).thenReturn(character)
        `when`(response.data).thenReturn(transaction)
        return response
    }

    private fun buildCharacter(): ArtifactsCharacter = ArtifactsCharacter(
        name = "Renoir", account = "tellenn", level = 30, gold = 0,
        hp = 100, maxHp = 100, x = 0, y = 0, mapId = 545, layer = "main",
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
}
