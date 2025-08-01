package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty

class UnequipRequest(
    @JsonProperty("slot") val slot: String,
    @JsonProperty("quantity") val quantity: Int = 1
)