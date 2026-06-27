package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class EventData (
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("content") val content: Content,
    @param:JsonProperty("maps") val maps: List<EventMap>,
    @param:JsonProperty("duration") val duration: String,
    @param:JsonProperty("rate") val rate: Int,
    @param:JsonProperty("cooldown") val cooldown: Int,
    @param:JsonProperty("price") val price: Int?,
    @param:JsonProperty("transition") val transition: Transition?,
    @param:JsonProperty("cooldown_expiration") val cooldownExpiration: Instant?,
)

@Suppress("unused")
class Content(
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("code") val code: String
)

@Suppress("unused")
class EventMap(
    @param:JsonProperty("map_id") val mapId: Int,
    @param:JsonProperty("x") val x: Int,
    @param:JsonProperty("y") val y: Int,
    @param:JsonProperty("layer") val layer: String,
    @param:JsonProperty("skin") val skin: String,
)