package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

class NpcMerchantTransaction(
    @JsonAlias("cooldown") val cooldown: Cooldown,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("transaction") val transaction: Transaction
)

class Transaction(
    @JsonAlias("code") val code: String,
    @JsonAlias("quantity") val quantity: Int,
    @JsonAlias("currency") val currency: String,
    @JsonAlias("price") val price: Int,
    @JsonAlias("total_price") val totalPrice: String,
)