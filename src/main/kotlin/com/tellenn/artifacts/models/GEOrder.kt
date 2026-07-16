package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/** Ordre du Grand Exchange — miroir exact de GEOrderSchema (l'ObjectMapper échoue sur un champ inconnu). */
@Suppress("unused")
class GEOrder(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("account") val account: String,
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("quantity") val quantity: Int,
    @param:JsonProperty("price") val price: Int,
    @param:JsonProperty("created_at") val createdAt: Instant,
)
