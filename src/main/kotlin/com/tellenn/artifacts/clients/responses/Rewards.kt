package com.tellenn.artifacts.clients.responses
import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.SimpleItem

class Rewards(
    @param:JsonProperty("items") val items: List<SimpleItem>,
    @param:JsonProperty("gold") val gold: Int
)