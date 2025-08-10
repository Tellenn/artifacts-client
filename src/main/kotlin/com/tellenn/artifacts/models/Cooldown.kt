package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class Cooldown(
    @JsonProperty("total_seconds") totalSeconds : Int,
    @JsonProperty("remaining_seconds") remainingSeconds : Int,
    @JsonProperty("started_at") startedAt : Instant,
    @JsonProperty("expiration") expiration : Instant,
    @JsonProperty("reason") reason : String,
) {
}