package com.tellenn.artifacts.services

import com.tellenn.artifacts.clients.AccountClient
import com.tellenn.artifacts.models.NpcItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

// Mockito any() renvoie null : NPE sur un paramètre Kotlin non-null. On enregistre le matcher
// puis on renvoie une valeur non vérifiée pour satisfaire le compilateur.
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
 * Missions d'achat marchand déclenchées par le dashboard : pré-checks read-only (marchand fixe
 * présent, devise suffisante) puis assignation d'une mission HUMAN_ORDER au trader.
 */
class MerchantOrderServiceTest {

    private lateinit var merchantService: MerchantService
    private lateinit var threadService: ThreadService
    private lateinit var accountClient: AccountClient
    private lateinit var orderService: MerchantOrderService

    private val item = "perfect_pearl"
    private val currency = "small_pearls"
    private val price = 20

    @BeforeEach
    fun setUp() {
        merchantService = mock(MerchantService::class.java)
        threadService = mock(ThreadService::class.java)
        accountClient = mock(AccountClient::class.java)
        orderService = MerchantOrderService(merchantService, threadService, accountClient)
    }

    @Test
    fun `requestBuy rejette quand aucun marchand fixe ne vend l'item`() {
        // given
        `when`(merchantService.findFixedMerchantSelling(item)).thenReturn(null)

        // when
        val result = orderService.requestBuy(item, 5)

        // then
        assertInstanceOf(MerchantMissionResult.Rejected::class.java, result)
        verify(threadService, never()).assignMissionAsync(anyObject(), anyObject(), anyObject())
    }

    @Test
    fun `requestBuy rejette quand la devise en banque est insuffisante`() {
        // given — de quoi acheter 3 seulement, on en demande 5
        givenFixedMerchant()
        `when`(merchantService.affordableQuantity(currency, price)).thenReturn(3)

        // when
        val result = orderService.requestBuy(item, 5)

        // then
        assertInstanceOf(MerchantMissionResult.Rejected::class.java, result)
        verify(threadService, never()).assignMissionAsync(anyObject(), anyObject(), anyObject())
    }

    @Test
    fun `requestBuy assigne une mission HUMAN_ORDER quand tout est ok`() {
        // given
        givenFixedMerchant()
        `when`(merchantService.affordableQuantity(currency, price)).thenReturn(10)
        `when`(threadService.assignMissionAsync(eqObject("Aerith"), eqObject(MissionPriority.HUMAN_ORDER), anyObject()))
            .thenReturn(true)

        // when
        val result = orderService.requestBuy(item, 5)

        // then
        assertEquals(MerchantMissionResult.Accepted, result)
        verify(threadService).assignMissionAsync(eqObject("Aerith"), eqObject(MissionPriority.HUMAN_ORDER), anyObject())
    }

    @Test
    fun `requestBuy rejette quand le trader est deja sur une mission prioritaire`() {
        // given
        givenFixedMerchant()
        `when`(merchantService.affordableQuantity(currency, price)).thenReturn(10)
        `when`(threadService.assignMissionAsync(eqObject("Aerith"), eqObject(MissionPriority.HUMAN_ORDER), anyObject()))
            .thenReturn(false)

        // when
        val result = orderService.requestBuy(item, 5)

        // then
        assertInstanceOf(MerchantMissionResult.Rejected::class.java, result)
    }

    private fun givenFixedMerchant() {
        `when`(merchantService.findFixedMerchantSelling(item)).thenReturn(
            NpcItem(code = item, npc = "pearl_diver", currency = currency, buyPrice = price, sellPrice = null)
        )
    }
}
