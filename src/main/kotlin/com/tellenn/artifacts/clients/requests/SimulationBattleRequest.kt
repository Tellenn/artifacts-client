package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter

class SimulationBattleRequest(
        @param:JsonProperty("characters") val characters: List<ArtifactsCharacter>,
        @param:JsonProperty("monster") val monster: String,
        @param:JsonProperty("iterations") val iterations: Int
)