package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.clients.NpcClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.models.ItemCraft
import com.tellenn.artifacts.models.ItemDetails
import com.tellenn.artifacts.models.NpcItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class MerchantServiceTest {

    private lateinit var npcClient: NpcClient
    private lateinit var bankService: BankService
    private lateinit var movementService: MovementService
    private lateinit var itemService: ItemService
    private lateinit var accountClient: AccountClient
    private lateinit var merchantService: MerchantService

    private val npcName = "herbal_merchant"

    @BeforeEach
    fun setUp() {
        npcClient = mock(NpcClient::class.java)
        bankService = mock(BankService::class.java)
        movementService = mock(MovementService::class.java)
        itemService = mock(ItemService::class.java)
        accountClient = mock(AccountClient::class.java)
        merchantService = MerchantService(npcClient, bankService, movementService, itemService, accountClient)
    }

    private fun npcItem(code: String, sellPrice: Int?) =
        NpcItem(code = code, npc = npcName, currency = "gold", buyPrice = null, sellPrice = sellPrice)

    private fun npcItemsResponse(vararg items: NpcItem) =
        ArtifactsArrayResponseBody(data = items.toList(), total = items.size, page = 1, size = items.size, pages = 1)

    private fun emptyNpcItemsResponse() =
        ArtifactsArrayResponseBody<NpcItem>(data = emptyList(), total = 0, page = 1, size = 0, pages = 0)

    private fun itemDetails(code: String, craft: ItemCraft?): ItemDetails =
        ItemDetails(
            code = code,
            name = code,
            description = "",
            type = "resource",
            subtype = "",
            level = 1,
            tradeable = true,
            recyclable = false,
            craft = craft,
            effects = null,
            conditions = null
        )

    @Test
    fun `findSellableItems returns an item that is valuable, non-craftable, not a currency and in the bank`() {
        // given
        val item = npcItem("sunflower", sellPrice = 150)
        `when`(npcClient.getNpcItems(npcName)).thenReturn(npcItemsResponse(item))
        `when`(itemService.getItem("sunflower")).thenReturn(itemDetails("sunflower", craft = null))
        `when`(npcClient.getItemsBoughtWith("sunflower")).thenReturn(emptyNpcItemsResponse())
        `when`(bankService.isInBank("sunflower", 1)).thenReturn(true)

        // when
        val result = merchantService.findSellableItems(npcName)

        // then
        assertEquals(1, result.size)
        assertEquals("sunflower", result.first().code)
    }

    @Test
    fun `findSellableItems excludes an item that is not in the bank`() {
        // given
        val item = npcItem("sunflower", sellPrice = 150)
        `when`(npcClient.getNpcItems(npcName)).thenReturn(npcItemsResponse(item))
        `when`(itemService.getItem("sunflower")).thenReturn(itemDetails("sunflower", craft = null))
        `when`(npcClient.getItemsBoughtWith("sunflower")).thenReturn(emptyNpcItemsResponse())
        `when`(bankService.isInBank("sunflower", 1)).thenReturn(false)

        // when
        val result = merchantService.findSellableItems(npcName)

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findSellableItems excludes an item whose sell price is too low`() {
        // given
        val item = npcItem("weed", sellPrice = 50)
        `when`(npcClient.getNpcItems(npcName)).thenReturn(npcItemsResponse(item))

        // when
        val result = merchantService.findSellableItems(npcName)

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findSellableItems excludes a craftable item`() {
        // given
        val item = npcItem("health_potion", sellPrice = 150)
        `when`(npcClient.getNpcItems(npcName)).thenReturn(npcItemsResponse(item))
        val craft = ItemCraft(skill = "alchemy", level = 1, items = emptyList(), quantity = 1)
        `when`(itemService.getItem("health_potion")).thenReturn(itemDetails("health_potion", craft = craft))

        // when
        val result = merchantService.findSellableItems(npcName)

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findSellableItems returns empty when the npc buys nothing`() {
        // given
        `when`(npcClient.getNpcItems(npcName)).thenReturn(emptyNpcItemsResponse())

        // when
        val result = merchantService.findSellableItems(npcName)

        // then
        assertTrue(result.isEmpty())
    }
}