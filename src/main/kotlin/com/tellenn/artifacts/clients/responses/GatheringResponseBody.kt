package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.SimpleItem

class GatheringResponseBody(
    @JsonAlias("cooldown") val cooldown: Cooldown,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("details") val details: SkillInfo
)

class SkillInfo(
    @JsonAlias("xp") val xp: Int,
    @JsonAlias("items") val items: List<SimpleItem>
)