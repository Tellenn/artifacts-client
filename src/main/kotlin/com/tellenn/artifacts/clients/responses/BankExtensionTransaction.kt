package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem

class BankExtensionTransaction(
    @JsonProperty("cooldown") val cooldown: Cooldown,
    @JsonProperty("character") val character: ArtifactsCharacter,
    @JsonProperty("transaction") val transaction: BankTransaction
)

class BankTransaction(
    @JsonProperty("price") val price: Int
)
