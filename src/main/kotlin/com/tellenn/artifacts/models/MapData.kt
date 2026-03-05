package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class MapData(
    @param:JsonProperty("name") val name: String = "map",
    @param:JsonProperty("skin") val skin: String,
    @param:JsonProperty("x") val x: Int,
    @param:JsonProperty("y") val y: Int,
    @param:JsonProperty("map_id") val mapId: Int,
    @param:JsonProperty("layer") val layer: String,
    @param:JsonProperty("access") val access: Access?,
    @param:JsonProperty("interactions") val interactions: Interactions?,
    var region: Int? // Added by analyzing which map you can directly enter from another. Begining one is 1
)

@Suppress("unused")
class Access(
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("conditions") val conditions: List<Conditions>
)

@Suppress("unused")
class Conditions(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("operator") val operator: String,
    @param:JsonProperty("value") val value: Int
)

@Suppress("unused")
class Interactions(
    @param:JsonProperty("content") val content: MapContent?,
    @param:JsonProperty("transition") val transition: Transition?
)

@Suppress("unused")
class Transition(
    @param:JsonProperty("map_id") val mapId: Int,
    @param:JsonProperty("layer") val layer: String,
    @param:JsonProperty("x") val x: Int,
    @param:JsonProperty("y") val y: Int,
    @param:JsonProperty("conditions") val conditions: List<Conditions>,
)