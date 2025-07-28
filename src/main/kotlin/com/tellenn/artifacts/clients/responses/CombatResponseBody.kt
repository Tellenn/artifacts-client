package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.clients.models.ArtifactsCharacter
import com.tellenn.artifacts.clients.models.Cooldown
import com.tellenn.artifacts.clients.models.SimpleItem

class CombatResponseBody(
    @JsonAlias("cooldown") val cooldown: Cooldown,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("fight") val fight: Fight?
)

class Fight(
    @JsonAlias("xp") val xp: Int,
    @JsonAlias("gold") val gold: Int,
    @JsonAlias("drops") val drops: List<SimpleItem>,
    @JsonAlias("turns") val turns: Int,
    @JsonAlias("logs") val logs: List<String>,
    @JsonAlias("result") val result: String
)