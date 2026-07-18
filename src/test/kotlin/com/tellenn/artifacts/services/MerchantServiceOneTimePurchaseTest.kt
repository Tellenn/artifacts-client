package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.EventClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.NpcMerchantTransaction
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.BankDetails
import com.tellenn.artifacts.models.InventorySlot
import com.tellenn.artifacts.models.NpcItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant

@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyObject(): T {
    any<T>()
    return uninitialized()
}

/**
 * Registre d'achats NPC uniques par saison : la limite est auto-portée par le comptage
 * des exemplaires possédés (slots équipés + inventaires + banque). Une fois la cible
 * atteinte, plus aucun achat — zéro état persistant.
 */
class MerchantServiceOneTimePurchaseTest {

    private lateinit var npcClient: NpcClient
    private lateinit var bankService: BankService
    private lateinit var movementService: MovementService
    private lateinit var itemService: ItemService
    private lateinit var accountClient: AccountClient
    private lateinit var eventClient: EventClient
    private lateinit var merchantService: MerchantService

    private val npcCode = "nomadic_merchant"
    private val mapCode = "lost_world_map"
    private val mapPrice = 15000

    @BeforeEach
    fun setUp() {
        npcClient = mock(NpcClient::class.java)
        bankService = mock(BankService::class.java)
        movementService = mock(MovementService::class.java)
        itemService = mock(ItemService::class.java)
        accountClient = mock(AccountClient::class.java)
        eventClient = mock(EventClient::class.java)
        merchantService = MerchantService(npcClient, bankService, movementService, itemService, accountClient, eventClient)
    }

    // ── findPendingOneTimePurchases ────────────────────────────────────────────

    @Test
    fun `findPendingOneTimePurchases retourne la map quand il en manque et que l'or suffit`() {
        // given — personne ne possède de map, 15000 gold en banque
        givenNpcSells(lostWorldMap())
        givenCharacters(character("Cloud"), character("Renoir"))
        givenBankGold(mapPrice)
        `when`(bankService.quantityInBank(mapCode)).thenReturn(0)

        // when
        val result = merchantService.findPendingOneTimePurchases(npcCode)

        // then
        assertEquals(listOf(mapCode), result.map { it.code })
    }

    @Test
    fun `findPendingOneTimePurchases ignore un item hors registre`() {
        // given — le NPC ne vend que des items hors registre : aucun appel API personnage
        givenNpcSells(npcItem("backpack", buyPrice = 50000))

        // when
        val result = merchantService.findPendingOneTimePurchases(npcCode)

        // then
        assertTrue(result.isEmpty())
        verify(accountClient, never()).getCharacters()
    }

    @Test
    fun `findPendingOneTimePurchases retourne vide quand la cible de 5 est atteinte`() {
        // given — 2 équipées (slots artifact) + 1 en inventaire + 2 en banque = 5 possédées
        givenNpcSells(lostWorldMap())
        givenCharacters(
            character("Cloud", artifact1Slot = mapCode),
            character("Renoir", artifact2Slot = mapCode),
            character("Aerith", inventory = arrayOf(InventorySlot(slot = 1, code = mapCode, quantity = 1))),
        )
        givenBankGold(1_000_000)
        `when`(bankService.quantityInBank(mapCode)).thenReturn(2)

        // when
        val result = merchantService.findPendingOneTimePurchases(npcCode)

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findPendingOneTimePurchases retourne vide quand l'or ne suffit pas pour un exemplaire`() {
        // given
        givenNpcSells(lostWorldMap())
        givenCharacters(character("Cloud"))
        givenBankGold(mapPrice - 1)
        `when`(bankService.quantityInBank(mapCode)).thenReturn(0)

        // when
        val result = merchantService.findPendingOneTimePurchases(npcCode)

        // then
        assertTrue(result.isEmpty())
    }

    // ── buyOneTimePurchases ────────────────────────────────────────────────────

    @Test
    fun `buyOneTimePurchases achete le manquant plafonne par l'or en banque`() {
        // given — 1 possédée (banque), il en manque 4, mais l'or ne couvre que 2 exemplaires
        givenNpcSells(lostWorldMap())
        givenCharacters(character("Cloud"))
        givenBankGold(2 * mapPrice)
        `when`(bankService.quantityInBank(mapCode)).thenReturn(1)
        val aerith = character("Aerith")
        givenMovementAndBankPassThrough(aerith)
        givenBuySucceeds(aerith)

        // when
        merchantService.buyOneTimePurchases(aerith, npcCode)

        // then
        verify(bankService).withdrawGold(2 * mapPrice, aerith)
        verify(npcClient).buyItem("Aerith", mapCode, 2)
    }

    @Test
    fun `buyOneTimePurchases depose les achats en banque`() {
        // given — il manque 5 maps, or illimité
        givenNpcSells(lostWorldMap())
        givenCharacters(character("Cloud"))
        givenBankGold(1_000_000)
        `when`(bankService.quantityInBank(mapCode)).thenReturn(0)
        val aerith = character("Aerith")
        givenMovementAndBankPassThrough(aerith)
        givenBuySucceeds(aerith)

        // when
        merchantService.buyOneTimePurchases(aerith, npcCode)

        // then
        verify(bankService).deposit(anyObject(), argThatSingleItem(mapCode, 5))
    }

