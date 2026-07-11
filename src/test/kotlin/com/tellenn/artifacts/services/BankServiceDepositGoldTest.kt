package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.BankGoldTransaction
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.exceptions.CharacterGoldInsufficientException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class BankServiceDepositGoldTest {

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
    fun `depositMoney tolère un 492 quand l'or a déjà été encaissé par le serveur`() {
        // given — l'état mémoire croit à 3875 or, mais le serveur l'a déjà encaissé
        // (requête rejouée après une coupure de connexion au reset de minuit)
        val staleCharacter = buildCharacter(gold = 3875)
        val freshCharacter = buildCharacter(gold = 0)
        val freshResponse = characterResponse(freshCharacter)
        `when`(bankClient.depositGold("Renoir", 3875)).thenThrow(CharacterGoldInsufficientException())
        `when`(accountClient.getCharacter("Renoir")).thenReturn(freshResponse)

        // when
        val result = service.depositMoney(staleCharacter, 3875)

        // then — on repart de l'état serveur sans crash ni second dépôt (plus rien à déposer)
        assertEquals(0, result.gold)
        verify(bankClient, times(1)).depositGold(anyString(), anyInt())
    }

    @Test
    fun `depositMoney redépose le solde serveur réel après un 492`() {
        // given — le serveur n'a encaissé qu'une partie : il reste 250 or à déposer
        val staleCharacter = buildCharacter(gold = 3875)
        val freshCharacter = buildCharacter(gold = 250)
        val afterDeposit = buildCharacter(gold = 0)
        val freshResponse = characterResponse(freshCharacter)
        val depositResponse = goldTransactionResponse(afterDeposit)
        `when`(bankClient.depositGold("Renoir", 3875)).thenThrow(CharacterGoldInsufficientException())
        `when`(accountClient.getCharacter("Renoir")).thenReturn(freshResponse)
        `when`(bankClient.depositGold("Renoir", 250)).thenReturn(depositResponse)

        // when
        val result = service.depositMoney(staleCharacter, 3875)

        // then — seul le solde réel est redéposé
        assertEquals(0, result.gold)
        verify(bankClient).depositGold("Renoir", 250)
    }

    private fun characterResponse(character: ArtifactsCharacter): ArtifactsResponseBody<ArtifactsCharacter> {
        @Suppress("UNCHECKED_CAST")
        val response = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<ArtifactsCharacter>
        `when`(response.data).thenReturn(character)
        return response
    }

    private fun goldTransactionResponse(character: ArtifactsCharacter): ArtifactsResponseBody<BankGoldTransaction> {
        @Suppress("UNCHECKED_CAST")
        val response = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<BankGoldTransaction>
        val transaction = mock(BankGoldTransaction::class.java)
        `when`(transaction.character).thenReturn(character)
        `when`(response.data).thenReturn(transaction)
        return response
    }

    private fun buildCharacter(gold: Int): ArtifactsCharacter = ArtifactsCharacter(
        name = "Renoir", account = "tellenn", level = 30, gold = gold,
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
