package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

class TaskTradeResponseBody(
    @JsonAlias("cooldown") val cooldown: Cooldown,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("trade") val trade: Trade
)

class Trade(
    @JsonAlias("code") val items: String,
    @JsonAlias("quantity") val gold: Int
)