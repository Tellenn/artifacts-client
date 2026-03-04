package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class Event (
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("duration") val duration: String,
    @param:JsonProperty("expiration") val expiration: Instant,
    @param:JsonProperty("created_at") val createdAt: Instant,
    @param:JsonProperty("map") val map: MapData,
    @param:JsonProperty("previous_map") val previousMaps: MapData,
)