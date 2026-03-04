package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

@Suppress("unused")
class TaskTradeResponseBody(
    @param:JsonProperty("cooldown") val cooldown: Cooldown,
    @param:JsonProperty("character") val character: ArtifactsCharacter,
    @param:JsonProperty("trade") val trade: Trade
)

@Suppress("unused")
class Trade(
    @param:JsonProperty("code") val items: String,
    @param:JsonProperty("quantity") val gold: Int
)