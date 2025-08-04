package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

class Event (
    @JsonProperty("name") val name: String,
    @JsonProperty("code") val code: String,
    @JsonProperty("duration") val duration: String,
    @JsonProperty("expiration") val expiration: Instant,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("map") val map: MapData,
    @JsonProperty("previous_map") val previousMaps: MapData,
)