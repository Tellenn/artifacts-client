package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem

@Suppress("unused")
class CraftingResponseBody(
    @param:JsonProperty("cooldown") val cooldown: Cooldown,
    @param:JsonProperty("character") val character: ArtifactsCharacter,
    @param:JsonProperty("details") val details: Details
)

@Suppress("unused")
class Details (
    @param:JsonProperty("xp") val xp: Int,
    @param:JsonProperty("items") val items: List<SimpleItem>,
)
