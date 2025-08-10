package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

class NpcMerchantTransaction(
    @JsonProperty("cooldown") val cooldown: Cooldown,
    @JsonProperty("character") val character: ArtifactsCharacter,
    @JsonProperty("transaction") val transaction: Transaction
)

class Transaction(
    @JsonProperty("code") val code: String,
    @JsonProperty("quantity") val quantity: Int,
    @JsonProperty("currency") val currency: String,
    @JsonProperty("price") val price: Int,
    @JsonProperty("total_price") val totalPrice: String,
)