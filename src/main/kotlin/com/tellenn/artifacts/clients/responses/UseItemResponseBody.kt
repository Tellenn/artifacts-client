package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.ItemDetails

class UseItemResponseBody(
    @JsonProperty("cooldown") val cooldown: Cooldown?,
    @JsonProperty("character") val character: ArtifactsCharacter,
    @JsonProperty("item") val item: ItemDetails?
)