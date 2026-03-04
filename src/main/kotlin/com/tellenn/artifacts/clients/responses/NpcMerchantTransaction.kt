package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

@Suppress("unused")
class NpcMerchantTransaction(
    @param:JsonProperty("cooldown") val cooldown: Cooldown,
    @param:JsonProperty("character") val character: ArtifactsCharacter,
    @param:JsonProperty("transaction") val transaction: Transaction
)

@Suppress("unused")
class Transaction(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("quantity") val quantity: Int,
    @param:JsonProperty("currency") val currency: String,
    @param:JsonProperty("price") val price: Int,
    @param:JsonProperty("total_price") val totalPrice: String,
)