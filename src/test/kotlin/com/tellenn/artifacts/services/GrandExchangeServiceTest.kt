package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.GrandExchangeClient
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import com.tellenn.artifacts.exceptions.GENoOrdersException
import com.tellenn.artifacts.exceptions.NotFoundException
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.GEOrder
import com.tellenn.artifacts.models.ItemDetails
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Une offre GC peut disparaître entre la lecture du prix et l'achat (achetée/expirée par un autre
 * acteur) : l'API répond 404 « Sell order not found ». buyFromGC doit traduire ce cas en
 * GENoOrdersException pour que l'appelant retombe sur gather/craft au lieu de propager le 404 brut,
 * qui empoisonnait la tranche de production (ex. steel_bar → iron_ore).
 */
class GrandExchangeServiceTest {

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T
    private fun <T> anyObject(): T { any<T>(); return uninitialized() }

    private lateinit var grandExchangeClient: GrandExchangeClient
    private lateinit var movementService: MovementService
    private lateinit var bankService: BankService
    private lateinit var service: GrandExchangeService
    private val character = mock(ArtifactsCharacter::class.java)

    @BeforeEach
    fun setUp() {
        grandExchangeClient = mock(GrandExchangeClient::class.java)
        movementService = mock(MovementService::class.java)
        bankService = mock(BankService::class.java)
        service = GrandExchangeService(
            grandExchangeClient, mock(ItemService::class.java), mock(MapService::class.java),
            mock(MonsterService::class.java), mock(ResourceService::class.java),
            movementService, bankService, 1.0
        )
        `when`(character.name).thenReturn("Kepo")
        `when`(movementService.moveToBank(anyObject(), anyBoolean())).thenReturn(character)
        `when`(movementService.moveToGrandExchange(anyObject())).thenReturn(character)
        `when`(bankService.withdrawGold(anyInt(), anyObject())).thenReturn(character)
    }

    @Test
    fun `buyFromGC traduit un 404 d'offre disparue en GENoOrdersException`() {
        // given — une offre existe à la lecture du prix, mais disparaît avant l'achat (404)
        `when`(grandExchangeClient.getPublicSellOrders("iron_ore")).thenReturn(ordersOf(order()))
        `when`(grandExchangeClient.buyItem(anyString(), anyString(), anyInt()))
            .thenThrow(NotFoundException("Sell order not found."))

        // when / then — l'appelant retombera sur gather/craft au lieu de crasher sur le 404
        assertThrows(GENoOrdersException::class.java) {
            service.buyFromGC(character, ironOre(), 3)
        }
    }

    @Test
    fun `shouldBuyFromGC ignore un ordre d'achat et ne declenche pas un achat impossible`() {
        // given — le carnet ne contient qu'un ordre buy (type=buy), jamais achetable
        `when`(grandExchangeClient.getPublicSellOrders("iron_ore")).thenReturn(ordersOf(buyOrder()))

        // when / then — aucun ordre de vente : on n'achète pas (sinon 404 « Sell order not found »)
        assert(!service.shouldBuyFromGC(character, ironOre(), 3))
    }

    private fun order() = GEOrder("ord-1", "sell", "acc", "iron_ore", 100, 2, Instant.EPOCH)
    private fun buyOrder() = GEOrder("ord-buy", "buy", "acc", "iron_ore", 2000, 2, Instant.EPOCH)
    private fun ordersOf(vararg o: GEOrder) = ArtifactsArrayResponseBody(o.toList(), o.size, 1, o.size, 1)
    private fun ironOre() = ItemDetails(
        code = "iron_ore", name = "Iron Ore", description = "", type = "resource", subtype = "mining",
        level = 1, tradeable = true, craft = null, effects = emptyList(), conditions = emptyList()
    )
}
