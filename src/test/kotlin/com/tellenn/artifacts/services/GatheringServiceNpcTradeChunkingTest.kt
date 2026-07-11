package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.CraftingClient
import com.tellenn.artifacts.clients.GatheringClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.NpcMerchantTransaction
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.NpcItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Helpers null-safe pour les matchers Mockito sur des paramètres Kotlin non-nullables :
 * `Mockito.any()` renvoie `null`, ce que l'assertion de non-nullité Kotlin rejette.
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

/**
 * Reproduit le livelock du 2026-07-10 : Renoir voulait acheter un composant NPC dont la devise
 * coûtait 384 snake_hide pour un inventaire de 158 slots. Le retrait banque de la devise en un
 * seul appel est rejeté en 497 quel que soit l'état de l'inventaire — l'achat doit être chunké.
 */
class GatheringServiceNpcTradeChunkingTest {

    private lateinit var bankService: BankService
    private lateinit var itemService: ItemService
    private lateinit var movementService: MovementService
    private lateinit var npcClient: NpcClient
    private lateinit var gatheringService: GatheringService

    private val character = character()
    private val withdrawnCurrencyBatches = mutableListOf<Int>()
    private val boughtBatches = mutableListOf<Int>()

    @BeforeEach
    fun setUp() {
        bankService = mock(BankService::class.java)
        itemService = mock(ItemService::class.java)
        movementService = mock(MovementService::class.java)
        npcClient = mock(NpcClient::class.java)

        gatheringService = GatheringService(
            gatheringClient = mock(GatheringClient::class.java),
            mapService = mock(MapService::class.java),
            movementService = movementService,
            bankService = bankService,
            characterService = mock(CharacterService::class.java),
            craftingClient = mock(CraftingClient::class.java),
            resourceService = mock(ResourceService::class.java),
            itemService = itemService,
            battleService = mock(BattleService::class.java),
            equipmentService = mock(EquipmentService::class.java),
            accountClient = mock(AccountClient::class.java),
            npcClient = npcClient,
            grandExchangeService = mock(GrandExchangeService::class.java),
            gatheringTaskService = mock(GatheringTaskService::class.java),
            materialResponsibility = mock(MaterialResponsibility::class.java),
            characterContextService = mock(CharacterContextService::class.java),
        )

        `when`(itemService.getInvSizeToCraft(anyObject())).thenReturn(0)
        `when`(itemService.getItem("strange_ingredient")).thenReturn(item("strange_ingredient", subtype = "npc"))
        `when`(itemService.getItem("snake_hide")).thenReturn(item("snake_hide", subtype = "mob"))
        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenReturn(character)
        `when`(movementService.moveToNpc(anyObject(), anyString())).thenReturn(character)
        `when`(bankService.emptyInventory(anyObject())).thenReturn(character)
        `when`(bankService.withdrawOne(anyString(), anyInt(), anyObject())).thenAnswer { invocation ->
            if (invocation.getArgument<String>(0) == "snake_hide") {
                withdrawnCurrencyBatches.add(invocation.getArgument(1))
            }
            character
        }
        val transaction = mock(NpcMerchantTransaction::class.java)
        `when`(transaction.character).thenReturn(character)
        `when`(npcClient.buyItem(anyString(), anyString(), anyInt())).thenAnswer { invocation ->
            boughtBatches.add(invocation.getArgument(2))
            ArtifactsResponseBody(transaction)
        }
    }

    @Test
    fun `l'achat NPC chunke les retraits de devise pour ne jamais depasser la capacite d'inventaire`() {
        // given — 48 items à 8 snake_hide pièce = 384 de devise pour 100 slots d'inventaire
        givenNpcSells("strange_ingredient", currency = "snake_hide", buyPrice = 8)
        `when`(bankService.availableQuantity("snake_hide")).thenReturn(384)

        // when
        gatheringService.craftOrGather(character, "strange_ingredient", 48, functionLevel = 1)

        // then — aucun retrait de devise ne dépasse ce que l'inventaire peut contenir
        assertTrue(
            withdrawnCurrencyBatches.all { it <= character.inventoryMaxItems },
            "Retraits de devise dépassant l'inventaire (${character.inventoryMaxItems}) : $withdrawnCurrencyBatches"
        )
    }

    @Test
    fun `l'achat NPC chunke preserve la quantite totale achetee et la devise depensee`() {
        // given
        givenNpcSells("strange_ingredient", currency = "snake_hide", buyPrice = 8)
        `when`(bankService.availableQuantity("snake_hide")).thenReturn(384)

        // when
        gatheringService.craftOrGather(character, "strange_ingredient", 48, functionLevel = 1)

        // then
        assertEquals(48, boughtBatches.sum(), "Quantité totale achetée au NPC")
        assertEquals(384, withdrawnCurrencyBatches.sum(), "Devise totale retirée de la banque")
    }

    @Test
    fun `un achat NPC qui tient dans l'inventaire reste un aller-retour unique`() {
        // given — 5 items à 2 de devise = 10, largement sous la capacité
        givenNpcSells("strange_ingredient", currency = "snake_hide", buyPrice = 2)
        `when`(bankService.availableQuantity("snake_hide")).thenReturn(10)

        // when
        gatheringService.craftOrGather(character, "strange_ingredient", 5, functionLevel = 1)

        // then — un seul retrait de devise, un seul achat, pas de re-retrait de l'item acheté
        assertEquals(listOf(10), withdrawnCurrencyBatches)
        assertEquals(listOf(5), boughtBatches)
        verify(bankService, never()).withdrawOne(eqObject("strange_ingredient"), anyInt(), anyObject())
    }

    private fun givenNpcSells(itemCode: String, currency: String, buyPrice: Int) {
        val npcItem = NpcItem(code = itemCode, npc = "nomadic_merchant", currency = currency, buyPrice = buyPrice, sellPrice = null)
        `when`(npcClient.getNpcByItemCode(itemCode))
            .thenReturn(ArtifactsArrayResponseBody(listOf(npcItem), total = 1, page = 1, size = 1, pages = 1))
    }

    private fun item(code: String, subtype: String) = ItemDetails(
        code = code, name = code, description = "", type = "resource", subtype = subtype,
        level = 10, tradeable = true, craft = null, effects = emptyList(), conditions = emptyList()
    )

    private fun character() = ArtifactsCharacter(
        name = "Renoir", account = "tellenn", level = 20, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 100, miningLevel = 20, woodcuttingXp = 0, woodcuttingMaxXp = 100,
        woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 100, fishingLevel = 1,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 100, weaponcraftingLevel = 20,
        gearcraftingXp = 0, gearcraftingMaxXp = 100, gearcraftingLevel = 1,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 100, jewelrycraftingLevel = 1,
        cookingXp = 0, cookingMaxXp = 100, cookingLevel = 1,
        alchemyXp = 0, alchemyMaxXp = 100, alchemyLevel = 1,
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
