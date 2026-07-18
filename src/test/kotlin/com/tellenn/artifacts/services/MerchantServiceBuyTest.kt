package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.EventClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.NpcMerchantTransaction
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.BankDetails
import com.tellenn.artifacts.models.Content
import com.tellenn.artifacts.models.EventData
import com.tellenn.artifacts.models.NpcItem
import com.tellenn.artifacts.models.SimpleItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
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
 * Achat chez un marchand fixe (hors événement). Le marchand est identifié via /npcs/items et
 * accepté seulement si son code n'est pas dans /events?type=npc. Devise or ou item. L'achat est
 * plafonné par la quantité demandée et par la devise disponible en banque.
 */
class MerchantServiceBuyTest {

    private lateinit var npcClient: NpcClient
    private lateinit var bankService: BankService
    private lateinit var movementService: MovementService
    private lateinit var itemService: ItemService
    private lateinit var accountClient: AccountClient
    private lateinit var eventClient: EventClient
    private lateinit var merchantService: MerchantService

    private val fixedMerchant = "pearl_diver"
    private val eventMerchant = "nomadic_merchant"
    private val item = "perfect_pearl"
    private val pearlCurrency = "small_pearls"
    private val price = 20

    @BeforeEach
    fun setUp() {
        npcClient = mock(NpcClient::class.java)
        bankService = mock(BankService::class.java)
        movementService = mock(MovementService::class.java)
        itemService = mock(ItemService::class.java)
        accountClient = mock(AccountClient::class.java)
        eventClient = mock(EventClient::class.java)
        merchantService = MerchantService(npcClient, bankService, movementService, itemService, accountClient, eventClient)
        givenNoEventNpcs()
    }

    @Test
    fun `buyFromFixedMerchant achete en devise item, plafonne par le stock banque, depose l'item`() {
        // given — marchand fixe, devise small_pearls ; 60 en banque ne couvrent que 3 exemplaires.
        givenMerchantSells(npcItem(fixedMerchant, currency = pearlCurrency))
        `when`(bankService.quantityInBank(pearlCurrency)).thenReturn(60)
        val trader = character("Aerith")
        givenMovementAndBankPassThrough(trader)
        givenBuySucceeds(trader)

        // when — on en demande 5, seulement 3 achetables
        merchantService.buyFromFixedMerchant(trader, item, 5)

        // then
        verify(bankService).withdrawOne(pearlCurrency, 3 * price, trader)
        verify(npcClient).buyItem("Aerith", item, 3)
        verify(bankService).deposit(anyObject(), argThatSingleItem(item, 3))
    }

    @Test
    fun `buyFromFixedMerchant en devise or retire l'or et non un item`() {
        // given — marchand fixe vendant contre de l'or, banque bien fournie
        givenMerchantSells(npcItem(fixedMerchant, currency = "gold"))
        `when`(bankService.getBankDetails()).thenReturn(bankWith(gold = 1_000))
        val trader = character("Aerith")
        givenMovementAndBankPassThrough(trader)
        givenBuySucceeds(trader)

        // when
        merchantService.buyFromFixedMerchant(trader, item, 4)

        // then
        verify(bankService).withdrawGold(4 * price, trader)
        verify(npcClient).buyItem("Aerith", item, 4)
    }

    @Test
    fun `buyFromFixedMerchant plafonne par la quantite demandee`() {
        // given — banque quasi illimitée : c'est la demande (2) qui borne
        givenMerchantSells(npcItem(fixedMerchant, currency = pearlCurrency))
        `when`(bankService.quantityInBank(pearlCurrency)).thenReturn(10_000)
        val trader = character("Aerith")
        givenMovementAndBankPassThrough(trader)
        givenBuySucceeds(trader)

        // when
        merchantService.buyFromFixedMerchant(trader, item, 2)

        // then
        verify(npcClient).buyItem("Aerith", item, 2)
    }

    @Test
    fun `buyFromFixedMerchant ignore un marchand d'evenement`() {
        // given — le seul vendeur est un marchand d'événement : aucun achat
        givenMerchantSells(npcItem(eventMerchant, currency = pearlCurrency))
        givenEventNpcs(eventMerchant)
        val trader = character("Aerith")

        // when
        val result = merchantService.buyFromFixedMerchant(trader, item, 5)

        // then
        assertEquals(trader, result)
        verify(npcClient, never()).buyItem(anyString(), anyString(), anyInt())
    }

    @Test
    fun `buyFromFixedMerchant ne fait rien quand la banque ne couvre aucun exemplaire`() {
        // given — marchand fixe mais 0 devise en banque
        givenMerchantSells(npcItem(fixedMerchant, currency = pearlCurrency))
        `when`(bankService.quantityInBank(pearlCurrency)).thenReturn(0)
        val trader = character("Aerith")

        // when
        val result = merchantService.buyFromFixedMerchant(trader, item, 5)

        // then
        assertEquals(trader, result)
        verify(npcClient, never()).buyItem(anyString(), anyString(), anyInt())
    }

