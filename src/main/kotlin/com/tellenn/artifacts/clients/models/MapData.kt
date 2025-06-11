package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class MapData(
    @JsonAlias("x") val x: Int,
    @JsonAlias("y") val y: Int,
    @JsonAlias("width") val width: Int,
    @JsonAlias("height") val height: Int,
    @JsonAlias("cells") val cells: List<MapCell>
)

class MapCell(
    @JsonAlias("x") val x: Int,
    @JsonAlias("y") val y: Int,
    @JsonAlias("type") val type: String,
    @JsonAlias("content") val content: MapContent?,
    @JsonAlias("characters") val characters: List<ArtifactsCharacter>?
)