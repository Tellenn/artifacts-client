package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.GrandExchangeClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.clients.responses.ArtifactsResponseBody
import com.tellenn.artifacts.clients.responses.GEBuyTransaction
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.BankDetails
import com.tellenn.artifacts.models.GEOrder
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
 * Missions GE humaines : pré-checks read-only (carnet d'ordres, or, stock banque)
 * puis mission Aerith HUMAN_ORDER. Les missions sont exécutées synchronement dans
 * les tests via le stub d'assignMissionAsync.
 */
class GrandExchangeOrderServiceTest {

    private lateinit var grandExchangeClient: GrandExchangeClient
    private lateinit var bankService: BankService
    private lateinit var movementService: MovementService
    private lateinit var threadService: ThreadService
    private lateinit var accountClient: AccountClient
    private lateinit var service: GrandExchangeOrderService

    private val aerith = character("Aerith")

    @BeforeEach
    fun setUp() {
        grandExchangeClient = mock(GrandExchangeClient::class.java)
        bankService = mock(BankService::class.java)
        movementService = mock(MovementService::class.java)
        threadService = mock(ThreadService::class.java)
        accountClient = mock(AccountClient::class.java)
        service = GrandExchangeOrderService(
            grandExchangeClient, bankService, movementService, threadService, accountClient
        )
    }

    // ── requestBuy ─────────────────────────────────────────────────────────────

    @Test
    fun `requestBuy rejette quand le carnet ne couvre pas la quantite sous le prix max`() {
        // given — un seul ordre de 5 unités sous le prix max, 20 demandées
        givenSellOrders(order("o1", price = 10, quantity = 5), order("o2", price = 50, quantity = 100))

        // when
        val result = service.requestBuy("iron_ore", 20, maxUnitPrice = 15)

        // then
        assertInstanceOf(GeMissionResult.Rejected::class.java, result)
        verify(threadService, never()).assignMissionAsync(anyString(), anyObject(), anyObject())
    }

    @Test
    fun `requestBuy rejette quand l'or en banque est insuffisant`() {
        // given — coût 200g, banque 100g
        givenSellOrders(order("o1", price = 10, quantity = 20))
        givenBankGold(100)

        // when
        val result = service.requestBuy("iron_ore", 20, maxUnitPrice = 15)

        // then
        assertInstanceOf(GeMissionResult.Rejected::class.java, result)
        verify(threadService, never()).assignMissionAsync(anyString(), anyObject(), anyObject())
    }

    @Test
    fun `requestBuy rejette quand Aerith est deja en mission prioritaire`() {
        // given
        givenSellOrders(order("o1", price = 10, quantity = 20))
        givenBankGold(10_000)
        `when`(threadService.assignMissionAsync(anyString(), anyObject(), anyObject())).thenReturn(false)

        // when
        val result = service.requestBuy("iron_ore", 20, maxUnitPrice = 15)

        // then
        assertInstanceOf(GeMissionResult.Rejected::class.java, result)
    }

    @Test
    fun `requestBuy accepte et la mission achete les ordres les moins chers`() {
        // given — 20 demandées : 15 sur l'ordre à 8g puis 5 sur l'ordre à 12g
        givenSellOrders(order("cheap", price = 8, quantity = 15), order("mid", price = 12, quantity = 50))
        givenBankGold(10_000)
        givenMissionRunsSynchronously()
        givenMovementAndBankPassThrough()
        givenGeTransactionSucceeds()

        // when
        val result = service.requestBuy("iron_ore", 20, maxUnitPrice = 15)

        // then
        assertTrue(result is GeMissionResult.Accepted)
        verify(bankService).withdrawGold(8 * 15 + 12 * 5, aerith)
        verify(grandExchangeClient).buyItem("Aerith", "cheap", 15)
        verify(grandExchangeClient).buyItem("Aerith", "mid", 5)
    }

    // ── requestSell ────────────────────────────────────────────────────────────

    @Test
    fun `requestSell rejette quand le stock banque est insuffisant`() {
        // given
        `when`(bankService.quantityInBank("iron_ore")).thenReturn(3)

        // when
        val result = service.requestSell("iron_ore", 20, unitPrice = 18)

        // then
        assertInstanceOf(GeMissionResult.Rejected::class.java, result)
        verify(threadService, never()).assignMissionAsync(anyString(), anyObject(), anyObject())
    }

    @Test
    fun `requestSell accepte et la mission cree l'ordre de vente`() {
        // given
        `when`(bankService.quantityInBank("iron_ore")).thenReturn(50)
        givenBankGold(10_000)
        givenMissionRunsSynchronously()
        givenMovementAndBankPassThrough()
        givenGeTransactionSucceeds()

        // when
        val result = service.requestSell("iron_ore", 20, unitPrice = 18)

        // then
        assertTrue(result is GeMissionResult.Accepted)
        verify(bankService).withdrawOne("iron_ore", 20, aerith)
        verify(grandExchangeClient).createSellOrder("Aerith", "iron_ore", 20, 18)
    }

    // ── requestCancel ──────────────────────────────────────────────────────────

    @Test
    fun `requestCancel accepte et la mission annule l'ordre`() {
        // given
        givenMissionRunsSynchronously()
        givenMovementAndBankPassThrough()
        givenGeTransactionSucceeds()

        // when
        val result = service.requestCancel("order42")

        // then
        assertTrue(result is GeMissionResult.Accepted)
        verify(grandExchangeClient).cancelOrder("Aerith", "order42")
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun givenSellOrders(vararg orders: GEOrder) {
        `when`(grandExchangeClient.getPublicSellOrders(anyString())).thenReturn(
            ArtifactsArrayResponseBody(data = orders.toList(), total = orders.size, page = 1, size = orders.size, pages = 1)
        )
    }

    private fun givenBankGold(gold: Int) {
        `when`(bankService.getBankDetails()).thenReturn(
            BankDetails(gold = gold, nextExpansionCost = 0, expansions = 0, slots = 0)
        )
    }

    /** assignMissionAsync exécute la mission immédiatement et rend true. */
    private fun givenMissionRunsSynchronously() {
        `when`(threadService.assignMissionAsync(anyString(), anyObject(), anyObject())).thenAnswer { invocation ->
            (invocation.getArgument(2) as () -> Unit)()
            true
        }
    }

    private fun givenMovementAndBankPassThrough() {
        `when`(accountClient.getCharacter("Aerith")).thenReturn(ArtifactsResponseBody(aerith))
        `when`(movementService.moveToBank(anyObject(), org.mockito.Mockito.anyBoolean())).thenReturn(aerith)
        `when`(movementService.moveToGrandExchange(anyObject())).thenReturn(aerith)
        `when`(bankService.withdrawGold(anyInt(), anyObject())).thenReturn(aerith)
        `when`(bankService.withdrawOne(anyString(), anyInt(), anyObject())).thenReturn(aerith)
        `when`(bankService.emptyInventory(anyObject())).thenReturn(aerith)
    }

    private fun givenGeTransactionSucceeds() {
        val transaction = mock(GEBuyTransaction::class.java)
        `when`(transaction.character).thenReturn(aerith)
        `when`(grandExchangeClient.buyItem(anyString(), anyString(), anyInt())).thenReturn(ArtifactsResponseBody(transaction))
        `when`(grandExchangeClient.createSellOrder(anyString(), anyString(), anyInt(), anyInt())).thenReturn(ArtifactsResponseBody(transaction))
        `when`(grandExchangeClient.cancelOrder(anyString(), anyString())).thenReturn(ArtifactsResponseBody(transaction))
    }

    private fun order(id: String, price: Int, quantity: Int) = GEOrder(
        id = id, itemCode = "iron_ore", itemName = "Iron Ore", quantity = quantity,
        price = price, total = price * quantity, createdAt = Instant.now(), status = "active",
    )

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
