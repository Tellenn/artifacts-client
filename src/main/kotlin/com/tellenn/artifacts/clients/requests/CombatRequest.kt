package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty

class CombatRequest(
    @param:JsonProperty("participants") val participants: List<String>
)