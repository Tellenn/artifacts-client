package com.tellenn.artifacts.clients.models

import com.fasterxml.jackson.annotation.JsonAlias
import java.util.Date

class Cooldown(
    @JsonAlias("total_seconds") totalSeconds : Int,
    @JsonAlias("remaining_seconds") remainingSeconds : Int,
    @JsonAlias("startedAt") startedAt : Date,
    @JsonAlias("expiration") expiration : Date,
    @JsonAlias("reason") reason : String,
) {
}