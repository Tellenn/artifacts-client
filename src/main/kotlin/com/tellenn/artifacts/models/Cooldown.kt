package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class Cooldown(
    @param:JsonProperty("total_seconds") val totalSeconds : Int,
    @param:JsonProperty("remaining_seconds") val remainingSeconds : Int,
    @param:JsonProperty("started_at") val startedAt : Instant,
    @param:JsonProperty("expiration") val expiration : Instant,
    @param:JsonProperty("reason") val reason : String,
) {
}