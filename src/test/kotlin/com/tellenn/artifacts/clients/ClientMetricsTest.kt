package com.tellenn.artifacts.clients

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class ClientMetricsTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var clientMetrics: ClientMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        clientMetrics = ClientMetrics(meterRegistry)
    }

    private fun response(code: Int): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.test/whatever").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .build()

    @Test
    fun `should record timer with client method status and normalized uri tags`() {
        // when
        clientMetrics.executeTimed("BattleClient", "POST", "/my/Cloud/action/fight") { response(200) }

        // then
        val timer = meterRegistry.get("artifacts.api.request")
            .tag("client", "BattleClient")
            .tag("method", "POST")
            .tag("status", "200")
            .tag("uri", "/my/{name}/action/fight")
            .timer()
        assertEquals(1, timer.count())
    }

    @Test
    fun `should normalize numeric segments to id placeholder`() {
        // when
        clientMetrics.executeTimed("MapClient", "GET", "/maps/5/12") { response(200) }

        // then
        val timer = meterRegistry.get("artifacts.api.request")
            .tag("uri", "/maps/{id}/{id}")
            .timer()
        assertEquals(1, timer.count())
    }

    @Test
    fun `should normalize resource codes to code placeholder`() {
        // when
        clientMetrics.executeTimed("ItemClient", "GET", "/items/iron_sword") { response(200) }

        // then
        val timer = meterRegistry.get("artifacts.api.request")
            .tag("uri", "/items/{code}")
            .timer()
        assertEquals(1, timer.count())
    }

    @Test
    fun `should strip query parameters from uri tag`() {
        // when
        clientMetrics.executeTimed("ItemClient", "GET", "/items?page=2&size=100") { response(200) }

        // then
        val timer = meterRegistry.get("artifacts.api.request")
            .tag("uri", "/items")
            .timer()
        assertEquals(1, timer.count())
    }

    @Test
    fun `should record error status code on failed response`() {
        // when
        clientMetrics.executeTimed("BankClient", "POST", "/my/Renoir/action/bank/withdraw") { response(404) }

        // then
        val timer = meterRegistry.get("artifacts.api.request")
            .tag("status", "404")
            .timer()
        assertEquals(1, timer.count())
    }

    @Test
    fun `should record IO_ERROR status and rethrow when the call throws`() {
        // when
        assertThrows(IOException::class.java) {
            clientMetrics.executeTimed("BattleClient", "GET", "/my/Cloud") { throw IOException("timeout") }
        }

        // then
        val timer = meterRegistry.get("artifacts.api.request")
            .tag("status", "IO_ERROR")
            .tag("uri", "/my/{name}")
            .timer()
        assertEquals(1, timer.count())
    }

    @Test
    fun `should return the response unchanged`() {
        // when
        val result = clientMetrics.executeTimed("ItemClient", "GET", "/items") { response(200) }

        // then
        assertEquals(200, result.code)
    }
}
