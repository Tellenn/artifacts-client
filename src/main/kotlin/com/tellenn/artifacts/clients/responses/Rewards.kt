package com.tellenn.artifacts.clients.responses
import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.SimpleItem

class Rewards(
    @JsonProperty("items") val items: List<SimpleItem>,
    @JsonProperty("gold") val gold: Int
)