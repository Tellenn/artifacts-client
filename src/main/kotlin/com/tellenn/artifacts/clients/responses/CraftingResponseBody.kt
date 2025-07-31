package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem

class CraftingResponseBody(
    @JsonAlias("cooldown") val cooldown: Cooldown,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("details") val details: Details
)

class Details (
    @JsonAlias("xp") val xp: Int,
    @JsonAlias("items") val items: List<SimpleItem>,
)
