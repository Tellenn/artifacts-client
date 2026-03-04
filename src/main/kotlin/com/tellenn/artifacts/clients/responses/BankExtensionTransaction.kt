package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

@Suppress("unused")
class BankExtensionTransaction(
    @param:JsonProperty("cooldown") val cooldown: Cooldown,
    @param:JsonProperty("character") val character: ArtifactsCharacter,
    @param:JsonProperty("transaction") val transaction: BankTransaction
)

@Suppress("unused")
class BankTransaction(
    @param:JsonProperty("price") val price: Int
)
