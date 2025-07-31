package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonAlias
import java.time.Instant

@Suppress("unused")
class Cooldown(
    @JsonAlias("total_seconds") totalSeconds : Int,
    @JsonAlias("remaining_seconds") remainingSeconds : Int,
    @JsonAlias("started_at") startedAt : Instant,
    @JsonAlias("expiration") expiration : Instant,
    @JsonAlias("reason") reason : String,
) {
}