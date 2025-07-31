package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown

class RestResponseBody(
    @JsonAlias("cooldown") val cooldown: Cooldown,
    @JsonAlias("character") val character: ArtifactsCharacter,
    @JsonAlias("hp_restored") val hpRestored: Int
)