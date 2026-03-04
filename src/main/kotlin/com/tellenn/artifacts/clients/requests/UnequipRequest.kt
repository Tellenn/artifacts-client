package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty

class UnequipRequest(
    @param:JsonProperty("slot") val slot: String,
    @param:JsonProperty("quantity") val quantity: Int = 1
)