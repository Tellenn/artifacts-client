package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem

class BankGoldTransaction(
    @JsonProperty("cooldown") val cooldown: Cooldown,
    @JsonProperty("character") val character: ArtifactsCharacter,
    @JsonProperty("bank") val bank: GoldTransaction
)

class GoldTransaction(
    @JsonProperty("gold") val quantity: Int
)
