package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

class UseItemResponseBody(
    @JsonAlias("cooldown") val cooldown: Cooldown?,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("message") val message: String?
)