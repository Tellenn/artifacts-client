package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("unused")
class MapData(
    @JsonProperty("name") val name: String = "map",
    @JsonProperty("skin") val skin: String,
    @JsonProperty("x") val x: Int,
    @JsonProperty("y") val y: Int,
    @JsonProperty("content") val content: MapContent?
)

class MapCell(
    @JsonProperty("type") val type: String,
    @JsonProperty("content") val content: MapContent?,
)
