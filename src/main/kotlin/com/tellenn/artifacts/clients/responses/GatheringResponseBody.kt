package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem

@Suppress("unused")
class GatheringResponseBody(
    @param:JsonProperty("cooldown") val cooldown: Cooldown,
    @param:JsonProperty("character") val character: ArtifactsCharacter,
    @param:JsonProperty("details") val details: SkillInfo
)

@Suppress("unused")
class SkillInfo(
    @param:JsonProperty("xp") val xp: Int,
    @param:JsonProperty("items") val items: List<SimpleItem>
)