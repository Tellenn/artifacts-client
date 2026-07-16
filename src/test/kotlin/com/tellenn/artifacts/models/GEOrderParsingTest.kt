package com.tellenn.artifacts.models

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tellenn.artifacts.clients.responses.ArtifactsArrayResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * L'ObjectMapper des clients échoue sur tout champ inconnu : un modèle qui diverge
 * du schéma de l'API fait échouer le parsing, et `runCatching` transforme ça en
 * « aucun ordre » silencieux. Ce test fige la forme réelle de /grandexchange/orders.
 */
class GEOrderParsingTest {

    private val objectMapper = jacksonObjectMapper().apply { registerModule(JavaTimeModule()) }

    @Test
    fun `GEOrder parse la reponse reelle de l'API`() {
        // given — payload capturé sur GET /grandexchange/orders?code=copper_ore
        val json = """
            {"data":[{"id":"6a5928f8d2b2e7341650b364","type":"sell","account":"Cadaver",
            "code":"copper_ore","quantity":88,"price":5,"created_at":"2026-07-16T18:54:48.994Z"}],
            "total":1,"page":1,"size":1,"pages":1}
        """.trimIndent()

        // when
        val response = objectMapper.readValue<ArtifactsArrayResponseBody<GEOrder>>(json)

        // then
        val order = response.data.single()
        assertEquals("6a5928f8d2b2e7341650b364", order.id)
        assertEquals("copper_ore", order.code)
        assertEquals(88, order.quantity)
        assertEquals(5, order.price)
    }
}
