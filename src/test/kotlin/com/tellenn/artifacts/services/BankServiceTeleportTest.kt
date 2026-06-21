package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.BankClient
import com.tellenn.artifacts.clients.MovementClient
import com.tellenn.artifacts.clients.responses.*
import com.tellenn.artifacts.db.documents.BankItemDocument
import com.tellenn.artifacts.db.repositories.BankItemRepository
import com.tellenn.artifacts.db.repositories.ItemRepository
import com.tellenn.artifacts.models.*
import com.tellenn.artifacts.services.sync.BankItemSyncService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables.
 * `Mockito.any()/eq()/argThat()` renvoient `null`, ce que l'assertion de non-nullité
 * Kotlin rejette ; on enregistre le matcher puis on renvoie une valeur non vérifiée.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

private fun <T> eqObject(value: T): T {
    eq(value)
    return uninitialized()
}

private fun argMatchItems(predicate: (List<SimpleItem>) -> Boolean): List<SimpleItem> {
    argThat<List<SimpleItem>> { predicate(it) }
    return uninitialized()
}

class BankServiceTeleportTest {

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

    private fun buildCharacter(mapId: Int = 545): ArtifactsCharacter = ArtifactsCharacter(
        name = "Renoir", account = "tellenn", level = 30, gold = 0,
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

    @Test
    fun `emptyInventory withdraws bank teleport potion when available`() {
        val character = buildCharacter()
        val potion = ItemDetails("tp_bank", "TP Bank", "", "consumable", "potion", 1,
            true, false, null, listOf(Effect("teleport", 545, null)), null)
        `when`(teleportService.findBankPotionAvailableInBank(character)).thenReturn(potion)

        val bankDoc = mock(BankItemDocument::class.java)
        `when`(bankDoc.quantity).thenReturn(3)
        `when`(bankRepository.findByCode("tp_bank")).thenReturn(bankDoc)

        @Suppress("UNCHECKED_CAST")
        val withdrawResp = mock(ArtifactsResponseBody::class.java) as ArtifactsResponseBody<BankItemTransaction>
        val withdrawBody = mock(BankItemTransaction::class.java)
        `when`(withdrawBody.character).thenReturn(character)
        `when`(withdrawResp.data).thenReturn(withdrawBody)
        `when`(bankClient.withdrawItems(eqObject("Renoir"), anyObject())).thenReturn(withdrawResp)

        service.emptyInventory(character)

        verify(bankClient).withdrawItems(eqObject("Renoir"), argMatchItems { items ->
            items.any { it.code == "tp_bank" && it.quantity == 1 }
        })
    }

    @Test
    fun `emptyInventory proceeds normally when no bank teleport potion available`() {
        val character = buildCharacter()
        `when`(teleportService.findBankPotionAvailableInBank(character)).thenReturn(null)

        service.emptyInventory(character)

        verify(bankClient, never()).withdrawItems(anyObject(), anyObject())
    }
}
