package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.ItemDetails

class UseItemResponseBody(
    @param:JsonProperty("cooldown") val cooldown: Cooldown?,
    @param:JsonProperty("character") val character: ArtifactsCharacter,
    @param:JsonProperty("item") val item: ItemDetails?
)