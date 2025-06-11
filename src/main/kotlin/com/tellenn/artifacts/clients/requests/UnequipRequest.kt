package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty

class UnequipRequest(
    @JsonProperty("slot") val slot: String
)