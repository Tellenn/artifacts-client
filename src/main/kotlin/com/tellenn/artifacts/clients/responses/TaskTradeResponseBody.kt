package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

class TaskTradeResponseBody(
    @JsonProperty("cooldown") val cooldown: Cooldown,
    @JsonProperty("character") val character: ArtifactsCharacter,
    @JsonProperty("trade") val trade: Trade
)

class Trade(
    @JsonProperty("code") val items: String,
    @JsonProperty("quantity") val gold: Int
)