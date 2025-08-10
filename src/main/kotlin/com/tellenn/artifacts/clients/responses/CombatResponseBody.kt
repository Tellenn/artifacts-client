package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem

class CombatResponseBody(
    @JsonProperty("cooldown") val cooldown: Cooldown,
    @JsonProperty("character") val character: ArtifactsCharacter,
    @JsonProperty("fight") val fight: Fight?
)

class Fight(
    @JsonProperty("xp") val xp: Int,
    @JsonProperty("gold") val gold: Int,
    @JsonProperty("drops") val drops: List<SimpleItem>,
    @JsonProperty("turns") val turns: Int,
    @JsonProperty("logs") val logs: List<String>,
    @JsonProperty("result") val result: String
)