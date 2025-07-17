package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.Cooldown
import com.tellenn.artifacts.clients.models.SimpleItem

class TaskTradeResponseBody(
    @JsonAlias("cooldown") val cooldown: Cooldown,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("trade") val trade: Trade
)

class Trade(
    @JsonAlias("code") val items: String,
    @JsonAlias("quantity") val gold: Int
)