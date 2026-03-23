package com.tellenn.artifacts.clients.responses

import com.fasterxml.jackson.annotation.JsonProperty
import com.tellenn.artifacts.models.Cooldown
import com.tellenn.artifacts.models.Destination
import com.tellenn.artifacts.models.ArtifactsCharacter
import com.tellenn.artifacts.models.Conditions

open class TransitionResponseBody(
    val cooldown : Cooldown,
    val destination: Destination,
    val character: ArtifactsCharacter,
    val transition: Transition)

class Transition(
    @param:JsonProperty("map_id") val mapId: Int,
    @param:JsonProperty("layer") val layer: String,
    @param:JsonProperty("x") val x: Int,
    @param:JsonProperty("y") val y: Int,
    @param:JsonProperty("conditions") val conditions: List<Conditions>?,
)