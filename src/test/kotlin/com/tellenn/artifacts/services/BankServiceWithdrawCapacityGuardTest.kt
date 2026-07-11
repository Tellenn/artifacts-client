package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.BankItemTransaction
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.SimpleItem
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

/**
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables
 * (même approche que BankServiceDepositCacheTest).
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

/**
 * Garde de capacité (défense en profondeur du livelock 497 du 2026-07-10) : un retrait dont la
 * quantité totale dépasse `inventoryMaxItems` est impossible par construction — l'API répondrait
 * 497 même inventaire vide. Il doit échouer localement, sans consommer le quota API.
 */
class BankServiceWithdrawCapacityGuardTest {

    private val bankClient = mock(BankClient::class.java)
    private val bankRepository = mock(BankItemRepository::class.java)

    private val service = BankService(
        bankClient, bankRepository, mock(ItemRepository::class.java), mock(CharacterService::class.java),
        mock(BankItemSyncService::class.java), mock(AccountClient::class.java),
        mock(MovementClient::class.java), mock(MapService::class.java), mock(TeleportService::class.java)
    )

    @Test
    fun `withdrawMany refuse sans appel API un retrait depassant la capacite d'inventaire`() {
        // given — 384 items demandés pour un inventaire de 100
        val character = buildCharacter(inventoryMaxItems = 100)
        val items = arrayListOf(SimpleItem("snake_hide", 384))

        // when / then — échec local immédiat, aucun aller-retour API
        assertThrows(IllegalArgumentException::class.java) {
            service.withdrawMany(items, character)
        }
        verifyNoInteractions(bankClient)
    }

    @Test
    fun `withdrawMany borne la somme des quantites, pas chaque ligne`() {
        // given — deux lignes de 60 : chacune tient, la somme (120) non
        val character = buildCharacter(inventoryMaxItems = 100)
        val items = arrayListOf(SimpleItem("snake_hide", 60), SimpleItem("flying_wing", 60))

        // when / then
        assertThrows(IllegalArgumentException::class.java) {
            service.withdrawMany(items, character)
        }
        verifyNoInteractions(bankClient)
    }

    @Test
    fun `withdrawMany laisse passer un retrait egal a la capacite d'inventaire`() {
        // given
        val character = buildCharacter(inventoryMaxItems = 100)
        val items = arrayListOf(SimpleItem("snake_hide", 100))
        val response = withdrawResponse(character)
        `when`(bankClient.withdrawItems(anyString(), anyObject())).thenReturn(response)

        // when
        service.withdrawMany(items, character)

        // then
        verify(bankClient).withdrawItems("Renoir", items)
    }

    private fun withdrawResponse(character: ArtifactsCharacter): ArtifactsResponseBody<BankItemTransaction> {
        @Suppress("UNCHECKED_CAST")
        val response = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<BankItemTransaction>
        val transaction = mock(BankItemTransaction::class.java)
        `when`(transaction.character).thenReturn(character)
        `when`(response.data).thenReturn(transaction)
        return response
    }

    private fun buildCharacter(inventoryMaxItems: Int): ArtifactsCharacter = ArtifactsCharacter(
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
        inventoryMaxItems = inventoryMaxItems,
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
