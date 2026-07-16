package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import java.time.Instant

/**
 * Ordre renvoyé par les actions GE : achat/annulation (GETransactionSchema) et
 * création d'ordre de vente (GEOrderCreatedSchema, qui ajoute `created_at`).
 */
@Suppress("unused")
class GETransactionOrder(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("quantity") val quantity: Int,
    @param:JsonProperty("price") val price: Int,
    @param:JsonProperty("total_price") val totalPrice: Int,
    @param:JsonProperty("created_at") val createdAt: Instant? = null,
)

@Suppress("unused")
class GEBuyTransaction(
    @param:JsonProperty("cooldown") val cooldown: Cooldown,
    @param:JsonProperty("character") val character: ArtifactsCharacter,
    @param:JsonProperty("order") val order: GETransactionOrder
)
