package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class MapData(
    @JsonAlias("name") val name: String = "map",
    @JsonAlias("skin") val skin: String,
    @JsonAlias("x") val x: Int,
    @JsonAlias("y") val y: Int,
    @JsonAlias("content") val content: MapContent?
)

class MapCell(
    @JsonAlias("type") val type: String,
    @JsonAlias("content") val content: MapContent?,
)
