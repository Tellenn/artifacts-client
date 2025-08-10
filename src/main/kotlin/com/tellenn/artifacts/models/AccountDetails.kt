package com.tellenn.artifacts.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@Suppress("unused")
class AccountDetails(
    @JsonProperty("username") val username: String,
    @JsonProperty("email") val email: String,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("characters") val characters: List<String>,
    @JsonProperty("max_characters") val maxCharacters: Int
)