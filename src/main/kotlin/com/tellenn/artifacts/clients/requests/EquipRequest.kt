package com.tellenn.artifacts.clients.requests

import com.fasterxml.jackson.annotation.JsonProperty

class EquipRequest(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("slot") val slot: String,
    @param:JsonProperty("quantity") val quantity: Int
)