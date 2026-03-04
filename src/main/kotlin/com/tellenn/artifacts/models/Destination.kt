package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class Destination(
    @param:JsonProperty("name") val name : String,
    @param:JsonProperty("skin") val skin : String,
    @param:JsonProperty("x") val x : Int,
    @param:JsonProperty("y") val y : Int,
    @param:JsonProperty("map_id") val mapId : Int,
    @param:JsonProperty("layer") val layer : String,
    @param:JsonProperty("access") val access: Access?,
    @param:JsonProperty("interactions") val interactions: Interactions?,
) {
}