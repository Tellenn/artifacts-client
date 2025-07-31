package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.models.ArtifactsCharacter

class EquipmentResponseBody(
    @JsonAlias("character") val character: ArtifactsCharacter
)