    @Test
    fun `buyOneTimePurchases ne fait rien quand rien n'est en attente`() {
        // given — cible atteinte : 5 en banque
        givenNpcSells(lostWorldMap())
        givenCharacters(character("Cloud"))
        givenBankGold(1_000_000)
        `when`(bankService.quantityInBank(mapCode)).thenReturn(5)
        val aerith = character("Aerith")

        // when
        val result = merchantService.buyOneTimePurchases(aerith, npcCode)

        // then
        assertEquals(aerith, result)
        verify(npcClient, never()).buyItem(anyString(), anyString(), anyInt())
        verify(bankService, never()).withdrawGold(anyInt(), anyObject())
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun givenNpcSells(vararg items: NpcItem) {
        `when`(npcClient.getNpcItems(npcCode)).thenReturn(
            ArtifactsArrayResponseBody(data = items.toList(), total = items.size, page = 1, size = items.size, pages = 1)
        )
    }

    private fun givenCharacters(vararg characters: ArtifactsCharacter) {
        `when`(accountClient.getCharacters()).thenReturn(ArtifactsResponseBody(characters.toList()))
    }

    private fun givenBankGold(gold: Int) {
        `when`(bankService.getBankDetails()).thenReturn(BankDetails(gold = gold, nextExpansionCost = 0, expansions = 0, slots = 0))
    }

    private fun lostWorldMap() = npcItem(mapCode, buyPrice = mapPrice)

    private fun npcItem(code: String, buyPrice: Int?) =
        NpcItem(code = code, npc = npcCode, currency = "gold", buyPrice = buyPrice, sellPrice = null)

    private fun character(
        name: String,
        artifact1Slot: String? = null,
        artifact2Slot: String? = null,
        artifact3Slot: String? = null,
        inventory: Array<InventorySlot> = arrayOf(),
    ) = ArtifactsCharacter(
        name = name, account = "acc", level = 40, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = inventory, cooldown = 0, skin = null, task = null,
        initiative = 0, threat = 0, dmg = 0, wisdom = 0, prospecting = 0, criticalStrike = 0, speed = 0,
        haste = 0, xp = 0, maxXp = 100, taskType = null, taskTotal = 0, taskProgress = 0,
        miningXp = 0, miningMaxXp = 100, miningLevel = 1, woodcuttingXp = 0, woodcuttingMaxXp = 100,
        woodcuttingLevel = 1, fishingXp = 0, fishingMaxXp = 100, fishingLevel = 1,
        weaponcraftingXp = 0, weaponcraftingMaxXp = 100, weaponcraftingLevel = 1,
        gearcraftingXp = 0, gearcraftingMaxXp = 100, gearcraftingLevel = 1,
        jewelrycraftingXp = 0, jewelrycraftingMaxXp = 100, jewelrycraftingLevel = 1,
        cookingXp = 0, cookingMaxXp = 100, cookingLevel = 1, alchemyXp = 0, alchemyMaxXp = 100, alchemyLevel = 1,
        inventoryMaxItems = 100, attackFire = 0, attackEarth = 0, attackWater = 0, attackAir = 0,
        dmgFire = 0, dmgEarth = 0, dmgWater = 0, dmgAir = 0, resFire = 0, resEarth = 0, resWater = 0, resAir = 0,
        weaponSlot = null, runeSlot = null, shieldSlot = null, helmetSlot = null, bodyArmorSlot = null,
        legArmorSlot = null, bootsSlot = null, ring1Slot = null, ring2Slot = null, amuletSlot = null,
        artifact1Slot = artifact1Slot, artifact2Slot = artifact2Slot, artifact3Slot = artifact3Slot,
        utility1Slot = "", utility1SlotQuantity = 0, utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = Instant.now(),
    )

    private fun givenMovementAndBankPassThrough(character: ArtifactsCharacter) {
        `when`(movementService.moveToBank(anyObject(), org.mockito.Mockito.anyBoolean())).thenReturn(character)
        `when`(movementService.moveToNpc(anyObject(), anyString())).thenReturn(character)
        `when`(bankService.withdrawGold(anyInt(), anyObject())).thenReturn(character)
        `when`(bankService.deposit(anyObject(), anyObject())).thenReturn(character)
    }

    private fun givenBuySucceeds(characterAfter: ArtifactsCharacter) {
        val transaction = mock(NpcMerchantTransaction::class.java)
        `when`(transaction.character).thenReturn(characterAfter)
        `when`(npcClient.buyItem(anyString(), anyString(), anyInt())).thenReturn(ArtifactsResponseBody(transaction))
    }

    private fun argThatSingleItem(code: String, quantity: Int): List<com.tellenn.artifacts.models.SimpleItem> {
        org.mockito.Mockito.argThat<List<com.tellenn.artifacts.models.SimpleItem>> { items ->
            items.size == 1 && items.first().code == code && items.first().quantity == quantity
        }
        return uninitialized()
    }
}
