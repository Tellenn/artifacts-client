package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias

@Suppress("unused")
class MapData(
    @JsonAlias("name") val name: String,
    @JsonAlias("skin") val skin: String,
    @JsonAlias("x") val x: Int,
    @JsonAlias("y") val y: Int,
    @JsonAlias("content") val content: MapContent
)

class MapCell(
    @JsonAlias("x") val x: Int,
    @JsonAlias("y") val y: Int,
    @JsonAlias("type") val type: String,
    @JsonAlias("content") val content: MapContent?,
    @JsonAlias("characters") val characters: List<ArtifactsCharacter>?
)
