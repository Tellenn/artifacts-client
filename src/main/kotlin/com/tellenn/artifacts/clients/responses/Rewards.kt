package com.tellenn.artifacts.clients.responses
import com.fasterxml.jackson.annotation.JsonAlias
import com.tellenn.artifacts.models.SimpleItem

class Rewards(
    @JsonAlias("items") val items: List<SimpleItem>,
    @JsonAlias("gold") val gold: Int
)