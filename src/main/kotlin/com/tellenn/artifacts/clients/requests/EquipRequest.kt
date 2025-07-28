package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty

class EquipRequest(
    @JsonProperty("code") val code: String,
    @JsonProperty("slot") val slot: String,
    @JsonProperty("quantity") val quantity: Int
)