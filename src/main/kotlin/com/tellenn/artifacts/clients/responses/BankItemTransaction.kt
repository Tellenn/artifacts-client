package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem

class BankItemTransaction(
    @param:JsonProperty("cooldown") val cooldown: Cooldown,
    @param:JsonProperty("character") val character: ArtifactsCharacter,
    @param:JsonProperty("items") val items: List<SimpleItem>,
    @param:JsonProperty("bank") val bank: List<SimpleItem>
)
