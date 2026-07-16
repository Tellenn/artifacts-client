package com.tellenn.artifacts.controller

import com.tellenn.artifacts.services.GeMissionResult
import com.tellenn.artifacts.services.GrandExchangeOrderService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus

class GrandExchangeControllerTest {

    private lateinit var grandExchangeOrderService: GrandExchangeOrderService
    private lateinit var controller: GrandExchangeController

    @BeforeEach
    fun setUp() {
        grandExchangeOrderService = mock(GrandExchangeOrderService::class.java)
        controller = GrandExchangeController(grandExchangeOrderService)
    }

    @Test
    fun `buy repond 202 quand la mission est acceptee`() {
        // given
        `when`(grandExchangeOrderService.requestBuy("iron_ore", 20, 15)).thenReturn(GeMissionResult.Accepted)

        // when
        val response = controller.buy(GeBuyRequest("iron_ore", 20, 15))

        // then
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals("accepted", response.body?.status)
    }

    @Test
    fun `buy repond 409 avec la raison quand la mission est rejetee`() {
        // given
        `when`(grandExchangeOrderService.requestBuy("iron_ore", 20, 15))
            .thenReturn(GeMissionResult.Rejected("Aerith est déjà sur une mission prioritaire"))

        // when
        val response = controller.buy(GeBuyRequest("iron_ore", 20, 15))

        // then
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("Aerith est déjà sur une mission prioritaire", response.body?.reason)
    }

    @Test
    fun `buy repond 400 sur une quantite invalide sans appeler le service`() {
        // when
        val response = controller.buy(GeBuyRequest("iron_ore", 0, 15))

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verify(grandExchangeOrderService, never()).requestBuy(anyString(), anyInt(), anyInt())
    }

    @Test
    fun `sell repond 400 sur un code vide sans appeler le service`() {
        // when
        val response = controller.sell(GeSellRequest("", 10, 18))

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verify(grandExchangeOrderService, never()).requestSell(anyString(), anyInt(), anyInt())
    }

    @Test
    fun `sell repond 202 quand la mission est acceptee`() {
        // given
        `when`(grandExchangeOrderService.requestSell("iron_ore", 10, 18)).thenReturn(GeMissionResult.Accepted)

        // when
        val response = controller.sell(GeSellRequest("iron_ore", 10, 18))

        // then
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
    }

    @Test
    fun `cancel repond 400 sur un orderId vide sans appeler le service`() {
        // when
        val response = controller.cancel(GeCancelRequest(" "))

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verify(grandExchangeOrderService, never()).requestCancel(anyString())
    }

    @Test
    fun `cancel repond 202 quand la mission est acceptee`() {
        // given
        `when`(grandExchangeOrderService.requestCancel("order42")).thenReturn(GeMissionResult.Accepted)

        // when
        val response = controller.cancel(GeCancelRequest("order42"))

        // then
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
    }
}