    @Test
    fun `listBuyableItems exclut les marchands d'evenement et enrichit devise, solde banque et finançable`() {
        // given — 2 offres fixes (or + small_pearls) et 1 offre d'événement, à écarter.
        `when`(npcClient.getAllNpcItems()).thenReturn(listOf(
            NpcItem(code = "perfect_pearl", npc = fixedMerchant, currency = pearlCurrency, buyPrice = 20, sellPrice = null),
            NpcItem(code = "healing_aura_rune", npc = "rune_seller", currency = "gold", buyPrice = 500, sellPrice = null),
            NpcItem(code = "lost_world_map", npc = eventMerchant, currency = "gold", buyPrice = 15000, sellPrice = null),
        ))
        givenEventNpcs(eventMerchant)
        `when`(bankService.quantityInBank(pearlCurrency)).thenReturn(60)
        `when`(bankService.getBankDetails()).thenReturn(bankWith(gold = 1_200))

        // when
        val result = merchantService.listBuyableItems()

        // then — l'offre d'événement exclue, tri par code, solde + finançable calculés
        assertEquals(listOf("healing_aura_rune", "perfect_pearl"), result.map { it.code })
        val pearl = result.first { it.code == "perfect_pearl" }
        assertEquals(pearlCurrency, pearl.currency)
        assertEquals(60, pearl.currencyInBank)
        assertEquals(3, pearl.affordable)
        val rune = result.first { it.code == "healing_aura_rune" }
        assertEquals(1_200, rune.currencyInBank)
        assertEquals(2, rune.affordable)
    }

    @Test
    fun `findFixedMerchantSelling retourne null quand seul un marchand d'evenement vend l'item`() {
        // given
        givenMerchantSells(npcItem(eventMerchant, currency = pearlCurrency))
        givenEventNpcs(eventMerchant)

        // when / then
        assertEquals(null, merchantService.findFixedMerchantSelling(item))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun givenMerchantSells(vararg offers: NpcItem) {
        `when`(npcClient.getNpcByItemCode(item)).thenReturn(
            ArtifactsArrayResponseBody(offers.toList(), offers.size, 1, offers.size, 1)
        )
    }

    private fun npcItem(npc: String, currency: String) =
        NpcItem(code = item, npc = npc, currency = currency, buyPrice = price, sellPrice = null)

    private fun givenNoEventNpcs() = givenEventNpcs()

    private fun givenEventNpcs(vararg npcCodes: String) {
        val events = npcCodes.map { eventData(it) }
        `when`(eventClient.getEvents("npc", 1, 50)).thenReturn(
            ArtifactsArrayResponseBody(events, events.size, 1, events.size, 1)
        )
    }

    private fun eventData(npcCode: String) = EventData(
        name = npcCode, code = npcCode, content = Content(type = "npc", code = npcCode),
        maps = emptyList(), duration = "PT2H", rate = 1, cooldown = 0, price = null,
        transition = null, cooldownExpiration = null,
    )

    private fun bankWith(gold: Int) = BankDetails(gold = gold, nextExpansionCost = 0, expansions = 0, slots = 0)

    private fun givenMovementAndBankPassThrough(character: ArtifactsCharacter) {
        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenReturn(character)
        `when`(movementService.moveToNpc(anyObject(), anyString())).thenReturn(character)
        `when`(bankService.withdrawOne(anyString(), anyInt(), anyObject())).thenReturn(character)
        `when`(bankService.withdrawGold(anyInt(), anyObject())).thenReturn(character)
        `when`(bankService.deposit(anyObject(), anyObject())).thenReturn(character)
    }

    private fun givenBuySucceeds(characterAfter: ArtifactsCharacter) {
        val transaction = mock(NpcMerchantTransaction::class.java)
        `when`(transaction.character).thenReturn(characterAfter)
        `when`(npcClient.buyItem(anyString(), anyString(), anyInt())).thenReturn(ArtifactsResponseBody(transaction))
    }

    private fun argThatSingleItem(code: String, quantity: Int): List<SimpleItem> {
        org.mockito.Mockito.argThat<List<SimpleItem>> { items ->
            items.size == 1 && items.first().code == code && items.first().quantity == quantity
        }
        return uninitialized()
    }

    private fun character(name: String) = ArtifactsCharacter(
        name = name, account = "acc", level = 40, gold = 0, hp = 100, maxHp = 100, x = 0, y = 0,
        mapId = 1, layer = "main", inventory = arrayOf(), cooldown = 0, skin = null, task = null,
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
        artifact1Slot = null, artifact2Slot = null, artifact3Slot = null,
        utility1Slot = "", utility1SlotQuantity = 0, utility2Slot = "", utility2SlotQuantity = 0,
        bagSlot = null, cooldownExpiration = Instant.now(),
    )
}
