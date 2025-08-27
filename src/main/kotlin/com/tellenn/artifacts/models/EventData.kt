package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

class EventData (
    @JsonProperty("name") val name: String,
    @JsonProperty("code") val code: String,
    @JsonProperty("duration") val duration: String,
    @JsonProperty("rate") val rate: Int,
    @JsonProperty("content") val content: Content,
    @JsonProperty("map") val map: EventMap
)

class Content(
    @JsonProperty("type") val type: String,
    @JsonProperty("code") val code: String
)

class EventMap(
    @JsonProperty("x") val x: String,
    @JsonProperty("y") val y: String,
    @JsonProperty("skin") val skin: String,
)